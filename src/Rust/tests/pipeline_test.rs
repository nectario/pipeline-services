use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::{mpsc, Arc, Mutex};
use std::thread;
use std::time::Duration;

use pipeline_services::remote::http_step::{http_step, RemoteSpec};
use pipeline_services::{Pipeline, PipelineJsonLoader, PipelineRegistry, StepControl};

const REMOTE_FIXTURE_BODY: &str = "Hello from remote fixture\n";

fn identity_action(value: String) -> String {
  value
}

fn handle_fixture_connection(mut stream: TcpStream) {
  let mut request_buffer: [u8; 2048] = [0; 2048];
  let bytes_read = match stream.read(&mut request_buffer) {
    Ok(value) => value,
    Err(_) => 0,
  };

  let request_text = String::from_utf8_lossy(&request_buffer[..bytes_read]).to_string();
  let request_line = request_text.lines().next().unwrap_or("");
  let mut parts_iterator = request_line.split_whitespace();
  let method_value = parts_iterator.next().unwrap_or("");
  let path_value = parts_iterator.next().unwrap_or("");

  let response_body = if method_value == "GET" && path_value == "/remote_hello.txt" {
    REMOTE_FIXTURE_BODY.to_string()
  } else {
    "not found".to_string()
  };

  let status_line = if method_value == "GET" && path_value == "/remote_hello.txt" {
    "HTTP/1.1 200 OK"
  } else {
    "HTTP/1.1 404 Not Found"
  };

  let response_text = format!(
    "{status_line}\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
    response_body.len(),
    response_body
  );

  let unused_result = stream.write_all(response_text.as_bytes());
  drop(unused_result);
}

struct FixtureServer {
  address: String,
  shutdown_sender: mpsc::Sender<()>,
  join_handle: Option<thread::JoinHandle<()>>,
}

impl FixtureServer {
  fn start() -> Self {
    let listener = TcpListener::bind("127.0.0.1:0").expect("bind failed");
    listener.set_nonblocking(true).expect("nonblocking failed");
    let address = listener.local_addr().expect("local_addr failed");

    let (shutdown_sender, shutdown_receiver) = mpsc::channel::<()>();
    let join_handle = thread::spawn(move || loop {
      if shutdown_receiver.try_recv().is_ok() {
        break;
      }
      match listener.accept() {
        Ok((stream, _)) => handle_fixture_connection(stream),
        Err(error) => {
          if error.kind() == std::io::ErrorKind::WouldBlock {
            thread::sleep(Duration::from_millis(5));
            continue;
          }
          break;
        }
      }
    });

    Self {
      address: format!("http://{}", address),
      shutdown_sender,
      join_handle: Some(join_handle),
    }
  }

  fn url(&self, path: &str) -> String {
    format!("{}{}", self.address, path)
  }

  fn stop(&mut self) {
    let unused_result = self.shutdown_sender.send(());
    let _ = unused_result;
    if let Some(join_handle) = self.join_handle.take() {
      let unused_result = join_handle.join();
      drop(unused_result);
    }
  }
}

impl Drop for FixtureServer {
  fn drop(&mut self) {
    self.stop();
  }
}

#[test]
fn short_circuit_stops_main_only() {
  let calls: Arc<Mutex<Vec<String>>> = Arc::new(Mutex::new(Vec::new()));

  let calls_pre = calls.clone();
  let pre_action = move |ctx: String| -> String {
    calls_pre.lock().unwrap().push("pre".to_string());
    format!("{ctx}pre|")
  };

  let calls_one = calls.clone();
  let action_one = move |ctx: String| -> String {
    calls_one.lock().unwrap().push("a1".to_string());
    format!("{ctx}a1|")
  };

  let calls_two = calls.clone();
  let action_two_short_circuit = move |ctx: String, control: &mut StepControl<String>| -> String {
    calls_two.lock().unwrap().push("a2".to_string());
    control.short_circuit();
    format!("{ctx}a2|")
  };

  let calls_three = calls.clone();
  let action_three = move |ctx: String| -> String {
    calls_three.lock().unwrap().push("a3".to_string());
    format!("{ctx}a3|")
  };

  let calls_post = calls.clone();
  let post_action = move |ctx: String| -> String {
    calls_post.lock().unwrap().push("post".to_string());
    format!("{ctx}post|")
  };

  let mut pipeline = Pipeline::new("t", true);
  pipeline.add_pre_action(pre_action);
  pipeline.add_action(action_one);
  pipeline.add_action_control(action_two_short_circuit);
  pipeline.add_action(action_three);
  pipeline.add_post_action(post_action);

  let result = pipeline.execute("".to_string());
  assert_eq!(result.short_circuited, true);
  assert_eq!(
    calls.lock().unwrap().clone(),
    vec!["pre".to_string(), "a1".to_string(), "a2".to_string(), "post".to_string()]
  );
}

