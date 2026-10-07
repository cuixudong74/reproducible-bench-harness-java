package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.engine;

import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.config.BenchmarkConfiguration;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.contract.BenchmarkWorkload;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.contract.ExecutionClock;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.BenchmarkResult;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.LatencyMetrics;
import com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.model.SampleOutcome;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Executes configured workloads without knowing business implementations. */
@Component
public class BenchmarkEngine {
    /** Defensive wall-clock margin above the logical timeout, used only to stop hung samples. */
    private static final long WALL_CLOCK_TIMEOUT_MARGIN_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);

    public <I, O> BenchmarkResult run(BenchmarkWorkload<I, O> workload,
                                      BenchmarkConfiguration configuration) {
        Objects.requireNonNull(workload, "workload");
        Objects.requireNonNull(configuration, "configuration");
        if (!workload.id().equals(configuration.workloadName())) {
            throw new IllegalArgumentException("workload id does not match configuration");
        }

        int total = configuration.totalIterations();
        List<SamplePlan<I>> plans = createPlans(workload, configuration, total);
        runWarmups(workload, configuration, plans);

        List<SamplePlan<I>> measured = plans.stream()
                .filter(SamplePlan::measured)
                .toList();
        List<SampleOutcome> outcomes = configuration.concurrency() == 1
                ? runSerial(workload, configuration, measured)
                : runConcurrent(workload, configuration, measured);
        return buildResult(configuration, outcomes);
    }

    private static <I> List<SamplePlan<I>> createPlans(
            BenchmarkWorkload<I, ?> workload,
            BenchmarkConfiguration configuration,
            int total) {
        List<SamplePlan<I>> plans = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            long sampleSeed = configuration.seed() + index;
            I input = workload.createInput(sampleSeed, index, configuration.inputSize());
            int measuredIndex = index - configuration.warmupIterations();
            plans.add(new SamplePlan<>(index, measuredIndex, index >= configuration.warmupIterations(), input));
        }
        return plans;
    }

    private static <I, O> void runWarmups(
            BenchmarkWorkload<I, O> workload,
            BenchmarkConfiguration configuration,
            List<SamplePlan<I>> plans) {
        for (SamplePlan<I> plan : plans) {
            if (!plan.measured()) {
                executeSample(workload, plan.input(), plan.generationIndex(), configuration);
            }
        }
    }

    private static <I, O> List<SampleOutcome> runSerial(
            BenchmarkWorkload<I, O> workload,
            BenchmarkConfiguration configuration,
            List<SamplePlan<I>> plans) {
        List<SampleOutcome> outcomes = new ArrayList<>(plans.size());
        for (SamplePlan<I> plan : plans) {
            outcomes.add(executeSample(workload, plan.input(),
                    plan.measuredIndex(), configuration));
        }
        return outcomes;
    }

    private <I, O> List<SampleOutcome> runConcurrent(
            BenchmarkWorkload<I, O> workload,
            BenchmarkConfiguration configuration,
            List<SamplePlan<I>> plans) {
        ExecutorService executor = Executors.newFixedThreadPool(
                configuration.concurrency(),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "benchmark-worker-" + configuration.workloadName());
                    thread.setDaemon(true);
                    return thread;
                });
        List<Future<SampleOutcome>> futures = new ArrayList<>(plans.size());
        try {
            for (SamplePlan<I> plan : plans) {
                futures.add(executor.submit(() ->
                        executeSample(workload, plan.input(), plan.measuredIndex(), configuration)));
            }
            return collectOutcomes(futures, plans, configuration);
        } finally {
            executor.shutdownNow();
            awaitTermination(executor);
        }
    }

    private static List<SampleOutcome> collectOutcomes(
            List<Future<SampleOutcome>> futures,
            List<? extends SamplePlan<?>> plans,
            BenchmarkConfiguration configuration) {
        List<SampleOutcome> outcomes = new ArrayList<>(futures.size());
        for (int index = 0; index < futures.size(); index++) {
            Future<SampleOutcome> future = futures.get(index);
            try {
                outcomes.add(future.get(
                        configuration.timeoutNanos() + WALL_CLOCK_TIMEOUT_MARGIN_NANOS,
                        TimeUnit.NANOSECONDS));
            } catch (TimeoutException e) {
                future.cancel(true);
                outcomes.add(SampleOutcome.failure(plans.get(index).measuredIndex(),
                        configuration.timeoutNanos(), true, TimeoutException.class.getName()));
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                outcomes.add(SampleOutcome.failure(plans.get(index).measuredIndex(),
                        configuration.timeoutNanos(), false, cause.getClass().getName()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                futures.forEach(openFuture -> openFuture.cancel(true));
                throw new IllegalStateException("benchmark execution interrupted", e);
            }
        }
        return outcomes;
    }

    private static void awaitTermination(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(1L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("benchmark executor did not terminate");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while stopping benchmark executor", e);
        }
    }

    private static <I, O> SampleOutcome executeSample(
            BenchmarkWorkload<I, O> workload,
            I input,
            int index,
            BenchmarkConfiguration configuration) {
        TimeoutTrackingClock clock = new TimeoutTrackingClock(configuration.timeoutNanos());
        try {
            O output = workload.execute(input, clock);
            if (clock.timedOut()) {
                return SampleOutcome.failure(index, configuration.timeoutNanos(),
                        true, TimeoutException.class.getName());
            }
            return SampleOutcome.success(index, clock.elapsedNanos(),
                    ResultHasher.outputHash(output));
        } catch (DeterministicTimeoutException e) {
            return SampleOutcome.failure(index, configuration.timeoutNanos(),
                    true, TimeoutException.class.getName());
        } catch (Exception e) {
            if (clock.timedOut()) {
                return SampleOutcome.failure(index, configuration.timeoutNanos(),
                        true, TimeoutException.class.getName());
            }
            return SampleOutcome.failure(index, clock.elapsedNanos(),
                    false, e.getClass().getName());
        }
    }

    private static BenchmarkResult buildResult(
            BenchmarkConfiguration configuration, List<SampleOutcome> outcomes) {
        outcomes.sort(Comparator.comparingInt(SampleOutcome::sampleIndex));
        int success = 0;
        int failure = 0;
        long[] latencies = new long[outcomes.size()];
        long denominator = 0L;
        for (int index = 0; index < outcomes.size(); index++) {
            SampleOutcome outcome = outcomes.get(index);
            if (outcome.success()) {
                success++;
            } else {
                failure++;
            }
            latencies[index] = outcome.latencyNanos();
            denominator = Math.addExact(denominator, Math.max(1L, outcome.latencyNanos()));
        }
        Map<Double, Long> quantileValues =
                Quantiles.nearestRanks(latencies, configuration.quantiles());
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        long sum = 0L;
        for (long latency : latencies) {
            min = Math.min(min, latency);
            max = Math.max(max, latency);
            sum = Math.addExact(sum, latency);
        }
        LatencyMetrics latencyMetrics = new LatencyMetrics(
                latencies.length, min, max, sum, (double) sum / latencies.length, quantileValues);
        String fingerprint = ResultHasher.sha256(
                canonicalResultText(configuration, outcomes, latencyMetrics));
        return new BenchmarkResult(
                BenchmarkResult.CURRENT_SCHEMA_VERSION,
                BenchmarkResult.DETERMINISTIC_LOGICAL_TIME_SOURCE,
                configuration,
                outcomes.size(),
                success,
                failure,
                (double) outcomes.size() * 1_000_000_000.0d / denominator,
                denominator,
                latencyMetrics,
                outcomes,
                fingerprint);
    }

    private static String canonicalResultText(
            BenchmarkConfiguration configuration,
            List<SampleOutcome> outcomes,
            LatencyMetrics latencyMetrics) {
        StringBuilder builder = new StringBuilder();
        builder.append("v=").append(BenchmarkResult.CURRENT_SCHEMA_VERSION)
                .append("|workload=").append(configuration.workloadName())
                .append("|inputSize=").append(configuration.inputSize())
                .append("|seed=").append(configuration.seed())
                .append("|warmup=").append(configuration.warmupIterations())
                .append("|measurements=").append(configuration.measurementIterations())
                .append("|timeout=").append(configuration.timeoutNanos())
                .append("|quantiles=");
        for (double quantile : configuration.quantiles()) {
            builder.append(quantile).append(',');
        }
        builder.append("|samples=");
        for (SampleOutcome outcome : outcomes) {
            builder.append(outcome.sampleIndex()).append(':')
                    .append(outcome.success() ? 'S' : 'F').append(':')
                    .append(outcome.timeout() ? 'T' : 'N').append(':')
                    .append(outcome.latencyNanos()).append(':')
                    .append(outcome.outputHash()).append(':')
                    .append(outcome.exceptionType()).append(';');
        }
        builder.append("|latency=")
                .append(latencyMetrics.count()).append(',')
                .append(latencyMetrics.minNanos()).append(',')
                .append(latencyMetrics.maxNanos()).append(',')
                .append(latencyMetrics.sumNanos()).append(',');
        new LinkedHashMap<>(latencyMetrics.quantileNanos())
                .forEach((quantile, value) ->
                        builder.append(quantile).append('=').append(value).append(','));
        return builder.toString();
    }

    private record SamplePlan<I>(
            int generationIndex, int measuredIndex, boolean measured, I input) {
    }

    private static final class TimeoutTrackingClock implements ExecutionClock {
        private final long timeoutNanos;
        private long currentNanos;
        private boolean timedOut;

        private TimeoutTrackingClock(long timeoutNanos) {
            this.timeoutNanos = timeoutNanos;
        }

        @Override
        public long nanoTime() {
            return currentNanos;
        }

        @Override
        public void advanceNanos(long deltaNanos) {
            if (deltaNanos < 0L) {
                throw new IllegalArgumentException("clock cannot move backwards");
            }
            long updated = currentNanos + deltaNanos;
            if (updated < currentNanos) {
                throw new ArithmeticException("logical clock overflow");
            }
            currentNanos = updated;
            if (currentNanos > timeoutNanos) {
                timedOut = true;
                throw new DeterministicTimeoutException();
            }
        }

        private long elapsedNanos() {
            return currentNanos;
        }

        private boolean timedOut() {
            return timedOut;
        }
    }
}
