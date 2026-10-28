// src/pages/hr/trainingRequired.logic.ts
//
// Required training: the matrix of who must hold which certificate, a manager's team view, and booking yourself onto a session. The server
// works out every status; these turn them into words, order the rows and check what a person types before it is sent.
import type { MyBooking, MyCertificate } from "./myTraining.logic"

export type ReqStatus = "OK" | "EXPIRING" | "EXPIRED" | "MISSING" | "BOOKED"

export interface Requirement { id: string; courseId: string; courseTitle: string; jobTitle: string | null; department: string | null; appliesTo: string }
export interface MatrixColumn { courseId: string; courseTitle: string }
export interface MatrixCell { status: ReqStatus; label: string; expiryDate: string | null }
export interface MatrixRow { employeeId: string; employeeNumber: string | null; name: string; jobTitle: string | null; department: string | null; cells: (MatrixCell | null)[] }
export interface MatrixSummary { people: number; required: number; valid: number; expiring: number; expired: number; missing: number; booked: number }
export interface Matrix { columns: MatrixColumn[]; rows: MatrixRow[]; summary: MatrixSummary }

export interface RequiredLine { courseId: string; courseTitle: string; status: ReqStatus; label: string; expiryDate: string | null }
export interface TeamMember { employeeId: string; name: string; jobTitle: string | null; required: RequiredLine[]; certificates: MyCertificate[]; bookings: MyBooking[] }
export interface Team { linked: boolean; members: TeamMember[] }

export interface OpenSession { id: string; courseId: string; courseTitle: string; startDate: string; endDate: string; venue: string | null; seatsLeft: number | null; required: boolean }

export const STATUS_TONE: Record<ReqStatus, { color: string; bg: string }> = {
  OK: { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  EXPIRING: { color: "var(--hf-warning-text-strong)", bg: "var(--hf-warning-soft)" },
  EXPIRED: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
  MISSING: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
  BOOKED: { color: "var(--hf-sky-text-strong)", bg: "var(--hf-surface-sunken)" },
}

/** Nothing valid is held and nothing is booked. */
export const needsAction = (s: ReqStatus) => s === "MISSING" || s === "EXPIRED"

const gaps = (cells: (MatrixCell | null)[]) => cells.filter(c => c && needsAction(c.status)).length

/** People with something to put right come first (most gaps first), then by name. With {@code onlyGaps}, the rest are left out. */
export function visibleRows(rows: readonly MatrixRow[], onlyGaps: boolean): MatrixRow[] {
  const shown = onlyGaps ? rows.filter(r => gaps(r.cells) > 0) : [...rows]
  return shown.sort((a, b) => gaps(b.cells) - gaps(a.cells) || a.name.localeCompare(b.name))
}

/** A cell in words, with the date when there is one. */
export function cellText(c: Pick<MatrixCell, "label" | "status" | "expiryDate">): string {
  if (!c.expiryDate || c.status === "MISSING") return c.label
  return c.status === "EXPIRED" ? `${c.label} on ${c.expiryDate}` : `${c.label} · until ${c.expiryDate}`
}

export function summaryText(s: MatrixSummary): string {
  if (s.required === 0) return "Nothing is required yet."
  const open = s.expired + s.missing
  const people = `${s.people} ${s.people === 1 ? "person" : "people"}`
  if (open === 0) return `${people} ${s.people === 1 ? "has" : "have"} every required certificate in date${s.expiring ? ` (${s.expiring} expiring soon)` : ""}.`
  return `${open} of ${s.required} required certificates for ${people} ${open === 1 ? "is" : "are"} missing or expired.`
}

/** Null when the requirement can be sent. The server decides which courses may be chosen. */
export function requirementProblem(courseId: string, jobTitle: string, department: string): string | null {
  if (!courseId) return "Choose the course that must be held."
  if (jobTitle.trim().length > 150 || department.trim().length > 150) return "A job title or department can be at most 150 characters."
  return null
}

export const requirementBody = (courseId: string, jobTitle: string, department: string) =>
  ({ courseId, jobTitle: jobTitle.trim() || null, department: department.trim() || null })

/** One word for a team member: do they need to do something, will something run out soon, or are they in order. */
export function attention(m: Pick<TeamMember, "required">): "ACTION" | "EXPIRING" | "OK" {
  if (m.required.some(r => needsAction(r.status))) return "ACTION"
  return m.required.some(r => r.status === "EXPIRING") ? "EXPIRING" : "OK"
}

/** Team members who need to act come first, then those with something expiring, then the rest; by name within each. */
export function orderTeam(members: readonly TeamMember[]): TeamMember[] {
  const rank = { ACTION: 0, EXPIRING: 1, OK: 2 } as const
  return [...members].sort((a, b) => rank[attention(a)] - rank[attention(b)] || a.name.localeCompare(b.name))
}

export function teamSummary(members: readonly TeamMember[]): string {
  if (members.length === 0) return "Nobody reports to you."
  const action = members.filter(m => attention(m) === "ACTION").length
  const soon = members.filter(m => attention(m) === "EXPIRING").length
  const who = `${members.length} ${members.length === 1 ? "person" : "people"}`
  if (action === 0 && soon === 0) return `${who} on your team, all in order.`
  return `${who} on your team: ${action} to act on, ${soon} with something expiring soon.`
}

export function seatsText(seatsLeft: number | null): string {
  if (seatsLeft === null) return "Places available"
  return seatsLeft === 1 ? "1 place left" : `${seatsLeft} places left`
}

/** Sessions that train something the person is required to hold come first, then by date. */
export function orderOpenSessions(rows: readonly OpenSession[]): OpenSession[] {
  return [...rows].sort((a, b) => Number(b.required) - Number(a.required) || a.startDate.localeCompare(b.startDate))
}

/** Booking can be taken back only until the day the session starts. */
export function canCancelBooking(b: Pick<MyBooking, "status" | "startDate">, today: string): boolean {
  return b.status === "ENROLLED" && !!b.startDate && b.startDate > today
}
