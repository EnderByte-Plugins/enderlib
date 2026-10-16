package com.enderbyte.enderlib.database.core.metrics;

import com.enderbyte.enderlib.database.api.metrics.DatabaseMetrics;
import com.enderbyte.enderlib.database.api.metrics.MetricsSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * Publishes the library's counters into a Micrometer registry, while still answering
 * {@link #snapshot()} from the in-memory implementation.
 *
 * <p>Micrometer is an <em>optional</em> dependency: this class is only loaded when a plugin
 * explicitly constructs it, so servers that do not use Micrometer never need the jar on the
 * classpath.
 *
 * <pre>{@code
 * MeterRegistry registry = ...;
 * DatabaseMetrics metrics = new MicrometerMetricsBridge(registry, "players", delegate);
 * }</pre>
 */
public final class MicrometerMetricsBridge implements DatabaseMetrics {

    private final DatabaseMetrics delegate;
    private final Timer queryTimer;
    private final Timer transactionTimer;
    private final Counter errorCounter;
    private final Counter retryCounter;
    private final Counter timeoutCounter;
    private final Counter rollbackCounter;

    /**
     * @param registry     the Micrometer registry to publish into
     * @param databaseName database name, added as a {@code database} tag
     * @param delegate     the in-memory implementation that answers {@link #snapshot()}
     */
    public MicrometerMetricsBridge(final MeterRegistry registry,
                                   final String databaseName,
                                   final DatabaseMetrics delegate) {
        this.delegate = delegate;
        this.queryTimer = Timer.builder("enderdb.query")
                .tag("database", databaseName)
                .publishPercentileHistogram()
                .register(registry);
        this.transactionTimer = Timer.builder("enderdb.transaction")
                .tag("database", databaseName)
                .register(registry);
        this.errorCounter = Counter.builder("enderdb.errors")
                .tag("database", databaseName)
                .register(registry);
        this.retryCounter = Counter.builder("enderdb.retries")
                .tag("database", databaseName)
                .register(registry);
        this.timeoutCounter = Counter.builder("enderdb.timeouts")
                .tag("database", databaseName)
                .register(registry);
        this.rollbackCounter = Counter.builder("enderdb.rollbacks")
                .tag("database", databaseName)
                .register(registry);
        registry.gauge("enderdb.pool.active", this, bridge -> bridge.snapshot().pool().activeConnections());
        registry.gauge("enderdb.pool.pending", this, bridge -> bridge.snapshot().pool().pendingThreads());
        registry.gauge("enderdb.operations.pending", this, bridge -> bridge.snapshot().pendingOperations());
    }

    @Override
    public void recordQuery(final String operation, final long nanos, final boolean success) {
        delegate.recordQuery(operation, nanos, success);
        queryTimer.record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordError(final String errorType) {
        delegate.recordError(errorType);
        errorCounter.increment();
    }

    @Override
    public void recordRetry() {
        delegate.recordRetry();
        retryCounter.increment();
    }

    @Override
    public void recordTimeout() {
        delegate.recordTimeout();
        timeoutCounter.increment();
    }

    @Override
    public void recordTransaction(final boolean committed, final long nanos) {
        delegate.recordTransaction(committed, nanos);
        transactionTimer.record(nanos, TimeUnit.NANOSECONDS);
        if (!committed) {
            rollbackCounter.increment();
        }
    }

    @Override
    public void operationStarted() {
        delegate.operationStarted();
    }

    @Override
    public void operationFinished() {
        delegate.operationFinished();
    }

    @Override
    public MetricsSnapshot snapshot() {
        return delegate.snapshot();
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
