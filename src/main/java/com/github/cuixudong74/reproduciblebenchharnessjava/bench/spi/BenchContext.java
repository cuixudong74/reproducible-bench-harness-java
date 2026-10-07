package com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi;

/**
 * 传递给被测对象的运行上下文，只包含与可复现性相关的确定性信息。
 */
public record BenchContext(long seed, int inputSize, int warmupIterations, int measurementIterations) {
}
