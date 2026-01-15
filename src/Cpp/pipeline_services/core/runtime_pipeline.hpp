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
  RuntimePipeline(std::string name, bool short_circuit_on_exception, ContextType initial)
    : name_(std::move(name)),
      short_circuit_on_exception_(short_circuit_on_exception),
      ended_(false),
      current_(std::move(initial)),
      pre_actions_(),
      actions_(),
      post_actions_(),
      pre_index_(0),
      action_index_(0),
      post_index_(0),
      control_(name_, default_on_error<ContextType>) {}

  const ContextType& value() const {
    return current_;
  }

  void reset(ContextType value) {
    current_ = std::move(value);
    ended_ = false;
    control_.reset();
  }

  template <typename CallableType>
  const ContextType& add_pre_action(CallableType callable) {
    if (ended_) {
      return current_;
    }
    RegisteredAction<ContextType> registered_action{
      .name = "",
      .action = to_action_variant<ContextType>(std::move(callable)),
    };
    pre_actions_.push_back(registered_action);
    const std::size_t index_value = pre_index_;
    pre_index_ += 1;
    return apply_action(registered_action, "pre", index_value);
  }

  const ContextType& add_pre_action(const remote::RemoteSpec<ContextType>& spec) {
    if (ended_) {
      return current_;
    }
    RegisteredAction<ContextType> registered_action{
      .name = "",
      .action = spec,
    };
    pre_actions_.push_back(registered_action);
    const std::size_t index_value = pre_index_;
    pre_index_ += 1;
    return apply_action(registered_action, "pre", index_value);
  }

  Pipeline<ContextType> freeze() const {
    return to_immutable();
  }

  Pipeline<ContextType> to_immutable() const {
    Pipeline<ContextType> pipeline(name_, short_circuit_on_exception_);
    pipeline.on_error_handler(default_on_error<ContextType>);
    for (const auto& registered_action : pre_actions_) {
      pipeline.add_registered_pre_action(registered_action);
    }
    for (const auto& registered_action : actions_) {
      pipeline.add_registered_action(registered_action);
    }
    for (const auto& registered_action : post_actions_) {
      pipeline.add_registered_post_action(registered_action);
    }
    return pipeline;
  }

  template <typename CallableType>
  const ContextType& add_action(CallableType callable) {
    if (ended_) {
      return current_;
    }
    RegisteredAction<ContextType> registered_action{
      .name = "",
      .action = to_action_variant<ContextType>(std::move(callable)),
    };
    actions_.push_back(registered_action);
    const std::size_t index_value = action_index_;
    action_index_ += 1;
    return apply_action(registered_action, "main", index_value);
  }

  const ContextType& add_action(const remote::RemoteSpec<ContextType>& spec) {
    if (ended_) {
      return current_;
    }
    RegisteredAction<ContextType> registered_action{
      .name = "",
      .action = spec,
    };
    actions_.push_back(registered_action);
    const std::size_t index_value = action_index_;
    action_index_ += 1;
    return apply_action(registered_action, "main", index_value);
  }

  template <typename CallableType>
  const ContextType& add_post_action(CallableType callable) {
    if (ended_) {
      return current_;
    }
    RegisteredAction<ContextType> registered_action{
      .name = "",
      .action = to_action_variant<ContextType>(std::move(callable)),
    };
    post_actions_.push_back(registered_action);
    const std::size_t index_value = post_index_;
    post_index_ += 1;
    return apply_action(registered_action, "post", index_value);
  }

  const ContextType& add_post_action(const remote::RemoteSpec<ContextType>& spec) {
    if (ended_) {
      return current_;
    }
    RegisteredAction<ContextType> registered_action{
      .name = "",
      .action = spec,
    };
    post_actions_.push_back(registered_action);
    const std::size_t index_value = post_index_;
    post_index_ += 1;
    return apply_action(registered_action, "post", index_value);
  }

private:
  const ContextType& apply_action(
    const RegisteredAction<ContextType>& registered_action,
    const std::string& phase,
    std::size_t index
  ) {
    const std::string step_name = format_step_name(phase, index, registered_action.name);
    control_.begin_step(phase, index, step_name);
    const ContextType ctx_before_step = current_;
    try {
      if (std::holds_alternative<UnaryOperator<ContextType>>(registered_action.action)) {
        const UnaryOperator<ContextType>& unary_action = std::get<UnaryOperator<ContextType>>(registered_action.action);
        current_ = unary_action(current_);
      } else if (std::holds_alternative<StepAction<ContextType>>(registered_action.action)) {
        const StepAction<ContextType>& step_action = std::get<StepAction<ContextType>>(registered_action.action);
        current_ = step_action(current_, control_);
      } else {
        const remote::RemoteSpec<ContextType>& remote_spec =
          std::get<remote::RemoteSpec<ContextType>>(registered_action.action);
        current_ = remote::http_step(remote_spec, current_);
      }
    } catch (const std::exception& error) {
      current_ = control_.record_error(ctx_before_step, safe_error_to_string(error));
      if (short_circuit_on_exception_) {
        control_.short_circuit();
        ended_ = true;
      }
    } catch (...) {
      current_ = control_.record_error(ctx_before_step, safe_unknown_exception_to_string());
      if (short_circuit_on_exception_) {
        control_.short_circuit();
        ended_ = true;
      }
    }

    if (control_.is_short_circuited()) {
      ended_ = true;
    }

    return current_;
  }

  std::string name_;
  bool short_circuit_on_exception_;
  bool ended_;
  ContextType current_;

  std::vector<RegisteredAction<ContextType>> pre_actions_;
  std::vector<RegisteredAction<ContextType>> actions_;
  std::vector<RegisteredAction<ContextType>> post_actions_;

  std::size_t pre_index_;
  std::size_t action_index_;
  std::size_t post_index_;

  StepControl<ContextType> control_;
};

}  // namespace pipeline_services::core
