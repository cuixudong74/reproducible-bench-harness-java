package com.github.cuixudong74.reproduciblebenchharnessjava.bench.demo;

import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.InputGenerator;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.SampleInput;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.SampleResult;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.Workload;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;

/**
 * 演示用工作负载：对输入做确定性哈希。
 * 仅作为框架的示例接入方，框架本身不依赖它。
 *
 * 逻辑耗时由输入内容确定性推导（1µs ~ 2µs 区间），模拟"处理成本"，
 * 保证相同输入永远得到相同的成本读数。
 */
@Component
public class HashWorkload implements Workload {

    public static final String NAME = "hash";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SampleResult execute(SampleInput input) {
        String digest = InputGenerator.sha256Hex(input.payload());
        long costNanos = 1_000L + (long) (Math.floorMod(
                ByteBuffer.wrap(input.payload(), 0, 8).getLong(), 1_000L));
        return new SampleResult(digest, costNanos);
    }
}
