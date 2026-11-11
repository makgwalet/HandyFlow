-- Minimum charge default (product decision of 11 Nov): a contract that bills weekdays only is pro-rated by billable days unless a basis is chosen;
-- a seven-day contract by calendar days. A rule with no basis chosen now stores NULL, meaning "the default for this rule", worked out when billing runs.
-- Every rule saved before this change keeps exactly the basis it has (it was CALENDAR_DAYS whether or not anyone chose it): nothing existing changes.
-- minimum_basis_source records where the basis came from so administrators can review the older rules: LEGACY (saved before this change), EXPLICIT
-- (chosen by an administrator) or DEFAULT (left to the default).
ALTER TABLE whse_billing_day_rules ALTER COLUMN minimum_basis DROP NOT NULL;
ALTER TABLE whse_billing_day_rules ALTER COLUMN minimum_basis DROP DEFAULT;
ALTER TABLE whse_billing_day_rules ADD COLUMN IF NOT EXISTS minimum_basis_source VARCHAR(10) NOT NULL DEFAULT 'LEGACY' CHECK (minimum_basis_source IN ('LEGACY','EXPLICIT','DEFAULT'));
ALTER TABLE whse_billing_day_rules ADD CONSTRAINT chk_whse_day_rule_basis_source CHECK (minimum_basis_source <> 'DEFAULT' OR minimum_basis IS NULL);
