-- Validates the constraints added NOT VALID by V930, after the audit output has been reviewed and any violating rows have been corrected by a person.
-- Each statement scans its table and fails, changing nothing, if any existing row still breaks the constraint. Run them one at a time and fix what each reports.
-- This is not a Flyway migration. Constraints that already passed can be left as they are; validating an already valid constraint is harmless.
ALTER TABLE trainprov_authorities VALIDATE CONSTRAINT ck_trainprov_authority_retired;
ALTER TABLE trainprov_delivery_sites VALIDATE CONSTRAINT ck_trainprov_site_retired;
ALTER TABLE trainprov_qualifications VALIDATE CONSTRAINT ck_trainprov_qual_retired;
ALTER TABLE trainprov_programmes VALIDATE CONSTRAINT ck_trainprov_prog_retired;
ALTER TABLE trainprov_accreditation_scopes VALIDATE CONSTRAINT ck_trainprov_scope_withdrawn;
ALTER TABLE trainprov_programme_offerings VALIDATE CONSTRAINT ck_trainprov_offering_withdrawn;
ALTER TABLE trainprov_accreditations VALIDATE CONSTRAINT ck_trainprov_accr_conditional_has_conditions;
ALTER TABLE trainprov_accreditations VALIDATE CONSTRAINT ck_trainprov_accr_verified;
ALTER TABLE trainprov_accreditations VALIDATE CONSTRAINT ck_trainprov_accr_approved_verified;
ALTER TABLE trainprov_qualification_versions VALIDATE CONSTRAINT ck_trainprov_qualver_current;
ALTER TABLE trainprov_authorities VALIDATE CONSTRAINT ck_trainprov_authority_name;
ALTER TABLE trainprov_delivery_sites VALIDATE CONSTRAINT ck_trainprov_site_name;
ALTER TABLE trainprov_accreditations VALIDATE CONSTRAINT ck_trainprov_accr_title;
ALTER TABLE trainprov_accreditation_scopes VALIDATE CONSTRAINT ck_trainprov_scope_title;
ALTER TABLE trainprov_qualifications VALIDATE CONSTRAINT ck_trainprov_qual_title;
ALTER TABLE trainprov_programmes VALIDATE CONSTRAINT ck_trainprov_prog_code_title;
ALTER TABLE trainprov_programme_modules VALIDATE CONSTRAINT ck_trainprov_module_title;
ALTER TABLE trainprov_accreditations VALIDATE CONSTRAINT ck_trainprov_accr_number_nonblank;
ALTER TABLE trainprov_qualifications VALIDATE CONSTRAINT ck_trainprov_qual_refs_nonblank;
ALTER TABLE trainprov_programme_versions VALIDATE CONSTRAINT ck_trainprov_progver_override;
ALTER TABLE trainprov_accreditation_events VALIDATE CONSTRAINT ck_trainprov_accr_event_override;
ALTER TABLE trainprov_accreditations VALIDATE CONSTRAINT ck_trainprov_accr_submitted_before_approved;
ALTER TABLE trainprov_accreditations VALIDATE CONSTRAINT ck_trainprov_accr_reason_states;
