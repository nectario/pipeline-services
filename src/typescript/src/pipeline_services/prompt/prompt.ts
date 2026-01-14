export class PromptStep {
  public prompt_spec: unknown;

  constructor(prompt_spec: unknown) {
    this.prompt_spec = prompt_spec;
  }

  run(input_value: unknown, adapter: unknown): unknown {
    if (adapter == null) {
      throw new Error("No prompt adapter provided");
    }
    if (typeof adapter !== "function") {
      throw new Error("Prompt adapter must be callable");
    }
    return (adapter as (input_value: unknown, prompt_spec: unknown) => unknown)(input_value, this.prompt_spec);
  }
}

