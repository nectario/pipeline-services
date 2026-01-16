using System;

namespace PipelineServices.Examples;

public static class Program
{
    public static int Main(string[] args)
    {
        if (args == null || args.Length == 0)
        {
            PrintUsage();
            return 2;
        }

        string command = args[0];
        if (string.Equals(command, "example01_text_clean", StringComparison.Ordinal))
        {
            Example01TextClean.Run();
            return 0;
        }
        if (string.Equals(command, "example02_json_loader", StringComparison.Ordinal))
        {
            Example02JsonLoader.Run();
            return 0;
        }
        if (string.Equals(command, "example03_runtime_pipeline", StringComparison.Ordinal))
        {
            Example03RuntimePipeline.Run();
            return 0;
        }
        if (string.Equals(command, "example04_json_loader_remote_get", StringComparison.Ordinal))
        {
            Example04JsonLoaderRemoteGet.Run();
            return 0;
        }
        if (string.Equals(command, "example05_metrics_post_action", StringComparison.Ordinal))
        {
            Example05MetricsPostAction.Run();
            return 0;
        }
        if (string.Equals(command, "benchmark01_pipeline_run", StringComparison.Ordinal))
        {
            Benchmark01PipelineRun.Run();
            return 0;
        }

        Console.Error.WriteLine("Unknown command: " + command);
        PrintUsage();
        return 2;
    }

    private static void PrintUsage()
    {
        Console.WriteLine("Usage: dotnet run --project src/CSharp/pipeline_services_examples -- <command>");
        Console.WriteLine("Commands:");
        Console.WriteLine("  example01_text_clean");
        Console.WriteLine("  example02_json_loader");
        Console.WriteLine("  example03_runtime_pipeline");
        Console.WriteLine("  example04_json_loader_remote_get");
        Console.WriteLine("  example05_metrics_post_action");
        Console.WriteLine("  benchmark01_pipeline_run");
    }
}

