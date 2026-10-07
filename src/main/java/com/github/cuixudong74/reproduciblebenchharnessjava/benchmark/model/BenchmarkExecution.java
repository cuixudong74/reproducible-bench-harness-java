package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model;

/** Result of a normal read-only benchmark run and its automatic baseline comparison. */
public record BenchmarkExecution(
        BenchmarkResult result,
        RegressionReport regressionReport) {

    public BenchmarkExecution {
        if (result == null || regressionReport == null) {
            throw new IllegalArgumentException("result and regressionReport are required");
        }
    }

    public boolean hasRegression() {
        return regressionReport.hasRegression();
    }
}
