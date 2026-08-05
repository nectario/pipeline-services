import { AsyncLocalStorage } from "node:async_hooks";

export type MaybePromise<ValueType> = ValueType | Promise<ValueType>;
export type Action<ContextType = unknown> = (
  context: ContextType,
) => MaybePromise<ContextType>;
export type UnaryOperator<ContextType = unknown> = Action<ContextType>;
export type StepAction<ContextType = unknown> = (
  context: ContextType,
  control: ActionControl<ContextType>,
) => MaybePromise<ContextType>;
export type OnErrorFn<ContextType = unknown> = (
  context: ContextType,
  error: PipelineError,
) => MaybePromise<ContextType>;

export class PipelineError {
  constructor(
    public readonly pipelineName: string,
    public readonly phase: string,
    public readonly actionIndex: number,
    public readonly actionName: string,
    public readonly exception: Error,
  ) {}

  get pipeline(): string {
    return this.pipelineName;
  }

  get pipeline_name(): string {
    return this.pipelineName;
  }

  get index(): number {
    return this.actionIndex;
  }

  get action_index(): number {
    return this.actionIndex;
  }

  get action_name(): string {
    return this.actionName;
  }

  get message(): string {
    return this.exception.message;
  }
}

export class ActionTiming {
  constructor(
    public readonly phase: string,
    public readonly actionIndex: number,
    public readonly actionName: string,
    public readonly elapsedNanos: bigint,
    public readonly success: boolean,
  ) {}

  get index(): number {
    return this.actionIndex;
  }

  get action_index(): number {
    return this.actionIndex;
  }

  get action_name(): string {
    return this.actionName;
  }

  get elapsed_nanos(): bigint {
    return this.elapsedNanos;
  }
}

export class PipelineResult<ContextType = unknown> {
  constructor(
    public readonly context: ContextType,
    public readonly shortCircuited: boolean,
    public readonly errors: ReadonlyArray<PipelineError>,
    public readonly actionTimings: ReadonlyArray<ActionTiming>,
    public readonly totalNanos: bigint,
  ) {}

  get short_circuited(): boolean {
    return this.shortCircuited;
  }

  get action_timings(): ReadonlyArray<ActionTiming> {
    return this.actionTimings;
  }

  get timings(): ReadonlyArray<ActionTiming> {
    return this.actionTimings;
  }

  get total_nanos(): bigint {
    return this.totalNanos;
  }

  hasErrors(): boolean {
    return this.errors.length > 0;
  }

  has_errors(): boolean {
    return this.hasErrors();
  }
}

export interface PipelineObserver {
  onPipelineStarted?(pipelineName: string): MaybePromise<void>;
  onActionStarted?(
    pipelineName: string,
    phase: string,
    actionIndex: number,
    actionName: string,
  ): MaybePromise<void>;
  onActionCompleted?(
    pipelineName: string,
    phase: string,
    actionIndex: number,
    actionName: string,
    elapsedNanos: bigint,
  ): MaybePromise<void>;
  onActionFailed?(
    pipelineName: string,
    phase: string,
    actionIndex: number,
    actionName: string,
    exception: Error,
    elapsedNanos: bigint,
  ): MaybePromise<void>;
  onShortCircuited?(
    pipelineName: string,
    phase: string,
    actionIndex: number,
    actionName: string,
  ): MaybePromise<void>;
  onPipelineCompleted?(
    pipelineName: string,
    shortCircuited: boolean,
    errorCount: number,
    elapsedNanos: bigint,
  ): MaybePromise<void>;
}

interface RegisteredAction<ContextType> {
  readonly name: string | null;
  readonly action: Action<ContextType>;
}

interface PipelinePlan<ContextType> {
  readonly pipelineName: string;
  readonly shortCircuitOnException: boolean;
  readonly errorHandler: OnErrorFn<ContextType>;
  readonly observer: PipelineObserver;
  readonly preActions: ReadonlyArray<RegisteredAction<ContextType>>;
  readonly actions: ReadonlyArray<RegisteredAction<ContextType>>;
  readonly postActions: ReadonlyArray<RegisteredAction<ContextType>>;
}

