package com.pipeline.disruptor;

import com.pipeline.core.Pipeline;
import com.pipeline.metrics.Metrics;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DisruptorEngine<T> implements AutoCloseable {
    private final String name;
    private final Pipeline<T> pipeline;
    private final ArrayBlockingQueue<T> queue;
    private final ExecutorService worker;
    private volatile boolean running = true;

    public DisruptorEngine(String name, int bufferSize, Pipeline<T> pipeline, com.pipeline.metrics.MetricsRecorder metrics) {
        this.name = Objects.requireNonNull(name);
        this.pipeline = Objects.requireNonNull(pipeline);
        this.queue = new ArrayBlockingQueue<>(bufferSize);
        if (metrics != null) Metrics.setRecorder(metrics);
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ps-disruptor-" + name);
            t.setDaemon(true);
            return t;
        });
        worker.submit(this::runLoop);
    }

    private void runLoop() {
        while (running) {
            try {
                T payload = queue.poll(50, TimeUnit.MILLISECONDS);
                if (payload == null) continue;
                try {
                    pipeline.run(payload);
                } catch (Exception e) {
                    if (pipeline.shortCircuit()) {
                        Metrics.recorder().onShortCircuit(pipeline.name(), "engine");
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
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
