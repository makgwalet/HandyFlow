// Rules for verifying a client's compliance document. The server enforces them; this only decides what to ask.

export const MIN_EXCEPTION_REASON = 15

/** The person who uploaded a document verifies it only with a recorded reason. */
export function needsVerifyException(doc: { uploadedBy?: string | null }, userId: string | null | undefined): boolean {
  return !!userId && !!doc.uploadedBy && doc.uploadedBy === userId
}

/** Why the verify form cannot be submitted yet, or null. */
export function verifyBlocker(needsException: boolean, reason: string): string | null {
  if (!needsException) return null
  return reason.trim().length >= MIN_EXCEPTION_REASON
    ? null
    : `You uploaded this document, so someone else should verify it. If no one else can, give a reason of at least ${MIN_EXCEPTION_REASON} characters.`
}
