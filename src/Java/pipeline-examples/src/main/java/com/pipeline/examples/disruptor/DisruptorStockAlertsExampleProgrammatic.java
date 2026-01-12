package com.pipeline.examples.disruptor;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;

import com.pipeline.api.Pipeline;
import com.pipeline.core.metrics.LoggingMetrics;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/** Stock trading alerting example using Disruptor + Pipeline Services (programmatic pipeline). */
public final class DisruptorStockAlertsExampleProgrammatic {

  /** Typed pipeline built in code: MarketDataEvent -> Enriched -> Alert */
  static Pipeline<MarketDataEvent, Alerts.Alert> buildPipeline() {
    return new Pipeline<MarketDataEvent, MarketDataEvent>()
        .metrics(new LoggingMetrics())
        .enableJumps(false) // no jumps needed here
        .addAction("enrich", PriceSteps::enrich)      // MarketDataEvent -> Enriched
        .addAction("alert",  PriceSteps::alert);      // Enriched -> Alert
  }

  static final class AlertingHandler implements EventHandler<MarketDataEvent> {
    private final Pipeline<MarketDataEvent, Alerts.Alert> pipeline;
    AlertingHandler(Pipeline<MarketDataEvent, Alerts.Alert> pipeline) { this.pipeline = pipeline; }
    @Override public void onEvent(MarketDataEvent ev, long seq, boolean endOfBatch) throws Exception {
      var out = pipeline.run(ev, Alerts.Alert.class);
      if (!"NONE".equals(out.level)) {
        System.out.println(out);
      }
    }
  }

  public static void run() {
    int bufferSize = 1024;
    var pipeline = buildPipeline();

    Disruptor<MarketDataEvent> disruptor = new Disruptor<>(MarketDataEvent::new, bufferSize,
        DaemonThreadFactory.INSTANCE, ProducerType.SINGLE, new com.lmax.disruptor.BlockingWaitStrategy());

    disruptor.handleEventsWith(new AlertingHandler(pipeline));
    RingBuffer<MarketDataEvent> ring = disruptor.start();

    // Producer: random walk prices
    Thread producer = new Thread(() -> {
      Random rnd = new Random(7);
      double px = 100.0;
      String[] syms = {"AAPL","MSFT","NVDA"};
      try {
        for (int i=0; i<500; i++) {
          String sym = syms[i % syms.length];
          px += (rnd.nextDouble() - 0.5) * 2.0; // +/-1
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
