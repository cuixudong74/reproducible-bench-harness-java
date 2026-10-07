package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.baselining;

import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.config.BenchmarkConfiguration;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.config.RegressionTolerance;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.BenchmarkResult;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.LatencyMetrics;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.MetricDifference;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.RegressionReport;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.RegressionStatus;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.SampleOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compares a run against a captured baseline using the fixed metric contract. */
@Component
public class BaselineComparator {

    public RegressionReport compare(BenchmarkResult current, BenchmarkResult baseline) {
        Objects.requireNonNull(current, "current");
        if (baseline == null) {
            return new RegressionReport(RegressionStatus.NO_BASELINE,
                    "", current.fingerprint(), List.of());
        }

        List<MetricDifference> differences = new ArrayList<>();
        if (!isComparableConfiguration(current.configuration(), baseline.configuration())) {
            differences.add(new MetricDifference(
                    "configuration.compatible",
                    "true",
                    "false",
                    "configuration differs",
                    true));
            return toReport(current, baseline, differences);
        }

        if (current.fingerprint().equals(baseline.fingerprint())) {
            return new RegressionReport(RegressionStatus.PASSED,
                    baseline.fingerprint(), current.fingerprint(), List.of());
        }

        compareCounts(differences, current, baseline);
        compareThroughput(differences, current, baseline);
        compareLatency(differences, current.latency(), baseline.latency(),
                current.configuration().tolerance());
        compareSamples(differences, current.samples(), baseline.samples());
        return toReport(current, baseline, differences);
    }

    private static boolean isComparableConfiguration(
            BenchmarkConfiguration current, BenchmarkConfiguration baseline) {
        return current.inputSize() == baseline.inputSize()
                && current.warmupIterations() == baseline.warmupIterations()
                && current.measurementIterations() == baseline.measurementIterations()
                && current.seed() == baseline.seed()
                && current.timeoutNanos() == baseline.timeoutNanos()
                && current.workloadName().equals(baseline.workloadName())
                && java.util.Arrays.equals(current.quantiles(), baseline.quantiles());
    }

    private static void compareCounts(
            List<MetricDifference> differences, BenchmarkResult current,
            BenchmarkResult baseline) {
        addLongDifference(differences, "samples.total",
                baseline.totalSamples(), current.totalSamples(),
                current.totalSamples() != baseline.totalSamples());
        addLongDifference(differences, "samples.success",
                baseline.successCount(), current.successCount(),
                current.successCount() < baseline.successCount());
        addLongDifference(differences, "samples.failure",
                baseline.failureCount(), current.failureCount(),
                current.failureCount() > baseline.failureCount());
    }

    private static void compareThroughput(
            List<MetricDifference> differences, BenchmarkResult current,
            BenchmarkResult baseline) {
        double baselineValue = baseline.throughputSamplesPerSecond();
        double currentValue = current.throughputSamplesPerSecond();
        RegressionTolerance tolerance = current.configuration().tolerance();
        double absoluteDrop = baselineValue - currentValue;
        double relativeDrop = baselineValue == 0.0d ? 0.0d : absoluteDrop / baselineValue;
        boolean regression = absoluteDrop > tolerance.absoluteThroughput()
                && relativeDrop > tolerance.relativeTolerance();
        addDoubleDifference(differences, "throughput.samplesPerSecond",
                baselineValue, currentValue, absoluteDrop, regression);
    }

    private static void compareLatency(
            List<MetricDifference> differences,
            LatencyMetrics current,
            LatencyMetrics baseline,
            RegressionTolerance tolerance) {
        addLatencyDifference(differences, "latency.min.nanos",
                baseline.minNanos(), current.minNanos(), tolerance);
        addLatencyDifference(differences, "latency.max.nanos",
                baseline.maxNanos(), current.maxNanos(), tolerance);
        addLatencyDifference(differences, "latency.sum.nanos",
                baseline.sumNanos(), current.sumNanos(), tolerance);
        addDoubleDifference(differences, "latency.average.nanos",
                baseline.averageNanos(), current.averageNanos(),
                current.averageNanos() - baseline.averageNanos(),
                exceedsLatencyTolerance(baseline.averageNanos(), current.averageNanos(), tolerance));
        baseline.quantileNanos().forEach((quantile, baselineValue) -> {
            Long currentValue = current.quantileNanos().get(quantile);
            if (currentValue != null) {
                addLatencyDifference(differences,
                        "latency." + quantileKey(quantile) + ".nanos",
                        baselineValue, currentValue, tolerance);
            }
        });
    }

