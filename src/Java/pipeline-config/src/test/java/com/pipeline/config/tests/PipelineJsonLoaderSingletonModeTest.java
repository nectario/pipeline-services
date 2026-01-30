package com.pipeline.config.tests;

import com.pipeline.config.PipelineJsonLoader;
import com.pipeline.core.ActionRegistry;
import com.pipeline.core.Pipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class PipelineJsonLoaderSingletonModeTest {

    @Test
    void pooledLifecycleProvidesConcurrencySafety() throws Exception {
        String json = """
            {
              "pipeline": "pooled_concurrency",
              "type": "unary",
              "singletonMode": true,
              "actions": [
                {
                  "$local": "com.pipeline.config.tests.PooledStatefulEchoAction",
                  "lifecycle": "pooled",
                  "pool": { "max": 32 }
                }
              ]
            }
            """;

        ActionRegistry<String> registry = new ActionRegistry<>();
        Pipeline<String> pipeline = PipelineJsonLoader.loadUnary(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), registry);

        int taskCount = 200;
        int threadCount = 32;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int index = 0; index < taskCount; index++) {
                String input = "run-" + index;
                futures.add(executor.submit(new PipelineRunTask(pipeline, input)));
            }

            for (int index = 0; index < taskCount; index++) {
                String expected = "run-" + index;
                assertEquals(expected, get(futures.get(index)));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void pooledLifecycleRequiresResettableAction() {
        String json = """
            {
              "pipeline": "pooled_requires_resettable",
              "type": "unary",
              "singletonMode": true,
              "actions": [
                {
                  "$local": "com.pipeline.config.tests.NonResettableEchoAction",
                  "lifecycle": "pooled"
                }
              ]
            }
            """;

        ActionRegistry<String> registry = new ActionRegistry<>();
        IOException exception = assertThrows(
            IOException.class,
            new LoadPipelineTask(json, registry)
        );

        assertEquals(
            "Action lifecycle 'pooled' requires ResettableAction: com.pipeline.config.tests.NonResettableEchoAction",
            exception.getMessage()
        );
    }

    @Test
    void actionArrayAliasesPreferNewKeysOverLegacy() throws Exception {
        ActionRegistry<String> registry = new ActionRegistry<>();
        registry.registerUnary("new_pre", PipelineJsonLoaderSingletonModeTest::newPre);
        registry.registerUnary("legacy_pre", PipelineJsonLoaderSingletonModeTest::legacyPre);
        registry.registerUnary("new_main", PipelineJsonLoaderSingletonModeTest::newMain);
        registry.registerUnary("legacy_main", PipelineJsonLoaderSingletonModeTest::legacyMain);
        registry.registerUnary("new_post", PipelineJsonLoaderSingletonModeTest::newPost);
        registry.registerUnary("legacy_post", PipelineJsonLoaderSingletonModeTest::legacyPost);

        String json = """
            {
              "pipeline": "aliases",
              "type": "unary",
              "preActions": [ { "$local": "new_pre" } ],
              "pre":       [ { "$local": "legacy_pre" } ],
              "actions":   [ { "$local": "new_main" } ],
              "steps":     [ { "$local": "legacy_main" } ],
              "postActions": [ { "$local": "new_post" } ],
              "post":        [ { "$local": "legacy_post" } ]
            }
            """;

        Pipeline<String> pipeline = PipelineJsonLoader.loadUnary(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), registry);
        assertEquals("input[preActions][actions][postActions]", pipeline.run("input").context());
    }

    @Test
    void legacyActionArrayKeysStillLoad() throws Exception {
        ActionRegistry<String> registry = new ActionRegistry<>();
        registry.registerUnary("legacy_pre", PipelineJsonLoaderSingletonModeTest::legacyPre);
        registry.registerUnary("legacy_main", PipelineJsonLoaderSingletonModeTest::legacyMain);
        registry.registerUnary("legacy_post", PipelineJsonLoaderSingletonModeTest::legacyPost);

        String json = """
            {
              "pipeline": "legacy_aliases",
              "type": "unary",
              "pre":   [ { "$local": "legacy_pre" } ],
              "steps": [ { "$local": "legacy_main" } ],
              "post":  [ { "$local": "legacy_post" } ]
            }
            """;

        Pipeline<String> pipeline = PipelineJsonLoader.loadUnary(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), registry);
        assertEquals("input[preLegacy][steps][postLegacy]", pipeline.run("input").context());
    }

    private static String newPre(String input) {
        return input + "[preActions]";
    }

    private static String legacyPre(String input) {
        return input + "[preLegacy]";
    }

    private static String newMain(String input) {
        return input + "[actions]";
    }

    private static String legacyMain(String input) {
        return input + "[steps]";
    }

    private static String newPost(String input) {
        return input + "[postActions]";
    }

    private static String legacyPost(String input) {
        return input + "[postLegacy]";
    }

    private static String get(Future<String> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            if (cause instanceof Exception causeException) {
                throw causeException;
            }
            throw executionException;
        }
    }

    private static final class PipelineRunTask implements java.util.concurrent.Callable<String> {
        private final Pipeline<String> pipeline;
        private final String input;

        private PipelineRunTask(Pipeline<String> pipeline, String input) {
            this.pipeline = pipeline;
            this.input = input;
        }

        @Override
        public String call() {
            return pipeline.run(input).context();
        }
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
