import { LoggingMetrics, Metrics, NoopMetrics, RunScope, nowNanos } from "./metrics.js";
import { JumpSignal } from "./jumps.js";
import { ShortCircuit } from "./short_circuit.js";

export type StepFunction<T> = (inputValue: T) => T | Promise<T>;

export interface LabeledStep<T> {
  label?: string;
  name: string;
  fn: StepFunction<T>;
  section: "pre" | "main" | "post";
}

export class Pipeline<T> {
  public readonly name: string;
  public readonly shortCircuit: boolean;
  public maxJumps: number;
  private steps: Array<LabeledStep<T>>;
  private beforeEachSteps: Array<StepFunction<T>>;
  private afterEachSteps: Array<StepFunction<T>>;
  private metrics: Metrics;

  constructor(name: string, shortCircuit: boolean = true, metrics: Metrics = new NoopMetrics()) {
    this.name = name;
    this.shortCircuit = shortCircuit;
    this.maxJumps = 1000;
    this.steps = [];
    this.beforeEachSteps = [];
    this.afterEachSteps = [];
    this.metrics = metrics;
  }

  static builder<T>(name: string, shortCircuit: boolean = true, metrics: Metrics = new NoopMetrics()): Pipeline<T> {
    return new Pipeline<T>(name, shortCircuit, metrics);
  }

  beforeEach(stepFunction: StepFunction<T>): this {
    this.beforeEachSteps.push(stepFunction);
    return this;
  }

  afterEach(stepFunction: StepFunction<T>): this {
    this.afterEachSteps.push(stepFunction);
    return this;
  }

  step(stepFunction: StepFunction<T>, options?: { label?: string; name?: string; section?: "pre" | "main" | "post" }): this {
    const label = options?.label;
    const section = options?.section ?? "main";
    const stepName = options?.name ?? (stepFunction as any).name ?? "step";
    const labeled: LabeledStep<T> = { label, name: stepName, fn: stepFunction, section };
    this.steps.push(labeled);
    return this;
  }

  addSteps(...functions: Array<StepFunction<T>>): this {
    for (const functionPointer of functions) {
      this.step(functionPointer);
    }
    return this;
  }

  async run(inputValue: T, options?: { startLabel?: string | null; runId?: string | null }): Promise<T> {
    const startLabel = options?.startLabel ?? null;
    const runId = options?.runId ?? String(Date.now());
    const runScope = this.metrics.onPipelineStart(this.name, runId, startLabel ?? undefined);
    const runStartNanos = nowNanos();

    const preSteps: Array<LabeledStep<T>> = [];
    const mainSteps: Array<LabeledStep<T>> = [];
    const postSteps: Array<LabeledStep<T>> = [];
    for (const labeledStep of this.steps) {
      if (labeledStep.section === "pre") {
        preSteps.push(labeledStep);
      } else if (labeledStep.section === "post") {
        postSteps.push(labeledStep);
      } else {
        mainSteps.push(labeledStep);
      }
    }

    // Duplicate label detection across all sections
    const seenLabels = new Set<string>();
    for (const labeledStep of this.steps) {
      if (labeledStep.label) {
        if (seenLabels.has(labeledStep.label)) {
          const message = `Duplicate label detected: ${labeledStep.label}`;
          runScope.onPipelineEnd(false, nowNanos() - runStartNanos, new Error(message));
          throw new Error(message);
        }
        seenLabels.add(labeledStep.label);
      }
    }

    // Index only main labels
    const labelToIndex = new Map<string, number>();
    for (let buildIndex = 0; buildIndex < mainSteps.length; buildIndex += 1) {
      const stepToIndex = mainSteps[buildIndex];
      if (stepToIndex.label) {
        labelToIndex.set(stepToIndex.label, buildIndex);
      }
    }

    let currentIndex: number = 0;
    if (startLabel != null) {
      if (!labelToIndex.has(startLabel)) {
        const message = `Unknown or disallowed start label (must be a main-step label): ${startLabel}`;
        runScope.onPipelineEnd(false, nowNanos() - runStartNanos, new Error(message));
        throw new Error(message);
      }
      currentIndex = labelToIndex.get(startLabel)!;
    }

    let currentValue: T = inputValue;
    let lastError: unknown | undefined;

    // PRE once
    for (const preStep of preSteps) {
      const labelOrName = preStep.label ?? preStep.name;
      runScope.onStepStart(-1, labelOrName);
      try {
        const stepStart = nowNanos();
        currentValue = await preStep.fn(currentValue);
        runScope.onStepEnd(-1, labelOrName, nowNanos() - stepStart, true);
      } catch (caughtError) {
        runScope.onStepError(-1, labelOrName, caughtError);
        lastError = caughtError;
        if (this.shortCircuit) {
          runScope.onPipelineEnd(false, nowNanos() - runStartNanos, caughtError);
          throw caughtError;
        }
      }
    }

    // MAIN with jumps
    let jumpCount = 0;
    while (currentIndex < mainSteps.length) {
      const currentStep = mainSteps[currentIndex];
      const labelOrName = currentStep.label ?? currentStep.name;
      runScope.onStepStart(currentIndex, labelOrName);

      try {
        for (const beforeFunction of this.beforeEachSteps) {
          currentValue = await beforeFunction(currentValue);
        }

        const stepStart = nowNanos();
        currentValue = await currentStep.fn(currentValue);
        runScope.onStepEnd(currentIndex, labelOrName, nowNanos() - stepStart, true);

        for (const afterFunction of this.afterEachSteps) {
          currentValue = await afterFunction(currentValue);
        }

        currentIndex += 1;
      } catch (caughtError) {
        if (caughtError instanceof ShortCircuit) {
          runScope.onStepEnd(currentIndex, labelOrName, 0, true);
          currentValue = caughtError.value as T;
          lastError = undefined;
          break;
        } else if (caughtError instanceof JumpSignal) {
          jumpCount += 1;
          if (jumpCount > this.maxJumps) {
            const message = `max_jumps exceeded (${this.maxJumps})`;
            runScope.onStepError(currentIndex, labelOrName, new Error(message));
            lastError = new Error(message);
            break;
          }
          if (caughtError.delayMillis > 0) {
            await new Promise<void>(resolve => setTimeout(resolve, caughtError.delayMillis));
          }
          const target = caughtError.label;
          if (!labelToIndex.has(target)) {
            const message = `Unknown jump label (must be a main-step label): ${target}`;
            runScope.onStepError(currentIndex, labelOrName, new Error(message));
            lastError = new Error(message);
            break;
          }
          runScope.onJump(labelOrName, target, caughtError.delayMillis);
          currentIndex = labelToIndex.get(target)!;
          continue;
        } else {
          runScope.onStepError(currentIndex, labelOrName, caughtError);
          lastError = caughtError;
          if (this.shortCircuit) {
            break;
          } else {
            currentIndex += 1;
          }
        }
      }
    }

    // POST once
    if (lastError == null) {
      for (const postStep of postSteps) {
        const labelOrName = postStep.label ?? postStep.name;
        runScope.onStepStart(-1, labelOrName);
        try {
          const stepStart = nowNanos();
          currentValue = await postStep.fn(currentValue);
          runScope.onStepEnd(-1, labelOrName, nowNanos() - stepStart, true);
        } catch (caughtError) {
          runScope.onStepError(-1, labelOrName, caughtError);
          lastError = caughtError;
          if (this.shortCircuit) {
            break;
          }
        }
      }
    }

    runScope.onPipelineEnd(lastError == null, nowNanos() - runStartNanos, lastError);
    if (lastError != null && this.shortCircuit) {
      throw lastError;
    }
    return currentValue;
  }
}

