package com.enderbyte.enderlib.database.api.migration;

import java.time.Duration;
import java.util.List;

/**
 * The outcome of a migration run.
 *
 * @param applied        migrations applied by this run, in order
 * @param initialVersion schema version before the run, or {@code 0} for an empty database
 * @param currentVersion schema version after the run
 * @param duration       total wall-clock time
 * @param skipped        {@code true} when another server held the migration lock and had already
 *                       brought the schema up to date
 */
public record MigrationResult(List<AppliedMigration> applied,
                              int initialVersion,
                              int currentVersion,
                              Duration duration,
                              boolean skipped) {

    public MigrationResult {
        applied = List.copyOf(applied);
    }

    /** @return how many migrations this run applied */
    public int appliedCount() {
        return applied.size();
    }

    /** @return {@code true} when the schema was already current */
    public boolean isUpToDate() {
        return applied.isEmpty();
    }
}
