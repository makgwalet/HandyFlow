// Figures for the CRM dashboard: the headline tiles and the list of things that need attention today.
// Pure functions over the pipeline and the open tasks; the panel only fetches and draws.
import { isOpen, pipelineTotals, type PipelineLead } from "./pipeline.logic"
import { buildForecast } from "./forecast.logic"
import { daysLate, daysUntilDue, isEscalated, type Task } from "./tasks.logic"

export interface DashboardFigures {
  openDeals: number; openValue: number; weighted: number; wonValue: number
  overdueDeals: number; overdueTasks: number; escalatedTasks: number; dueToday: number
}
export function buildDashboard(leads: PipelineLead[], tasks: Task[], today: string): DashboardFigures {
  const totals = pipelineTotals(leads, today), open = tasks.filter(t => !t.completed)
  return {
    openDeals: leads.filter(isOpen).length, openValue: totals.openValue, weighted: buildForecast(leads, today).weighted, wonValue: totals.wonValue,
    overdueDeals: totals.overdue,
    overdueTasks: open.filter(t => daysUntilDue(t, today) < 0).length,
    escalatedTasks: open.filter(t => isEscalated(t, today)).length,
    dueToday: open.filter(t => daysUntilDue(t, today) === 0).length,
  }
}
/** The tasks to look at first: late ones, latest first, then those due today. At most `limit`. */
export function attentionTasks(tasks: Task[], today: string, limit = 6): Task[] {
  return tasks.filter(t => !t.completed && daysUntilDue(t, today) <= 0)
    .sort((a, b) => daysLate(b, today) - daysLate(a, today) || a.customerName.localeCompare(b.customerName))
    .slice(0, limit)
}
