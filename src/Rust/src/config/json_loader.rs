use std::collections::HashMap;
use std::path::{Path, PathBuf};

use crate::core::pipeline::{ActionControl, Pipeline};
use crate::core::registry::PipelineRegistry;
use crate::remote::http_step::{http_step, RemoteDefaults, RemoteSpec};

pub struct PipelineJsonLoader;

impl PipelineJsonLoader {
  pub fn new() -> Self {
    Self
  }

  pub fn load_str(
    &self,
    json_text: &str,
    registry: &PipelineRegistry<String>,
  ) -> Result<Pipeline<String>, String> {
    let spec: serde_json::Value =
      serde_json::from_str(json_text).map_err(|error| format!("Invalid JSON: {error}"))?;
    if spec_contains_prompt_actions(&spec) {
      return Err(
        "Pipeline contains $prompt Actions. Run prompt codegen and load the compiled JSON under pipelines/generated/rust/."
          .to_string(),
      );
    }
    self.build_from_spec(&spec, registry)
  }

  pub fn load_file(
    &self,
    file_path: &str,
    registry: &PipelineRegistry<String>,
  ) -> Result<Pipeline<String>, String> {
    let text_value = std::fs::read_to_string(file_path)
      .map_err(|error| format!("Failed to read file '{file_path}': {error}"))?;
    let spec: serde_json::Value =
      serde_json::from_str(&text_value).map_err(|error| format!("Invalid JSON: {error}"))?;
    let pipeline_name = spec
      .as_object()
      .and_then(|object| object.get("pipeline"))
      .and_then(serde_json::Value::as_str)
      .unwrap_or("pipeline");

    if spec_contains_prompt_actions(&spec) {
      let compiled_path = resolve_compiled_pipeline_path(file_path, pipeline_name, "rust")?;
      let compiled_text = std::fs::read_to_string(&compiled_path).map_err(|error| {
        format!(
          "Pipeline contains $prompt Actions but compiled JSON was not found. Run prompt codegen. Expected compiled Pipeline at: {} ({error})",
          compiled_path.display()
        )
      })?;
      return self.load_str(&compiled_text, registry);
    }
    self.load_str(&text_value, registry)
  }

  pub fn build_from_spec(
    &self,
    spec: &serde_json::Value,
    registry: &PipelineRegistry<String>,
  ) -> Result<Pipeline<String>, String> {
    let object = spec
      .as_object()
      .ok_or_else(|| "Pipeline spec must be a JSON object".to_string())?;
    let pipeline_name = object
      .get("pipeline")
      .and_then(serde_json::Value::as_str)
      .unwrap_or("pipeline");
    let pipeline_type = object
      .get("type")
      .and_then(serde_json::Value::as_str)
      .unwrap_or("unary");
    if pipeline_type != "unary" {
      return Err("Only 'unary' Pipelines are supported by this loader".to_string());
    }

    let short_circuit_on_exception = object
      .get("shortCircuitOnException")
      .or_else(|| object.get("shortCircuit"))
      .and_then(serde_json::Value::as_bool)
      .unwrap_or(true);
    let mut pipeline = Pipeline::new(pipeline_name, short_circuit_on_exception);

    let mut remote_defaults = RemoteDefaults::default();
    if let Some(defaults) = object.get("remoteDefaults") {
      remote_defaults = parse_remote_defaults(defaults, remote_defaults)?;
    }

    add_section(
      first_section(object, "preActions", "pre")?,
      "preActions",
      &mut pipeline,
      registry,
      &remote_defaults,
    )?;
    add_section(
      first_section(object, "actions", "steps")?,
      "actions",
      &mut pipeline,
      registry,
      &remote_defaults,
    )?;
    add_section(
      first_section(object, "postActions", "post")?,
      "postActions",
      &mut pipeline,
      registry,
      &remote_defaults,
    )?;
    Ok(pipeline)
  }
}

impl Default for PipelineJsonLoader {
  fn default() -> Self {
    Self::new()
  }
}

