import { Pipeline, Pipe, StepFunction } from "../core/pipeline.js";
import { jumpAfter, jumpNow } from "../core/jumps.js";
import { Metrics, NoopMetrics } from "../core/metrics.js";

export interface LoaderOptions {
  beans?: Record<string, unknown>;
  instance?: unknown;
  metrics?: Metrics;
}

export class PipelineJsonLoader {
  private beans: Record<string, unknown>;
  private instance: unknown;
  private metrics: Metrics;

  constructor(options?: LoaderOptions) {
    this.beans = { ...(options?.beans ?? {}) };
    this.instance = options?.instance;
    this.metrics = options?.metrics ?? new NoopMetrics();
  }

  async loadStr(jsonText: string): Promise<unknown> {
    const spec = JSON.parse(jsonText);
    return this.build(spec);
  }

  async loadFile(filePath: string): Promise<unknown> {
    const fs = await import("node:fs/promises");
    const text = await fs.readFile(filePath, { encoding: "utf-8" });
    return this.loadStr(text);
  }

  async build(root: any): Promise<unknown> {
    const pipelineName = String(root.pipeline ?? "pipeline");
    const pipelineType = String(root.type ?? "unary");
    const shortFlag = Boolean(root.shortCircuit ?? true);

    if (pipelineType === "unary") {
      const pipeline = new Pipeline<any>(pipelineName, shortFlag, this.metrics);
      for (const sectionName of ["pre", "steps", "post"] as const) {
        const nodes: any[] = root[sectionName] ?? [];
        for (const node of nodes) {
          const { step, label } = await this.compileStep(node, false);
          const section = sectionName === "steps" ? "main" : (sectionName as "pre" | "post");
          pipeline.step(step, { label, section });
        }
      }
      return pipeline;
    } else if (pipelineType === "typed") {
      const pipe = new Pipe<any, any>(pipelineName, shortFlag, this.metrics);
      for (const sectionName of ["pre", "steps", "post"] as const) {
        const nodes: any[] = root[sectionName] ?? [];
        for (const node of nodes) {
          const { step } = await this.compileStep(node, true);
          pipe.step(step as any);
        }
      }
      return pipe;
    } else {
      throw new Error(`Unsupported pipeline type: ${pipelineType}`);
    }
  }

  private async compileStep(node: any, typed: boolean): Promise<{ step: StepFunction<any>; label?: string }> {
    const label = node.label as string | undefined;

    const jumpWhen = node.jumpWhen as any | undefined;
    let predicateStep: StepFunction<any> | undefined;
    let jumpLabel: string | undefined;
    let jumpDelayMs: number = 0;

    if (jumpWhen) {
      jumpLabel = String(jumpWhen.label ?? "");
      jumpDelayMs = Number(jumpWhen.delayMillis ?? 0);
      if (jumpWhen.predicate) {
        const compiled = await this.compileStep(jumpWhen.predicate, true);
        predicateStep = compiled.step;
      }
    }

    let stepCallable: StepFunction<any>;

    if (node["$local"]) {
      const fqn = String(node["$local"]);
      stepCallable = await this.resolveLocal(fqn);
    } else if (node["$method"]) {
      stepCallable = await this.resolveMethod(node["$method"]);
    } else if (node["$prompt"]) {
      stepCallable = this.resolvePrompt(node["$prompt"]);
    } else if (node["$remote"]) {
      stepCallable = this.resolveRemote(node["$remote"]);
    } else {
      throw new Error("Unsupported step node");
    }

    if (jumpWhen && predicateStep && jumpLabel) {
      const wrapped: StepFunction<any> = async (inputValue: any) => {
        const truthy = Boolean(await predicateStep!(inputValue));
        if (truthy) {
          if (jumpDelayMs > 0) {
            jumpAfter(jumpLabel!, jumpDelayMs);
          } else {
            jumpNow(jumpLabel!);
          }
        }
        const result = await stepCallable(inputValue);
        return result;
      };
      return { step: wrapped, label };
    } else {
      return { step: stepCallable, label };
    }
  }

