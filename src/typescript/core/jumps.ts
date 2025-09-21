export class JumpSignal extends Error {
  public readonly label: string;
  public readonly delayMillis: number;

  constructor(label: string, delayMillis: number = 0) {
    if (!label || String(label).trim().length === 0) {
      throw new Error("label must be non-empty");
    }
    super(`jump to '${label}' after ${Number(delayMillis)}ms`);
    this.name = "JumpSignal";
    this.label = String(label);
    this.delayMillis = Math.max(0, Number(delayMillis));
  }
}

export function jumpNow(label: string): never {
  throw new JumpSignal(label, 0);
}

export function jumpAfter(label: string, delayMillis: number): never {
  throw new JumpSignal(label, delayMillis);
}