fn first_section<'a>(
  object: &'a serde_json::Map<String, serde_json::Value>,
  canonical_name: &str,
  legacy_name: &str,
) -> Result<&'a [serde_json::Value], String> {
  let value = object
    .get(canonical_name)
    .or_else(|| object.get(legacy_name));
  match value {
    None => Ok(&[]),
    Some(value) => value
      .as_array()
      .map(Vec::as_slice)
      .ok_or_else(|| format!("'{canonical_name}' must be an array")),
  }
}

fn add_section(
  nodes: &[serde_json::Value],
  phase: &str,
  pipeline: &mut Pipeline<String>,
  registry: &PipelineRegistry<String>,
  remote_defaults: &RemoteDefaults,
) -> Result<(), String> {
  for node in nodes {
    add_action(node, phase, pipeline, registry, remote_defaults)?;
  }
  Ok(())
}

fn add_action(
  node: &serde_json::Value,
  phase: &str,
  pipeline: &mut Pipeline<String>,
  registry: &PipelineRegistry<String>,
  remote_defaults: &RemoteDefaults,
) -> Result<(), String> {
  let object = node
    .as_object()
    .ok_or_else(|| "Each Action must be a JSON object".to_string())?;
  if object.get("$prompt").is_some() {
    return Err(
      "Runtime does not execute $prompt Actions. Run prompt codegen to produce compiled Pipeline JSON with $local references."
        .to_string(),
    );
  }

  let display_name = object
    .get("name")
    .or_else(|| object.get("label"))
    .and_then(serde_json::Value::as_str)
    .unwrap_or("")
    .to_string();

  if let Some(local_value) = object.get("$local") {
    let local_ref = local_value
      .as_str()
      .ok_or_else(|| "$local must be a string".to_string())?;
    return add_local(local_ref, &display_name, phase, pipeline, registry);
  }

  if let Some(remote_node) = object.get("$remote") {
    let spec = parse_remote_spec(remote_node, remote_defaults)?;
    add_remote(spec, &display_name, phase, pipeline);
    return Ok(());
  }

  Err("Unsupported Action: expected '$local' or '$remote'".to_string())
}

fn add_local(
  local_ref: &str,
  display_name: &str,
  phase: &str,
  pipeline: &mut Pipeline<String>,
  registry: &PipelineRegistry<String>,
) -> Result<(), String> {
  if registry.has_unary(local_ref) {
    let action = registry.get_unary(local_ref)?;
    let wrapped = move |context: String| action(context);
    match phase {
      "preActions" => pipeline.add_pre_action_named(display_name, wrapped),
      "postActions" => pipeline.add_post_action_named(display_name, wrapped),
      _ => pipeline.add_action_named(display_name, wrapped),
    };
    return Ok(());
  }

  if registry.has_action(local_ref) {
    let action = registry.get_action(local_ref)?;
    let wrapped = move |context: String, control: &mut ActionControl<String>| action(context, control);
    #[allow(deprecated)]
    match phase {
      "preActions" => pipeline.add_pre_action_control_named(display_name, wrapped),
      "postActions" => pipeline.add_post_action_control_named(display_name, wrapped),
      _ => pipeline.add_action_control_named(display_name, wrapped),
    };
    return Ok(());
  }

  if local_ref.starts_with("prompt:") {
    return Err(format!(
      "Prompt-generated Action is missing from the registry: {local_ref}. Run prompt codegen and register generated Actions."
    ));
  }
  Err(format!("Unknown $local reference: {local_ref}"))
}

fn add_remote(spec: RemoteSpec, display_name: &str, phase: &str, pipeline: &mut Pipeline<String>) {
  let action = move |context: String| match http_step(&spec, &context) {
    Ok(response) => response,
    Err(message) => panic!("{message}"),
  };
  match phase {
    "preActions" => pipeline.add_pre_action_named(display_name, action),
    "postActions" => pipeline.add_post_action_named(display_name, action),
    _ => pipeline.add_action_named(display_name, action),
  };
}

