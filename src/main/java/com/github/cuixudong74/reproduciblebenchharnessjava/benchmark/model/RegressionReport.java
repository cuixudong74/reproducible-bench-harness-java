package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model;

import java.util.List;

/** Baseline comparison result for one benchmark execution. */
public record RegressionReport(
        RegressionStatus status,
        String baselineFingerprint,
        String currentFingerprint,
        List<MetricDifference> differences) {

    public RegressionReport {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        baselineFingerprint = baselineFingerprint == null ? "" : baselineFingerprint;
        currentFingerprint = currentFingerprint == null ? "" : currentFingerprint;
        differences = differences == null ? List.of() : List.copyOf(differences);
        if (status == RegressionStatus.REGRESSED && differences.stream().noneMatch(MetricDifference::regression)) {
            throw new IllegalArgumentException("REGRESSED report must contain a regression");
        }
    }

    public boolean hasRegression() {
        return differences.stream().anyMatch(MetricDifference::regression);
    }
}
