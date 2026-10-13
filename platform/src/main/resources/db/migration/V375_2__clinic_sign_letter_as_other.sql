-- Signing a letter in another practitioner's name (patch 0182, CLINIC-DEC-027). Until now any user who could write a letter could name any
-- practitioner as its signer. Naming someone else now needs its own permission; a letter signed by the visit's doctor (or the practice)
-- needs nothing extra. Holders of the coarse clinical-sign permission keep the ability, so doctors lose nothing.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CLINIC_DOCUMENT_SIGN_AS_OTHER', 'Sign a letter in the name of another practitioner')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions op ON op.id = rp.permission_id AND op.name = 'CLINIC_CLINICAL_SIGN'
JOIN permissions np ON np.name = 'CLINIC_DOCUMENT_SIGN_AS_OTHER'
ON CONFLICT DO NOTHING;
