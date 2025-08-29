package com.ps.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ps.core.Pipeline;
import com.ps.core.ThrowingFn;
import com.ps.prompt.Prompt;
import com.ps.remote.http.HttpStep;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Minimal JSON loader for unary pipelines. */
public final class PipelineJsonLoader {
    private final ObjectMapper mapper = new ObjectMapper();

    public Pipeline<String> loadUnary(InputStream json) throws IOException {
        JsonNode root = mapper.readTree(json);
        String name = req(root, "pipeline").asText();
        boolean shortCircuit = root.path("shortCircuit").asBoolean(true);

        List<ThrowingFn<String, String>> steps = new ArrayList<>();
        for (JsonNode step : req(root, "steps")) {
            if (step.has("$local")) {
                String cls = step.get("$local").asText();
                try {
                    @SuppressWarnings("unchecked")
                    ThrowingFn<String, String> fn = (ThrowingFn<String, String>) Class.forName(cls).getDeclaredConstructor().newInstance();
                    steps.add(fn);
                } catch (Exception e) {
                    throw new IOException("Failed to load local step: " + cls, e);
                }
            } else if (step.has("$prompt")) {
                // Placeholder prompt usage; psGenerate should produce real implementation
                JsonNode p = step.get("$prompt");
                String stepName = p.path("name").asText("generatedStep");
                steps.add(Prompt.<String, String>step(String.class, String.class).name(stepName).goal(p.path("goal").asText("")) .build());
            } else if (step.has("$remote")) {
                JsonNode r = step.get("$remote");
                HttpStep.RemoteSpec<String, String> spec = new HttpStep.RemoteSpec<>();
                spec.endpoint = r.path("endpoint").asText();
                spec.timeoutMillis = r.path("timeoutMillis").asInt(1000);
                spec.retries = r.path("retries").asInt(0);
                spec.toJson = s -> s; // naive mapping
                spec.fromJson = s -> s;
                steps.add("POST".equalsIgnoreCase(r.path("method").asText("POST")) ? HttpStep.jsonPost(spec) : HttpStep.jsonGet(spec));
            }
        }

        @SuppressWarnings("unchecked")
        ThrowingFn<String, String>[] arr = steps.toArray(new ThrowingFn[0]);
        return Pipeline.build(name, shortCircuit, arr);
    }

    private static JsonNode req(JsonNode n, String field) throws IOException {
        if (!n.has(field)) throw new IOException("Missing required field: " + field);
        return n.get(field);
    }
}

