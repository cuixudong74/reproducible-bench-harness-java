package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.contract;

/**
 * Uniform contract used by every business workload under test.
 *
 * @param <I> deterministic input type
 * @param <O> deterministic output type
 */
public interface BenchmarkWorkload<I, O> {
    String id();

    I createInput(long seed, int sampleIndex, int inputSize);

    O execute(I input, ExecutionClock clock) throws Exception;
}
