# 可复现性能基准测试框架

本框架为服务提供可复现的性能基准能力：相同配置与相同随机种子下重复运行，
每个样本的处理结果与全部统计指标完全一致；一次运行可固化为回归基线，
后续运行自动比对并标记超出容差的回归。

## 1. 被测对象接入契约

被测对象实现 `bench.spi.Workload` 接口并注册为 Spring Bean，框架通过
`WorkloadRegistry` 按名称解析，不与任何具体业务实现绑定：

```java
public interface Workload {
    String name();                                        // 唯一名称
    SampleResult execute(SampleInput input) throws Exception;
    default void setUp(BenchContext context) {}           // 可选
    default void tearDown() {}                            // 可选，不得残留后台线程
}
```

- `SampleInput(index, payload)`：`payload` 由框架根据 `(seed, index, inputSize)`
  确定性生成（SHA-256 逐块展开），与调度顺序、执行线程、运行时刻无关。
- `SampleResult(outputDigest, costNanos)`：
  - `outputDigest`：处理输出的确定性摘要，用于跨运行比对处理结果；
  - `costNanos`：**逻辑耗时（纳秒）**，即可比对延迟指标的时间来源。

实现要求：相同输入必须返回相同结果；实现必须线程安全；抛出的异常会被框架
隔离并计为失败样本。

## 2. 指标口径（固定）

| 指标 | 字段 | 口径 |
|---|---|---|
| 成功计数 | `successCount` | 测量阶段成功样本数 |
| 失败计数 | `failureCount` | 测量阶段失败样本数（异常 `EXCEPTION:*` 与超时 `TIMEOUT` 均计入） |
| 吞吐 | `throughputPerSecond` | 逻辑吞吐 = `successCount / ΣcostNanos × 10^9` |
| 延迟均值 | `meanCostNanos` | 成功样本 `costNanos` 的算术平均 |
| 延迟分位数 | `p50/p90/p99CostNanos` | 成功样本 `costNanos` 的分位数 |

**分位数算法**：升序排序后线性插值，`rank = q × (n − 1)`，在 `floor(rank)`
与 `ceil(rank)` 两个相邻有序值之间按小数部分插值（与 numpy 默认
`method='linear'`、Excel `PERCENTILE.INC` 一致）；`n = 0` 时返回 0。

**时间来源**：所有可比对延迟指标均取自被测对象返回的**逻辑耗时**
`costNanos`，不读取墙钟（`System.nanoTime` / 当前时刻）。可比对结果
（`RunReport`）中不包含运行时刻、进程标识、主机名等易变值。

**吞吐口径**：吞吐同样由逻辑耗时推导，因此也是确定性的。

## 3. 配置项（`BenchConfig`）

| 字段 | 含义 | 约束 |
|---|---|---|
| `workloadName` | 被测工作负载名称 | 非空，须已注册 |
| `inputSize` | 每样本输入字节数 | > 0 |
| `concurrency` | 并发度，1 为串行 | > 0 |
| `warmupIterations` | 预热样本数（不计入统计） | ≥ 0 |
| `measurementIterations` | 测量样本数（计入统计） | > 0 |
| `seed` | 随机种子 | 任意长整型 |
| `sampleTimeoutMillis` | 单样本超时，超时计失败 | > 0 |
| `tolerancePercent` | 回归判定容差（百分比） | ≥ 0 |

服务级配置：`bench.baseline-dir` —— 基线文件存储目录。

## 4. 可复现性保证

- 输入仅由 `(seed, index, inputSize)` 决定；
- 每个样本的结局写入按 `index` 定位的槽位，聚合前按 `index` 升序排序，
  线程完成顺序不影响结果；
- 统计指标仅由确定性逻辑耗时推导；
- 因此相同配置 + 相同种子下，并发与串行、不同时间点运行的可比对结果
  完全一致（由 `DeterminismTest`、`ConcurrentAggregationTest` 守护）。

## 5. 基线与回归判定

- **固化**：`POST /bench/baseline/update` 运行一次并把结果固化为基线
  （JSON 文件，位于 `bench.baseline-dir`）。
- **自动比对**：`POST /bench/run` 正常运行，若存在基线则自动比对。
  正常运行**绝不改写**基线。
- **回归判定**：延迟类指标与失败计数**上升**超过 `tolerancePercent`，
  或吞吐、成功计数**下降**超过容差，即标记回归；比对结果中逐项给出
  指标名、基线值、当前值与变化幅度（`RegressionFinding`）。
- **受控更新**：显式更新时先把当前基线保留为
  `<name>.baseline.previous.json`，再原子写入新基线。
- **回退**：`POST /bench/baseline/{workloadName}/rollback` 恢复上一份
  基线（回退本身也可再回退）。

## 6. REST 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/bench/run` | 运行一次基准并自动比对基线（不写基线） |
| POST | `/bench/baseline/update` | 运行并显式固化为新基线（保留旧基线） |
| POST | `/bench/baseline/{name}/rollback` | 回退到上一份基线 |
| GET | `/bench/baseline/{name}` | 查看当前基线 |

## 7. 异常隔离与资源管理

- 单样本抛异常 → 计为 `EXCEPTION:<类型>` 失败；超时 → 计为 `TIMEOUT`
  失败并取消任务；均不影响其余样本，工作负载不中断、样本不丢失。
- 线程池随每次运行创建、运行结束即销毁（`shutdown` + `awaitTermination`），
  工作线程为 daemon 线程，运行结束后不残留后台线程，重复运行不累积资源。
