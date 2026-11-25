// Wording and checks for regulatory reporting. The server enforces every rule.
// Nothing about any authority is built in: the provider sets each obligation up and says where the rule came from.
// A submission is a staff record. An authority's acknowledgement is recorded separately. Nothing here says a provider is compliant.

export type Obligation = {
  id: string; obligationNo: string; authorityId: string; authorityName: string | null; programmeId: string | null; programmeTitle: string | null; name: string
  cadence: string; dueDaysAfterEnd: number; sourceNote: string; status: string; retiredReason: string | null
}
export type Period = {
  id: string; periodNo: string; obligationId: string; obligationNo: string | null; obligationName: string | null; authorityName: string | null; programmeTitle: string | null
  periodStart: string; periodEnd: string; dueOn: string; dueOverrideReason: string | null; status: string; state: string; submittedOn: string | null
  submissionRef: string | null; acknowledgedOn: string | null; acknowledgementRef: string | null; waivedReason: string | null; voidReason: string | null; note: string | null
}

export const MIN_REASON = 10
export const CADENCES: Record<string, string> = { MONTHLY: "Monthly", QUARTERLY: "Quarterly", HALF_YEARLY: "Half-yearly", ANNUAL: "Annual", ONE_OFF: "One-off" }
export const STATES: Record<string, string> = {
  OVERDUE: "Overdue", DUE_SOON: "Due soon", OPEN_LATER: "Open", SUBMITTED: "Submitted by staff", WAIVED: "Waived", VOID: "Void",
}
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")
export const REPORT_NOTE = "These obligations are set up by you. Nothing is built in. A period marked submitted is a staff record, not an authority's acceptance, and nothing here shows that you are compliant."

export function stateTone(s: string): "good" | "warn" | "bad" | "neutral" {
  return s === "OVERDUE" ? "bad" : s === "DUE_SOON" ? "warn" : s === "SUBMITTED" ? "good" : "neutral"
}

const long = (s: string, n: number) => s.trim().length >= n
export const reasonProblem = (r: string): string | null => (long(r, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`)

export type ObligationForm = { authorityId: string; programmeId: string; name: string; cadence: string; dueDays: string; sourceNote: string }
export const emptyObligation: ObligationForm = { authorityId: "", programmeId: "", name: "", cadence: "QUARTERLY", dueDays: "", sourceNote: "" }
export const obligationForm = (o: Obligation): ObligationForm => ({
  authorityId: o.authorityId, programmeId: o.programmeId ?? "", name: o.name, cadence: o.cadence, dueDays: String(o.dueDaysAfterEnd), sourceNote: o.sourceNote,
})
export function obligationProblem(f: ObligationForm, isNew: boolean): string | null {
  if (isNew && !f.authorityId) return "Choose the authority"
  if (!long(f.name, 2)) return "Give the obligation a name"
  if (!CADENCES[f.cadence]) return "Choose how often it is reported"
  const d = Number(f.dueDays)
  if (f.dueDays.trim() === "" || !Number.isInteger(d) || d < 0 || d > 366) return "Days after the period ends must be a whole number from 0 to 366"
  if (!long(f.sourceNote, 10)) return "Say where this rule came from, in at least 10 characters (for example the notice or letter you took it from)"
  return null
}
const content = (f: ObligationForm) => ({ name: f.name.trim(), cadence: f.cadence, dueDaysAfterEnd: Number(f.dueDays), sourceNote: f.sourceNote.trim() })
export const obligationBody = (f: ObligationForm) => ({ authorityId: f.authorityId, programmeId: f.programmeId || null, ...content(f) })
export const updateBody = (f: ObligationForm) => content(f)

/** The due day an obligation's rule gives for a period end. */
export function computedDue(periodEnd: string, days: number): string {
  const d = new Date(`${periodEnd}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

export type PeriodForm = { obligationId: string; periodStart: string; periodEnd: string; dueOn: string; overrideReason: string }
export const emptyPeriod: PeriodForm = { obligationId: "", periodStart: "", periodEnd: "", dueOn: "", overrideReason: "" }
export function periodProblem(f: PeriodForm, days: number | null): string | null {
  if (!f.obligationId) return "Choose the obligation"
  if (!f.periodStart || !f.periodEnd) return "Enter the start and end of the reporting period"
  if (f.periodEnd < f.periodStart) return "The period cannot end before it starts"
  if (f.dueOn && days !== null && f.dueOn !== computedDue(f.periodEnd, days) && !long(f.overrideReason, MIN_REASON)) {
    return `The due day differs from the obligation's rule. Say why, in at least ${MIN_REASON} characters`
  }
  return null
}
export const periodBody = (f: PeriodForm, days: number | null) => {
  const differs = f.dueOn && days !== null && f.dueOn !== computedDue(f.periodEnd, days)
  return { obligationId: f.obligationId, periodStart: f.periodStart, periodEnd: f.periodEnd, dueOn: differs ? f.dueOn : null, dueOverrideReason: differs ? f.overrideReason.trim() : null }
}

export function submitProblem(on: string, periodStart: string, today: string): string | null {
  if (!on) return "Enter the day it was submitted"
  if (on > today) return "The day cannot be in the future"
  if (on < periodStart) return "It cannot be submitted before the period starts"
  return null
}
export function acknowledgeProblem(on: string, submittedOn: string | null, today: string): string | null {
  if (!on) return "Enter the day the authority acknowledged it"
  if (on > today) return "The day cannot be in the future"
  if (submittedOn && on < submittedOn) return "It cannot be acknowledged before it was submitted"
  return null
}

export function periodLine(p: Pick<Period, "status" | "dueOn" | "submittedOn" | "submissionRef" | "acknowledgedOn" | "acknowledgementRef" | "waivedReason" | "voidReason">): string {
  if (p.status === "VOID") return `Voided. Reason: ${p.voidReason ?? "—"}`
  if (p.status === "WAIVED") return `Waived. Reason: ${p.waivedReason ?? "—"}`
  if (p.status === "SUBMITTED") {
    const ack = p.acknowledgedOn ? `Acknowledged by the authority on ${p.acknowledgedOn}${p.acknowledgementRef ? ` (${p.acknowledgementRef})` : ""}.` : "No acknowledgement recorded yet."
    return `Recorded by staff as submitted on ${p.submittedOn}${p.submissionRef ? ` (${p.submissionRef})` : ""}. ${ack}`
  }
  return `Due ${p.dueOn}`
}
