package com.enderbyte.enderlib.scheduler;

import com.enderbyte.enderlib.core.Terminable;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * A scheduled unit of work. Closing a task cancels it.
 */
public final class Task implements Terminable {

    /** Returned when a task could not be scheduled (for example, its entity was already removed). */
    public static final Task NONE = new Task(null);

    private final ScheduledTask handle;

    Task(ScheduledTask handle) {
        this.handle = handle;
    }

    public void cancel() {
        if (handle != null) {
            handle.cancel();
        }
    }

    public boolean isCancelled() {
        return handle == null || handle.isCancelled();
    }

    /** Whether the task is scheduled and has not finished or been cancelled. */
    public boolean isActive() {
        if (handle == null) {
            return false;
        }
        ScheduledTask.ExecutionState state = handle.getExecutionState();
        return state == ScheduledTask.ExecutionState.IDLE || state == ScheduledTask.ExecutionState.RUNNING;
    }

    @Override
    public void close() {
        cancel();
    }

    @Override
    public boolean isClosed() {
        return !isActive();
    }
}
