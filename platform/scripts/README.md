# Schema audits

Two checks for a class of bug that keeps producing HTTP 500 "An unexpected error occurred"
from a perfectly valid user action: **the database schema disagrees with the code that writes
to it.** Both were written after such bugs were found while testing the UI (see V303).

| Script | Needs | Finds |
|---|---|---|
| `audit_enum_checks.py` | Python 3 only (reads source + migrations) | A Java enum constant that the column's `CHECK (col IN (...))` would reject |
| `audit_wrong_fk_targets.sql` | a migrated PostgreSQL database | An audit column ("who did this") whose foreign key targets the wrong table |

```bash
python3 platform/scripts/audit_enum_checks.py                 # exit 1 if anything is found
psql -d handyflow -f platform/scripts/audit_wrong_fk_targets.sql   # prints nothing when clean
```

## What they found (fixed in V303)

**Wrong foreign key targets.** Seven Security audit columns were declared
`REFERENCES security_guards(id)`, but the code stores the logged-in *staff user's* id in them, so
PostgreSQL rejected every insert or update:

`security_shift_swap_requests.decided_by`, `security_device_sessions.forced_close_by`,
`security_guard_screening_records.created_by`, `security_alarm_events.triaged_by`,
`security_dispatches.dispatched_by`, `security_principal_vetting.created_by`,
`security_declined_principals.declined_by`.

HR had the same bug and was fixed in V52; Security never got the same treatment.
Correctly left alone: `security_resource_custody.witnessed_by` (a second guard's id),
`clinic_lab_results.reviewed_by` (the controller resolves the practitioner),
`acc_workpaper_files.superseded_by` (points at a file).

**Status CHECKs that never learned a value.**
`ShiftStatus.PULLED` (the "Pull From Site" action) vs `chk_shift_status`, and
`InvoiceStatus.OVERPAID` (a payment larger than the total) vs `chk_invoices_status`.

## Limits (read before trusting an all-clear)

* `audit_enum_checks.py` is static text analysis. It resolves enum types with Java scoping rules
  and takes the *last* `CHECK ... IN (...)` list defined per column, but it does not understand
  Postgres enum types, `ALTER TYPE`, differently shaped CHECKs, or a CHECK dropped without
  replacement. A finding means "verify this", not "proven". It skips fields whose enum type it
  cannot resolve and says so.
* `audit_wrong_fk_targets.sql` only looks at columns named like `*_by`, `approver*`, `reviewer*`
  or `actor*`. Whether a flagged column is a bug depends on what the code writes into it, so
  every row needs a human decision. Known-correct exceptions are listed in the script.
* Neither replaces an integration test that performs the real action against a real database.

## How V303 was verified

On a PostgreSQL 16 database built by applying this project's own migrations in order (the five
`*_test_data_zeta` seed scripts fail on a blank database, which is expected), a harness created
real parent rows and attempted each write. Before V303 the seven foreign-key writes, `PULLED`
and `OVERPAID` were all rejected; after V303 all were accepted. Controls confirmed that the
foreign keys and CHECKs that should remain (guard ids, unknown statuses) still reject bad
values, that HR still accepts a user id (V52), and that running V303 a second time is a no-op.
The harness is not committed because it depends on a local scratch database.
