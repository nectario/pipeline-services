use pipeline_services::examples::text_steps::{append_marker, normalize_whitespace, strip, to_lower};
use pipeline_services::remote::http_step::{http_step, RemoteSpec};
use pipeline_services::Pipeline;

fn remote_echo(text_value: String) -> String {
  let fixture_endpoint = "http://127.0.0.1:8765/echo";
  let mut remote_spec = RemoteSpec::new(fixture_endpoint);
  remote_spec.method = "POST".to_string();
  remote_spec.timeout_millis = 1000;
  remote_spec.retries = 0;

  match http_step(&remote_spec, &text_value) {
    Ok(response_body) => response_body,
    Err(message) => panic!("{message}"),
  }
}

fn main() {
  let mut pipeline = Pipeline::new("example06_mixed_local_remote", true);
  pipeline.add_action(strip);
  pipeline.add_action(normalize_whitespace);
  pipeline.add_action_named("remote_echo", remote_echo);
  pipeline.add_action(to_lower);
  pipeline.add_action(append_marker);

  let result = pipeline.run("  Hello   Remote  ".to_string());
  println!("output={}", result.context);
}
