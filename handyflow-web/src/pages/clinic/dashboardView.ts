// Pure rules for the clinic home screen: the greeting, how today's list is grouped, and who can do what to a row.
import type { DashItem } from "./ClinicDashboard"

export const CLINIC_TZ = "Africa/Johannesburg"

/** "Good morning" before noon, "Good afternoon" before 18:00, else "Good evening" (clinic-local hour). */
export function greeting(now: Date): string {
  const hour = Number(new Intl.DateTimeFormat("en-GB", { hour: "numeric", hour12: false, timeZone: CLINIC_TZ }).format(now)) % 24
  return hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening"
}

export type QueueGroupId = "with" | "ready" | "waiting" | "expected" | "done"
export const QUEUE_GROUPS: { id: QueueGroupId; title: string; statuses: string[] }[] = [
  { id: "with", title: "With the clinician", statuses: ["IN_PROGRESS"] },
  { id: "ready", title: "Triaged — ready to be seen", statuses: ["TRIAGED"] },
  { id: "waiting", title: "Checked in — waiting", statuses: ["CHECKED_IN"] },
  { id: "expected", title: "Expected later", statuses: ["SCHEDULED", "CONFIRMED"] },
  { id: "done", title: "Seen today", statuses: ["COMPLETED"] },
]

export interface QueueGroup { id: QueueGroupId; title: string; items: DashItem[] }

/** Today's appointments by where the patient is. Cancelled and no-shows are left out. `mine` keeps one doctor's patients. */
export function groupQueue(items: DashItem[], mine?: string | null): QueueGroup[] {
  const own = mine ? items.filter(i => i.practitionerId === mine) : items
  return QUEUE_GROUPS.map(g => ({ id: g.id, title: g.title, items: own.filter(i => g.statuses.includes(i.status)) }))
}

/** Patients in the building who have not yet been seen. */
export const inBuilding = (items: DashItem[]) => items.filter(i => i.status === "CHECKED_IN" || i.status === "TRIAGED").length

export type RowAction = { kind: "check_in"; label: string } | { kind: "open"; label: string } | null

/**
 * What the main button on a queue row does. Front desk can check people in; a clinician opens the patient file
 * (its Overview starts or resumes the consultation). Done and expected rows have nothing urgent to do.
 */
export function rowAction(status: string, clinician: boolean, canCheckIn: boolean): RowAction {
  if ((status === "SCHEDULED" || status === "CONFIRMED") && canCheckIn) return { kind: "check_in", label: "Check in" }
  if (clinician && (status === "CHECKED_IN" || status === "TRIAGED")) return { kind: "open", label: "See patient" }
  if (clinician && status === "IN_PROGRESS") return { kind: "open", label: "Resume" }
  return null
}

export const displayName = (user?: { firstName?: string; lastName?: string } | null, practitionerName?: string | null, clinician?: boolean) =>
  clinician && practitionerName ? `Dr. ${practitionerName.replace(/^Dr\.?\s+/i, "")}` : (user?.firstName ?? "")
