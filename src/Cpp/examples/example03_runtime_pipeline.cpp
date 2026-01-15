#include <iostream>
#include <string>

#include "pipeline_services/core/runtime_pipeline.hpp"
#include "pipeline_services/examples/text_steps.hpp"

int main() {
  pipeline_services::core::RuntimePipeline<std::string> runtime_pipeline(
    "example03_runtime_pipeline",
    false,
    "  Hello   Runtime  "
  );
  runtime_pipeline.add_action(pipeline_services::examples::strip);
  runtime_pipeline.add_action(pipeline_services::examples::normalize_whitespace);
  std::cout << "runtimeValue=" << runtime_pipeline.value() << std::endl;

  const auto frozen_pipeline = runtime_pipeline.freeze();
  const auto result = frozen_pipeline.execute("  Hello   Frozen  ");
  std::cout << "frozenValue=" << result.context << std::endl;
  return 0;
}

