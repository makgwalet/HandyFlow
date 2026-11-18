-- Business version of a contract template's wording. Raised each time a custom template is edited.
ALTER TABLE contract_templates ADD COLUMN IF NOT EXISTS template_version INT NOT NULL DEFAULT 1;
