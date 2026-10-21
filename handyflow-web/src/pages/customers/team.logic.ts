// Per-salesperson view of the pipeline and the task queue. Pure functions.
import { closeState, isOpen, stageOf, type PipelineLead } from "./pipeline.logic"
import { weighted } from "./forecast.logic"
import { daysLate, type Task } from "./tasks.logic"

export const UNASSIGNED = "unassigned"
export interface TeamRow {
  owner: string; openDeals: number; openValue: number; weighted: number; won: number; wonValue: number; lost: number
  winRate: number | null; overdueDeals: number; openTasks: number; lateTasks: number
}
const blank = (owner: string): TeamRow => ({ owner, openDeals: 0, openValue: 0, weighted: 0, won: 0, wonValue: 0, lost: 0, winRate: null, overdueDeals: 0, openTasks: 0, lateTasks: 0 })

/** One row per owner (or "unassigned"), biggest weighted forecast first. winRate is a percentage of decided deals, or null. */
export function buildTeam(leads: PipelineLead[], tasks: Task[], today: string): TeamRow[] {
  const rows = new Map<string, TeamRow>(), row = (o: string | null) => { const k = o ?? UNASSIGNED; if (!rows.has(k)) rows.set(k, blank(k)); return rows.get(k)! }
  for (const l of leads) {
    const r = row(l.ownerId), stage = stageOf(l)
    if (isOpen(l)) { r.openDeals++; r.openValue += l.dealValue ?? 0; r.weighted += weighted(l); if (closeState(l, today) === "overdue") r.overdueDeals++ }
    else if (stage === "WON") { r.won++; r.wonValue += l.dealValue ?? 0 }
    else if (stage === "LOST") r.lost++
  }
  for (const t of tasks) { if (t.completed) continue; const r = row(t.assignedTo); r.openTasks++; if (daysLate(t, today) > 0) r.lateTasks++ }
  for (const r of rows.values()) r.winRate = r.won + r.lost === 0 ? null : Math.round((r.won * 1000) / (r.won + r.lost)) / 10
  return [...rows.values()].sort((a, b) => b.weighted - a.weighted || b.openDeals - a.openDeals || a.owner.localeCompare(b.owner))
}
export const ownerName = (owner: string, me: string | null, names: Record<string, string>) =>
  owner === UNASSIGNED ? "Unassigned" : owner === me ? "You" : names[owner] ?? "Colleague"

// ── Monthly targets ─────────────────────────────────────────────────────────
export interface TargetRow { ownerId: string; target: number | null; achieved: number; wonDeals: number; percent: number | null }
/** The month ("YYYY-MM") a local date falls in. */
export const monthOfDate = (iso: string) => iso.slice(0, 7)
/** Moves a "YYYY-MM" month by whole months. */
export function shiftMonth(month: string, by: number): string {
  const [y, m] = month.split("-").map(Number), n = y * 12 + (m - 1) + by
  return `${Math.floor(n / 12)}-${String((n % 12) + 1).padStart(2, "0")}`
}
const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
export const monthTitle = (month: string) => { const [y, m] = month.split("-").map(Number); return `${MONTHS[m - 1]} ${y}` }
/** Why a typed target cannot be saved, or null. The server checks the same limits. */
export function targetProblem(text: string): string | null {
  const t = text.trim().replace(/\s/g, "")
  if (!t) return "Enter a target amount."
  const n = Number(t)
  if (!Number.isFinite(n)) return "The target must be a number."
  if (n <= 0) return "The target must be more than zero."
  if (n > 9_999_999_999_999.99) return "That target is too large."
  return null
}
export const targetAmount = (text: string) => Number(text.trim().replace(/\s/g, ""))
/** A team row with that month's target and wins attached. Owners who only appear in the targets list get a row too. */
export interface TeamTargetRow extends TeamRow { target: number | null; achieved: number; wonDeals: number; percent: number | null }
export function withTargets(rows: TeamRow[], targets: TargetRow[]): TeamTargetRow[] {
  const by = new Map(targets.map(t => [t.ownerId, t])), seen = new Set<string>(), out: TeamTargetRow[] = []
  for (const r of rows) { const t = by.get(r.owner); seen.add(r.owner); out.push({ ...r, target: t?.target ?? null, achieved: t?.achieved ?? 0, wonDeals: t?.wonDeals ?? 0, percent: t?.percent ?? null }) }
  for (const t of targets) if (!seen.has(t.ownerId)) out.push({ ...blank(t.ownerId), target: t.target, achieved: t.achieved, wonDeals: t.wonDeals, percent: t.percent })
  return out
}
