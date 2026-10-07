package com.github.cuixudong74.reproduciblebenchharnessjava.bench.web;

import com.github.cuixudong74.reproduciblebenchharnessjava.bench.baseline.BaselineComparator;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.baseline.BaselineStore;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.baseline.ComparisonResult;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.BenchConfig;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.BenchmarkRunner;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.RunReport;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.WorkloadRegistry;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.Workload;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 基准测试编排服务：运行、自动比对基线、受控更新与回退。
 *
 * 写纪律：{@link #run(BenchConfig)} 绝不触碰基线文件；
 * 只有 {@link #runAndUpdateBaseline(BenchConfig)} 会覆盖基线（并保留旧版）。
 */
@Service
public class BenchService {

    private final WorkloadRegistry registry;
    private final BenchmarkRunner runner;
    private final BaselineStore baselineStore;
    private final BaselineComparator comparator;

    public BenchService(WorkloadRegistry registry, BenchmarkRunner runner,
                        BaselineStore baselineStore, BaselineComparator comparator) {
        this.registry = registry;
        this.runner = runner;
        this.baselineStore = baselineStore;
        this.comparator = comparator;
    }

    /** 运行一次基准并自动与基线比对（绝不改写基线）。 */
    public RunAndComparison run(BenchConfig config) {
        RunReport report = runner.run(registry.require(config.workloadName()), config);
        ComparisonResult comparison = baselineStore.load(config.workloadName())
                .map(baseline -> comparator.compare(baseline, report, config.tolerancePercent()))
                .orElseGet(ComparisonResult::noBaseline);
        return new RunAndComparison(report, comparison);
    }

    /** 运行一次基准并把结果显式固化为新基线（保留旧基线，可回退）。 */
    public RunReport runAndUpdateBaseline(BenchConfig config) {
        Workload workload = registry.require(config.workloadName());
        RunReport report = runner.run(workload, config);
        baselineStore.update(config.workloadName(), report);
        return report;
    }

    public Optional<RunReport> loadBaseline(String workloadName) {
        return baselineStore.load(workloadName);
    }

    public boolean rollbackBaseline(String workloadName) {
        return baselineStore.rollback(workloadName);
    }

    public record RunAndComparison(RunReport report, ComparisonResult comparison) {
    }
}
