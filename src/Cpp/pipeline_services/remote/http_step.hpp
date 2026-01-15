#pragma once

#include <chrono>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <map>
#include <optional>
#include <stdexcept>
#include <string>
#include <string_view>
#include <thread>
#include <type_traits>

#include <httplib.h>
#include <nlohmann/json.hpp>

namespace pipeline_services::remote {

template <typename ContextType>
std::string default_to_json(const ContextType& ctx) {
  nlohmann::json json_value = ctx;
  return json_value.dump();
}

template <typename ContextType>
ContextType default_from_json(const ContextType& ctx, const std::string& response_body) {
  (void)ctx;
  if constexpr (std::is_same_v<ContextType, std::string>) {
    return response_body;
  } else {
    nlohmann::json json_value = nlohmann::json::parse(response_body);
    return json_value.get<ContextType>();
  }
}

template <typename ContextType>
struct RemoteSpec {
  std::string endpoint;
  std::int32_t timeout_millis;
  std::int32_t retries;
  std::string method;
  std::optional<std::map<std::string, std::string>> headers;

  std::function<std::string(const ContextType&)> to_json;
  std::function<ContextType(const ContextType&, const std::string&)> from_json;

  explicit RemoteSpec(std::string endpoint_value)
    : endpoint(std::move(endpoint_value)),
      timeout_millis(1000),
      retries(0),
      method("POST"),
      headers(std::nullopt),
      to_json(default_to_json<ContextType>),
      from_json(default_from_json<ContextType>) {}
};

struct RemoteDefaults {
  std::string base_url;
  std::int32_t timeout_millis;
  std::int32_t retries;
  std::string method;
  std::optional<std::map<std::string, std::string>> headers;

  RemoteDefaults()
    : base_url(""),
      timeout_millis(1000),
      retries(0),
      method("POST"),
      headers(std::nullopt) {}

  std::string resolve_endpoint(const std::string& endpoint_or_path) const {
    if (endpoint_or_path.rfind("http://", 0) == 0 || endpoint_or_path.rfind("https://", 0) == 0) {
      return endpoint_or_path;
    }
    if (base_url.empty()) {
      return endpoint_or_path;
    }
    if (!base_url.empty() && base_url.back() == '/' && !endpoint_or_path.empty() && endpoint_or_path.front() == '/') {
      return base_url + endpoint_or_path.substr(1);
    }
    if (!base_url.empty() && base_url.back() != '/' && !endpoint_or_path.empty() && endpoint_or_path.front() != '/') {
      return base_url + "/" + endpoint_or_path;
    }
    return base_url + endpoint_or_path;
  }

  template <typename ContextType>
  RemoteSpec<ContextType> to_spec(const std::string& endpoint_or_path) const {
    const std::string resolved_endpoint = resolve_endpoint(endpoint_or_path);
    RemoteSpec<ContextType> spec(resolved_endpoint);
    spec.timeout_millis = timeout_millis;
    spec.retries = retries;
    spec.method = method;
    spec.headers = headers;
    return spec;
  }
};

struct ParsedUrl {
  std::string scheme;
  std::string host;
  int port;
  std::string path;
};

inline ParsedUrl parse_url(const std::string& endpoint) {
  constexpr std::string_view http_prefix = "http://";
  constexpr std::string_view https_prefix = "https://";

  std::string_view endpoint_view(endpoint);
  std::string scheme;
  int default_port = 80;

  if (endpoint_view.rfind(http_prefix, 0) == 0) {
    scheme = "http";
    endpoint_view.remove_prefix(http_prefix.size());
    default_port = 80;
  } else if (endpoint_view.rfind(https_prefix, 0) == 0) {
    scheme = "https";
    endpoint_view.remove_prefix(https_prefix.size());
    default_port = 443;
  } else {
    throw std::runtime_error("Unsupported URL scheme (expected http:// or https://): " + endpoint);
  }

  const std::size_t slash_index = endpoint_view.find('/');
  std::string_view host_port_view = endpoint_view;
  std::string_view path_view = "/";
  if (slash_index != std::string_view::npos) {
    host_port_view = endpoint_view.substr(0, slash_index);
    path_view = endpoint_view.substr(slash_index);
  }

  std::string host;
  int port = default_port;
  const std::size_t colon_index = host_port_view.rfind(':');
  if (colon_index != std::string_view::npos) {
    host = std::string(host_port_view.substr(0, colon_index));
    const std::string port_text = std::string(host_port_view.substr(colon_index + 1));
    port = std::stoi(port_text);
  } else {
    host = std::string(host_port_view);
  }

  if (host.empty()) {
    throw std::runtime_error("Invalid endpoint URL (missing host): " + endpoint);
  }

  return ParsedUrl{
    .scheme = scheme,
    .host = host,
    .port = port,
    .path = std::string(path_view),
  };
}

inline void sleep_ms(std::int32_t delay_millis) {
  std::this_thread::sleep_for(std::chrono::milliseconds(delay_millis));
}

template <typename ContextType>
ContextType http_step(const RemoteSpec<ContextType>& spec, const ContextType& input_value) {
  const ParsedUrl parsed_url = parse_url(spec.endpoint);

  if (parsed_url.scheme != "http") {
    throw std::runtime_error("Only http:// endpoints are supported by this C++ port currently: " + spec.endpoint);
  }

  httplib::Client client(parsed_url.host, parsed_url.port);
  const int timeout_seconds = static_cast<int>(spec.timeout_millis / 1000);
  const int timeout_microseconds = static_cast<int>((spec.timeout_millis % 1000) * 1000);
  client.set_connection_timeout(timeout_seconds, timeout_microseconds);
  client.set_read_timeout(timeout_seconds, timeout_microseconds);
  client.set_write_timeout(timeout_seconds, timeout_microseconds);

  httplib::Headers headers_value;
  if (spec.headers.has_value()) {
    for (const auto& header_item : spec.headers.value()) {
      headers_value.insert(header_item);
    }
  }

  const std::string method_value = spec.method;
  const std::string json_body = spec.to_json(input_value);
  std::string last_error_message;

  std::int32_t attempt_index = 0;
  while (attempt_index < (spec.retries + 1)) {
    bool should_retry = false;
    try {
      if (method_value == "GET") {
        const auto response = client.Get(parsed_url.path.c_str(), headers_value);
        if (!response) {
          throw std::runtime_error("HTTP request failed");
        }
        if (response->status < 200 || response->status >= 300) {
          throw std::runtime_error("HTTP " + std::to_string(response->status) + " body=" + response->body);
        }
        return spec.from_json(input_value, response->body);
      }

      if (method_value != "POST") {
        throw std::runtime_error("Unsupported HTTP method: " + method_value);
      }

      const auto response = client.Post(parsed_url.path.c_str(), headers_value, json_body, "application/json");
      if (!response) {
        throw std::runtime_error("HTTP request failed");
      }
      if (response->status < 200 || response->status >= 300) {
        throw std::runtime_error("HTTP " + std::to_string(response->status) + " body=" + response->body);
      }
      return spec.from_json(input_value, response->body);
    } catch (const std::exception& error) {
      last_error_message = error.what() ? error.what() : "exception";
      should_retry = attempt_index < spec.retries;
    }

    if (should_retry) {
      const std::int32_t backoff_millis = 50 * (attempt_index + 1);
      sleep_ms(backoff_millis);
    }
    attempt_index += 1;
  }

  throw std::runtime_error(last_error_message);
}

}  // namespace pipeline_services::remote
