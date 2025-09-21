export type Unary<T> = (value: T) => T | Promise<T>;

export function ignoreErrors<T>(step: Unary<T>): Unary<T> {
  return async function wrapper(inputValue: T): Promise<T> {
    try {
      const result = await step(inputValue);
      return result;
    } catch {
      return inputValue;
    }
  };
}

export function withFallback<I, O>(step: (value: I) => O | Promise<O>,
                                   fallback: (error: unknown) => O | Promise<O>): (value: I) => Promise<O> {
  return async function wrapper(inputValue: I): Promise<O> {
    try {
      const result = await step(inputValue);
      return result;
    } catch (caughtError) {
      const alternative = await fallback(caughtError);
      return alternative;
    }
  };
}
