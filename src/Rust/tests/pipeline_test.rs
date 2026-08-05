use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{mpsc, Arc, Barrier, Mutex};
use std::thread;
use std::time::Duration;

use pipeline_services::remote::http_step::{http_step, RemoteSpec};
use pipeline_services::{
  short_circuit, ActionControl, Pipeline, PipelineJsonLoader, PipelineObserver, PipelineProvider,
  PipelineProviderMode, PipelineRegistry, PipelineRouter,
};

const REMOTE_FIXTURE_BODY: &str = "Hello from remote fixture\n";

fn identity_action(value: String) -> String {
  value
}

fn append_and_stop(value: String) -> String {
  short_circuit();
  format!("{value}S")
}

fn failing_action(value: String) -> String {
  drop(value);
  panic!("boom")
}

#[derive(Clone, Debug, PartialEq, Eq)]
struct ImmutableContext {
  value: String,
  error: String,
}

#[test]
fn run_returns_context_and_detailed_uses_same_semantics() {
  let mut pipeline = Pipeline::new("simple", true);
  pipeline.add_pre_action(|value: String| format!("{value}P"));
  pipeline.add_action(|value: String| format!("{value}A"));
  pipeline.add_post_action(|value: String| format!("{value}Z"));

  assert_eq!(pipeline.run("X".to_string()), "XPAZ");
  let result = pipeline.run_detailed("X".to_string());
  assert_eq!(result.context, "XPAZ");
  assert_eq!(result.action_timings.len(), 3);
}

#[test]
fn short_circuit_preserves_phase_rules() {
  let mut main = Pipeline::new("main", true);
  main.add_action(append_and_stop);
  main.add_action(|value: String| format!("{value}B"));
  main.add_post_action(|value: String| format!("{value}P"));
  assert_eq!(main.run("X".to_string()), "XSP");

  let mut pre = Pipeline::new("pre", true);
  pre.add_pre_action(append_and_stop);
  pre.add_pre_action(|value: String| format!("{value}2"));
  pre.add_action(|value: String| format!("{value}M"));
  pre.add_post_action(|value: String| format!("{value}P"));
  assert_eq!(pre.run("X".to_string()), "XS2P");

  let mut post = Pipeline::new("post", true);
  post.add_action(|value: String| format!("{value}M"));
  post.add_post_action(append_and_stop);
  post.add_post_action(|value: String| format!("{value}2"));
  let result = post.run_detailed("X".to_string());
  assert_eq!(result.context, "XMS2");
  assert!(result.short_circuited);
}

#[test]
fn exception_policies_capture_errors_and_run_post_actions() {
  let mut continuing = Pipeline::new("continue", false);
  continuing.add_action(|value: String| format!("{value}A"));
  continuing.add_action(failing_action);
  continuing.add_action(|value: String| format!("{value}B"));
  let result = continuing.run_detailed("X".to_string());
  assert_eq!(result.context, "XAB");
  assert!(!result.short_circuited);
  assert_eq!(result.errors.len(), 1);
  assert_eq!(result.errors[0].action_index, 1);

  let mut stopping = Pipeline::new("stop", true);
  stopping.add_action(|value: String| format!("{value}A"));
  stopping.add_action(failing_action);
  stopping.add_action(|value: String| format!("{value}B"));
  stopping.add_post_action(|value: String| format!("{value}P"));
  let result = stopping.run_detailed("X".to_string());
  assert_eq!(result.context, "XAP");
  assert!(result.short_circuited);
}

#[test]
fn error_handler_updates_immutable_context() {
  let mut pipeline = Pipeline::new("immutable", false);
  pipeline.on_error_handler(|context: ImmutableContext, error| ImmutableContext {
    value: context.value,
    error: error.message,
  });
  pipeline.add_action(|context: ImmutableContext| {
    drop(context);
    panic!("bad")
  });
  pipeline.add_action(|mut context: ImmutableContext| {
    context.value.push('A');
    context
  });

  let result = pipeline.run_detailed(ImmutableContext {
    value: "X".to_string(),
    error: String::new(),
  });
  assert_eq!(
    result.context,
    ImmutableContext {
      value: "XA".to_string(),
      error: "bad".to_string(),
    }
  );
}

