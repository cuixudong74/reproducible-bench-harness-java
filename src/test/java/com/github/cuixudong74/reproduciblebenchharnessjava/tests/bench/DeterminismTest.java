package com.github.cuixudong74.reproduciblebenchharnessjava.tests.bench;

import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.BenchConfig;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.BenchmarkRunner;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.RunReport;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.demo.HashWorkload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 确定性复现：相同配置 + 相同种子，重复运行结果完全一致；
 * 并发与串行得到的可复现性结论一致。
 */
class DeterminismTest {

    private final BenchmarkRunner runner = new BenchmarkRunner();
    private final HashWorkload workload = new HashWorkload();

    private BenchConfig config(int concurrency) {
        return new BenchConfig(HashWorkload.NAME, 256, concurrency, 5, 200, 12345L, 10_000L, 5.0);
    }

    @Test
    void sameSeedSameConfigProducesIdenticalReports() {
        RunReport first = runner.run(workload, config(4));
        RunReport second = runner.run(workload, config(4));
        assertEquals(first, second, "两次运行的完整报告必须完全一致");
    }

    @Test
    void concurrentAndSerialRunsProduceIdenticalResults() {
        RunReport serial = runner.run(workload, config(1));
        RunReport concurrent = runner.run(workload, config(8));
        assertEquals(serial.metrics(), concurrent.metrics(), "并发与串行的统计指标必须一致");
        assertEquals(serial.outcomes(), concurrent.outcomes(), "并发与串行的逐样本结局必须一致");
    }

    @Test
    void differentSeedProducesDifferentInputs() {
        RunReport a = runner.run(workload, config(2));
        RunReport b = runner.run(workload,
                new BenchConfig(HashWorkload.NAME, 256, 2, 5, 200, 99999L, 10_000L, 5.0));
        org.junit.jupiter.api.Assertions.assertNotEquals(a.outcomes(), b.outcomes(),
                "不同种子应产生不同输入与结果");
    }
}
