import {
  Action,
  Pipeline,
  PipelineResult,
  StepAction,
} from "./pipeline.js";
import { RemoteSpec, http_step } from "../remote/http_step.js";

type RuntimeAction<ContextType> =
  | Action<ContextType>
  | StepAction<ContextType>
  | RemoteSpec;

function asAction<ContextType>(
  action: RuntimeAction<ContextType>,
): Action<ContextType> | StepAction<ContextType> {
  if (action instanceof RemoteSpec) {
    return (context: ContextType) =>
      http_step(action, context) as Promise<ContextType>;
  }
  if (typeof action !== "function") {
    throw new TypeError("Action must be callable or a RemoteSpec");
  }
  return action;
}

/** @deprecated Construct a Pipeline directly. */
export class RuntimePipeline<ContextType = unknown> {
  private currentValue: ContextType | null;
  private ended = false;
  private readonly preActions: Array<
    Action<ContextType> | StepAction<ContextType>
  > = [];
  private readonly actions: Array<
    Action<ContextType> | StepAction<ContextType>
  > = [];
  private readonly postActions: Array<
    Action<ContextType> | StepAction<ContextType>
  > = [];
  public lastResult: PipelineResult<ContextType> | null = null;

  constructor(
    public readonly name: string,
    public readonly shortCircuitOnException: boolean = true,
    initial: ContextType | null = null,
  ) {
    this.currentValue = initial;
  }

  value(): ContextType | null {
    return this.currentValue;
  }

  reset(value: ContextType): void {
    this.currentValue = value;
    this.ended = false;
    this.lastResult = null;
  }

  clearRecorded(): void {
    this.preActions.length = 0;
    this.actions.length = 0;
    this.postActions.length = 0;
  }

  async addPreAction(
    action: RuntimeAction<ContextType>,
  ): Promise<ContextType | null> {
    return this.addAndExecute("preActions", this.preActions, action);
  }

  async addAction(
    action: RuntimeAction<ContextType>,
  ): Promise<ContextType | null> {
    return this.addAndExecute("actions", this.actions, action);
  }

  async addPostAction(
    action: RuntimeAction<ContextType>,
  ): Promise<ContextType | null> {
    return this.addAndExecute("postActions", this.postActions, action);
  }

  async add_pre_action(
    action: RuntimeAction<ContextType>,
  ): Promise<ContextType | null> {
    return this.addPreAction(action);
  }

  async add_action(
    action: RuntimeAction<ContextType>,
  ): Promise<ContextType | null> {
    return this.addAction(action);
  }

  async add_post_action(
    action: RuntimeAction<ContextType>,
  ): Promise<ContextType | null> {
    return this.addPostAction(action);
  }

  toImmutable(): Pipeline<ContextType> {
    const pipeline = new Pipeline<ContextType>(
      this.name,
      this.shortCircuitOnException,
    );
    for (const action of this.preActions) {
      pipeline.addPreAction(action);
    }
    for (const action of this.actions) {
      pipeline.addAction(action);
    }
    for (const action of this.postActions) {
      pipeline.addPostAction(action);
    }
    return pipeline.freeze();
  }

  to_immutable(): Pipeline<ContextType> {
    return this.toImmutable();
  }

  freeze(): Pipeline<ContextType> {
    return this.toImmutable();
  }

  private async addAndExecute(
    phase: string,
    destination: Array<
      Action<ContextType> | StepAction<ContextType>
    >,
    action: RuntimeAction<ContextType>,
  ): Promise<ContextType | null> {
    if (this.ended) {
      return this.currentValue;
    }
    if (this.currentValue == null) {
      throw new TypeError(
        "RuntimePipeline requires a non-null current value",
      );
    }

    const normalized = asAction(action);
    destination.push(normalized);
    const pipeline = new Pipeline<ContextType>(
      `${this.name}:runtime`,
      this.shortCircuitOnException,
    );
    if (phase === "preActions") {
      pipeline.addPreAction(normalized);
    } else if (phase === "postActions") {
      pipeline.addPostAction(normalized);
    } else {
      pipeline.addAction(normalized);
    }

    const result = await pipeline.runDetailed(this.currentValue);
    this.currentValue = result.context;
    this.lastResult = result;
    this.ended = result.shortCircuited;
    return this.currentValue;
  }
}
