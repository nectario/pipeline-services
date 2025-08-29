package com.ps.prompt;

import com.ps.core.ThrowingFn;

import java.util.ArrayList;
import java.util.List;

public final class Prompt {
    private Prompt() {}

    public static <I, O> PromptBuilder<I, O> step(Class<I> in, Class<O> out) {
        return new PromptBuilder<>();
    }

    public static final class PromptBuilder<I, O> {
        private String name = "promptStep";
        private String goal = "";
        private final List<String> rules = new ArrayList<>();
        private final List<Example<I, O>> examples = new ArrayList<>();
        private final List<String> properties = new ArrayList<>();
        private int p50Micros = 0;

        public PromptBuilder<I, O> name(String stepName) { this.name = stepName; return this; }
        public PromptBuilder<I, O> goal(String text) { this.goal = text; return this; }
        public PromptBuilder<I, O> rules(String... lines) { if (lines != null) for (String s : lines) rules.add(s); return this; }
        public PromptBuilder<I, O> example(I input, O expected) { examples.add(new Example<>(input, expected)); return this; }
        public PromptBuilder<I, O> property(String assertion) { properties.add(assertion); return this; }
        public PromptBuilder<I, O> p50Micros(int budget) { this.p50Micros = budget; return this; }

        public ThrowingFn<I, O> build() {
            // Placeholder; build-time codegen should replace this via psGenerate
            return in -> { throw new UnsupportedOperationException("Prompt-generated code not available for step '" + name + "'"); };
        }

        record Example<I, O>(I in, O out) {}
    }
}

