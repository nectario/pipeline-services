#pragma once

#include <chrono>
#include <cstddef>
#include <cstdint>
#include <exception>
#include <functional>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <string_view>
#include <type_traits>
#include <utility>
#include <vector>

namespace pipeline_services {

enum class StepPhase {
  PRE,
  MAIN,
  POST,
};

inline constexpr std::string_view phaseName(StepPhase phase) noexcept {
  switch (phase) {
    case StepPhase::PRE:
      return "preActions";
    case StepPhase::POST:
      return "postActions";
    default:
      return "actions";
  }
}

inline std::string stepPhaseToString(StepPhase phase) {
  return std::string(phaseName(phase));
}

struct PipelineError {
  std::string pipelineName;
  StepPhase phase;
  std::size_t actionIndex;
  std::string actionName;
  std::exception_ptr exception;
  std::string message;

  // Preview compatibility aliases.
  std::size_t stepIndex;
  std::string stepName;
};

struct ActionTiming {
  StepPhase phase;
  std::size_t actionIndex;
  std::string actionName;
  std::int64_t elapsedNanos;
  bool success;

  // Preview compatibility alias.
  std::size_t index;
};

template <typename ContextType>
struct PipelineResult {
  ContextType context;
  bool shortCircuited;
  std::vector<PipelineError> errors;
  std::vector<ActionTiming> actionTimings;
  std::int64_t totalNanos;

  bool hasErrors() const noexcept {
    return !errors.empty();
  }
};

class InvalidErrorHandlerError final : public std::runtime_error {
 public:
  InvalidErrorHandlerError(
      std::string message,
      std::exception_ptr actionException,
      std::exception_ptr handlerException = nullptr)
      : std::runtime_error(std::move(message)),
        actionException_(std::move(actionException)),
        handlerException_(std::move(handlerException)) {}

  const std::exception_ptr& actionException() const noexcept {
    return actionException_;
  }

  const std::exception_ptr& handlerException() const noexcept {
    return handlerException_;
  }

 private:
  std::exception_ptr actionException_;
  std::exception_ptr handlerException_;
};

class PipelineObserver {
 public:
  virtual ~PipelineObserver() = default;

  virtual void onPipelineStarted(const std::string&) {}
  virtual void onActionStarted(
      const std::string&,
      StepPhase,
      std::size_t,
      const std::string&) {}
  virtual void onActionCompleted(
      const std::string&,
      StepPhase,
      std::size_t,
      const std::string&,
      std::int64_t) {}
  virtual void onActionFailed(
      const std::string&,
      StepPhase,
      std::size_t,
      const std::string&,
      std::exception_ptr,
      std::int64_t) {}
  virtual void onShortCircuited(
      const std::string&,
      StepPhase,
      std::size_t,
      const std::string&) {}
  virtual void onPipelineCompleted(
      const std::string&,
      bool,
      std::size_t,
      std::int64_t) {}
};

namespace detail {

inline std::string safeExceptionToString(const std::exception_ptr& exception) {
  if (!exception) {
    return "unknown exception";
  }
  try {
    std::rethrow_exception(exception);
  } catch (const std::exception& error) {
    return error.what() == nullptr ? "exception" : std::string(error.what());
  } catch (...) {
    return "unknown exception";
  }
}

inline std::string formatActionName(
    StepPhase phase,
    std::size_t actionIndex,
    const std::string& registeredName) {
  std::string prefix = "s";
  if (phase == StepPhase::PRE) {
    prefix = "pre";
  } else if (phase == StepPhase::POST) {
    prefix = "post";
  }
  if (registeredName.empty()) {
    return prefix + std::to_string(actionIndex);
  }
  return prefix + std::to_string(actionIndex) + ":" + registeredName;
}

struct ExecutionStateBase {
  virtual ~ExecutionStateBase() = default;

