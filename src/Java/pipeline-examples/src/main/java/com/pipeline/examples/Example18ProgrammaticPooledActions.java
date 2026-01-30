package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.core.PipelineProvider;
import com.pipeline.examples.adapters.PooledScratchNormalizeAction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class Example18ProgrammaticPooledActions {
  private Example18ProgrammaticPooledActions() {}

  public static void run() throws Exception {
    PipelineProvider<String> provider = PipelineProvider.pooled(
        () -> new Pipeline<String>("programmatic_pooled", true)
            .addAction("normalize_whitespace", new PooledScratchNormalizeAction()),
        64
    );

    int runCount = 30;
    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      List<Future<String>> futures = new ArrayList<>();
      for (int index = 0; index < runCount; index++) {
        String input = "  hello   world  " + index;
        futures.add(executor.submit(new PipelineRunTask(provider, input)));
      }

      for (Future<String> future : futures) {
        System.out.println("[ex18] => " + future.get());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static final class PipelineRunTask implements Callable<String> {
    private final PipelineProvider<String> provider;
    private final String input;

    private PipelineRunTask(PipelineProvider<String> provider, String input) {
      this.provider = provider;
      this.input = input;
    }

    @Override
    public String call() {
      return provider.run(input).context();
    }
  }
}
