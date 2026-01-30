use std::collections::BTreeMap;
use std::time::Instant;

use pipeline_services::examples::text_steps::{append_marker, strip, to_lower};
use pipeline_services::Pipeline;

fn main() {
  let mut pipeline = Pipeline::new("benchmark01_pipeline_run", true);
  pipeline.add_action(strip);
  pipeline.add_action(to_lower);
  pipeline.add_action(append_marker);

  let input_value = "  Hello Benchmark  ".to_string();
  let warmup_iterations: usize = 1000;
  let iterations: usize = 10_000;

  let mut warmup_index: usize = 0;
  while warmup_index < warmup_iterations {
    let warmup_result = pipeline.run(input_value.clone());
    drop(warmup_result);
    warmup_index += 1;
  }

  let mut total_pipeline_nanos: u128 = 0;
  let mut action_totals: BTreeMap<String, u128> = BTreeMap::new();
  let mut action_counts: BTreeMap<String, u128> = BTreeMap::new();

  let start_instant = Instant::now();
  let mut iteration_index: usize = 0;
  while iteration_index < iterations {
    let result = pipeline.run(input_value.clone());
    total_pipeline_nanos += result.total_nanos;

    for timing in &result.timings {
      let total_entry = action_totals.entry(timing.action_name.clone()).or_insert(0);
      *total_entry += timing.elapsed_nanos;

      let count_entry = action_counts.entry(timing.action_name.clone()).or_insert(0);
      *count_entry += 1;
    }

    iteration_index += 1;
  }
  let wall_nanos = start_instant.elapsed().as_nanos();

  println!("iterations={iterations}");
  println!("wallMs={}", (wall_nanos as f64) / 1_000_000.0);
  println!(
    "avgPipelineUs={}",
    (total_pipeline_nanos as f64) / (iterations as f64) / 1_000.0
  );
  println!("avgActionUs=");
  for (action_name, nanos_total) in action_totals {
    let count_total = action_counts.get(&action_name).cloned().unwrap_or(1);
    println!(
      "  {}={}",
      action_name,
      (nanos_total as f64) / (count_total as f64) / 1_000.0
    );
  }
}
