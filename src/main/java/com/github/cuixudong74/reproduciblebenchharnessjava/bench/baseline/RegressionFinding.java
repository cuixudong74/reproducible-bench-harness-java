package com.github.cuixudong74.reproduciblebenchharnessjava.bench.baseline;

/**
 * 一项回归发现：指标名、基线值、当前值、相对变化幅度（百分比）。
 */
public record RegressionFinding(
        String metric,
        double baselineValue,
        double currentValue,
        double changePercent) {
}
