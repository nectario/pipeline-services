using System;
using System.IO;

using PipelineServices.Config;
using PipelineServices.Core;
using PipelineServices.Generated;

namespace PipelineServices.Examples;

public static class Example07PromptCodegenPipeline
{
    public static void Run()
    {
        PipelineRegistry<string> registry = new PipelineRegistry<string>();
        registry.RegisterUnary("strip", TextActions.Strip);
        PromptGeneratedActions.Register(registry);

        string pipelineFilePath = FindPipelineFile("normalize_name.json");
        PipelineJsonLoader loader = new PipelineJsonLoader();
        Pipeline<string> pipeline = loader.LoadFile(pipelineFilePath, registry);
        string outputValue = pipeline.Run("  john   SMITH ");
        Console.WriteLine("output=" + outputValue);
    }

    private static string FindPipelineFile(string pipelineFileName)
    {
        DirectoryInfo? currentDirectory = new DirectoryInfo(Directory.GetCurrentDirectory());
        while (currentDirectory != null)
        {
            string candidatePath = Path.Combine(currentDirectory.FullName, "pipelines", pipelineFileName);
            if (File.Exists(candidatePath))
            {
                return candidatePath;
            }
            currentDirectory = currentDirectory.Parent;
        }

        throw new InvalidOperationException("Could not locate pipelines directory from current working directory");
    }
}
