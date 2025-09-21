const predicateState = { count: 0 };

export class TypedPredicates {
  static needsAwait(_: unknown): boolean {
    predicateState.count += 1;
    const isNeeded = predicateState.count <= 2;
    return isNeeded;
  }
}
