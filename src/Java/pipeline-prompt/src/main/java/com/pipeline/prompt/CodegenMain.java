package com.pipeline.prompt;

import java.nio.file.*;

public final class CodegenMain {
  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: CodegenMain <pipelinesDir> <outDir>");
      return;
    }
    Path in = Paths.get(args[0]);
    Path out = Paths.get(args[1]);
    Files.createDirectories(out);
    // v0.1: no-op placeholder. Future versions will scan JSON and emit sources.
  }
}