export type TypedStepFunction<I, O> = (inputValue: I) => O | Promise<O>;

export class Pipe<I, O> {
  public readonly name: string;
  public readonly shortCircuit: boolean;
  private steps: Array<TypedStepFunction<any, any>>;
  private metrics: Metrics;

  constructor(name: string, shortCircuit: boolean = true, metrics: Metrics = new NoopMetrics()) {
    this.name = name;
    this.shortCircuit = shortCircuit;
    this.steps = [];
    this.metrics = metrics;
  }

  step(stepFunction: TypedStepFunction<any, any>): this {
    this.steps.push(stepFunction);
    return this;
  }

  async run(inputValue: I, options?: { runId?: string | null }): Promise<O> {
    const runId = options?.runId ?? String(Date.now());
    const runScope = this.metrics.onPipelineStart(this.name, runId, undefined);
    const runStart = nowNanos();
    let currentValue: any = inputValue;
    let lastError: unknown | undefined;

    for (let loopIndex = 0; loopIndex < this.steps.length; loopIndex += 1) {
      const functionPointer = this.steps[loopIndex];
      const stepName = "step";
      runScope.onStepStart(loopIndex, stepName);
      try {
        const stepStart = nowNanos();
        currentValue = await functionPointer(currentValue);
        runScope.onStepEnd(loopIndex, stepName, nowNanos() - stepStart, true);
      } catch (caughtError) {
        runScope.onStepError(loopIndex, stepName, caughtError);
        lastError = caughtError;
        if (this.shortCircuit) {
          break;
        }
      }
    }

    runScope.onPipelineEnd(lastError == null, nowNanos() - runStart, lastError);
    if (lastError != null && this.shortCircuit) {
      throw lastError;
    }
    return currentValue as O;
  }
}

export class RuntimePipeline<T> {
  public readonly name: string;
  public readonly shortCircuit: boolean;
  private pre: Array<StepFunction<T>>;
  private main: Array<StepFunction<T>>;
  private post: Array<StepFunction<T>>;
  private ended: boolean;
  private current: T | undefined;

  constructor(name: string, shortCircuit: boolean = true) {
    this.name = name;
    this.shortCircuit = shortCircuit;
    this.pre = [];
    this.main = [];
    this.post = [];
    this.ended = false;
    this.current = undefined;
  }

  reset(value: T): void {
    this.current = value;
    this.ended = false;
  }

  async addPre(stepFunction: StepFunction<T>): Promise<this> {
    this.pre.push(stepFunction);
    if (!this.ended && this.current !== undefined) {
      this.current = await stepFunction(this.current);
    }
    return this;
  }

  async step(stepFunction: StepFunction<T>): Promise<this> {
    this.main.push(stepFunction);
    if (!this.ended && this.current !== undefined) {
      try {
        this.current = await stepFunction(this.current);
      } catch (caughtError) {
        if (caughtError instanceof ShortCircuit) {
          this.current = caughtError.value as T;
          this.ended = true;
        } else if (this.shortCircuit) {
          this.ended = true;
        }
      }
    }
    return this;
  }

  async addPost(stepFunction: StepFunction<T>): Promise<this> {
    this.post.push(stepFunction);
    if (!this.ended && this.current !== undefined) {
      this.current = await stepFunction(this.current);
    }
    return this;
  }

  value(): T | undefined {
    return this.current;
  }
}
