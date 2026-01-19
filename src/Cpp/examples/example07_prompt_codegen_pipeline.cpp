#include <filesystem>
#include <iostream>
#include <stdexcept>
#include <string>

#include "pipeline_services/config/json_loader.hpp"
#include "pipeline_services/core/registry.hpp"
#include "pipeline_services/examples/text_steps.hpp"
#include "pipeline_services/generated/prompt_actions.hpp"

static std::string findPipelineFile(const std::string& pipelineFileName) {
  std::filesystem::path currentDir = std::filesystem::current_path();
  while (true) {
    const std::filesystem::path candidatePath = currentDir / "pipelines" / pipelineFileName;
    if (std::filesystem::exists(candidatePath)) {
      return candidatePath.string();
    }
    const std::filesystem::path parentDir = currentDir.parent_path();
    if (parentDir == currentDir) {
      break;
    }
    currentDir = parentDir;
  }
  throw std::runtime_error("Could not locate pipelines directory from current working directory");
}

int main() {
  const std::string pipelineFile = findPipelineFile("normalize_name.json");

  pipeline_services::core::PipelineRegistry<std::string> registry;
  registry.registerUnary("strip", pipeline_services::examples::strip);
  pipeline_services::generated::registerGeneratedActions(registry);

  pipeline_services::config::PipelineJsonLoader loader;
  auto pipeline = loader.load_file(pipelineFile, registry);
  const std::string outputValue = pipeline.run("  john   SMITH ");
  std::cout << "output=" << outputValue << std::endl;
  return 0;
}
