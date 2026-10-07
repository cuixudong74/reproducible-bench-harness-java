# Reproducible Benchmark Harness

这套能力为服务中的任意业务链路提供统一的性能基准契约、确定性统计、自动基线比对和受控基线更新。框架只依赖 `BenchmarkWorkload` 接口，不依赖任何具体业务实现。

## 1. 接入契约

业务代码实现两个泛型方法：

```java
public final class MyWorkload implements BenchmarkWorkload<MyInput, String> {
    @Override
    public String id() {
        return "my-workload";
    }

    @Override
    public MyInput createInput(long seed, int sampleIndex, int inputSize) {
        // 只允许由 seed、sampleIndex、inputSize 等配置生成输入。
        return MyInput.deterministic(seed, sampleIndex, inputSize);
    }

    @Override
    public String execute(MyInput input, ExecutionClock clock) {
        // 用逻辑时钟记录确定性耗时，不调用 System.nanoTime()/currentTimeMillis()。
        clock.advanceNanos(input.costNanos());
        return input.expectedOutput();
    }
}
```

要求：

- `createInput` 在相同 `seed`、样本序号和 `inputSize` 下必须生成等价输入。
- `execute` 的分支、输出、异常、超时和 `clock.advanceNanos(...)` 必须由输入与固定配置决定。
- 输出的 `toString()` 必须稳定；结果中保存其 UTF-8 SHA-256，而不是对象内存地址。
- workload 需要支持多个工作线程安全并发调用；不得依赖实例上的可变共享计数器。
- 可响应中断。若 workload 忽略逻辑超时且永久阻塞，框架会用防御性的 wall-clock 超时取消该样本。

## 2. 配置项

`BenchmarkConfiguration` 字段：

| 字段 | 含义 |
| --- | --- |
| `workloadName` | 稳定 workload ID，也是基线文件名的逻辑键 |
| `inputSize` | 业务输入规模，必须为正数 |
| `concurrency` | 工作线程数；1 表示串行 |
| `warmupIterations` | 预热次数；预热结果不进入统计 |
| `measurementIterations` | 测量样本数；从 0 开始连续编号 |
| `seed` | 固定随机种子 |
| `timeoutNanos` | 单样本逻辑超时 |
| `quantiles` | 已排序的分位点，范围 `[0,1]`，默认 p50、p90、p95、p99 |
| `tolerance` | 基线回归容差 |

`RegressionTolerance`：

- `relativeTolerance`：相对容差，默认 `0.05`（5%）。
- `absoluteLatencyNanos`：延迟绝对容差，默认 1000 ns。
- `absoluteThroughput`：吞吐绝对容差，默认 0 samples/s。

应用配置：

```properties
benchmark.baseline.directory=/path/to/baselines
```

默认目录为 `${user.dir}/target/benchmark-baselines`。

## 3. 时间来源与指标口径

为保证同一结果可在不同时间、不同进程和不同调度状态下比较，默认时间来源不是宿主机 wall clock，而是：

```text
deterministic-logical-clock:java.lang.Long.advanceNanos
```

每个样本从 0 ns 开始，`ExecutionClock.nanoTime()` 只能通过 `advanceNanos` 单调递增。因此延迟不会受到 CPU 频率、操作系统调度、线程启动顺序或当前时间影响。

延迟分位数算法固定为 **nearest-rank（最近秩，无插值）**：

1. 复制延迟数组并排序，不修改原始样本顺序。
2. 对分位点 `p` 和样本数 `N`，秩为 `ceil(p * N)`，最小为 1，最大为 `N`。
3. 返回排序后数组的第 rank 个值。

成功、业务异常和逻辑超时样本都属于“完成尝试”，都进入延迟分位数。零延迟在吞吐分母中按 1 ns 计，避免除零；分位数仍保留原始零值。

结果包含：

- `throughputSamplesPerSecond`：`N / sum(每样本逻辑耗时秒数)`。该口径基于服务时间总和，不基于并发运行墙钟，因此同种子下串行与并发得到相同吞吐。
- `totalSamples`、`successCount`、`failureCount`。
- `latency.count/min/max/sum/average` 和所有配置的分位数。
- 每个样本的状态、超时标记、延迟、输出 SHA-256 和异常类型。
- `fingerprint`：由 schema 版本、固定配置、逐样本结果和延迟统计生成的 SHA-256。

结果和基线禁止保存运行时刻、主机名、进程 ID、线程名、异常消息或堆栈。日志中的这些信息不属于可比较结果。

## 4. 基线比对与回归判定

正常调用 `BenchmarkService.run(...)` 是只读操作：执行基准、读取当前基线并返回 `BenchmarkExecution`，不会改写基线文件。

- 没有基线：状态为 `NO_BASELINE`。
- 指纹一致：状态为 `PASSED`。
- 指纹不同：按固定指标列表比较并返回每项的基线值、当前值、绝对差值和回归标记。

回归规则：

- 吞吐下降同时超过绝对和相对容差。
- 延迟 min/max/sum/average 或分位数上升同时超过绝对和相对容差。
- 成功数下降或失败数上升。
- 逐样本成功状态、超时标记、输出哈希、异常类型发生变化。
- workload 名、输入规模、种子、预热/测量次数、超时或分位点等可比配置不一致。

容差内的数值移动仍会作为非回归差异展示，但状态保持 `PASSED`。

## 5. 基线更新、保留与回退

只有显式调用才会覆盖基线：

```java
BenchmarkResult newBaseline = service.captureBaseline(workload, configuration);
```

存储文件：

- `<workload>.baseline.json`：当前基线。
- `<workload>.baseline.previous.json`：上一份基线。

更新时先把当前基线复制到 previous，再通过临时文件和原子 move 替换当前基线。回退：

```java
BenchmarkResult restored = service.rollbackBaseline("my-workload");
```

回退把 previous 恢复为 current，但保留 previous 文件，因此可反复恢复到同一上一份基线。

## 6. 并发、异常和资源语义

- 输入在执行前预生成；测量结果按测量样本序号排序后聚合。
- 聚合不依赖 `Future` 完成顺序，也不使用共享累加器。
- `concurrency` 不进入结果指纹；同种子、同其他配置下并发与串行的样本和统计必须相同。
- `execute` 抛出的任何 `Exception` 都被转换为失败样本，只记录异常的实际类型。
- `clock.advanceNanos` 超过 `timeoutNanos` 时抛出内部逻辑超时，结果中标准化为 `java.util.concurrent.TimeoutException`。
- 单个样本失败不会中断其他样本，也不会从结果中丢弃样本。
- 每次运行创建独立固定线程池，`finally` 中 `shutdownNow()` 并等待结束；线程为 daemon，正常结束后不残留 worker。

## 7. 已覆盖的自动化测试

- 同种子重复运行的逐样本输出、异常、延迟、统计值和指纹完全一致。
- 串行与并发在相同种子下的结果、指纹、吞吐和延迟统计一致。
- 业务异常与超时被计数且没有样本丢失。
- 重复并发运行后不残留 benchmark worker 线程。
- 正常运行不改写基线。
- 显式更新保留上一份基线，回退可恢复。
- 超容差吞吐和延迟回归被明确标记并给出变化幅度。
- 容差内变化通过；逐样本输出哈希漂移被标记为回归。
