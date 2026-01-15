#pragma once

#include <cstdint>
#include <iostream>
#include <string>

#include <nlohmann/json.hpp>

#include "pipeline_services/core/pipeline.hpp"

namespace pipeline_services::core {

template <typename ContextType>
ContextType printMetrics(ContextType ctx, StepControl<ContextType>& control) {
  nlohmann::json metrics_json;
  metrics_json["pipeline"] = control.pipelineName();
  metrics_json["shortCircuited"] = control.isShortCircuited();
  metrics_json["errorCount"] = control.errors().size();

  const double pipeline_latency_ms = static_cast<double>(control.runElapsedNanos()) / 1'000'000.0;
  metrics_json["pipelineLatencyMs"] = pipeline_latency_ms;

  nlohmann::json action_latency_ms = nlohmann::json::object();
  for (const auto& timing : control.actionTimings()) {
    const double elapsed_ms = static_cast<double>(timing.elapsedNanos) / 1'000'000.0;
    action_latency_ms[timing.actionName] = elapsed_ms;
  }
  metrics_json["actionLatencyMs"] = action_latency_ms;

  std::cout << metrics_json.dump() << std::endl;
  return ctx;
}

}  // namespace pipeline_services::core

