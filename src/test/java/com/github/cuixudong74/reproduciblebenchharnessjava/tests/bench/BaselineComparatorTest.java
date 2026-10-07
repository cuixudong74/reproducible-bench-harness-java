package com.github.cuixudong74.reproduciblebenchharnessjava.tests.bench;

import com.github.cuixudong74.reproduciblebenchharnessjava.bench.baseline.BaselineComparator;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.baseline.ComparisonResult;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.baseline.RegressionFinding;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.BenchConfig;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.Metrics;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.RunReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基线比对：回归必须被明确标记，并给出发生变化的指标与幅度；容差可配置。
 */
class BaselineComparatorTest {

    private final BaselineComparator comparator = new BaselineComparator();

    private RunReport report(Metrics metrics) {
        return new RunReport(BenchConfig.ofDefaults("w"), metrics, List.of());
    }

    private Metrics metrics(long success, long failure, double throughput,
                            double mean, double p50, double p90, double p99) {
        return new Metrics(success, failure, throughput, mean, p50, p90, p99);
    }

    @Test
    void identicalMetricsProduceNoRegression() {
        Metrics m = metrics(100, 0, 1000.0, 500.0, 480.0, 700.0, 900.0);
        ComparisonResult result = comparator.compare(report(m), report(m), 5.0);
        assertTrue(result.compared());
        assertFalse(result.regression());
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void latencyIncreaseBeyondToleranceIsFlaggedWithMagnitude() {
        Metrics baseline = metrics(100, 0, 1000.0, 500.0, 480.0, 700.0, 900.0);
        Metrics current = metrics(100, 0, 1000.0, 500.0, 480.0, 770.0, 900.0); // p90 +10%
        ComparisonResult result = comparator.compare(report(baseline), report(current), 5.0);
        assertTrue(result.regression());
        assertEquals(1, result.findings().size());
        RegressionFinding f = result.findings().get(0);
        assertEquals("p90CostNanos", f.metric());
        assertEquals(700.0, f.baselineValue());
        assertEquals(770.0, f.currentValue());
        assertEquals(10.0, f.changePercent(), 1e-9);
    }

    @Test
    void latencyIncreaseWithinToleranceIsNotFlagged() {
        Metrics baseline = metrics(100, 0, 1000.0, 500.0, 480.0, 700.0, 900.0);
        Metrics current = metrics(100, 0, 1000.0, 500.0, 480.0, 720.0, 900.0); // p90 +2.86%
        ComparisonResult result = comparator.compare(report(baseline), report(current), 5.0);
        assertFalse(result.regression());
    }

    @Test
    void throughputDropBeyondToleranceIsFlagged() {
        Metrics baseline = metrics(100, 0, 1000.0, 500.0, 480.0, 700.0, 900.0);
        Metrics current = metrics(100, 0, 800.0, 500.0, 480.0, 700.0, 900.0); // 吞吐 -20%
        ComparisonResult result = comparator.compare(report(baseline), report(current), 5.0);
        assertTrue(result.regression());
        RegressionFinding f = result.findings().get(0);
        assertEquals("throughputPerSecond", f.metric());
        assertEquals(-20.0, f.changePercent(), 1e-9);
    }

    @Test
    void failureCountIncreaseIsFlagged() {
        Metrics baseline = metrics(100, 0, 1000.0, 500.0, 480.0, 700.0, 900.0);
        Metrics current = metrics(97, 3, 1000.0, 500.0, 480.0, 700.0, 900.0);
        ComparisonResult result = comparator.compare(report(baseline), report(current), 5.0);
        assertTrue(result.regression());
        assertTrue(result.findings().stream().anyMatch(f -> f.metric().equals("failureCount")));
    }

    @Test
    void improvementIsNotRegression() {
        Metrics baseline = metrics(100, 0, 1000.0, 500.0, 480.0, 700.0, 900.0);
        Metrics current = metrics(100, 0, 1200.0, 400.0, 380.0, 560.0, 720.0);
        ComparisonResult result = comparator.compare(report(baseline), report(current), 5.0);
        assertFalse(result.regression());
    }
}
