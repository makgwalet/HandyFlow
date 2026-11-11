// src/pages/events/peopleOps.ts
//
// Rules and wording for the event-day roster, incidents and emergency contacts. The server holds the real rules (no overlapping shifts for one person,
// escalation limits, who may see phone numbers); these keep wrong requests from being sent and say each state in plain words.
import { emailOk } from "./guestOps"

const words = (c: string) => { const s = c.toLowerCase().replace(/_/g, " "); return s.charAt(0).toUpperCase() + s.slice(1) }
export const label = words
const nz = (v: string | undefined) => ((v ?? "").trim() === "" ? null : (v as string).trim())
const dt = (v: string | undefined) => (!v ? null : v.length === 16 ? `${v}:00` : v)

// ── Roster ───────────────────────────────────────────────────────────────────
export const STAFF_KINDS = ["STAFF", "VOLUNTEER", "CONTRACTOR"]
export const STAFF_ROLES = ["COORDINATOR", "STEWARD", "REGISTRATION", "SECURITY", "FIRST_AID", "AV_TECH", "CATERING", "DRIVER", "VOLUNTEER_LEAD", "OTHER"]

export interface Staff {
  id: string; userId: string | null; fullName: string; kind: string; role: string; phone: string | null; email: string | null; post: string | null
  shiftStart: string; shiftEnd: string; status: string; arrivedAt: string | null; notes: string | null
}
export interface Coverage { role: string; rostered: number; confirmed: number; arrived: number; noShow: number }

export const staffStatusText = (s: string) => ({ ASSIGNED: "Rostered", CONFIRMED: "Confirmed", ARRIVED: "Arrived", NO_SHOW: "No-show", CANCELLED: "Cancelled" } as Record<string, string>)[s] ?? s
export const canEditStaff = (s: string) => s === "ASSIGNED" || s === "CONFIRMED"
export const canConfirmStaff = (s: string) => s === "ASSIGNED"
export const canArriveStaff = (s: string) => s === "ASSIGNED" || s === "CONFIRMED"
export const canNoShowStaff = (s: string) => s === "ASSIGNED" || s === "CONFIRMED"
export const canCancelStaff = (s: string) => s === "ASSIGNED" || s === "CONFIRMED"

export function staffProblem(f: { fullName: string; kind: string; role: string; email?: string; shiftStart: string; shiftEnd: string }): string | null {
  if (!f.fullName.trim()) return "Enter the person's name."
  if (!STAFF_KINDS.includes(f.kind)) return "Choose staff, volunteer or contractor."
  if (!STAFF_ROLES.includes(f.role)) return "Choose a role."
  if (!emailOk(f.email ?? "")) return "That email address is not valid."
  if (!f.shiftStart || !f.shiftEnd) return "Set the start and end of the shift."
  if (f.shiftEnd <= f.shiftStart) return "The shift must end after it starts."
  return null
}
export function staffBody(f: Record<string, string>) {
  return { userId: null, fullName: f.fullName.trim(), kind: f.kind, role: f.role, phone: nz(f.phone), email: nz(f.email), post: nz(f.post), shiftStart: dt(f.shiftStart), shiftEnd: dt(f.shiftEnd), notes: nz(f.notes) }
}

/** Roles where fewer people have arrived than were rostered; this is the gap a coordinator needs to chase. */
export function coverageGaps(rows: Coverage[]): { role: string; missing: number }[] {
  return rows.map(r => ({ role: r.role, missing: Math.max(0, r.rostered - r.arrived) })).filter(g => g.missing > 0)
}
export const coverageText = (r: Coverage) => `${r.arrived} arrived of ${r.rostered} rostered (${r.confirmed} confirmed${r.noShow ? `, ${r.noShow} no-show` : ""})`

// ── Incidents ────────────────────────────────────────────────────────────────
export const SEVERITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
export const INCIDENT_CATEGORIES = ["MEDICAL", "SECURITY", "SAFETY", "FACILITY", "LOGISTICS", "GUEST_COMPLAINT", "SUPPLIER", "WEATHER", "OTHER"]
export const MAX_ESCALATION = 3

export interface Incident {
  id: string; reference: string; title: string; description: string | null; category: string; severity: string; status: string; location: string | null
  reportedByName: string | null; assignedToName: string | null; escalationLevel: number; acknowledgedAt: string | null; resolvedAt: string | null; resolution: string | null
  createdAt: string; minutesOpen: number; acknowledgementOverdue: boolean; resolutionOverdue: boolean
  timeline: { id: string; kind: string; note: string; authorName: string | null; createdAt: string }[] | null
}

