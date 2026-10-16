# Transactions and batches

Committed on normal return, rolled back on any exception, and the connection is always released.

```java
// no result
database.runTransactionAsync(tx -> {
    tx.execute("UPDATE accounts SET balance = balance - ? WHERE uuid = ?", amount, from);
    tx.execute("UPDATE accounts SET balance = balance + ? WHERE uuid = ?", amount, to);
});

// with a result
database.transactionAsync(tx -> tx.query("SELECT balance FROM accounts WHERE uuid = ?", uuid)
        .value(Long.class)
        .orElse(0L));
```

The two have different names on purpose: as overloads, an ordinary lambda like
`tx -> tx.execute(...)` matches both and fails to compile.

Keep transactions short. They hold locks, and a slow transaction on a shared database is felt by
every server on the network.

## Transaction

A `Transaction` *is* a `DatabaseConnection`: `query`, `execute`, `executeReturningKeys`, `batch`,
`jdbc()`. The surrounding machinery begins the transaction, commits on normal return, rolls back on
any exception, and always releases the connection. The callback never calls `commit()` or
`rollback()` — only `setRollbackOnly()` when it wants to abort without throwing.

| Method | Purpose |
| --- | --- |
| `isolationLevel()` | the level actually in effect |
| `setRollbackOnly()` | finish the callback, commit nothing |
| `isRollbackOnly()` | whether rollback-only was requested |
| `savepoint(name)` | named savepoint (identifier-validated) |
| `rollbackTo(name)` | undo work after that savepoint |
| `releaseSavepoint(name)` | free engine resources for it |

## Options

```java
database.transactionAsync(
        TransactionOptions.isolation(IsolationLevel.SERIALIZABLE)
                .withTimeout(Duration.ofSeconds(5)),
        tx -> {
            tx.savepoint("before_risky");
            try {
                tx.execute("...");
            } catch (DatabaseException e) {
                tx.rollbackTo("before_risky");
            }
            tx.releaseSavepoint("before_risky");

            if (somethingIsWrong) {
                tx.setRollbackOnly();
            }
            return result;
        });

database.runTransactionAsync(TransactionOptions.readOnly(), tx -> {
    tx.query("SELECT COUNT(*) FROM players");
});
```

`TransactionOptions` is immutable; every `with*` returns a copy.

| Factory | Meaning |
| --- | --- |
| `defaults()` | engine isolation, read-write, library transaction timeout |
| `isolation(level)` | `DEFAULT`, `READ_UNCOMMITTED`, `READ_COMMITTED`, `REPEATABLE_READ`, `SERIALIZABLE` |
| `readOnly()` | lets the engine optimise; writes will fail |

`SERIALIZABLE` on PostgreSQL can fail with a serialization conflict (SQLSTATE 40001). That is a
normal, retryable outcome — see retries in [Configuration](configuration.md#retries).

Transactions are **not** retried unless you opt in with `RetryPolicy.retryTransactions(true)`,
because replaying one re-runs your callback.

## Row locking

For "claim exactly one job" patterns across servers, lock inside the same transaction that does the
claim:

```java
database.transactionAsync(tx -> {
    QueryResult claimed = tx.query(Query.selectFrom("pending_rewards")
            .where("claimed", false)
            .limit(1)
            .forUpdateSkipLocked());
    Row row = claimed.firstOrNull();
    if (row == null) {
        return Optional.empty();
    }
    tx.execute("UPDATE pending_rewards SET claimed = TRUE, claimed_by = ? WHERE id = ?",
            serverId, row.getLong("id"));
    return Optional.of(row);
});
```

`forUpdate()` waits for the lock instead of skipping. Both are ignored by SQLite, which locks at
file level. H2 supports `FOR UPDATE` but not `SKIP LOCKED` — the dialect degrades to a plain lock
rather than failing to parse.

Full recipe: [Work queue](../cookbook/work-queue.md).

## Atomic updates without a transaction

When several servers increment the same counter, prefer an expression assignment over
read-modify-write:

```java
database.updateAsync(Query.update("players")
        .set("balance", Expressions.raw("balance + ?", amount))
        .where("uuid", uuid));
```

The engine applies the delta under its own row lock, so no update is lost. Combine with `@Version`
when the rest of the row is being rewritten — [Economy transfer](../cookbook/economy-transfer.md).

## Batch operations

One round trip instead of a thousand. This is the right tool for flushing a stats buffer or saving
every online player on shutdown.

```java
List<List<Object>> rows = players.stream()
        .map(p -> List.<Object>of(p.uuid(), p.name(), p.balance()))
        .toList();

database.batchInsertAsync("players", List.of("uuid", "name", "balance"), rows);

database.batchAsync("UPDATE players SET balance = ? WHERE uuid = ?", updates);
database.batchUpdateAsync("UPDATE players SET world = ? WHERE uuid = ?", worldUpdates);
```

Batches run inside a single transaction and get a longer default deadline than a single statement
(60s versus 10s), since bulk work is expected to take longer.

On the connection / transaction:

```java
tx.batch("UPDATE players SET balance = ? WHERE uuid = ?", parameterSets);
```

Multi-row `InsertQuery.columns(...).row(...)` is a different tradeoff: one large statement instead
of a JDBC batch. Use it for a handful of rows; use `batchInsertAsync` for hundreds or thousands.
