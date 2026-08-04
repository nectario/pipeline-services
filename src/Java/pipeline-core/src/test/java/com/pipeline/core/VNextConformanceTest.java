package com.pipeline.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.pipeline.core.PipelineExecution.shortCircuit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VNextConformanceTest {

  @Test
  void actionsExecuteInExactPhaseOrderAndPropagateContext() {
    List<String> executionOrder = new ArrayList<>();

    Pipeline<String> pipeline = new Pipeline<String>("ordered")
        .addPreAction("pre1", value -> record(executionOrder, "pre1", value + "1"))
        .addPreAction("pre2", value -> record(executionOrder, "pre2", value + "2"))
        .addAction("action1", value -> record(executionOrder, "action1", value + "3"))
        .addAction("action2", value -> record(executionOrder, "action2", value + "4"))
        .addPostAction("post1", value -> record(executionOrder, "post1", value + "5"))
        .addPostAction("post2", value -> record(executionOrder, "post2", value + "6"));

    assertEquals("X123456", pipeline.run("X"));
    assertEquals(
        List.of("pre1", "pre2", "action1", "action2", "post1", "post2"),
        executionOrder);
  }

  @Test
  void runAndRunDetailedHaveTheSameExecutionSemantics() {
    Pipeline<String> pipeline = new Pipeline<String>("same_runner")
        .addPreAction(value -> value + "P")
        .addAction(value -> value + "A")
        .addPostAction(value -> value + "Z");

    String simpleResult = pipeline.run("X");
    PipelineResult<String> detailedResult = pipeline.runDetailed("X");

    assertEquals("XPAZ", simpleResult);
    assertEquals(simpleResult, detailedResult.context());
    assertFalse(detailedResult.shortCircuited());
    assertTrue(detailedResult.errors().isEmpty());
    assertEquals(3, detailedResult.actionTimings().size());
  }

  @Test
  void exceptionInPreActionsCompletesPreSkipsMainAndRunsPost() {
    List<String> executionOrder = new ArrayList<>();

    Pipeline<String> pipeline = new Pipeline<String>("pre_exception", true)
        .addPreAction("pre1", value -> {
          executionOrder.add("pre1");
          throw new Exception("pre boom");
        })
        .addPreAction("pre2", value -> record(executionOrder, "pre2", value + "2"))
        .addAction("action1", value -> record(executionOrder, "action1", value + "M"))
        .addPostAction("post1", value -> record(executionOrder, "post1", value + "P"));

    PipelineResult<String> result = pipeline.runDetailed("X");

    assertEquals("X2P", result.context());
    assertEquals(List.of("pre1", "pre2", "post1"), executionOrder);
    assertTrue(result.shortCircuited());
    assertEquals(1, result.errors().size());
    assertEquals(0, result.errors().getFirst().actionIndex());
    assertEquals("pre0:pre1", result.errors().getFirst().actionName());
  }

  @Test
  void shortCircuitStateIsClearedAfterEveryRun() {
    Pipeline<String> pipeline = new Pipeline<String>("reusable")
        .addAction(value -> {
          if (value.startsWith("stop")) shortCircuit();
          return value + "|A";
        })
        .addAction(value -> value + "|B")
        .addPostAction(value -> value + "|P");

    PipelineResult<String> stoppedResult = pipeline.runDetailed("stop");
    PipelineResult<String> continuingResult = pipeline.runDetailed("continue");

    assertEquals("stop|A|P", stoppedResult.context());
    assertTrue(stoppedResult.shortCircuited());

    assertEquals("continue|A|B|P", continuingResult.context());
    assertFalse(continuingResult.shortCircuited());
  }

  private static String record(
      List<String> executionOrder,
      String actionName,
      String nextContext) {
    executionOrder.add(actionName);
    return nextContext;
  }
}
