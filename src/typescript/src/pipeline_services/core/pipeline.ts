import { RemoteSpec, http_step } from "../remote/http_step.js";

export interface PipelineError {
  pipeline: string;
  phase: string;
  index: number;
  action_name: string;
  message: string;
}

export interface ActionTiming {
  phase: string;
  index: number;
  action_name: string;
  elapsed_nanos: bigint;
  success: boolean;
}

export type UnaryOperator = (ctx: unknown) => unknown | Promise<unknown>;
export type StepAction = (ctx: unknown, control: ActionControl) => unknown | Promise<unknown>;
export type OnErrorFn = (ctx: unknown, err: PipelineError) => unknown;

export function default_on_error(ctx: unknown, err: PipelineError): unknown {
  void err;
  return ctx;
}

export class ActionControl {
  public pipeline_name: string;
  public on_error: OnErrorFn;
  public errors: Array<PipelineError>;
  public timings: Array<ActionTiming>;
  public short_circuited: boolean;

  public phase: string;
  public index: number;
  public action_name: string;

  public run_start_ns: bigint;

  constructor(pipeline_name: string, on_error: OnErrorFn = default_on_error) {
    this.pipeline_name = pipeline_name;
    this.on_error = on_error;
    this.errors = [];
    this.timings = [];
    this.short_circuited = false;
    this.phase = "main";
    this.index = 0;
    this.action_name = "?";
    this.run_start_ns = 0n;
  }

  begin_step(phase: string, index: number, action_name: string): void {
    this.phase = phase;
    this.index = index;
    this.action_name = action_name;
  }

  begin_run(run_start_ns: bigint): void {
    this.run_start_ns = run_start_ns;
  }

  reset(): void {
    this.short_circuited = false;
    this.errors = [];
    this.timings = [];
    this.phase = "main";
    this.index = 0;
    this.action_name = "?";
    this.run_start_ns = 0n;
  }

  short_circuit(): void {
    this.short_circuited = true;
  }

  is_short_circuited(): boolean {
    return this.short_circuited;
  }

  record_error(ctx: unknown, message: string): unknown {
    const pipeline_error: PipelineError = {
      pipeline: this.pipeline_name,
      phase: this.phase,
      index: this.index,
      action_name: this.action_name,
      message,
    };
    this.errors.push(pipeline_error);
    return this.on_error(ctx, pipeline_error);
  }

  record_timing(elapsed_nanos: bigint, success: boolean): void {
    const timing: ActionTiming = {
      phase: this.phase,
      index: this.index,
      action_name: this.action_name,
      elapsed_nanos,
      success,
    };
    this.timings.push(timing);
  }
}

/** @deprecated Renamed to `ActionControl`. */
export { ActionControl as StepControl };

export class PipelineResult {
  public context: unknown;
  public short_circuited: boolean;
  public errors: Array<PipelineError>;
  public timings: Array<ActionTiming>;
  public total_nanos: bigint;

  constructor(
    context: unknown,
    short_circuited: boolean,
    errors: Array<PipelineError>,
    timings: Array<ActionTiming>,
    total_nanos: bigint,
  ) {
    this.context = context;
    this.short_circuited = short_circuited;
    this.errors = [...errors];
    this.timings = [...timings];
    this.total_nanos = total_nanos;
  }

  has_errors(): boolean {
    return this.errors.length > 0;
  }
}

export interface RegisteredAction {
  name: string;
  kind: number; // 0 = unary, 1 = step_action, 2 = remote_http
  unary: UnaryOperator;
  step_action: StepAction;
  remote_spec: RemoteSpec;
}

export function format_action_name(phase: string, index: number, name: string): string {
  let prefix = "s";
  if (phase === "pre") {
    prefix = "pre";
  } else if (phase === "post") {
    prefix = "post";
  }

  if (name === "") {
    return `${prefix}${index}`;
  }
  return `${prefix}${index}:${name}`;
}

export function format_step_name(phase: string, index: number, name: string): string {
  return format_action_name(phase, index, name);
}

export function safe_error_to_string(value: unknown): string {
  return String(value);
}

export function now_ns(): bigint {
  if (typeof process !== "undefined" && typeof process.hrtime === "function") {
    const hrtimeValue = process.hrtime as unknown as { bigint?: () => bigint };
    if (typeof hrtimeValue.bigint === "function") {
      return hrtimeValue.bigint();
    }
  }
  return BigInt(Date.now()) * 1_000_000n;
}

export function callable_accepts_two_positional_args(callable_value: unknown): boolean {
  if (typeof callable_value !== "function") {
    return false;
  }
  return callable_value.length >= 2;
}