#[test]
fn short_circuit_on_exception_stops_main() {
  let calls: Arc<Mutex<Vec<String>>> = Arc::new(Mutex::new(Vec::new()));

  let calls_fail = calls.clone();
  let failing_action = move |ctx: String| -> String {
    calls_fail.lock().unwrap().push("fail".to_string());
    drop(ctx);
    panic!("boom");
  };

  let calls_later = calls.clone();
  let later_action = move |ctx: String| -> String {
    calls_later.lock().unwrap().push("later".to_string());
    format!("{ctx}|later")
  };

  let calls_post = calls.clone();
  let post_action = move |ctx: String| -> String {
    calls_post.lock().unwrap().push("post".to_string());
    format!("{ctx}|post")
  };

  let mut pipeline = Pipeline::new("t", true);
  pipeline.add_action(failing_action);
  pipeline.add_action(later_action);
  pipeline.add_post_action(post_action);

  let result = pipeline.execute("start".to_string());
  assert_eq!(result.short_circuited, true);
  assert_eq!(result.errors.len(), 1);
  assert_eq!(calls.lock().unwrap().clone(), vec!["fail".to_string(), "post".to_string()]);
}

#[test]
fn continue_on_exception_runs_remaining_actions() {
  let calls: Arc<Mutex<Vec<String>>> = Arc::new(Mutex::new(Vec::new()));

  let calls_fail = calls.clone();
  let failing_action = move |ctx: String| -> String {
    calls_fail.lock().unwrap().push("fail".to_string());
    drop(ctx);
    panic!("boom");
  };

  let calls_later = calls.clone();
  let later_action = move |ctx: String| -> String {
    calls_later.lock().unwrap().push("later".to_string());
    format!("{ctx}|later")
  };

  let mut pipeline = Pipeline::new("t", false);
  pipeline.add_action(failing_action);
  pipeline.add_action(later_action);

  let result = pipeline.execute("start".to_string());
  assert_eq!(result.short_circuited, false);
  assert_eq!(result.errors.len(), 1);
  assert_eq!(result.context, "start|later".to_string());
  assert_eq!(calls.lock().unwrap().clone(), vec!["fail".to_string(), "later".to_string()]);
}

#[test]
fn json_loader_supports_actions_alias() {
  let mut registry: PipelineRegistry<String> = PipelineRegistry::new();
  registry.register_unary("identity", identity_action);

  let json_text = r#"
{
  "pipeline": "t",
  "type": "unary",
  "actions": [
    {"$local": "identity"}
  ]
}
"#;

  let loader = PipelineJsonLoader::new();
  let pipeline = loader.load_str(json_text, &registry).expect("loader failed");
  let output_value = pipeline.run("ok".to_string());
  assert_eq!(output_value, "ok".to_string());
}

#[test]
fn remote_http_step_get() {
  let server = FixtureServer::start();
  let endpoint = server.url("/remote_hello.txt");

  let mut spec = RemoteSpec::new(endpoint);
  spec.method = "GET".to_string();
  let response_body = http_step(&spec, &"ignored").expect("http_step failed");
  assert_eq!(response_body, REMOTE_FIXTURE_BODY.to_string());
}

#[test]
fn json_loader_remote_get() {
  let server = FixtureServer::start();
  let endpoint = server.url("/remote_hello.txt");

  let json_text = format!(
    r#"
{{
  "pipeline": "t",
  "type": "unary",
  "steps": [
    {{
      "name": "remote_get_fixture",
      "$remote": {{
        "endpoint": "{endpoint}",
        "method": "GET"
      }}
    }}
  ]
}}
"#
  );

  let registry: PipelineRegistry<String> = PipelineRegistry::new();
  let loader = PipelineJsonLoader::new();
  let pipeline = loader.load_str(&json_text, &registry).expect("loader failed");
  let output_value = pipeline.run("ignored".to_string());
  assert_eq!(output_value, REMOTE_FIXTURE_BODY.to_string());
}
