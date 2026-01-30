package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.examples.steps.TextSteps;
import com.pipeline.remote.http.HttpStep;

public final class Example15MixedLocalRemote {
  private Example15MixedLocalRemote() {}

  public static void main(String[] args) {
    String fixtureEndpoint = "http://127.0.0.1:8765/echo";

    HttpStep.RemoteSpec<String> remoteSpec = new HttpStep.RemoteSpec<>();
    remoteSpec.endpoint = fixtureEndpoint;
    remoteSpec.timeoutMillis = 1000;
    remoteSpec.retries = 0;
    remoteSpec.toJson = value -> value;
    remoteSpec.fromJson = (ctx, body) -> body;

    Pipeline<String> pipeline = new Pipeline<String>("example15_mixed_local_remote", true)
        .addAction(TextSteps::strip)
        .addAction(TextSteps::normalizeWhitespace)
        .addAction("remote_echo", HttpStep.jsonPost(remoteSpec))
        .addAction(value -> value.toLowerCase())
        .addAction(value -> value + "|");

    String outputValue = pipeline.run("  Hello   Remote  ").context();
    System.out.println("output=" + outputValue);
  }
}
