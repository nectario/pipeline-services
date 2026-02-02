#pragma once

#include <chrono>
#include <cstddef>
#include <cstdint>
#include <exception>
#include <functional>
#include <optional>
#include <stdexcept>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

namespace pipeline_services::core {

enum class StepPhase {
  PRE,
  MAIN,
  POST,
};

inline std::string stepPhaseToString(StepPhase phase) {
  if (phase == StepPhase::PRE) {
    return "pre";
  }
  if (phase == StepPhase::POST) {
    return "post";
  }
  return "main";
}

struct PipelineError {
  std::string pipelineName;
  StepPhase phase;
  std::size_t stepIndex;
  std::string stepName;
  std::string message;
  std::exception_ptr exception;
};

struct ActionTiming {
  StepPhase phase;
  std::size_t index;
  std::string actionName;
  std::int64_t elapsedNanos;
  bool success;
};

template <typename ContextType>
using UnaryOperator = std::function<ContextType(ContextType)>;

template <typename ContextType>
class ActionControl;

template <typename ContextType>
using StepAction = std::function<ContextType(ContextType, ActionControl<ContextType>&)>;

template <typename ContextType>
using OnErrorFn = std::function<ContextType(ContextType, const PipelineError&)>;

template <typename ContextType>
ContextType defaultOnError(ContextType ctx, const PipelineError& error) {
  (void)error;
  return ctx;
}

inline std::string safeExceptionToString(std::exception_ptr exception) {
  if (!exception) {
    return "unknown exception";
  }

  try {
    std::rethrow_exception(exception);
  } catch (const std::exception& error) {
    return error.what() ? std::string(error.what()) : std::string("exception");
  } catch (...) {
    return "unknown exception";
  }
}

inline std::string formatStepName(StepPhase phase, std::size_t index, const std::string& label) {
  std::string prefix = "s";
  if (phase == StepPhase::PRE) {
    prefix = "pre";
  } else if (phase == StepPhase::POST) {
    prefix = "post";
  }

  if (label.empty()) {
    return prefix + std::to_string(index);
  }
  return prefix + std::to_string(index) + ":" + label;
}

template <typename ContextType>
class ActionControl {
public:
  explicit ActionControl(std::string pipelineName, OnErrorFn<ContextType> onError = defaultOnError<ContextType>)
    : pipelineName_(std::move(pipelineName)),
      onError_(std::move(onError)),
      errors_(),
      actionTimings_(),
      shortCircuited_(false),
      phase_(StepPhase::MAIN),
      index_(0),
      stepName_("?"),
      runStartTimepoint_() {}

  void shortCircuit() {
    shortCircuited_ = true;
  }

  bool isShortCircuited() const {
    return shortCircuited_;
  }

  ContextType recordError(ContextType ctx, std::exception_ptr exception) {
    PipelineError pipelineError{
      .pipelineName = pipelineName_,
      .phase = phase_,
      .stepIndex = index_,
      .stepName = stepName_,
      .message = safeExceptionToString(exception),
      .exception = exception,
    };
    errors_.push_back(std::move(pipelineError));
    return onError_(std::move(ctx), errors_.back());
  }

  const std::vector<PipelineError>& errors() const {
    return errors_;
  }

  const std::string& pipelineName() const {
    return pipelineName_;
  }

  std::int64_t runStartNanos() const {
    if (!runStartTimepoint_.has_value()) {
      return 0;
    }
    return std::chrono::duration_cast<std::chrono::nanoseconds>(runStartTimepoint_.value().time_since_epoch()).count();
  }

  std::int64_t runElapsedNanos() const {
    if (!runStartTimepoint_.has_value()) {
      return 0;
    }
    const auto nowTimepoint = std::chrono::steady_clock::now();
    return std::chrono::duration_cast<std::chrono::nanoseconds>(nowTimepoint - runStartTimepoint_.value()).count();
  }

  const std::vector<ActionTiming>& actionTimings() const {
    return actionTimings_;
  }

private:
  template <typename>
  friend class Pipeline;

  template <typename>
  friend class RuntimePipeline;

  void beginRun(std::chrono::steady_clock::time_point startTimepoint) {
    runStartTimepoint_ = startTimepoint;
  }

  void beginStep(StepPhase phase, std::size_t index, std::string stepName) {
    phase_ = phase;
    index_ = index;
    stepName_ = std::move(stepName);
  }

  void reset() {
    shortCircuited_ = false;
    errors_.clear();
    actionTimings_.clear();
    phase_ = StepPhase::MAIN;
    index_ = 0;
    stepName_ = "?";
    runStartTimepoint_.reset();
  }

  void recordTiming(std::int64_t elapsedNanos, bool success) {
    ActionTiming timing{
      .phase = phase_,
      .index = index_,
      .actionName = stepName_,
      .elapsedNanos = elapsedNanos,
      .success = success,
    };
    actionTimings_.push_back(std::move(timing));
  }

  std::string pipelineName_;
  OnErrorFn<ContextType> onError_;
  std::vector<PipelineError> errors_;
  std::vector<ActionTiming> actionTimings_;
  bool shortCircuited_;

  StepPhase phase_;
  std::size_t index_;
  std::string stepName_;

  std::optional<std::chrono::steady_clock::time_point> runStartTimepoint_;
};

template <typename ContextType>
using StepControl = ActionControl<ContextType>;

template <typename ContextType>
struct PipelineResult {
  ContextType context;
  bool shortCircuited;
  std::vector<PipelineError> errors;
  std::vector<ActionTiming> actionTimings;
  std::int64_t totalNanos;

  bool hasErrors() const {
    return !errors.empty();
  }
};

template <typename ContextType>
struct UnaryAdapter {
  UnaryOperator<ContextType> unary;

