package com.pipeline.examples.bloomberg;

public final class BloombergSession implements AutoCloseable {
  private final String env;
  private boolean open;

  public BloombergSession(String env) { this.env = env; }
  public boolean isOpen() { return open; }
  public void open() { this.open = true; }
  public void close() { this.open = false; }

  @Override public String toString() { return "BloombergSession(" + env + ", open=" + open + ")"; }
}
