import { StepAction, UnaryOperator } from "./pipeline.js";

export class PipelineRegistry {
  private unary_actions: Map<string, UnaryOperator>;
  private step_actions: Map<string, StepAction>;

  constructor() {
    this.unary_actions = new Map<string, UnaryOperator>();
    this.step_actions = new Map<string, StepAction>();
  }

  register_unary(name: string, action: UnaryOperator): void {
    this.unary_actions.set(name, action);
  }

  register_action(name: string, action: StepAction): void {
    this.step_actions.set(name, action);
  }

  has_unary(name: string): boolean {
    return this.unary_actions.has(name);
  }

  has_action(name: string): boolean {
    return this.step_actions.has(name);
  }

  get_unary(name: string): UnaryOperator {
    const action = this.unary_actions.get(name);
    if (action != null) {
      return action;
    }
    throw new Error("Unknown unary action: " + name);
  }

  get_action(name: string): StepAction {
    const action = this.step_actions.get(name);
    if (action != null) {
      return action;
    }
    throw new Error("Unknown step action: " + name);
  }
}

