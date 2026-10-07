package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model;

/**
 * Immutable result for exactly one measured sample.
 *
 * <p>Exception messages and stack traces are intentionally omitted: they can contain file
 * paths, JDK wording, or other environment-dependent values. The exception's concrete type
 * is enough to distinguish deterministic failure classes without making the result volatile.
 */
public record SampleOutcome(
        int sampleIndex,
        boolean success,
        boolean timeout,
        long latencyNanos,
        String outputHash,
        String exceptionType) {

    public SampleOutcome {
        if (sampleIndex < 0) {
            throw new IllegalArgumentException("sampleIndex must not be negative");
        }
        if (latencyNanos < 0L) {
            throw new IllegalArgumentException("latencyNanos must not be negative");
        }
        outputHash = outputHash == null ? "" : outputHash;
        exceptionType = exceptionType == null ? "" : exceptionType;
        if (success && !exceptionType.isEmpty()) {
            throw new IllegalArgumentException("successful samples cannot carry an exception");
        }
        if (!success && exceptionType.isEmpty()) {
            throw new IllegalArgumentException("failed samples must carry an exception type");
        }
    }

    public static SampleOutcome success(int sampleIndex, long latencyNanos, String outputHash) {
        return new SampleOutcome(sampleIndex, true, false, latencyNanos, outputHash, "");
    }

    public static SampleOutcome failure(
            int sampleIndex, long latencyNanos, boolean timeout, String exceptionType) {
        return new SampleOutcome(sampleIndex, false, timeout, latencyNanos, "", exceptionType);
    }
}
