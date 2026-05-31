-- V26__widen_logo_url.sql
-- WHY? Logo stored as base64 data URI.
-- A 100KB PNG encodes to ~133KB base64 = ~136,000 chars.
-- Also add trading_name for businesses with a different trading name vs registered name.
ALTER TABLE tenants ALTER COLUMN logo_url TYPE TEXT;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS trading_name VARCHAR(255);