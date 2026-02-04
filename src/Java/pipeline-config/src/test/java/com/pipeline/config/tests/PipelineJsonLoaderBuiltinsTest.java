package com.pipeline.config.tests;

import com.pipeline.config.PipelineJsonLoader;
import com.pipeline.core.ActionRegistry;
import com.pipeline.core.Pipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class PipelineJsonLoaderBuiltinsTest {

    @Test
    void identityBuiltinCanBeUsedAsAPlaceholderWithoutRegistryOrReflection() throws Exception {
        String json = """
            {
              "pipeline": "builtins_identity",
              "type": "unary",
              "reflectionEnabled": false,
              "actions": [
                { "label": "todo_normalize", "$local": "identity" }
              ]
            }
            """;

        ActionRegistry<String> registry = new ActionRegistry<>();
        Pipeline<String> pipeline = PipelineJsonLoader.loadUnary(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
            registry
        );

        assertEquals("input", pipeline.run("input").context());
    }

    @Test
    void reflectionDisabledFailsFastForUnknownLocalActions() {
        String json = """
            {
              "pipeline": "no_reflection",
              "type": "unary",
              "reflectionEnabled": false,
              "actions": [
                { "$local": "com.example.DoesNotExist" }
              ]
            }
            """;

        ActionRegistry<String> registry = new ActionRegistry<>();
        IOException exception = assertThrows(
            IOException.class,
            new LoadPipelineTask(json, registry)
        );

        assertEquals(
            "Reflection is disabled. Register the action in the ActionRegistry or use built-ins (e.g., $local: \"identity\"): com.example.DoesNotExist",
            exception.getMessage()
        );
    }

    private static final class LoadPipelineTask implements Executable {
        private final String json;
        private final ActionRegistry<String> registry;

        private LoadPipelineTask(String json, ActionRegistry<String> registry) {
            this.json = json;
            this.registry = registry;
        }

        @Override
        public void execute() throws Throwable {
            PipelineJsonLoader.loadUnary(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), registry);
        }
    }
}

