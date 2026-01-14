use std::collections::HashMap;
use std::time::Duration;

use reqwest::blocking::Client;
use reqwest::header::{HeaderMap, HeaderName, HeaderValue};
use reqwest::Method;
use serde::Serialize;

#[derive(Clone, Debug)]
pub struct RemoteSpec {
  pub endpoint: String,
  pub timeout_millis: u64,
  pub retries: usize,
  pub method: String,
  pub headers: Option<HashMap<String, String>>,
}

impl RemoteSpec {
  pub fn new(endpoint: impl Into<String>) -> Self {
    Self {
      endpoint: endpoint.into(),
      timeout_millis: 1000,
      retries: 0,
      method: "POST".to_string(),
      headers: None,
    }
  }
}

#[derive(Clone, Debug)]
pub struct RemoteDefaults {
  pub base_url: String,
  pub timeout_millis: u64,
  pub retries: usize,
  pub method: String,
  pub headers: Option<HashMap<String, String>>,
}

impl Default for RemoteDefaults {
  fn default() -> Self {
    Self {
      base_url: "".to_string(),
      timeout_millis: 1000,
      retries: 0,
      method: "POST".to_string(),
      headers: None,
    }
  }
}

impl RemoteDefaults {
  pub fn resolve_endpoint(&self, endpoint_or_path: &str) -> String {
    if endpoint_or_path.starts_with("http://") || endpoint_or_path.starts_with("https://") {
      return endpoint_or_path.to_string();
    }
    if self.base_url.is_empty() {
      return endpoint_or_path.to_string();
    }
    if self.base_url.ends_with('/') && endpoint_or_path.starts_with('/') {
      return format!("{}{}", self.base_url, &endpoint_or_path[1..]);
    }
    if !self.base_url.ends_with('/') && !endpoint_or_path.starts_with('/') {
      return format!("{}/{}", self.base_url, endpoint_or_path);
    }
    format!("{}{}", self.base_url, endpoint_or_path)
  }

  pub fn to_spec(&self, endpoint_or_path: &str) -> RemoteSpec {
    let resolved_endpoint = self.resolve_endpoint(endpoint_or_path);
    let mut spec = RemoteSpec::new(resolved_endpoint);
    spec.timeout_millis = self.timeout_millis;
    spec.retries = self.retries;
    spec.method = self.method.clone();
    spec.headers = self.headers.clone();
    spec
  }
}

fn build_header_map(headers: &HashMap<String, String>) -> Result<HeaderMap, String> {
  let mut header_map = HeaderMap::new();
  for (header_name, header_value) in headers {
    let name_value = HeaderName::from_bytes(header_name.as_bytes())
      .map_err(|error| format!("Invalid header name '{header_name}': {error}"))?;
    let value_value = HeaderValue::from_str(header_value)
      .map_err(|error| format!("Invalid header value for '{header_name}': {error}"))?;
    header_map.insert(name_value, value_value);
  }
  Ok(header_map)
}

fn parse_method(method_value: &str) -> Result<Method, String> {
  let normalized = method_value.trim().to_uppercase();
  Method::from_bytes(normalized.as_bytes()).map_err(|error| format!("Invalid HTTP method '{method_value}': {error}"))
}

pub fn http_step<InputType: Serialize>(spec: &RemoteSpec, input_value: &InputType) -> Result<String, String> {
  let client = Client::builder()
    .timeout(Duration::from_millis(spec.timeout_millis))
    .build()
    .map_err(|error| format!("Failed to build HTTP client: {error}"))?;

  let json_body = serde_json::to_string(input_value).map_err(|error| format!("JSON encode failed: {error}"))?;
  let mut last_error_message = String::new();

  let mut attempt_index: usize = 0;
  while attempt_index < (spec.retries + 1) {
    let request_method = match parse_method(&spec.method) {
      Ok(value) => value,
      Err(message) => return Err(message),
    };

    let mut header_map = HeaderMap::new();
    if let Some(headers_value) = &spec.headers {
      header_map = build_header_map(headers_value)?;
    }

    let request_result = if request_method == Method::GET {
      client.request(Method::GET, &spec.endpoint).headers(header_map).send()
    } else {
      header_map.insert(
        reqwest::header::CONTENT_TYPE,
        HeaderValue::from_static("application/json"),
      );
      client
        .request(request_method, &spec.endpoint)
        .headers(header_map)
        .body(json_body.clone())
        .send()
    };

    match request_result {
      Ok(response) => {
        let status_code = response.status();
        let response_body = response.text().unwrap_or_else(|error| format!("(read body failed: {error})"));
        if !status_code.is_success() {
          last_error_message = format!("HTTP {status_code} body={response_body}");
        } else {
          return Ok(response_body);
        }
      }
      Err(error) => {
        last_error_message = error.to_string();
      }
    }

    if attempt_index < spec.retries {
      let backoff_millis = 50u64 * ((attempt_index + 1) as u64);
      std::thread::sleep(Duration::from_millis(backoff_millis));
    }
    attempt_index += 1;
  }

  Err(last_error_message)
}

