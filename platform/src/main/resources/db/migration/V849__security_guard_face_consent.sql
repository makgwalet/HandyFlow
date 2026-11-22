-- When the guard's consent to keep a face template was recorded. Null with a stored template means
-- "kept before consent was recorded"; the Security screen can list those so they can be re-consented or removed.
ALTER TABLE security_guards ADD COLUMN IF NOT EXISTS face_consent_at TIMESTAMPTZ;
