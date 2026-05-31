-- Add to your next Flyway migration file:
ALTER TABLE invoices ALTER COLUMN customer_id DROP NOT NULL;

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS walkin_client_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS walkin_client_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS walkin_client_phone VARCHAR(50);