package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.engine;

/** Thrown by a logical clock when a sample exceeds its configured deterministic timeout. */
final class DeterministicTimeoutException extends RuntimeException {
    DeterministicTimeoutException() {
        super("deterministic sample timeout", null, false, false);
    }
}
