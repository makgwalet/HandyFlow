-- A billable-day rule may also say how the monthly minimum storage charge is pro-rated. CALENDAR_DAYS (the default, and what every existing rule and
-- every client with no rule gets) pro-rates by the calendar days an invoice covers. BILLABLE_DAYS pro-rates by the days the contract actually bills.
ALTER TABLE whse_billing_day_rules ADD COLUMN IF NOT EXISTS minimum_basis VARCHAR(20) NOT NULL DEFAULT 'CALENDAR_DAYS' CHECK (minimum_basis IN ('CALENDAR_DAYS','BILLABLE_DAYS'));
