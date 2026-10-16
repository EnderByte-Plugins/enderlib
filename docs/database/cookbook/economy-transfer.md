# Economy transfer

Move coins from one account to another without losing updates when two servers write the same
player.

## Two tools

1. **Atomic increment** when you only change a counter. The engine applies the delta under its own
   row lock.
2. **`@Version`** when you rewrite the rest of the row. The loser of a race gets
   `OptimisticLockException` instead of silently discarding the other's write.

Use (1) for `balance += amount`. Use (2) when you also persist inventory, stats, or anything else
read-modify-written.

## Atomic increment

```java
public CompletableFuture<Integer> credit(UUID uuid, long amount) {
    return database.updateAsync(Query.update("players")
            .set("balance", Expressions.raw("balance + ?", amount))
            .where("uuid", uuid));
}
```

A transfer that must not overdraw:

```java
public CompletableFuture<Boolean> transfer(UUID from, UUID to, long amount) {
    return database.transactionAsync(tx -> {
        int debit = tx.execute(Query.update("players")
                .set("balance", Expressions.raw("balance - ?", amount))
                .where("uuid", from)
                .and("balance", Operator.GREATER_THAN, amount - 1));
        if (debit == 0) {
            tx.setRollbackOnly();
            return false;
        }
        tx.execute(Query.update("players")
                .set("balance", Expressions.raw("balance + ?", amount))
                .where("uuid", to));
        return true;
    });
}
```

The `AND balance > amount - 1` clause is the overdraft check. Zero rows updated means not enough
funds; `setRollbackOnly()` discards the empty debit rather than applying a credit without one.

Equivalent with raw SQL inside the same transaction:

```java
database.runTransactionAsync(tx -> {
    tx.execute("UPDATE accounts SET balance = balance - ? WHERE uuid = ?", amount, from);
    tx.execute("UPDATE accounts SET balance = balance + ? WHERE uuid = ?", amount, to);
});
```

## Optimistic locking

```java
@Table("players")
public class VersionedPlayer {

    @Id
    private UUID uuid;
    private String name;
    private long balance;
    @Version
    private long version;

    VersionedPlayer() {
    }

    public VersionedPlayer(UUID uuid, String name, long balance, long version) {
        this.uuid = uuid;
        this.name = name;
        this.balance = balance;
        this.version = version;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public long balance() { return balance; }
    public long version() { return version; }

    public VersionedPlayer withBalance(long balance) {
        return new VersionedPlayer(uuid, name, balance, version);
    }
}
```

```java
public CompletableFuture<Void> addBalance(UUID uuid, long amount) {
    CrudRepository<VersionedPlayer, UUID> players =
            database.repository(VersionedPlayer.class, UUID.class);
    return players.findById(uuid).thenCompose(found -> {
        VersionedPlayer player = found.orElseThrow();
        return players.save(player.withBalance(player.balance() + amount));
    }).exceptionally(error -> {
        if (error.getCause() instanceof OptimisticLockException) {
            return addBalance(uuid, amount).join(); // re-read and retry; not on the server thread
        }
        throw new CompletionException(error);
    });
}
```

Do not `.join()` that retry on the server thread. Chain another `thenCompose` instead:

```java
public CompletableFuture<Void> addBalanceRetry(UUID uuid, long amount) {
    CrudRepository<VersionedPlayer, UUID> players =
            database.repository(VersionedPlayer.class, UUID.class);
    return attempt(players, uuid, amount, 3);
}

private CompletableFuture<Void> attempt(CrudRepository<VersionedPlayer, UUID> players,
                                        UUID uuid, long amount, int remaining) {
    return players.findById(uuid).thenCompose(found ->
            players.save(found.orElseThrow().withBalance(found.get().balance() + amount))
    ).handle((ok, error) -> {
        if (error == null) {
            return CompletableFuture.<Void>completedFuture(null);
        }
        if (remaining > 1 && error.getCause() instanceof OptimisticLockException) {
            return attempt(players, uuid, amount, remaining - 1);
        }
        return CompletableFuture.<Void>failedFuture(error);
    }).thenCompose(next -> next);
}
```

Prefer the atomic increment when the only mutation is the counter. Optimistic locking is for the
case where two servers each have a full entity in memory and both call `save()`.
