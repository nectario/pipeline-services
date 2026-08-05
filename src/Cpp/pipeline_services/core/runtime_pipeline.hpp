#pragma once

#include <cstddef>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#include "pipeline_services/core/pipeline.hpp"

namespace pipeline_services {

/**
 * Deprecated immediate-execution helper retained for preview compatibility.
 * Every applied Action is executed through the canonical Pipeline runner.
 */
template <typename ContextType>
class RuntimePipeline {
 private:
  struct RecordedAction {
    std::string name;
    Action<ContextType> action;
  };

 public:
  RuntimePipeline(
      std::string pipelineName,
      bool shortCircuitOnException,
      ContextType initialContext)
      : pipelineName_(std::move(pipelineName)),
        shortCircuitOnException_(shortCircuitOnException),
        currentContext_(std::move(initialContext)) {}

  const ContextType& value() const noexcept {
    return currentContext_;
  }

  void reset(ContextType context) {
    currentContext_ = std::move(context);
    ended_ = false;
  }

  template <typename CallableType>
  const ContextType& addPreAction(CallableType callable) {
    return addAndApply(
        preActions_, StepPhase::PRE, std::move(callable));
  }

  template <typename CallableType>
  const ContextType& addAction(CallableType callable) {
    if (ended_) {
      return currentContext_;
    }
    return addAndApply(
        actions_, StepPhase::MAIN, std::move(callable));
  }

  template <typename CallableType>
  const ContextType& addPostAction(CallableType callable) {
    return addAndApply(
        postActions_, StepPhase::POST, std::move(callable));
  }

  Pipeline<ContextType> freeze() const {
    return toImmutable();
  }

  Pipeline<ContextType> toImmutable() const {
    Pipeline<ContextType> pipeline(
        pipelineName_, shortCircuitOnException_);
    for (const auto& action : preActions_) {
      pipeline.addPreAction(action.name, action.action);
    }
    for (const auto& action : actions_) {
      pipeline.addAction(action.name, action.action);
    }
    for (const auto& action : postActions_) {
      pipeline.addPostAction(action.name, action.action);
    }
    pipeline.freeze();
    return pipeline;
  }

 private:
  template <typename CallableType>
  static Action<ContextType> normalize(CallableType callable) {
    using StoredCallable = std::decay_t<CallableType>;
    if constexpr (std::is_invocable_r_v<
                      ContextType, StoredCallable&, ContextType>) {
      return [stored = StoredCallable(std::move(callable))](
                 ContextType context) mutable {
        return std::invoke(stored, std::move(context));
      };
    } else if constexpr (std::is_invocable_r_v<
                             ContextType,
                             StoredCallable&,
                             ContextType,
                             ActionControl<ContextType>&>) {
      return [stored = StoredCallable(std::move(callable))](
                 ContextType context) mutable {
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
  const ContextType& addAndApply(
      std::vector<RecordedAction>& destination,
      StepPhase phase,
      CallableType callable) {
    Action<ContextType> action = normalize(std::move(callable));
    destination.push_back(RecordedAction{"", action});

    Pipeline<ContextType> oneAction(
        pipelineName_ + ":runtime", shortCircuitOnException_);
    if (phase == StepPhase::PRE) {
      oneAction.addPreAction(action);
    } else if (phase == StepPhase::POST) {
      oneAction.addPostAction(action);
    } else {
      oneAction.addAction(action);
    }
    PipelineResult<ContextType> result =
        oneAction.runDetailed(currentContext_);
    currentContext_ = std::move(result.context);
    if (phase == StepPhase::MAIN && result.shortCircuited) {
      ended_ = true;
    }
    return currentContext_;
  }

  std::string pipelineName_;
  bool shortCircuitOnException_;
  ContextType currentContext_;
  bool ended_ = false;
  std::vector<RecordedAction> preActions_;
  std::vector<RecordedAction> actions_;
  std::vector<RecordedAction> postActions_;
};

namespace core {
template <typename ContextType>
using RuntimePipeline = ::pipeline_services::RuntimePipeline<ContextType>;
}  // namespace core

}  // namespace pipeline_services
