-- A document may be verified by the person who uploaded it only as a recorded exception (a one-person practice has no one else).
-- The reason is kept with the document; it is null whenever someone other than the uploader verified it.
ALTER TABLE client_compliance_documents ADD COLUMN IF NOT EXISTS verification_exception_reason TEXT;
