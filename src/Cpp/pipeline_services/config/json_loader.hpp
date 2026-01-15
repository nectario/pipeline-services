#pragma once

#include <fstream>
#include <map>
#include <stdexcept>
#include <string>
#include <string_view>

#include <nlohmann/json.hpp>

#include "pipeline_services/core/pipeline.hpp"
#include "pipeline_services/core/registry.hpp"
#include "pipeline_services/remote/http_step.hpp"

namespace pipeline_services::config {

namespace detail {

inline bool parse_short_circuit_on_exception(const nlohmann::json& spec_value) {
  if (spec_value.contains("shortCircuitOnException")) {
    return spec_value.at("shortCircuitOnException").get<bool>();
  }
  if (spec_value.contains("shortCircuit")) {
    return spec_value.at("shortCircuit").get<bool>();
  }
  return true;
}

inline remote::RemoteDefaults parse_remote_defaults(const nlohmann::json& node_value, remote::RemoteDefaults base) {
  remote::RemoteDefaults defaults = std::move(base);

  if (node_value.contains("baseUrl")) {
    defaults.base_url = node_value.at("baseUrl").get<std::string>();
  } else if (node_value.contains("endpointBase")) {
    defaults.base_url = node_value.at("endpointBase").get<std::string>();
  }

  if (node_value.contains("timeoutMillis")) {
    defaults.timeout_millis = node_value.at("timeoutMillis").get<std::int32_t>();
  } else if (node_value.contains("timeout_millis")) {
    defaults.timeout_millis = node_value.at("timeout_millis").get<std::int32_t>();
  }

  if (node_value.contains("retries")) {
    defaults.retries = node_value.at("retries").get<std::int32_t>();
  }

  if (node_value.contains("method")) {
    defaults.method = node_value.at("method").get<std::string>();
  }

  if (node_value.contains("headers")) {
    std::map<std::string, std::string> headers_map;
    const auto& headers_value = node_value.at("headers");
    for (auto iter = headers_value.begin(); iter != headers_value.end(); ++iter) {
      headers_map[iter.key()] = iter.value().get<std::string>();
    }
    defaults.headers = std::move(headers_map);
  }

  return defaults;
}

inline remote::RemoteSpec<std::string> parse_remote_spec(
  const nlohmann::json& remote_node,
  const remote::RemoteDefaults& remote_defaults
) {
  if (remote_node.is_string()) {
    return remote_defaults.to_spec<std::string>(remote_node.get<std::string>());
  }

  if (!remote_node.is_object()) {
    throw std::runtime_error("$remote must be a string or an object");
  }

  std::string endpoint_value;
  if (remote_node.contains("endpoint")) {
    endpoint_value = remote_node.at("endpoint").get<std::string>();
  } else if (remote_node.contains("path")) {
    endpoint_value = remote_node.at("path").get<std::string>();
  } else {
    throw std::runtime_error("Missing required $remote field: endpoint|path");
  }

  remote::RemoteSpec<std::string> remote_spec = remote_defaults.to_spec<std::string>(endpoint_value);

  if (remote_node.contains("timeoutMillis")) {
    remote_spec.timeout_millis = remote_node.at("timeoutMillis").get<std::int32_t>();
  } else if (remote_node.contains("timeout_millis")) {
    remote_spec.timeout_millis = remote_node.at("timeout_millis").get<std::int32_t>();
  }

  if (remote_node.contains("retries")) {
    remote_spec.retries = remote_node.at("retries").get<std::int32_t>();
  }

  if (remote_node.contains("method")) {
    remote_spec.method = remote_node.at("method").get<std::string>();
  }

  if (remote_node.contains("headers")) {
    std::map<std::string, std::string> merged_headers;
    if (remote_spec.headers.has_value()) {
      merged_headers = remote_spec.headers.value();
    }

    const auto& headers_value = remote_node.at("headers");
    for (auto iter = headers_value.begin(); iter != headers_value.end(); ++iter) {
      merged_headers[iter.key()] = iter.value().get<std::string>();
    }
    remote_spec.headers = std::move(merged_headers);
  }

  return remote_spec;
}

inline void add_local(
  const std::string& local_ref,
  const std::string& display_name,
  const std::string& section_name,
  core::Pipeline<std::string>& pipeline,
  const core::PipelineRegistry<std::string>& registry
) {
  if (registry.has_unary(local_ref)) {
    auto unary_action = registry.get_unary(local_ref);
    if (section_name == "pre") {
      pipeline.add_pre_action_named(display_name, unary_action);
    } else if (section_name == "post") {
      pipeline.add_post_action_named(display_name, unary_action);
    } else {
      pipeline.add_action_named(display_name, unary_action);
    }
    return;
  }

  if (registry.has_action(local_ref)) {
    auto step_action = registry.get_action(local_ref);
    if (section_name == "pre") {
      pipeline.add_pre_action_named(display_name, step_action);
    } else if (section_name == "post") {
      pipeline.add_post_action_named(display_name, step_action);
    } else {
      pipeline.add_action_named(display_name, step_action);
    }
    return;
  }

  throw std::runtime_error("Unknown $local reference: " + local_ref);
}

inline void add_remote(
  const remote::RemoteSpec<std::string>& spec,
  const std::string& display_name,
  const std::string& section_name,
  core::Pipeline<std::string>& pipeline
) {
  if (section_name == "pre") {
    pipeline.add_pre_action_named(display_name, spec);
  } else if (section_name == "post") {
    pipeline.add_post_action_named(display_name, spec);
  } else {
    pipeline.add_action_named(display_name, spec);
  }
}

inline void add_step(
  const nlohmann::json& node_value,
  const std::string& section_name,
  core::Pipeline<std::string>& pipeline,
  const core::PipelineRegistry<std::string>& registry,
  const remote::RemoteDefaults& remote_defaults
) {
  if (!node_value.is_object()) {
    throw std::runtime_error("Each action must be a JSON object");
  }

  std::string display_name;
  if (node_value.contains("name")) {
    display_name = node_value.at("name").get<std::string>();
  } else if (node_value.contains("label")) {
    display_name = node_value.at("label").get<std::string>();
  } else {
    display_name = "";
  }

  if (node_value.contains("$local")) {
    const std::string local_ref = node_value.at("$local").get<std::string>();
    add_local(local_ref, display_name, section_name, pipeline, registry);
    return;
  }

  if (node_value.contains("$remote")) {
    const remote::RemoteSpec<std::string> remote_spec = parse_remote_spec(node_value.at("$remote"), remote_defaults);
    add_remote(remote_spec, display_name, section_name, pipeline);
    return;
  }

  throw std::runtime_error("Unsupported action: expected '$local' or '$remote'");
}

inline void add_section(
  const nlohmann::json& spec_value,
  const std::string& section_name,
  core::Pipeline<std::string>& pipeline,
  const core::PipelineRegistry<std::string>& registry,
  const remote::RemoteDefaults& remote_defaults
) {
  if (!spec_value.contains(section_name)) {
    return;
  }

  const nlohmann::json& nodes_value = spec_value.at(section_name);
  if (!nodes_value.is_array()) {
    throw std::runtime_error("Section '" + section_name + "' must be an array");
  }

  for (const auto& node_value : nodes_value) {
    add_step(node_value, section_name, pipeline, registry, remote_defaults);
  }
}

}  // namespace detail

class PipelineJsonLoader {
public:
  PipelineJsonLoader() = default;