  bool shortCircuited = false;
  bool actionExecuting = false;
  StepPhase phase = StepPhase::MAIN;
  std::size_t actionIndex = 0;
  std::string actionName = "?";
  std::chrono::steady_clock::time_point runStarted =
      std::chrono::steady_clock::now();
};

inline thread_local std::vector<ExecutionStateBase*> executionStack;

inline ExecutionStateBase& currentExecution() {
  if (executionStack.empty()) {
    throw std::logic_error(
        "shortCircuit() can only be called during an active Pipeline run");
  }
  return *executionStack.back();
}

class ExecutionScope {
 public:
  explicit ExecutionScope(ExecutionStateBase& state) : state_(&state) {
    executionStack.push_back(state_);
  }

  ExecutionScope(const ExecutionScope&) = delete;
  ExecutionScope& operator=(const ExecutionScope&) = delete;

  ~ExecutionScope() noexcept {
    if (executionStack.empty() || executionStack.back() != state_) {
      // Destructors must not throw. Clear the corrupted internal scope stack so
      // a later run cannot inherit invalid ambient execution state.
      executionStack.clear();
      return;
    }
    executionStack.pop_back();
  }

 private:
  ExecutionStateBase* state_;
};

template <typename ContextType>
struct ExecutionState;

}  // namespace detail

inline void shortCircuit() {
  detail::ExecutionStateBase& state = detail::currentExecution();
  if (!state.actionExecuting) {
    throw std::logic_error(
        "shortCircuit() can only be called while a Pipeline Action is executing");
  }
  state.shortCircuited = true;
}

template <typename ContextType>
class ActionControl;

template <typename ContextType>
using Action = std::function<ContextType(ContextType)>;

template <typename ContextType>
using UnaryOperator = Action<ContextType>;

template <typename ContextType>
using StepAction =
    std::function<ContextType(ContextType, ActionControl<ContextType>&)>;

template <typename ContextType>
using OnErrorFn =
    std::function<ContextType(ContextType, const PipelineError&)>;

template <typename ContextType>
ContextType defaultOnError(ContextType context, const PipelineError&) {
  return context;
}

namespace detail {

template <typename ContextType>
struct ExecutionState final : ExecutionStateBase {
  ExecutionState(
      std::string pipelineNameValue,
      OnErrorFn<ContextType> errorHandlerValue,
      ContextType input,
      bool collectTimingsValue)
      : pipelineName(std::move(pipelineNameValue)),
        errorHandler(std::move(errorHandlerValue)),
        context(std::move(input)),
        collectTimings(collectTimingsValue) {}

  std::string pipelineName;
  OnErrorFn<ContextType> errorHandler;
  ContextType context;
  bool collectTimings;
  std::vector<PipelineError> errors;
  std::vector<ActionTiming> actionTimings;
};

template <typename ContextType>
ExecutionState<ContextType>& currentTypedExecution() {
  ExecutionStateBase& base = currentExecution();
  auto* typed = dynamic_cast<ExecutionState<ContextType>*>(&base);
  if (typed == nullptr) {
    throw std::logic_error(
        "Active Pipeline execution has an incompatible context type");
  }
  return *typed;
}

}  // namespace detail

template <typename ContextType>
class ActionControl {
 public:
  void shortCircuit() const {
    pipeline_services::shortCircuit();
  }

  bool isShortCircuited() const {
    return state().shortCircuited;
  }

  ContextType recordError(
      ContextType context,
      std::exception_ptr exception) const {
    auto& executionState = state();
    PipelineError pipelineError{
        .pipelineName = executionState.pipelineName,
        .phase = executionState.phase,
        .actionIndex = executionState.actionIndex,
        .actionName = executionState.actionName,
        .exception = exception,
        .message = detail::safeExceptionToString(exception),
        .stepIndex = executionState.actionIndex,
        .stepName = executionState.actionName,
    };
    executionState.errors.push_back(pipelineError);

    const bool wasExecuting = executionState.actionExecuting;
    executionState.actionExecuting = false;
    try {
      ContextType updated = executionState.errorHandler(
          std::move(context), executionState.errors.back());
      executionState.actionExecuting = wasExecuting;
      return updated;
    } catch (...) {
      executionState.actionExecuting = wasExecuting;
      throw InvalidErrorHandlerError(
          "onError handler raised while recovering from an Action failure",
          std::move(exception),
          std::current_exception());
    }
  }

