#pragma once

#include <functional>
#include <string>
#include <unordered_map>

#include "pipeline_services/core/pipeline.hpp"

namespace pipeline_services::core {

template <typename ContextType>
class PipelineRegistry {
public:
  PipelineRegistry() = default;

  void registerUnary(const std::string& name, UnaryOperator<ContextType> action) {
    unaryActions_[name] = std::move(action);
  }

  void registerAction(const std::string& name, StepAction<ContextType> action) {
    stepActions_[name] = std::move(action);
  }

  bool hasUnary(const std::string& name) const {
    return unaryActions_.contains(name);
  }

  bool hasAction(const std::string& name) const {
    return stepActions_.contains(name);
  }

  UnaryOperator<ContextType> getUnary(const std::string& name) const {
    const auto iter = unaryActions_.find(name);
    if (iter == unaryActions_.end()) {
      throw std::runtime_error("Unknown unary action: " + name);
    }
    return iter->second;
  }

  StepAction<ContextType> getAction(const std::string& name) const {
    const auto iter = stepActions_.find(name);
    if (iter == stepActions_.end()) {
      throw std::runtime_error("Unknown step action: " + name);
    }
    return iter->second;
  }

private:
  std::unordered_map<std::string, UnaryOperator<ContextType>> unaryActions_;
  std::unordered_map<std::string, StepAction<ContextType>> stepActions_;
};

}  // namespace pipeline_services::core