  core::Pipeline<std::string> load_str(
    std::string_view json_text,
    const core::PipelineRegistry<std::string>& registry
  ) const;

  core::Pipeline<std::string> load_file(
    const std::string& file_path,
    const core::PipelineRegistry<std::string>& registry
  ) const;
};

inline core::Pipeline<std::string> PipelineJsonLoader::load_str(
  std::string_view json_text,
  const core::PipelineRegistry<std::string>& registry
) const {
  const nlohmann::json spec_value = nlohmann::json::parse(json_text);

  const std::string pipeline_name = spec_value.value("pipeline", "pipeline");
  const std::string pipeline_type = spec_value.value("type", "unary");
  if (pipeline_type != "unary") {
    throw std::runtime_error("Only 'unary' pipelines are supported by this loader");
  }

  const bool short_circuit_on_exception = detail::parse_short_circuit_on_exception(spec_value);
  core::Pipeline<std::string> pipeline(pipeline_name, short_circuit_on_exception);

  remote::RemoteDefaults remote_defaults;
  if (spec_value.contains("remoteDefaults")) {
    remote_defaults = detail::parse_remote_defaults(spec_value.at("remoteDefaults"), remote_defaults);
  }

  detail::add_section(spec_value, "pre", pipeline, registry, remote_defaults);
  if (spec_value.contains("actions")) {
    detail::add_section(spec_value, "actions", pipeline, registry, remote_defaults);
  } else {
    detail::add_section(spec_value, "steps", pipeline, registry, remote_defaults);
  }
  detail::add_section(spec_value, "post", pipeline, registry, remote_defaults);

  return pipeline;
}

inline core::Pipeline<std::string> PipelineJsonLoader::load_file(
  const std::string& file_path,
  const core::PipelineRegistry<std::string>& registry
) const {
  std::ifstream input_stream(file_path);
  if (!input_stream.is_open()) {
    throw std::runtime_error("Failed to open file: " + file_path);
  }

  std::string file_text;
  input_stream.seekg(0, std::ios::end);
  file_text.reserve(static_cast<std::size_t>(input_stream.tellg()));
  input_stream.seekg(0, std::ios::beg);
  file_text.assign((std::istreambuf_iterator<char>(input_stream)), std::istreambuf_iterator<char>());

  return load_str(file_text, registry);
}

}  // namespace pipeline_services::config
