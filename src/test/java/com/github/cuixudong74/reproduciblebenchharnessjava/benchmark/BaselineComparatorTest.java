package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark;

import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.baselining.BaselineComparator;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.config.BenchmarkConfiguration;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.config.RegressionTolerance;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.contract.BenchmarkWorkload;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.contract.ExecutionClock;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.engine.BenchmarkEngine;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.BenchmarkResult;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.RegressionStatus;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineComparatorTest {
    private final BenchmarkEngine engine = new BenchmarkEngine();
    private final BaselineComparator comparator = new BaselineComparator();

    @Test
    void toleranceControlsLatencyRegressionAndOutputDriftIsAlwaysReported() {
        BenchmarkConfiguration configuration = new BenchmarkConfiguration(
                "comparator-workload", 2, 1, 0, 10, 5L,
                TimeUnit.MILLISECONDS.toNanos(10L),
                new double[] {0.5d, 0.95d},
                new RegressionTolerance(0.10d, 500L, 0.0d));

        BenchmarkResult baseline = engine.run(new FixedWorkload(1_000L, "same"), configuration);
        BenchmarkResult withinTolerance = engine.run(new FixedWorkload(1_010L, "same"), configuration);
        BenchmarkResult outsideTolerance = engine.run(new FixedWorkload(1_200L, "same"), configuration);
        BenchmarkResult outputDrift = engine.run(new FixedWorkload(1_000L, "changed"), configuration);

        assertThat(comparator.compare(baseline, baseline).status())
                .isEqualTo(RegressionStatus.PASSED);
        assertThat(comparator.compare(withinTolerance, baseline).status())
                .isEqualTo(RegressionStatus.PASSED);
        assertThat(comparator.compare(withinTolerance, baseline).differences())
                .allSatisfy(difference -> assertThat(difference.regression()).isFalse());
        assertThat(comparator.compare(outsideTolerance, baseline).status())
                .isEqualTo(RegressionStatus.REGRESSED);
        assertThat(comparator.compare(outputDrift, baseline).differences())
                .anySatisfy(difference -> {
                    assertThat(difference.metricName())
                            .isEqualTo("samples.deterministic.outputHashChanges");
                    assertThat(difference.regression()).isTrue();
                });
    }

    private static final class FixedWorkload implements BenchmarkWorkload<Long, String> {
        private final long latencyMicros;
        private final String outputPrefix;

        private FixedWorkload(long latencyMicros, String outputPrefix) {
            this.latencyMicros = latencyMicros;
            this.outputPrefix = outputPrefix;
        }

        @Override
        public String id() {
            return "comparator-workload";
        }

        @Override
        public Long createInput(long seed, int sampleIndex, int inputSize) {
            return seed + sampleIndex;
        }

        @Override
        public String execute(Long input, ExecutionClock clock) {
            clock.advanceNanos(TimeUnit.MICROSECONDS.toNanos(latencyMicros));
            return outputPrefix + ':' + input;
        }
    }
}
