package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.config;

import java.util.Arrays;

/**
 * Fixed input and execution configuration for one benchmark run.
 *
 * @param workloadName        stable workload identifier; must not contain run timestamps
 * @param inputSize           workload-specific size passed to every deterministic input
 * @param concurrency         number of worker threads; one means strictly serial execution
 * @param warmupIterations    number of untimed warm-up samples
 * @param measurementIterations number of samples included in the result
 * @param seed                deterministic seed shared by input generation and clocks
 * @param timeoutNanos        logical per-sample timeout
 * @param quantiles           sorted quantiles in the inclusive range [0, 1]
 * @param tolerance           allowed metric movement during baseline comparison
 */
public record BenchmarkConfiguration(
        String workloadName,
        int inputSize,
        int concurrency,
        int warmupIterations,
        int measurementIterations,
        long seed,
        long timeoutNanos,
        double[] quantiles,
        RegressionTolerance tolerance) {

    public static final double[] DEFAULT_QUANTILES = {0.50d, 0.90d, 0.95d, 0.99d};
    public static final RegressionTolerance DEFAULT_TOLERANCE =
            new RegressionTolerance(0.05d, 1_000L, 0.0d);

    public BenchmarkConfiguration {
        if (workloadName == null || workloadName.isBlank()) {
            throw new IllegalArgumentException("workloadName must not be blank");
        }
        if (inputSize <= 0) {
            throw new IllegalArgumentException("inputSize must be positive");
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException("concurrency must be positive");
        }
        if (warmupIterations < 0) {
            throw new IllegalArgumentException("warmupIterations must not be negative");
        }
        if (measurementIterations <= 0) {
            throw new IllegalArgumentException("measurementIterations must be positive");
        }
        if (timeoutNanos <= 0) {
            throw new IllegalArgumentException("timeoutNanos must be positive");
        }
        if (tolerance == null) {
            throw new IllegalArgumentException("tolerance must not be null");
        }
        quantiles = validatedQuantiles(quantiles);
    }

    public BenchmarkConfiguration(
            String workloadName,
            int inputSize,
            int concurrency,
            int warmupIterations,
            int measurementIterations,
            long seed,
            long timeoutNanos) {
        this(workloadName, inputSize, concurrency, warmupIterations,
                measurementIterations, seed, timeoutNanos,
                DEFAULT_QUANTILES.clone(), DEFAULT_TOLERANCE);
    }

    @Override
    public double[] quantiles() {
        return quantiles.clone();
    }

    public int totalIterations() {
        return warmupIterations + measurementIterations;
    }

    private static double[] validatedQuantiles(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("at least one quantile is required");
        }
        double[] copy = values.clone();
        double previous = Double.NaN;
        for (double value : copy) {
            if (Double.isNaN(value) || value < 0.0d || value > 1.0d) {
                throw new IllegalArgumentException("quantiles must be in [0, 1]");
            }
            if (!Double.isNaN(previous) && value < previous) {
                throw new IllegalArgumentException("quantiles must be sorted");
            }
            previous = value;
        }
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BenchmarkConfiguration that
                && inputSize == that.inputSize
                && concurrency == that.concurrency
                && warmupIterations == that.warmupIterations
                && measurementIterations == that.measurementIterations
                && seed == that.seed
                && timeoutNanos == that.timeoutNanos
                && workloadName.equals(that.workloadName)
                && tolerance.equals(that.tolerance)
                && Arrays.equals(quantiles, that.quantiles);
    }

    @Override
    public int hashCode() {
        int result = workloadName.hashCode();
        result = 31 * result + inputSize;
        result = 31 * result + concurrency;
        result = 31 * result + warmupIterations;
        result = 31 * result + measurementIterations;
        result = 31 * result + Long.hashCode(seed);
        result = 31 * result + Long.hashCode(timeoutNanos);
        result = 31 * result + Arrays.hashCode(quantiles);
        result = 31 * result + tolerance.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "BenchmarkConfiguration[workloadName=" + workloadName
                + ", inputSize=" + inputSize
                + ", concurrency=" + concurrency
                + ", warmupIterations=" + warmupIterations
                + ", measurementIterations=" + measurementIterations
                + ", seed=" + seed
                + ", timeoutNanos=" + timeoutNanos
                + ", quantiles=" + Arrays.toString(quantiles)
                + ", tolerance=" + tolerance + "]";
    }
}
