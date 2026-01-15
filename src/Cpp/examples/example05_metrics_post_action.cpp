#include <iostream>
#include <string>

#include "pipeline_services/core/metrics_actions.hpp"
#include "pipeline_services/core/pipeline.hpp"
#include "pipeline_services/examples/text_steps.hpp"

int main() {
  pipeline_services::core::Pipeline<std::string> pipeline("example05_metrics_post_action", true);
  pipeline.addAction(pipeline_services::examples::strip);
  pipeline.addAction(pipeline_services::examples::normalize_whitespace);
  pipeline.addPostAction(pipeline_services::core::printMetrics<std::string>);

  const std::string output_value = pipeline.run("  Hello   Metrics  ");
  std::cout << output_value << std::endl;
  return 0;
}

