package com.pipeline.core;

import org.junit.jupiter.api.Test;

import static com.pipeline.core.PipelineExecution.shortCircuit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PipelineTest {

  @Test
  void runReturnsTheFinalContext() {
    Pipeline<String> pipeline = new Pipeline<String>("simple", false)
        .addAction(value -> value + "A")
        .addAction(value -> value + "B");

    assertEquals("XAB", pipeline.run("X"));
  }

  @Test
  void checkedActionExceptionIsCapturedWithoutAdapterCode() {
    Pipeline<String> pipeline = new Pipeline<String>("checked_exception", true)
        .addAction(PipelineTest::throwCheckedException)
        .addPostAction(value -> value + "P");

    PipelineResult<String> result = pipeline.runDetailed("X");

    assertEquals("XP", result.context());
    assertTrue(result.shortCircuited());
    assertEquals(1, result.errors().size());
    assertEquals("checked boom", result.errors().getFirst().exception().getMessage());
  }

  @Test
  void shortCircuitOnExceptionTrueStopsAndCaptures() {
    Pipeline<String> pipeline = new Pipeline<String>("stop_on_error", true)
        .addAction(value -> value + "A")
        .addAction(value -> {
          throw new RuntimeException("boom");
        })
        .addAction(value -> value + "B")
        .addPostAction(value -> value + "P");

    PipelineResult<String> result = pipeline.runDetailed("X");

    assertEquals("XAP", result.context());
    assertTrue(result.shortCircuited());
    assertEquals(1, result.errors().size());
    assertEquals("boom", result.errors().getFirst().exception().getMessage());
  }

  @Test
  void shortCircuitOnExceptionFalseContinuesAndCaptures() {
    Pipeline<String> pipeline = new Pipeline<String>("continue_on_error", false)
        .addAction(value -> value + "A")
        .addAction(value -> {
          throw new RuntimeException("boom");
        })
        .addAction(value -> value + "B");

    PipelineResult<String> result = pipeline.runDetailed("X");

    assertEquals("XAB", result.context());
    assertFalse(result.shortCircuited());
    assertEquals(1, result.errors().size());
  }

  @Test
  void detachedShortCircuitPreservesReturnedContextAndRunsPostActions() {
    Pipeline<String> pipeline = new Pipeline<String>("detached", true)
        .addAction(PipelineTest::appendAndShortCircuit)
        .addAction(value -> value + "B")
        .addPostAction(value -> value + "P");

    PipelineResult<String> result = pipeline.runDetailed("X");

    assertEquals("XAP", result.context());
    assertTrue(result.shortCircuited());
    assertTrue(result.errors().isEmpty());
  }

  @Test
  void shortCircuitInPreActionsCompletesPreActionsSkipsActionsAndRunsPostActions() {
    Pipeline<String> pipeline = new Pipeline<String>("pre_short_circuit", true)
        .addPreAction(value -> {
          shortCircuit();
          return value + "1";
        })
        .addPreAction(value -> value + "2")
        .addAction(value -> value + "M")
        .addPostAction(value -> value + "P");

    assertEquals("X12P", pipeline.run("X"));
  }

  @Test
  void shortCircuitInPostActionsDoesNotSkipRemainingPostActions() {
    Pipeline<String> pipeline = new Pipeline<String>("post_short_circuit", true)
        .addAction(value -> value + "M")
        .addPostAction(value -> {
          shortCircuit();
          return value + "1";
        })
        .addPostAction(value -> value + "2");

    PipelineResult<String> result = pipeline.runDetailed("X");

    assertEquals("XM12", result.context());
    assertTrue(result.shortCircuited());
  }

  @Test
  void directBuilderSubclassAndCompositionUseTheSameSemantics() {
    Pipeline<String> direct = new Pipeline<String>("direct")
        .addAction(PipelineTest::appendAndShortCircuit)
        .addAction(value -> value + "B")
        .addPostAction(value -> value + "P");

    Pipeline<String> built = Pipeline.<String>builder("builder")
        .addAction(PipelineTest::appendAndShortCircuit)
        .addAction(value -> value + "B")
        .addPostAction(value -> value + "P")
        .build();

    assertEquals("XAP", direct.run("X"));
    assertEquals("XAP", built.run("X"));
    assertEquals("XAP", new SubclassPipeline().run("X"));
    assertEquals("XAP", new ComposedProcessor().process("X"));
  }

  @Test
  void firstRunFreezesStructuralConfiguration() {
    Pipeline<String> pipeline = new Pipeline<String>("frozen")
        .addAction(value -> value + "A");

    assertEquals("XA", pipeline.run("X"));
    assertTrue(pipeline.isFrozen());
    assertThrows(
        IllegalStateException.class,
        () -> pipeline.addAction(value -> value + "B"));
  }

  @Test
  void shortCircuitOutsideAnActiveRunFailsClearly() {
    IllegalStateException error = assertThrows(
        IllegalStateException.class,
        PipelineExecution::shortCircuit);

    assertTrue(error.getMessage().contains("active pipeline run"));
  }

  private static String throwCheckedException(String value) throws Exception {
    throw new Exception("checked boom");
  }

  private static String appendAndShortCircuit(String value) {
    shortCircuit();
    return value + "A";
  }

  private static final class SubclassPipeline extends Pipeline<String> {
    private SubclassPipeline() {
      super("subclass");
      addAction(this::stopAfterAppend);
      addAction(value -> value + "B");
      addPostAction(value -> value + "P");
    }

    private String stopAfterAppend(String value) {
      shortCircuit();
      return value + "A";
    }
  }

  private static final class ComposedProcessor {
    private final Pipeline<String> pipeline = new Pipeline<String>("composition")
        .addAction(this::stopAfterAppend)
        .addAction(value -> value + "B")
        .addPostAction(value -> value + "P");

    private String process(String value) {
      return pipeline.run(value);
    }

    private String stopAfterAppend(String value) {
      pipeline.shortCircuit();
      return value + "A";
    }
  }
}
