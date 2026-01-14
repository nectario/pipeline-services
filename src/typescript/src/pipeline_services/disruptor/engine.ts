export class DisruptorEngine {
  public name: string;

  constructor(name: string) {
    this.name = name;
  }

  publish(value: unknown): void {
    void value;
    throw new Error("DisruptorEngine is not implemented in this TypeScript port yet");
  }
}

