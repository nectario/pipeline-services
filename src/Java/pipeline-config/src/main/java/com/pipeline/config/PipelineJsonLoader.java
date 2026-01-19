package com.pipeline.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.core.ActionRegistry;
import com.pipeline.core.Pipeline;
import com.pipeline.core.StepAction;
import com.pipeline.remote.http.HttpStep;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Minimal JSON loader for unary pipelines. */
public final class PipelineJsonLoader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private PipelineJsonLoader() {}

    public static Pipeline<String> loadUnary(InputStream in) throws IOException {
        return loadUnary(in, new ActionRegistry<>());
    }

    public static Pipeline<String> loadUnary(Path filePath, ActionRegistry<String> registry) throws IOException {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(registry, "registry");

        JsonNode root;
        try (InputStream sourceInputStream = Files.newInputStream(filePath)) {
            root = OBJECT_MAPPER.readTree(sourceInputStream);
        }
        String pipelineName = req(root, "pipeline").asText(filePath.getFileName().toString());
        if (containsPromptSteps(root)) {
            Path pipelinesRoot = findPipelinesRoot(filePath);
            Path compiledPath = pipelinesRoot.resolve("generated").resolve("java").resolve(pipelineName + ".json");
            if (!Files.exists(compiledPath)) {
                throw new IOException(
                    "Pipeline contains $prompt steps but compiled JSON was not found. Run prompt codegen. Expected compiled pipeline at: "
                        + compiledPath);
            }
            try (InputStream compiledInputStream = Files.newInputStream(compiledPath)) {
                return loadUnary(compiledInputStream, registry);
            }
        }

        try (InputStream sourceInputStream = Files.newInputStream(filePath)) {
            return loadUnary(sourceInputStream, registry);
        }
    }

    public static Pipeline<String> loadUnary(InputStream in, ActionRegistry<String> registry) throws IOException {
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
                    addLocal(pipeline, registry, cls);
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
                } else if (s.has("$prompt")) {
                    throw new IOException(
                        "Runtime does not execute $prompt steps. Run prompt codegen to produce a compiled pipeline JSON with $local references.");
                } else {
                    throw new IOException("Unsupported step: " + s.toString());
                }
            }
        }
        return pipeline;
    }

    private static void addLocal(Pipeline<String> pipeline, ActionRegistry<String> registry, String localRef) throws IOException {
        if (registry.hasUnary(localRef)) {
            pipeline.addAction(registry.getUnary(localRef));
            return;
        }
        if (registry.hasAction(localRef)) {
            pipeline.addAction(registry.getAction(localRef));
            return;
        }

        if (localRef.startsWith("prompt:")) {
            throw new IOException(
                "Prompt-generated action is missing from the registry: " + localRef
                    + ". Run prompt codegen and register generated actions (com.pipeline.generated.PromptGeneratedActions.register).");
        }

        Object instance = instantiate(localRef);
        if (instance instanceof UnaryOperator<?> fn) {
            @SuppressWarnings("unchecked") UnaryOperator<String> unaryAction = (UnaryOperator<String>) fn;
            pipeline.addAction(unaryAction);
            return;
        }
        if (instance instanceof StepAction<?> stepAction) {
            @SuppressWarnings("unchecked") StepAction<String> action = (StepAction<String>) stepAction;
            pipeline.addAction(action);
            return;
        }
        throw new IOException("Class must implement UnaryOperator or StepAction: " + localRef);
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

    private static boolean containsPromptSteps(JsonNode root) {
        for (String sectionName : new String[] { "pre", "actions", "steps", "post" }) {
            JsonNode nodes = root.get(sectionName);
            if (nodes == null || !nodes.isArray()) continue;
            for (JsonNode node : nodes) {
                if (node != null && node.has("$prompt")) return true;
            }
        }
        return false;
    }

    private static Path findPipelinesRoot(Path sourceFilePath) throws IOException {
        Path current = sourceFilePath.toAbsolutePath().getParent();
        while (current != null) {
            if ("pipelines".equals(current.getFileName().toString())) return current;
            current = current.getParent();
        }
        throw new IOException(
            "Pipeline contains $prompt steps but the pipelines root directory could not be inferred from path: "
                + sourceFilePath + " (expected the file to be under a 'pipelines' directory).");
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
