package com.github.cuixudong74.reproduciblebenchharnessjava.bench.baseline;

import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.Metrics;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.RunReport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基线比对器：逐项指标按相对容差判定回归。
 *
 * 口径：
 * - 延迟类指标（mean/p50/p90/p99）与失败计数：上升超过容差为劣化；
 * - 吞吐：下降超过容差为劣化；
 * - 相对变化 = (current - baseline) / baseline * 100%，基线为 0 时
 *   仅当当前值也为 0 才视为无变化，否则视为 100% 劣化方向变化。
 */
@Component
public class BaselineComparator {

    public ComparisonResult compare(RunReport baseline, RunReport current, double tolerancePercent) {
        Metrics b = baseline.metrics();
        Metrics c = current.metrics();
        List<RegressionFinding> findings = new ArrayList<>();

        // 越高越差的指标
        checkHigherWorse(findings, "failureCount", b.failureCount(), c.failureCount(), tolerancePercent);
        checkHigherWorse(findings, "meanCostNanos", b.meanCostNanos(), c.meanCostNanos(), tolerancePercent);
        checkHigherWorse(findings, "p50CostNanos", b.p50CostNanos(), c.p50CostNanos(), tolerancePercent);
        checkHigherWorse(findings, "p90CostNanos", b.p90CostNanos(), c.p90CostNanos(), tolerancePercent);
        checkHigherWorse(findings, "p99CostNanos", b.p99CostNanos(), c.p99CostNanos(), tolerancePercent);
        // 越低越差的指标
        checkLowerWorse(findings, "successCount", b.successCount(), c.successCount(), tolerancePercent);
        checkLowerWorse(findings, "throughputPerSecond",
                b.throughputPerSecond(), c.throughputPerSecond(), tolerancePercent);

        return new ComparisonResult(true, !findings.isEmpty(), List.copyOf(findings));
    }

    private void checkHigherWorse(List<RegressionFinding> out, String name,
                                  double baseline, double current, double tolerancePercent) {
        double change = changePercent(baseline, current);
        if (change > tolerancePercent) {
            out.add(new RegressionFinding(name, baseline, current, change));
        }
    }

    private void checkLowerWorse(List<RegressionFinding> out, String name,
                                 double baseline, double current, double tolerancePercent) {
        double change = changePercent(baseline, current);
        if (change < -tolerancePercent) {
            out.add(new RegressionFinding(name, baseline, current, change));
        }
    }

    static double changePercent(double baseline, double current) {
        if (baseline == 0.0) {
            return current == 0.0 ? 0.0 : 100.0;
        }
        return (current - baseline) / baseline * 100.0;
    }
}
