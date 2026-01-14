use std::collections::BTreeMap;

use crate::core::pipeline::StepControl;

pub fn print_metrics<ContextType>(ctx: ContextType, control: &mut StepControl<ContextType>) -> ContextType {
  let mut metrics_map: BTreeMap<String, serde_json::Value> = BTreeMap::new();

  metrics_map.insert("pipeline".to_string(), serde_json::Value::String(control.pipeline_name.clone()));
  metrics_map.insert(
    "shortCircuited".to_string(),
    serde_json::Value::Bool(control.is_short_circuited()),
  );
  metrics_map.insert(
    "errorCount".to_string(),
    serde_json::Value::Number(serde_json::Number::from(control.errors.len())),
  );

  let pipeline_latency_ms = (control.run_elapsed_nanos() as f64) / 1_000_000.0;
  metrics_map.insert(
    "pipelineLatencyMs".to_string(),
    serde_json::Value::Number(
      serde_json::Number::from_f64(pipeline_latency_ms).unwrap_or_else(|| serde_json::Number::from(0)),
    ),
  );

  let mut action_latency_ms: BTreeMap<String, serde_json::Value> = BTreeMap::new();
  for timing in &control.timings {
    let elapsed_ms = (timing.elapsed_nanos as f64) / 1_000_000.0;
    action_latency_ms.insert(
      timing.action_name.clone(),
      serde_json::Value::Number(
        serde_json::Number::from_f64(elapsed_ms).unwrap_or_else(|| serde_json::Number::from(0)),
      ),
    );
  }
  metrics_map.insert("actionLatencyMs".to_string(), serde_json::to_value(action_latency_ms).unwrap_or_default());

  println!("{}", serde_json::to_string(&metrics_map).unwrap_or_else(|_| "{}".to_string()));
  ctx
}

