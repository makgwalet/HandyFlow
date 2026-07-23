-- ═══════════════════════════════════════════════════════════════════
-- ONE-TIME CORRECTIVE FIX — run manually via psql.
-- seed-accounting-data.sql computed balance_after starting from zero
-- instead of the account's real starting balance (R324,244.30) — this
-- was wrong on both the 5 transaction rows themselves AND the parent
-- account's current_balance, which the seed script never touched at all.
-- Keyed on (bank_account_id, transaction_date, reference) — unique per
-- row in the seeded set, so this only ever touches exactly these 5 rows.
-- ═══════════════════════════════════════════════════════════════════

\set bank_account_id 'e2b8b7a3-5156-4ed5-b08d-4c5d75eac469'

BEGIN;

UPDATE acc_bank_transactions SET balance_after = 312244.30
    WHERE bank_account_id = :'bank_account_id' AND transaction_date = '2026-05-01' AND reference = 'RENT-MAY-2026';

UPDATE acc_bank_transactions SET balance_after = 270244.30
    WHERE bank_account_id = :'bank_account_id' AND transaction_date = '2026-05-01' AND reference = 'PAYROLL-MAY-2026';

UPDATE acc_bank_transactions SET balance_after = 454244.30
    WHERE bank_account_id = :'bank_account_id' AND transaction_date = '2026-05-25' AND reference = 'RCP-ZE-101';

UPDATE acc_bank_transactions SET balance_after = 436044.30
    WHERE bank_account_id = :'bank_account_id' AND transaction_date = '2026-06-15' AND reference = 'INS-JUN-2026';

UPDATE acc_bank_transactions SET balance_after = 435858.80
    WHERE bank_account_id = :'bank_account_id' AND transaction_date = '2026-07-01' AND reference = 'BANKFEE-JUL';

UPDATE acc_bank_accounts SET current_balance = 435858.80
    WHERE id = :'bank_account_id';

-- Sanity check before committing — should show 5 rows, correct final balance
SELECT transaction_date, reference, amount, transaction_type, balance_after
FROM acc_bank_transactions
WHERE bank_account_id = :'bank_account_id'
ORDER BY transaction_date;

SELECT current_balance FROM acc_bank_accounts WHERE id = :'bank_account_id';

COMMIT;