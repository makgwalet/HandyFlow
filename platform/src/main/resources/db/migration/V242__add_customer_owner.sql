-- ============================================================================
-- IMPORTANT — FILENAME PLACEHOLDER, ACTION REQUIRED BEFORE USE:
-- This file is named VNEXT__add_customer_owner.sql deliberately, NOT a real
-- Vnn number. I could not safely determine the actual highest Flyway
-- version already in your real db/migration folder from the code I could
-- see (confirmed migrations up to at least V90__project_cancellation_reason.sql
-- and V58__accountant_module.sql, but CRM alone has shipped several more
-- migrations since — pipeline_stage, customer_follow_ups, customer_consent,
-- customer_communications — so the real ceiling is almost certainly higher
-- than what I could confirm, and guessing a number risks a silent collision
-- or a broken migration ordering). Rename this file to the correct next
-- Vnn__add_customer_owner.sql before running Flyway.
-- ============================================================================

-- backlog 4.1 — CRM lead ownership/assignment
-- No FK to the users table: matches this codebase's existing convention for
-- every other "who did this" column on Customer (created_by is implicit via
-- CustomerActivity.performedBy, deleted_by is a plain UUID with no FK) —
-- users live in a different module (identity), and Customer intentionally
-- never hard-references across that module boundary at the DB level.
ALTER TABLE customers ADD COLUMN owner_id UUID;

-- Backs the "my leads" filter (WHERE owner_id = :ownerId OR owner_id IS NULL)
-- and the eventual "show me everything assigned to X" admin view.
-- Partial index (only non-deleted rows) — same pattern as the rest of this
-- table's indexes, which all exist to speed up the "active" queries that
-- dominate real traffic, not the rare "deleted" ones.
CREATE INDEX idx_customers_owner_id ON customers (tenant_id, owner_id)
    WHERE deleted_at IS NULL;