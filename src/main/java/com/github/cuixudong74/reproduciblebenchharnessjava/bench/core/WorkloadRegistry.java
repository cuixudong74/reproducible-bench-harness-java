package com.github.cuixudong74.reproduciblebenchharnessjava.bench.core;

import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.Workload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作负载注册表：按名称索引所有 {@link Workload} 实现 Bean。
 * 框架通过该注册表解析被测对象，不与具体实现绑定。
 */
@Component
public class WorkloadRegistry {

    private final Map<String, Workload> workloads;

    public WorkloadRegistry(List<Workload> implementations) {
        this.workloads = implementations.stream()
                .collect(Collectors.toUnmodifiableMap(Workload::name, Function.identity()));
    }

    public Workload require(String name) {
        Workload w = workloads.get(name);
        if (w == null) {
            throw new IllegalArgumentException("unknown workload: " + name
                    + ", available: " + workloads.keySet());
        }
        return w;
    }
}
