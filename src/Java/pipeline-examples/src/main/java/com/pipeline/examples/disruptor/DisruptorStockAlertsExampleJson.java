package com.pipeline.examples.disruptor;

import com.pipeline.api.Pipeline;
import com.pipeline.core.metrics.LoggingMetrics;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;

public final class DisruptorStockAlertsExampleJson {

  static Pipeline<MarketDataEvent, MarketDataEvent> buildPipelineFromJson() {
    String path = "pipeline-examples/src/main/resources/pipelines/disruptor_alerts.json";
    return new Pipeline<MarketDataEvent, MarketDataEvent>(path)
        .metrics(new LoggingMetrics())
        .enableJumps(false);
  }

  static final class AlertingHandler implements EventHandler<MarketDataEvent> {
    private final Pipeline<MarketDataEvent, MarketDataEvent> pipeline;
    AlertingHandler(Pipeline<MarketDataEvent, MarketDataEvent> pipeline) { this.pipeline = pipeline; }
    @Override public void onEvent(MarketDataEvent ev, long seq, boolean endOfBatch) throws Exception {
      var out = pipeline.run(ev, Alerts.Alert.class);
      if (!"NONE".equals(out.level)) {
        System.out.println(out);
      }
    }
  }

  public static void run() {
    int bufferSize = 1024;
    var pipeline = buildPipelineFromJson();

    Disruptor<MarketDataEvent> disruptor = new Disruptor<>(MarketDataEvent::new, bufferSize,
        DaemonThreadFactory.INSTANCE, ProducerType.SINGLE, new com.lmax.disruptor.BlockingWaitStrategy());

    disruptor.handleEventsWith(new AlertingHandler(pipeline));
    RingBuffer<MarketDataEvent> ring = disruptor.start();

    Thread producer = new Thread(() -> {
      Random rnd = new Random(7);
      double px = 100.0;
      String[] syms = {"AAPL","MSFT","NVDA"};
      try {
        for (int i=0; i<500; i++) {
          String sym = syms[i % syms.length];
          px += (rnd.nextDouble() - 0.5) * 2.0;
          double pxx = Math.max(1.0, px);
          ring.publishEvent((ev, seq, s, p) -> ev.set(s, p, System.nanoTime()), sym, pxx);
          TimeUnit.MILLISECONDS.sleep(5);
        }
      } catch (InterruptedException ignored) {}
    }, "producer");
    producer.setDaemon(true);
    producer.start();

    try { TimeUnit.SECONDS.sleep(3); } catch (InterruptedException ignored) {}
    disruptor.shutdown();
  }
}
