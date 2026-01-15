#include <chrono>
#include <cstddef>
#include <cstdint>
#include <iostream>
#include <map>
#include <string>

#include "pipeline_services/core/pipeline.hpp"
#include "pipeline_services/examples/text_steps.hpp"

int main() {
  pipeline_services::core::Pipeline<std::string> pipeline("benchmark01_pipeline_run", true);
  pipeline.add_action(pipeline_services::examples::strip);
  pipeline.add_action(pipeline_services::examples::to_lower);
  pipeline.add_action(pipeline_services::examples::append_marker);

  const std::string input_value = "  Hello Benchmark  ";
  const std::size_t warmup_iterations = 1000;
  const std::size_t iterations = 10'000;

  std::size_t warmup_index = 0;
  while (warmup_index < warmup_iterations) {
    const std::string unused_value = pipeline.run(input_value);
    (void)unused_value;
    warmup_index += 1;
  }

  std::int64_t total_pipeline_nanos = 0;
  std::map<std::string, std::int64_t> action_totals;
  std::map<std::string, std::int64_t> action_counts;

  const auto start_timepoint = std::chrono::steady_clock::now();
  std::size_t iteration_index = 0;
  while (iteration_index < iterations) {
    const auto result = pipeline.execute(input_value);
    total_pipeline_nanos += result.total_nanos;

    for (const auto& timing : result.timings) {
      action_totals[timing.action_name] += timing.elapsed_nanos;
      action_counts[timing.action_name] += 1;
    }

    iteration_index += 1;
  }
  const auto wall_nanos =
    std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start_timepoint).count();

  std::cout << "iterations=" << iterations << std::endl;
  std::cout << "wallMs=" << (static_cast<double>(wall_nanos) / 1'000'000.0) << std::endl;
  std::cout << "avgPipelineUs=" << (static_cast<double>(total_pipeline_nanos) / static_cast<double>(iterations) / 1'000.0)
            << std::endl;
  std::cout << "avgActionUs=" << std::endl;
  for (const auto& totals_item : action_totals) {
    const auto action_name = totals_item.first;
    const auto nanos_total = totals_item.second;
    const auto count_total = action_counts[action_name];
    std::cout << "  " << action_name << "="
              << (static_cast<double>(nanos_total) / static_cast<double>(count_total) / 1'000.0) << std::endl;
  }

  return 0;
}

