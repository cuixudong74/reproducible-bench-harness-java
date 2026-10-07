package com.github.cuixudong74.reproduciblebenchharnessjava.bench.core;

/**
 * 一次基准运行的全部可配置项。相同配置 + 相同 seed 必须产生完全一致的可比对结果。
 *
 * @param workloadName          被测工作负载名称
 * @param inputSize             每个样本输入的字节数
 * @param concurrency           并发度，1 表示串行
 * @param warmupIterations      预热样本数（不计入统计）
 * @param measurementIterations 测量样本数（计入统计）
 * @param seed                  随机种子
 * @param sampleTimeoutMillis   单样本超时（毫秒），超时计为失败
 * @param tolerancePercent      回归判定容差（百分比），各指标相对基线的劣化幅度超过该值即标记回归
 */
public record BenchConfig(
        String workloadName,
        int inputSize,
        int concurrency,
        int warmupIterations,
        int measurementIterations,
        long seed,
        long sampleTimeoutMillis,
        double tolerancePercent) {

    public BenchConfig {
        if (workloadName == null || workloadName.isBlank()) {
            throw new IllegalArgumentException("workloadName must not be blank");
        }
        if (inputSize <= 0) {
            throw new IllegalArgumentException("inputSize must be positive");
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException("concurrency must be positive");
        }
        if (warmupIterations < 0 || measurementIterations <= 0) {
            throw new IllegalArgumentException("iterations invalid: warmup=" + warmupIterations
                    + ", measurement=" + measurementIterations);
        }
        if (sampleTimeoutMillis <= 0) {
            throw new IllegalArgumentException("sampleTimeoutMillis must be positive");
        }
        if (tolerancePercent < 0) {
            throw new IllegalArgumentException("tolerancePercent must be >= 0");
        }
    }

    /** 提供合理默认值的构造入口。 */
    public static BenchConfig ofDefaults(String workloadName) {
        return new BenchConfig(workloadName, 1024, 1, 10, 100, 42L, 10_000L, 5.0);
    }
}
