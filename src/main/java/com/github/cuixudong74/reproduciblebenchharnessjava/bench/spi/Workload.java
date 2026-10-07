package com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi;

/**
 * 被测对象统一接入契约。框架仅依赖本接口，不与任何具体业务实现绑定。
 *
 * 实现要求：
 * - 对相同的 {@link SampleInput} 必须返回确定性的 {@link SampleResult}；
 * - 实现必须线程安全，会被并发调用；
 * - 抛出的任何异常都会被框架隔离并计为失败样本。
 */
public interface Workload {

    /** 工作负载唯一名称，用于在运行配置中选择被测对象。 */
    String name();

    /** 处理一个样本。 */
    SampleResult execute(SampleInput input) throws Exception;

    /** 一次运行前的初始化（可选）。 */
    default void setUp(BenchContext context) throws Exception {
    }

    /** 一次运行后的清理（可选），不允许残留后台线程。 */
    default void tearDown() throws Exception {
    }
}
