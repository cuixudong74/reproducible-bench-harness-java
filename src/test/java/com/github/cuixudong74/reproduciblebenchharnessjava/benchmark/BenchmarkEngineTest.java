package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark;

import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.config.BenchmarkConfiguration;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.contract.BenchmarkWorkload;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.contract.ExecutionClock;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.engine.BenchmarkEngine;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.BenchmarkResult;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkEngineTest {
    private final BenchmarkEngine engine = new BenchmarkEngine();

    @Test
    void sameSeedProducesIdenticalSamplesAndStatistics() {
        DeterministicTestWorkload workload = new DeterministicTestWorkload();
        BenchmarkConfiguration configuration = configuration(1, 123L);

        BenchmarkResult first = engine.run(workload, configuration);
        BenchmarkResult second = engine.run(workload, configuration);

        assertThat(second).usingRecursiveComparison().isEqualTo(first);
        assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(first.samples()).hasSize(12);
    }

    @Test
    void serialAndConcurrentRunsHaveSameReproducibleConclusion() {
        BenchmarkConfiguration serial = configuration(1, 99L);
        BenchmarkConfiguration concurrent = configuration(4, 99L);
        DeterministicTestWorkload workload = new DeterministicTestWorkload();

        BenchmarkResult serialResult = engine.run(workload, serial);
        BenchmarkResult concurrentResult = engine.run(workload, concurrent);

        assertThat(concurrentResult.samples()).isEqualTo(serialResult.samples());
        assertThat(concurrentResult.fingerprint()).isEqualTo(serialResult.fingerprint());
        assertThat(concurrentResult.throughputSamplesPerSecond())
                .isEqualTo(serialResult.throughputSamplesPerSecond());
        assertThat(concurrentResult.latency()).usingRecursiveComparison()
                .isEqualTo(serialResult.latency());
    }

    @Test
    void exceptionsAndTimeoutsAreCountedWithoutDroppingSamples() {
        BenchmarkResult result = engine.run(
                new DeterministicTestWorkload(), configuration(3, 7L));

        assertThat(result.totalSamples()).isEqualTo(12);
        assertThat(result.successCount() + result.failureCount()).isEqualTo(12);
        assertThat(result.samples()).filteredOn(sample -> !sample.success()).hasSize(6);
        assertThat(result.samples()).filteredOn(sample ->
                "java.lang.ArithmeticException".equals(sample.exceptionType())).hasSize(3);
        assertThat(result.samples()).filteredOn(sample ->
                "java.util.concurrent.TimeoutException".equals(sample.exceptionType())).hasSize(3);
    }

    @Test
    void repeatedRunsDoNotLeaveWorkerThreads() {
        long baselineWorkers = benchmarkWorkerThreads();
        for (int run = 0; run < 10; run++) {
            engine.run(new DeterministicTestWorkload(), configuration(4, 42L + run));
        }
        System.gc();
        assertThat(benchmarkWorkerThreads()).isEqualTo(baselineWorkers);
    }

    private static BenchmarkConfiguration configuration(int concurrency, long seed) {
        return new BenchmarkConfiguration(
                "deterministic-test",
                10,
                concurrency,
                3,
                12,
                seed,
                TimeUnit.MICROSECONDS.toNanos(20L));
    }

    private static long benchmarkWorkerThreads() {
        return java.util.Arrays.stream(ManagementFactory.getThreadMXBean().getAllThreadIds())
                .mapToObj(ManagementFactory.getThreadMXBean()::getThreadInfo)
                .filter(java.util.Objects::nonNull)
                .filter(info -> info.getThreadName().startsWith("benchmark-worker-"))
                .count();
    }

    private static final class DeterministicTestWorkload
            implements BenchmarkWorkload<Long, String> {
        @Override
        public String id() {
            return "deterministic-test";
        }

        @Override
        public Long createInput(long seed, int sampleIndex, int inputSize) {
            return seed * 31L + sampleIndex;
        }

        @Override
        public String execute(Long input, ExecutionClock clock) {
            long latency = Math.floorMod(input * 17L + 11L, 25L) + 1L;
            clock.advanceNanos(TimeUnit.MICROSECONDS.toNanos(latency));
            long normalized = Math.floorMod(input, 6L);
            if (normalized == 1L) {
                throw new ArithmeticException("environment independent deterministic failure");
            }
            if (normalized == 2L) {
                clock.advanceNanos(TimeUnit.MICROSECONDS.toNanos(200L));
            }
            return "output-" + input;
        }
    }
}
