-- V475: three more custom field types: several choices from a list, an email address, a web address.
ALTER TABLE crm_custom_fields DROP CONSTRAINT IF EXISTS chk_crm_custom_fields_type;
ALTER TABLE crm_custom_fields
    ADD CONSTRAINT chk_crm_custom_fields_type CHECK (field_type IN ('TEXT', 'NUMBER', 'DATE', 'CHOICE', 'YES_NO', 'MULTI_CHOICE', 'EMAIL', 'URL'));
