-- V72__invoice_overpaid_credit.sql

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS credit_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN invoices.credit_amount IS
    'Amount paid beyond the invoice total. Set automatically when amountPaid > total (status = OVERPAID). Can be applied to future invoices or refunded.';
