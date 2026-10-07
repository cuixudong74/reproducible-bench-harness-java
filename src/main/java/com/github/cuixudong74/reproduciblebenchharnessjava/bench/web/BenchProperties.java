package com.github.cuixudong74.reproduciblebenchharnessjava.bench.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 基准框架配置项。
 *
 * @param baselineDir 基线文件存储目录
 */
@ConfigurationProperties(prefix = "bench")
public record BenchProperties(String baselineDir) {
}
