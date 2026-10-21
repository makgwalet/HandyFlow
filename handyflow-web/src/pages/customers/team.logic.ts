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
