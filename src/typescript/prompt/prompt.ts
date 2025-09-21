export interface PromptSpec<I, O> {
  name: string;
  goal: string;
  rules: Array<string>;
  examples: Array<{ in: I; out: O }>;
  p50Micros: number;
}

export class PromptBuilder<I, O> {
  private spec: PromptSpec<I, O>;

  constructor() {
    this.spec = { name: "promptStep", goal: "", rules: [], examples: [], p50Micros: 0 };
  }

  name(value: string): this {
    this.spec.name = value;
    return this;
  }

  goal(value: string): this {
    this.spec.goal = value;
    return this;
  }

  rule(text: string): this {
    this.spec.rules.push(text);
    return this;
  }

  example(inputValue: I, outputValue: O): this {
    this.spec.examples.push({ in: inputValue, out: outputValue });
    return this;
  }

  p50Micros(micros: number): this {
    this.spec.p50Micros = Math.max(0, Math.floor(micros));
    return this;
  }

  build(adapter?: (inputValue: I, spec: PromptSpec<I, O>) => O | Promise<O>) {
    const specCopy: PromptSpec<I, O> = JSON.parse(JSON.stringify(this.spec));
    if (typeof adapter !== "function") {
      return async () => {
        throw new Error("Prompt-generated code not available; provide an adapter to 'build()'");
      };
    }
    return async (inputValue: I) => {
      return await adapter(inputValue, specCopy);
    };
  }
}
