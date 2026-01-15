#pragma once

#include <cstddef>
#include <cstdint>
#include <exception>
#include <functional>
#include <map>
#include <stdexcept>
#include <string>
#include <string_view>
#include <type_traits>
#include <utility>

#include <httplib.h>
#include <nlohmann/json.hpp>

#include "pipeline_services/core/pipeline.hpp"

namespace pipeline_services::remote {

template <typename ContextType>
std::string defaultToJson(const ContextType& ctx) {
  if constexpr (std::is_same_v<ContextType, std::string>) {
    return ctx;
  } else {
    nlohmann::json jsonValue = ctx;
    return jsonValue.dump();
  }
}

template <typename ContextType>
ContextType defaultFromJson(const ContextType& ctx, const std::string& responseBody) {
  (void)ctx;
  if constexpr (std::is_same_v<ContextType, std::string>) {
    return responseBody;
  } else {
    nlohmann::json jsonValue = nlohmann::json::parse(responseBody);
    return jsonValue.get<ContextType>();
  }
}

template <typename ContextType>
struct RemoteSpec {
  std::string endpoint;
  std::int32_t timeoutMillis;
  std::int32_t retries;
  std::map<std::string, std::string> headers;

  std::function<std::string(const ContextType&)> toJson;
  std::function<ContextType(const ContextType&, const std::string&)> fromJson;

  explicit RemoteSpec(std::string endpointValue)
    : endpoint(std::move(endpointValue)),
      timeoutMillis(1000),
      retries(0),
      headers(),
      toJson(defaultToJson<ContextType>),
      fromJson(defaultFromJson<ContextType>) {}
};

struct RemoteDefaults {
  std::string baseUrl;
  std::int32_t timeoutMillis;
  std::int32_t retries;
  std::map<std::string, std::string> headers;
  std::string method;

  RemoteDefaults()
    : baseUrl(""),
      timeoutMillis(1000),
      retries(0),
      headers(),
      method("POST") {}

  std::string resolveEndpoint(const std::string& endpointOrPath) const {
    if (endpointOrPath.rfind("http://", 0) == 0 || endpointOrPath.rfind("https://", 0) == 0) {
      return endpointOrPath;
    }
    if (baseUrl.empty()) {
      return endpointOrPath;
    }
    if (!baseUrl.empty() && baseUrl.back() == '/' && !endpointOrPath.empty() && endpointOrPath.front() == '/') {
      return baseUrl + endpointOrPath.substr(1);
    }
    if (!baseUrl.empty() && baseUrl.back() != '/' && !endpointOrPath.empty() && endpointOrPath.front() != '/') {
      return baseUrl + "/" + endpointOrPath;
    }
    return baseUrl + endpointOrPath;
  }

  std::map<std::string, std::string> mergeHeaders(const std::map<std::string, std::string>& overrides) const {
    if (overrides.empty()) {
      return headers;
    }

    std::map<std::string, std::string> merged = headers;
    for (const auto& overrideItem : overrides) {
      merged[overrideItem.first] = overrideItem.second;
    }
    return merged;
  }

