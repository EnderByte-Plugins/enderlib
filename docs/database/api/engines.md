# Engines

| Engine | Notes |
| --- | --- |
| **PostgreSQL** | The recommended default for networks. Native `UUID` and `JSONB`, `ON CONFLICT` upserts, transactional DDL, batch inserts rewritten into multi-row statements. |
| **MySQL 8+** | Server-side prepared statements and caching are enabled. `ON DUPLICATE KEY UPDATE` upserts. **DDL commits implicitly**, so a failed migration cannot be rolled back. |
| **MariaDB 10.4+** | As MySQL, with its own driver and `CREATE INDEX IF NOT EXISTS` support. |
| **SQLite** | Single server only. WAL mode and a busy timeout are set; the pool is forced to one connection. |
| **H2** | Best for tests and small embedded setups. Native `UUID`; upserts render as `MERGE ... KEY`. |

UUIDs work everywhere regardless of storage: native `UUID`, `CHAR(36)` or `BINARY(16)` are all read
and written transparently.

`SqlType` in the schema helpers is engine-neutral — `SqlType.UUID` becomes the native type on
PostgreSQL and H2, `CHAR(36)` on MySQL, and `TEXT` on SQLite.

## Upserts

You write `onConflictUpdate` / `onConflictUpdateAll` once:

| Engine | Rendered as |
| --- | --- |
| PostgreSQL, SQLite | `ON CONFLICT (...) DO UPDATE SET ...` |
| MySQL, MariaDB | `ON DUPLICATE KEY UPDATE ...` (unique key is inferred; `conflictColumns` is ignored) |
| H2 | `MERGE INTO ... KEY (...)` (overwrites every listed column) |

## Row locks

`FOR UPDATE` / `FOR UPDATE SKIP LOCKED` are ignored by SQLite (file-level locking). H2 supports
`FOR UPDATE` but not `SKIP LOCKED` — the dialect degrades to a plain lock rather than failing to
parse.

## Pagination

`LIMIT` / `OFFSET` are inlined as integers the builder already validated. MySQL, MariaDB and
SQLite require a `LIMIT` when you set `OFFSET`.

## Choosing one

- **Network of servers:** PostgreSQL.
- **One server, existing MySQL:** MySQL 8+ or MariaDB 10.4+.
- **One server, no external database:** SQLite. Never share the file.
- **Tests:** H2 in-memory (`database("mem:name;DB_CLOSE_DELAY=-1")`).
