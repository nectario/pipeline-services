package com.pipeline.core;

@FunctionalInterface
public interface ThrowingBiConsumer<A, B> {
  void accept(A a, B b) throws Exception;
}