#[test]
fn invalid_error_handler_panics_after_all_post_actions() {
  let calls = Arc::new(Mutex::new(Vec::<String>::new()));
  let mut pipeline = Pipeline::new("bad_handler", true);
  pipeline.on_error_handler(|context: String, _error| {
    drop(context);
    panic!("handler failed")
  });
  pipeline.add_action(failing_action);

  let post_one_calls = calls.clone();
  pipeline.add_post_action(move |value: String| {
    post_one_calls.lock().unwrap().push("post1".to_string());
    value
  });
  let post_two_calls = calls.clone();
  pipeline.add_post_action(move |value: String| {
    post_two_calls.lock().unwrap().push("post2".to_string());
    value
  });

  let result = catch_unwind(AssertUnwindSafe(|| pipeline.run("X".to_string())));
  assert!(result.is_err());
  assert_eq!(
    calls.lock().unwrap().clone(),
    vec!["post1".to_string(), "post2".to_string()]
  );
}

#[test]
fn nested_and_concurrent_runs_keep_control_state_isolated() {
  let mut inner_pipeline = Pipeline::new("inner", true);
  inner_pipeline.add_action(append_and_stop);
  inner_pipeline.add_action(|value: String| format!("{value}I2"));
  inner_pipeline.add_post_action(|value: String| format!("{value}IP"));
  let inner = Arc::new(inner_pipeline);

  let inner_for_outer = inner.clone();
  let mut outer = Pipeline::new("outer", true);
  outer.add_action(move |value: String| {
    format!("{value}:{}", inner_for_outer.run("inner".to_string()))
  });
  outer.add_action(|value: String| format!("{value}O2"));
  assert_eq!(outer.run("outer".to_string()), "outer:innerSIPO2");

  let barrier = Arc::new(Barrier::new(2));
  let mut shared_pipeline = Pipeline::new("shared", true);
  let action_barrier = barrier.clone();
  shared_pipeline.add_action(move |value: String| {
    action_barrier.wait();
    if value == "stop" {
      short_circuit();
    }
    format!("{value}A")
  });
  shared_pipeline.add_action(|value: String| format!("{value}B"));
  shared_pipeline.add_post_action(|value: String| format!("{value}P"));
  let shared = Arc::new(shared_pipeline);

  let stop_pipeline = shared.clone();
  let stop_thread = thread::spawn(move || stop_pipeline.run("stop".to_string()));
  let go_pipeline = shared.clone();
  let go_thread = thread::spawn(move || go_pipeline.run("go".to_string()));

  assert_eq!(stop_thread.join().unwrap(), "stopAP");
  assert_eq!(go_thread.join().unwrap(), "goABP");
}

#[test]
fn freeze_and_legacy_control_adapter_use_the_same_runner() {
  let mut pipeline = Pipeline::new("frozen", true);
  pipeline.add_action(|value: String| format!("{value}A"));
  assert_eq!(pipeline.run("X".to_string()), "XA");
  assert!(pipeline.is_frozen());
  let mutation = catch_unwind(AssertUnwindSafe(|| {
    pipeline.add_action(|value: String| format!("{value}B"));
  }));
  assert!(mutation.is_err());
  assert!(catch_unwind(short_circuit).is_err());

  #[allow(deprecated)]
  {
    let mut legacy = Pipeline::new("legacy", true);
    legacy.add_action_control(|value: String, control: &mut ActionControl<String>| {
      control.short_circuit();
      format!("{value}L")
    });
    legacy.add_action(|value: String| format!("{value}B"));
    assert_eq!(legacy.run("X".to_string()), "XL");
  }
}

struct RecordingObserver {
  events: Arc<Mutex<Vec<String>>>,
}

impl PipelineObserver for RecordingObserver {
  fn on_action_started(&self, _pipeline_name: &str, phase: &str, index: usize, name: &str) {
    self.events.lock().unwrap().push(format!("{phase}:{index}:{name}"));
    short_circuit();
  }
}

#[test]
fn observer_failures_and_control_attempts_do_not_change_semantics() {
  let events = Arc::new(Mutex::new(Vec::new()));
  let mut pipeline = Pipeline::new("observed", true);
  pipeline.observer(RecordingObserver {
    events: events.clone(),
  });
  pipeline.add_action(|value: String| format!("{value}A"));
  pipeline.add_action(|value: String| format!("{value}B"));

  assert_eq!(pipeline.run("X".to_string()), "XAB");
  assert_eq!(
    events.lock().unwrap().clone(),
    vec!["actions:0:s0".to_string(), "actions:1:s1".to_string()]
  );
}

