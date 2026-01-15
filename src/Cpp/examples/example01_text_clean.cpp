#include <iostream>
#include <string>

#include "pipeline_services/core/pipeline.hpp"
#include "pipeline_services/examples/text_steps.hpp"

std::string truncate_at_280(std::string text_value, pipeline_services::core::StepControl<std::string>& control) {
  if (text_value.size() <= 280) {
    return text_value;
  }
  control.short_circuit();
  return text_value.substr(0, 280);
}

int main() {
  pipeline_services::core::Pipeline<std::string> pipeline("example01_text_clean", true);
  pipeline.add_action(pipeline_services::examples::strip);
  pipeline.add_action(pipeline_services::examples::normalize_whitespace);
  pipeline.add_action_named("truncate", truncate_at_280);

  const auto result = pipeline.execute("  Hello   World  ");
  std::cout << "output=" << result.context << std::endl;
  std::cout << "shortCircuited=" << (result.short_circuited ? "true" : "false") << std::endl;
  std::cout << "errors=" << result.errors.size() << std::endl;
  return 0;
}

