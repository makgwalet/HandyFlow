// Pure helpers for the employee relations screens. The server decides what each case allows and which buttons to offer; this only
// words things, colours them and catches obvious mistakes before a request is sent. South Africa has no daylight saving.

export interface ErRights { read: boolean; manage: boolean; decide: boolean }

export function erRights(permissions: string[]): ErRights {
  const has = (p: string) => permissions.includes(p)
  return { read: has("HR_ER_READ"), manage: has("HR_ER_MANAGE"), decide: has("HR_ER_DECIDE") }
}

/** "FINAL_WRITTEN_WARNING" becomes "Final written warning". */
export function words(code: string | null | undefined): string {
  if (!code) return "–"
  const t = code.toLowerCase().replace(/_/g, " ")
  return t.charAt(0).toUpperCase() + t.slice(1)
}

export const TYPES = ["DISCIPLINARY", "GRIEVANCE", "INCAPACITY"] as const
export const TYPE_LABEL: Record<string, string> = { DISCIPLINARY: "Discipline", GRIEVANCE: "Grievance", INCAPACITY: "Incapacity" }

export const STATUS_LABEL: Record<string, string> = {
  REPORTED: "Reported", INVESTIGATING: "Investigating", NOTICE_ISSUED: "Notice issued", HEARING_HELD: "Hearing held",
  DECIDED: "Decided", APPEALED: "Appealed", CLOSED: "Closed", WITHDRAWN: "Withdrawn",
}

export const CATEGORIES: Record<string, string[]> = {
  DISCIPLINARY: ["ABSENTEEISM", "LATENESS", "INSUBORDINATION", "DISHONESTY", "THEFT", "ASSAULT", "HARASSMENT", "SUBSTANCE", "NEGLIGENCE", "SAFETY",
    "POLICY_BREACH", "MISUSE_OF_PROPERTY", "OTHER"],
  GRIEVANCE: ["PAY_AND_BENEFITS", "WORKING_CONDITIONS", "TREATMENT_BY_MANAGER", "DISCRIMINATION", "HARASSMENT", "VICTIMISATION", "HEALTH_AND_SAFETY",
    "WORKLOAD", "OTHER"],
  INCAPACITY: ["POOR_PERFORMANCE", "ILL_HEALTH"],
}

/** What a chair can give without a formal hearing. Anything stronger needs notice and a hearing. */
export const INFORMAL_SANCTIONS = ["COUNSELLING", "VERBAL_WARNING", "WRITTEN_WARNING"]
export const HEARING_SANCTIONS = ["NOT_GUILTY", "COUNSELLING", "VERBAL_WARNING", "WRITTEN_WARNING", "FINAL_WRITTEN_WARNING", "DISMISSAL"]
export const WARNING_SANCTIONS = ["VERBAL_WARNING", "WRITTEN_WARNING", "FINAL_WRITTEN_WARNING"]
export const GRIEVANCE_OUTCOMES = ["UPHELD", "PARTLY_UPHELD", "NOT_UPHELD"]
export const APPEAL_OUTCOMES = ["CONFIRMED", "VARIED", "OVERTURNED"]
export const LOWER_SANCTIONS = ["COUNSELLING", "VERBAL_WARNING", "WRITTEN_WARNING", "FINAL_WRITTEN_WARNING"]

export const ACTION_LABEL: Record<string, string> = {
  ASSIGN: "Assign case officer", PARTIES: "Set the people involved", CONFIDENTIAL: "Confidentiality", INVESTIGATE: "Start investigation",
  WITHDRAW: "Withdraw", INFORMAL: "Informal outcome", CLOSE_NO_CASE: "Close – no case to answer", NOTICE: "Issue notice of hearing",
  HEARING: "Record the hearing", DECIDE: "Record the decision", RETURN_TO_INVESTIGATION: "Back to investigation",
  COMMUNICATE: "Record that the employee was told", APPEAL: "Record an appeal", CLOSE: "Close the case", DECIDE_APPEAL: "Decide the appeal",
  SUSPEND: "Precautionary suspension", END_SUSPENSION: "End the suspension", STEP: "Record a step", REVIEW: "Set the review period",
}

/** Colour tokens for a status chip: [text, background]. */
export function statusColors(status: string): [string, string] {
  switch (status) {
    case "REPORTED":
    case "INVESTIGATING": return ["var(--hf-warning-text-strong)", "var(--hf-warning-soft)"]
    case "NOTICE_ISSUED":
    case "HEARING_HELD":
    case "APPEALED": return ["var(--hf-info-text)", "var(--hf-info-soft)"]
    case "DECIDED": return ["var(--hf-success-text-strong)", "var(--hf-success-soft-strong)"]
    default: return ["var(--hf-text-muted)", "var(--hf-surface-sunken)"]
  }
}

export const isOpen = (status: string) => status !== "CLOSED" && status !== "WITHDRAWN"

// ── Dates ───────────────────────────────────────────────────────────────────────────────────────────

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

/** "2026-10-05" becomes "5 Oct 2026". Does not depend on the browser's locale. */
export function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "–"
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  if (!m) return iso
  return `${Number(m[3])} ${MONTHS[Number(m[2]) - 1] ?? m[2]} ${m[1]}`
}

