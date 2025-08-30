package com.pipeline.disruptor;

import com.pipeline.core.Pipeline;
import com.pipeline.metrics.Metrics;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DisruptorEngine<T> implements AutoCloseable {
    private final String name;
    private final Pipeline<T> pipeline;
    private final ArrayBlockingQueue<T> queue;
    private final ExecutorService worker;
    private volatile boolean running = true;

    public DisruptorEngine(String name, int bufferSize, Pipeline<T> pipeline) {
        this.name = Objects.requireNonNull(name);
        this.pipeline = Objects.requireNonNull(pipeline);
        this.queue = new ArrayBlockingQueue<>(bufferSize);
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "engine-" + name);
            t.setDaemon(true);
            return t;
        });
        worker.execute(this::loop);
    }

    private void loop() {
        while (running) {
            try {
                T payload = queue.take();
                var rec = Metrics.recorder();
                long t0 = System.nanoTime();
                pipeline.run(payload);
                rec.onStepSuccess(name, "e2e", System.nanoTime() - t0);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                Metrics.recorder().onStepError(name, "e2e", t);
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

    @Override public void close() { shutdown(); }
}
