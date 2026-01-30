use pipeline_services::Pipeline;

fn main() {
  let pipeline = Pipeline::new("example00_import", true);
  let result = pipeline.run("ok".to_string());
  println!("{}", result.context);
}
