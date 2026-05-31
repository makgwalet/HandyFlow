ALTER TABLE quotes
    ALTER COLUMN customer_id DROP NOT NULL,
    ADD COLUMN walkin_client_name  VARCHAR(255),
    ADD COLUMN walkin_client_email VARCHAR(255),
    ADD COLUMN walkin_client_phone VARCHAR(50);