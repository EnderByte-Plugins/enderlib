# Work queue

Claim exactly one job across a network of servers. The database is the lock.

## Schema

```sql
CREATE TABLE pending_rewards (
    id         BIGINT PRIMARY KEY,
    player     UUID NOT NULL,
    payload    VARCHAR(128) NOT NULL,
    claimed    BOOLEAN NOT NULL DEFAULT FALSE,
    claimed_by VARCHAR(64)
);
```

## Claim inside a transaction

`FOR UPDATE SKIP LOCKED` only has meaning inside the same transaction that then marks the row
claimed. Selecting for update and updating on a *different* connection races.

```java
public CompletableFuture<Optional<RewardJob>> claimNext(String serverId) {
    return database.transactionAsync(tx -> {
        QueryResult result = tx.query(Query.selectFrom("pending_rewards")
                .where("claimed", false)
                .orderBy("id")
                .limit(1)
                .forUpdateSkipLocked());
        Row row = result.firstOrNull();
        if (row == null) {
            return Optional.empty();
        }
        long id = row.getLong("id");
        tx.execute(Query.update("pending_rewards")
                .set("claimed", true)
                .set("claimed_by", serverId)
                .where("id", id));
        return Optional.of(new RewardJob(
                id, row.getUuid("player"), row.getString("payload")));
    });
}

public record RewardJob(long id, UUID player, String payload) {
}
```

Two servers calling `claimNext` at once: one gets the row, the other skips it and takes the next
unlocked row (or nothing). Without `SKIP LOCKED`, the second server waits for the lock instead.

`forUpdate()` waits. Use it when you would rather serialise than skip.

## Engine notes

| Engine | Behaviour |
| --- | --- |
| PostgreSQL, MySQL 8+, MariaDB | `FOR UPDATE SKIP LOCKED` as requested |
| H2 | Degrades to `FOR UPDATE` (no skip) |
| SQLite | Ignored — SQLite locks the whole file |

Never share a SQLite file between servers. Use PostgreSQL for this pattern on a network.

## After the claim

Do the work (give the item, send the message) on the server thread, then either leave the row
claimed or delete it:

```java
claimNext(serverName).thenAccept(job -> {
    if (job.isEmpty()) {
        return;
    }
    Bukkit.getScheduler().runTask(plugin, () -> deliver(job.get()));
});
```

If delivery can fail, keep a `attempts` column and un-claim on failure rather than deleting
up-front:

```java
tx.execute(Query.update("pending_rewards")
        .set("claimed", false)
        .set("claimed_by", null)
        .set("attempts", Expressions.raw("attempts + 1"))
        .where("id", id));
```

Keep the transaction short: lock, mark, commit. Do not call Bukkit from inside the callback.

See [Transactions](../api/transactions.md#row-locking).
