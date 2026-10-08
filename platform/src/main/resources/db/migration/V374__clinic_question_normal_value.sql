-- Examination libraries (Q-6): a question can carry the answer a clinician may fill in with one tap ("Mark all normal").
-- The value is written by the clinical reviewer through the authoring API; nothing is seeded here (CLINIC-DEC-019).
-- Examination groups are ordinary question groups whose category is EXAMINATION.
ALTER TABLE clinic_question ADD COLUMN normal_value JSONB;
