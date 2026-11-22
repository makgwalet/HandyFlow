// Showing a guard's full ID number or bank account: the reason rules and request body.
// The server enforces the same rules; this only saves a round trip.

export type PiiField = "ID_NUMBER" | "BANK_ACCOUNT"

export const MIN_REASON = 10
export const MAX_REASON = 500
/** How long a revealed value stays on screen. */
export const SHOW_SECONDS = 30

export const MASK_CHARS = /[*•]/

/** True when the text is a masked value as sent by the server. */
export function isMasked(v: string | null | undefined): boolean {
  return !!v && MASK_CHARS.test(v)
}

/** Text for the edit forms: a masked value is never put into an input, so it can't be saved back by mistake. */
export function editableValue(v: string | null | undefined): string {
  return !v || isMasked(v) ? "" : v
}

export function reasonProblem(reason: string): string | null {
  const t = reason.trim()
  if (t.length < MIN_REASON) return `Give a reason of at least ${MIN_REASON} characters. It is recorded.`
  if (t.length > MAX_REASON) return `Keep the reason under ${MAX_REASON} characters.`
  return null
}

export function revealBody(field: PiiField, reason: string) {
  return { field, reason: reason.trim() }
}

export const FIELD_LABEL: Record<PiiField, string> = { ID_NUMBER: "ID number", BANK_ACCOUNT: "bank account number" }
