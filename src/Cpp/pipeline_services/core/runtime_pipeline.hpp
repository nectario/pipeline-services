#pragma once

#include <cstddef>
#include <string>
#include <utility>
#include <vector>

#include "pipeline_services/core/pipeline.hpp"

namespace pipeline_services::core {

template <typename ContextType>
class RuntimePipeline {
public:
  RuntimePipeline(std::string name, bool shortCircuitOnException, ContextType initial)
    : name_(std::move(name)),
      shortCircuitOnException_(shortCircuitOnException),
      ended_(false),
      current_(std::move(initial)),
      preActions_(),
      actions_(),
      postActions_(),
      preIndex_(0),
      actionIndex_(0),
      postIndex_(0),
      control_(name_, defaultOnError<ContextType>) {}

  const ContextType& value() const {
    return current_;
  }

  void reset(ContextType value) {
    current_ = std::move(value);
    ended_ = false;
    control_.reset();
  }

  template <typename CallableType>
  const ContextType& addPreAction(CallableType callable) {
    if (ended_) {
      return current_;
    }
    RegisteredAction registeredAction{
      .name = "",
      .action = toStepAction<ContextType>(std::move(callable)),
    };
    preActions_.push_back(registeredAction);
    const std::size_t indexValue = preIndex_;
    preIndex_ += 1;
    return applyAction(registeredAction, StepPhase::PRE, indexValue);
  }

  Pipeline<ContextType> freeze() const {
    return toImmutable();
  }

  Pipeline<ContextType> toImmutable() const {
    Pipeline<ContextType> pipeline(name_, shortCircuitOnException_);
    pipeline.onError(defaultOnError<ContextType>);
    for (const auto& registeredAction : preActions_) {
      pipeline.addPreAction(registeredAction.name, registeredAction.action);
    }
    for (const auto& registeredAction : actions_) {
      pipeline.addAction(registeredAction.name, registeredAction.action);
    }
    for (const auto& registeredAction : postActions_) {
      pipeline.addPostAction(registeredAction.name, registeredAction.action);
    }
    return pipeline;
  }

  template <typename CallableType>
  const ContextType& addAction(CallableType callable) {
    if (ended_) {
      return current_;
    }
    RegisteredAction registeredAction{
      .name = "",
      .action = toStepAction<ContextType>(std::move(callable)),
    };
    actions_.push_back(registeredAction);
    const std::size_t indexValue = actionIndex_;
    actionIndex_ += 1;
    return applyAction(registeredAction, StepPhase::MAIN, indexValue);
  }

  template <typename CallableType>
  const ContextType& addPostAction(CallableType callable) {
    if (ended_) {
      return current_;
    }
    RegisteredAction registeredAction{
      .name = "",
      .action = toStepAction<ContextType>(std::move(callable)),
    };
    postActions_.push_back(registeredAction);
    const std::size_t indexValue = postIndex_;
    postIndex_ += 1;
    return applyAction(registeredAction, StepPhase::POST, indexValue);
  }

private:
  struct RegisteredAction {
    std::string name;
    StepAction<ContextType> action;
  };

  const ContextType& applyAction(
    const RegisteredAction& registeredAction,
    StepPhase phase,
    std::size_t index
  ) {
    const std::string stepName = formatStepName(phase, index, registeredAction.name);
    control_.beginStep(phase, index, stepName);
    const ContextType ctxBeforeStep = current_;
    try {
      current_ = registeredAction.action(std::move(current_), control_);
    } catch (...) {
      current_ = control_.recordError(ctxBeforeStep, std::current_exception());
      if (shortCircuitOnException_) {
        control_.shortCircuit();
        ended_ = true;
      }
    }

    if (control_.isShortCircuited()) {
      ended_ = true;
    }

    return current_;
  }

  std::string name_;
  bool shortCircuitOnException_;
  bool ended_;
  ContextType current_;

  std::vector<RegisteredAction> preActions_;
  std::vector<RegisteredAction> actions_;
  std::vector<RegisteredAction> postActions_;

  std::size_t preIndex_;
  std::size_t actionIndex_;
  std::size_t postIndex_;

  ActionControl<ContextType> control_;
};

}  // namespace pipeline_services::core
