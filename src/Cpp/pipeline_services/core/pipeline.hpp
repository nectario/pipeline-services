#pragma once

#include <chrono>
#include <cstddef>
#include <exception>
#include <functional>
#include <optional>
#include <stdexcept>
#include <string>
#include <type_traits>
#include <utility>
#include <variant>
#include <vector>

#include "pipeline_services/remote/http_step.hpp"

namespace pipeline_services::core {

struct PipelineError {
  std::string pipeline;
  std::string phase;
  std::size_t index;
  std::string action_name;
  std::string message;
};

struct ActionTiming {
  std::string phase;
  std::size_t index;
  std::string action_name;
  std::int64_t elapsed_nanos;
  bool success;
};

template <typename ContextType>
using UnaryOperator = std::function<ContextType(ContextType)>;

template <typename ContextType>
class StepControl;

template <typename ContextType>
using StepAction = std::function<ContextType(ContextType, StepControl<ContextType>&)>;

template <typename ContextType>
using OnErrorFn = std::function<ContextType(ContextType, PipelineError)>;

template <typename ContextType>
ContextType default_on_error(ContextType ctx, PipelineError error) {
  (void)error;
  return ctx;
}

template <typename ContextType>
class StepControl {
public:
  explicit StepControl(std::string pipeline_name, OnErrorFn<ContextType> on_error = default_on_error<ContextType>)
    : pipeline_name_(std::move(pipeline_name)),
      on_error_(std::move(on_error)),
      errors_(),
      timings_(),
      short_circuited_(false),
      phase_("main"),
      index_(0),
      action_name_("?"),
      run_start_timepoint_(std::nullopt) {}

  void begin_step(std::string phase, std::size_t index, std::string action_name) {
    phase_ = std::move(phase);
    index_ = index;
    action_name_ = std::move(action_name);
  }

  void begin_run() {
    run_start_timepoint_ = std::chrono::steady_clock::now();
  }

  void reset() {
    short_circuited_ = false;
    errors_.clear();
    timings_.clear();
    phase_ = "main";
    index_ = 0;
    action_name_ = "?";
    run_start_timepoint_ = std::nullopt;
  }

  void short_circuit() {
    short_circuited_ = true;
  }

  bool is_short_circuited() const {
    return short_circuited_;
  }

  ContextType record_error(ContextType ctx, std::string message) {
    PipelineError pipeline_error{
      .pipeline = pipeline_name_,
      .phase = phase_,
      .index = index_,
      .action_name = action_name_,
      .message = std::move(message),
    };
    errors_.push_back(pipeline_error);
    return on_error_(std::move(ctx), pipeline_error);
  }

  void record_timing(std::int64_t elapsed_nanos, bool success) {
    ActionTiming timing{
      .phase = phase_,
      .index = index_,
      .action_name = action_name_,
      .elapsed_nanos = elapsed_nanos,
      .success = success,
    };
    timings_.push_back(std::move(timing));
  }

  std::int64_t run_elapsed_nanos() const {
    if (!run_start_timepoint_.has_value()) {
      return 0;
    }
    const auto now_timepoint = std::chrono::steady_clock::now();
    return std::chrono::duration_cast<std::chrono::nanoseconds>(now_timepoint - run_start_timepoint_.value()).count();
  }

  const std::string& pipeline_name() const {
    return pipeline_name_;
  }

  const std::vector<PipelineError>& errors() const {
    return errors_;
  }

  const std::vector<ActionTiming>& timings() const {
    return timings_;
  }

private:
  std::string pipeline_name_;
  OnErrorFn<ContextType> on_error_;
  std::vector<PipelineError> errors_;
  std::vector<ActionTiming> timings_;
  bool short_circuited_;

  std::string phase_;
  std::size_t index_;
  std::string action_name_;

  std::optional<std::chrono::steady_clock::time_point> run_start_timepoint_;
};

template <typename ContextType>
struct PipelineResult {
  ContextType context;
  bool short_circuited;
  std::vector<PipelineError> errors;
  std::vector<ActionTiming> timings;
  std::int64_t total_nanos;

