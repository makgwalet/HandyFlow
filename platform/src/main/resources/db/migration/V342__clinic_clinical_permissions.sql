-- Clinic Sprint 1 (S1-5): split clinical work from front-desk work.
--   CLINIC_CLINICAL_WRITE  nurse + doctor: consultations, vitals/observations, allergies, conditions,
--                          medications, nurse-side handoff (send to doctor, resume)
--   CLINIC_CLINICAL_SIGN   doctor: accept/return/complete a handoff, sign, certificates, referral letters
-- CLINIC_WRITE stays the front-desk permission (patients, appointments, waitlist, consent).
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CLINIC_CLINICAL_WRITE', 'Record consultations, vitals, allergies, conditions and medications; hand over to a doctor'),
    (gen_random_uuid(), 'CLINIC_CLINICAL_SIGN',  'Accept or return a handoff, complete review, sign consultations, issue certificates and referral letters')
ON CONFLICT (name) DO NOTHING;

-- No one loses access silently, except the default front-desk role: every role that can write
-- clinic data today (other than EMPLOYEE, the receptionist default in V83) keeps clinical write.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions op ON op.id = rp.permission_id AND op.name = 'CLINIC_WRITE'
JOIN roles r ON r.id = rp.role_id
JOIN permissions np ON np.name = 'CLINIC_CLINICAL_WRITE'
WHERE r.name <> 'EMPLOYEE'
ON CONFLICT DO NOTHING;

-- Prescribers are treated as signing clinicians.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions op ON op.id = rp.permission_id AND op.name = 'CLINIC_PRESCRIPTION_WRITE'
JOIN permissions np ON np.name = 'CLINIC_CLINICAL_SIGN'
ON CONFLICT DO NOTHING;
