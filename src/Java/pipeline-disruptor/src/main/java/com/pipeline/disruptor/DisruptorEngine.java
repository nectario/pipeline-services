package com.pipeline.disruptor;

import com.pipeline.core.Pipeline;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Experimental single-consumer engine. Pipeline observability is configured on the Pipeline itself. */
public final class DisruptorEngine<T> implements AutoCloseable {
  private final String name;
  private final Pipeline<T> pipeline;
  private final ArrayBlockingQueue<T> queue;
  private final ExecutorService worker;
  private volatile boolean running = true;

  public DisruptorEngine(String name, int bufferSize, Pipeline<T> pipeline) {
    this.name = Objects.requireNonNull(name, "name");
    this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
    this.queue = new ArrayBlockingQueue<>(bufferSize);
    this.worker = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "engine-" + name);
      thread.setDaemon(true);
      return thread;
    });
    worker.execute(this::loop);
  }

  private void loop() {
    while (running) {
      try {
        T payload = queue.take();
        pipeline.run(payload);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        break;
      } catch (RuntimeException ignored) {
        // The engine remains alive; PipelineObserver is the canonical observability seam.
      }
    }
  }

  public void publish(T payload) {
    if (!running) throw new IllegalStateException("engine stopped");
    queue.offer(payload);
  }

  public void shutdown() {
    running = false;
    worker.shutdownNow();
  }

  @Override
  public void close() {
    shutdown();
  }
}