interface ExecutionState<ContextType> {
  readonly plan: PipelinePlan<ContextType>;
  context: ContextType;
  readonly collectTimings: boolean;
  readonly runStartNanos: bigint;
  readonly errors: Array<PipelineError>;
  readonly actionTimings: Array<ActionTiming>;
  shortCircuited: boolean;
  actionExecuting: boolean;
  phase: string;
  actionIndex: number;
  actionName: string;
}

const NOOP_OBSERVER: PipelineObserver = {};
const executionStorage =
  new AsyncLocalStorage<ReadonlyArray<ExecutionState<unknown>>>();

export class InvalidErrorHandlerError extends Error {
  constructor(
    message: string,
    public readonly actionException: Error,
    public readonly handlerException: unknown = null,
  ) {
    super(message, {
      cause: handlerException ?? actionException,
    });
    this.name = "InvalidErrorHandlerError";
  }
}

function toError(value: unknown): Error {
  if (value instanceof Error) {
    return value;
  }
  return new Error(String(value));
}

export function nowNs(): bigint {
  return process.hrtime.bigint();
}

function currentState<ContextType>(): ExecutionState<ContextType> {
  const stack = executionStorage.getStore();
  if (stack == null || stack.length === 0) {
    throw new Error("No active Pipeline execution");
  }
  return stack[stack.length - 1] as ExecutionState<ContextType>;
}

export function shortCircuit(): void {
  const stack = executionStorage.getStore();
  if (stack == null || stack.length === 0) {
    throw new Error(
      "shortCircuit() can only be called during an active Pipeline run",
    );
  }
  const state = stack[stack.length - 1];
  if (!state.actionExecuting) {
    throw new Error(
      "shortCircuit() can only be called while a Pipeline Action is executing",
    );
  }
  state.shortCircuited = true;
}

/** @deprecated Use shortCircuit(). */
export function short_circuit(): void {
  shortCircuit();
}

export class ActionControl<ContextType = unknown> {
  shortCircuit(): void {
    shortCircuit();
  }

  short_circuit(): void {
    shortCircuit();
  }

  isShortCircuited(): boolean {
    return currentState<ContextType>().shortCircuited;
  }

  is_short_circuited(): boolean {
    return this.isShortCircuited();
  }

  get shortCircuited(): boolean {
    return this.isShortCircuited();
  }

  get short_circuited(): boolean {
    return this.isShortCircuited();
  }

  get pipelineName(): string {
    return currentState<ContextType>().plan.pipelineName;
  }

  get pipeline_name(): string {
    return this.pipelineName;
  }

  get errors(): ReadonlyArray<PipelineError> {
    return [...currentState<ContextType>().errors];
  }

  get actionTimings(): ReadonlyArray<ActionTiming> {
    return [...currentState<ContextType>().actionTimings];
  }

  get timings(): ReadonlyArray<ActionTiming> {
    return this.actionTimings;
  }

  get phase(): string {
    return currentState<ContextType>().phase;
  }

  get actionIndex(): number {
    return currentState<ContextType>().actionIndex;
  }

  get index(): number {
    return this.actionIndex;
  }

  get actionName(): string {
    return currentState<ContextType>().actionName;
  }

  get action_name(): string {
    return this.actionName;
  }

  get runStartNanos(): bigint {
    return currentState<ContextType>().runStartNanos;
  }

  get run_start_ns(): bigint {
    return this.runStartNanos;
  }

  async recordError(
    context: ContextType,
    exception: Error | string,
  ): Promise<ContextType> {
    return recordError(
      currentState<ContextType>(),
      context,
      typeof exception === "string" ? new Error(exception) : exception,
    );
  }

  async record_error(
    context: ContextType,
    exception: Error | string,
  ): Promise<ContextType> {
    return this.recordError(context, exception);
  }
}

/** @deprecated Renamed to ActionControl. */
export { ActionControl as StepControl };

function acceptsControlParameter(action: Function): boolean {
  return action.length >= 2;
}

function normalizeAction<ContextType>(
  action: Action<ContextType> | StepAction<ContextType>,
): Action<ContextType> {
  if (typeof action !== "function") {
    throw new TypeError("Action must be callable");
  }

  if (acceptsControlParameter(action)) {
    return (context: ContextType) =>
      (action as StepAction<ContextType>)(
        context,
        new ActionControl<ContextType>(),
      );
  }
  return action as Action<ContextType>;
}

