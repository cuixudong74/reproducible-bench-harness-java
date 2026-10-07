package com.github.cuixudong74.reproduciblebenchharnessjava.tests.bench;

import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.BenchConfig;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.BenchmarkRunner;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.RunReport;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.SampleOutcome;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.SampleInput;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.SampleResult;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.Workload;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异常隔离：异常与超时计入失败，不影响其余样本，运行不中断；
 * 运行结束后不残留后台线程。
 */
class FailureIsolationTest {

    private final BenchmarkRunner runner = new BenchmarkRunner();

    /** 每第 5 个样本抛异常的工作负载。 */
    private Workload flaky() {
        return new Workload() {
            @Override
            public String name() {
                return "flaky";
            }

            @Override
            public SampleResult execute(SampleInput input) {
                if (input.index() % 5 == 0) {
                    throw new IllegalStateException("boom at " + input.index());
                }
                return new SampleResult("ok-" + input.index(), 100L);
            }
        };
    }

    /** 每第 7 个样本沉睡远超超时时间的工作负载。 */
    private Workload sleepy() {
        return new Workload() {
            @Override
            public String name() {
                return "sleepy";
            }

            @Override
            public SampleResult execute(SampleInput input) throws Exception {
                if (input.index() % 7 == 0) {
                    Thread.sleep(60_000L);
                }
                return new SampleResult("ok-" + input.index(), 100L);
            }
        };
    }

    private BenchConfig config(int measurement, long timeoutMillis) {
        return new BenchConfig("flaky", 64, 4, 0, measurement, 42L, timeoutMillis, 5.0);
    }

    @Test
    void exceptionsAreCountedAndOtherSamplesSurvive() {
        RunReport report = runner.run(flaky(), config(50, 5_000L));
        assertEquals(10, report.metrics().failureCount());
        assertEquals(40, report.metrics().successCount());
        assertEquals(50, report.outcomes().size(), "失败样本不得静默丢失");
        Set<Long> failedIndexes = report.outcomes().stream()
                .filter(o -> !o.success()).map(SampleOutcome::index).collect(Collectors.toSet());
        for (long i = 0; i < 50; i++) {
            assertEquals(i % 5 == 0, failedIndexes.contains(i), "index " + i + " 结局不符");
        }
        assertTrue(report.outcomes().stream().filter(o -> !o.success())
                .allMatch(o -> o.failureKind().startsWith("EXCEPTION")));
    }

    @Test
    void timeoutsAreCountedAndRunCompletes() {
        RunReport report = runner.run(sleepy(), config(28, 200L));
        assertEquals(4, report.metrics().failureCount());
        assertEquals(24, report.metrics().successCount());
        assertTrue(report.outcomes().stream().filter(o -> !o.success())
                .allMatch(o -> o.failureKind().equals("TIMEOUT")));
    }

    @Test
    void noWorkerThreadsRemainAfterRun() {
        runner.run(flaky(), config(20, 5_000L));
        runner.run(sleepy(), config(14, 200L));
        long benchThreads = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith("bench-worker-") && t.isAlive())
                .count();
        assertEquals(0, benchThreads, "运行结束后不得残留 bench-worker 线程");
    }

    @Test
    void repeatedRunsDoNotLeakThreads() {
        long before = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith("bench-worker-")).count();
        for (int i = 0; i < 10; i++) {
            runner.run(flaky(), config(10, 5_000L));
        }
        long after = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith("bench-worker-")).count();
        assertEquals(before, after, "重复运行不得累积线程");
    }
}
