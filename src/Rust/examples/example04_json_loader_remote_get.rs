use pipeline_services::{PipelineJsonLoader, PipelineRegistry};

fn main() {
  let json_text = r#"
{
  "pipeline": "example04_json_loader_remote_get",
  "type": "unary",
  "steps": [
    {
      "name": "remote_get_fixture",
      "$remote": {
        "endpoint": "http://127.0.0.1:8765/remote_hello.txt",
        "method": "GET",
        "timeoutMillis": 1000,
        "retries": 0
      }
    }
  ]
}
"#;

  let registry: PipelineRegistry<String> = PipelineRegistry::new();
  let loader = PipelineJsonLoader::new();
  let pipeline = loader.load_str(json_text, &registry).expect("loader failed");
  let output_value = pipeline.run("ignored".to_string());
  println!("{output_value}");
}