function formatActionName(
  phase: string,
  actionIndex: number,
  registeredName: string | null,
): string {
  const prefix =
    phase === "preActions"
      ? "pre"
      : phase === "postActions"
        ? "post"
        : "s";
  return registeredName == null || registeredName.length === 0
    ? `${prefix}${actionIndex}`
    : `${prefix}${actionIndex}:${registeredName}`;
}

async function notify(callback: (() => MaybePromise<void>) | undefined): Promise<void> {
  if (callback == null) {
    return;
  }
  try {
    await callback();
  } catch {
    // Observers cannot change Pipeline semantics.
  }
}

async function recordError<ContextType>(
  state: ExecutionState<ContextType>,
  context: ContextType,
  exception: Error,
): Promise<ContextType> {
  const pipelineError = new PipelineError(
    state.plan.pipelineName,
    state.phase,
    state.actionIndex,
    state.actionName,
    exception,
  );
  state.errors.push(pipelineError);

  const wasActionExecuting = state.actionExecuting;
  state.actionExecuting = false;
  let updatedContext: ContextType;
  try {
    updatedContext = await state.plan.errorHandler(context, pipelineError);
  } catch (handlerException) {
    throw new InvalidErrorHandlerError(
      "onError handler raised while recovering from an Action failure",
      exception,
      handlerException,
    );
  } finally {
    state.actionExecuting = wasActionExecuting;
  }

  if (updatedContext == null) {
    throw new InvalidErrorHandlerError(
      "onError handler returned null or undefined",
      exception,
    );
  }
  state.context = updatedContext;
  return updatedContext;
}

class PipelineRunner {
  static async run<ContextType>(
    plan: PipelinePlan<ContextType>,
    inputValue: ContextType,
  ): Promise<ContextType> {
    return (await this.execute(plan, inputValue, false)).context;
  }

  static async runDetailed<ContextType>(
    plan: PipelinePlan<ContextType>,
    inputValue: ContextType,
  ): Promise<PipelineResult<ContextType>> {
    const state = await this.execute(plan, inputValue, true);
    return new PipelineResult(
      state.context,
      state.shortCircuited,
      [...state.errors],
      [...state.actionTimings],
      nowNs() - state.runStartNanos,
    );
  }

  private static async execute<ContextType>(
    plan: PipelinePlan<ContextType>,
    inputValue: ContextType,
    collectTimings: boolean,
  ): Promise<ExecutionState<ContextType>> {
    if (inputValue == null) {
      throw new TypeError("inputValue must not be null or undefined");
    }

    const runStartNanos = nowNs();
    const state: ExecutionState<ContextType> = {
      plan,
      context: inputValue,
      collectTimings,
      runStartNanos,
      errors: [],
      actionTimings: [],
      shortCircuited: false,
      actionExecuting: false,
      phase: "actions",
      actionIndex: 0,
      actionName: "?",
    };

    await notify(
      plan.observer.onPipelineStarted?.bind(
        plan.observer,
        plan.pipelineName,
      ),
    );

    const parentStack = executionStorage.getStore() ?? [];
    return executionStorage.run(
      [...parentStack, state as ExecutionState<unknown>],
      async () => {
        let pendingFailure: Error | null = null;
        try {
          try {
            pendingFailure = await this.executeActions(
              state,
              "preActions",
              plan.preActions,
              false,
              pendingFailure,
            );
            if (pendingFailure == null && !state.shortCircuited) {
              pendingFailure = await this.executeActions(
                state,
                "actions",
                plan.actions,
                true,
                pendingFailure,
              );
            }
          } finally {
            pendingFailure = await this.executeActions(
              state,
              "postActions",
              plan.postActions,
              false,
              pendingFailure,
            );
          }
        } finally {
          await notify(
            plan.observer.onPipelineCompleted?.bind(
              plan.observer,
              plan.pipelineName,
              state.shortCircuited,
              state.errors.length,
              nowNs() - runStartNanos,
            ),
          );
        }

        if (pendingFailure != null) {
          throw pendingFailure;
        }
        return state;
      },
    );
  }

