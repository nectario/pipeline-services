package com.pipeline.examples;

import com.pipeline.config.PipelineJsonLoader;
import com.pipeline.core.Pipeline;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class Example17JsonSingletonMode {
  private Example17JsonSingletonMode() {}

  public static void run() throws Exception {
    try (InputStream inputStream = Example17JsonSingletonMode.class.getResourceAsStream("/pipelines/singleton_mode_pooled.json")) {
      if (inputStream == null) throw new IllegalStateException("Missing resource: /pipelines/singleton_mode_pooled.json");

        Pipeline<String> pipeline = PipelineJsonLoader.loadUnary(inputStream);

      int runCount = 50;
      ExecutorService executor = Executors.newFixedThreadPool(8);
      try {
        List<Future<String>> futures = new ArrayList<>();
        for (int index = 0; index < runCount; index++) {
          String input = "  hello   world  " + index;
          futures.add(executor.submit(new PipelineRunTask(pipeline, input)));
        }

        for (int index = 0; index < futures.size(); index++) {
          System.out.println("[ex17] => " + futures.get(index).get());
        }
      } finally {
        executor.shutdownNow();
      }
    }
  }

  private static final class PipelineRunTask implements Callable<String> {
    private final Pipeline<String> pipeline;
    private final String input;

    private PipelineRunTask(Pipeline<String> pipeline, String input) {
      this.pipeline = pipeline;
      this.input = input;
    }

    @Override
    public String call() {
      return pipeline.run(input).context();
    }
  }
}
