package com.pipeline.examples;

import com.pipeline.config.PipelineJsonLoader;
import com.pipeline.core.ActionRegistry;
import com.pipeline.core.Pipeline;
import com.pipeline.examples.steps.TextSteps;
import com.pipeline.generated.PromptGeneratedActions;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Example16PromptCodegenPipeline {
  private Example16PromptCodegenPipeline() {}

  public static void main(String[] args) throws Exception {
    run();
  }

  public static void run() throws Exception {
    Path pipelineFile = findPipelineFile("normalize_name.json");

    ActionRegistry<String> registry = new ActionRegistry<>();
    registry.registerUnary("strip", TextSteps::strip);
    PromptGeneratedActions.register(registry);

    Pipeline<String> pipeline = PipelineJsonLoader.loadUnary(pipelineFile, registry);
    String outputValue = pipeline.run("  john   SMITH ");
    System.out.println("output=" + outputValue);
  }

  private static Path findPipelineFile(String pipelineFileName) {
    Path currentDir = Path.of("").toAbsolutePath();
    while (true) {
      Path candidatePath = currentDir.resolve("pipelines").resolve(pipelineFileName);
      if (Files.exists(candidatePath)) return candidatePath;
      Path parentDir = currentDir.getParent();
      if (parentDir == null || parentDir.equals(currentDir)) break;
      currentDir = parentDir;
    }
    throw new IllegalStateException("Could not locate pipelines directory from current working directory");
  }
}
