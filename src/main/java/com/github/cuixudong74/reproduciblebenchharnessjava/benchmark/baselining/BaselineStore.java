package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.baselining;

import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.BenchmarkResult;

import java.util.Optional;

/** Persistence operations for controlled, auditable benchmark baselines. */
public interface BaselineStore {

    Optional<BenchmarkResult> load(String workloadName);

    /**
     * Explicitly replace the current baseline.
     *
     * <p>If a current baseline exists, its contents are copied to the workload's single
     * previous-baseline slot before the replacement becomes visible.
     */
    void update(BenchmarkResult result);

    /** Restore the retained previous baseline as the current baseline. */
    BenchmarkResult rollback(String workloadName);

    boolean hasPrevious(String workloadName);
}
