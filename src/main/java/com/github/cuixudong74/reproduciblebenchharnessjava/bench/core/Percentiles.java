package com.github.cuixudong74.reproduciblebenchharnessjava.bench.core;

/**
 * 分位数计算。
 *
 * 算法口径（固定，写入文档）：
 * - 输入先升序排序（由 {@link #of(long[], double)} 内部复制并排序，不改动调用方数组）；
 * - 采用线性插值法：rank = q * (n - 1)，在 floor(rank) 与 ceil(rank) 两个相邻
 *   有序值之间按小数部分线性插值。与 numpy 默认 method='linear'、
 *   Excel PERCENTILE.INC 一致；
 * - n = 0 时返回 0；q 取值范围 [0, 1]。
 */
public final class Percentiles {

    private Percentiles() {
    }

    /** 对已升序排序的数组求分位数。 */
    public static double percentile(long[] sortedValues, double quantile) {
        if (quantile < 0.0 || quantile > 1.0) {
            throw new IllegalArgumentException("quantile out of range: " + quantile);
        }
        int n = sortedValues.length;
        if (n == 0) {
            return 0.0;
        }
        if (n == 1) {
            return sortedValues[0];
        }
        double rank = quantile * (n - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sortedValues[lo];
        }
        double frac = rank - lo;
        return sortedValues[lo] * (1.0 - frac) + sortedValues[hi] * frac;
    }

    /** 复制、排序后求分位数，不修改入参数组。 */
    public static double of(long[] values, double quantile) {
        long[] copy = values.clone();
        java.util.Arrays.sort(copy);
        return percentile(copy, quantile);
    }
}
