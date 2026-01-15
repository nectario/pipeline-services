#include <chrono>
#include <cstddef>
#include <exception>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include <httplib.h>

#include "pipeline_services/config/json_loader.hpp"
#include "pipeline_services/core/pipeline.hpp"
#include "pipeline_services/core/registry.hpp"

namespace {

constexpr const char* remote_fixture_body = "Hello from remote fixture\n";

void run_server_after_bind(httplib::Server* server) {
  server->listen_after_bind();
}

void require_true(bool value, const std::string& message) {
  if (!value) {
    throw std::runtime_error(message);
  }
}

template <typename LeftType, typename RightType>
void require_equal(const LeftType& left_value, const RightType& right_value, const std::string& message) {
  if (!(left_value == right_value)) {
    throw std::runtime_error(message);
  }
}

struct FixtureServer {
  FixtureServer()
    : server(),
      server_port(0),
      server_thread() {}

  void start() {
    server.Get("/remote_hello.txt", fixture_handler);
    server_port = server.bind_to_any_port("127.0.0.1");
    if (server_port <= 0) {
      throw std::runtime_error("Failed to bind fixture server");
    }
    server_thread = std::thread(run_server_after_bind, &server);
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
  }

  void stop() {
    server.stop();
    if (server_thread.joinable()) {
      server_thread.join();
    }
  }

  std::string base_url() const {
    return "http://127.0.0.1:" + std::to_string(server_port);
  }

  static void fixture_handler(const httplib::Request& request, httplib::Response& response) {
    if (request.path == "/remote_hello.txt") {
      response.status = 200;
      response.set_content(remote_fixture_body, "text/plain; charset=utf-8");
      return;
    }
    response.status = 404;
    response.set_content("not found", "text/plain; charset=utf-8");
  }

  ~FixtureServer() {
    stop();
  }

  httplib::Server server;
  int server_port;
  std::thread server_thread;
};

struct AppendAction {
  std::shared_ptr<std::vector<std::string>> calls;
  std::string call_name;
  std::string suffix;

  std::string operator()(std::string ctx) const {
    calls->push_back(call_name);
    return ctx + suffix;
  }
};

struct ShortCircuitAction {
  std::shared_ptr<std::vector<std::string>> calls;
  std::string call_name;
  std::string suffix;

  std::string operator()(std::string ctx, pipeline_services::core::StepControl<std::string>& control) const {
    calls->push_back(call_name);
    control.short_circuit();
    return ctx + suffix;
  }
};

struct FailingAction {
  std::shared_ptr<std::vector<std::string>> calls;
  std::string call_name;

  std::string operator()(std::string ctx) const {
    calls->push_back(call_name);
    (void)ctx;
    throw std::runtime_error("boom");
  }
};

std::string identity_action(std::string value) {
  return value;
}

void test_short_circuit_stops_main_only() {
  auto calls = std::make_shared<std::vector<std::string>>();

  pipeline_services::core::Pipeline<std::string> pipeline("t", true);
  pipeline.add_pre_action(AppendAction{calls, "pre", "pre|"});
  pipeline.add_action(AppendAction{calls, "a1", "a1|"});
  pipeline.add_action(ShortCircuitAction{calls, "a2", "a2|"});
  pipeline.add_action(AppendAction{calls, "a3", "a3|"});
  pipeline.add_post_action(AppendAction{calls, "post", "post|"});

  const auto result = pipeline.execute("");
  require_true(result.short_circuited, "expected short_circuited=true");
  require_equal(
    *calls,
    std::vector<std::string>{"pre", "a1", "a2", "post"},
    "unexpected call order"
  );
}

void test_short_circuit_on_exception_stops_main() {
  auto calls = std::make_shared<std::vector<std::string>>();

  pipeline_services::core::Pipeline<std::string> pipeline("t", true);
  pipeline.add_action(FailingAction{calls, "fail"});
  pipeline.add_action(AppendAction{calls, "later", "|later"});
  pipeline.add_post_action(AppendAction{calls, "post", "|post"});

  const auto result = pipeline.execute("start");
  require_true(result.short_circuited, "expected short_circuited=true");
  require_equal(result.errors.size(), static_cast<std::size_t>(1), "expected one error");
  require_equal(*calls, std::vector<std::string>{"fail", "post"}, "unexpected call order");
}

void test_continue_on_exception_runs_remaining_actions() {
  auto calls = std::make_shared<std::vector<std::string>>();

  pipeline_services::core::Pipeline<std::string> pipeline("t", false);
  pipeline.add_action(FailingAction{calls, "fail"});
  pipeline.add_action(AppendAction{calls, "later", "|later"});

  const auto result = pipeline.execute("start");
  require_true(!result.short_circuited, "expected short_circuited=false");
  require_equal(result.errors.size(), static_cast<std::size_t>(1), "expected one error");
  require_equal(result.context, std::string("start|later"), "unexpected output");
  require_equal(*calls, std::vector<std::string>{"fail", "later"}, "unexpected call order");
}

void test_json_loader_actions_alias() {
  pipeline_services::core::PipelineRegistry<std::string> registry;
  registry.register_unary("identity", identity_action);

  const std::string json_text = R"json(
{
  "pipeline": "t",
  "type": "unary",
  "actions": [
    {"$local": "identity"}
  ]
}
)json";

  pipeline_services::config::PipelineJsonLoader loader;
  auto pipeline = loader.load_str(json_text, registry);
  const std::string output_value = pipeline.run("ok");
  require_equal(output_value, std::string("ok"), "unexpected output");
}

void test_remote_http_step_get() {
  FixtureServer server;
  server.start();

  pipeline_services::remote::RemoteSpec<std::string> spec(server.base_url() + "/remote_hello.txt");
  spec.method = "GET";
  const std::string response_body = pipeline_services::remote::http_step(spec, std::string("ignored"));
  require_equal(response_body, std::string(remote_fixture_body), "unexpected remote response");
}

void test_json_loader_remote_get() {
  FixtureServer server;
  server.start();

  const std::string endpoint = server.base_url() + "/remote_hello.txt";
  const std::string json_text = R"json(
{
  "pipeline": "t",
  "type": "unary",
  "steps": [
    {
      "name": "remote_get_fixture",
      "$remote": {
        "endpoint": ")json" + endpoint + R"json(",
        "method": "GET"
      }
    }
  ]
}
)json";

  pipeline_services::core::PipelineRegistry<std::string> registry;
  pipeline_services::config::PipelineJsonLoader loader;
  const auto pipeline = loader.load_str(json_text, registry);
  const std::string output_value = pipeline.run("ignored");
  require_equal(output_value, std::string(remote_fixture_body), "unexpected remote output");
}

}  // namespace

int main() {
  try {
    test_short_circuit_stops_main_only();
    test_short_circuit_on_exception_stops_main();
    test_continue_on_exception_runs_remaining_actions();
    test_json_loader_actions_alias();
    test_remote_http_step_get();
    test_json_loader_remote_get();
  } catch (const std::exception& error) {
    std::cerr << "FAILED: " << error.what() << std::endl;
    return 1;
  } catch (...) {
    std::cerr << "FAILED: unknown exception" << std::endl;
    return 1;
  }

  std::cout << "OK" << std::endl;
  return 0;
}
