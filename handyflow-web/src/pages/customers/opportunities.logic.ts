// Opportunities (many deals per company) and company groups. Pure functions; the server holds the real rules and these mirror them for instant feedback.
import { LOST_REASONS, lostProblem, type LostReason } from "./pipeline.logic"
export { LOST_REASONS, lostProblem, type LostReason }

export type OppStage = "PROSPECTING" | "QUALIFIED" | "PROPOSAL" | "NEGOTIATION" | "WON" | "LOST"
export const OPP_STAGES: OppStage[] = ["PROSPECTING", "QUALIFIED", "PROPOSAL", "NEGOTIATION", "WON", "LOST"]
export const OPP_LABEL: Record<OppStage, string> = { PROSPECTING: "Prospecting", QUALIFIED: "Qualified", PROPOSAL: "Proposal", NEGOTIATION: "Negotiation", WON: "Won", LOST: "Lost" }
/** Rule-of-thumb chance used to weight an open opportunity (the server uses the same numbers). */
export const OPP_CHANCE: Record<OppStage, number> = { PROSPECTING: 10, QUALIFIED: 25, PROPOSAL: 50, NEGOTIATION: 75, WON: 100, LOST: 0 }
export const isOpenStage = (s: OppStage) => s !== "WON" && s !== "LOST"

export interface Opportunity {
  id: string; customerId: string; customerName: string | null; name: string; stage: OppStage; chance: number; amount: number; weightedAmount: number
  expectedCloseDate: string | null; ownerId: string | null; lostReason: LostReason | null; lostNote: string | null; notes: string | null
}

/** Stages an opportunity can move to. A won or lost one is reopened first; it cannot jump to the other way of closing. */
export function oppMoves(stage: OppStage): OppStage[] {
  return OPP_STAGES.filter(s => s !== stage && !((stage === "WON" && s === "LOST") || (stage === "LOST" && s === "WON")))
}

// ── Form ────────────────────────────────────────────────────────────────────
export interface OppForm { customerId: string; name: string; amount: string; expectedCloseDate: string; notes: string }
export const EMPTY_OPP: OppForm = { customerId: "", name: "", amount: "", expectedCloseDate: "", notes: "" }
export const toOppForm = (o: Opportunity): OppForm => ({ customerId: o.customerId, name: o.name, amount: String(o.amount), expectedCloseDate: o.expectedCloseDate ?? "", notes: o.notes ?? "" })
export const MAX_OPP_AMOUNT = 9_999_999_999_999.99
const realDate = (s: string) => /^\d{4}-\d{2}-\d{2}$/.test(s) && new Date(s).toISOString().slice(0, 10) === s
/** Why the form cannot be saved yet, or null. `needsCompany` is true when creating from the all-company tab. */
export function oppProblem(f: OppForm, needsCompany: boolean): string | null {
  if (needsCompany && !f.customerId) return "Choose the company."
  if (!f.name.trim()) return "Give the opportunity a name."
  if (f.name.trim().length > 255) return "The name can be at most 255 characters."
  const a = f.amount.trim().replace(/\s/g, "")
  if (a) {
    const n = Number(a)
    if (!Number.isFinite(n)) return "The amount must be a number."
    if (n < 0) return "The amount cannot be negative."
    if (n > MAX_OPP_AMOUNT) return "That amount is too large."
  }
  if (f.expectedCloseDate.trim() && !realDate(f.expectedCloseDate.trim())) return "Use a real date."
  if (f.notes.length > 4000) return "The notes can be at most 4000 characters."
  return null
}
export const oppBody = (f: OppForm, ownerId: string | null = null) => ({
  customerId: f.customerId || null, name: f.name.trim(), amount: f.amount.trim() ? Math.round(Number(f.amount.trim().replace(/\s/g, "")) * 100) / 100 : 0,
  expectedCloseDate: f.expectedCloseDate.trim() || null, ownerId, notes: f.notes.trim() || null,
})
export const oppStageBody = (stage: OppStage, reason: LostReason | "" = "", note = "") =>
  stage === "LOST" ? { stage, lostReason: reason, lostNote: note.trim() || null } : { stage }

// ── Board ───────────────────────────────────────────────────────────────────
const dayNumber = (iso: string) => { const [y, m, d] = iso.split("-").map(Number); return Date.UTC(y, m - 1, d) / 86400000 }
export type CloseState = "none" | "overdue" | "soon" | "later"
export function oppCloseState(o: Opportunity, today: string): CloseState {
  if (!o.expectedCloseDate || !isOpenStage(o.stage)) return "none"
  const diff = dayNumber(o.expectedCloseDate) - dayNumber(today)
  return diff < 0 ? "overdue" : diff <= 14 ? "soon" : "later"
}
export function oppCloseText(o: Opportunity, today: string): string {
  if (!o.expectedCloseDate) return "No close date"
  if (!isOpenStage(o.stage)) return `Close ${o.expectedCloseDate}`
  const diff = dayNumber(o.expectedCloseDate) - dayNumber(today)
  return diff < 0 ? `Overdue by ${-diff} ${diff === -1 ? "day" : "days"}` : diff === 0 ? "Closes today" : `Closes in ${diff} ${diff === 1 ? "day" : "days"}`
}

