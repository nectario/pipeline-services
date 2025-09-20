package com.pipeline.core;

@FunctionalInterface
public interface ThrowingFn<I, O> {
    O apply(I in) throws Exception;
}