export const incidentStatusText = (s: string) => ({ OPEN: "Open", IN_PROGRESS: "In progress", ESCALATED: "Escalated", RESOLVED: "Resolved", CLOSED: "Closed" } as Record<string, string>)[s] ?? s
export const isUnresolved = (s: string) => s === "OPEN" || s === "IN_PROGRESS" || s === "ESCALATED"
export const canAcknowledge = (i: Pick<Incident, "status" | "acknowledgedAt">) => isUnresolved(i.status) && !i.acknowledgedAt
export const canAssign = (s: string) => isUnresolved(s)
export const canEscalate = (i: Pick<Incident, "status" | "escalationLevel">) => isUnresolved(i.status) && i.escalationLevel < MAX_ESCALATION
export const canResolve = (s: string) => isUnresolved(s)
export const canClose = (s: string) => s === "RESOLVED"
export const canReopen = (s: string) => s === "RESOLVED" || s === "CLOSED"

/** Escalation may only raise severity; offer the levels above the current one. */
export const higherSeverities = (current: string) => SEVERITIES.slice(SEVERITIES.indexOf(current) + 1)

export const severityTone = (s: string): "bad" | "warn" | "muted" => (s === "CRITICAL" || s === "HIGH" ? "bad" : s === "MEDIUM" ? "warn" : "muted")

export function minutesText(m: number): string {
  if (m < 1) return "less than a minute"
  if (m < 60) return `${m} min`
  const h = Math.floor(m / 60), r = m % 60
  return r === 0 ? `${h} h` : `${h} h ${r} min`
}

/** What is wrong with the clocks on an incident, in words; nothing pushes an alert, so the screen must make these hard to miss. */
export function overdueText(i: Pick<Incident, "acknowledgementOverdue" | "resolutionOverdue" | "status">): string | null {
  if (!isUnresolved(i.status)) return null
  if (i.acknowledgementOverdue && i.resolutionOverdue) return "Nobody has taken this and it is past its resolve target"
  if (i.acknowledgementOverdue) return "Nobody has taken this yet"
  if (i.resolutionOverdue) return "Past its resolve target"
  return null
}

export function incidentProblem(f: { title: string; category: string; severity: string }): string | null {
  if (!f.title.trim()) return "Say briefly what happened."
  if (!INCIDENT_CATEGORIES.includes(f.category)) return "Choose a category."
  if (!SEVERITIES.includes(f.severity)) return "Choose how serious it is."
  return null
}
export const incidentBody = (f: Record<string, string>) => ({ title: f.title.trim(), description: nz(f.description), category: f.category, severity: f.severity, location: nz(f.location) })

/** Sorted so what needs a person first comes first: unresolved before resolved, then severity, then oldest. */
export function sortIncidents(list: Incident[]): Incident[] {
  return [...list].sort((a, b) => (Number(!isUnresolved(a.status)) - Number(!isUnresolved(b.status))) || (SEVERITIES.indexOf(b.severity) - SEVERITIES.indexOf(a.severity)) || (a.createdAt < b.createdAt ? -1 : 1))
}

// ── Emergency contacts ───────────────────────────────────────────────────────
export const CONTACT_TYPES = ["MEDICAL", "FIRE", "POLICE", "SECURITY", "VENUE", "ORGANISER", "SUPPLIER", "OTHER"]
export interface Contact { id: string; contactType: string; name: string; organisation: string | null; phone: string; alternatePhone: string | null; email: string | null; notes: string | null }

export function contactProblem(f: { contactType: string; name: string; phone: string; email?: string }): string | null {
  if (!CONTACT_TYPES.includes(f.contactType)) return "Choose a type."
  if (!f.name.trim()) return "Enter a name."
  if (!f.phone.trim()) return "Enter a phone number."
  if (!emailOk(f.email ?? "")) return "That email address is not valid."
  return null
}
export const contactBody = (f: Record<string, string>) => ({ contactType: f.contactType, name: f.name.trim(), organisation: nz(f.organisation), phone: f.phone.trim(), alternatePhone: nz(f.alternatePhone), email: nz(f.email), notes: nz(f.notes) })
/** A phone number as a tel: link, without spaces or punctuation that dialers dislike. */
export const telHref = (p: string) => `tel:${p.replace(/[^\d+]/g, "")}`