  const std::vector<PipelineError>& errors() const {
    return state().errors;
  }

  const std::string& pipelineName() const {
    return state().pipelineName;
  }

  std::int64_t runStartNanos() const {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
               state().runStarted.time_since_epoch())
        .count();
  }

  std::int64_t runElapsedNanos() const {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
               std::chrono::steady_clock::now() - state().runStarted)
        .count();
  }

  const std::vector<ActionTiming>& actionTimings() const {
    return state().actionTimings;
  }

 private:
  static detail::ExecutionState<ContextType>& state() {
    return detail::currentTypedExecution<ContextType>();
  }
};

template <typename ContextType>
using StepControl = ActionControl<ContextType>;

template <typename ContextType>
class Pipeline {
 private:
  struct RegisteredAction {
    std::string name;
    Action<ContextType> action;
  };

  struct PipelinePlan {
    std::string pipelineName;
    bool shortCircuitOnException;
    OnErrorFn<ContextType> errorHandler;
    std::shared_ptr<PipelineObserver> observer;
    std::vector<RegisteredAction> preActions;
    std::vector<RegisteredAction> actions;
    std::vector<RegisteredAction> postActions;
  };

 public:
  explicit Pipeline(
      std::string pipelineName,
      bool shortCircuitOnException = true)
      : pipelineName_(std::move(pipelineName)),
        shortCircuitOnException_(shortCircuitOnException),
        errorHandler_(defaultOnError<ContextType>),
        observer_(std::make_shared<PipelineObserver>()) {
    static_assert(
        std::is_copy_constructible_v<ContextType>,
        "Pipeline currently requires CopyConstructible context types so the "
        "last successful context can be retained when an Action throws");
    if (pipelineName_.empty()) {
      throw std::invalid_argument("pipelineName must not be blank");
    }
  }

  Pipeline(const Pipeline&) = delete;
  Pipeline& operator=(const Pipeline&) = delete;

  Pipeline(Pipeline&& other) noexcept {
    std::scoped_lock lock(other.assemblyMutex_);
    moveFrom(std::move(other));
  }

  Pipeline& operator=(Pipeline&& other) noexcept {
    if (this == &other) {
      return *this;
    }
    std::scoped_lock lock(assemblyMutex_, other.assemblyMutex_);
    moveFrom(std::move(other));
    return *this;
  }

  const std::string& pipelineName() const noexcept {
    return pipelineName_;
  }

  const std::string& name() const noexcept {
    return pipelineName_;
  }

  bool shortCircuitOnException() const {
    std::lock_guard lock(assemblyMutex_);
    return frozenPlan_ == nullptr
        ? shortCircuitOnException_
        : frozenPlan_->shortCircuitOnException;
  }

  std::size_t size() const {
    return plan()->actions.size();
  }

  bool isFrozen() const {
    std::lock_guard lock(assemblyMutex_);
    return frozenPlan_ != nullptr;
  }

  Pipeline& freeze() {
    (void)plan();
    return *this;
  }

  Pipeline& onError(OnErrorFn<ContextType> handler) {
    std::lock_guard lock(assemblyMutex_);
    ensureMutableLocked();
    errorHandler_ = handler ? std::move(handler) : defaultOnError<ContextType>;
    return *this;
  }

  Pipeline& observer(std::shared_ptr<PipelineObserver> observerValue) {
    std::lock_guard lock(assemblyMutex_);
    ensureMutableLocked();
    observer_ = observerValue == nullptr
        ? std::make_shared<PipelineObserver>()
        : std::move(observerValue);
    return *this;
  }

  template <typename CallableType>
  Pipeline& addPreAction(CallableType callable) {
    return addPreAction("", std::move(callable));
  }

