package com.pipeline.core;

@FunctionalInterface
public interface ThrowingBiFn<A, B, R> {
  R apply(A a, B b) throws Exception;
}