  private async resolveLocal(fqn: string): Promise<StepFunction<any>> {
    // Interpret "package.module.ClassName" as "package/module" and "ClassName"
    const lastDot = fqn.lastIndexOf(".");
    if (lastDot < 0) {
      throw new Error(`Invalid $local value (expected module.Class): ${fqn}`);
    }
    const moduleName = fqn.slice(0, lastDot).replaceAll(".", "/");
    const className = fqn.slice(lastDot + 1);
    const moduleObj: any = await import(moduleName);
    const classRef = moduleObj[className];
    if (!classRef) {
      throw new Error(`Class ${className} not found in module ${moduleName}`);
    }
    const instance = new classRef();
    if (typeof instance.apply !== "function") {
      throw new Error(`Class ${fqn} lacks an 'apply' method`);
    }
    const callApply: StepFunction<any> = async (inputValue: any) => {
      return await instance.apply(inputValue);
    };
    return callApply;
  }

  private async resolveMethod(spec: any): Promise<StepFunction<any>> {
    const ref = String(spec.ref ?? "");
    const target = spec.target ? String(spec.target) : "";

    if (ref.includes("#")) {
      const [moduleClass, methodName] = ref.split("#");
      const lastDot = moduleClass.lastIndexOf(".");
      if (lastDot < 0) {
        throw new Error(`Invalid method ref: ${ref}`);
      }
      const moduleName = moduleClass.slice(0, lastDot).replaceAll(".", "/");
      const className = moduleClass.slice(lastDot + 1);
      const moduleObj: any = await import(moduleName);
      const classRef = moduleObj[className];
      if (!classRef) {
        throw new Error(`Class ${className} not found in module ${moduleName}`);
      }

      let bound: any;
      if (target === "@this") {
        bound = this.instance;
        if (bound == null) {
          throw new Error("target=@this but loader 'instance' is undefined");
        }
      } else if (target.startsWith("@")) {
        const beanId = target.slice(1);
        bound = this.beans[beanId];
        if (bound == null) {
          throw new Error(`Unknown bean id: ${beanId}`);
        }
      } else {
        bound = classRef;
      }

      const method = bound[methodName];
      if (typeof method !== "function") {
        throw new Error(`Method ${methodName} not found on ${target || className}`);
      }
      const callMethod: StepFunction<any> = async (inputValue: any) => {
        return await method.call(bound, inputValue);
      };
      return callMethod;
    } else {
      const lastColon = ref.lastIndexOf(":");
      if (lastColon < 0) {
        throw new Error(`Invalid function ref: ${ref}`);
      }
      const moduleName = ref.slice(0, lastColon).replaceAll(".", "/");
      const funcName = ref.slice(lastColon + 1);
      const moduleObj: any = await import(moduleName);
      const func = moduleObj[funcName];
      if (typeof func !== "function") {
        throw new Error(`Function ${funcName} not found in module ${moduleName}`);
      }
      const callFunc: StepFunction<any> = async (inputValue: any) => {
        return await func(inputValue);
      };
      return callFunc;
    }
  }

  private resolvePrompt(promptSpec: any): StepFunction<any> {
    const adapter = this.beans["llm_adapter"];
    if (typeof adapter !== "function") {
      const raise: StepFunction<any> = async () => {
        throw new Error("No 'llm_adapter' bean provided for $prompt step");
      };
      return raise;
    }
    const callAdapter: StepFunction<any> = async (inputValue: any) => {
      return await (adapter as Function)(inputValue, promptSpec);
    };
    return callAdapter;
  }

  private resolveRemote(remoteSpec: any): StepFunction<any> {
    const { httpStep, RemoteSpec } = requireRemote();
    const spec = new RemoteSpec({
      endpoint: String(remoteSpec.endpoint),
      timeoutMillis: Number(remoteSpec.timeoutMillis ?? 1000),
      retries: Number(remoteSpec.retries ?? 0),
      headers: { ...(remoteSpec.headers ?? {}) },
      method: String(remoteSpec.method ?? "POST").toUpperCase()
    });
    const toJsonId = remoteSpec.toJsonBean;
    const fromJsonId = remoteSpec.fromJsonBean;
    if (toJsonId && typeof this.beans[toJsonId] === "function") {
      spec.toJson = this.beans[toJsonId] as (inputValue: unknown) => string;
    }
    if (fromJsonId && typeof this.beans[fromJsonId] === "function") {
      spec.fromJson = this.beans[fromJsonId] as (text: string) => unknown;
    }
    return httpStep(spec);
  }
}

function requireRemote() {
  // Local import to avoid circular import issues in ESM
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const mod = require("../remote/http_step.js");
  return mod;
}
