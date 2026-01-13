package com.pipeline.remote.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.core.StepAction;
import com.pipeline.core.ThrowingFn;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class HttpStep {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private HttpStep() {}

    public static <C> StepAction<C> jsonPost(RemoteSpec<C> spec) {
        Objects.requireNonNull(spec, "spec");
        return (ctx, control) -> {
            try {
                return invoke(spec, "POST", ctx);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        };
    }

    public static <C> StepAction<C> jsonGet(RemoteSpec<C> spec) {
        Objects.requireNonNull(spec, "spec");
        return (ctx, control) -> {
            try {
                return invoke(spec, "GET", ctx);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        };
    }

    private static <C> C invoke(RemoteSpec<C> spec, String method, C ctx) throws IOException, InterruptedException {
        validateSpec(spec);
        HttpClient client = (spec.client != null) ? spec.client : HttpClient.newHttpClient();

        String body = spec.toJson.apply(ctx);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .timeout(Duration.ofMillis(spec.timeoutMillis));

        if ("POST".equalsIgnoreCase(method)) {
            b = b.uri(URI.create(spec.endpoint));
            b = b.POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            String uri = withQuery(spec.endpoint, body);
            b = b.uri(URI.create(uri));
            b = b.GET();
        }
        Map<String, String> headers = spec.headers == null ? Map.of() : spec.headers;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            b.header(e.getKey(), e.getValue());
        }
        b.header("Content-Type", "application/json");

        IOException last = null;
        for (int attempt = 0; attempt <= spec.retries; attempt++) {
            try {
                HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code >= 200 && code < 300) {
                    return spec.fromJson.apply(ctx, resp.body());
                }
                last = new IOException("HTTP " + code + " body=" + resp.body());
            } catch (IOException ioe) {
                last = ioe;
            }
        }
        throw last != null ? last : new IOException("Unknown HTTP error");
    }

    public static <I, O> ThrowingFn<I, O> jsonPostTyped(RemoteSpecTyped<I, O> spec) {
        return in -> invokeTyped(spec, "POST", in);
    }

    public static <I, O> ThrowingFn<I, O> jsonGetTyped(RemoteSpecTyped<I, O> spec) {
        return in -> invokeTyped(spec, "GET", in);
    }

    private static <I, O> O invokeTyped(RemoteSpecTyped<I, O> spec, String method, I in) throws IOException, InterruptedException {
        validateSpec(spec);
        HttpClient client = (spec.client != null) ? spec.client : HttpClient.newHttpClient();

        String body = spec.toJson.apply(in);
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .timeout(Duration.ofMillis(spec.timeoutMillis));

        if ("POST".equalsIgnoreCase(method)) {
            b = b.uri(URI.create(spec.endpoint));
            b = b.POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            String uri = withQuery(spec.endpoint, body);
            b = b.uri(URI.create(uri));
            b = b.GET();
        }
        Map<String, String> headers = spec.headers == null ? Map.of() : spec.headers;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            b.header(e.getKey(), e.getValue());
        }
        b.header("Content-Type", "application/json");

        IOException last = null;
        for (int attempt = 0; attempt <= spec.retries; attempt++) {
            try {
                HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code >= 200 && code < 300) {
                    return spec.fromJson.apply(resp.body());
                }
                last = new IOException("HTTP " + code + " body=" + resp.body());
            } catch (IOException ioe) {
                last = ioe;
            }
        }
        throw last != null ? last : new IOException("Unknown HTTP error");
    }

    private static String withQuery(String endpoint, String query) {
        if (query == null || query.isBlank()) return endpoint;
        if (endpoint.contains("?")) return endpoint + "&" + query;
        return endpoint + "?" + query;
    }

    private static void validateSpec(RemoteSpec<?> spec) {
        if (spec.endpoint == null || spec.endpoint.isBlank()) {
            throw new IllegalArgumentException("RemoteSpec.endpoint is required");
        }
        Objects.requireNonNull(spec.toJson, "RemoteSpec.toJson");
        Objects.requireNonNull(spec.fromJson, "RemoteSpec.fromJson");
    }

    private static void validateSpec(RemoteSpecTyped<?, ?> spec) {
        if (spec.endpoint == null || spec.endpoint.isBlank()) {
            throw new IllegalArgumentException("RemoteSpecTyped.endpoint is required");
        }
        Objects.requireNonNull(spec.toJson, "RemoteSpecTyped.toJson");
        Objects.requireNonNull(spec.fromJson, "RemoteSpecTyped.fromJson");
    }

    public static final class RemoteSpec<C> {
        public String endpoint;
        public int timeoutMillis = 1000;
        public int retries = 0;
        public Map<String, String> headers = Map.of();
        public HttpClient client = HttpClient.newHttpClient();
        public Function<C, String> toJson;          // C -> JSON body or query string
        public BiFunction<C, String, C> fromJson;   // (C, body) -> updated context
    }

    public static final class RemoteSpecTyped<I, O> {
        public String endpoint;
        public int timeoutMillis = 1000;
        public int retries = 0;
        public Map<String, String> headers = Map.of();
        public HttpClient client = HttpClient.newHttpClient();
        public Function<I, String> toJson;   // I -> JSON body or query string
        public Function<String, O> fromJson; // JSON -> O
    }

    /** Shared defaults so you don't repeat endpoint base, timeouts, retries, headers, and client wiring. */
    public static final class RemoteDefaults {
        public String baseUrl;
        public int timeoutMillis = 1000;
        public int retries = 0;
        public Map<String, String> headers = Map.of();
        public String method = "POST"; // POST | GET
        public String serde;           // null | "string" | "jackson"
        public HttpClient client = HttpClient.newHttpClient();

        public String resolveEndpoint(String endpointOrPath) {
            String v = Objects.requireNonNull(endpointOrPath, "endpointOrPath").strip();
            if (v.startsWith("http://") || v.startsWith("https://")) return v;
            if (baseUrl == null || baseUrl.isBlank()) return v;
            String b = baseUrl.strip();
            if (b.endsWith("/") && v.startsWith("/")) return b + v.substring(1);
            if (!b.endsWith("/") && !v.startsWith("/")) return b + "/" + v;
            return b + v;
        }

        public Map<String, String> mergeHeaders(Map<String, String> overrides) {
            Map<String, String> base = (headers == null) ? Map.of() : headers;
            if (overrides == null || overrides.isEmpty()) return base;
            Map<String, String> merged = new LinkedHashMap<>(base);
            merged.putAll(overrides);
            return Map.copyOf(merged);
        }

        public <C> RemoteSpec<C> spec(String endpointOrPath,
                                      Function<C, String> toJson,
                                      BiFunction<C, String, C> fromJson) {
            RemoteSpec<C> spec = new RemoteSpec<>();
            spec.endpoint = resolveEndpoint(endpointOrPath);
            spec.timeoutMillis = timeoutMillis;
            spec.retries = retries;
            spec.headers = mergeHeaders(null);
            spec.client = client;
            spec.toJson = toJson;
            spec.fromJson = fromJson;
            return spec;
        }

        public <C> StepAction<C> action(String endpointOrPath,
                                        Function<C, String> toJson,
                                        BiFunction<C, String, C> fromJson) {
            RemoteSpec<C> spec = spec(endpointOrPath, toJson, fromJson);
            if ("GET".equalsIgnoreCase(method)) return jsonGet(spec);
            return jsonPost(spec);
        }

        public <I, O> RemoteSpecTyped<I, O> typedSpec(String endpointOrPath,
                                                      Function<I, String> toJson,
                                                      Function<String, O> fromJson) {
            RemoteSpecTyped<I, O> spec = new RemoteSpecTyped<>();
            spec.endpoint = resolveEndpoint(endpointOrPath);
            spec.timeoutMillis = timeoutMillis;
            spec.retries = retries;
            spec.headers = mergeHeaders(null);
            spec.client = client;
            spec.toJson = toJson;
            spec.fromJson = fromJson;
            return spec;
        }

        public <I, O> ThrowingFn<I, O> fn(String endpointOrPath,
                                          Function<I, String> toJson,
                                          Function<String, O> fromJson) {
            RemoteSpecTyped<I, O> spec = typedSpec(endpointOrPath, toJson, fromJson);
            if ("GET".equalsIgnoreCase(method)) return jsonGetTyped(spec);
            return jsonPostTyped(spec);
        }

        public <I, O> ThrowingFn<I, O> jacksonFn(String endpointOrPath, Class<O> outClass) {
            Objects.requireNonNull(outClass, "outClass");
            return fn(endpointOrPath,
                obj -> {
                    try { return (obj == null) ? "null" : OBJECT_MAPPER.writeValueAsString(obj); }
                    catch (Exception e) { throw new RuntimeException(e); }
                },
                body -> {
                    try {
                        if (outClass == String.class) return outClass.cast(body);
                        return OBJECT_MAPPER.readValue(body, outClass);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            );
        }
    }
}
