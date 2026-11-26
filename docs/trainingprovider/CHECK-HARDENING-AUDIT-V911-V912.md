# Training Provider: V911/V912 CHECK-hardening audit (patch 0162)

Status: audit only. This patch adds no constraint and changes no data. The constraints below are proposed for patch 0163, after you have run the audit script and decided what to do with any rows it finds.

## What was inspected

`V911__trainingprovider_accreditation.sql` (authorities, delivery sites, accreditations, accreditation events, accreditation scopes) and `V912__trainingprovider_catalogue.sql` (qualifications, qualification versions, programmes, programme versions, modules, programme offerings, and the `programme_id` column added to scopes). I also read `AccreditationRules` and `TrainProvAccreditation` to see what the service already enforces, so the database is only asked to enforce what the service already promises.

## The main finding: the existing CHECKs let a missing value through

In SQL, a CHECK passes when its condition is true **or unknown**. Every length test in V911/V912 is written as `char_length(btrim(x)) >= N`. When `x` is NULL the test is unknown, so a condition such as `status = 'RETIRED' AND char_length(btrim(retired_reason)) >= 10 AND retired_by IS NOT NULL ...` does not reject a retired row that has no reason, as long as the other fields are set.

I confirmed this on a scratch PostgreSQL 16 with rows that the service would never write but the database accepted:

- an authority and a delivery site retired with no reason,
- a conditional approval with no conditions,
- an accreditation with a verified date and verifier but no source,
- an authority named with a single space.

The fix is the same one used in every table since V913: `COALESCE(char_length(btrim(x)), 0) >= N`.

## Proposed constraints (for 0163, not applied)

| Id | Table(s) | Proposed rule | Why it is not a duplicate |
|----|----------|---------------|---------------------------|
| C1 | authorities, delivery_sites, qualifications, programmes, accreditation_scopes, programme_offerings | Replace the retired/withdrawn CHECK with a NULL-safe one: RETIRED or WITHDRAWN needs `COALESCE(char_length(btrim(reason)),0) >= 10` | Existing CHECK passes a NULL reason |
| C2 | accreditations | `status <> 'CONDITIONAL_APPROVAL' OR COALESCE(char_length(btrim(conditions)),0) >= 10` | Existing CHECK passes NULL conditions |
| C3 | accreditations | verification fields all NULL, or all set with a source of 5+ characters (NULL-safe) | Existing CHECK passes a NULL source |
| C4 | accreditations | APPROVED or CONDITIONAL_APPROVAL needs `last_verified_on` and `verified_by` | Not enforced today. The service always records verification with an approval |
| C5 | qualification_versions | `status = 'DRAFT'` or a source note of 5+ characters (NULL-safe) | Existing CHECK passes a NULL source note |
| C6 | authorities, sites, accreditations, scopes, qualifications, programmes, modules | Names, titles and codes of at least 2 characters after trimming | Only NOT NULL today, so a space is accepted |
| C7 | accreditations, qualifications | `accreditation_number`, `official_ref` and `saqa_id` are NULL or non-blank | A blank string is accepted and blocks the unique indexes |
| C8 | programme_versions, accreditation_events | An override reason, when present, is 10+ characters | Not enforced today |
| C9 | accreditations, programme_offerings | `submitted_on <= approval_date` (enforced by V930); a withdrawal is not before `offered_from` (audit only: it needs `timestamptz::date`, which is not immutable, so it cannot be a CHECK) | Not enforced today |
| C10 | accreditations | Status REJECTED, SUSPENDED, WITHDRAWN, MORE_INFORMATION_REQUIRED or EXPIRED needs a reason of 10+ characters | The service requires it, the database does not |

Not duplicated, because they are already correct: the status and type enumerations, NQF level 1–10, credits and hours not negative, the date-order checks on accreditations, scopes and qualification registration, the approved-has-number rule, the live-unique indexes, `ck_trainprov_prog_accredited_has_qual`, and `ck_trainprov_progver_approved`.

## Decisions for a person (not proposed as CHECKs)

1. **Cross-tenant references (C11).** Foreign keys here point at a row's id only. Nothing in the database stops a row in one tenant from pointing at another tenant's authority, site or programme. A CHECK cannot fix this. It needs composite foreign keys on `(tenant_id, id)` plus a unique index on `(tenant_id, id)` in each referenced table. That is a wider change touching many tables, so it is a separate decision. The audit script reports how many rows already cross tenants.
2. **Approved programme versions and `effective_from` (C12).** An approved version without an effective date may be intended. I have not proposed a rule. The audit counts them.
3. **Accreditation events are described as immutable, but nothing enforces it.** A trigger that rejects UPDATE and DELETE on `trainprov_accreditation_events` would close that. It is a trigger, not a CHECK. I'd add it with the constraints if you agree.

## How existing rows are handled

- Nothing is deleted, rewritten or defaulted. Where a row would break a proposed constraint, 0163 will add that constraint `NOT VALID` only if you ask for it, so old rows stay as they are while new and changed rows are checked. Otherwise the constraint is held back until a person has corrected the rows.
- The audit script lists every violating row by check, table and a sample of ids. Each correction is made by a person through the screens, with a reason, or by a reviewed script you approve.
- For each check the remediation report is that script's output: table, rule, count, sample ids.

## How to run the audit

`platform/src/main/resources/db/audit/trainprov_v911_v912_check_audit.sql` is a single SELECT. It is not a Flyway migration and lives outside `db/migration`.

```
psql -h <host> -U <read-only user> -d <database> -f platform/src/main/resources/db/audit/trainprov_v911_v912_check_audit.sql
```

An empty result means no existing row breaks any proposed constraint, and all of them can be added as written. Each row of output is one check with its count. Send me that output and I will build 0163 (done, see below) around it.

## Checks done for this patch

- The script runs without error on a PostgreSQL 16 with all migrations applied, and returns no rows on clean data.
- With deliberately invalid rows inserted in a transaction that was rolled back, the script reported C1, C2, C3, C4 and C6 as expected.
- The Flyway numbers are unchanged (no migration is added).

## 0163: what V930 does

`V930__trainprov_check_hardening.sql` adds C1 to C10 as NULL-safe CHECK constraints (23 constraints; six replace the weak V911/V912 versions, the rest are new). Every one is added `NOT VALID`:

- New rows and changed rows are checked from the moment the migration runs.
- Existing rows are not examined, rewritten, defaulted or deleted, so the migration cannot fail on old data.
- A legacy row that already breaks a constraint cannot be updated until it is corrected. This is deliberate: nothing is fixed silently.
- After you have reviewed the audit output and corrected any violating rows, run `db/audit/trainprov_v911_v912_validate_constraints.sql` one statement at a time. A statement that finds a bad row fails and changes nothing.

Left out because they still need your decision: cross-tenant composite foreign keys, the `effective_from` rule for approved programme versions, the accreditation-events immutability trigger. The withdrawn-before-offered half of C9 stays audit-only (see the table).

Tested on PostgreSQL 16: with violators seeded under the old rules, V930 applies, existing rows are untouched, each new violation is rejected, VALIDATE fails while violators exist and succeeds on a constraint with none.
