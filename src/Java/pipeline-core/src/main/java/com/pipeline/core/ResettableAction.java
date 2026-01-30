package com.pipeline.core;

/**
 * Contract for actions that can be safely reused across runs.
 *
 * <p>Used by lifecycle "pooled" wrappers (JSON loader and programmatic) to ensure per-run state does not leak.
 */
public interface ResettableAction {
    void reset();
}

