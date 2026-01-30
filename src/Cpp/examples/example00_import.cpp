#include <iostream>
#include <string>

#include "pipeline_services/pipeline_services.hpp"

int main() {
  pipeline_services::core::Pipeline<std::string> pipeline("example00_import", true);
  const auto result = pipeline.run("ok");
  std::cout << result.context << std::endl;
  return 0;
}