    private static void compareSamples(
            List<MetricDifference> differences,
            List<SampleOutcome> currentSamples,
            List<SampleOutcome> baselineSamples) {
        int statusChanges = 0;
        int timeoutChanges = 0;
        int outputChanges = 0;
        int exceptionChanges = 0;
        int latencySequenceChanges = 0;
        int compared = Math.min(currentSamples.size(), baselineSamples.size());
        for (int index = 0; index < compared; index++) {
            SampleOutcome current = currentSamples.get(index);
            SampleOutcome baseline = baselineSamples.get(index);
            if (current.success() != baseline.success()) {
                statusChanges++;
            }
            if (current.timeout() != baseline.timeout()) {
                timeoutChanges++;
            }
            if (!Objects.equals(current.outputHash(), baseline.outputHash())) {
                outputChanges++;
            }
            if (!Objects.equals(current.exceptionType(), baseline.exceptionType())) {
                exceptionChanges++;
            }
            if (current.latencyNanos() != baseline.latencyNanos()) {
                latencySequenceChanges++;
            }
        }
        addLongDifference(differences, "samples.deterministic.statusChanges",
                0L, statusChanges, statusChanges > 0);
        addLongDifference(differences, "samples.deterministic.timeoutChanges",
                0L, timeoutChanges, timeoutChanges > 0);
        addLongDifference(differences, "samples.deterministic.outputHashChanges",
                0L, outputChanges, outputChanges > 0);
        addLongDifference(differences, "samples.deterministic.exceptionTypeChanges",
                0L, exceptionChanges, exceptionChanges > 0);
        addLongDifference(differences, "samples.deterministic.latencySequenceChanges",
                0L, latencySequenceChanges, false);
    }

    private static void addLatencyDifference(
            List<MetricDifference> differences, String name,
            long baseline, long current, RegressionTolerance tolerance) {
        long delta = current - baseline;
        addLongDifference(differences, name, baseline, current,
                exceedsLatencyTolerance((double) baseline, (double) current, tolerance));
    }

    private static boolean exceedsLatencyTolerance(
            double baseline, double current, RegressionTolerance tolerance) {
        double increase = current - baseline;
        double relativeIncrease = baseline == 0.0d
                ? (increase > 0.0d ? Double.POSITIVE_INFINITY : 0.0d)
                : increase / baseline;
        return increase > tolerance.absoluteLatencyNanos()
                && relativeIncrease > tolerance.relativeTolerance();
    }

    private static void addLongDifference(
            List<MetricDifference> differences, String name,
            long baseline, long current, boolean regression) {
        if (baseline != current) {
            differences.add(new MetricDifference(name,
                    Long.toString(baseline), Long.toString(current),
                    Long.toString(current - baseline), regression));
        }
    }

    private static void addDoubleDifference(
            List<MetricDifference> differences, String name,
            double baseline, double current, double delta, boolean regression) {
        if (Double.compare(baseline, current) != 0) {
            differences.add(new MetricDifference(name,
                    Double.toString(baseline), Double.toString(current),
                    Double.toString(delta), regression));
        }
    }

    private static String quantileKey(double quantile) {
        return "p" + new BigDecimal(Double.toString(quantile))
                .multiply(BigDecimal.valueOf(100L))
                .stripTrailingZeros()
                .toPlainString()
                .replace('.', '_');
    }

    private static RegressionReport toReport(
            BenchmarkResult current, BenchmarkResult baseline,
            List<MetricDifference> differences) {
        boolean regression = differences.stream().anyMatch(MetricDifference::regression);
        return new RegressionReport(
                regression ? RegressionStatus.REGRESSED : RegressionStatus.PASSED,
                baseline.fingerprint(), current.fingerprint(), differences);
    }
}
