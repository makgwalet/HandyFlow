# Load tests with budgets

Two checks, both pass/fail (they exit non-zero when a budget is missed, so a script or CI notices).

## 1. Database volume check (`platform/scripts/db-volume-check.sql`)
Builds a big company in a transaction (5,000 sites, 100 branches, 10 regions, 400,000 incidents, 300 users of whom 200 are limited) plus 20 other companies as noise (400,000 incidents), times the hot queries on real PostgreSQL and rolls everything back.

    psql -v ON_ERROR_STOP=1 -d scratch -f platform/scripts/db-volume-check.sql

Use a scratch database with the full schema, as a superuser. Budgets are at the top of each `pg_temp.vol_time(...)` line, in milliseconds, worst of 5 runs. The queries are copies of the ones in the Java code (scope, limited-users overview, SLA live and report, regions); if you change those queries, regenerate or edit the copies.

## 2. HTTP load test (`platform/scripts/load/loadtest.mjs`)
Node 18+, no dependencies. Copy `load.config.example.json`, point it at a **test** system with a test user, set the password in the environment variable the config names, and run:

    HF_LOAD_PASSWORD=... node platform/scripts/load/loadtest.mjs --config load.config.json --confirm staging.example.com --out report.json

- `--confirm` must equal the host (and port) of `baseUrl`. Without it the test refuses to run, so it cannot be aimed at production by accident.
- Stages ramp the number of virtual users up or down and hold for the given seconds. Scenarios are picked by weight.
- Budgets: `p95Ms`, `p99Ms`, `maxErrorRate`, `minRps`, overall and per scenario. A test with no budgets is refused; a run with no requests fails.
- Exit 0 = all budgets met, 1 = a budget missed, 2 = could not run (bad config, sign-in failed, target not confirmed).
- Tests for the script: `node --test platform/scripts/load/loadtest.test.mjs`.
