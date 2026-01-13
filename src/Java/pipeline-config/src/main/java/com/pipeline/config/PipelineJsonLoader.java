package com.pipeline.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.core.Pipeline;
import com.pipeline.core.StepAction;
import com.pipeline.remote.http.HttpStep;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Minimal JSON loader for unary pipelines. */
public final class PipelineJsonLoader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private PipelineJsonLoader() {}

    public static Pipeline<String> loadUnary(InputStream in) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(in);
        String name = req(root, "pipeline").asText();
        String type = root.path("type").asText("unary");
        if (!"unary".equals(type)) throw new IOException("Only unary pipelines supported by this loader");
        boolean shortCircuitOnException = root.has("shortCircuitOnException")
            ? root.path("shortCircuitOnException").asBoolean(true)
            : root.path("shortCircuit").asBoolean(true);

        HttpStep.RemoteDefaults remoteDefaults = parseRemoteDefaults(root.get("remoteDefaults"));

        Pipeline<String> pipeline = new Pipeline<>(name, shortCircuitOnException);
        JsonNode arr = root.has("actions") ? root.path("actions") : root.path("steps");
        if (arr.isArray()) {
            for (JsonNode s : arr) {
                if (s.has("$local")) {
                    String cls = s.get("$local").asText();
                    Object o = instantiate(cls);
                    if (o instanceof UnaryOperator<?> fn) {
                        @SuppressWarnings("unchecked") UnaryOperator<String> u = (UnaryOperator<String>) fn;
                        pipeline.addAction(u);
                    } else if (o instanceof StepAction<?> sa) {
                        @SuppressWarnings("unchecked") StepAction<String> a = (StepAction<String>) sa;
                        pipeline.addAction(a);
                    } else {
                        throw new IOException("Class must implement UnaryOperator or StepAction: " + cls);
                    }
                } else if (s.has("$remote")) {
                    JsonNode r = s.get("$remote");
                    String endpointOrPath = parseRemoteEndpointOrPath(r);

                    HttpStep.RemoteSpec<String> spec = remoteDefaults.spec(endpointOrPath, body -> body, (ctx, body) -> body);
                    if (r.isObject()) {
                        spec.timeoutMillis = r.path("timeoutMillis").asInt(spec.timeoutMillis);
                        spec.retries = r.path("retries").asInt(spec.retries);
                        spec.headers = remoteDefaults.mergeHeaders(parseStringMap(r.get("headers")));
                    }

                    String method = r.path("method").asText(remoteDefaults.method);
                    pipeline.addAction("GET".equalsIgnoreCase(method) ? HttpStep.jsonGet(spec) : HttpStep.jsonPost(spec));
                } else {
                    throw new IOException("Unsupported step: " + s.toString());
                }
            }
        }
        return pipeline;
    }

    private static Object instantiate(String fqcn) throws IOException {
        try {
            Class<?> c = Class.forName(fqcn);
            Constructor<?> ctor = c.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new IOException("Failed to instantiate " + fqcn, e);
        }
    }

    private static JsonNode req(JsonNode n, String field) throws IOException {
        if (!n.has(field)) throw new IOException("Missing required field: " + field);
        return n.get(field);
    }

    private static HttpStep.RemoteDefaults parseRemoteDefaults(JsonNode node) throws IOException {
        HttpStep.RemoteDefaults defaults = new HttpStep.RemoteDefaults();
        if (node == null || node.isNull() || !node.isObject()) return defaults;
        defaults.baseUrl = node.path("baseUrl").asText(node.path("endpointBase").asText(null));
        defaults.timeoutMillis = node.path("timeoutMillis").asInt(defaults.timeoutMillis);
        defaults.retries = node.path("retries").asInt(defaults.retries);
        defaults.method = node.path("method").asText(defaults.method);
        defaults.serde = node.path("serde").asText(defaults.serde);
        defaults.headers = defaults.mergeHeaders(parseStringMap(node.get("headers")));
        return defaults;
    }

    private static String parseRemoteEndpointOrPath(JsonNode remoteNode) throws IOException {
        if (remoteNode == null || remoteNode.isNull()) {
            throw new IOException("$remote must be a string or object");
        }
        if (remoteNode.isTextual()) return remoteNode.asText();
        if (!remoteNode.isObject()) throw new IOException("Unsupported $remote spec: " + remoteNode);
        if (remoteNode.has("endpoint")) return req(remoteNode, "endpoint").asText();
        if (remoteNode.has("path")) return req(remoteNode, "path").asText();
        throw new IOException("Missing required $remote field: endpoint|path");
    }

    private static Map<String, String> parseStringMap(JsonNode node) throws IOException {
        if (node == null || node.isNull()) return Map.of();
        if (!node.isObject()) throw new IOException("Expected object for map field: " + node);
        Map<String, String> out = new LinkedHashMap<>();
        var it = node.fields();
        while (it.hasNext()) {
            var entry = it.next();
            out.put(entry.getKey(), entry.getValue().asText());
        }
        return out;
    }
}
