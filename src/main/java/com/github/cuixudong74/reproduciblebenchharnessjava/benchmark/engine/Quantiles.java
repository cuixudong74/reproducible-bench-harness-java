package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.engine;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Nearest-rank quantile calculations with deterministic tie and index behavior. */
final class Quantiles {
    private Quantiles() {
    }

    static long nearestRank(long[] sortedValues, double quantile) {
        if (sortedValues.length == 0) {
            throw new IllegalArgumentException("cannot calculate quantile without values");
        }
        if (quantile < 0.0d || quantile > 1.0d || Double.isNaN(quantile)) {
            throw new IllegalArgumentException("quantile must be in [0, 1]");
        }
        int rank = (int) Math.ceil(quantile * sortedValues.length);
        if (rank < 1) {
            rank = 1;
        }
        if (rank > sortedValues.length) {
            rank = sortedValues.length;
        }
        return sortedValues[rank - 1];
    }

    static Map<Double, Long> nearestRanks(long[] values, double[] quantiles) {
        if (values.length == 0) {
            throw new IllegalArgumentException("cannot calculate quantiles without values");
        }
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        Map<Double, Long> result = new LinkedHashMap<>();
        for (double quantile : quantiles) {
            result.put(quantile, nearestRank(sorted, quantile));
        }
        return Map.copyOf(result);
    }
}
