package com.github.cuixudong74.reproduciblebenchharnessjava.bench.baseline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.RunReport;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.web.BenchProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * 基线存储：每个工作负载一个基线文件，JSON 格式。
 *
 * 目录布局（baselineDir 下）：
 * - {@code <workload>.baseline.json}          当前基线
 * - {@code <workload>.baseline.previous.json} 上一份基线（用于回退）
 *
 * 写纪律：正常运行路径绝不调用本类的写方法；只有显式更新才允许覆盖，
 * 覆盖前先把当前基线保留为 previous，回退即把 previous 恢复为当前。
 */
@Component
public class BaselineStore {

    private final Path baselineDir;
    private final ObjectMapper objectMapper;

    public BaselineStore(BenchProperties properties) {
        this.baselineDir = Path.of(properties.baselineDir());
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        try {
            Files.createDirectories(baselineDir);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create baseline dir " + baselineDir, e);
        }
    }

    public Optional<RunReport> load(String workloadName) {
        Path file = currentFile(workloadName);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(read(file));
    }

    /** 显式更新：先保留旧基线为 previous，再原子写入新基线。 */
    public synchronized void update(String workloadName, RunReport report) {
        Path current = currentFile(workloadName);
        Path previous = previousFile(workloadName);
        try {
            if (Files.exists(current)) {
                Files.copy(current, previous, StandardCopyOption.REPLACE_EXISTING);
            }
            writeAtomic(current, report);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to update baseline for " + workloadName, e);
        }
    }

    /** 回退到上一份基线；没有上一份时返回 false。 */
    public synchronized boolean rollback(String workloadName) {
        Path current = currentFile(workloadName);
        Path previous = previousFile(workloadName);
        if (!Files.exists(previous)) {
            return false;
        }
        try {
            // 当前基线保留为 previous，使回退本身也可再回退。
            if (Files.exists(current)) {
                Path tmp = current.resolveSibling(current.getFileName() + ".tmp");
                Files.copy(current, tmp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(previous, current, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, previous, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(previous, current, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to rollback baseline for " + workloadName, e);
        }
    }

    private Path currentFile(String workloadName) {
        return baselineDir.resolve(sanitize(workloadName) + ".baseline.json");
    }

    private Path previousFile(String workloadName) {
        return baselineDir.resolve(sanitize(workloadName) + ".baseline.previous.json");
    }

    private String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private RunReport read(Path file) {
        try {
            return objectMapper.readValue(file.toFile(), RunReport.class);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read baseline " + file, e);
        }
    }

    private void writeAtomic(Path file, RunReport report) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        objectMapper.writeValue(tmp.toFile(), report);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
