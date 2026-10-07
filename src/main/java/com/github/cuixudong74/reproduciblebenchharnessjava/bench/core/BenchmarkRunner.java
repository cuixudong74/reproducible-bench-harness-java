package com.github.cuixudong74.reproduciblebenchharnessjava.bench.core;

import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.BenchContext;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.SampleInput;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.SampleResult;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.spi.Workload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基准运行器。
 *
 * 可复现性设计：
 * - 每个样本的输入仅由 (seed, index) 决定，与调度顺序无关；
 * - 每个样本的结局写入按 index 定位的槽位，聚合前按 index 升序排序，
 *   线程完成顺序不影响结果；
 * - 统计指标仅由确定性的逻辑耗时推导，不读取墙钟；
 * - 并发度只影响执行快慢，不影响任何可比对字段，因此并发与串行结论一致。
 *
 * 资源管理：线程池随运行创建、运行结束即销毁，不残留后台线程。
 */
@Component
public class BenchmarkRunner {

    public RunReport run(Workload workload, BenchConfig config) {
        ExecutorService pool = newPool(config.concurrency());
        try {
            workload.setUp(new BenchContext(config.seed(), config.inputSize(),
                    config.warmupIterations(), config.measurementIterations()));
            try {
                warmup(workload, config, pool);
                List<SampleOutcome> outcomes = measure(workload, config, pool);
                return new RunReport(config, aggregate(outcomes), outcomes);
            } finally {
                try {
                    workload.tearDown();
                } catch (Exception e) {
                    throw new IllegalStateException("workload tearDown failed", e);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("benchmark run failed", e);
        } finally {
            shutdownAndAwait(pool);
        }
    }

    private void warmup(Workload workload, BenchConfig config, ExecutorService pool) {
        // 预热样本使用负数下标空间，与测量样本互不影响；结果直接丢弃。
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < config.warmupIterations(); i++) {
            long index = -1L - i;
            futures.add(pool.submit(() -> {
                try {
                    workload.execute(new SampleInput(index,
                            InputGenerator.payload(config.seed(), index, config.inputSize())));
                } catch (Throwable ignored) {
                    // 预热阶段失败不影响测量
                }
            }));
        }
        for (Future<?> f : futures) {
            try {
                f.get(config.sampleTimeoutMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted during warmup", e);
            } catch (ExecutionException | TimeoutException ignored) {
                // 预热阶段失败/超时不影响测量
            }
        }
    }

    private List<SampleOutcome> measure(Workload workload, BenchConfig config, ExecutorService pool) {
        int n = config.measurementIterations();
        @SuppressWarnings("unchecked")
        Future<SampleOutcome>[] futures = new Future[n];
        for (int i = 0; i < n; i++) {
            final long index = i;
            futures[i] = pool.submit(() -> executeOne(workload, config, index));
        }
        List<SampleOutcome> outcomes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            outcomes.add(await(futures[i], i, config.sampleTimeoutMillis()));
        }
        // 按 index 升序固定聚合顺序，消除完成顺序带来的任何差异。
        outcomes.sort(Comparator.comparingLong(SampleOutcome::index));
        return outcomes;
    }

    private SampleOutcome executeOne(Workload workload, BenchConfig config, long index) {
        try {
            SampleResult result = workload.execute(new SampleInput(index,
                    InputGenerator.payload(config.seed(), index, config.inputSize())));
            return SampleOutcome.ok(index, result.outputDigest(), result.costNanos());
        } catch (Throwable t) {
            // 异常隔离：单个样本失败计为失败，不影响其余样本。
            return SampleOutcome.failed(index, "EXCEPTION:" + t.getClass().getSimpleName());
        }
    }

    private SampleOutcome await(Future<SampleOutcome> future, long index, long timeoutMillis) {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return SampleOutcome.failed(index, "TIMEOUT");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return SampleOutcome.failed(index, "INTERRUPTED");
        } catch (ExecutionException e) {
            // executeOne 已兜底，理论上不会到达；防御性处理。
            return SampleOutcome.failed(index, "EXCEPTION:" + e.getCause().getClass().getSimpleName());
        }
    }

    private Metrics aggregate(List<SampleOutcome> outcomes) {
        long failures = outcomes.stream().filter(o -> !o.success()).count();
        long[] costs = outcomes.stream().filter(SampleOutcome::success)
                .mapToLong(SampleOutcome::costNanos).sorted().toArray();
        long success = costs.length;
        double total = 0.0;
        for (long c : costs) {
            total += c;
        }
        double mean = success == 0 ? 0.0 : total / success;
        double throughput = total <= 0.0 ? 0.0 : success * 1_000_000_000.0 / total;
        return new Metrics(
                success,
                failures,
                throughput,
                mean,
                Percentiles.percentile(costs, 0.50),
                Percentiles.percentile(costs, 0.90),
                Percentiles.percentile(costs, 0.99));
    }

    private ExecutorService newPool(int concurrency) {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "bench-worker-" + seq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        return Executors.newFixedThreadPool(concurrency, factory);
    }

    private void shutdownAndAwait(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("benchmark worker pool did not terminate");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }
}
