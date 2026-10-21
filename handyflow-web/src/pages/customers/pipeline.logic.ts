// Rules for the sales pipeline board and the funnel report: grouping leads by stage, totals, close-date state, deal validation.
// Pure functions; the components only fetch, draw and send.
export type Stage = "NEW" | "CONTACTED" | "QUALIFIED" | "WON" | "LOST"
export const STAGES: Stage[] = ["NEW", "CONTACTED", "QUALIFIED", "WON", "LOST"]
export const STAGE_LABEL: Record<Stage, string> = { NEW: "New", CONTACTED: "Contacted", QUALIFIED: "Qualified", WON: "Won", LOST: "Lost" }
export const OPEN_STAGES: Stage[] = ["NEW", "CONTACTED", "QUALIFIED"]
export type LostReason = "PRICE" | "COMPETITOR" | "NO_RESPONSE" | "NO_BUDGET" | "NOT_A_FIT" | "TIMING" | "OTHER"
export const LOST_REASONS: { id: LostReason; label: string }[] = [
  { id: "PRICE", label: "Price too high" }, { id: "COMPETITOR", label: "Chose a competitor" }, { id: "NO_RESPONSE", label: "Stopped responding" },
  { id: "NO_BUDGET", label: "No budget" }, { id: "NOT_A_FIT", label: "Not a fit" }, { id: "TIMING", label: "Bad timing" }, { id: "OTHER", label: "Other (note needed)" },
]
export const reasonLabel = (r: LostReason | null | undefined) => (r ? LOST_REASONS.find(x => x.id === r)?.label ?? r : "No reason recorded")
export const MAX_NOTE = 500
/** Why a lead cannot be marked lost yet, or null. The server checks the same rules. */
export function lostProblem(reason: LostReason | "", note: string): string | null {
  if (!reason) return "Choose why the lead was lost."
  if (reason === "OTHER" && !note.trim()) return "Add a note saying why."
  if (note.trim().length > MAX_NOTE) return `The note can be at most ${MAX_NOTE} characters.`
  return null
}
/** Stages a lead can be moved to. A won or lost lead is reopened first; it cannot jump straight to the other way of closing. */
export function allowedMoves(stage: Stage): Stage[] {
  return STAGES.filter(s => s !== stage && !((stage === "WON" && s === "LOST") || (stage === "LOST" && s === "WON")))
}
export const stageBody = (stage: Stage, reason: LostReason | "" = "", note = "") =>
  stage === "LOST" ? { stage, lostReason: reason, lostNote: note.trim() || null } : { stage }
/** The server returns at most this many leads; the board says so when it is reached. */
export const PIPELINE_LIMIT = 500

export interface PipelineLead {
  id: string; name: string; email?: string | null; phone?: string | null; ownerId: string | null
  dealValue: number | null; expectedCloseDate: string | null; pipelineStage: Stage | null; status?: string; createdAt?: string
  lostReason?: LostReason | null; lostNote?: string | null; customerType?: "LEAD" | "CUSTOMER"
}
/** A lead with no recorded stage is a new one. */
export const stageOf = (l: PipelineLead): Stage => l.pipelineStage ?? "NEW"
export const isOpen = (l: PipelineLead) => OPEN_STAGES.includes(stageOf(l))

export const money = (n: number | null | undefined) => (n == null ? "—" : `R ${Number(n).toLocaleString("en-ZA", { maximumFractionDigits: 2 })}`)
/** Today as a local calendar date (YYYY-MM-DD) — not the UTC date, which is wrong for South African evenings. */
export const localToday = (now = new Date()) => `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`
const dayNumber = (iso: string) => { const [y, m, d] = iso.split("-").map(Number); return Date.UTC(y, m - 1, d) / 86400000 }

export type CloseState = "none" | "overdue" | "soon" | "later"
/** Overdue = an open lead whose expected close date has passed. "Soon" = within 14 days. Won and lost leads are never overdue. */
export function closeState(l: PipelineLead, today: string): CloseState {
  if (!l.expectedCloseDate || !isOpen(l)) return "none"
  const diff = dayNumber(l.expectedCloseDate) - dayNumber(today)
  return diff < 0 ? "overdue" : diff <= 14 ? "soon" : "later"
}
export function closeText(l: PipelineLead, today: string): string {
  if (!l.expectedCloseDate) return "No close date"
  const diff = dayNumber(l.expectedCloseDate) - dayNumber(today)
  if (!isOpen(l)) return `Close ${l.expectedCloseDate}`
  return diff < 0 ? `Overdue by ${-diff} ${diff === -1 ? "day" : "days"}` : diff === 0 ? "Closes today" : `Closes in ${diff} ${diff === 1 ? "day" : "days"}`
}

