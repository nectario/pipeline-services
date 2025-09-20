package com.pipeline.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.core.Pipeline;
import com.pipeline.core.ThrowingFn;
import com.pipeline.remote.http.HttpStep;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/** Minimal JSON loader for unary pipelines. */
public final class PipelineJsonLoader {
    private static final ObjectMapper M = new ObjectMapper();
    private PipelineJsonLoader() {}

    public static Pipeline<String> loadUnary(InputStream in) throws IOException {
        JsonNode root = M.readTree(in);
        String name = req(root, "pipeline").asText();
        String type = root.path("type").asText("unary");
        if (!"unary".equals(type)) throw new IOException("Only unary pipelines supported by this loader");
        boolean shortCircuit = root.path("shortCircuit").asBoolean(true);

        List<ThrowingFn<String,String>> steps = new ArrayList<>();
        JsonNode arr = root.path("steps");
        if (arr.isArray()) {
            for (JsonNode s : arr) {
                if (s.has("$local")) {
                    String cls = s.get("$local").asText();
                    steps.add(instantiateFn(cls));
                } else if (s.has("$remote")) {
                    JsonNode r = s.get("$remote");
                    var spec = new HttpStep.RemoteSpec<String,String>();
                    spec.endpoint = req(r, "endpoint").asText();
                    spec.timeoutMillis = r.path("timeoutMillis").asInt(1000);
                    spec.retries = r.path("retries").asInt(0);
                    spec.toJson = body -> body;
                    spec.fromJson = body -> body;
                    steps.add(HttpStep.jsonPost(spec));
                } else {
                    throw new IOException("Unsupported step: " + s.toString());
                }
            }
        }

        @SuppressWarnings("unchecked")
        ThrowingFn<String,String>[] arrSteps = steps.toArray(new ThrowingFn[0]);
        return Pipeline.build(name, shortCircuit, arrSteps);
    }

    @SuppressWarnings("unchecked")
    private static ThrowingFn<String,String> instantiateFn(String fqcn) throws IOException {
        try {
            Class<?> c = Class.forName(fqcn);
            Constructor<?> ctor = c.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object o = ctor.newInstance();
            if (o instanceof ThrowingFn<?,?> fn) {
                return (ThrowingFn<String,String>) fn;
            }
            throw new IOException("Class does not implement ThrowingFn: " + fqcn);
        } catch (Exception e) {
            throw new IOException("Failed to instantiate " + fqcn, e);
        }
    }

    private static JsonNode req(JsonNode n, String field) throws IOException {
        if (!n.has(field)) throw new IOException("Missing required field: " + field);
        return n.get(field);
    }
}
