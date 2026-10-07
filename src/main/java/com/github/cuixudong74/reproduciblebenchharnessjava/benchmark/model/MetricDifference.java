package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model;

/**
 * One named metric or deterministic-result difference between current and baseline runs.
 *
 * @param metricName stable dotted metric name
 * @param baselineValue baseline value rendered in JSON-compatible text
 * @param currentValue current value rendered in JSON-compatible text
 * @param absoluteDelta current minus baseline, or an explanatory value for non-numeric metrics
 * @param regression true when this difference represents an unacceptable regression
 */
public record MetricDifference(
        String metricName,
        String baselineValue,
        String currentValue,
        String absoluteDelta,
        boolean regression) {

    public MetricDifference {
        if (metricName == null || metricName.isBlank()) {
            throw new IllegalArgumentException("metricName must not be blank");
        }
        baselineValue = baselineValue == null ? "" : baselineValue;
        currentValue = currentValue == null ? "" : currentValue;
        absoluteDelta = absoluteDelta == null ? "" : absoluteDelta;
    }
}
