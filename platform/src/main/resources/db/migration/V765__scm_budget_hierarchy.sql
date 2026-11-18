-- V765: budget parent/child allocation.
-- A budget may be allocated out of a parent budget (e.g. a department budget split into cost centres or projects).
-- Rules (enforced in BudgetService / BudgetHierarchyRules): the child's period sits inside the parent's, the child's scope is
-- the same level or finer, the active children's amounts add up to no more than the parent, and spend against a child also
-- counts against every ancestor. The column is nullable: existing budgets stay top level.

ALTER TABLE sc_budgets
    ADD COLUMN IF NOT EXISTS parent_budget_id UUID REFERENCES sc_budgets(id);

ALTER TABLE sc_budgets
    ADD CONSTRAINT chk_sc_budget_not_own_parent CHECK (parent_budget_id IS NULL OR parent_budget_id <> id);

CREATE INDEX IF NOT EXISTS idx_sc_budgets_parent ON sc_budgets (tenant_id, parent_budget_id) WHERE parent_budget_id IS NOT NULL;
