package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.contract;

/** Logical clock available to a deterministic workload execution. */
public interface ExecutionClock {
    long nanoTime();

    void advanceNanos(long deltaNanos);
}
