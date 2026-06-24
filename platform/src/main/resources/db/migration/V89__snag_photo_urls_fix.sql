-- V89 — Fix snag_items.photo_urls column type
-- Hibernate's StringListConverter stores List<String> as a TEXT value
-- (PostgreSQL array literal: {"url1","url2"}) so the column must be TEXT not TEXT[].
-- V88 created it as TEXT[] which conflicts with Hibernate's schema validation.

ALTER TABLE snag_items
    ALTER COLUMN photo_urls TYPE TEXT USING photo_urls::text;