package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.service;

import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.baselining.BaselineComparator;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.baselining.BaselineStore;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.config.BenchmarkConfiguration;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.contract.BenchmarkWorkload;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.engine.BenchmarkEngine;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.BenchmarkExecution;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.BenchmarkResult;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.RegressionReport;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Application facade for running benchmarks and managing baselines.
 *
 * <p>{@link #run} is intentionally read-only with respect to baseline files. Baseline
 * replacement requires an explicit call to {@link #captureBaseline}.
 */
@Service
public class BenchmarkService {
    private final BenchmarkEngine engine;
    private final BaselineStore baselineStore;
    private final BaselineComparator comparator;

    public BenchmarkService(
            BenchmarkEngine engine,
            BaselineStore baselineStore,
            BaselineComparator comparator) {
        this.engine = engine;
        this.baselineStore = baselineStore;
        this.comparator = comparator;
    }

    public <I, O> BenchmarkExecution run(
            BenchmarkWorkload<I, O> workload, BenchmarkConfiguration configuration) {
        BenchmarkResult result = engine.run(workload, configuration);
        Optional<BenchmarkResult> baseline = baselineStore.load(configuration.workloadName());
        RegressionReport report = comparator.compare(result, baseline.orElse(null));
        return new BenchmarkExecution(result, report);
    }

    public <I, O> BenchmarkResult captureBaseline(
            BenchmarkWorkload<I, O> workload, BenchmarkConfiguration configuration) {
        BenchmarkResult result = engine.run(workload, configuration);
        baselineStore.update(result);
        return result;
    }

    public BenchmarkResult rollbackBaseline(String workloadName) {
        return baselineStore.rollback(workloadName);
    }

    public Optional<BenchmarkResult> loadBaseline(String workloadName) {
        return baselineStore.load(workloadName);
    }

    public boolean hasPreviousBaseline(String workloadName) {
        return baselineStore.hasPrevious(workloadName);
    }
}
