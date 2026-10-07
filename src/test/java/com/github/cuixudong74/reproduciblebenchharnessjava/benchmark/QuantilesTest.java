package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuantilesTest {
    @Test
    void usesNearestRankWithoutInterpolation() throws Exception {
        long[] values = {30L, 10L, 20L};
        double[] quantiles = {0.0d, 0.5d, 1.0d};

        Class<?> type = Class.forName(
                "com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.engine.Quantiles");
        Method method = type.getDeclaredMethod("nearestRanks", long[].class, double[].class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Double, Long> result = (Map<Double, Long>) method.invoke(null, values, quantiles);

        assertThat(result)
                .containsEntry(0.0d, 10L)
                .containsEntry(0.5d, 20L)
                .containsEntry(1.0d, 30L);
        assertThat(values).containsExactly(30L, 10L, 20L);
    }
}