  private static async executeActions<ContextType>(
    state: ExecutionState<ContextType>,
    phase: string,
    actions: ReadonlyArray<RegisteredAction<ContextType>>,
    stopOnShortCircuit: boolean,
    initialFailure: Error | null,
  ): Promise<Error | null> {
    if (initialFailure != null && phase !== "postActions") {
      return initialFailure;
    }

    let pendingFailure = initialFailure;
    const observer = state.plan.observer;
    const observerEnabled = observer !== NOOP_OBSERVER;

    for (let actionIndex = 0; actionIndex < actions.length; actionIndex += 1) {
      const registeredAction = actions[actionIndex];
      state.phase = phase;
      state.actionIndex = actionIndex;
      state.actionName = formatActionName(
        phase,
        actionIndex,
        registeredAction.name,
      );
      const wasShortCircuited = state.shortCircuited;

      if (observerEnabled) {
        await notify(
          observer.onActionStarted?.bind(
            observer,
            state.plan.pipelineName,
            phase,
            actionIndex,
            state.actionName,
          ),
        );
      }

      const actionStartNanos =
        state.collectTimings || observerEnabled ? nowNs() : 0n;
      let actionSucceeded = true;
      let actionFailure: Error | null = null;

      try {
        let nextContext: ContextType;
        state.actionExecuting = true;
        try {
          nextContext = await registeredAction.action(state.context);
        } finally {
          state.actionExecuting = false;
        }
        if (nextContext == null) {
          throw new TypeError(`Action returned null or undefined: ${state.actionName}`);
        }
        state.context = nextContext;
      } catch (caughtError) {
        actionSucceeded = false;
        actionFailure = toError(caughtError);
        try {
          await recordError(state, state.context, actionFailure);
        } catch (handlerFailure) {
          if (pendingFailure == null) {
            pendingFailure = toError(handlerFailure);
          }
          state.shortCircuited = true;
        }
        if (state.plan.shortCircuitOnException) {
          state.shortCircuited = true;
        }
      }

      const elapsedNanos =
        state.collectTimings || observerEnabled
          ? nowNs() - actionStartNanos
          : 0n;
      if (state.collectTimings) {
        state.actionTimings.push(
          new ActionTiming(
            phase,
            actionIndex,
            state.actionName,
            elapsedNanos,
            actionSucceeded,
          ),
        );
      }

      if (observerEnabled) {
        if (actionSucceeded) {
          await notify(
            observer.onActionCompleted?.bind(
              observer,
              state.plan.pipelineName,
              phase,
              actionIndex,
              state.actionName,
              elapsedNanos,
            ),
          );
        } else {
          await notify(
            observer.onActionFailed?.bind(
              observer,
              state.plan.pipelineName,
              phase,
              actionIndex,
              state.actionName,
              actionFailure!,
              elapsedNanos,
            ),
          );
        }
      }

      if (!wasShortCircuited && state.shortCircuited) {
        await notify(
          observer.onShortCircuited?.bind(
            observer,
            state.plan.pipelineName,
            phase,
            actionIndex,
            state.actionName,
          ),
        );
      }

      if (pendingFailure != null && phase !== "postActions") {
        break;
      }
      if (stopOnShortCircuit && state.shortCircuited) {
        break;
      }
    }

    return pendingFailure;
  }
}

export class Pipeline<ContextType = unknown> {
  public readonly pipelineName: string;
  public readonly name: string;
  public shortCircuitOnException: boolean;

  private errorHandler: OnErrorFn<ContextType>;
  private pipelineObserver: PipelineObserver;
  private readonly mutablePreActions: Array<RegisteredAction<ContextType>>;
  private readonly mutableActions: Array<RegisteredAction<ContextType>>;
  private readonly mutablePostActions: Array<RegisteredAction<ContextType>>;
  private frozenPlan: PipelinePlan<ContextType> | null;

  constructor(
    pipelineName: string,
    shortCircuitOnException: boolean = true,
  ) {
    const normalizedName = pipelineName.trim();
    if (normalizedName.length === 0) {
      throw new TypeError("pipelineName must not be blank");
    }
    this.pipelineName = normalizedName;
    this.name = normalizedName;
    this.shortCircuitOnException = shortCircuitOnException;
    this.errorHandler = (context) => context;
    this.pipelineObserver = NOOP_OBSERVER;
    this.mutablePreActions = [];
    this.mutableActions = [];
    this.mutablePostActions = [];
    this.frozenPlan = null;
  }

