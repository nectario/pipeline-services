import {
  Pipeline,
  RegisteredAction,
  StepAction,
  StepControl,
  UnaryOperator,
  format_step_name,
  safe_error_to_string,
  to_registered_action,
} from "./pipeline.js";
import { RemoteSpec, http_step } from "../remote/http_step.js";

export class RuntimePipeline {
  public name: string;
  public short_circuit_on_exception: boolean;

  private current: unknown;
  private ended: boolean;

  private pre_actions: Array<RegisteredAction>;
  private actions: Array<RegisteredAction>;
  private post_actions: Array<RegisteredAction>;

  private pre_index: number;
  private action_index: number;
  private post_index: number;

  private control: StepControl;

  constructor(name: string, short_circuit_on_exception: boolean = true, initial: unknown = null) {
    this.name = name;
    this.short_circuit_on_exception = short_circuit_on_exception;
    this.current = initial;
    this.ended = false;
    this.pre_actions = [];
    this.actions = [];
    this.post_actions = [];
    this.pre_index = 0;
    this.action_index = 0;
    this.post_index = 0;
    this.control = new StepControl(name);
  }

  value(): unknown {
    return this.current;
  }

  reset(value: unknown): void {
    this.current = value;
    this.ended = false;
    this.control.reset();
  }

  async add_pre_action(action: UnaryOperator | StepAction | RemoteSpec): Promise<unknown> {
    if (this.ended) {
      return this.current;
    }
    const registered_action = to_registered_action("", action);
    this.pre_actions.push(registered_action);
    const output_value = await this.apply_action(registered_action, "pre", this.pre_index);
    this.pre_index += 1;
    return output_value;
  }

  async add_action(action: UnaryOperator | StepAction | RemoteSpec): Promise<unknown> {
    if (this.ended) {
      return this.current;
    }
    const registered_action = to_registered_action("", action);
    this.actions.push(registered_action);
    const output_value = await this.apply_action(registered_action, "main", this.action_index);
    this.action_index += 1;
    return output_value;
  }

  async add_post_action(action: UnaryOperator | StepAction | RemoteSpec): Promise<unknown> {
    if (this.ended) {
      return this.current;
    }
    const registered_action = to_registered_action("", action);
    this.post_actions.push(registered_action);
    const output_value = await this.apply_action(registered_action, "post", this.post_index);
    this.post_index += 1;
    return output_value;
  }

  to_immutable(): Pipeline {
    const pipeline = new Pipeline(this.name, this.short_circuit_on_exception);

    for (const registered_action of this.pre_actions) {
      if (registered_action.kind === 0) {
        pipeline.add_pre_action(registered_action.unary);
      } else if (registered_action.kind === 1) {
        pipeline.add_pre_action(registered_action.step_action);
      } else {
        pipeline.add_pre_action(registered_action.remote_spec);
      }
    }

    for (const registered_action of this.actions) {
      if (registered_action.kind === 0) {
        pipeline.add_action(registered_action.unary);
      } else if (registered_action.kind === 1) {
        pipeline.add_action(registered_action.step_action);
      } else {
        pipeline.add_action(registered_action.remote_spec);
      }
    }

    for (const registered_action of this.post_actions) {
      if (registered_action.kind === 0) {
        pipeline.add_post_action(registered_action.unary);
      } else if (registered_action.kind === 1) {
        pipeline.add_post_action(registered_action.step_action);
      } else {
        pipeline.add_post_action(registered_action.remote_spec);
      }
    }

    return pipeline;
  }

  freeze(): Pipeline {
    return this.to_immutable();
  }

  private async apply_action(registered_action: RegisteredAction, phase: string, index: number): Promise<unknown> {
    if (this.ended) {
      return this.current;
    }

    const step_name = format_step_name(phase, index, registered_action.name);
    this.control.begin_step(phase, index, step_name);
    try {
      if (registered_action.kind === 0) {
        this.current = await registered_action.unary(this.current);
      } else if (registered_action.kind === 1) {
        this.current = await registered_action.step_action(this.current, this.control);
      } else {
        this.current = await http_step(registered_action.remote_spec, this.current);
      }
    } catch (caught_error) {
      this.current = this.control.record_error(this.current, safe_error_to_string(caught_error));
      if (this.short_circuit_on_exception) {
        this.control.short_circuit();
        this.ended = true;
      }
    }

    if (this.control.is_short_circuited()) {
      this.ended = true;
    }

    return this.current;
  }
}

