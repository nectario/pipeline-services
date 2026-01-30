use pipeline_services::examples::text_steps::{normalize_whitespace, strip};
use pipeline_services::{print_metrics, Pipeline};

fn main() {
  let mut pipeline = Pipeline::new("example05_metrics_post_action", true);
  pipeline.add_action(strip);
  pipeline.add_action(normalize_whitespace);
  pipeline.add_post_action_control(print_metrics);

  let result = pipeline.run("  Hello   Metrics  ".to_string());
  println!("{}", result.context);
}
