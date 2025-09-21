import { Pipeline, Pipe, StepFunction, TypedStepFunction } from "./pipeline.js";

declare module './pipeline.js' {
  interface Pipeline<T> {
    addAction(stepFunction: StepFunction<T>): this;
    addAction(label: string, stepFunction: StepFunction<T>): this;
  }
  interface Pipe<I, O> {
    addAction<M>(stepFunction: (inputValue: O) => M | Promise<M>): Pipe<I, M>;
  }
}

function addActionForPipeline<T>(this: Pipeline<T>, arg1: string | StepFunction<T>, arg2?: StepFunction<T>): Pipeline<T> {
  const hasLabel: boolean = typeof arg1 === "string";
  if (hasLabel) {
    const labelValue: string = arg1 as string;
    const functionPointer = arg2;
    if (typeof functionPointer !== "function") {
      throw new Error("addAction(label, stepFunction) requires a function for stepFunction.");
    }
    this.step(functionPointer, { label: labelValue, section: "main" });
  } else {
    const functionPointer = arg1 as StepFunction<T>;
    this.step(functionPointer, { section: "main" });
  }
  return this;
}

(Pipeline as any).prototype.addAction = addActionForPipeline;

function addActionForPipe<I, O, M>(this: Pipe<I, O>, stepFunction: (inputValue: O) => M | Promise<M>): Pipe<I, M> {
  const typedFunction = stepFunction as unknown as TypedStepFunction<any, any>;
  (this as any).steps.push(typedFunction);
  return (this as unknown) as Pipe<I, M>;
}

(Pipe as any).prototype.addAction = addActionForPipe;
