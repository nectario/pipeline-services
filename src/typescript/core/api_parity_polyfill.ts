import { Pipeline, StepFunction } from "./pipeline.js";
import { Metrics } from "./metrics.js";
import { PipelineJsonLoader } from "../config/json_loader.js";

declare module './pipeline.js' {
  interface Pipeline<T> {
    before(label: string, stepFunction: StepFunction<T>): this;
    after(label: string, stepFunction: StepFunction<T>): this;
    jumpTo(label: string): this;
    setMetrics(metrics: Metrics): this;
    setMaxJumpsPerRun(maxJumps: number): this;
    enableJumps(enabled: boolean): this;
    setName(newName: string): this;
    setShortCircuit(enabled: boolean): this;
    onErrorReturn(handler: (error: unknown) => T | Promise<T>): this;
    sleeper(sleeperFn: (delayMillis: number) => Promise<void>): this;
    fork(): Pipeline<T>;
    isSealed(): boolean;
    addBean(id: string, instance: unknown): this;
    addPipelineConfig(jsonOrPath: string): Promise<this>;
  }
}

// Internal helpers (kept small and readable)
function getSteps<T>(self: Pipeline<T>): Array<any> {
  const anySelf = self as any;
  if (!Array.isArray(anySelf.steps)) {
    throw new Error("Pipeline internal steps array not found.");
  }
  return anySelf.steps as Array<any>;
}

function findMainLabelIndex<T>(self: Pipeline<T>, label: string): number {
  const steps = getSteps(self);
  for (let i = 0; i < steps.length; i += 1) {
    const step = steps[i];
    if (step && step.section === "main" && step.label === label) {
      return i;
    }
  }
  return -1;
}

function makeLabeledStep<T>(fn: StepFunction<T>): any {
  const nameValue: string = (fn as any).name ? String((fn as any).name) : "step";
  return { label: undefined, name: nameValue, fn, section: "main" };
}

// --- Add methods on Pipeline.prototype ---

(Pipeline as any).prototype.before = function<T>(this: Pipeline<T>, label: string, stepFunction: StepFunction<T>): Pipeline<T> {
  if (!label || String(label).trim().length === 0) {
    throw new Error("before(label, fn) requires a non-empty label.");
  }
  if (typeof stepFunction !== "function") {
    throw new Error("before(label, fn) requires a function for fn.");
  }
  const index = findMainLabelIndex(this, label);
  if (index < 0) {
    throw new Error(`before: label not found among main steps: ${label}`);
  }
  const steps = getSteps(this);
  const newStep = makeLabeledStep(stepFunction);
  steps.splice(index, 0, newStep);
  return this;
};

(Pipeline as any).prototype.after = function<T>(this: Pipeline<T>, label: string, stepFunction: StepFunction<T>): Pipeline<T> {
  if (!label || String(label).trim().length === 0) {
    throw new Error("after(label, fn) requires a non-empty label.");
  }
  if (typeof stepFunction !== "function") {
    throw new Error("after(label, fn) requires a function for fn.");
  }
  const index = findMainLabelIndex(this, label);
  if (index < 0) {
    throw new Error(`after: label not found among main steps: ${label}`);
  }
  const steps = getSteps(this);
  const newStep = makeLabeledStep(stepFunction);
  steps.splice(index + 1, 0, newStep);
  return this;
};

(Pipeline as any).prototype.jumpTo = function<T>(this: Pipeline<T>, label: string): Pipeline<T> {
  const anySelf = this as any;
  anySelf.defaultStartLabel = String(label);
  return this;
};

(Pipeline as any).prototype.setMetrics = function<T>(this: Pipeline<T>, metrics: Metrics): Pipeline<T> {
  const anySelf = this as any;
  anySelf.metrics = metrics;
  return this;
};

(Pipeline as any).prototype.setMaxJumpsPerRun = function<T>(this: Pipeline<T>, maxJumps: number): Pipeline<T> {
  const anySelf = this as any;
  anySelf.maxJumps = Math.max(0, Number(maxJumps));
  return this;
};

(Pipeline as any).prototype.enableJumps = function<T>(this: Pipeline<T>, enabled: boolean): Pipeline<T> {
  const anySelf = this as any;
  if (enabled) {
    if (typeof anySelf.previousMaxJumps === "number" && anySelf.previousMaxJumps >= 0) {
      anySelf.maxJumps = anySelf.previousMaxJumps;
    } else if (typeof anySelf.maxJumps !== "number" || anySelf.maxJumps <= 0) {
      anySelf.maxJumps = 1000;
    }
  } else {
    anySelf.previousMaxJumps = typeof anySelf.maxJumps === "number" ? anySelf.maxJumps : 1000;
    anySelf.maxJumps = 0;
  }
  return this;
};

(Pipeline as any).prototype.setName = function<T>(this: Pipeline<T>, newName: string): Pipeline<T> {
  const anySelf = this as any;
  anySelf.name = String(newName);
  return this;
};

(Pipeline as any).prototype.setShortCircuit = function<T>(this: Pipeline<T>, enabled: boolean): Pipeline<T> {
  const anySelf = this as any;
  anySelf.shortCircuit = Boolean(enabled);
  return this;
};

(Pipeline as any).prototype.onErrorReturn = function<T>(this: Pipeline<T>, handler: (error: unknown) => T | Promise<T>): Pipeline<T> {
  if (typeof handler !== "function") {
    throw new Error("onErrorReturn(handler) requires a function.");
  }
  const anySelf = this as any;
  anySelf.errorHandler = handler;
  return this;
};

