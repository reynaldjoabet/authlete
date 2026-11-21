# pg_plan_advice: Pinning Query Plans in PostgreSQL 19

PostgreSQL 19 can pin a query to a chosen plan without changing application SQL. Two modules ship
with it, and both are off by default:

| Module            | What it does                                                    | How to enable                                       |
| ----------------- | --------------------------------------------------------------- | --------------------------------------------------- |
| `pg_plan_advice`  | Generates and applies plan advice in a small hint language      | `LOAD`, or a preload setting (not `CREATE EXTENSION`) |
| `pg_stash_advice` | Stores advice per query identifier and applies it automatically | `CREATE EXTENSION pg_stash_advice`                  |

Every example here ran in the official `postgres:19` Docker image (PostgreSQL 19.0) on two test
tables of 100,000 rows each.

## Enabling it

`pg_plan_advice` is a loadable module with no extension control file. `CREATE EXTENSION` fails:

```
ERROR:  extension "pg_plan_advice" is not available
```

Load it in a session, or list it in `session_preload_libraries` or `shared_preload_libraries`:

```sql
LOAD 'pg_plan_advice';
```

Compared with the other ways to load a module:

| Method                      | When it loads       | Who gets it                                    | Change takes effect |
| --------------------------- | ------------------- | ---------------------------------------------- | ------------------- |
| `shared_preload_libraries`  | Server start        | Every session and background process           | Restart             |
| `session_preload_libraries` | Each new connection | New sessions (can be set per role or database) | New connections     |
| `LOAD 'module'`             | When you run it     | That session only                              | Immediately         |

Loading it adds five settings, each settable per session:

| Setting                                         | Default | Purpose                                   |
| ----------------------------------------------- | ------- | ----------------------------------------- |
| `pg_plan_advice.advice`                         | (empty) | The advice string to apply                |
| `pg_plan_advice.always_explain_supplied_advice` | `on`    | Show supplied advice in `EXPLAIN` output  |
| `pg_plan_advice.always_store_advice_details`    | `off`   | Not tested                                |
| `pg_plan_advice.feedback_warnings`              | `off`   | Warn when supplied advice is not applied  |
| `pg_plan_advice.trace_mask`                     | `off`   | Not tested                                |

Availability depends on the install. Both modules are in the official Docker image. Distro packages
usually ship them in the server or `contrib` package. Managed services (RDS, Cloud SQL and others)
allow only modules they have approved.

## Generating advice

Ask `EXPLAIN` to write advice for the plan the planner chose:

```sql
EXPLAIN (PLAN_ADVICE) SELECT * FROM a JOIN b ON b.a_id = a.id WHERE a.v = 7;
```

```
 Hash Join ...
 Generated Plan Advice:
   JOIN_ORDER(b a)
   HASH_JOIN(a)
   SEQ_SCAN(b a)
   NO_GATHER(a b)
```

Each hint names the tables it applies to. The hints seen in testing:

| Hint                                      | Controls                              |
| ----------------------------------------- | ------------------------------------- |
| `JOIN_ORDER(x y)`                         | The order tables are joined in        |
| `HASH_JOIN(x)`, `NESTED_LOOP_PLAIN(x)`    | The join method for a table           |
| `SEQ_SCAN(x)`, `INDEX_SCAN(x index_name)` | How a table is scanned                |
| `NO_GATHER(x y)`                          | No parallel query for those tables    |

The usual workflow is to capture the advice while the plan is good, then edit it rather than write
it from scratch.

## Applying advice in a session

Set the advice string, and the planner follows it for matching queries:

```sql
SET pg_plan_advice.advice = 'JOIN_ORDER(a b) NESTED_LOOP_PLAIN(b) INDEX_SCAN(b b_a_id_idx)';
EXPLAIN SELECT * FROM a JOIN b ON b.a_id = a.id WHERE a.v = 7;
```

```
 Nested Loop
   ->  Seq Scan on a
   ->  Index Scan using b_a_id_idx on b
 Supplied Plan Advice:
   INDEX_SCAN(b b_a_id_idx) /* matched */
   JOIN_ORDER(a b) /* matched */
   NESTED_LOOP_PLAIN(b) /* matched */
```

`EXPLAIN` marks each hint `matched` or `not matched`. Advice that names something not in the query
is ignored quietly by default. Turn on warnings to catch that:

```sql
SET pg_plan_advice.feedback_warnings = on;
SET pg_plan_advice.advice = 'HASH_JOIN(zzz)';   -- no table zzz
```

```
WARNING:  supplied plan advice was not enforced
DETAIL:  advice HASH_JOIN(zzz) feedback is "not matched"
```

Clear it with `RESET pg_plan_advice.advice;`.