export interface OppFilter { search: string; overdueOnly: boolean }
export const NO_OPP_FILTER: OppFilter = { search: "", overdueOnly: false }
export function filterOpps(list: Opportunity[], f: OppFilter, today: string): Opportunity[] {
  const q = f.search.trim().toLowerCase()
  return list.filter(o => (!q || o.name.toLowerCase().includes(q) || (o.customerName ?? "").toLowerCase().includes(q)) && (!f.overdueOnly || oppCloseState(o, today) === "overdue"))
}

export interface OppColumn { stage: OppStage; items: Opportunity[]; count: number; value: number }
/** One column per stage; open columns are sorted by close date (no date last), closed ones newest-first by name. */
export function oppColumns(list: Opportunity[]): OppColumn[] {
  return OPP_STAGES.map(stage => {
    const items = list.filter(o => o.stage === stage).sort((a, b) => (a.expectedCloseDate ?? "9999").localeCompare(b.expectedCloseDate ?? "9999") || a.name.localeCompare(b.name))
    return { stage, items, count: items.length, value: items.reduce((s, o) => s + (o.amount ?? 0), 0) }
  })
}

export interface OppTotals { openCount: number; openValue: number; weighted: number; wonCount: number; wonValue: number; lostCount: number; overdue: number }
export function oppTotals(list: Opportunity[], today: string): OppTotals {
  const t: OppTotals = { openCount: 0, openValue: 0, weighted: 0, wonCount: 0, wonValue: 0, lostCount: 0, overdue: 0 }
  for (const o of list) {
    if (isOpenStage(o.stage)) { t.openCount++; t.openValue += o.amount ?? 0; t.weighted += (o.amount ?? 0) * OPP_CHANCE[o.stage] / 100; if (oppCloseState(o, today) === "overdue") t.overdue++ }
    else if (o.stage === "WON") { t.wonCount++; t.wonValue += o.amount ?? 0 }
    else t.lostCount++
  }
  t.weighted = Math.round(t.weighted * 100) / 100
  return t
}

// ── Company groups ──────────────────────────────────────────────────────────
export interface GroupNode { id: string; name: string; parentId: string | null; customerType: string; status: string }
export interface Hierarchy {
  self: GroupNode; ancestors: GroupNode[]; children: GroupNode[]; branchCount: number
  rollup: { companies: number; openCount: number; openValue: number; weightedValue: number; wonCount: number; wonValue: number }
}
export interface TreeRow { node: GroupNode; depth: number; own: OppTotals; group: OppTotals; branches: number }
const sumTotals = (a: OppTotals, b: OppTotals): OppTotals => ({ openCount: a.openCount + b.openCount, openValue: a.openValue + b.openValue, weighted: a.weighted + b.weighted, wonCount: a.wonCount + b.wonCount, wonValue: a.wonValue + b.wonValue, lostCount: a.lostCount + b.lostCount, overdue: a.overdue + b.overdue })

/**
 * The group tree as indented rows: top-level companies first (by name), each followed by its branches. `group` totals include the company and everything
 * below it. A company whose parent is not in the list is shown at the top, and a loop in the data cannot repeat a company.
 */
export function buildTree(nodes: GroupNode[], opps: Opportunity[], today: string): TreeRow[] {
  const byId = new Map(nodes.map(n => [n.id, n])), kids = new Map<string, GroupNode[]>()
  for (const n of nodes) if (n.parentId && byId.has(n.parentId)) kids.set(n.parentId, [...(kids.get(n.parentId) ?? []), n])
  const byName = (a: GroupNode, b: GroupNode) => a.name.localeCompare(b.name)
  const ownOf = (id: string) => oppTotals(opps.filter(o => o.customerId === id), today)
  const rows: TreeRow[] = [], seen = new Set<string>()
  const walk = (n: GroupNode, depth: number): { total: OppTotals; count: number } => {
    seen.add(n.id)
    const at = rows.length; rows.push({ node: n, depth, own: ownOf(n.id), group: ownOf(n.id), branches: 0 })
    let total = rows[at].own, count = 0
    for (const k of (kids.get(n.id) ?? []).sort(byName)) {
      if (seen.has(k.id)) continue
      const sub = walk(k, depth + 1); total = sumTotals(total, sub.total); count += 1 + sub.count
    }
    rows[at].group = total; rows[at].branches = count
    return { total, count }
  }
  const tops = nodes.filter(n => !n.parentId || !byId.has(n.parentId)).sort(byName)
  for (const t of tops) if (!seen.has(t.id)) walk(t, 0)
  for (const n of [...nodes].sort(byName)) if (!seen.has(n.id)) walk(n, 0)   // anything caught in a loop
  return rows
}
