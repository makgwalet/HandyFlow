// Checks and wording for client portal access. The server checks the email and the invite; this keeps a mistyped
// address from being sent and words each person's state.

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/

export const STATUS_LABELS: Record<string, string> = { PENDING: "Invited, not yet signed in", ACTIVE: "Has access", REVOKED: "Access removed" }
export const statusLabel = (s: string) => STATUS_LABELS[s] ?? s

export function emailProblem(raw: string): string | null {
  const e = raw.trim()
  if (!e) return "An email address is required"
  if (e.length > 255 || !EMAIL.test(e)) return "That does not look like an email address"
  return null
}

export interface Grant { id: string; email: string; status: string; emailSent: boolean; invitedAt: string; acceptedAt: string | null; revokedAt: string | null; inviteExpiresAt: string | null }

/** A line saying what to do about an invite whose email did not go out. The link is never shown to staff. */
export function emailNote(g: Grant): string | null {
  if (g.status === "PENDING" && !g.emailSent) return "The invite email could not be sent. Remove this invite and send it again once the address is checked"
  return null
}

/** Live people first (active, then pending), people whose access was removed last. */
export function ordered(grants: Grant[]): Grant[] {
  const rank: Record<string, number> = { ACTIVE: 0, PENDING: 1, REVOKED: 2 }
  return [...grants].sort((a, b) => (rank[a.status] ?? 9) - (rank[b.status] ?? 9) || a.email.localeCompare(b.email))
}

export const dayOf = (iso: string | null) => (iso ? iso.slice(0, 10) : "")
