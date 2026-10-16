# Generated keys

Auto-increment primary keys, then read the value the database assigned.

## Schema

```java
database.executeAsync(Schema.createTable("homes")
        .ifNotExists()
        .column(ColumnDefinition.of("id", SqlType.BIGINT).autoIncrement().primaryKey())
        .column(ColumnDefinition.of("owner", SqlType.UUID).notNull())
        .column(ColumnDefinition.of("name", SqlType.VARCHAR).length(32).notNull())).join();
```

On PostgreSQL this becomes `BIGSERIAL`; on MySQL/MariaDB/H2, `BIGINT AUTO_INCREMENT`; on SQLite,
`INTEGER`.

## Insert and read the key

```java
QueryResult keys = database.insertReturningKeysAsync(Query.insertInto("homes")
                .value("owner", owner)
                .value("name", "Spawn"))
        .join();

long id = ((Number) keys.generatedKeys().get(0)).longValue();
```

`insertAsync` returns only the affected-row count. Use `insertReturningKeysAsync` when you need
the new id.

Inside a transaction:

```java
database.transactionAsync(tx -> {
    QueryResult keys = tx.executeReturningKeys(Query.insertInto("homes")
            .value("owner", owner)
            .value("name", "Spawn"));
    return ((Number) keys.generatedKeys().get(0)).longValue();
});
```

Raw SQL:

```java
tx.executeReturningKeys("INSERT INTO homes (owner, name) VALUES (?, ?)", owner, "Spawn");
```

## Entity mapping

```java
@Table("homes")
public record Home(
        @Id(generated = true) long id,
        UUID owner,
        String name) {
}
```

The generated field is omitted from inserts. After `save()`, repositories that go through
`insertReturningKeys` populate it; if you insert with the query builder yourself, read
`generatedKeys()` as above and construct the record with that id.

A zero or unset id is the usual "not yet persisted" signal. Composite generated keys are not
supported.

## Several rows

`generatedKeys()` holds one value per inserted row, in statement order, for a multi-row insert:

```java
QueryResult keys = database.insertReturningKeysAsync(Query.insertInto("homes")
                .columns("owner", "name")
                .row(owner, "Spawn")
                .row(owner, "Mine"))
        .join();
```

Driver support for multi-row generated keys varies. If you need a reliable id per row, insert
one at a time or use UUIDs as keys instead.
