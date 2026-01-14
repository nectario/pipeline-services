use std::collections::HashMap;

use crate::core::pipeline::Pipeline;
use crate::core::registry::PipelineRegistry;
use crate::remote::http_step::{http_step, RemoteDefaults, RemoteSpec};

pub struct PipelineJsonLoader;

impl PipelineJsonLoader {
  pub fn new() -> Self {
    Self
  }

  pub fn load_str(&self, json_text: &str, registry: &PipelineRegistry<String>) -> Result<Pipeline<String>, String> {
    let spec: serde_json::Value =
      serde_json::from_str(json_text).map_err(|error| format!("Invalid JSON: {error}"))?;
    self.build_from_spec(&spec, registry)
  }

  pub fn load_file(&self, file_path: &str, registry: &PipelineRegistry<String>) -> Result<Pipeline<String>, String> {
    let text_value = std::fs::read_to_string(file_path)
      .map_err(|error| format!("Failed to read file '{file_path}': {error}"))?;
    self.load_str(&text_value, registry)
  }

  pub fn build_from_spec(
    &self,
    spec: &serde_json::Value,
    registry: &PipelineRegistry<String>,
  ) -> Result<Pipeline<String>, String> {
    let spec_object = spec
      .as_object()
      .ok_or_else(|| "Pipeline spec must be a JSON object".to_string())?;

    let pipeline_name = spec_object
      .get("pipeline")
      .and_then(|value| value.as_str())
      .unwrap_or("pipeline")
      .to_string();

    let pipeline_type = spec_object.get("type").and_then(|value| value.as_str()).unwrap_or("unary");
    if pipeline_type != "unary" {
      return Err("Only 'unary' pipelines are supported by this loader".to_string());
    }

    let short_circuit_on_exception = parse_short_circuit_on_exception(spec_object);
    let mut pipeline = Pipeline::new(pipeline_name, short_circuit_on_exception);

    let mut remote_defaults = RemoteDefaults::default();
    if let Some(defaults_node) = spec_object.get("remoteDefaults") {
      remote_defaults = parse_remote_defaults(defaults_node, remote_defaults)?;
    }

    add_section(spec_object, "pre", &mut pipeline, registry, &remote_defaults)?;
    if spec_object.get("actions").is_some() {
      add_section(spec_object, "actions", &mut pipeline, registry, &remote_defaults)?;
    } else {
      add_section(spec_object, "steps", &mut pipeline, registry, &remote_defaults)?;
    }
    add_section(spec_object, "post", &mut pipeline, registry, &remote_defaults)?;

    Ok(pipeline)
  }
}

impl Default for PipelineJsonLoader {
  fn default() -> Self {
    Self::new()
  }
}

fn parse_short_circuit_on_exception(spec_object: &serde_json::Map<String, serde_json::Value>) -> bool {
  if let Some(value) = spec_object.get("shortCircuitOnException") {
    return value.as_bool().unwrap_or(true);
  }
  if let Some(value) = spec_object.get("shortCircuit") {
    return value.as_bool().unwrap_or(true);
  }
  true
}

fn add_section(
  spec_object: &serde_json::Map<String, serde_json::Value>,
  section_name: &str,
  pipeline: &mut Pipeline<String>,
  registry: &PipelineRegistry<String>,
  remote_defaults: &RemoteDefaults,
) -> Result<(), String> {
  let nodes = match spec_object.get(section_name) {
    Some(value) => value.as_array().cloned().unwrap_or_default(),
    None => return Ok(()),
  };

  for node in nodes {
    add_step(&node, section_name, pipeline, registry, remote_defaults)?;
  }
  Ok(())
}

fn add_step(
  node: &serde_json::Value,
  section_name: &str,
  pipeline: &mut Pipeline<String>,
  registry: &PipelineRegistry<String>,
  remote_defaults: &RemoteDefaults,
) -> Result<(), String> {
  let node_object = node
    .as_object()
    .ok_or_else(|| "Each action must be a JSON object".to_string())?;

  let display_name = node_object
    .get("name")
    .and_then(|value| value.as_str())
    .or_else(|| node_object.get("label").and_then(|value| value.as_str()))
    .unwrap_or("")
    .to_string();

  if let Some(local_ref_value) = node_object.get("$local") {
    let local_ref = local_ref_value
      .as_str()
      .ok_or_else(|| "$local must be a string".to_string())?;
    add_local(local_ref, &display_name, section_name, pipeline, registry)?;
    return Ok(());
  }

  if let Some(remote_node) = node_object.get("$remote") {
    let remote_spec = parse_remote_spec(remote_node, remote_defaults)?;
    add_remote(remote_spec, &display_name, section_name, pipeline);
    return Ok(());
  }

  Err("Unsupported action: expected '$local' or '$remote'".to_string())
}

