export class PipelineRegistry<T> {
  private mapping: Map<string, (value: T) => T | Promise<T>>;

  constructor() {
    this.mapping = new Map();
  }

  register(key: string, pipelineCallable: (value: T) => T | Promise<T>): void {
    this.mapping.set(key, pipelineCallable);
  }

  lookup(key: string): ((value: T) => T | Promise<T>) | undefined {
    return this.mapping.get(key);
  }

  asMap(): Map<string, (value: T) => T | Promise<T>> {
    return new Map(this.mapping);
  }

  size(): number {
    return this.mapping.size;
  }
}
