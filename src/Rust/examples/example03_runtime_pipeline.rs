use pipeline_services::examples::text_steps::{normalize_whitespace, strip};
use pipeline_services::RuntimePipeline;

fn main() {
  let mut runtime_pipeline = RuntimePipeline::new("example03_runtime_pipeline", false, "  Hello   Runtime  ".to_string());
  runtime_pipeline.add_action(strip);
  runtime_pipeline.add_action(normalize_whitespace);
  match runtime_pipeline.value() {
    Some(value) => println!("runtimeValue={value}"),
    None => println!("runtimeValue=<none>"),
  }

  let frozen_pipeline = runtime_pipeline.freeze();
  let result = frozen_pipeline.run("  Hello   Frozen  ".to_string());
  println!("frozenValue={}", result.context);
}
