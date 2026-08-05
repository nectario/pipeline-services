use pipeline_services::Pipeline;

fn main() {
  let pipeline = Pipeline::new("example00_import", true);
  println!("{}", pipeline.run("ok".to_string()));
}