  bool has_errors() const {
    return !errors.empty();
  }
};

template <typename ContextType>
struct RegisteredAction {
  std::string name;
  std::variant<UnaryOperator<ContextType>, StepAction<ContextType>, remote::RemoteSpec<ContextType>> action;
};

template <typename ContextType, typename CallableType>
std::variant<UnaryOperator<ContextType>, StepAction<ContextType>, remote::RemoteSpec<ContextType>> to_action_variant(
  CallableType callable
) {
  if constexpr (std::is_invocable_r_v<ContextType, CallableType, ContextType, StepControl<ContextType>&>) {
    StepAction<ContextType> step_action = std::move(callable);
    return step_action;
  } else if constexpr (std::is_invocable_r_v<ContextType, CallableType, ContextType>) {
    UnaryOperator<ContextType> unary_action = std::move(callable);
    return unary_action;
  } else {
    throw std::invalid_argument("Action must be callable as (C)->C or (C, StepControl<C>&)->C");
  }
}

inline std::string format_action_name(const std::string& phase, std::size_t index, const std::string& name) {
  std::string prefix = "s";
  if (phase == "pre") {
    prefix = "pre";
  } else if (phase == "post") {
    prefix = "post";
  }

  if (name.empty()) {
    return prefix + std::to_string(index);
  }
  return prefix + std::to_string(index) + ":" + name;
}

inline std::string format_step_name(const std::string& phase, std::size_t index, const std::string& name) {
  return format_action_name(phase, index, name);
}

inline std::string safe_error_to_string(const std::exception& error) {
  return error.what() ? std::string(error.what()) : std::string("exception");
}

inline std::string safe_unknown_exception_to_string() {
  return "unknown exception";
}

template <typename ContextType>
class Pipeline {
public:
  Pipeline(std::string name, bool short_circuit_on_exception = true)
    : name_(std::move(name)),
      short_circuit_on_exception_(short_circuit_on_exception),
      on_error_(default_on_error<ContextType>),
      pre_actions_(),
      actions_(),
      post_actions_() {
    static_assert(std::is_copy_constructible_v<ContextType>, "Pipeline requires CopyConstructible context types");
  }

  const std::string& name() const {
    return name_;
  }

  bool short_circuit_on_exception() const {
    return short_circuit_on_exception_;
  }

  Pipeline& on_error_handler(OnErrorFn<ContextType> handler) {
    on_error_ = std::move(handler);
    return *this;
  }

  template <typename CallableType>
  Pipeline& add_pre_action(CallableType callable) {
    return add_pre_action_named("", std::move(callable));
  }

  template <typename CallableType>
  Pipeline& add_pre_action_named(std::string name, CallableType callable) {
    pre_actions_.push_back(RegisteredAction<ContextType>{
      .name = std::move(name),
      .action = to_action_variant<ContextType>(std::move(callable)),
    });
    return *this;
  }

  Pipeline& add_pre_action(const remote::RemoteSpec<ContextType>& spec) {
    return add_pre_action_named("", spec);
  }

  Pipeline& add_pre_action_named(std::string name, const remote::RemoteSpec<ContextType>& spec) {
    pre_actions_.push_back(RegisteredAction<ContextType>{
      .name = std::move(name),
      .action = spec,
    });
    return *this;
  }

  template <typename CallableType>
  Pipeline& add_action(CallableType callable) {
    return add_action_named("", std::move(callable));
  }

  template <typename CallableType>
  Pipeline& add_action_named(std::string name, CallableType callable) {
    actions_.push_back(RegisteredAction<ContextType>{
      .name = std::move(name),
      .action = to_action_variant<ContextType>(std::move(callable)),
    });
    return *this;
  }

  Pipeline& add_action(const remote::RemoteSpec<ContextType>& spec) {
    return add_action_named("", spec);
  }

  Pipeline& add_action_named(std::string name, const remote::RemoteSpec<ContextType>& spec) {
    actions_.push_back(RegisteredAction<ContextType>{
      .name = std::move(name),
      .action = spec,
    });
    return *this;
  }

  template <typename CallableType>
  Pipeline& add_post_action(CallableType callable) {
    return add_post_action_named("", std::move(callable));
  }

  template <typename CallableType>
  Pipeline& add_post_action_named(std::string name, CallableType callable) {
    post_actions_.push_back(RegisteredAction<ContextType>{
      .name = std::move(name),
      .action = to_action_variant<ContextType>(std::move(callable)),
    });
    return *this;
  }

  Pipeline& add_post_action(const remote::RemoteSpec<ContextType>& spec) {
    return add_post_action_named("", spec);
  }

