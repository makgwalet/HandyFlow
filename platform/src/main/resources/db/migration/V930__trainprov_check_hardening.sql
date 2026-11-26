-- Training provider: CHECK hardening for V911/V912 (audit in docs/trainingprovider/CHECK-HARDENING-AUDIT-V911-V912.md).
-- The length tests in V911/V912 were written as char_length(btrim(x)) >= N. When x is NULL that test is unknown, and a CHECK passes on unknown,
-- so a retired record with no reason, a conditional approval with no conditions and a verification with no source were all accepted.
-- Every constraint below is NULL-safe (COALESCE) and is added NOT VALID: new and changed rows are checked from now on, but existing rows are not
-- examined, rewritten, defaulted or deleted, so this migration cannot fail on historical data. A person reviews the audit output
-- (db/audit/trainprov_v911_v912_check_audit.sql) and then runs db/audit/trainprov_v911_v912_validate_constraints.sql to validate each one.
-- Note: a legacy row that already breaks one of these cannot be updated until it is corrected.

-- C1: retired or withdrawn records carry a written reason (10+ characters).
ALTER TABLE trainprov_authorities DROP CONSTRAINT ck_trainprov_authority_retired;
ALTER TABLE trainprov_authorities ADD CONSTRAINT ck_trainprov_authority_retired CHECK (
    (status = 'ACTIVE' AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
    OR (status = 'RETIRED' AND COALESCE(char_length(btrim(retired_reason)), 0) >= 10 AND retired_by IS NOT NULL AND retired_at IS NOT NULL)) NOT VALID;

ALTER TABLE trainprov_delivery_sites DROP CONSTRAINT ck_trainprov_site_retired;
ALTER TABLE trainprov_delivery_sites ADD CONSTRAINT ck_trainprov_site_retired CHECK (
    (status = 'ACTIVE' AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
    OR (status = 'RETIRED' AND COALESCE(char_length(btrim(retired_reason)), 0) >= 10 AND retired_by IS NOT NULL AND retired_at IS NOT NULL)) NOT VALID;

ALTER TABLE trainprov_qualifications DROP CONSTRAINT ck_trainprov_qual_retired;
ALTER TABLE trainprov_qualifications ADD CONSTRAINT ck_trainprov_qual_retired CHECK (
    (status = 'ACTIVE' AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
    OR (status = 'RETIRED' AND COALESCE(char_length(btrim(retired_reason)), 0) >= 10 AND retired_by IS NOT NULL AND retired_at IS NOT NULL)) NOT VALID;

ALTER TABLE trainprov_programmes DROP CONSTRAINT ck_trainprov_prog_retired;
ALTER TABLE trainprov_programmes ADD CONSTRAINT ck_trainprov_prog_retired CHECK (
    (status <> 'RETIRED' AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
    OR (status = 'RETIRED' AND COALESCE(char_length(btrim(retired_reason)), 0) >= 10 AND retired_by IS NOT NULL AND retired_at IS NOT NULL)) NOT VALID;

ALTER TABLE trainprov_accreditation_scopes DROP CONSTRAINT ck_trainprov_scope_withdrawn;
ALTER TABLE trainprov_accreditation_scopes ADD CONSTRAINT ck_trainprov_scope_withdrawn CHECK (
    (status = 'ACTIVE' AND withdrawn_reason IS NULL AND withdrawn_by IS NULL AND withdrawn_at IS NULL)
    OR (status = 'WITHDRAWN' AND COALESCE(char_length(btrim(withdrawn_reason)), 0) >= 10 AND withdrawn_by IS NOT NULL AND withdrawn_at IS NOT NULL)) NOT VALID;

ALTER TABLE trainprov_programme_offerings DROP CONSTRAINT ck_trainprov_offering_withdrawn;
ALTER TABLE trainprov_programme_offerings ADD CONSTRAINT ck_trainprov_offering_withdrawn CHECK (
    (status = 'ACTIVE' AND withdrawn_reason IS NULL AND withdrawn_by IS NULL AND withdrawn_at IS NULL)
    OR (status = 'WITHDRAWN' AND COALESCE(char_length(btrim(withdrawn_reason)), 0) >= 10 AND withdrawn_by IS NOT NULL AND withdrawn_at IS NOT NULL)) NOT VALID;

-- C2: a conditional approval has its conditions written down.
ALTER TABLE trainprov_accreditations DROP CONSTRAINT ck_trainprov_accr_conditional_has_conditions;
ALTER TABLE trainprov_accreditations ADD CONSTRAINT ck_trainprov_accr_conditional_has_conditions CHECK (
    status <> 'CONDITIONAL_APPROVAL' OR COALESCE(char_length(btrim(conditions)), 0) >= 10) NOT VALID;

-- C3: a recorded verification names its source, the day and the person, all together or not at all.
ALTER TABLE trainprov_accreditations DROP CONSTRAINT ck_trainprov_accr_verified;
ALTER TABLE trainprov_accreditations ADD CONSTRAINT ck_trainprov_accr_verified CHECK (
    (last_verified_on IS NULL AND verified_by IS NULL AND verification_source IS NULL)
    OR (last_verified_on IS NOT NULL AND verified_by IS NOT NULL AND COALESCE(char_length(btrim(verification_source)), 0) >= 5)) NOT VALID;

-- C4: an approval has been verified (the service always records the verification with the approval).
ALTER TABLE trainprov_accreditations ADD CONSTRAINT ck_trainprov_accr_approved_verified CHECK (
    status NOT IN ('APPROVED', 'CONDITIONAL_APPROVAL') OR (last_verified_on IS NOT NULL AND verified_by IS NOT NULL)) NOT VALID;

-- C5: a qualification version that is not a draft names its source.
ALTER TABLE trainprov_qualification_versions DROP CONSTRAINT ck_trainprov_qualver_current;
ALTER TABLE trainprov_qualification_versions ADD CONSTRAINT ck_trainprov_qualver_current CHECK (
    status = 'DRAFT' OR (COALESCE(char_length(btrim(source_note)), 0) >= 5 AND made_current_by IS NOT NULL AND made_current_at IS NOT NULL)) NOT VALID;

-- C6: names, titles and codes are not blank.
ALTER TABLE trainprov_authorities ADD CONSTRAINT ck_trainprov_authority_name CHECK (COALESCE(char_length(btrim(name)), 0) >= 2) NOT VALID;
ALTER TABLE trainprov_delivery_sites ADD CONSTRAINT ck_trainprov_site_name CHECK (COALESCE(char_length(btrim(name)), 0) >= 2) NOT VALID;
ALTER TABLE trainprov_accreditations ADD CONSTRAINT ck_trainprov_accr_title CHECK (COALESCE(char_length(btrim(title)), 0) >= 2) NOT VALID;
ALTER TABLE trainprov_accreditation_scopes ADD CONSTRAINT ck_trainprov_scope_title CHECK (COALESCE(char_length(btrim(programme_title)), 0) >= 2) NOT VALID;
ALTER TABLE trainprov_qualifications ADD CONSTRAINT ck_trainprov_qual_title CHECK (COALESCE(char_length(btrim(title)), 0) >= 2) NOT VALID;
ALTER TABLE trainprov_programmes ADD CONSTRAINT ck_trainprov_prog_code_title CHECK (COALESCE(char_length(btrim(code)), 0) >= 2 AND COALESCE(char_length(btrim(title)), 0) >= 2) NOT VALID;
ALTER TABLE trainprov_programme_modules ADD CONSTRAINT ck_trainprov_module_title CHECK (COALESCE(char_length(btrim(title)), 0) >= 2) NOT VALID;

-- C7: optional reference fields are NULL or non-blank.
ALTER TABLE trainprov_accreditations ADD CONSTRAINT ck_trainprov_accr_number_nonblank CHECK (accreditation_number IS NULL OR char_length(btrim(accreditation_number)) > 0) NOT VALID;
ALTER TABLE trainprov_qualifications ADD CONSTRAINT ck_trainprov_qual_refs_nonblank CHECK (
    (official_ref IS NULL OR char_length(btrim(official_ref)) > 0) AND (saqa_id IS NULL OR char_length(btrim(saqa_id)) > 0)) NOT VALID;

-- C8: an override reason, when given, is a real reason.
ALTER TABLE trainprov_programme_versions ADD CONSTRAINT ck_trainprov_progver_override CHECK (
    approval_override_reason IS NULL OR char_length(btrim(approval_override_reason)) >= 10) NOT VALID;
ALTER TABLE trainprov_accreditation_events ADD CONSTRAINT ck_trainprov_accr_event_override CHECK (
    override_reason IS NULL OR char_length(btrim(override_reason)) >= 10) NOT VALID;

-- C9: an accreditation is not approved before it was submitted.
ALTER TABLE trainprov_accreditations ADD CONSTRAINT ck_trainprov_accr_submitted_before_approved CHECK (
    submitted_on IS NULL OR approval_date IS NULL OR submitted_on <= approval_date) NOT VALID;

-- C10: states the service only allows with a stated reason keep that reason.
ALTER TABLE trainprov_accreditations ADD CONSTRAINT ck_trainprov_accr_reason_states CHECK (
    status NOT IN ('REJECTED', 'SUSPENDED', 'WITHDRAWN', 'MORE_INFORMATION_REQUIRED', 'EXPIRED') OR COALESCE(char_length(btrim(status_reason)), 0) >= 10) NOT VALID;
