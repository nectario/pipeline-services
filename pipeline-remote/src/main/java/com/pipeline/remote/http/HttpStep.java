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
        return in -> execute(spec, in, true);
    }

    public static <I, O> ThrowingFn<I, O> jsonGet(RemoteSpec<I, O> spec) {
        return in -> execute(spec, in, false);
    }

    private static <I, O> O execute(RemoteSpec<I, O> spec, I in, boolean post) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(spec.timeoutMillis))
                .build();

        int attempts = 0;
        Exception last = null;
        while (attempts <= spec.retries) {
            try {
                HttpRequest request;
                if (post) {
                    String body = spec.toJson.apply(in);
                    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(spec.endpoint))
                            .timeout(Duration.ofMillis(spec.timeoutMillis))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body));
                    for (Map.Entry<String, String> e : spec.headers.entrySet()) b.header(e.getKey(), e.getValue());
                    request = b.build();
                } else {
                    String query = spec.toJson.apply(in);
                    String url = spec.endpoint + (spec.endpoint.contains("?") ? "&" : "?") + query;
                    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofMillis(spec.timeoutMillis))
                            .GET();
                    for (Map.Entry<String, String> e : spec.headers.entrySet()) b.header(e.getKey(), e.getValue());
                    request = b.build();
                }
                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code >= 200 && code < 300) {
                    return spec.fromJson.apply(resp.body());
                }
                throw new IOException("HTTP " + code + ": " + resp.body());
            } catch (Exception e) {
                last = e;
                attempts++;
                if (attempts > spec.retries) throw e;
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