  Pipeline& add_post_action_named(std::string name, const remote::RemoteSpec<ContextType>& spec) {
    post_actions_.push_back(RegisteredAction<ContextType>{
      .name = std::move(name),
      .action = spec,
    });
    return *this;
  }

  ContextType run(ContextType input_value) const {
    return execute(std::move(input_value)).context;
  }

  PipelineResult<ContextType> execute(ContextType input_value) const {
    ContextType ctx = std::move(input_value);
    StepControl<ContextType> control(name_, on_error_);
    control.begin_run();

    ctx = run_phase("pre", std::move(ctx), pre_actions_, control, false);
    if (!control.is_short_circuited()) {
      ctx = run_phase("main", std::move(ctx), actions_, control, true);
    }
    ctx = run_phase("post", std::move(ctx), post_actions_, control, false);

    const std::int64_t total_nanos = control.run_elapsed_nanos();
    PipelineResult<ContextType> result{
      .context = std::move(ctx),
      .short_circuited = control.is_short_circuited(),
      .errors = control.errors(),
      .timings = control.timings(),
      .total_nanos = total_nanos,
    };
    return result;
  }

  Pipeline& add_registered_pre_action(const RegisteredAction<ContextType>& registered_action) {
    pre_actions_.push_back(registered_action);
    return *this;
  }

  Pipeline& add_registered_action(const RegisteredAction<ContextType>& registered_action) {
    actions_.push_back(registered_action);
    return *this;
  }

  Pipeline& add_registered_post_action(const RegisteredAction<ContextType>& registered_action) {
    post_actions_.push_back(registered_action);
    return *this;
  }

private:
  ContextType run_phase(
    const std::string& phase,
    ContextType start_ctx,
    const std::vector<RegisteredAction<ContextType>>& actions,
    StepControl<ContextType>& control,
    bool stop_on_short_circuit
  ) const {
    ContextType ctx = std::move(start_ctx);
    std::size_t step_index = 0;
    while (step_index < actions.size()) {
      const RegisteredAction<ContextType>& registered_action = actions.at(step_index);
      const std::string action_name = format_action_name(phase, step_index, registered_action.name);
      control.begin_step(phase, step_index, action_name);

      const auto step_start_timepoint = std::chrono::steady_clock::now();
      bool step_succeeded = true;

      const ContextType ctx_before_step = ctx;
      try {
        if (std::holds_alternative<UnaryOperator<ContextType>>(registered_action.action)) {
          const UnaryOperator<ContextType>& unary_action = std::get<UnaryOperator<ContextType>>(registered_action.action);
          ctx = unary_action(ctx);
        } else if (std::holds_alternative<StepAction<ContextType>>(registered_action.action)) {
          const StepAction<ContextType>& step_action = std::get<StepAction<ContextType>>(registered_action.action);
          ctx = step_action(ctx, control);
        } else {
          const remote::RemoteSpec<ContextType>& remote_spec =
            std::get<remote::RemoteSpec<ContextType>>(registered_action.action);
          ctx = remote::http_step(remote_spec, ctx);
        }
      } catch (const std::exception& error) {
        step_succeeded = false;
        ctx = control.record_error(ctx_before_step, safe_error_to_string(error));
        if (short_circuit_on_exception_) {
          control.short_circuit();
        }
      } catch (...) {
        step_succeeded = false;
        ctx = control.record_error(ctx_before_step, safe_unknown_exception_to_string());
        if (short_circuit_on_exception_) {
          control.short_circuit();
        }
      }

      const auto step_end_timepoint = std::chrono::steady_clock::now();
      const auto elapsed_nanos =
        std::chrono::duration_cast<std::chrono::nanoseconds>(step_end_timepoint - step_start_timepoint).count();
      control.record_timing(elapsed_nanos, step_succeeded);

      if (stop_on_short_circuit && control.is_short_circuited()) {
        break;
      }

      step_index += 1;
    }

    return ctx;
  }

  std::string name_;
  bool short_circuit_on_exception_;
  OnErrorFn<ContextType> on_error_;

  std::vector<RegisteredAction<ContextType>> pre_actions_;
  std::vector<RegisteredAction<ContextType>> actions_;
  std::vector<RegisteredAction<ContextType>> post_actions_;
};

}  // namespace pipeline_services::core
