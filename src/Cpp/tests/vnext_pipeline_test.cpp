#include <algorithm>
#include <cassert>
#include <condition_variable>
#include <cstddef>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

#include "pipeline_services/core/pipeline.hpp"
#include "pipeline_services/core/pipeline_provider.hpp"
#include "pipeline_services/core/pipeline_router.hpp"

namespace {

using pipeline_services::InvalidErrorHandlerError;
using pipeline_services::Pipeline;
using pipeline_services::PipelineObserver;
using pipeline_services::PipelineProvider;
using pipeline_services::PipelineProviderMode;
using pipeline_services::PipelineRouter;
using pipeline_services::StepPhase;
using pipeline_services::shortCircuit;

std::string appendAndStop(std::string value) {
  shortCircuit();
  value.push_back('S');
  return value;
}

std::string fail(std::string value) {
  (void)value;
  throw std::runtime_error("boom");
}

class ControllingObserver final : public PipelineObserver {
 public:
  void onActionStarted(
      const std::string&,
      StepPhase phase,
      std::size_t index,
      const std::string& name) override {
    events.push_back(
        std::string(pipeline_services::phaseName(phase)) + ":" +
        std::to_string(index) + ":" + name);
    shortCircuit();
  }

  std::vector<std::string> events;
};

void testRunAndDetailed() {
  Pipeline<std::string> pipeline("simple", true);
  pipeline.addPreAction([](std::string value) { return value + "P"; });
  pipeline.addAction([](std::string value) { return value + "A"; });
  pipeline.addPostAction([](std::string value) { return value + "Z"; });

  assert(pipeline.run("X") == "XPAZ");
  auto result = pipeline.runDetailed("X");
  assert(result.context == "XPAZ");
  assert(result.actionTimings.size() == 3);
}

void testShortCircuitRules() {
  Pipeline<std::string> main("main", true);
  main.addAction(appendAndStop);
  main.addAction([](std::string value) { return value + "B"; });
  main.addPostAction([](std::string value) { return value + "P"; });
  assert(main.run("X") == "XSP");

  Pipeline<std::string> pre("pre", true);
  pre.addPreAction(appendAndStop);
  pre.addPreAction([](std::string value) { return value + "2"; });
  pre.addAction([](std::string value) { return value + "M"; });
  pre.addPostAction([](std::string value) { return value + "P"; });
  assert(pre.run("X") == "XS2P");

  Pipeline<std::string> post("post", true);
  post.addAction([](std::string value) { return value + "M"; });
  post.addPostAction(appendAndStop);
  post.addPostAction([](std::string value) { return value + "2"; });
  auto result = post.runDetailed("X");
  assert(result.context == "XMS2");
  assert(result.shortCircuited);
}

void testExceptionPolicies() {
  Pipeline<std::string> continuing("continue", false);
  continuing.addAction([](std::string value) { return value + "A"; });
  continuing.addAction(fail);
  continuing.addAction([](std::string value) { return value + "B"; });
  auto continued = continuing.runDetailed("X");
  assert(continued.context == "XAB");
  assert(!continued.shortCircuited);
  assert(continued.errors.size() == 1);
  assert(continued.errors.front().actionIndex == 1);

  Pipeline<std::string> stopping("stop", true);
  stopping.addAction([](std::string value) { return value + "A"; });
  stopping.addAction(fail);
  stopping.addAction([](std::string value) { return value + "B"; });
  stopping.addPostAction([](std::string value) { return value + "P"; });
  auto stopped = stopping.runDetailed("X");
  assert(stopped.context == "XAP");
  assert(stopped.shortCircuited);
}

void testInvalidHandlerRunsAllPostActions() {
  std::vector<std::string> calls;
  Pipeline<std::string> pipeline("handler", true);
  pipeline.onError([](std::string, const pipeline_services::PipelineError&) -> std::string {
    throw std::runtime_error("handler failed");
  });
  pipeline.addAction(fail);
  pipeline.addPostAction([&calls](std::string value) {
    calls.push_back("post1");
    return value;
  });
  pipeline.addPostAction([&calls](std::string value) {
    calls.push_back("post2");
    return value;
  });

  bool failedClearly = false;
  try {
    (void)pipeline.run("X");
  } catch (const InvalidErrorHandlerError&) {
    failedClearly = true;
  }
  assert(failedClearly);
  assert((calls == std::vector<std::string>{"post1", "post2"}));
}

void testNestedAndConcurrentIsolation() {
  auto inner = std::make_shared<Pipeline<std::string>>("inner", true);
  inner->addAction(appendAndStop);
  inner->addAction([](std::string value) { return value + "I2"; });
  inner->addPostAction([](std::string value) { return value + "IP"; });

  Pipeline<std::string> outer("outer", true);
  outer.addAction([inner](std::string value) {
    return value + ":" + inner->run("inner");
  });
  outer.addAction([](std::string value) { return value + "O2"; });
  assert(outer.run("outer") == "outer:innerSIPO2");

  std::mutex mutex;
  std::condition_variable condition;
  int started = 0;
  bool release = false;

  auto shared = std::make_shared<Pipeline<std::string>>("shared", true);
  shared->addAction([&](std::string value) {
    {
      std::unique_lock lock(mutex);
      ++started;
      condition.notify_all();
      condition.wait(lock, [&] { return release; });
    }
    if (value == "stop") {
      shortCircuit();
    }
    return value + "A";
  });
  shared->addAction([](std::string value) { return value + "B"; });
  shared->addPostAction([](std::string value) { return value + "P"; });

  std::string stopped;
  std::string continued;
  std::thread stopThread([&] { stopped = shared->run("stop"); });
  std::thread continueThread([&] { continued = shared->run("go"); });
  {
    std::unique_lock lock(mutex);
    condition.wait(lock, [&] { return started == 2; });
    release = true;
  }
  condition.notify_all();
  stopThread.join();
  continueThread.join();

  assert(stopped == "stopAP");
  assert(continued == "goABP");
}

void testFreezeAndObserverIsolation() {
  Pipeline<std::string> frozen("frozen", true);
  frozen.addAction([](std::string value) { return value + "A"; });
  assert(frozen.run("X") == "XA");
  assert(frozen.isFrozen());
  bool mutationFailed = false;
  try {
    frozen.addAction([](std::string value) { return value + "B"; });
  } catch (const std::logic_error&) {
    mutationFailed = true;
  }
  assert(mutationFailed);

  bool outsideFailed = false;
  try {
    shortCircuit();
  } catch (const std::logic_error&) {
    outsideFailed = true;
  }
  assert(outsideFailed);

  auto observer = std::make_shared<ControllingObserver>();
  Pipeline<std::string> observed("observed", true);
  observed.observer(observer);
  observed.addAction([](std::string value) { return value + "A"; });
  observed.addAction([](std::string value) { return value + "B"; });
  assert(observed.run("X") == "XAB");
  assert(observer->events.size() == 2);
}

void testProviderAndRouter() {
  std::size_t created = 0;
  auto factory = [&created]() {
    const std::size_t instanceId = ++created;
    auto pipeline = std::make_shared<Pipeline<std::string>>(
        "p" + std::to_string(instanceId),
        true);
    pipeline->addAction([instanceId](std::string value) {
      return value + std::to_string(instanceId);
    });
    return pipeline;
  };

  auto pooled = PipelineProvider<std::string>::pooled(factory, 3);
  assert(created == 3);
  assert(pooled.mode() == PipelineProviderMode::POOLED);
  assert(pooled.instanceCount() == 3);
  std::vector<std::string> outputs;
  for (int index = 0; index < 5; ++index) {
    outputs.push_back(pooled.run(""));
  }
  assert((outputs == std::vector<std::string>{"1", "2", "3", "1", "2"}));

  auto singletonPipeline = std::make_shared<Pipeline<std::string>>("singleton", true);
  auto singleton = PipelineProvider<std::string>::singleton(singletonPipeline);
  assert(singleton.getPipeline() == singleton.getPipeline());

  auto perEvent = PipelineProvider<std::string>::newInstancePerEvent(factory);
  assert(perEvent.getPipeline() != perEvent.getPipeline());

  auto trade = std::make_shared<PipelineProvider<std::string>>(
      PipelineProvider<std::string>::singleton(
          std::make_shared<Pipeline<std::string>>("trade", true)));
  auto quote = std::make_shared<PipelineProvider<std::string>>(
      PipelineProvider<std::string>::singleton(
          std::make_shared<Pipeline<std::string>>("quote", true)));
  PipelineRouter<std::string, std::string> router(
      [trade, quote](const std::string& event) {
        return event == "TRADE" ? trade : quote;
      });
  assert(router.getPipelineProvider("TRADE") == trade);
  assert(router.getPipelineProvider("QUOTE") == quote);
}

}  // namespace

int main() {
  testRunAndDetailed();
  testShortCircuitRules();
  testExceptionPolicies();
  testInvalidHandlerRunsAllPostActions();
  testNestedAndConcurrentIsolation();
  testFreezeAndObserverIsolation();
  testProviderAndRouter();
}
