#pragma once

#include <stdexcept>
#include <string>
#include <unordered_map>
#include <utility>

#include "pipeline_services/core/pipeline.hpp"

namespace pipeline_services {

template <typename ContextType>
class PipelineRegistry {
 public:
  void registerUnary(
      const std::string& name,
      UnaryOperator<ContextType> action) {
    registerAction(name, std::move(action));
  }

  void registerAction(
      const std::string& name,
      Action<ContextType> action) {
    if (!action) {
      throw std::invalid_argument("action must not be empty");
    }
    actions_[name] = std::move(action);
  }

  void registerLegacyAction(
      const std::string& name,
      StepAction<ContextType> action) {
    if (!action) {
      throw std::invalid_argument("action must not be empty");
    }
    legacyActions_[name] = std::move(action);
  }

  bool hasUnary(const std::string& name) const {
    return actions_.contains(name);
  }

  bool hasAction(const std::string& name) const {
    return actions_.contains(name);
  }

  bool hasLegacyAction(const std::string& name) const {
    return legacyActions_.contains(name);
  }

  UnaryOperator<ContextType> getUnary(const std::string& name) const {
    return getAction(name);
  }

  Action<ContextType> getAction(const std::string& name) const {
    const auto found = actions_.find(name);
    if (found == actions_.end()) {
      throw std::runtime_error("Unknown Action: " + name);
    }
    return found->second;
  }

  StepAction<ContextType> getLegacyAction(const std::string& name) const {
    const auto found = legacyActions_.find(name);
    if (found == legacyActions_.end()) {
      throw std::runtime_error("Unknown legacy Action: " + name);
    }
    return found->second;
  }

 private:
  std::unordered_map<std::string, Action<ContextType>> actions_;
  std::unordered_map<std::string, StepAction<ContextType>> legacyActions_;
};

namespace core {
template <typename ContextType>
using PipelineRegistry = ::pipeline_services::PipelineRegistry<ContextType>;
}  // namespace core

}  // namespace pipeline_services
