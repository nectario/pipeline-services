#include <iostream>
#include <string>

#include "pipeline_services/core/metrics_actions.hpp"
#include "pipeline_services/core/pipeline.hpp"
#include "pipeline_services/examples/text_steps.hpp"

int main() {
  pipeline_services::core::Pipeline<std::string> pipeline("example05_metrics_post_action", true);
  pipeline.add_action(pipeline_services::examples::strip);
  pipeline.add_action(pipeline_services::examples::normalize_whitespace);
  pipeline.add_post_action(pipeline_services::core::print_metrics<std::string>);

  const std::string output_value = pipeline.run("  Hello   Metrics  ");
  std::cout << output_value << std::endl;
  return 0;
}

