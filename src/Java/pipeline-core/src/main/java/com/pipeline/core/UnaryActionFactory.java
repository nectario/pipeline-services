package com.pipeline.core;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@FunctionalInterface
public interface UnaryActionFactory<C> extends Supplier<UnaryOperator<C>> {}

