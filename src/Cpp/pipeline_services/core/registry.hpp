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

  void register_unary(const std::string& name, UnaryOperator<ContextType> action) {
    unary_actions_[name] = std::move(action);
  }

  void register_action(const std::string& name, StepAction<ContextType> action) {
    step_actions_[name] = std::move(action);
  }

  bool has_unary(const std::string& name) const {
    return unary_actions_.contains(name);
  }

  bool has_action(const std::string& name) const {
    return step_actions_.contains(name);
  }

  UnaryOperator<ContextType> get_unary(const std::string& name) const {
    const auto iter = unary_actions_.find(name);
    if (iter == unary_actions_.end()) {
      throw std::runtime_error("Unknown unary action: " + name);
    }
    return iter->second;
  }

  StepAction<ContextType> get_action(const std::string& name) const {
    const auto iter = step_actions_.find(name);
    if (iter == step_actions_.end()) {
      throw std::runtime_error("Unknown step action: " + name);
    }
    return iter->second;
  }

private:
  std::unordered_map<std::string, UnaryOperator<ContextType>> unary_actions_;
  std::unordered_map<std::string, StepAction<ContextType>> step_actions_;
};

}  // namespace pipeline_services::core