fn spec_contains_prompt_actions(spec: &serde_json::Value) -> bool {
  let Some(object) = spec.as_object() else {
    return false;
  };
  for section_name in [
    "preActions",
    "pre",
    "actions",
    "steps",
    "postActions",
    "post",
  ] {
    let Some(nodes) = object.get(section_name).and_then(serde_json::Value::as_array) else {
      continue;
    };
    if nodes.iter().any(|node| {
      node
        .as_object()
        .map(|action| action.get("$prompt").is_some())
        .unwrap_or(false)
    }) {
      return true;
    }
  }
  false
}

fn resolve_compiled_pipeline_path(
  source_file_path: &str,
  pipeline_name: &str,
  language_name: &str,
) -> Result<PathBuf, String> {
  let source_path = Path::new(source_file_path)
    .canonicalize()
    .map_err(|error| format!("Failed to resolve Pipeline path '{source_file_path}': {error}"))?;
  let mut current = source_path.parent().map(Path::to_path_buf);
  while let Some(directory) = current {
    if directory.file_name().and_then(|value| value.to_str()) == Some("pipelines") {
      return Ok(
        directory
          .join("generated")
          .join(language_name)
          .join(format!("{pipeline_name}.json")),
      );
    }
    current = directory.parent().map(Path::to_path_buf);
  }
  Err(format!(
    "Pipeline contains $prompt Actions but the pipelines root directory could not be inferred from path: {}",
    source_path.display()
  ))
}

fn parse_remote_spec(node: &serde_json::Value, defaults: &RemoteDefaults) -> Result<RemoteSpec, String> {
  if let Some(endpoint) = node.as_str() {
    return Ok(defaults.to_spec(endpoint));
  }
  let object = node
    .as_object()
    .ok_or_else(|| "$remote must be a string or object".to_string())?;
  let endpoint = object
    .get("endpoint")
    .or_else(|| object.get("path"))
    .and_then(serde_json::Value::as_str)
    .ok_or_else(|| "Missing required $remote field: endpoint|path".to_string())?;
  let mut spec = defaults.to_spec(endpoint);
  if let Some(timeout) = object
    .get("timeoutMillis")
    .or_else(|| object.get("timeout_millis"))
    .and_then(serde_json::Value::as_u64)
  {
    spec.timeout_millis = timeout;
  }
  if let Some(retries) = object.get("retries").and_then(serde_json::Value::as_u64) {
    spec.retries = retries as usize;
  }
  if let Some(method) = object.get("method").and_then(serde_json::Value::as_str) {
    spec.method = method.to_string();
  }
  if let Some(headers) = object.get("headers") {
    spec.headers = Some(parse_headers(headers, spec.headers.as_ref())?);
  }
  Ok(spec)
}

fn parse_remote_defaults(node: &serde_json::Value, mut defaults: RemoteDefaults) -> Result<RemoteDefaults, String> {
  let object = node
    .as_object()
    .ok_or_else(|| "remoteDefaults must be a JSON object".to_string())?;
  if let Some(base_url) = object
    .get("baseUrl")
    .or_else(|| object.get("endpointBase"))
    .and_then(serde_json::Value::as_str)
  {
    defaults.base_url = base_url.to_string();
  }
  if let Some(timeout) = object
    .get("timeoutMillis")
    .or_else(|| object.get("timeout_millis"))
    .and_then(serde_json::Value::as_u64)
  {
    defaults.timeout_millis = timeout;
  }
  if let Some(retries) = object.get("retries").and_then(serde_json::Value::as_u64) {
    defaults.retries = retries as usize;
  }
  if let Some(method) = object.get("method").and_then(serde_json::Value::as_str) {
    defaults.method = method.to_string();
  }
  if let Some(headers) = object.get("headers") {
    defaults.headers = Some(parse_headers(headers, defaults.headers.as_ref())?);
  }
  Ok(defaults)
}

fn parse_headers(
  node: &serde_json::Value,
  base: Option<&HashMap<String, String>>,
) -> Result<HashMap<String, String>, String> {
  let object = node
    .as_object()
    .ok_or_else(|| "headers must be a JSON object".to_string())?;
  let mut headers = base.cloned().unwrap_or_default();
  for (name, value) in object {
    if let Some(value) = value.as_str() {
      headers.insert(name.clone(), value.to_string());
    }
  }
  Ok(headers)
}
