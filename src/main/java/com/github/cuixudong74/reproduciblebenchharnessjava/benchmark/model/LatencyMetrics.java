package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model;

import java.util.Map;
import java.util.TreeMap;

/** Fixed latency summary calculated from every completed measured attempt. */
public record LatencyMetrics(
        long count,
        long minNanos,
        long maxNanos,
        long sumNanos,
        double averageNanos,
        Map<Double, Long> quantileNanos) {

    public LatencyMetrics {
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive");
        }
        if (minNanos < 0L || maxNanos < minNanos || sumNanos < 0L) {
            throw new IllegalArgumentException("invalid latency bounds");
        }
        if (quantileNanos == null || quantileNanos.isEmpty()) {
            throw new IllegalArgumentException("quantileNanos must not be empty");
        }
        quantileNanos = Map.copyOf(new TreeMap<>(quantileNanos));
    }
}