## Storing advice with pg_stash_advice

`pg_stash_advice` matches stored advice to queries by query identifier, so the application's SQL
stays unchanged.

1. Create the extension and find the query's identifier:

   ```sql
   CREATE EXTENSION pg_stash_advice;
   SET compute_query_id = on;
   EXPLAIN (VERBOSE) SELECT * FROM a JOIN b ON b.a_id = a.id WHERE a.v = 7;
   -- Query Identifier: 4276626657652774220
   ```

2. Create a stash and store advice for that identifier:

   ```sql
   SELECT pg_create_advice_stash('prod');
   SELECT pg_set_stashed_advice('prod', 4276626657652774220,
          'JOIN_ORDER(a b) NESTED_LOOP_PLAIN(b) INDEX_SCAN(b b_a_id_idx)');
   ```

3. In sessions that should use it, load the module and pick the stash:

   ```sql
   LOAD 'pg_stash_advice';
   SET pg_stash_advice.stash_name = 'prod';
   ```

With that, the query ran with the stored plan and no other change. The same query with `v = 42`
used it too: the query identifier ignores constants, so one entry covers every parameter value.
That helps when one plan suits all values, and is a risk when different values need different
plans.

| Function                                        | Purpose                              |
| ----------------------------------------------- | ------------------------------------ |
| `pg_create_advice_stash(name)`                  | Create a stash                       |
| `pg_set_stashed_advice(name, query_id, advice)` | Store advice for a query             |
| `pg_get_advice_stashes()`                       | List stashes                         |
| `pg_get_advice_stash_contents(name)`            | List a stash's entries               |
| `pg_drop_advice_stash(name)`                    | Drop a stash                         |
| `pg_start_stash_advice_worker()`                | Start the worker that saves stashes  |

## Production setup and when to use it

This setup follows from the tests above but was not itself tested:

- **Loading:** add both modules to `shared_preload_libraries` so every session has them without a
  `LOAD`. Setting it replaces the whole list, so keep any modules already in it:

  ```sql
  ALTER SYSTEM SET shared_preload_libraries = 'pg_stat_statements,pg_plan_advice,pg_stash_advice';
  -- then restart the server
  SHOW shared_preload_libraries;
  ```
- **Query IDs:** set `compute_query_id` to `on` or `auto`.
- **Default stash:** set it per role or database, for example
  `ALTER ROLE app SET pg_stash_advice.stash_name = 'prod';`.
- **Persistence:** a background worker saves stashes every `pg_stash_advice.persist_interval`
  seconds (default 30). Whether stashes survive a restart is untested.

Use advice as a targeted fix for one query whose plan got worse as its data changed. Fix statistics
and indexes first. Review stored advice after upgrades and schema changes:
`INDEX_SCAN(b b_a_id_idx)` stops matching if that index is renamed or dropped.

## Approved for production

Modules and extensions that are safe for routine production use. Anything not listed here is
niche, for diagnosis only, or obsolete; check before enabling it.

| Name                                | Type                                | Use it for                                                                | Notes                                                                                            |
| ----------------------------------- | ----------------------------------- | ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `pg_stat_statements`                | Extension, needs preload            | Finding your most expensive queries                                       | Most widely used; enable it everywhere                                                           |
| `auto_explain`                      | Module                              | Logging plans of slow queries                                             | Set `auto_explain.log_min_duration`. `log_analyze` adds overhead to every query, so sample it     |
| `pgcrypto`                          | Extension                           | Hashing, encryption, random bytes                                         | For UUIDs, use the built-in `gen_random_uuid()` or `uuidv7()` instead                            |
| `pg_trgm`                           | Extension                           | Fast `LIKE '%x%'` and fuzzy search via GIN/GiST indexes                   |                                                                                                  |
| `citext`                            | Extension                           | Case-insensitive text, such as emails                                     | A nondeterministic collation is the modern alternative                                           |
| `btree_gist`                        | Extension                           | Exclusion constraints mixing scalars and ranges (no overlapping bookings) |                                                                                                  |
| `btree_gin`                         | Extension                           | Multicolumn GIN indexes that include scalar columns                       |                                                                                                  |
| `unaccent`                          | Extension                           | Accent-insensitive search                                                 |                                                                                                  |
| `fuzzystrmatch`                     | Extension                           | Levenshtein distance, Soundex                                             |                                                                                                  |
| `ltree`                             | Extension                           | Tree paths such as categories or org charts                               |                                                                                                  |
| `hstore`                            | Extension                           | Key/value pairs                                                           | Prefer `jsonb` in new designs                                                                    |
| `postgres_fdw`                      | Extension                           | Querying other PostgreSQL servers                                         | Better than `dblink`: pushes down filters, joins and aggregates                                  |
| `pg_buffercache`                    | Extension                           | Seeing what's in shared memory                                            | Light enough to run on a live server                                                             |
| `pg_prewarm`                        | Extension (preload for autoprewarm) | Warm cache after a restart                                                | The autoprewarm worker restores the cache automatically                                          |
| `amcheck`                           | Extension                           | Detecting index and table corruption                                      | Run it on a schedule or before upgrades                                                          |
| `auth_delay`                        | Module, needs preload               | Slowing down password guessing                                            |                                                                                                  |
| `pg_plan_advice`, `pg_stash_advice` | Module, extension                   | Pinning one query's plan                                                  | New in 19; use as a targeted fix                                                                 |
| `plpgsql`                           | Extension                           | Stored procedures                                                         | Installed by default                                                                             |

