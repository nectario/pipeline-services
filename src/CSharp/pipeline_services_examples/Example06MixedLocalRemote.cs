using System;

using PipelineServices.Core;
using PipelineServices.Remote;

namespace PipelineServices.Examples;

public static class Example06MixedLocalRemote
{
    public static void Run()
    {
        string fixtureEndpoint = "http://127.0.0.1:8765/echo";

        HttpStep.RemoteSpec<string> remoteSpec = new HttpStep.RemoteSpec<string>();
        remoteSpec.Endpoint = fixtureEndpoint;
        remoteSpec.TimeoutMillis = 1000;
        remoteSpec.Retries = 0;
        remoteSpec.ToJson = value => value;
        remoteSpec.FromJson = (contextValue, responseBody) => responseBody;

        Pipeline<string> pipeline = new Pipeline<string>("example06_mixed_local_remote", shortCircuitOnException: true);
        pipeline.AddAction(TextActions.Strip);
        pipeline.AddAction(TextActions.NormalizeWhitespace);
        pipeline.AddAction("remote_echo", HttpStep.JsonPost(remoteSpec));
        pipeline.AddAction(TextActions.ToLower);
        pipeline.AddAction(TextActions.AppendMarker);

        PipelineResult<string> result = pipeline.Run("  Hello   Remote  ");
        Console.WriteLine("output=" + result.Context);
    }
}
