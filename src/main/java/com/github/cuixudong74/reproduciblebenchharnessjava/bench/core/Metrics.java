package com.github.cuixudong74.reproduciblebenchharnessjava.bench.core;

/**
 * 一次运行的可比对统计指标。所有字段均由确定性数据推导，
 * 不包含运行时刻、进程标识等易变值。
 *
 * @param successCount        成功样本数
 * @param failureCount        失败样本数（含异常与超时）
 * @param throughputPerSecond 逻辑吞吐：成功样本数 / 成功样本逻辑耗时总和（秒）
 * @param meanCostNanos       成功样本逻辑耗时均值
 * @param p50CostNanos        成功样本逻辑耗时 p50
 * @param p90CostNanos        成功样本逻辑耗时 p90
 * @param p99CostNanos        成功样本逻辑耗时 p99
 */
public record Metrics(
        long successCount,
        long failureCount,
        double throughputPerSecond,
        double meanCostNanos,
        double p50CostNanos,
        double p90CostNanos,
        double p99CostNanos) {
}
