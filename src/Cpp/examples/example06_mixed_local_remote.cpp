#include <iostream>
#include <string>
#include <utility>

#include "pipeline_services/core/pipeline.hpp"
#include "pipeline_services/examples/text_steps.hpp"
#include "pipeline_services/remote/http_step.hpp"

int main() {
  const std::string fixture_endpoint = "http://127.0.0.1:8765/echo";

  pipeline_services::remote::RemoteSpec<std::string> remote_spec(fixture_endpoint);
  remote_spec.timeoutMillis = 1000;
  remote_spec.retries = 0;

  pipeline_services::core::Pipeline<std::string> pipeline("example06_mixed_local_remote", true);
  pipeline.addAction(pipeline_services::examples::strip);
  pipeline.addAction(pipeline_services::examples::normalize_whitespace);
  pipeline.addAction("remote_echo", pipeline_services::remote::jsonPost<std::string>(std::move(remote_spec)));
  pipeline.addAction(pipeline_services::examples::to_lower);
  pipeline.addAction(pipeline_services::examples::append_marker);

  const std::string output_value = pipeline.run("  Hello   Remote  ");
  std::cout << "output=" << output_value << std::endl;
  return 0;
}

