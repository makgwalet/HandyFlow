-- FIX: backlog 1.1b — AP bill rejection state machine. Nullable: every
-- existing bill (and every bill that never gets rejected) simply has
-- no reason recorded. Populated only by ApBill.reject(), and cleared
-- again by backToDraftForResubmission() once the bill has been edited
-- and is being sent back through approval — a stale reason from a
-- previous rejection round shouldn't linger once the bill has moved on.
ALTER TABLE ap_bills ADD COLUMN rejection_reason TEXT;