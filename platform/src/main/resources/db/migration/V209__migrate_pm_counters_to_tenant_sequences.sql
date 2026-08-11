-- Migrate existing PM counter values into tenant_number_sequences so
-- Projects' SequenceService can be re-based onto TenantSequenceService
-- without resetting any tenant's project/task/change-order/snag numbering
-- back to 1 (which would produce colliding numbers against records that
-- already exist).
--
-- Safe to run more than once: the ON CONFLICT clause takes the GREATEST of
-- the two values, so a re-run never moves a counter backwards even if new
-- pm_counters activity happened between deploys.
--
-- pm_counters and tenant_number_sequences are structurally compatible for
-- this purpose: (tenant_id, some-string-key, a monotonically-increasing
-- integer value) in both. counter_type's existing values ("PROJECT",
-- "TASK:<uuid>", "CO:<uuid>", "SNAG:<uuid>", "PHASE:<uuid>", "BUDGET:<uuid>")
-- become sequence_name values as-is — no reformatting needed, since
-- TenantSequenceService's sequence_name column is just as free-form.

INSERT INTO tenant_number_sequences (tenant_id, sequence_name, last_value, updated_at)
SELECT tenant_id, counter_type, current_value, now()
FROM pm_counters
ON CONFLICT (tenant_id, sequence_name)
DO UPDATE SET
    last_value = GREATEST(tenant_number_sequences.last_value, EXCLUDED.last_value),
    updated_at = now();

-- pm_counters is deliberately NOT dropped by this migration. Keep it as a
-- rollback reference until SequenceService has been running on
-- TenantSequenceService in production for a full deploy cycle with no
-- issues, then drop it in a separate, later migration:
--
--   DROP TABLE pm_counters;
--
-- Dropping it in the same migration as the code cutover removes the one
-- artifact that would let you diagnose a discrepancy if something about
-- this migration's assumptions turns out to be wrong.