  ContextType operator()(ContextType ctx, ActionControl<ContextType>& control) const {
    (void)control;
    return unary(std::move(ctx));
  }
};

template <typename ContextType, typename CallableType>
StepAction<ContextType> toStepAction(CallableType callable) {
  if constexpr (std::is_invocable_r_v<ContextType, CallableType, ContextType, ActionControl<ContextType>&>) {
    StepAction<ContextType> stepAction = std::move(callable);
    return stepAction;
  } else if constexpr (std::is_invocable_r_v<ContextType, CallableType, ContextType>) {
    UnaryOperator<ContextType> unaryAction = std::move(callable);
    UnaryAdapter<ContextType> adapter{.unary = std::move(unaryAction)};
    StepAction<ContextType> stepAction = std::move(adapter);
    return stepAction;
  } else {
    throw std::invalid_argument("Action must be callable as (C)->C or (C, ActionControl<C>&)->C");
  }
}

template <typename ContextType>
class Pipeline {
public:
  Pipeline(std::string name, bool shortCircuitOnException = true)
    : name_(std::move(name)),
      shortCircuitOnException_(shortCircuitOnException),
      onError_(defaultOnError<ContextType>),
      preActions_(),
      actions_(),
      postActions_() {
    static_assert(std::is_copy_constructible_v<ContextType>, "Pipeline requires CopyConstructible context types");
  }

  const std::string& name() const {
    return name_;
  }

  bool shortCircuitOnException() const {
    return shortCircuitOnException_;
  }

  std::size_t size() const {
    return actions_.size();
  }

  Pipeline& onError(OnErrorFn<ContextType> handler) {
    onError_ = std::move(handler);
    return *this;
  }

  template <typename CallableType>
  Pipeline& addPreAction(CallableType callable) {
    return addPreAction("", std::move(callable));
  }

  template <typename CallableType>
  Pipeline& addPreAction(std::string actionName, CallableType callable) {
    preActions_.push_back(RegisteredAction{
      .name = std::move(actionName),
      .action = toStepAction<ContextType>(std::move(callable)),
    });
    return *this;
  }

  template <typename CallableType>
  Pipeline& addAction(CallableType callable) {
    return addAction("", std::move(callable));
  }

  template <typename CallableType>
  Pipeline& addAction(std::string actionName, CallableType callable) {
    actions_.push_back(RegisteredAction{
      .name = std::move(actionName),
      .action = toStepAction<ContextType>(std::move(callable)),
    });
    return *this;
  }

  template <typename CallableType>
  Pipeline& addPostAction(CallableType callable) {
    return addPostAction("", std::move(callable));
  }

  template <typename CallableType>
  Pipeline& addPostAction(std::string actionName, CallableType callable) {
    postActions_.push_back(RegisteredAction{
      .name = std::move(actionName),
      .action = toStepAction<ContextType>(std::move(callable)),
    });
    return *this;
  }

  PipelineResult<ContextType> run(ContextType input_value) const {
    ContextType ctx = std::move(input_value);

    const auto runStartTimepoint = std::chrono::steady_clock::now();
    ActionControl<ContextType> control(name_, onError_);
    control.beginRun(runStartTimepoint);

    ctx = runPhase(control, StepPhase::PRE, std::move(ctx), preActions_, false);
    if (!control.isShortCircuited()) {
      ctx = runPhase(control, StepPhase::MAIN, std::move(ctx), actions_, true);
    }
    ctx = runPhase(control, StepPhase::POST, std::move(ctx), postActions_, false);

    const auto totalNanos =
      std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - runStartTimepoint).count();
    PipelineResult<ContextType> result{
      .context = std::move(ctx),
      .shortCircuited = control.isShortCircuited(),
      .errors = control.errors(),
      .actionTimings = control.actionTimings(),
      .totalNanos = totalNanos,
    };
    return result;
  }

  // Backwards-compatible alias
  PipelineResult<ContextType> execute(ContextType input_value) const {
    return run(std::move(input_value));
  }

private:
  struct RegisteredAction {
    std::string name;
    StepAction<ContextType> action;
  };

  ContextType runPhase(
    ActionControl<ContextType>& control,
    StepPhase phase,
    ContextType startContext,
    const std::vector<RegisteredAction>& actions,
    bool stopOnShortCircuit
  ) const {
    ContextType ctx = std::move(startContext);
    for (std::size_t index = 0; index < actions.size(); index += 1) {
      const RegisteredAction& registeredAction = actions.at(index);
      const std::string stepName = formatStepName(phase, index, registeredAction.name);
      control.beginStep(phase, index, stepName);

      const auto stepStartTimepoint = std::chrono::steady_clock::now();
      bool actionSucceeded = true;

      const ContextType ctxBeforeStep = ctx;
      try {
        ctx = registeredAction.action(std::move(ctx), control);
      } catch (...) {
        actionSucceeded = false;
        ctx = control.recordError(ctxBeforeStep, std::current_exception());
        if (shortCircuitOnException_) {
          control.shortCircuit();
        }
      }

      const auto stepEndTimepoint = std::chrono::steady_clock::now();
      const auto elapsedNanos =
        std::chrono::duration_cast<std::chrono::nanoseconds>(stepEndTimepoint - stepStartTimepoint).count();
      control.recordTiming(elapsedNanos, actionSucceeded);

      if (stopOnShortCircuit && control.isShortCircuited()) {
        break;
      }
    }

    return ctx;
  }

  std::string name_;
  bool shortCircuitOnException_;
  OnErrorFn<ContextType> onError_;

  std::vector<RegisteredAction> preActions_;
  std::vector<RegisteredAction> actions_;
  std::vector<RegisteredAction> postActions_;
};

}  // namespace pipeline_services::core
