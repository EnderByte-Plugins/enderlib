# Leaderboards

Top-N queries, joins and aggregates. Always `LIMIT` a leaderboard — never `findAll()` a players
table.

## Top balances

```java
@Table("players")
public record PlayerData(
        @Id UUID uuid,
        String name,
        long balance,
        @Column("last_seen") Instant lastSeen) {
}

List<PlayerData> top = database.queryAsync(Query.select("uuid", "name", "balance")
                .from("players")
                .where("balance", Operator.GREATER_THAN, 0)
                .orderBy("balance", SortDirection.DESCENDING)
                .limit(10), PlayerData.class)
        .join();
```

Pagination for a `/baltop 2` command:

```java
int page = 2;
int pageSize = 10;
database.queryAsync(Query.selectFrom("players")
        .orderBy("balance", SortDirection.DESCENDING)
        .limit(pageSize)
        .offset((page - 1) * pageSize), PlayerData.class);
```

`offset` requires a `limit` on MySQL, MariaDB and SQLite.

## Distinct names, counted rows

```java
long players = database.queryAsync(Query.selectCount("players"))
        .join()
        .value(Long.class)
        .orElse(0L);

database.queryAsync(Query.select("world").distinct().from("players"));
```

## Join + aggregate

Kills per player, only those above a threshold:

```java
Query.select(
                Expressions.column("p.name"),
                Expressions.sum("s.kills").as("kills"))
        .from("players", "p")
        .join(JoinType.LEFT, "stats", "s", raw("s.uuid = p.uuid"))
        .groupBy("p.name")
        .having(raw("SUM(s.kills) > ?", 100))
        .orderBy(Expressions.column("kills"), SortDirection.DESCENDING)
        .limit(10);
```

`Conditions.raw` is required for `SUM(s.kills)`: the condition factories validate their first
argument as a column identifier and will reject an expression.

Read the unaliased aggregate with a positional getter, or alias it:

```java
database.queryAsync(Query.select(Expressions.count()).from("players"))
        .thenAccept(result -> {
            long count = ((Number) result.firstOrNull().get(0)).longValue();
        });
```

## Subquery filter

Players who have at least one ban:

```java
Query.selectFrom("players")
        .whereExists(Query.selectFrom("bans")
                .where(Conditions.columns("bans.uuid", Operator.EQUALS, "players.uuid"))
                .where("active", true));
```

Or `IN`:

```java
Query.selectFrom("players")
        .whereIn("uuid", Query.select("uuid").from("bans").where("active", true));
```

## Repository helper

```java
public CompletableFuture<List<PlayerData>> richest(int limit) {
    return database().queryAsync(Query.selectFrom(table())
            .orderBy("balance", SortDirection.DESCENDING)
            .limit(limit), entityType());
}
```

See [Queries](../api/queries.md) for the full builder surface.