(Pipeline as any).prototype.sleeper = function<T>(this: Pipeline<T>, sleeperFn: (delayMillis: number) => Promise<void>): Pipeline<T> {
  if (typeof sleeperFn !== "function") {
    throw new Error("sleeper(fn) requires a function that returns a Promise.");
  }
  const anySelf = this as any;
  anySelf.sleeperFn = sleeperFn;
  return this;
};

(Pipeline as any).prototype.addBean = function<T>(this: Pipeline<T>, id: string, instance: unknown): Pipeline<T> {
  const anySelf = this as any;
  if (!anySelf.beans) {
    anySelf.beans = {};
  }
  anySelf.beans[String(id)] = instance;
  return this;
};

(Pipeline as any).prototype.addPipelineConfig = async function<T>(this: Pipeline<T>, jsonOrPath: string): Promise<Pipeline<T>> {
  const text = String(jsonOrPath ?? "");
  const anySelf = this as any;
  const loader = new PipelineJsonLoader({ beans: anySelf.beans || {}, metrics: anySelf.metrics });

  let loadedPipeline: any;
  const looksJson = text.trim().startsWith("{") || text.trim().startsWith("[");
  if (looksJson) {
    loadedPipeline = await loader.loadStr(text);
  } else {
    loadedPipeline = await loader.loadFile(text);
  }

  // Merge steps and hooks
  const currentSteps = getSteps(this);
  const loadedSteps = getSteps(loadedPipeline);
  for (let i = 0; i < loadedSteps.length; i += 1) {
    currentSteps.push(loadedSteps[i]);
  }

  const currentBeforeEach = (this as any).beforeEachSteps as Array<StepFunction<T>>;
  const currentAfterEach = (this as any).afterEachSteps as Array<StepFunction<T>>;
  const loadedBeforeEach = (loadedPipeline as any).beforeEachSteps as Array<StepFunction<T>>;
  const loadedAfterEach = (loadedPipeline as any).afterEachSteps as Array<StepFunction<T>>;

  if (Array.isArray(loadedBeforeEach)) {
    for (let i = 0; i < loadedBeforeEach.length; i += 1) {
      currentBeforeEach.push(loadedBeforeEach[i]);
    }
  }
  if (Array.isArray(loadedAfterEach)) {
    for (let i = 0; i < loadedAfterEach.length; i += 1) {
      currentAfterEach.push(loadedAfterEach[i]);
    }
  }

  return this;
};

(Pipeline as any).prototype.fork = function<T>(this: Pipeline<T>): Pipeline<T> {
  const anySelf = this as any;
  const cloned = new (Pipeline as any)(anySelf.name, anySelf.shortCircuit, anySelf.metrics) as Pipeline<T>;

  // Copy steps and hooks
  const clonedAny = cloned as any;
  clonedAny.steps = [];
  const srcSteps = getSteps(this);
  for (let i = 0; i < srcSteps.length; i += 1) {
    const s = srcSteps[i];
    clonedAny.steps.push({ label: s.label, name: s.name, fn: s.fn, section: s.section });
  }

  clonedAny.beforeEachSteps = Array.isArray(anySelf.beforeEachSteps) ? [...anySelf.beforeEachSteps] : [];
  clonedAny.afterEachSteps = Array.isArray(anySelf.afterEachSteps) ? [...anySelf.afterEachSteps] : [];
  clonedAny.maxJumps = typeof anySelf.maxJumps === "number" ? anySelf.maxJumps : 1000;
  clonedAny.defaultStartLabel = anySelf.defaultStartLabel || "";
  clonedAny.errorHandler = anySelf.errorHandler;
  clonedAny.beans = { ...(anySelf.beans || {}) };
  clonedAny.sealed = false;

  return cloned;
};

(Pipeline as any).prototype.isSealed = function<T>(this: Pipeline<T>): boolean {
  const anySelf = this as any;
  return Boolean(anySelf.sealed);
};

// Wrap run to support default start label, sleeper, and onErrorReturn
(function patchRunOnce(){
  const proto: any = (Pipeline as any).prototype;
  if (proto.__runPatched) {
    return;
  }
  const originalRun = proto.run;
  proto.run = async function<T>(this: Pipeline<T>, inputValue: T, options?: { startLabel?: string | null; runId?: string | null }): Promise<T> {
    const anySelf = this as any;
    anySelf.sealed = true;

    const optionsToUse: any = { ...(options || {}) };
    if (optionsToUse.startLabel == null && anySelf.defaultStartLabel) {
      optionsToUse.startLabel = String(anySelf.defaultStartLabel);
    }

    let oldSetTimeout: any = null;
    let usingCustomSleeper: boolean = false;
    if (typeof anySelf.sleeperFn === "function") {
      oldSetTimeout = (globalThis as any).setTimeout;
      usingCustomSleeper = true;
      (globalThis as any).setTimeout = (callback: Function, ms?: number) => {
        const delayMillis: number = typeof ms === "number" ? ms : 0;
        (anySelf.sleeperFn as Function)(delayMillis).then(() => {
          try { (callback as any)(); } catch {}
        });
        return 0 as unknown as any;
      };
    }

    try {
      const result = await originalRun.call(this, inputValue, optionsToUse);
      return result as T;
    } catch (caughtError) {
      if (typeof anySelf.errorHandler === "function") {
        const fallbackValue = await (anySelf.errorHandler as Function)(caughtError);
        return fallbackValue as T;
      }
      throw caughtError;
    } finally {
      if (usingCustomSleeper && oldSetTimeout) {
        (globalThis as any).setTimeout = oldSetTimeout;
      }
    }
  };
  proto.__runPatched = true;
})();
