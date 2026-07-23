-- ═══════════════════════════════════════════════════════════════════
-- ONE-TIME DATA RESET — run manually via psql, NOT a Flyway migration.
-- Scoped to Zeta Earthmoving (tenant_id below) — change if you want a
-- different tenant, or remove the WHERE clauses entirely to wipe every
-- tenant (not recommended unless you mean it).
--
-- Deliberately does NOT touch: acc_accounts (Chart of Accounts),
-- acc_bank_accounts (current_balance / low_balance_threshold stay
-- exactly as they are), invoices, customers — none of those are in
-- scope per what was agreed.
-- ═══════════════════════════════════════════════════════════════════

\set tenant_id '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f'

BEGIN;

DELETE FROM acc_journal_lines
WHERE journal_entry_id IN (
    SELECT id FROM acc_journal_entries WHERE tenant_id = :'tenant_id'
);

DELETE FROM acc_journal_entries
WHERE tenant_id = :'tenant_id';

DELETE FROM acc_journal_sequences
WHERE tenant_id = :'tenant_id';

DELETE FROM acc_vat_periods
WHERE tenant_id = :'tenant_id';

DELETE FROM acc_bank_transactions
WHERE tenant_id = :'tenant_id';

-- Sanity check before committing — should all read 0
SELECT
    (SELECT COUNT(*) FROM acc_journal_entries    WHERE tenant_id = :'tenant_id') AS remaining_journal_entries,
    (SELECT COUNT(*) FROM acc_vat_periods        WHERE tenant_id = :'tenant_id') AS remaining_vat_periods,
    (SELECT COUNT(*) FROM acc_bank_transactions  WHERE tenant_id = :'tenant_id') AS remaining_bank_transactions;

COMMIT;