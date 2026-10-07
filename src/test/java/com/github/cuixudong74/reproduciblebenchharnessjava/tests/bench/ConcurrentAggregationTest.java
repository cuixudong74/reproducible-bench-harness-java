package com.github.cuixudong74.reproduciblebenchharnessjava.tests.bench;

import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.BenchConfig;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.BenchmarkRunner;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.RunReport;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.SampleOutcome;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.SampleInput;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.SampleResult;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.Workload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发聚合：多线程调度与结果聚合不得引入与执行顺序有关的偏差。
 */
class ConcurrentAggregationTest {

    private final BenchmarkRunner runner = new BenchmarkRunner();

    /**
     * 故意制造执行顺序扰动的工作负载：每个样本先随机睡眠 0~3ms，
     * 让完成顺序在每次运行中都不同；另有 1/4 样本抛异常。
     */
    private Workload jittery() {
        return new Workload() {
            @Override
            public String name() {
                return "jittery";
            }

            @Override
            public SampleResult execute(SampleInput input) throws Exception {
                Thread.sleep(ThreadLocalRandom.current().nextInt(4));
                if (input.index() % 4 == 1) {
                    throw new RuntimeException("flaky " + input.index());
                }
                return new SampleResult("digest-" + input.index(), 100L + input.index());
            }
        };
    }

    private BenchConfig config(int concurrency) {
        return new BenchConfig("jittery", 64, concurrency, 0, 120, 2024L, 10_000L, 5.0);
    }

    @Test
    void aggregationIsIndependentOfCompletionOrder() {
        Workload w = jittery();
        RunReport baseline = runner.run(w, config(8));
        for (int i = 0; i < 5; i++) {
            RunReport again = runner.run(w, config(8));
            assertEquals(baseline, again, "第 " + i + " 次并发运行结果与首次不一致");
        }
    }

    @Test
    void concurrentWithFailuresMatchesSerial() {
        Workload w = jittery();
        RunReport serial = runner.run(w, config(1));
        RunReport concurrent = runner.run(w, config(16));
        assertEquals(serial.metrics(), concurrent.metrics());
        assertEquals(serial.outcomes(), concurrent.outcomes());
        assertEquals(30, concurrent.metrics().failureCount());
    }

    @Test
    void outcomesAreSortedByIndex() {
        RunReport report = runner.run(jittery(), config(8));
        List<Long> indexes = report.outcomes().stream().map(SampleOutcome::index).toList();
        List<Long> expected = IntStream.range(0, 120).mapToObj(i -> (long) i).toList();
        assertEquals(expected, indexes, "聚合结果必须按 index 升序");
    }

    @Test
    void percentilesAreComputedWithDocumentedAlgorithm() {
        // 线性插值口径：rank = q * (n - 1)
        long[] sorted = {10, 20, 30, 40};
        assertEquals(25.0, com.github.cuixudong74.reproduciblebenchharnessjava.bench.core
                .Percentiles.percentile(sorted, 0.5), 1e-9);
        assertEquals(10.0, com.github.cuixudong74.reproduciblebenchharnessjava.bench.core
                .Percentiles.percentile(sorted, 0.0), 1e-9);
        assertEquals(40.0, com.github.cuixudong74.reproduciblebenchharnessjava.bench.core
                .Percentiles.percentile(sorted, 1.0), 1e-9);
        assertTrue(com.github.cuixudong74.reproduciblebenchharnessjava.bench.core
                .Percentiles.percentile(new long[0], 0.9) == 0.0);
    }
}
