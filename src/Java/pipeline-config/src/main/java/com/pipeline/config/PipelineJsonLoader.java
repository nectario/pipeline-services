package com.pipeline.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.core.ActionRegistry;
import com.pipeline.core.ActionLifecycle;
import com.pipeline.core.ActionPool;
import com.pipeline.core.ResettableAction;
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
        boolean singletonMode = root.path("singletonMode").asBoolean(false);
        boolean shortCircuitOnException = root.has("shortCircuitOnException")
            ? root.path("shortCircuitOnException").asBoolean(true)
            : root.path("shortCircuit").asBoolean(true);

        HttpStep.RemoteDefaults remoteDefaults = parseRemoteDefaults(root.get("remoteDefaults"));

        Pipeline<String> pipeline = new Pipeline<>(name, shortCircuitOnException);

        addSection(root, "preActions", "pre", JsonSection.PRE, pipeline, registry, remoteDefaults, singletonMode);
        addSection(root, "actions", "steps", JsonSection.MAIN, pipeline, registry, remoteDefaults, singletonMode);
        addSection(root, "postActions", "post", JsonSection.POST, pipeline, registry, remoteDefaults, singletonMode);
        return pipeline;
    }

    private enum JsonSection {
        PRE,
        MAIN,
        POST
    }

    private static void addSection(
        JsonNode root,
        String preferredFieldName,
        String legacyFieldName,
        JsonSection section,
        Pipeline<String> pipeline,
        ActionRegistry<String> registry,
        HttpStep.RemoteDefaults remoteDefaults,
        boolean singletonMode
    ) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(preferredFieldName, "preferredFieldName");
        Objects.requireNonNull(legacyFieldName, "legacyFieldName");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(pipeline, "pipeline");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(remoteDefaults, "remoteDefaults");

        boolean hasPreferred = root.has(preferredFieldName);
        JsonNode actionsArray = hasPreferred ? root.get(preferredFieldName) : root.get(legacyFieldName);
        if (actionsArray == null || actionsArray.isNull()) return;
        if (!actionsArray.isArray()) {
            String chosenName = hasPreferred ? preferredFieldName : legacyFieldName;
            throw new IOException("Section '" + chosenName + "' must be an array");
        }

        for (JsonNode actionNode : actionsArray) {
            addActionNode(actionNode, section, pipeline, registry, remoteDefaults, singletonMode);
        }
    }

    private static void addActionNode(
        JsonNode actionNode,
        JsonSection section,
        Pipeline<String> pipeline,
        ActionRegistry<String> registry,
        HttpStep.RemoteDefaults remoteDefaults,
        boolean singletonMode
    ) throws IOException {
        if (actionNode == null || actionNode.isNull() || !actionNode.isObject()) {
            throw new IOException("Each action must be a JSON object");
        }

        String actionName = parseActionName(actionNode);
        ActionLifecycle lifecycleOverride = singletonMode ? parseLifecycleOverride(actionNode) : null;

        if (actionNode.has("$local")) {
            JsonNode localNode = actionNode.get("$local");
            if (localNode == null || !localNode.isTextual()) throw new IOException("$local must be a string");
            String localRef = localNode.asText();

            boolean isRegistryLocal = registry.hasUnary(localRef) || registry.hasAction(localRef);
            ActionLifecycle lifecycle = ActionLifecycle.SHARED;
            if (singletonMode) {
                lifecycle = isRegistryLocal ? ActionLifecycle.SHARED : ActionLifecycle.POOLED;
                if (lifecycleOverride != null) lifecycle = lifecycleOverride;
            }
            int poolMax = defaultPoolMax();
            if (singletonMode && lifecycle == ActionLifecycle.POOLED) {
                poolMax = parsePoolMax(actionNode, poolMax);
            }
            addLocal(pipeline, registry, localRef, actionName, section, singletonMode, isRegistryLocal, lifecycle, poolMax);
            return;
        }

        if (actionNode.has("$remote")) {
            if (singletonMode && lifecycleOverride != null && lifecycleOverride != ActionLifecycle.SHARED) {
                throw new IOException("Action lifecycle '" + lifecycleOverride.name().toLowerCase()
                    + "' is not supported for $remote actions");
            }

            JsonNode remoteSpecNode = actionNode.get("$remote");
            String endpointOrPath = parseRemoteEndpointOrPath(remoteSpecNode);

            HttpStep.RemoteSpec<String> spec = remoteDefaults.spec(endpointOrPath, body -> body, (ctx, body) -> body);
            if (remoteSpecNode.isObject()) {
                spec.timeoutMillis = remoteSpecNode.path("timeoutMillis").asInt(spec.timeoutMillis);
                spec.retries = remoteSpecNode.path("retries").asInt(spec.retries);
                spec.headers = remoteDefaults.mergeHeaders(parseStringMap(remoteSpecNode.get("headers")));
            }

            String method = remoteSpecNode.path("method").asText(remoteDefaults.method);
            StepAction<String> remoteAction = "GET".equalsIgnoreCase(method) ? HttpStep.jsonGet(spec) : HttpStep.jsonPost(spec);
            addStepActionToPipeline(pipeline, section, actionName, remoteAction);
            return;
        }

        if (actionNode.has("$prompt")) {
            throw new IOException(
                "Runtime does not execute $prompt steps. Run prompt codegen to produce a compiled pipeline JSON with $local references.");
        }

        throw new IOException("Unsupported action: " + actionNode.toString());
    }

    private static void addLocal(
        Pipeline<String> pipeline,
        ActionRegistry<String> registry,
        String localRef,
        String actionName,
        JsonSection section,
        boolean singletonMode,
        boolean isRegistryLocal,
        ActionLifecycle lifecycle,
        int poolMax
    ) throws IOException {
        if (isRegistryLocal) {
            if (singletonMode && lifecycle != ActionLifecycle.SHARED) {
                throw new IOException("Action lifecycle '" + lifecycle.name().toLowerCase()
                    + "' is not supported for registry actions: " + localRef);
            }
            if (registry.hasUnary(localRef)) {
                addUnaryToPipeline(pipeline, section, actionName, registry.getUnary(localRef));
            } else {
                addStepActionToPipeline(pipeline, section, actionName, registry.getAction(localRef));
            }
            return;
        }

        if (localRef.startsWith("prompt:")) {
            throw new IOException(
                "Prompt-generated action is missing from the registry: " + localRef
                    + ". Run prompt codegen and register generated actions (com.pipeline.generated.PromptGeneratedActions.register).");
        }

        if (!singletonMode || lifecycle == ActionLifecycle.SHARED) {
            addLocalShared(pipeline, localRef, actionName, section);
            return;
        }

        if (lifecycle == ActionLifecycle.PER_RUN) {
            addLocalPerRun(pipeline, localRef, actionName, section);
            return;
        }

        if (lifecycle == ActionLifecycle.POOLED) {
            addLocalPooled(pipeline, localRef, actionName, section, poolMax);
            return;
        }

        throw new IOException("Unsupported lifecycle: " + lifecycle);
    }

    private static void addLocalShared(
        Pipeline<String> pipeline,
        String localRef,
        String actionName,
        JsonSection section
    ) throws IOException {
        Object instance = instantiate(localRef);
        if (instance instanceof UnaryOperator<?> fn) {
            @SuppressWarnings("unchecked") UnaryOperator<String> unaryAction = (UnaryOperator<String>) fn;
            addUnaryToPipeline(pipeline, section, actionName, unaryAction);
            return;
        }
        if (instance instanceof StepAction<?> stepAction) {
            @SuppressWarnings("unchecked") StepAction<String> action = (StepAction<String>) stepAction;
            addStepActionToPipeline(pipeline, section, actionName, action);
            return;
        }
        throw new IOException("Class must implement UnaryOperator or StepAction: " + localRef);
    }

    private static void addLocalPooled(
        Pipeline<String> pipeline,
        String localRef,
        String actionName,
        JsonSection section,
        int poolMax
    ) throws IOException {
        Class<?> actionClass = resolveClass(localRef);
        if (!ResettableAction.class.isAssignableFrom(actionClass)) {
            throw new IOException("Action lifecycle 'pooled' requires ResettableAction: " + localRef);
        }

        LocalActionInvokeStyle invokeStyle = determineInvokeStyle(actionClass, localRef);
        Constructor<?> constructor = resolveNoArgsConstructor(actionClass, localRef);

        ActionPool<Object> pool = new ActionPool<>(poolMax, new ReflectiveNoArgFactory(constructor, localRef));
        StepAction<String> pooledAction = new PooledLocalAction<>(pool, invokeStyle, localRef);
        addStepActionToPipeline(pipeline, section, actionName, pooledAction);
    }

    private static void addLocalPerRun(
        Pipeline<String> pipeline,
        String localRef,
        String actionName,
        JsonSection section
    ) throws IOException {
        Class<?> actionClass = resolveClass(localRef);
        LocalActionInvokeStyle invokeStyle = determineInvokeStyle(actionClass, localRef);
        Constructor<?> constructor = resolveNoArgsConstructor(actionClass, localRef);

        StepAction<String> perRunAction = new PerRunLocalAction<>(constructor, invokeStyle, localRef);
        addStepActionToPipeline(pipeline, section, actionName, perRunAction);
    }

    private static LocalActionInvokeStyle determineInvokeStyle(Class<?> actionClass, String localRef) throws IOException {
        if (UnaryOperator.class.isAssignableFrom(actionClass)) return LocalActionInvokeStyle.UNARY_OPERATOR;
        if (StepAction.class.isAssignableFrom(actionClass)) return LocalActionInvokeStyle.STEP_ACTION;
        throw new IOException("Class must implement UnaryOperator or StepAction: " + localRef);
    }

    private static Class<?> resolveClass(String fqcn) throws IOException {
        try {
            return Class.forName(fqcn);
        } catch (Exception exception) {
            throw new IOException("Failed to load class " + fqcn, exception);
        }
    }

    private static Constructor<?> resolveNoArgsConstructor(Class<?> actionClass, String fqcn) throws IOException {
        try {
            Constructor<?> constructor = actionClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor;
        } catch (Exception exception) {
            throw new IOException("Failed to resolve no-args constructor for " + fqcn, exception);
        }
    }

    private static Object instantiate(String fqcn) throws IOException {
        try {
            Class<?> actionClass = Class.forName(fqcn);
            Constructor<?> constructor = actionClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception exception) {
            throw new IOException("Failed to instantiate " + fqcn, exception);
        }
    }

    private static String parseActionName(JsonNode actionNode) {
        String fromName = textOrNull(actionNode.get("name"));
        if (fromName != null && !fromName.isBlank()) return fromName;

        String fromLabel = textOrNull(actionNode.get("label"));
        if (fromLabel != null && !fromLabel.isBlank()) return fromLabel;

        return null;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) return null;
        return node.asText();
    }

    private static ActionLifecycle parseLifecycleOverride(JsonNode actionNode) throws IOException {
        JsonNode lifecycleNode = actionNode.get("lifecycle");
        if (lifecycleNode == null || lifecycleNode.isNull()) return null;
        if (!lifecycleNode.isTextual()) throw new IOException("lifecycle must be a string");

        String raw = lifecycleNode.asText("").trim().toLowerCase();
        return switch (raw) {
            case "shared" -> ActionLifecycle.SHARED;
            case "pooled" -> ActionLifecycle.POOLED;
            case "perrun", "per_run", "per-run" -> ActionLifecycle.PER_RUN;
            default -> throw new IOException("Unsupported lifecycle: " + raw);
        };
    }

    private static int parsePoolMax(JsonNode actionNode, int defaultMax) throws IOException {
        JsonNode poolNode = actionNode.get("pool");
        if (poolNode == null || poolNode.isNull()) return defaultMax;
        if (!poolNode.isObject()) throw new IOException("pool must be an object");

        JsonNode maxNode = poolNode.get("max");
        if (maxNode == null || maxNode.isNull()) return defaultMax;
        if (!maxNode.canConvertToInt()) throw new IOException("pool.max must be an integer");

        int maxValue = maxNode.asInt();
        if (maxValue < 1) throw new IOException("pool.max must be >= 1");
        return maxValue;
    }

    private static int defaultPoolMax() {
        int processors = Runtime.getRuntime().availableProcessors();
        int computed = processors * 8;
        return Math.min(256, Math.max(1, computed));
    }

    private static void addUnaryToPipeline(
        Pipeline<String> pipeline,
        JsonSection section,
        String actionName,
        UnaryOperator<String> action
    ) {
        if (section == JsonSection.PRE) {
            pipeline.addPreAction(actionName, action);
        } else if (section == JsonSection.POST) {
            pipeline.addPostAction(actionName, action);
        } else {
            pipeline.addAction(actionName, action);
        }
    }

    private static void addStepActionToPipeline(
        Pipeline<String> pipeline,
        JsonSection section,
        String actionName,
        StepAction<String> action
    ) {
        if (section == JsonSection.PRE) {
            pipeline.addPreAction(actionName, action);
        } else if (section == JsonSection.POST) {
            pipeline.addPostAction(actionName, action);
        } else {
            pipeline.addAction(actionName, action);
        }
    }

    private static JsonNode req(JsonNode n, String field) throws IOException {
        if (!n.has(field)) throw new IOException("Missing required field: " + field);
        return n.get(field);
    }

    private static boolean containsPromptSteps(JsonNode root) {
        for (String sectionName : new String[] { "preActions", "pre", "actions", "steps", "postActions", "post" }) {
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
