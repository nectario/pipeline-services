package com.pipeline.core;

@FunctionalInterface
public interface ThrowingPred<T> {
    boolean test(T t) throws Exception;
}
