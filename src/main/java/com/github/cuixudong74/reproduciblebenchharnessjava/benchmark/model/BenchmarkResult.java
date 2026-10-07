package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model;

import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.config.BenchmarkConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Persistable, comparable result of one benchmark run.
 *
 * <p>The stable result schema never stores wall-clock run time, host name, process id,
 * thread names, exception messages, or stack traces.
 */
public record BenchmarkResult(
        int schemaVersion,
        String timeSource,
        BenchmarkConfiguration configuration,
        int totalSamples,
        int successCount,
        int failureCount,
        double throughputSamplesPerSecond,
        long throughputDenominatorNanos,
        LatencyMetrics latency,
        List<SampleOutcome> samples,
        String fingerprint) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String DETERMINISTIC_LOGICAL_TIME_SOURCE =
            "deterministic-logical-clock:java.lang.Long.advanceNanos";

    public BenchmarkResult {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported schema version");
        }
        if (timeSource == null || timeSource.isBlank()) {
            throw new IllegalArgumentException("timeSource must not be blank");
        }
        if (configuration == null) {
            throw new IllegalArgumentException("configuration must not be null");
        }
        if (totalSamples <= 0) {
            throw new IllegalArgumentException("totalSamples must be positive");
        }
        if (successCount < 0 || failureCount < 0 || successCount + failureCount != totalSamples) {
            throw new IllegalArgumentException("success and failure counts must cover all samples");
        }
        if (throughputDenominatorNanos <= 0L || Double.isNaN(throughputSamplesPerSecond)
                || throughputSamplesPerSecond <= 0.0d) {
            throw new IllegalArgumentException("invalid throughput values");
        }
        if (latency == null) {
            throw new IllegalArgumentException("latency must not be null");
        }
        List<SampleOutcome> sorted = new ArrayList<>(samples == null ? List.of() : samples);
        sorted.sort(Comparator.comparingInt(SampleOutcome::sampleIndex));
        if (sorted.size() != totalSamples) {
            throw new IllegalArgumentException("samples size must match totalSamples");
        }
        for (int index = 0; index < sorted.size(); index++) {
            if (sorted.get(index).sampleIndex() != index) {
                throw new IllegalArgumentException("sample indexes must be consecutive");
            }
        }
        samples = List.copyOf(sorted);
        fingerprint = fingerprint == null ? "" : fingerprint;
    }
}