  template <typename CallableType>
  Pipeline& addPreAction(
      std::string actionName,
      CallableType callable) {
    registerAction(
        preActions_, std::move(actionName), std::move(callable));
    return *this;
  }

  template <typename CallableType>
  Pipeline& addAction(CallableType callable) {
    return addAction("", std::move(callable));
  }

  template <typename CallableType>
  Pipeline& addAction(
      std::string actionName,
      CallableType callable) {
    registerAction(
        actions_, std::move(actionName), std::move(callable));
    return *this;
  }

  template <typename CallableType>
  Pipeline& addPostAction(CallableType callable) {
    return addPostAction("", std::move(callable));
  }

  template <typename CallableType>
  Pipeline& addPostAction(
      std::string actionName,
      CallableType callable) {
    registerAction(
        postActions_, std::move(actionName), std::move(callable));
    return *this;
  }

  void shortCircuit() const {
    pipeline_services::shortCircuit();
  }

  ContextType run(ContextType input) const {
    return execute(plan(), std::move(input), false).context;
  }

  PipelineResult<ContextType> runDetailed(ContextType input) const {
    detail::ExecutionState<ContextType> state =
        execute(plan(), std::move(input), true);
    return PipelineResult<ContextType>{
        .context = std::move(state.context),
        .shortCircuited = state.shortCircuited,
        .errors = std::move(state.errors),
        .actionTimings = std::move(state.actionTimings),
        .totalNanos = elapsedNanos(state.runStarted),
    };
  }

  [[deprecated("Use run() or runDetailed().")]]
  PipelineResult<ContextType> execute(ContextType input) const {
    return runDetailed(std::move(input));
  }

 private:
  template <typename CallableType>
  static Action<ContextType> normalizeAction(CallableType callable) {
    using StoredCallable = std::decay_t<CallableType>;
    if constexpr (std::is_invocable_r_v<
                      ContextType, StoredCallable&, ContextType>) {
      return [stored = StoredCallable(std::move(callable))](
                 ContextType context) mutable -> ContextType {
        return std::invoke(stored, std::move(context));
      };
    } else if constexpr (std::is_invocable_r_v<
                             ContextType,
                             StoredCallable&,
                             ContextType,
                             ActionControl<ContextType>&>) {
      return [stored = StoredCallable(std::move(callable))](
                 ContextType context) mutable -> ContextType {
        ActionControl<ContextType> control;
        return std::invoke(stored, std::move(context), control);
      };
    } else {
      static_assert(
          std::is_invocable_v<StoredCallable&, ContextType>,
          "Action must be callable as Context(Context) or "
          "Context(Context, ActionControl<Context>&)");
    }
  }

  template <typename CallableType>
  void registerAction(
      std::vector<RegisteredAction>& destination,
      std::string actionName,
      CallableType callable) {
    Action<ContextType> action = normalizeAction(std::move(callable));
    std::lock_guard lock(assemblyMutex_);
    ensureMutableLocked();
    destination.push_back(RegisteredAction{
        .name = std::move(actionName),
        .action = std::move(action),
    });
  }

  std::shared_ptr<const PipelinePlan> plan() const {
    std::lock_guard lock(assemblyMutex_);
    if (frozenPlan_ == nullptr) {
      frozenPlan_ = std::make_shared<const PipelinePlan>(PipelinePlan{
          .pipelineName = pipelineName_,
          .shortCircuitOnException = shortCircuitOnException_,
          .errorHandler = errorHandler_,
          .observer = observer_,
          .preActions = preActions_,
          .actions = actions_,
          .postActions = postActions_,
      });
    }
    return frozenPlan_;
  }

  void ensureMutableLocked() const {
    if (frozenPlan_ != nullptr) {
      throw std::logic_error(
          "Pipeline '" + pipelineName_ + "' is frozen");
    }
  }