  template <typename ContextType>
  RemoteSpec<ContextType> spec(const std::string& endpointOrPath) const {
    const std::string resolvedEndpoint = resolveEndpoint(endpointOrPath);
    RemoteSpec<ContextType> remoteSpec(resolvedEndpoint);
    remoteSpec.timeoutMillis = timeoutMillis;
    remoteSpec.retries = retries;
    remoteSpec.headers = headers;
    return remoteSpec;
  }
};

struct ParsedUrl {
  std::string scheme;
  std::string host;
  int port;
  std::string path;
};

inline ParsedUrl parseUrl(const std::string& endpoint) {
  constexpr std::string_view httpPrefix = "http://";
  constexpr std::string_view httpsPrefix = "https://";

  std::string_view endpointView(endpoint);
  std::string scheme;
  int defaultPort = 80;

  if (endpointView.rfind(httpPrefix, 0) == 0) {
    scheme = "http";
    endpointView.remove_prefix(httpPrefix.size());
    defaultPort = 80;
  } else if (endpointView.rfind(httpsPrefix, 0) == 0) {
    scheme = "https";
    endpointView.remove_prefix(httpsPrefix.size());
    defaultPort = 443;
  } else {
    throw std::runtime_error("Unsupported URL scheme (expected http:// or https://): " + endpoint);
  }

  const std::size_t slashIndex = endpointView.find('/');
  std::string_view hostPortView = endpointView;
  std::string_view pathView = "/";
  if (slashIndex != std::string_view::npos) {
    hostPortView = endpointView.substr(0, slashIndex);
    pathView = endpointView.substr(slashIndex);
  }

  std::string host;
  int port = defaultPort;
  const std::size_t colonIndex = hostPortView.rfind(':');
  if (colonIndex != std::string_view::npos) {
    host = std::string(hostPortView.substr(0, colonIndex));
    const std::string portText = std::string(hostPortView.substr(colonIndex + 1));
    port = std::stoi(portText);
  } else {
    host = std::string(hostPortView);
  }

  if (host.empty()) {
    throw std::runtime_error("Invalid endpoint URL (missing host): " + endpoint);
  }

  return ParsedUrl{
    .scheme = scheme,
    .host = host,
    .port = port,
    .path = std::string(pathView),
  };
}

inline std::string withQuery(const std::string& endpoint, const std::string& queryText) {
  if (queryText.empty()) {
    return endpoint;
  }

  if (endpoint.find('?') != std::string::npos) {
    return endpoint + "&" + queryText;
  }

  return endpoint + "?" + queryText;
}

template <typename ContextType>
void validateSpec(const RemoteSpec<ContextType>& spec) {
  if (spec.endpoint.empty()) {
    throw std::invalid_argument("RemoteSpec.endpoint is required");
  }
  if (!spec.toJson) {
    throw std::invalid_argument("RemoteSpec.toJson is required");
  }
  if (!spec.fromJson) {
    throw std::invalid_argument("RemoteSpec.fromJson is required");
  }
}

template <typename ContextType>
ContextType invoke(const RemoteSpec<ContextType>& spec, const std::string& methodName, const ContextType& ctx) {
  validateSpec(spec);

  const std::string body = spec.toJson(ctx);
  std::string endpoint = spec.endpoint;
  if (methodName == "GET") {
    endpoint = withQuery(endpoint, body);
  }

  const ParsedUrl parsedUrl = parseUrl(endpoint);
  if (parsedUrl.scheme != "http") {
    throw std::runtime_error("Only http:// endpoints are supported by this C++ port currently: " + endpoint);
  }

  httplib::Client client(parsedUrl.host, parsedUrl.port);
  const int timeoutSeconds = static_cast<int>(spec.timeoutMillis / 1000);
  const int timeoutMicroseconds = static_cast<int>((spec.timeoutMillis % 1000) * 1000);
  client.set_connection_timeout(timeoutSeconds, timeoutMicroseconds);
  client.set_read_timeout(timeoutSeconds, timeoutMicroseconds);
  client.set_write_timeout(timeoutSeconds, timeoutMicroseconds);

  httplib::Headers headersValue;
  for (const auto& headerItem : spec.headers) {
    headersValue.insert(headerItem);
  }
  headersValue.insert({"Content-Type", "application/json"});

  std::string lastErrorMessage;
  std::int32_t attemptIndex = 0;
  while (attemptIndex <= spec.retries) {
    try {
      if (methodName == "GET") {
        const auto response = client.Get(parsedUrl.path.c_str(), headersValue);
        if (!response) {
          throw std::runtime_error("HTTP request failed");
        }
        if (response->status < 200 || response->status >= 300) {
          throw std::runtime_error("HTTP " + std::to_string(response->status) + " body=" + response->body);
        }
        return spec.fromJson(ctx, response->body);
      }

      if (methodName != "POST") {
        throw std::runtime_error("Unsupported HTTP method: " + methodName);
      }

      const auto response = client.Post(parsedUrl.path.c_str(), headersValue, body, "application/json");
      if (!response) {
        throw std::runtime_error("HTTP request failed");
      }
      if (response->status < 200 || response->status >= 300) {
        throw std::runtime_error("HTTP " + std::to_string(response->status) + " body=" + response->body);
      }
      return spec.fromJson(ctx, response->body);
    } catch (const std::exception& error) {
      lastErrorMessage = error.what() ? error.what() : "exception";
    }

    attemptIndex += 1;
  }

  throw std::runtime_error(lastErrorMessage.empty() ? "HTTP request failed" : lastErrorMessage);
}

template <typename ContextType>
struct HttpStepAction {
  RemoteSpec<ContextType> spec;
  std::string method;

  ContextType operator()(ContextType ctx, pipeline_services::core::StepControl<ContextType>& control) const {
    (void)control;
    return invoke<ContextType>(spec, method, ctx);
  }
};

template <typename ContextType>
pipeline_services::core::StepAction<ContextType> jsonPost(RemoteSpec<ContextType> spec) {
  HttpStepAction<ContextType> action{.spec = std::move(spec), .method = "POST"};
  pipeline_services::core::StepAction<ContextType> stepAction = std::move(action);
  return stepAction;
}

template <typename ContextType>
pipeline_services::core::StepAction<ContextType> jsonGet(RemoteSpec<ContextType> spec) {
  HttpStepAction<ContextType> action{.spec = std::move(spec), .method = "GET"};
  pipeline_services::core::StepAction<ContextType> stepAction = std::move(action);
  return stepAction;
}

}  // namespace pipeline_services::remote
