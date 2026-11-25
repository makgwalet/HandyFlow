// Wording and simple checks for providers and the work given to them. The server enforces every rule; these only save a round trip.

export type Accreditation = {
  id: string; body: string; reference: string; issuedOn: string | null; expiresOn: string | null; status: string; expiry: string; usable: boolean
  recordedBy: string | null; checkedBy: string | null; checkNote: string | null; retireNote: string | null
}
export type ProviderSummary = {
  id: string; name: string; kind: string; status: string; email: string | null; phone: string | null; coverage: string | null; services: string[]
  usableAccreditations: number; unchecked: number; nextExpiry: string | null
}
export type ProviderDetail = { provider: ProviderSummary; notes: string | null; inactiveReason: string | null; accreditations: Accreditation[] }
export type Deliverable = { id: string; title: string; dueDate: string | null; status: string; receivedBy: string | null; reviewedBy: string | null; reviewNote: string | null }
export type Assignment = {
  id: string; providerId: string; providerName: string | null; clientId: string; clientName: string | null; title: string; description: string | null; dueDate: string | null
  status: string; accreditationRequired: boolean; waiverNote: string | null; conflictStatus: string; conflictNote: string | null; conflictApproved: boolean
  conflictApprovalNote: string | null; problemStarting: string | null; rating: number | null; onTime: boolean | null; completionNote: string | null; cancelNote: string | null
}
export type AssignmentDetail = { assignment: Assignment; deliverables: Deliverable[] }
export type Performance = { providerId: string; completed: number; onTime: number; averageRating: number | null; cancelled: number; open: number }

export const ACCREDITATION_STATUS: Record<string, string> = { CLAIMED: "Waiting to be checked", VERIFIED: "Checked", REJECTED: "Check failed", RETIRED: "Retired" }
export const accreditationStatus = (s: string) => ACCREDITATION_STATUS[s] ?? s

export const EXPIRY_TEXT: Record<string, string> = { NONE: "No expiry date", VALID: "In date", EXPIRING: "Expires soon", EXPIRED: "Expired" }
export const expiryText = (e: string) => EXPIRY_TEXT[e] ?? e

export const ASSIGNMENT_STATUS: Record<string, string> = { PROPOSED: "Not started", ACTIVE: "In progress", COMPLETED: "Finished", CANCELLED: "Cancelled" }
export const assignmentStatus = (s: string) => ASSIGNMENT_STATUS[s] ?? s

export const DELIVERABLE_STATUS: Record<string, string> = { EXPECTED: "Expected", RECEIVED: "Handed over, to be reviewed", ACCEPTED: "Accepted", REJECTED: "Sent back", WAIVED: "Not needed" }
export const deliverableStatus = (s: string) => DELIVERABLE_STATUS[s] ?? s

export function conflictText(a: Pick<Assignment, "conflictStatus" | "conflictApproved">): string {
  if (a.conflictStatus === "PENDING") return "No conflict declaration yet"
  if (a.conflictStatus === "NONE") return "Declared: no conflict"
  return a.conflictApproved ? "Conflict declared and approved" : "Conflict declared, waiting for an administrator"
}

export const noteProblem = (v: string): string | null => (v.trim().length >= 10 ? null : "Give a reason of at least 10 characters")
export const ratingProblem = (n: number): string | null => (Number.isInteger(n) && n >= 1 && n <= 5 ? null : "Rate the work from 1 to 5")
export const ratingText = (avg: number | null, completed: number) => (avg == null || completed === 0 ? "No finished work rated yet" : `${avg.toFixed(1)} out of 5`)

export function assignmentProblem(f: { providerId: string; title: string; accreditationRequired: boolean; waiverNote: string }): string | null {
  if (!f.providerId) return "Choose a provider"
  if (!f.title.trim()) return "Say what the work is"
  if (!f.accreditationRequired) return noteProblem(f.waiverNote) ? "Say why no accreditation is needed (at least 10 characters)" : null
  return null
}
