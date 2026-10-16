package com.enderbyte.enderlib.database.api.migration;

import java.time.Instant;

/**
 * A row of the migration history table.
 *
 * @param version         migration version
 * @param description     human description
 * @param checksum        checksum recorded when it was applied
 * @param appliedAt       when it ran
 * @param executionMillis how long it took
 * @param success         whether it completed; a failed entry blocks further migration until repaired
 * @param appliedBy       identifier of the server that applied it, for multi-server debugging
 */
public record AppliedMigration(int version,
                               String description,
                               String checksum,
                               Instant appliedAt,
                               long executionMillis,
                               boolean success,
                               String appliedBy) {
}
