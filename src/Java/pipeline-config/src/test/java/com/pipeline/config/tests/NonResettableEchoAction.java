package com.pipeline.config.tests;

import java.util.function.UnaryOperator;

public final class NonResettableEchoAction implements UnaryOperator<String> {
    public NonResettableEchoAction() {}

    @Override
    public String apply(String input) {
        return input;
    }
}