export function noop_unary(ctx: unknown): unknown {
  return ctx;
}

export function noop_action(ctx: unknown, control: ActionControl): unknown {
  void control;
  return ctx;
}

export function noop_remote_spec(): RemoteSpec {
  return new RemoteSpec("");
}

export function to_registered_action(name: string, action: UnaryOperator | StepAction | RemoteSpec): RegisteredAction {
  if (action instanceof RemoteSpec) {
    return {
      name,
      kind: 2,
      unary: noop_unary,
      step_action: noop_action,
      remote_spec: action,
    };
  }

  if (typeof action !== "function") {
    throw new TypeError("Action must be callable or a RemoteSpec");
  }

  if (callable_accepts_two_positional_args(action)) {
    return {
      name,
      kind: 1,
      unary: noop_unary,
      step_action: action as StepAction,
      remote_spec: noop_remote_spec(),
    };
  }

  return {
    name,
    kind: 0,
    unary: action as UnaryOperator,
    step_action: noop_action,
    remote_spec: noop_remote_spec(),
  };
}

export class Pipeline {
  public name: string;
  public short_circuit_on_exception: boolean;
  public on_error: OnErrorFn;

  public pre_actions: Array<RegisteredAction>;
  public actions: Array<RegisteredAction>;
  public post_actions: Array<RegisteredAction>;

  constructor(name: string, short_circuit_on_exception: boolean = true) {
    this.name = name;
    this.short_circuit_on_exception = short_circuit_on_exception;
    this.on_error = default_on_error;
    this.pre_actions = [];
    this.actions = [];
    this.post_actions = [];
  }

  on_error_handler(handler: OnErrorFn): void {
    this.on_error = handler;
  }

  add_pre_action(action: UnaryOperator | StepAction | RemoteSpec): void {
    this.add_pre_action_named("", action);
  }

  add_pre_action_named(name: string, action: UnaryOperator | StepAction | RemoteSpec): void {
    this.pre_actions.push(to_registered_action(name, action));
  }

  add_action(action: UnaryOperator | StepAction | RemoteSpec): void {
    this.add_action_named("", action);
  }

  add_action_named(name: string, action: UnaryOperator | StepAction | RemoteSpec): void {
    this.actions.push(to_registered_action(name, action));
  }

  add_post_action(action: UnaryOperator | StepAction | RemoteSpec): void {
    this.add_post_action_named("", action);
  }

  add_post_action_named(name: string, action: UnaryOperator | StepAction | RemoteSpec): void {
    this.post_actions.push(to_registered_action(name, action));
  }

  async run(input_value: unknown): Promise<PipelineResult> {
    let ctx: unknown = input_value;
    const control = new ActionControl(this.name, this.on_error);
    control.begin_run(now_ns());

    ctx = await this.run_phase("pre", ctx, this.pre_actions, control, false);
    if (!control.is_short_circuited()) {
      ctx = await this.run_phase("main", ctx, this.actions, control, true);
    }
    ctx = await this.run_phase("post", ctx, this.post_actions, control, false);

    const total_nanos = now_ns() - control.run_start_ns;
    return new PipelineResult(ctx, control.is_short_circuited(), control.errors, control.timings, total_nanos);
  }

  async execute(input_value: unknown): Promise<PipelineResult> {
    return this.run(input_value);
  }

  async run_phase(
    phase: string,
    start_ctx: unknown,
    actions: Array<RegisteredAction>,
    control: ActionControl,
    stop_on_short_circuit: boolean,
  ): Promise<unknown> {
    let ctx: unknown = start_ctx;
    let step_index = 0;
    while (step_index < actions.length) {
      const registered_action = actions[step_index];
      const action_name = format_action_name(phase, step_index, registered_action.name);
      control.begin_step(phase, step_index, action_name);

      const step_start_ns = now_ns();
      let step_succeeded = true;
      try {
        if (registered_action.kind === 0) {
          ctx = await registered_action.unary(ctx);
        } else if (registered_action.kind === 1) {
          ctx = await registered_action.step_action(ctx, control);
        } else {
          ctx = await http_step(registered_action.remote_spec, ctx);
        }
      } catch (caught_error) {
        step_succeeded = false;
        ctx = control.record_error(ctx, safe_error_to_string(caught_error));
        if (this.short_circuit_on_exception) {
          control.short_circuit();
        }
      }

      const step_elapsed_nanos = now_ns() - step_start_ns;
      control.record_timing(step_elapsed_nanos, step_succeeded);

      if (stop_on_short_circuit && control.is_short_circuited()) {
        break;
      }
      step_index += 1;
    }
    return ctx;
  }
}
