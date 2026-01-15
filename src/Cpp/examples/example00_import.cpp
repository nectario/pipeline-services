#include <iostream>
#include <string>

#include "pipeline_services/pipeline_services.hpp"

int main() {
  pipeline_services::core::Pipeline<std::string> pipeline("example00_import", true);
  const std::string output_value = pipeline.run("ok");
  std::cout << output_value << std::endl;
  return 0;
}

