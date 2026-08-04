package com.pipeline.remote.http;

import com.pipeline.core.Pipeline;
import com.pipeline.core.PipelineResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HttpStepTest {

  @Test
  void timeoutIsCapturedAndShortCircuitsWhenEnabled() {
    HttpStep.RemoteSpec<String> spec = new HttpStep.RemoteSpec<>();
    spec.endpoint = "http://10.255.255.1/echo";
    spec.timeoutMillis = 100;
    spec.retries = 0;
    spec.toJson = value -> value;
    spec.fromJson = (context, body) -> body;

    Pipeline<String> pipeline = Pipeline.build(
        "remote_timeout",
        true,
        HttpStep.jsonGet(spec));

    PipelineResult<String> result = pipeline.runDetailed("q=1");

    assertEquals("q=1", result.context());
    assertTrue(result.shortCircuited());
    assertTrue(result.hasErrors());
  }
}
