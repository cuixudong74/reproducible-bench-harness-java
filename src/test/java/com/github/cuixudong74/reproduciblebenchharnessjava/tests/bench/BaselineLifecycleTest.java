package com.github.cuixudong74.reproduciblebenchharnessjava.tests.bench;

import com.github.cuixudong74.reproduciblebenchharnessjava.bench.baseline.BaselineComparator;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.baseline.BaselineStore;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.BenchConfig;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.BenchmarkRunner;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.RunReport;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.WorkloadRegistry;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.demo.HashWorkload;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.web.BenchProperties;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.web.BenchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基线生命周期：正常运行不改写基线；显式更新保留上一份；支持回退。
 */
class BaselineLifecycleTest {

    @TempDir
    Path baselineDir;

    private BenchService service;
    private final BenchConfig config =
            new BenchConfig(HashWorkload.NAME, 128, 2, 3, 50, 777L, 10_000L, 5.0);

    @BeforeEach
    void setUp() {
        HashWorkload workload = new HashWorkload();
        service = new BenchService(
                new WorkloadRegistry(java.util.List.of(workload)),
                new BenchmarkRunner(),
                new BaselineStore(new BenchProperties(baselineDir.toString())),
                new BaselineComparator());
    }

    @Test
    void normalRunNeverWritesBaseline() {
        assertTrue(service.loadBaseline(config.workloadName()).isEmpty());
        service.run(config);
        service.run(config);
        assertTrue(service.loadBaseline(config.workloadName()).isEmpty(),
                "正常运行不得创建或改写基线");
    }

    @Test
    void runWithoutBaselineReportsNotCompared() {
        assertFalse(service.run(config).comparison().compared());
    }

    @Test
    void runMatchingBaselineReportsNoRegression() {
        service.runAndUpdateBaseline(config);
        BenchService.RunAndComparison result = service.run(config);
        assertTrue(result.comparison().compared());
        assertFalse(result.comparison().regression());
    }

    @Test
    void explicitUpdateKeepsPreviousAndRollbackRestores() {
        RunReport first = service.runAndUpdateBaseline(config);
        // 第二次用不同种子产生不同结果，再固化为基线
        BenchConfig other = new BenchConfig(HashWorkload.NAME, 128, 2, 3, 50, 888L, 10_000L, 5.0);
        RunReport second = service.runAndUpdateBaseline(other);

        Optional<RunReport> current = service.loadBaseline(config.workloadName());
        assertTrue(current.isPresent());
        assertEquals(second, current.get(), "更新后当前基线应为最新结果");

        assertTrue(service.rollbackBaseline(config.workloadName()));
        Optional<RunReport> rolledBack = service.loadBaseline(config.workloadName());
        assertTrue(rolledBack.isPresent());
        assertEquals(first, rolledBack.get(), "回退后应恢复上一份基线");
    }

    @Test
    void rollbackWithoutPreviousReturnsFalse() {
        assertFalse(service.rollbackBaseline(config.workloadName()));
        service.runAndUpdateBaseline(config);
        // 只有一份基线，没有 previous，不可回退
        assertFalse(service.rollbackBaseline(config.workloadName()));
    }
}
