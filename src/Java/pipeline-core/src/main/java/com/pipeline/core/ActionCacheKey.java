package com.pipeline.core;

import java.util.Objects;

/**
 * Stable key used for caching/pooling actions across pipeline instances.
 *
 * <p>Key is based on the pipeline name, phase, index, and (optional) user label.
 * Including the index avoids collisions when the same label is reused.
 */
public record ActionCacheKey(String pipelineName, StepPhase phase, int index, String actionName) {
    public ActionCacheKey {
        pipelineName = Objects.requireNonNull(pipelineName, "pipelineName");
        phase = Objects.requireNonNull(phase, "phase");
        if (index < 0) throw new IllegalArgumentException("index must be >= 0");
        actionName = (actionName == null) ? "" : actionName;
    }

    @Override
    public String toString() {
        if (actionName.isBlank()) {
            return pipelineName + ":" + phase.name().toLowerCase() + ":" + index;
        }
        return pipelineName + ":" + phase.name().toLowerCase() + ":" + index + ":" + actionName;
    }
}
