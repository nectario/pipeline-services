package com.pipeline.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.pipeline.core.PipelineExecution.shortCircuit;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class PipelineObserverTest {

  @Test
  void observerReceivesEventsFromTheCanonicalRunner() {
    RecordingObserver observer = new RecordingObserver();
    Pipeline<String> pipeline = new Pipeline<String>("observed")
        .observer(observer)
        .addPreAction("prepare", value -> value + "P")
        .addAction("stop", value -> {
          shortCircuit();
          return value + "A";
        })
        .addAction("skipped", value -> value + "B")
        .addPostAction("audit", value -> value + "Z");

    assertEquals("XPAZ", pipeline.run("X"));
    assertEquals(List.of(
        "pipeline:start:observed",
        "action:start:PRE:0:pre0:prepare",
        "action:completed:PRE:0:pre0:prepare",
        "action:start:MAIN:0:s0:stop",
        "action:completed:MAIN:0:s0:stop",
        "pipeline:shortCircuit:MAIN:0:s0:stop",
        "action:start:POST:0:post0:audit",
        "action:completed:POST:0:post0:audit",
        "pipeline:completed:true:0"), observer.events);
  }

  @Test
  void observerFailuresNeverChangePipelineSemantics() {
    PipelineObserver failingObserver = new PipelineObserver() {
      @Override
      public void onPipelineStarted(String pipelineName) {
        throw new IllegalStateException("observer failure");
      }

      @Override
      public void onActionStarted(
          String pipelineName,
          StepPhase phase,
          int actionIndex,
          String actionName) {
        throw new IllegalStateException("observer failure");
      }

      @Override
      public void onPipelineCompleted(
          String pipelineName,
          boolean shortCircuited,
          int errorCount,
          long elapsedNanos) {
        throw new IllegalStateException("observer failure");
      }
    };

    Pipeline<String> pipeline = new Pipeline<String>("observer_isolation")
        .observer(failingObserver)
        .addAction(value -> value + "A")
        .addPostAction(value -> value + "P");

    assertEquals("XAP", pipeline.run("X"));
  }

  @Test
  void observerCannotShortCircuitOutsideAnActionBody() {
    PipelineObserver controllingObserver = new PipelineObserver() {
      @Override
      public void onActionStarted(
          String pipelineName,
          StepPhase phase,
          int actionIndex,
          String actionName) {
        shortCircuit();
      }

      @Override
      public void onActionCompleted(
          String pipelineName,
          StepPhase phase,
          int actionIndex,
          String actionName,
          long elapsedNanos) {
        shortCircuit();
      }
    };

    Pipeline<String> pipeline = new Pipeline<String>("observer_control_isolation")
        .observer(controllingObserver)
        .addAction(value -> value + "A")
        .addAction(value -> value + "B")
        .addPostAction(value -> value + "P");

    assertEquals("XABP", pipeline.run("X"));
  }

  private static final class RecordingObserver implements PipelineObserver {
    private final List<String> events = new ArrayList<>();

    @Override
    public void onPipelineStarted(String pipelineName) {
      events.add("pipeline:start:" + pipelineName);
    }

    @Override
    public void onActionStarted(
        String pipelineName,
        StepPhase phase,
        int actionIndex,
        String actionName) {
      events.add("action:start:" + phase + ":" + actionIndex + ":" + actionName);
    }

    @Override
    public void onActionCompleted(
        String pipelineName,
        StepPhase phase,
        int actionIndex,
        String actionName,
        long elapsedNanos) {
      events.add("action:completed:" + phase + ":" + actionIndex + ":" + actionName);
    }

    @Override
    public void onShortCircuited(
        String pipelineName,
        StepPhase phase,
        int actionIndex,
        String actionName) {
      events.add("pipeline:shortCircuit:" + phase + ":" + actionIndex + ":" + actionName);
    }

    @Override
    public void onPipelineCompleted(
        String pipelineName,
        boolean shortCircuited,
        int errorCount,
        long elapsedNanos) {
      events.add("pipeline:completed:" + shortCircuited + ":" + errorCount);
    }
  }
}
