package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.baselining.BaselineComparator;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.baselining.JsonBaselineStore;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.config.BenchmarkConfiguration;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.config.RegressionTolerance;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.contract.BenchmarkWorkload;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.contract.ExecutionClock;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.BenchmarkExecution;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.BenchmarkResult;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.MetricDifference;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.RegressionStatus;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.service.BenchmarkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineLifecycleTest {
    @TempDir
    Path tempDirectory;

    @Test
    void normalRunIsReadOnlyAndExplicitUpdateCanBeRolledBack() throws Exception {
        BenchmarkService service = service();
        VariableWorkload workload = new VariableWorkload(10L, "v1");
        BenchmarkConfiguration configuration = configuration();

        BenchmarkExecution firstRun = service.run(workload, configuration);
        assertThat(firstRun.regressionReport().status())
                .isEqualTo(RegressionStatus.NO_BASELINE);
        assertThat(service.loadBaseline(workload.id())).isEmpty();

        BenchmarkResult firstBaseline = service.captureBaseline(workload, configuration);
        assertThat(service.hasPreviousBaseline(workload.id())).isFalse();
        assertThat(service.run(workload, configuration).regressionReport().status())
                .isEqualTo(RegressionStatus.PASSED);

        VariableWorkload slower = new VariableWorkload(20L, "v1");
        BenchmarkExecution regressed = service.run(slower, configuration);
        assertThat(regressed.hasRegression()).isTrue();
        assertThat(regressed.regressionReport().status()).isEqualTo(RegressionStatus.REGRESSED);
        assertThat(service.loadBaseline(workload.id())).get()
                .usingRecursiveComparison().isEqualTo(firstBaseline);
        MetricDifference latency = findDifference(regressed, "latency.p50.nanos");
        assertThat(latency.regression()).isTrue();
        assertThat(latency.baselineValue()).isEqualTo("10000");
        assertThat(latency.currentValue()).isEqualTo("20000");
        assertThat(latency.absoluteDelta()).isEqualTo("10000");
        MetricDifference throughput = findDifference(regressed, "throughput.samplesPerSecond");
        assertThat(throughput.regression()).isTrue();

        BenchmarkResult secondBaseline = service.captureBaseline(slower, configuration);
        assertThat(service.hasPreviousBaseline(workload.id())).isTrue();
        assertThat(service.loadBaseline(workload.id())).get()
                .usingRecursiveComparison().isEqualTo(secondBaseline);

        Path previousFile = tempDirectory.resolve(
                "lifecycle-workload.baseline.previous.json");
        byte[] retainedPrevious = java.nio.file.Files.readAllBytes(previousFile);
        BenchmarkResult rolledBack = service.rollbackBaseline(workload.id());
        assertThat(rolledBack).usingRecursiveComparison().isEqualTo(firstBaseline);
        assertThat(service.loadBaseline(workload.id())).get()
                .usingRecursiveComparison().isEqualTo(firstBaseline);
        assertThat(service.hasPreviousBaseline(workload.id())).isTrue();
        assertThat(java.nio.file.Files.readAllBytes(previousFile))
                .isEqualTo(retainedPrevious);
        assertThat(service.rollbackBaseline(workload.id()))
                .usingRecursiveComparison().isEqualTo(firstBaseline);
    }

    private BenchmarkService service() {
        JsonBaselineStore store =
                new JsonBaselineStore(tempDirectory, new ObjectMapper());
        return new BenchmarkService(
                new com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.engine.BenchmarkEngine(),
                store,
                new BaselineComparator());
    }

    private static BenchmarkConfiguration configuration() {
        return new BenchmarkConfiguration(
                "lifecycle-workload",
                4,
                2,
                1,
                8,
                1234L,
                TimeUnit.MILLISECONDS.toNanos(1L),
                new double[] {0.50d, 0.95d},
                new RegressionTolerance(0.05d, 100L, 0.0d));
    }

    private static MetricDifference findDifference(BenchmarkExecution execution, String name) {
        return execution.regressionReport().differences().stream()
                .filter(difference -> difference.metricName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static final class VariableWorkload implements BenchmarkWorkload<Long, String> {
        private final long latencyMicros;
        private final String outputVersion;

        private VariableWorkload(long latencyMicros, String outputVersion) {
            this.latencyMicros = latencyMicros;
            this.outputVersion = outputVersion;
        }

        @Override
        public String id() {
            return "lifecycle-workload";
        }

        @Override
        public Long createInput(long seed, int sampleIndex, int inputSize) {
            return seed + sampleIndex;
        }

        @Override
        public String execute(Long input, ExecutionClock clock) {
            clock.advanceNanos(TimeUnit.MICROSECONDS.toNanos(latencyMicros));
            return outputVersion + ':' + input;
        }
    }
}