fn add_local(
  local_ref: &str,
  display_name: &str,
  section_name: &str,
  pipeline: &mut Pipeline<String>,
  registry: &PipelineRegistry<String>,
) -> Result<(), String> {
  if registry.has_unary(local_ref) {
    let unary_action = registry.get_unary(local_ref)?;
    let wrapped = {
      let unary_action = unary_action.clone();
      move |ctx: String| (unary_action)(ctx)
    };

    if section_name == "pre" {
      pipeline.add_pre_action_named(display_name.to_string(), wrapped);
    } else if section_name == "post" {
      pipeline.add_post_action_named(display_name.to_string(), wrapped);
    } else {
      pipeline.add_action_named(display_name.to_string(), wrapped);
    }
    return Ok(());
  }

  if registry.has_action(local_ref) {
    let step_action = registry.get_action(local_ref)?;
    let wrapped = {
      let step_action = step_action.clone();
      move |ctx: String, control: &mut crate::core::pipeline::StepControl<String>| (step_action)(ctx, control)
    };

    if section_name == "pre" {
      pipeline.add_pre_action_control_named(display_name.to_string(), wrapped);
    } else if section_name == "post" {
      pipeline.add_post_action_control_named(display_name.to_string(), wrapped);
    } else {
      pipeline.add_action_control_named(display_name.to_string(), wrapped);
    }
    return Ok(());
  }

  Err(format!("Unknown $local reference: {local_ref}"))
}

fn add_remote(spec: RemoteSpec, display_name: &str, section_name: &str, pipeline: &mut Pipeline<String>) {
  let wrapped = move |ctx: String| match http_step(&spec, &ctx) {
    Ok(response_body) => response_body,
    Err(message) => panic!("{message}"),
  };

  if section_name == "pre" {
    pipeline.add_pre_action_named(display_name.to_string(), wrapped);
  } else if section_name == "post" {
    pipeline.add_post_action_named(display_name.to_string(), wrapped);
  } else {
    pipeline.add_action_named(display_name.to_string(), wrapped);
  }
}

fn parse_remote_spec(remote_node: &serde_json::Value, remote_defaults: &RemoteDefaults) -> Result<RemoteSpec, String> {
  if let Some(endpoint_value) = remote_node.as_str() {
    return Ok(remote_defaults.to_spec(endpoint_value));
  }

  let remote_object = remote_node
    .as_object()
    .ok_or_else(|| "$remote must be a string or an object".to_string())?;

  let endpoint_value = remote_object
    .get("endpoint")
    .and_then(|value| value.as_str())
    .or_else(|| remote_object.get("path").and_then(|value| value.as_str()))
    .ok_or_else(|| "Missing required $remote field: endpoint|path".to_string())?;

  let mut remote_spec = remote_defaults.to_spec(endpoint_value);

  if let Some(timeout_value) = remote_object
    .get("timeoutMillis")
    .and_then(|value| value.as_u64())
    .or_else(|| remote_object.get("timeout_millis").and_then(|value| value.as_u64()))
  {
    remote_spec.timeout_millis = timeout_value;
  }

  if let Some(retries_value) = remote_object.get("retries").and_then(|value| value.as_u64()) {
    remote_spec.retries = retries_value as usize;
  }

  if let Some(method_value) = remote_object.get("method").and_then(|value| value.as_str()) {
    remote_spec.method = method_value.to_string();
  }

  if let Some(headers_value) = remote_object.get("headers") {
    let headers_object = headers_value
      .as_object()
      .ok_or_else(|| "$remote.headers must be an object".to_string())?;

    let mut merged_headers: HashMap<String, String> = HashMap::new();
    if let Some(base_headers) = &remote_spec.headers {
      for (header_name, header_value) in base_headers {
        merged_headers.insert(header_name.clone(), header_value.clone());
      }
    }

    for (header_name, header_value) in headers_object {
      if let Some(value_string) = header_value.as_str() {
        merged_headers.insert(header_name.clone(), value_string.to_string());
      }
    }

    remote_spec.headers = Some(merged_headers);
  }

  Ok(remote_spec)
}

fn parse_remote_defaults(node: &serde_json::Value, base: RemoteDefaults) -> Result<RemoteDefaults, String> {
  let defaults_object = node
    .as_object()
    .ok_or_else(|| "remoteDefaults must be a JSON object".to_string())?;

  let mut defaults = base;
  if let Some(base_url_value) = defaults_object
    .get("baseUrl")
    .and_then(|value| value.as_str())
    .or_else(|| defaults_object.get("endpointBase").and_then(|value| value.as_str()))
  {
    defaults.base_url = base_url_value.to_string();
  }

  if let Some(timeout_value) = defaults_object
    .get("timeoutMillis")
    .and_then(|value| value.as_u64())
    .or_else(|| defaults_object.get("timeout_millis").and_then(|value| value.as_u64()))
  {
    defaults.timeout_millis = timeout_value;
  }

  if let Some(retries_value) = defaults_object.get("retries").and_then(|value| value.as_u64()) {
    defaults.retries = retries_value as usize;
  }

  if let Some(method_value) = defaults_object.get("method").and_then(|value| value.as_str()) {
    defaults.method = method_value.to_string();
  }

  if let Some(headers_value) = defaults_object.get("headers") {
    let headers_object = headers_value
      .as_object()
      .ok_or_else(|| "remoteDefaults.headers must be an object".to_string())?;
    let mut headers_map: HashMap<String, String> = HashMap::new();
    for (header_name, header_value) in headers_object {
      if let Some(value_string) = header_value.as_str() {
        headers_map.insert(header_name.clone(), value_string.to_string());
      }
    }
    defaults.headers = Some(headers_map);
  }

  Ok(defaults)
}

