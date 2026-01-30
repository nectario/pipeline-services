use pipeline_services::examples::text_steps::{normalize_whitespace, strip};
use pipeline_services::{Pipeline, StepControl};

fn truncate_at_280(text_value: String, control: &mut StepControl<String>) -> String {
  if text_value.chars().count() <= 280 {
    return text_value;
  }
  control.short_circuit();
  text_value.chars().take(280).collect::<String>()
}

fn main() {
  let mut pipeline = Pipeline::new("example01_text_clean", true);
  pipeline.add_action(strip);
  pipeline.add_action(normalize_whitespace);
  pipeline.add_action_control_named("truncate", truncate_at_280);

  let result = pipeline.run("  Hello   World  ".to_string());
  println!("output={}", result.context);
  println!("shortCircuited={}", result.short_circuited);
  println!("errors={}", result.errors.len());
}
