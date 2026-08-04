package com.pipeline.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PipelineErrorHandlerFailureTest {

  @Test
  void nullErrorHandlerResultFailsClearlyAfterPostActionsRun() {
    AtomicBoolean postActionRan = new AtomicBoolean();

    Pipeline<String> pipeline = new Pipeline<String>("null_error_handler", true)
        .onError((context, error) -> null)
        .addAction(value -> {
          throw new Exception("action boom");
        })
        .addPostAction(value -> {
          postActionRan.set(true);
          return value + "|P";
        });

    IllegalStateException failure = assertThrows(
        IllegalStateException.class,
        () -> pipeline.run("X"));

    assertTrue(postActionRan.get(), "postActions must run before the handler failure escapes");
    assertTrue(failure.getMessage().contains("onError returned null"));
    assertEquals("action boom", failure.getCause().getMessage());
  }

  @Test
  void throwingErrorHandlerFailsClearlyAfterPostActionsRun() {
    AtomicBoolean postActionRan = new AtomicBoolean();

    Pipeline<String> pipeline = new Pipeline<String>("throwing_error_handler", true)
        .onError((context, error) -> {
          throw new IllegalArgumentException("handler boom");
        })
        .addAction(value -> {
          throw new Exception("action boom");
        })
        .addPostAction(value -> {
          postActionRan.set(true);
          return value + "|P";
        });

    IllegalStateException failure = assertThrows(
        IllegalStateException.class,
        () -> pipeline.run("X"));

    assertTrue(postActionRan.get(), "postActions must run before the handler failure escapes");
    assertTrue(failure.getMessage().contains("onError failed"));
    IllegalArgumentException handlerFailure = assertInstanceOf(
        IllegalArgumentException.class,
        failure.getCause());
    assertEquals("handler boom", handlerFailure.getMessage());
    assertEquals(1, failure.getSuppressed().length);
    assertEquals("action boom", failure.getSuppressed()[0].getMessage());
  }

  @Test
  void brokenErrorHandlerInPostDoesNotSkipRemainingPostActions() {
    AtomicBoolean secondPostActionRan = new AtomicBoolean();

    Pipeline<String> pipeline = new Pipeline<String>("post_error_handler", true)
        .onError((context, error) -> {
          throw new IllegalStateException("handler boom");
        })
        .addAction(value -> value + "|A")
        .addPostAction(value -> {
          throw new Exception("post boom");
        })
        .addPostAction(value -> {
          secondPostActionRan.set(true);
          return value + "|P2";
        });

    IllegalStateException failure = assertThrows(
        IllegalStateException.class,
        () -> pipeline.run("X"));

    assertTrue(secondPostActionRan.get(), "remaining postActions must still execute");
    assertTrue(failure.getMessage().contains("onError failed"));
    assertEquals("handler boom", failure.getCause().getMessage());
    assertEquals(1, failure.getSuppressed().length);
    assertEquals("post boom", failure.getSuppressed()[0].getMessage());
  }
}
