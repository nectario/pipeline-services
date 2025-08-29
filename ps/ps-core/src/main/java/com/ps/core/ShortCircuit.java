package com.ps.core;

public final class ShortCircuit {
    private ShortCircuit() {}

    static final class Signal extends RuntimeException {
        final Object value;
        Signal(Object v) { super(null, null, false, false); this.value = v; }
    }

    @SuppressWarnings("unchecked")
    public static <T> T now(T finalValue) {
        throw new Signal(finalValue);
    }
}