  onError(handler: OnErrorFn<ContextType> | null): this {
    this.ensureMutable();
    this.errorHandler = handler ?? ((context) => context);
    return this;
  }

  on_error_handler(handler: OnErrorFn<ContextType> | null): this {
    return this.onError(handler);
  }

  observer(observer: PipelineObserver | null): this {
    this.ensureMutable();
    this.pipelineObserver = observer ?? NOOP_OBSERVER;
    return this;
  }

  addPreAction(
    action: Action<ContextType> | StepAction<ContextType>,
    name: string | null = null,
  ): this {
    return this.register(this.mutablePreActions, name, action);
  }

  addAction(
    action: Action<ContextType> | StepAction<ContextType>,
    name: string | null = null,
  ): this {
    return this.register(this.mutableActions, name, action);
  }

  addPostAction(
    action: Action<ContextType> | StepAction<ContextType>,
    name: string | null = null,
  ): this {
    return this.register(this.mutablePostActions, name, action);
  }

  add_pre_action(action: Action<ContextType> | StepAction<ContextType>): this {
    return this.addPreAction(action);
  }

  add_action(action: Action<ContextType> | StepAction<ContextType>): this {
    return this.addAction(action);
  }

  add_post_action(action: Action<ContextType> | StepAction<ContextType>): this {
    return this.addPostAction(action);
  }

  add_pre_action_named(
    name: string,
    action: Action<ContextType> | StepAction<ContextType>,
  ): this {
    return this.addPreAction(action, name);
  }

  add_action_named(
    name: string,
    action: Action<ContextType> | StepAction<ContextType>,
  ): this {
    return this.addAction(action, name);
  }

  add_post_action_named(
    name: string,
    action: Action<ContextType> | StepAction<ContextType>,
  ): this {
    return this.addPostAction(action, name);
  }

  shortCircuit(): void {
    shortCircuit();
  }

  short_circuit(): void {
    shortCircuit();
  }

  async run(inputValue: ContextType): Promise<ContextType> {
    return PipelineRunner.run(this.plan(), inputValue);
  }

  async runDetailed(
    inputValue: ContextType,
  ): Promise<PipelineResult<ContextType>> {
    return PipelineRunner.runDetailed(this.plan(), inputValue);
  }

  async run_detailed(
    inputValue: ContextType,
  ): Promise<PipelineResult<ContextType>> {
    return this.runDetailed(inputValue);
  }

  async execute(
    inputValue: ContextType,
  ): Promise<PipelineResult<ContextType>> {
    return this.runDetailed(inputValue);
  }

  freeze(): this {
    this.plan();
    return this;
  }

  isFrozen(): boolean {
    return this.frozenPlan != null;
  }

  is_frozen(): boolean {
    return this.isFrozen();
  }

  size(): number {
    return this.frozenPlan?.actions.length ?? this.mutableActions.length;
  }

  private register(
    destination: Array<RegisteredAction<ContextType>>,
    name: string | null,
    action: Action<ContextType> | StepAction<ContextType>,
  ): this {
    this.ensureMutable();
    destination.push({
      name: name == null || name.trim().length === 0 ? null : name.trim(),
      action: normalizeAction(action),
    });
    return this;
  }

  private plan(): PipelinePlan<ContextType> {
    if (this.frozenPlan == null) {
      this.frozenPlan = Object.freeze({
        pipelineName: this.pipelineName,
        shortCircuitOnException: this.shortCircuitOnException,
        errorHandler: this.errorHandler,
        observer: this.pipelineObserver,
        preActions: Object.freeze([...this.mutablePreActions]),
        actions: Object.freeze([...this.mutableActions]),
        postActions: Object.freeze([...this.mutablePostActions]),
      });
    }
    return this.frozenPlan;
  }

  private ensureMutable(): void {
    if (this.frozenPlan != null) {
      throw new Error(`Pipeline '${this.pipelineName}' is frozen`);
    }
  }
}
