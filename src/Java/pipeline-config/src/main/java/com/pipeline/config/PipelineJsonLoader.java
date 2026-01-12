package com.pipeline.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.core.Pipeline;
import com.pipeline.core.StepAction;
import com.pipeline.remote.http.HttpStep;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.function.UnaryOperator;

/** Minimal JSON loader for unary pipelines. */
public final class PipelineJsonLoader {
    private static final ObjectMapper M = new ObjectMapper();
    private PipelineJsonLoader() {}

    public static Pipeline<String> loadUnary(InputStream in) throws IOException {
        JsonNode root = M.readTree(in);
        String name = req(root, "pipeline").asText();
        String type = root.path("type").asText("unary");
        if (!"unary".equals(type)) throw new IOException("Only unary pipelines supported by this loader");
        boolean shortCircuitOnException = root.has("shortCircuitOnException")
            ? root.path("shortCircuitOnException").asBoolean(true)
            : root.path("shortCircuit").asBoolean(true);

        Pipeline<String> pipeline = new Pipeline<>(name, shortCircuitOnException);
        JsonNode arr = root.path("steps");
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
                    var spec = new HttpStep.RemoteSpec<String>();
                    spec.endpoint = req(r, "endpoint").asText();
                    spec.timeoutMillis = r.path("timeoutMillis").asInt(1000);
                    spec.retries = r.path("retries").asInt(0);
                    spec.toJson = body -> body;
                    spec.fromJson = (ctx, body) -> body;
                    pipeline.addAction(HttpStep.jsonPost(spec));
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
}
