package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.baselining;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.BenchmarkResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/** JSON file implementation of {@link BaselineStore}. */
@Component
public class JsonBaselineStore implements BaselineStore {
    private static final String CURRENT_SUFFIX = ".baseline.json";
    private static final String PREVIOUS_SUFFIX = ".baseline.previous.json";

    private final Path directory;
    private final ObjectMapper objectMapper;

    public JsonBaselineStore(
            @Value("${benchmark.baseline.directory:${user.dir}/target/benchmark-baselines}") Path directory,
            ObjectMapper objectMapper) {
        this.directory = directory;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<BenchmarkResult> load(String workloadName) {
        Path file = currentFile(workloadName);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), BenchmarkResult.class));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read baseline: " + file, e);
        }
    }

    @Override
    public synchronized void update(BenchmarkResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        String workloadName = result.configuration().workloadName();
        Path current = currentFile(workloadName);
        Path previous = previousFile(workloadName);
        ensureDirectory();
        try {
            if (Files.isRegularFile(current)) {
                Files.copy(current, previous, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
            writeAtomically(current, result);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to update baseline for " + workloadName, e);
        }
    }

    @Override
    public synchronized BenchmarkResult rollback(String workloadName) {
        Path current = currentFile(workloadName);
        Path previous = previousFile(workloadName);
        if (!Files.isRegularFile(previous)) {
            throw new IllegalStateException("no previous baseline exists for " + workloadName);
        }
        try {
            Files.copy(previous, current, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            return objectMapper.readValue(current.toFile(), BenchmarkResult.class);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to roll back baseline for " + workloadName, e);
        }
    }

    @Override
    public boolean hasPrevious(String workloadName) {
        return Files.isRegularFile(previousFile(workloadName));
    }

    private void writeAtomically(Path target, BenchmarkResult result) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp-"
                    + Long.toUnsignedString(System.nanoTime()) + "-" + UUID.randomUUID());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), result);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create baseline directory", e);
        }
    }

    private Path currentFile(String workloadName) {
        return directory.resolve(fileName(workloadName) + CURRENT_SUFFIX);
    }

    private Path previousFile(String workloadName) {
        return directory.resolve(fileName(workloadName) + PREVIOUS_SUFFIX);
    }

    private static String fileName(String workloadName) {
        if (workloadName == null || workloadName.isBlank()) {
            throw new IllegalArgumentException("workloadName must not be blank");
        }
        return URLEncoder.encode(workloadName, StandardCharsets.UTF_8)
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}
