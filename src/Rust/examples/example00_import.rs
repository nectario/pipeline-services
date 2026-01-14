use pipeline_services::Pipeline;

fn main() {
  let pipeline = Pipeline::new("example00_import", true);
  let output_value = pipeline.run("ok".to_string());
  println!("{output_value}");
}

