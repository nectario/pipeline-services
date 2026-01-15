#pragma once

#include <optional>
#include <stdexcept>
#include <utility>

namespace pipeline_services::prompt {

template <typename PromptSpecType>
class PromptStep {
public:
  explicit PromptStep(PromptSpecType prompt_spec) : prompt_spec_(std::move(prompt_spec)) {}

  template <typename InputType, typename OutputType, typename AdapterType>
  OutputType run(InputType input_value, std::optional<AdapterType> adapter) const {
    if (!adapter.has_value()) {
      throw std::runtime_error("No prompt adapter provided");
    }
    return adapter.value()(std::move(input_value), prompt_spec_);
  }

private:
  PromptSpecType prompt_spec_;
};

}  // namespace pipeline_services::prompt
