package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.config;

/** Allowed difference before a metric movement is marked as a regression. */
public record RegressionTolerance(
        double relativeTolerance,
        long absoluteLatencyNanos,
        double absoluteThroughput) {
}
