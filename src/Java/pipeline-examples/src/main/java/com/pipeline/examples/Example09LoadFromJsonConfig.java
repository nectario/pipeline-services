package com.pipeline.examples;

import com.pipeline.config.PipelineJsonLoader;
import com.pipeline.core.Pipeline;

import java.io.InputStream;

public final class Example09LoadFromJsonConfig {
  private Example09LoadFromJsonConfig() {}

  public static void run() throws Exception {
    try (InputStream in = Example09LoadFromJsonConfig.class.getResourceAsStream("/pipelines/clean_text.json")) {
      if (in == null) throw new IllegalStateException("Missing resource: /pipelines/clean_text.json");
      Pipeline<String> p = PipelineJsonLoader.loadUnary(in);
      String out = p.run("  Hello   <b>World</b>  ");
      System.out.println("[ex09] => " + out);
    }
  }
}

