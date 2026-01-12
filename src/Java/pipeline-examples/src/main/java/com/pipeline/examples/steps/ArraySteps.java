package com.pipeline.examples.steps;

public final class ArraySteps {
  private ArraySteps() {}
  public record Stats(int count, long sum, double avg, int max) {}
  public static int[] clipNegatives(int[] a) {
    int[] out = a.clone();
    for (int i=0;i<out.length;i++) if (out[i] < 0) out[i] = 0;
    return out;
  }
  public static Stats stats(int[] a) {
    if (a.length == 0) return new Stats(0, 0, 0, 0);
    long sum = 0; int max = Integer.MIN_VALUE;
    for (int v : a) { sum += v; if (v > max) max = v; }
    return new Stats(a.length, sum, sum / (double)a.length, max);
  }
}
