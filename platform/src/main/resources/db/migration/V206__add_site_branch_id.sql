-- V218__add_site_branch_id.sql
-- (Rename to your actual next migration number before applying.)
--
-- Fixes a real gap found while scoping branch-level enforcement: Guard
-- already has primary_branch_id mapped (Guard.java, Phase 4), but Site.java
-- has no branch_id field at all -- despite BranchService's own javadoc
-- describing "site.branch_id" as an existing, working column ("Assigning
-- sites and guards to branches is done via direct UPDATE on those tables
-- (site.branch_id, guard.primary_branch_id)"). Idempotent (IF NOT EXISTS)
-- in case the column already exists at the DB level and was simply never
-- mapped into the JPA entity -- either way this migration is safe to run.

ALTER TABLE security_sites
    ADD COLUMN IF NOT EXISTS branch_id UUID;

CREATE INDEX IF NOT EXISTS idx_security_sites_branch
    ON security_sites (tenant_id, branch_id)
    WHERE branch_id IS NOT NULL;

COMMENT ON COLUMN security_sites.branch_id IS
    'Site''s assigned branch (regional/operational subdivision), same convention as security_guards.primary_branch_id. Nullable -- a site with no branch assignment is visible to any tenant-wide (non-branch-scoped) role. Query-level enforcement (filtering by the acting user''s branch scope) is NOT yet wired -- see BranchController''s ENFORCEMENT NOTE and the conversation this migration came from for what''s still needed (security_branch_assignments-based scope resolution).';