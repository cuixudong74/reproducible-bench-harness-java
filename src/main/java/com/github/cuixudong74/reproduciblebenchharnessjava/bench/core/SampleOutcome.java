package com.github.cuixudong74.reproduciblebenchharnessjava.bench.core;

/**
 * 单个测量样本的最终结局。聚合时按 index 排序，与执行完成顺序无关。
 */
public record SampleOutcome(
        long index,
        boolean success,
        String outputDigest,
        long costNanos,
        String failureKind) {

    public static SampleOutcome ok(long index, String outputDigest, long costNanos) {
        return new SampleOutcome(index, true, outputDigest, costNanos, null);
    }

    public static SampleOutcome failed(long index, String failureKind) {
        return new SampleOutcome(index, false, null, 0L, failureKind);
    }
}
