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

// ---- life of a document ----

export const STATUS_LABELS: Record<string, string> = {
  UPLOADED: "Uploaded", UNDER_REVIEW: "Under review", VERIFIED: "Verified", REJECTED: "Rejected", SUPERSEDED: "Replaced", ARCHIVED: "Archived",
}
export const statusText = (s: string) => STATUS_LABELS[s] ?? s

/** Replaced and archived documents stay on record but are hidden unless asked for. */
export const isRetired = (s: string) => s === "SUPERSEDED" || s === "ARCHIVED"

export const canReview = (s: string) => s === "UPLOADED"
export const canVerify = (s: string) => s === "UPLOADED" || s === "UNDER_REVIEW"
export const canReject = (s: string) => s === "UPLOADED" || s === "UNDER_REVIEW"
export const canReplace = (s: string) => s === "UPLOADED" || s === "UNDER_REVIEW" || s === "VERIFIED" || s === "REJECTED"
export const canArchive = (s: string) => !isRetired(s)

/** Why a reject or archive reason cannot be sent yet, or null. */
export function reasonBlocker(kind: "reject" | "archive", reason: string): string | null {
  return reason.trim().length >= MIN_EXCEPTION_REASON
    ? null
    : `Say why it is ${kind === "reject" ? "rejected" : "archived"}, in at least ${MIN_EXCEPTION_REASON} characters`
}

/** A replacement has to be the same kind of document as the one it replaces. */
export const replacementMatches = (oldType: string, newType: string) => oldType.trim().toLowerCase() === newType.trim().toLowerCase()
