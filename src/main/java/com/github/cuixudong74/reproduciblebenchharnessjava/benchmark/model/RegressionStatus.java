package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model;

/** Outcome of comparing one run with its stored baseline. */
public enum RegressionStatus {
    NO_BASELINE,
    PASSED,
    REGRESSED
}
