package com.pipeline.remote.http;

import com.pipeline.core.ThrowingFn;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

public final class HttpStep {
    private HttpStep() {}

    public static <I, O> ThrowingFn<I, O> jsonPost(RemoteSpec<I, O> spec) {
        return in -> invoke(spec, "POST", in);
    }

    public static <I, O> ThrowingFn<I, O> jsonGet(RemoteSpec<I, O> spec) {
        return in -> invoke(spec, "GET", in);
    }

    private static <I, O> O invoke(RemoteSpec<I, O> spec, String method, I in) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(spec.timeoutMillis))
                .build();

        String body = spec.toJson.apply(in);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(spec.endpoint))
                .timeout(Duration.ofMillis(spec.timeoutMillis));

        if ("POST".equalsIgnoreCase(method)) {
            b = b.POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
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

    public static final class RemoteSpec<I, O> {
        public String endpoint;
        public int timeoutMillis = 1000;
        public int retries = 0;
        public Map<String, String> headers = Map.of();
        public Function<I, String> toJson;   // I -> JSON body or query
        public Function<String, O> fromJson; // JSON -> O
    }
}
