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
    // TODO: scan JSON specs + Prompt.step(...) markers and emit Java sources into 'out'
    // For now, no-op or generate a placeholder to prove wiring works.
    // Minimal placeholder file to verify wiring
    Path placeholder = out.resolve("Placeholder.java");
    String code = "// generated placeholder\n" +
            "package com.ps.prompt.generated;\n" +
            "public final class Placeholder { public static final String OK = \"ok\"; }\n";
    Files.createDirectories(placeholder.getParent());
    Files.writeString(placeholder, code);
  }
}

