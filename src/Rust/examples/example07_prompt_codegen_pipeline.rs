use std::path::PathBuf;

use pipeline_services::examples::text_steps::strip;
use pipeline_services::generated::register_generated_actions;
use pipeline_services::{PipelineJsonLoader, PipelineRegistry};

fn find_pipeline_file(pipeline_file_name: &str) -> PathBuf {
  let mut current_dir = std::env::current_dir().expect("Failed to determine current directory");
  loop {
    let candidate_path = current_dir.join("pipelines").join(pipeline_file_name);
    if candidate_path.exists() {
      return candidate_path;
    }
    if !current_dir.pop() {
      break;
    }
  }
  panic!("Could not locate pipelines directory from current working directory");
}

fn main() {
  let pipeline_file = find_pipeline_file("normalize_name.json");

  let mut registry: PipelineRegistry<String> = PipelineRegistry::new();
  registry.register_unary("strip", strip);
  register_generated_actions(&mut registry);

  let loader = PipelineJsonLoader::new();
  let pipeline = loader
    .load_file(
      pipeline_file.to_str().expect("Invalid pipeline path"),
      &registry,
    )
    .expect("loader failed");
  let result = pipeline.run("  john   SMITH ".to_string());
  println!("output={}", result.context);
}
