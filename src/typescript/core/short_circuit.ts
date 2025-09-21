export class ShortCircuit<T = unknown> extends Error {
  public readonly value: T;
  constructor(value: T) {
    super("short_circuit");
    this.name = "ShortCircuit";
    this.value = value;
  }
}

export function shortCircuit<T>(value: T): never {
  throw new ShortCircuit(value);
}
