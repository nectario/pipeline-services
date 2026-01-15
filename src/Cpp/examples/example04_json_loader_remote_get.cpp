#include <iostream>
#include <string>

#include "pipeline_services/config/json_loader.hpp"
#include "pipeline_services/core/registry.hpp"

int main() {
  const std::string json_text = R"json(
{
  "pipeline": "example04_json_loader_remote_get",
  "type": "unary",
  "steps": [
    {
      "name": "remote_get_fixture",
      "$remote": {
        "endpoint": "http://127.0.0.1:8765/remote_hello.txt",
        "method": "GET",
        "timeoutMillis": 1000,
        "retries": 0
      }
    }
  ]
}
)json";

  pipeline_services::core::PipelineRegistry<std::string> registry;
  pipeline_services::config::PipelineJsonLoader loader;
  const auto pipeline = loader.loadStr(json_text, registry);
  const std::string output_value = pipeline.run("ignored");
  std::cout << output_value << std::endl;
  return 0;
}

