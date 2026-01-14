use pipeline_services::examples::text_steps::{normalize_whitespace, strip};
use pipeline_services::{PipelineJsonLoader, PipelineRegistry};

fn main() {
  let mut registry: PipelineRegistry<String> = PipelineRegistry::new();
  registry.register_unary("strip", strip);
  registry.register_unary("normalize_whitespace", normalize_whitespace);

  let json_text = r#"
{
  "pipeline": "example02_json_loader",
  "type": "unary",
  "shortCircuitOnException": true,
  "steps": [
    {"$local": "strip"},
    {"$local": "normalize_whitespace"}
  ]
}
"#;

  let loader = PipelineJsonLoader::new();
  let pipeline = loader.load_str(json_text, &registry).expect("loader failed");
  let output_value = pipeline.run("  Hello   JSON  ".to_string());
  println!("{output_value}");
}