#[test]
fn provider_modes_are_eager_fixed_and_round_robin() {
  let created = Arc::new(AtomicUsize::new(0));
  let factory_counter = created.clone();
  let factory = move || {
    let instance_id = factory_counter.fetch_add(1, Ordering::SeqCst) + 1;
    let mut pipeline = Pipeline::new(format!("p{instance_id}"), true);
    pipeline.add_action(move |value: String| format!("{value}{instance_id}"));
    pipeline
  };

  let pooled = PipelineProvider::pooled(factory, 3);
  assert_eq!(created.load(Ordering::SeqCst), 3);
  assert_eq!(pooled.mode(), PipelineProviderMode::Pooled);
  assert_eq!(pooled.instance_count(), 3);
  assert_eq!(
    (0..5).map(|_| pooled.run(String::new())).collect::<Vec<_>>(),
    vec!["1", "2", "3", "1", "2"]
  );

  let singleton: PipelineProvider<String> =
    PipelineProvider::singleton(Pipeline::<String>::new("singleton", true));
  assert!(Arc::ptr_eq(&singleton.get_pipeline(), &singleton.get_pipeline()));

  let per_event = PipelineProvider::new_instance_per_event(|| Pipeline::<String>::new("event", true));
  assert!(!Arc::ptr_eq(&per_event.get_pipeline(), &per_event.get_pipeline()));
}

#[test]
fn router_selects_provider_without_changing_lifecycle() {
  let trade: Arc<PipelineProvider<String>> = Arc::new(PipelineProvider::singleton(
    Pipeline::<String>::new("trade", true),
  ));
  let quote: Arc<PipelineProvider<String>> = Arc::new(PipelineProvider::singleton(
    Pipeline::<String>::new("quote", true),
  ));
  let trade_route = trade.clone();
  let quote_route = quote.clone();
  let router = PipelineRouter::new(move |event: &String| {
    if event == "TRADE" {
      trade_route.clone()
    } else {
      quote_route.clone()
    }
  });

  assert!(Arc::ptr_eq(&router.get_pipeline_provider(&"TRADE".to_string()), &trade));
  assert!(Arc::ptr_eq(&router.get_pipeline_provider(&"QUOTE".to_string()), &quote));
}

#[test]
fn json_loader_supports_canonical_action_arrays() {
  let mut registry: PipelineRegistry<String> = PipelineRegistry::new();
  registry.register_unary("identity", identity_action);

  let json_text = r#"
{
  "pipeline": "t",
  "type": "unary",
  "preActions": [{"$local": "identity"}],
  "actions": [{"$local": "identity"}],
  "postActions": [{"$local": "identity"}]
}
"#;

  let pipeline = PipelineJsonLoader::new()
    .load_str(json_text, &registry)
    .expect("loader failed");
  assert_eq!(pipeline.run("ok".to_string()), "ok");
}

#[test]
fn remote_actions_use_the_ordinary_pipeline_runner() {
  let server = FixtureServer::start();
  let endpoint = server.url("/remote_hello.txt");

  let mut spec = RemoteSpec::new(endpoint.clone());
  spec.method = "GET".to_string();
  assert_eq!(
    http_step(&spec, &"ignored").expect("http_step failed"),
    REMOTE_FIXTURE_BODY.to_string()
  );

  let json_text = format!(
    r#"
{{
  "pipeline": "t",
  "type": "unary",
  "actions": [{{"$remote": {{"endpoint": "{endpoint}", "method": "GET"}}}}]
}}
"#
  );
  let pipeline = PipelineJsonLoader::new()
    .load_str(&json_text, &PipelineRegistry::new())
    .expect("loader failed");
  assert_eq!(pipeline.run("ignored".to_string()), REMOTE_FIXTURE_BODY);
}

fn handle_fixture_connection(mut stream: TcpStream) {
  let mut request_buffer = [0_u8; 2048];
  let bytes_read = stream.read(&mut request_buffer).unwrap_or(0);
  let request_text = String::from_utf8_lossy(&request_buffer[..bytes_read]);
  let request_line = request_text.lines().next().unwrap_or("");
  let mut parts = request_line.split_whitespace();
  let method = parts.next().unwrap_or("");
  let path = parts.next().unwrap_or("");
  let found = method == "GET" && path == "/remote_hello.txt";
  let response_body = if found { REMOTE_FIXTURE_BODY } else { "not found" };
  let status_line = if found { "HTTP/1.1 200 OK" } else { "HTTP/1.1 404 Not Found" };
  let response_text = format!(
    "{status_line}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{response_body}",
    response_body.len()
  );
  let _ = stream.write_all(response_text.as_bytes());
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
        Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
          thread::sleep(Duration::from_millis(5));
        }
        Err(_) => break,
      }
    });
    Self {
      address: format!("http://{address}"),
      shutdown_sender,
      join_handle: Some(join_handle),
    }
  }

  fn url(&self, path: &str) -> String {
    format!("{}{}", self.address, path)
  }
}

impl Drop for FixtureServer {
  fn drop(&mut self) {
    let _ = self.shutdown_sender.send(());
    if let Some(handle) = self.join_handle.take() {
      let _ = handle.join();
    }
  }
}
