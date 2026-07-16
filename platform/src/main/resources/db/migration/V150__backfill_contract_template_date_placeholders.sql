-- ═══════════════════════════════════════════════════════════════════════════
-- IMPORTANT: rename this file before running it.
-- "V999" is a placeholder — replace it with your project's actual next
-- sequential Flyway version number (I don't have access to your migrations
-- folder, so I can't know what that is without guessing).
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Backfill for the raw-ISO-date bug fixed in ContractTemplateSeeder.java.
--
-- WHY THIS IS NEEDED:
-- ContractingService.createContract() calls templateSeeder.seedForTenant(),
-- which only seeds a tenant's 5 system templates ONCE (guarded by
-- countSystemTemplates(tenantId) > 0). Any tenant that had already created
-- a contract before the source fix has the OLD, broken template text
-- (e.g. {{start_date}} instead of {{start_date|date}}) permanently stored
-- in contract_templates.body_template. Fixing the Java source only
-- protects newly-registered tenants going forward — this migration is
-- what fixes the templates already sitting in every existing tenant's data.
--
-- WHY THIS IS SAFE:
-- Only rows where is_system = true are touched. Verified against the real
-- application code (ContractingService.java, ContractingController.java)
-- that there is no path — no update/PUT endpoint, no other save() call —
-- that lets a tenant edit a system template's body_template after it's
-- seeded. createTemplate() always creates NEW rows with isSystem = false.
-- So there is no tenant customization on an is_system = true row that this
-- could possibly overwrite.
--
-- WHAT THIS DOES NOT TOUCH:
-- Contracts (the `contracts` table) already created/sent/signed from the
-- old broken templates. A contract's `body` is resolved (variables already
-- substituted) at creation time and is a frozen historical/legal record —
-- rewriting it retroactively would be altering a document that may already
-- be signed, which is a different and much more serious decision than
-- fixing a template. This migration only fixes the TEMPLATE, so contracts
-- created from it FROM NOW ON get correctly formatted dates. Any existing
-- DRAFT contract still sitting with an unformatted date in its body would
-- need to be individually re-created or manually edited — that's a
-- separate, deliberate call for whoever owns that data, not something to
-- silently rewrite here.
--
-- Uses the exact same 7 literal placeholder strings verified byte-for-byte
-- against ContractTemplateSeeder.java's actual template text (not a regex
-- guess), since this touches legal-document templates.

UPDATE contract_templates
SET body_template =
    REPLACE(
      REPLACE(
        REPLACE(
          REPLACE(
            REPLACE(
              REPLACE(
                REPLACE(
                  body_template,
                  '{{start_date}}', '{{start_date|date}}'
                ),
                '{{end_date}}', '{{end_date|date}}'
              ),
              '{{hire_start_date}}', '{{hire_start_date|date}}'
            ),
            '{{hire_end_date}}', '{{hire_end_date|date}}'
          ),
          '{{payment_due_date}}', '{{payment_due_date|date}}'
        ),
        '{{completion_date}}', '{{completion_date|date}}'
      ),
      '{{first_payment_date}}', '{{first_payment_date|date}}'
    )
WHERE is_system = true
  AND deleted_at IS NULL;