package com.pipeline.core;

import java.util.function.Supplier;

@FunctionalInterface
public interface StepActionFactory<C> extends Supplier<StepAction<C>> {}

