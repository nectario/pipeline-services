package com.pipeline.core.metrics;

public final class NoopMetrics implements Metrics {
  public static final NoopMetrics INSTANCE = new NoopMetrics();
  private static final RunScope NOOP_SCOPE = new RunScope() {
    public void onStepStart(int index, String label) {}
    public void onStepEnd(int index, String label, long elapsedNanos, boolean success) {}
    public void onStepError(int index, String label, Throwable error) {}
    public void onJump(String fromLabel, String toLabel, long delayMillis) {}
    public void onPipelineEnd(boolean success, long elapsedNanos, Throwable error) {}
  };
  private NoopMetrics() {}
  @Override public RunScope onPipelineStart(String pipelineName, String runId, String startLabel) { return NOOP_SCOPE; }
}
