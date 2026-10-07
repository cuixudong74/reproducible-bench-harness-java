package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.baselining.JsonBaselineStore;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.config.BenchmarkConfiguration;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.contract.BenchmarkWorkload;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.contract.ExecutionClock;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.engine.BenchmarkEngine;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.BenchmarkResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineSerializationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void persistedJsonContainsOnlyStableBenchmarkFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        BenchmarkEngine engine = new BenchmarkEngine();
        JsonBaselineStore store = new JsonBaselineStore(tempDirectory, objectMapper);
        BenchmarkConfiguration configuration = new BenchmarkConfiguration(
                "serialization-workload", 2, 1, 1, 4, 8L,
                TimeUnit.MILLISECONDS.toNanos(1L));
        BenchmarkResult result = engine.run(new StableWorkload(), configuration);
        store.update(result);

        Path file = tempDirectory.resolve("serialization-workload.baseline.json");
        JsonNode root = objectMapper.readTree(Files.readAllBytes(file));
        Set<String> fieldNames = new java.util.TreeSet<>();
        collectFieldNames(root, fieldNames);

        assertThat(fieldNames).doesNotContain(
                "timestamp", "runTime", "wallTime", "pid", "processId",
                "host", "hostname", "thread", "threadName",
                "message", "stackTrace", "localizedMessage");
        assertThat(root.path("timeSource").asText())
                .isEqualTo("deterministic-logical-clock:java.lang.Long.advanceNanos");
        assertThat(root.path("samples").get(0).fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "sampleIndex", "success", "timeout",
                        "latencyNanos", "outputHash", "exceptionType");
    }

    private static void collectFieldNames(JsonNode node, Set<String> names) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(names::add);
            node.forEach(child -> collectFieldNames(child, names));
        } else if (node.isArray()) {
            node.forEach(child -> collectFieldNames(child, names));
        }
    }

    private static final class StableWorkload implements BenchmarkWorkload<Long, String> {
        @Override
        public String id() {
            return "serialization-workload";
        }

        @Override
        public Long createInput(long seed, int sampleIndex, int inputSize) {
            return seed + sampleIndex;
        }

        @Override
        public String execute(Long input, ExecutionClock clock) {
            clock.advanceNanos(1_000L);
            return "stable:" + input;
        }
    }
}
