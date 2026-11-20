-- V819: Legal / Compliance permission metadata. Confirm the next free Flyway version before applying.
--
-- Every LEGALCOMPLIANCE_* permission added after V590 (V800-V812 and later) was inserted without metadata, so it kept
-- module_key = 'unassigned': a company entitled to the Legal and Compliance module would still not be offered these
-- permissions, and AuthorityCatalogueCoverageTest.everyPermissionInTheCatalogueHasMetadata fails on them.
-- LEGALCOMPLIANCE_ADMIN, _MANAGE and _READ were covered by V590 and are not repeated.
--
-- requires_second_person marks the permissions where the service already enforces segregation of duties (the person
-- who raised the item may not approve, accept, verify or review it). Sensitive = personal information, ethics,
-- incidents, FICA, provider-client access, exports, and decisions that accept risk or approve policy.
-- No role grants change here: ADMIN already holds all of these. Nothing is revoked.

UPDATE permissions p
   SET module_key = v.module_key, resource = v.resource, verb = v.verb, sensitivity = v.sensitivity,
       requires_second_person = v.requires_second_person, grantable_by = v.grantable_by
  FROM (VALUES
    ('LEGALCOMPLIANCE_APPLICABILITY_APPROVE', 'legalcompliance', 'applicability', 'APPROVE', 'NORMAL', true, 'ADMIN'),
    ('LEGALCOMPLIANCE_APPLICABILITY_ASSESS', 'legalcompliance', 'applicability', 'ASSESS', 'NORMAL', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_ATTESTATION_MANAGE', 'legalcompliance', 'attestation', 'MANAGE', 'NORMAL', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_AUDIT_MANAGE', 'legalcompliance', 'audit', 'MANAGE', 'NORMAL', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_BCM_MANAGE', 'legalcompliance', 'bcm', 'MANAGE', 'NORMAL', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_CAPA_APPROVE', 'legalcompliance', 'capa', 'APPROVE', 'SENSITIVE', true, 'ADMIN'),
    ('LEGALCOMPLIANCE_CHANGE_REVIEW', 'legalcompliance', 'change', 'REVIEW', 'NORMAL', true, 'ADMIN'),
    ('LEGALCOMPLIANCE_CONTROL_ASSESS', 'legalcompliance', 'control', 'ASSESS', 'NORMAL', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_CONTROL_MANAGE', 'legalcompliance', 'control', 'MANAGE', 'NORMAL', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_CONTROL_TEST', 'legalcompliance', 'control_test', 'TEST', 'NORMAL', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_CONTROL_TEST_REVIEW', 'legalcompliance', 'control_test', 'REVIEW', 'NORMAL', true, 'ADMIN'),
    ('LEGALCOMPLIANCE_ESG_MANAGE', 'legalcompliance', 'esg', 'MANAGE', 'NORMAL', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_ETHICS_REVIEW', 'legalcompliance', 'ethics', 'REVIEW', 'SENSITIVE', true, 'ADMIN'),
    ('LEGALCOMPLIANCE_FICA_MANAGE', 'legalcompliance', 'fica', 'MANAGE', 'SENSITIVE', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_FINDING_MANAGE', 'legalcompliance', 'finding', 'MANAGE', 'NORMAL', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_FINDING_VERIFY', 'legalcompliance', 'finding', 'VERIFY', 'NORMAL', true, 'ADMIN'),
    ('LEGALCOMPLIANCE_GOVERNANCE_MANAGE', 'legalcompliance', 'governance', 'MANAGE', 'SENSITIVE', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_INCIDENT_MANAGE', 'legalcompliance', 'incident', 'MANAGE', 'SENSITIVE', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_LICENCE_MANAGE', 'legalcompliance', 'licence', 'MANAGE', 'SENSITIVE', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_MONITOR_MANAGE', 'legalcompliance', 'monitor', 'MANAGE', 'NORMAL', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_POLICY_APPROVE', 'legalcompliance', 'policy', 'APPROVE', 'SENSITIVE', true, 'ADMIN'),
    ('LEGALCOMPLIANCE_POLICY_MANAGE', 'legalcompliance', 'policy', 'MANAGE', 'NORMAL', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_PRIVACY_APPROVE', 'legalcompliance', 'privacy', 'APPROVE', 'SENSITIVE', true, 'ADMIN'),
    ('LEGALCOMPLIANCE_PRIVACY_MANAGE', 'legalcompliance', 'privacy', 'MANAGE', 'SENSITIVE', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_PROVIDER_CONSULT', 'legalcompliance', 'provider', 'CONSULT', 'SENSITIVE', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_PROVIDER_MANAGE', 'legalcompliance', 'provider', 'MANAGE', 'SENSITIVE', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_REGULATION_MANAGE', 'legalcompliance', 'regulation', 'MANAGE', 'NORMAL', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_REPORT_EXPORT', 'legalcompliance', 'report', 'EXPORT', 'SENSITIVE', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_RISK_ACCEPT', 'legalcompliance', 'risk', 'ACCEPT', 'SENSITIVE', true, 'ADMIN'),
    ('LEGALCOMPLIANCE_RISK_MANAGE', 'legalcompliance', 'risk', 'MANAGE', 'NORMAL', false, 'ADMIN'),
    ('LEGALCOMPLIANCE_THIRDPARTY_APPROVE', 'legalcompliance', 'thirdparty', 'APPROVE', 'NORMAL', true, 'ADMIN'),
    ('LEGALCOMPLIANCE_THIRDPARTY_MANAGE', 'legalcompliance', 'thirdparty', 'MANAGE', 'NORMAL', false, 'ADMIN')
  ) AS v(name, module_key, resource, verb, sensitivity, requires_second_person, grantable_by)
 WHERE p.name = v.name;