  static std::int64_t elapsedNanos(
      std::chrono::steady_clock::time_point started) {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
               std::chrono::steady_clock::now() - started)
        .count();
  }

  static void notify(const std::function<void()>& callback) noexcept {
    try {
      callback();
    } catch (...) {
      // Observers cannot change Pipeline semantics.
    }
  }

  static detail::ExecutionState<ContextType> execute(
      std::shared_ptr<const PipelinePlan> pipelinePlan,
      ContextType input,
      bool collectTimings) {
    detail::ExecutionState<ContextType> state(
        pipelinePlan->pipelineName,
        pipelinePlan->errorHandler,
        std::move(input),
        collectTimings);

    notify([&] {
      pipelinePlan->observer->onPipelineStarted(
          pipelinePlan->pipelineName);
    });

    std::exception_ptr pendingFailure;
    {
      detail::ExecutionScope scope(state);
      executeActions(
          *pipelinePlan,
          state,
          StepPhase::PRE,
          pipelinePlan->preActions,
          false,
          pendingFailure);
      if (pendingFailure == nullptr && !state.shortCircuited) {
        executeActions(
            *pipelinePlan,
            state,
            StepPhase::MAIN,
            pipelinePlan->actions,
            true,
            pendingFailure);
      }
      executeActions(
          *pipelinePlan,
          state,
          StepPhase::POST,
          pipelinePlan->postActions,
          false,
          pendingFailure);
    }

    notify([&] {
      pipelinePlan->observer->onPipelineCompleted(
          pipelinePlan->pipelineName,
          state.shortCircuited,
          state.errors.size(),
          elapsedNanos(state.runStarted));
    });

    if (pendingFailure != nullptr) {
      std::rethrow_exception(pendingFailure);
    }
    return state;
  }

  static void executeActions(
      const PipelinePlan& pipelinePlan,
      detail::ExecutionState<ContextType>& state,
      StepPhase phase,
      const std::vector<RegisteredAction>& registeredActions,
      bool stopOnShortCircuit,
      std::exception_ptr& pendingFailure) {
    if (pendingFailure != nullptr && phase != StepPhase::POST) {
      return;
    }

    for (std::size_t actionIndex = 0;
         actionIndex < registeredActions.size();
         ++actionIndex) {
      const RegisteredAction& registeredAction =
          registeredActions[actionIndex];
      state.phase = phase;
      state.actionIndex = actionIndex;
      state.actionName = detail::formatActionName(
          phase, actionIndex, registeredAction.name);
      const bool wasShortCircuited = state.shortCircuited;

      notify([&] {
        pipelinePlan.observer->onActionStarted(
            pipelinePlan.pipelineName,
            phase,
            actionIndex,
            state.actionName);
      });

      const auto actionStarted = std::chrono::steady_clock::now();
      bool succeeded = true;
      std::exception_ptr actionFailure;
      const ContextType contextBeforeAction = state.context;

      try {
        state.actionExecuting = true;
        try {
          state.context = registeredAction.action(std::move(state.context));
        } catch (...) {
          state.actionExecuting = false;
          throw;
        }
        state.actionExecuting = false;
      } catch (...) {
        state.actionExecuting = false;
        succeeded = false;
        actionFailure = std::current_exception();
        PipelineError pipelineError{
            .pipelineName = pipelinePlan.pipelineName,
            .phase = phase,
            .actionIndex = actionIndex,
            .actionName = state.actionName,
            .exception = actionFailure,
            .message = detail::safeExceptionToString(actionFailure),
            .stepIndex = actionIndex,
            .stepName = state.actionName,
        };
        state.errors.push_back(pipelineError);

        try {
          state.context = pipelinePlan.errorHandler(
              contextBeforeAction, state.errors.back());
        } catch (...) {
          state.context = contextBeforeAction;
          state.shortCircuited = true;
          if (pendingFailure == nullptr) {
            pendingFailure = std::make_exception_ptr(
                InvalidErrorHandlerError(
                    "onError handler raised while recovering from an Action failure",
                    actionFailure,
                    std::current_exception()));
          }
        }
        if (pipelinePlan.shortCircuitOnException) {
          state.shortCircuited = true;
        }
      }

      const std::int64_t actionElapsed =
          std::chrono::duration_cast<std::chrono::nanoseconds>(
              std::chrono::steady_clock::now() - actionStarted)
              .count();
      if (state.collectTimings) {
        state.actionTimings.push_back(ActionTiming{
            .phase = phase,
            .actionIndex = actionIndex,
            .actionName = state.actionName,
            .elapsedNanos = actionElapsed,
            .success = succeeded,
            .index = actionIndex,
        });
      }

      if (succeeded) {
        notify([&] {
          pipelinePlan.observer->onActionCompleted(
              pipelinePlan.pipelineName,
              phase,
              actionIndex,
              state.actionName,
              actionElapsed);
        });
      } else {
        notify([&] {
          pipelinePlan.observer->onActionFailed(
              pipelinePlan.pipelineName,
              phase,
              actionIndex,
              state.actionName,
              actionFailure,
              actionElapsed);
        });
      }

      if (!wasShortCircuited && state.shortCircuited) {
        notify([&] {
          pipelinePlan.observer->onShortCircuited(
              pipelinePlan.pipelineName,
              phase,
              actionIndex,
              state.actionName);
        });
      }

      if (pendingFailure != nullptr && phase != StepPhase::POST) {
        break;
      }
      if (stopOnShortCircuit && state.shortCircuited) {
        break;
      }
    }
  }

  void moveFrom(Pipeline&& other) {
    pipelineName_ = std::move(other.pipelineName_);
    shortCircuitOnException_ = other.shortCircuitOnException_;
    errorHandler_ = std::move(other.errorHandler_);
    observer_ = std::move(other.observer_);
    preActions_ = std::move(other.preActions_);
    actions_ = std::move(other.actions_);
    postActions_ = std::move(other.postActions_);
    frozenPlan_ = std::move(other.frozenPlan_);
  }

  std::string pipelineName_;
  bool shortCircuitOnException_;
  OnErrorFn<ContextType> errorHandler_;
  std::shared_ptr<PipelineObserver> observer_;
  std::vector<RegisteredAction> preActions_;
  std::vector<RegisteredAction> actions_;
  std::vector<RegisteredAction> postActions_;
  mutable std::mutex assemblyMutex_;
  mutable std::shared_ptr<const PipelinePlan> frozenPlan_;
};

