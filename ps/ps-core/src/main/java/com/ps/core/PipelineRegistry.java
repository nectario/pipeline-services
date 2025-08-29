package com.ps.core;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PipelineRegistry {
    private final Map<String, Pipeline<String>> map = new ConcurrentHashMap<>();

    public void register(String key, Pipeline<String> pipeline) { map.put(key, pipeline); }

    public Optional<Pipeline<String>> lookup(String key) { return Optional.ofNullable(map.get(key)); }

    public Map<String, Pipeline<String>> asMap() { return Collections.unmodifiableMap(map); }

    public int size() { return map.size(); }
}

