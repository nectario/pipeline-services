package com.pipeline.remote.http;

import com.pipeline.core.Pipeline;
import com.pipeline.core.PipelineResult;
import com.pipeline.metrics.Metrics;
import com.pipeline.metrics.SimpleMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class HttpStepTest {
    @BeforeEach
    void setup() {
        Metrics.setRecorder(new SimpleMetricsRecorder());
    }

    @Test
    void timeoutShortCircuitsWhenTrue() {
        HttpStep.RemoteSpec<String> spec = new HttpStep.RemoteSpec<>();
        // unroutable IP to force timeout
        spec.endpoint = "http://10.255.255.1/echo";
        spec.timeoutMillis = 100;
        spec.retries = 0;
        spec.toJson = s -> s;
        spec.fromJson = (ctx, body) -> body;

        var p = Pipeline.build("remote_timeout", true, HttpStep.jsonGet(spec));
        PipelineResult<String> r = p.execute("q=1");
        assertTrue(r.shortCircuited());
        assertTrue(r.hasErrors());

        MeterRegistry reg = Metrics.recorder().registry();
        var c = reg.find("ps.pipeline.remote_timeout.step.s0.errors").counter();
        assertNotNull(c);
        assertTrue(c.count() >= 1.0);
    }
}