inline std::string formatStepName(
    StepPhase phase,
    std::size_t index,
    const std::string& label) {
  return detail::formatActionName(phase, index, label);
}

namespace core {
using ::pipeline_services::ActionControl;
using ::pipeline_services::ActionTiming;
using ::pipeline_services::InvalidErrorHandlerError;
using ::pipeline_services::PipelineError;
using ::pipeline_services::PipelineObserver;
using ::pipeline_services::StepPhase;
using ::pipeline_services::formatStepName;
using ::pipeline_services::phaseName;
using ::pipeline_services::shortCircuit;
using ::pipeline_services::stepPhaseToString;

template <typename ContextType>
using Action = ::pipeline_services::Action<ContextType>;

template <typename ContextType>
using UnaryOperator = ::pipeline_services::UnaryOperator<ContextType>;

template <typename ContextType>
using StepAction = ::pipeline_services::StepAction<ContextType>;

template <typename ContextType>
using OnErrorFn = ::pipeline_services::OnErrorFn<ContextType>;

template <typename ContextType>
using StepControl = ::pipeline_services::StepControl<ContextType>;

template <typename ContextType>
using PipelineResult = ::pipeline_services::PipelineResult<ContextType>;

template <typename ContextType>
using Pipeline = ::pipeline_services::Pipeline<ContextType>;

template <typename ContextType>
ContextType defaultOnError(
    ContextType context,
    const PipelineError& error) {
  return ::pipeline_services::defaultOnError(
      std::move(context), error);
}
}  // namespace core

}  // namespace pipeline_services