export type OwnerFilter = "all" | "mine" | "unassigned"
export interface LeadFilter { search: string; owner: OwnerFilter; overdueOnly: boolean }
export const NO_FILTER: LeadFilter = { search: "", owner: "all", overdueOnly: false }
export function filterLeads(leads: PipelineLead[], f: LeadFilter, me: string | null, today: string): PipelineLead[] {
  const q = f.search.trim().toLowerCase()
  return leads.filter(l =>
    (!q || [l.name, l.email, l.phone].some(x => x?.toLowerCase().includes(q))) &&
    (f.owner === "all" || (f.owner === "mine" ? !!me && l.ownerId === me : l.ownerId == null)) &&
    (!f.overdueOnly || closeState(l, today) === "overdue"))
}
export const ownerText = (ownerId: string | null, me: string | null) => (ownerId == null ? "Unassigned" : ownerId === me ? "You" : "Colleague")

export interface Column { stage: Stage; leads: PipelineLead[]; count: number; value: number }
/** One column per stage; soonest close date first, leads without a date last. */
export function columns(leads: PipelineLead[]): Column[] {
  return STAGES.map(stage => {
    const inStage = leads.filter(l => stageOf(l) === stage).sort((a, b) =>
      (a.expectedCloseDate ?? "9999").localeCompare(b.expectedCloseDate ?? "9999") || a.name.localeCompare(b.name))
    return { stage, leads: inStage, count: inStage.length, value: inStage.reduce((s, l) => s + Number(l.dealValue ?? 0), 0) }
  })
}
export function pipelineTotals(leads: PipelineLead[], today: string) {
  const open = leads.filter(isOpen), val = (xs: PipelineLead[]) => xs.reduce((s, l) => s + Number(l.dealValue ?? 0), 0)
  return {
    openCount: open.length, openValue: val(open), wonValue: val(leads.filter(l => stageOf(l) === "WON")), lostValue: val(leads.filter(l => stageOf(l) === "LOST")),
    overdue: leads.filter(l => closeState(l, today) === "overdue").length, noValue: open.filter(l => l.dealValue == null).length,
  }
}

// ── Deal editing ────────────────────────────────────────────────────────────
export const MAX_DEAL = 9_999_999_999_999.99   // the database column holds 15 digits, 2 after the point
const validDate = (s: string) => /^\d{4}-\d{2}-\d{2}$/.test(s) && !Number.isNaN(Date.parse(s)) && new Date(s).toISOString().slice(0, 10) === s
export function dealProblem(valueText: string, dateText: string): string | null {
  const v = valueText.trim()
  if (v) {
    const n = Number(v.replace(/\s/g, ""))
    if (!Number.isFinite(n)) return "Deal value must be a number."
    if (n < 0) return "Deal value cannot be negative."
    if (n > MAX_DEAL) return "Deal value is too large."
  }
  if (dateText.trim() && !validDate(dateText.trim())) return "Use a real date."
  return null
}
/** Both fields are always sent: an empty box clears it. */
export const dealBody = (valueText: string, dateText: string) => ({
  dealValue: valueText.trim() ? Math.round(Number(valueText.replace(/\s/g, "")) * 100) / 100 : null,
  expectedCloseDate: dateText.trim() || null,
})

// ── Funnel report ───────────────────────────────────────────────────────────
export interface FunnelStage { stage: Stage; reachedCount: number; conversionFromPreviousStage: number | null; avgDaysInStage: number | null }
export interface Funnel { stages: FunnelStage[]; totalLeads: number; totalWon: number; totalLost: number; totalStillOpen: number; overallConversionRate: number | null }
/** The server sends rates as fractions (0.25 = 25%). */
export const percent = (fraction: number | null | undefined) => (fraction == null ? "—" : `${Math.round(Number(fraction) * 1000) / 10}%`)
export const daysText = (d: number | null | undefined) => (d == null ? "—" : d < 1 ? "under a day" : `${Math.round(d * 10) / 10} days`)
/** Bar length as a share of the widest stage, so the funnel reads at a glance. */
export function barShare(stages: FunnelStage[], s: FunnelStage): number {
  const max = Math.max(0, ...stages.map(x => x.reachedCount))
  return max === 0 ? 0 : Math.round((s.reachedCount / max) * 100)
}
/** The stage where the most leads stop: the largest drop in leads between one stage and the next. */
export function biggestDropOff(stages: FunnelStage[]): { from: Stage; to: Stage; lost: number } | null {
  let best: { from: Stage; to: Stage; lost: number } | null = null
  const path = stages.filter(s => s.stage !== "LOST")   // Lost is an exit, not a step forward
  for (let i = 0; i + 1 < path.length; i++) {
    const lost = path[i].reachedCount - path[i + 1].reachedCount
    if (lost > 0 && (!best || lost > best.lost)) best = { from: path[i].stage, to: path[i + 1].stage, lost }
  }
  return best
}

export interface LostReasonRow { reason: LostReason | null; count: number; dealValue: number | null }

/** A converted customer is no longer a lead: its stage is fixed at Won and it cannot be moved. */
export const isConverted = (l: PipelineLead) => l.customerType === "CUSTOMER"
/** Only a won lead that is still a lead can be converted. */
export const canConvert = (l: PipelineLead) => !isConverted(l) && stageOf(l) === "WON"
