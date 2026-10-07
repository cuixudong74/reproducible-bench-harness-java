package com.github.cuixudong74.reproduciblebenchharnessjava.bench.core;

import java.util.List;

/**
 * 一次运行的完整可比对结果。仅包含确定性内容：
 * 配置回显、逐样本结局（按 index 升序）与统计指标。
 */
public record RunReport(
        BenchConfig config,
        Metrics metrics,
        List<SampleOutcome> outcomes) {
}
