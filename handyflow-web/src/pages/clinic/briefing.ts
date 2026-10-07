// Pure helpers for the patient briefing: wording of dates and how "Start consultation" should begin.
export interface BriefingVisit {
  id: string; at: string; practitionerName?: string | null; chiefComplaint?: string | null; diagnosis?: string | null
  icd10Codes: string[]; followUpDays?: number | null; status: string
}
export interface Briefing {
  patientId: string; visitCount: number
  lastVisit: BriefingVisit | null; daysSinceLastVisit: number | null
  lastVitals: { takenAt: string; weightKg?: number | null; heightCm?: number | null; bloodPressure?: string | null
    pulseBpm?: number | null; temperatureC?: number | null; oxygenSatPct?: number | null } | null
  nextAppointment: { id: string; at: string; type?: string | null; status: string; practitionerName?: string | null; reason?: string | null } | null
  recall: { dueDate: string; overdueDays: number; due: boolean } | null
  openDraft: { id: string; status: string; startedAt: string } | null
  recentVisits: BriefingVisit[]
  allergies: { id: string; allergen: string; severity?: string | null; reaction?: string | null }[]
  conditions: { id: string; conditionName: string; status: string; icd10Code?: string | null }[]
  medications: { id: string; medicineName: string; dose?: string | null; frequency?: string | null }[]
  labs: { unreviewed: number; unreviewedAbnormal: number; unreviewedCritical: number; latestAt?: string | null
    recent: { id: string; at?: string | null; reference?: string | null; abnormal: boolean; critical: boolean; reviewed: boolean }[] }
  alerts: { code: string; severity: "DANGER" | "WARNING" | "INFO"; message: string }[]
}

export const CLINIC_TZ = "Africa/Johannesburg"
const dayKey = (d: Date) => new Intl.DateTimeFormat("en-CA", { timeZone: CLINIC_TZ }).format(d)   // 2026-10-08

export const fmtDay = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric", timeZone: CLINIC_TZ }) : "—"
export const fmtTimeOfDay = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", timeZone: CLINIC_TZ }) : ""

/** "Today", "Yesterday", "12 days ago", "5 months ago", "2 years ago"; null when never. */
export function ago(days: number | null | undefined): string | null {
  if (days == null) return null
  if (days <= 0) return "Today"
  if (days === 1) return "Yesterday"
  if (days < 60) return `${days} days ago`
  if (days < 730) return `${Math.floor(days / 30)} months ago`
  return `${Math.floor(days / 365)} years ago`
}

/** "Due 1 Oct 2026 (7 days overdue)", "Due today", "Due 20 Oct 2026", or null when nothing is promised. */
export function recallText(r: Briefing["recall"]): string | null {
  if (!r) return null
  const d = fmtDay(`${r.dueDate}T12:00:00+02:00`)
  if (r.overdueDays > 0) return `Due ${d} (${r.overdueDays} ${r.overdueDays === 1 ? "day" : "days"} overdue)`
  return r.due ? "Due today" : `Due ${d}`
}

export const bmi = (kg?: number | null, cm?: number | null): number | null =>
  kg && cm && kg > 0 && cm > 0 ? Math.round((kg / ((cm / 100) ** 2)) * 10) / 10 : null

// ── Starting a consultation from the overview ────────────────────────────────

export interface StartableAppt { id: string; status: string; scheduledAt: string }
/** What must happen before the session can open. Walk-in means there is no appointment today and one is created. */
export interface StartPlan<A extends StartableAppt> { kind: "resume" | "start" | "walk-in"; appt: A | null; steps: string[] }

const RANK: Record<string, number> = { IN_PROGRESS: 0, TRIAGED: 1, CHECKED_IN: 2, CONFIRMED: 3, SCHEDULED: 4 }

/**
 * Today's appointment (clinic calendar day) closest to being seen is used; a SCHEDULED one is checked in first
 * because the server only starts CONFIRMED, CHECKED_IN or TRIAGED appointments. With none today it is a walk-in.
 */
export function startPlan<A extends StartableAppt>(appts: A[], now: Date): StartPlan<A> {
  const today = dayKey(now)
  const todays = appts.filter(a => a.status in RANK && dayKey(new Date(a.scheduledAt)) === today)
    .sort((a, b) => RANK[a.status] - RANK[b.status] || a.scheduledAt.localeCompare(b.scheduledAt))
  const pick = todays[0]
  if (!pick) return { kind: "walk-in", appt: null, steps: ["check_in", "start"] }
  if (pick.status === "IN_PROGRESS") return { kind: "resume", appt: pick, steps: [] }
  return { kind: "start", appt: pick, steps: pick.status === "SCHEDULED" ? ["check_in", "start"] : ["start"] }
}
