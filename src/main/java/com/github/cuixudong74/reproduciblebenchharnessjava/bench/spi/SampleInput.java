package com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi;

/**
 * 一个样本的输入。payload 由框架根据 (seed, index) 确定性生成，
 * 与样本的调度顺序、执行线程无关。
 */
public record SampleInput(long index, byte[] payload) {
}
