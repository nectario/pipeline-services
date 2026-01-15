#include <iostream>
#include <string>

#include "pipeline_services/config/json_loader.hpp"
#include "pipeline_services/core/registry.hpp"
#include "pipeline_services/examples/text_steps.hpp"

int main() {
  pipeline_services::core::PipelineRegistry<std::string> registry;
  registry.registerUnary("strip", pipeline_services::examples::strip);
  registry.registerUnary("normalize_whitespace", pipeline_services::examples::normalize_whitespace);

  const std::string json_text = R"json(
{
  "pipeline": "example02_json_loader",
  "type": "unary",
  "shortCircuitOnException": true,
  "steps": [
    {"$local": "strip"},
    {"$local": "normalize_whitespace"}
  ]
}
)json";

  pipeline_services::config::PipelineJsonLoader loader;
  auto pipeline = loader.loadStr(json_text, registry);
  const std::string output_value = pipeline.run("  Hello   JSON  ");
  std::cout << output_value << std::endl;
  return 0;
}