/** An instant shown as South African date and time. */
export function fmtInstant(instant: string | null | undefined): string {
  if (!instant) return "–"
  const t = Date.parse(instant)
  if (Number.isNaN(t)) return instant
  const d = new Date(t + 2 * 3600 * 1000)
  const iso = d.toISOString()
  return `${fmtDate(iso.slice(0, 10))} ${iso.slice(11, 16)}`
}

export function fmtTime(t: string | null | undefined): string {
  return t ? t.slice(0, 5) : "–"
}

export function todaySast(now: Date = new Date()): string {
  return new Date(now.getTime() + 2 * 3600 * 1000).toISOString().slice(0, 10)
}

export const isFuture = (iso: string, now: Date = new Date()) => iso > todaySast(now)

// ── Checks before sending. The server checks everything again; these only save a round trip. ────────

export interface OpenForm { employeeId: string; type: string; category: string; title: string; description: string; incidentDate: string }

export function openProblem(f: OpenForm, now: Date = new Date()): string | null {
  if (!f.employeeId) return "Choose the employee."
  if (!(f.type in TYPE_LABEL)) return "Choose the kind of case."
  if (!CATEGORIES[f.type]?.includes(f.category)) return "Choose a category."
  if (!f.title.trim()) return "Give the case a short title."
  if (!f.description.trim()) return "Describe what happened."
  if (!f.incidentDate) return "Give the date of the incident."
  if (isFuture(f.incidentDate, now)) return "The incident date cannot be in the future."
  return null
}

export interface NoticeForm { hearingDate: string; hearingTime: string; venue: string; chairId: string; initiatorId: string; charges: string[] }

export function noticeProblem(f: NoticeForm, type: string, now: Date = new Date()): string | null {
  if (!f.hearingDate) return "Give the hearing date."
  if (f.hearingDate < todaySast(now)) return "The hearing cannot be in the past."
  if (!f.hearingTime) return "Give the time of the hearing."
  if (!f.venue.trim()) return "Give the venue."
  if (!f.chairId) return "Choose who will chair."
  if (f.initiatorId && f.initiatorId === f.chairId) return "The chair cannot also be the initiator."
  if (type === "DISCIPLINARY" && f.charges.filter(c => c.trim()).length === 0) return "State at least one charge."
  return null
}

export interface DecisionForm { findings: Record<string, string>; sanction: string; warningMonths: string; reasons: string }

export function decisionProblem(f: DecisionForm, chargeIds: string[]): string | null {
  if (chargeIds.some(id => !f.findings[id])) return "Give a finding on every charge."
  const anyGuilty = chargeIds.some(id => f.findings[id] === "GUILTY")
  if (!f.sanction) return "Choose the outcome."
  if (!anyGuilty && f.sanction !== "NOT_GUILTY") return "Not guilty on every charge means no sanction."
  if (anyGuilty && f.sanction === "NOT_GUILTY") return "Guilty on a charge needs a sanction."
  if (WARNING_SANCTIONS.includes(f.sanction)) {
    const m = Number(f.warningMonths)
    if (!Number.isInteger(m) || m < 1 || m > 60) return "Say how many months the warning stays valid (1 to 60)."
  }
  if (!f.reasons.trim()) return "Give the reasons."
  return null
}

export function appealDecisionProblem(outcome: string, newOutcome: string, warningMonths: string, note: string, deciderId: string): string | null {
  if (!deciderId) return "Choose who decides the appeal."
  if (!outcome) return "Choose the appeal outcome."
  if (outcome === "VARIED" && !newOutcome) return "Choose what replaces the original outcome."
  if (outcome === "VARIED" && WARNING_SANCTIONS.includes(newOutcome)) {
    const m = Number(warningMonths)
    if (!Number.isInteger(m) || m < 1 || m > 60) return "Say how many months the replacement warning stays valid (1 to 60)."
  }
  if (!note.trim()) return "Give the reasons for the decision."
  return null
}

export interface PolicyForm {
  noticeHours: number; verbalMonths: number; writtenMonths: number; finalMonths: number; appealDays: number; grievanceResponseDays: number; minReviewDays: number
}

export function policyProblem(p: PolicyForm): string | null {
  const whole = (n: number, lo: number, hi: number) => Number.isInteger(n) && n >= lo && n <= hi
  if (!whole(p.noticeHours, 1, 720)) return "Notice must be 1 to 720 hours."
  for (const [name, n] of [["Verbal", p.verbalMonths], ["Written", p.writtenMonths], ["Final", p.finalMonths]] as const)
    if (!whole(n, 1, 60)) return `${name} warning validity must be 1 to 60 months.`
  if (!(p.verbalMonths <= p.writtenMonths && p.writtenMonths <= p.finalMonths)) return "A stronger warning should stay valid at least as long as a weaker one."
  if (!whole(p.appealDays, 1, 90)) return "The appeal window must be 1 to 90 days."
  if (!whole(p.grievanceResponseDays, 1, 90)) return "The grievance response time must be 1 to 90 working days."
  if (!whole(p.minReviewDays, 1, 365)) return "The review period must be 1 to 365 days."
  return null
}

/** The line shown beside a case in the list: when the next thing is due, and whether it is late. */
export function dueText(dueLabel: string | null | undefined, dueOn: string | null | undefined, overdue: boolean): string {
  if (!dueOn) return "–"
  return `${dueLabel ? dueLabel + " " : ""}${fmtDate(dueOn)}${overdue ? " (late)" : ""}`
}
