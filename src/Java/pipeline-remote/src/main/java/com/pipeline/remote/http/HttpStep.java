package com.pipeline.remote.http;

import com.pipeline.core.StepAction;
import com.pipeline.core.ThrowingFn;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class HttpStep {
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
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(spec.timeoutMillis))
                .build();

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
        for (Map.Entry<String, String> e : spec.headers.entrySet()) {
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
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(spec.timeoutMillis))
            .build();

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
        for (Map.Entry<String, String> e : spec.headers.entrySet()) {
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

    public static final class RemoteSpec<C> {
        public String endpoint;
        public int timeoutMillis = 1000;
        public int retries = 0;
        public Map<String, String> headers = Map.of();
        public Function<C, String> toJson;          // C -> JSON body or query string
        public BiFunction<C, String, C> fromJson;   // (C, body) -> updated context
    }

    public static final class RemoteSpecTyped<I, O> {
        public String endpoint;
        public int timeoutMillis = 1000;
        public int retries = 0;
        public Map<String, String> headers = Map.of();
        public Function<I, String> toJson;   // I -> JSON body or query string
        public Function<String, O> fromJson; // JSON -> O
    }
}
