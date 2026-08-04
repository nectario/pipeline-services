package com.pipeline.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

final class PipelineProviderTest {

  @Test
  void newInstancePerEventCreatesOnePipelineForEverySelection() {
    AtomicInteger instanceCounter = new AtomicInteger();
    Supplier<Pipeline<String>> factory = () -> identifiedPipeline(
        "per_event",
        instanceCounter.incrementAndGet());

    PipelineProvider<String> provider = PipelineProvider.newInstancePerEvent(factory);

    Pipeline<String> first = provider.getPipeline();
    Pipeline<String> second = provider.getPipeline();

    assertEquals(PipelineProviderMode.NEW_INSTANCE_PER_EVENT, provider.mode());
    assertEquals(0, provider.instanceCount());
    assertNotSame(first, second);
    assertEquals("event|1", first.run("event"));
    assertEquals("event|2", second.run("event"));
  }

  @Test
  void singletonAlwaysReturnsTheSameFrozenPipeline() {
    Pipeline<String> pipeline = identifiedPipeline("singleton", 7);
    PipelineProvider<String> provider = PipelineProvider.singleton(pipeline);

    assertEquals(PipelineProviderMode.SINGLETON, provider.mode());
    assertEquals(1, provider.instanceCount());
    assertSame(pipeline, provider.getPipeline());
    assertSame(pipeline, provider.getPipeline());
    assertEquals("event|7", provider.run("event"));
    assertEquals(true, pipeline.isFrozen());
  }

  @Test
  void pooledEagerlyCreatesFixedInstancesAndSelectsRoundRobin() {
    AtomicInteger instanceCounter = new AtomicInteger();
    PipelineProvider<String> provider = PipelineProvider.pooled(
        () -> identifiedPipeline("pooled", instanceCounter.incrementAndGet()),
        3);

    assertEquals(PipelineProviderMode.POOLED, provider.mode());
    assertEquals(3, provider.instanceCount());
    assertEquals(3, instanceCounter.get(), "pool must be created eagerly");

    Pipeline<String> first = provider.getPipeline();
    Pipeline<String> second = provider.getPipeline();
    Pipeline<String> third = provider.getPipeline();
    Pipeline<String> fourth = provider.getPipeline();

    assertNotSame(first, second);
    assertNotSame(second, third);
    assertSame(first, fourth);
    assertEquals("event|1", first.run("event"));
    assertEquals("event|2", second.run("event"));
    assertEquals("event|3", third.run("event"));
  }

  @Test
  void providerBuilderDelegatesToTheSameLifecycleImplementation() {
    AtomicInteger instanceCounter = new AtomicInteger();
    PipelineProvider<String> provider = PipelineProvider.<String>builder(
            () -> identifiedPipeline("builder", instanceCounter.incrementAndGet()))
        .mode(PipelineProviderMode.POOLED)
        .instanceCount(2)
        .build();

    assertEquals(PipelineProviderMode.POOLED, provider.mode());
    assertEquals(2, provider.instanceCount());
    assertEquals(2, instanceCounter.get());
  }

  @Test
  void routerSelectsAProviderWithoutChangingProviderOrPipelineSemantics() {
    PipelineProvider<String> left = PipelineProvider.singleton(
        identifiedPipeline("left", 1));
    PipelineProvider<String> right = PipelineProvider.singleton(
        identifiedPipeline("right", 2));

    PipelineRouter<String, String> router = event ->
        event.startsWith("L") ? left : right;

    assertSame(left, router.getPipelineProvider("LEFT"));
    assertSame(right, router.getPipelineProvider("RIGHT"));
    assertEquals("value|1", router.run("LEFT", "value"));
    assertEquals("value|2", router.run("RIGHT", "value"));
  }

  private static Pipeline<String> identifiedPipeline(String name, int instanceId) {
    return new Pipeline<String>(name)
        .addAction(value -> value + "|" + instanceId);
  }
}
