package com.github.cuixudong74.reproduciblebenchharnessjava.bench.baseline;

import java.util.List;

/**
 * 当前运行与基线的比对结论。
 *
 * @param compared     是否实际执行了比对（无基线时为 false）
 * @param regression   是否存在超出容差的回归
 * @param findings     超出容差的回归项明细
 */
public record ComparisonResult(
        boolean compared,
        boolean regression,
        List<RegressionFinding> findings) {

    public static ComparisonResult noBaseline() {
        return new ComparisonResult(false, false, List.of());
    }
}
