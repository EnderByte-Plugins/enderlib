package com.enderbyte.enderlib.database.core.metrics;

import com.enderbyte.enderlib.database.api.metrics.DatabaseMetrics;
import com.enderbyte.enderlib.database.api.metrics.MetricsSnapshot;
import com.enderbyte.enderlib.database.api.metrics.PoolStatistics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * In-memory metrics based on striped adders and a fixed-bucket latency histogram.
 *
 * <p>Recording costs one adder increment plus one bucket increment, both lock-free and
 * allocation-free, which is why metrics can stay on by default. Percentiles are read off the
 * histogram and are therefore approximate: precise enough to notice that queries got slower, not a
 * replacement for a real APM.
 */
public final class StandardDatabaseMetrics implements DatabaseMetrics {

    /** Upper bounds in nanoseconds: 0.1ms, 0.25ms, 0.5ms, 1ms ... 5s, then everything above. */
    private static final long[] BUCKET_BOUNDS_NANOS = {
            100_000L, 250_000L, 500_000L,
            1_000_000L, 2_500_000L, 5_000_000L,
            10_000_000L, 25_000_000L, 50_000_000L,
            100_000_000L, 250_000_000L, 500_000_000L,
            1_000_000_000L, 2_500_000_000L, 5_000_000_000L,
            Long.MAX_VALUE};

    private final LongAdder queryCount = new LongAdder();
    private final LongAdder queryErrorCount = new LongAdder();
    private final LongAdder transactionCount = new LongAdder();
    private final LongAdder rollbackCount = new LongAdder();
    private final LongAdder retryCount = new LongAdder();
    private final LongAdder timeoutCount = new LongAdder();
    private final LongAdder pendingOperations = new LongAdder();
    private final LongAdder totalQueryNanos = new LongAdder();
    private final LongAdder[] histogram = new LongAdder[BUCKET_BOUNDS_NANOS.length];
    private final AtomicLong maxQueryNanos = new AtomicLong();

    private final Supplier<PoolStatistics> poolStatistics;

    /**
     * @param poolStatistics supplier of live pool state, or {@code null} when there is no pool
     */
    public StandardDatabaseMetrics(final Supplier<PoolStatistics> poolStatistics) {
        this.poolStatistics = poolStatistics;
        for (int i = 0; i < histogram.length; i++) {
            histogram[i] = new LongAdder();
        }
    }

    @Override
    public void recordQuery(final String operation, final long nanos, final boolean success) {
        if (success) {
            queryCount.increment();
        } else {
            queryErrorCount.increment();
        }
        totalQueryNanos.add(nanos);
        histogram[bucketFor(nanos)].increment();
        maxQueryNanos.accumulateAndGet(nanos, Math::max);
    }

    @Override
    public void recordError(final String errorType) {
        queryErrorCount.increment();
    }

    @Override
    public void recordRetry() {
        retryCount.increment();
    }

    @Override
    public void recordTimeout() {
        timeoutCount.increment();
    }

    @Override
    public void recordTransaction(final boolean committed, final long nanos) {
        if (committed) {
            transactionCount.increment();
        } else {
            rollbackCount.increment();
        }
    }

    @Override
    public void operationStarted() {
        pendingOperations.increment();
    }

    @Override
    public void operationFinished() {
        pendingOperations.decrement();
    }

    @Override
    public MetricsSnapshot snapshot() {
        final long[] counts = new long[histogram.length];
        long total = 0L;
        for (int i = 0; i < histogram.length; i++) {
            counts[i] = histogram[i].sum();
            total += counts[i];
        }
        return new MetricsSnapshot(
                queryCount.sum(),
                queryErrorCount.sum(),
                transactionCount.sum(),
                rollbackCount.sum(),
                retryCount.sum(),
                timeoutCount.sum(),
                Math.max(0L, pendingOperations.sum()),
                totalQueryNanos.sum(),
                percentile(counts, total, 0.50d),
                percentile(counts, total, 0.95d),
                percentile(counts, total, 0.99d),
                maxQueryNanos.get(),
                poolStatistics == null ? PoolStatistics.UNAVAILABLE : poolStatistics.get());
    }

    @Override
    public void reset() {
        queryCount.reset();
        queryErrorCount.reset();
        transactionCount.reset();
        rollbackCount.reset();
        retryCount.reset();
        timeoutCount.reset();
        totalQueryNanos.reset();
        maxQueryNanos.set(0L);
        for (final LongAdder bucket : histogram) {
            bucket.reset();
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private static int bucketFor(final long nanos) {
        for (int i = 0; i < BUCKET_BOUNDS_NANOS.length; i++) {
            if (nanos <= BUCKET_BOUNDS_NANOS[i]) {
                return i;
            }
        }
        return BUCKET_BOUNDS_NANOS.length - 1;
    }

    private static long percentile(final long[] counts, final long total, final double percentile) {
        if (total == 0L) {
            return 0L;
        }
        final long target = (long) Math.ceil(total * percentile);
        long seen = 0L;
        for (int i = 0; i < counts.length; i++) {
            seen += counts[i];
            if (seen >= target) {
                return BUCKET_BOUNDS_NANOS[i] == Long.MAX_VALUE
                        ? BUCKET_BOUNDS_NANOS[i - 1]
                        : BUCKET_BOUNDS_NANOS[i];
            }
        }
        return BUCKET_BOUNDS_NANOS[counts.length - 2];
    }
}