## Built-in extensions in PostgreSQL 19

The `postgres:19` image ships these extensions, installed per database with `CREATE EXTENSION`.
List what your own server offers with:

```sql
SELECT name, default_version, installed_version, comment
FROM pg_available_extensions ORDER BY name;
```

### Performance and monitoring

| Extension            | Version | Description                                               |
| -------------------- | ------- | --------------------------------------------------------- |
| `pg_stat_statements` | 1.13    | Planning and execution statistics for every SQL statement |
| `pg_stash_advice`    | 1.0     | Stores plan advice and applies it automatically           |
| `pg_buffercache`     | 1.7     | Examine the shared buffer cache                           |
| `pg_prewarm`         | 1.2     | Load table data into the cache ahead of use               |
| `pgstattuple`        | 1.5     | Row-level statistics, such as bloat                       |
| `pgrowlocks`         | 1.2     | Show row-level locks                                      |

### Diagnostics and repair

| Extension           | Version | Description                                          |
| ------------------- | ------- | ---------------------------------------------------- |
| `amcheck`           | 1.5     | Verify table and index integrity                     |
| `pageinspect`       | 1.13    | Inspect database pages at a low level                |
| `pg_freespacemap`   | 1.3     | Examine the free space map                           |
| `pg_visibility`     | 1.2     | Examine the visibility map and page-level visibility |
| `pg_walinspect`     | 1.1     | Inspect the write-ahead log                          |
| `pg_logicalinspect` | 1.0     | Inspect logical decoding components                  |
| `pg_surgery`        | 1.0     | Repair a damaged table                               |

### Data types

| Extension       | Version | Description                                          |
| --------------- | ------- | ---------------------------------------------------- |
| `citext`        | 1.8     | Case-insensitive strings                             |
| `hstore`        | 1.8     | Sets of key/value pairs                              |
| `ltree`         | 1.3     | Hierarchical tree-like paths                         |
| `cube`          | 1.5     | Multidimensional cubes                               |
| `seg`           | 1.4     | Line segments or floating-point intervals            |
| `isn`           | 1.3     | Product numbering standards (ISBN, EAN, UPC)         |
| `intarray`      | 1.5     | Functions, operators and indexing for integer arrays |
| `earthdistance` | 1.2     | Great-circle distances on the Earth                  |

### Indexing

| Extension    | Version | Description                                |
| ------------ | ------- | ------------------------------------------ |
| `btree_gin`  | 1.4     | Index common data types with GIN           |
| `btree_gist` | 1.9     | Index common data types with GiST          |
| `bloom`      | 1.0     | Bloom-filter index access method           |
| `pg_trgm`    | 1.6     | Trigram text similarity and indexed search |

### Text search and strings

| Extension       | Version | Description                                           |
| --------------- | ------- | ----------------------------------------------------- |
| `unaccent`      | 1.1     | Text search dictionary that removes accents           |
| `fuzzystrmatch` | 1.2     | String similarity and distance (Levenshtein, Soundex) |
| `dict_int`      | 1.0     | Text search dictionary template for integers          |
| `dict_xsyn`     | 1.0     | Text search dictionary template for extended synonyms |

### External data

| Extension      | Version | Description                                        |
| -------------- | ------- | -------------------------------------------------- |
| `postgres_fdw` | 1.3     | Foreign-data wrapper for remote PostgreSQL servers |
| `file_fdw`     | 1.0     | Foreign-data wrapper for flat files                |
| `dblink`       | 1.2     | Connect to other PostgreSQL databases              |

### Security and crypto

| Extension   | Version | Description                                    |
| ----------- | ------- | ---------------------------------------------- |
| `pgcrypto`  | 1.4     | Cryptographic functions                        |
| `sslinfo`   | 1.2     | Information about the client's SSL certificate |
| `uuid-ossp` | 1.1     | Generate UUIDs                                 |
