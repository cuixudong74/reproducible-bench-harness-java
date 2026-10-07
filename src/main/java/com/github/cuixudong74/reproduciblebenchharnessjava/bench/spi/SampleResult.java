package com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi;

/**
 * 被测对象返回的样本处理结果。
 *
 * @param outputDigest 处理输出的摘要（由实现计算，需确定性），用于跨运行比对处理结果
 * @param costNanos    本次处理的逻辑耗时（纳秒）。这是可复现的"逻辑时钟"读数，
 *                     由实现根据输入确定性地给出，作为可比对延迟指标的时间来源
 */
public record SampleResult(String outputDigest, long costNanos) {
}
