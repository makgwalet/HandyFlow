// Rules for the CRM task queue: which section a task is in, how late it is, filters, snooze dates and the new-task form.
// Pure functions; lateness is worked out from the due date and today's local date, not from the server's clock.
export type Priority = "LOW" | "NORMAL" | "HIGH"
export const PRIORITIES: Priority[] = ["HIGH", "NORMAL", "LOW"]
export const PRIORITY_LABEL: Record<Priority, string> = { HIGH: "High", NORMAL: "Normal", LOW: "Low" }
export type Outcome = "COMPLETED" | "NO_RESPONSE" | "RESCHEDULED"
export const OUTCOME_LABEL: Record<Outcome, string> = { COMPLETED: "Done", NO_RESPONSE: "No response", RESCHEDULED: "Rescheduled" }

export interface Task {
  id: string; customerId: string; customerName: string; dueDate: string; note: string; assignedTo: string | null; priority: Priority
  completed: boolean; completedAt?: string | null; outcome?: Outcome | null
}
export interface TaskList { tasks: Task[]; truncated: boolean }
/** A task this many days late is flagged for a manager's attention. */
export const ESCALATE_AFTER_DAYS = 3

const dayNumber = (iso: string) => { const [y, m, d] = iso.split("-").map(Number); return Date.UTC(y, m - 1, d) / 86400000 }
const fromDayNumber = (n: number) => new Date(n * 86400000).toISOString().slice(0, 10)
export const addDays = (iso: string, days: number) => fromDayNumber(dayNumber(iso) + days)
/** Days between today and the due date: negative when late. */
export const daysUntilDue = (t: Pick<Task, "dueDate">, today: string) => dayNumber(t.dueDate) - dayNumber(today)
export const daysLate = (t: Task, today: string) => (t.completed ? 0 : Math.max(0, -daysUntilDue(t, today)))
export const isEscalated = (t: Task, today: string) => daysLate(t, today) >= ESCALATE_AFTER_DAYS

export type Section = "overdue" | "today" | "week" | "later"
export const SECTION_LABEL: Record<Section, string> = { overdue: "Overdue", today: "Due today", week: "Next 7 days", later: "Later" }
export const SECTIONS: Section[] = ["overdue", "today", "week", "later"]
export function sectionOf(t: Task, today: string): Section {
  const d = daysUntilDue(t, today)
  return d < 0 ? "overdue" : d === 0 ? "today" : d <= 7 ? "week" : "later"
}
export function dueText(t: Task, today: string): string {
  const d = daysUntilDue(t, today)
  if (t.completed) return `Was due ${t.dueDate}`
  return d < 0 ? `${-d} ${d === -1 ? "day" : "days"} late` : d === 0 ? "Due today" : d === 1 ? "Due tomorrow" : `Due in ${d} days`
}

export type Scope = "mine" | "all"
export interface TaskFilter { scope: Scope; search: string; priority: Priority | "" }
export const DEFAULT_FILTER: TaskFilter = { scope: "mine", search: "", priority: "" }
export function filterTasks(tasks: Task[], f: TaskFilter, me: string | null): Task[] {
  const q = f.search.trim().toLowerCase()
  return tasks.filter(t =>
    (f.scope === "all" || (!!me && t.assignedTo === me)) &&
    (!f.priority || t.priority === f.priority) &&
    (!q || t.customerName.toLowerCase().includes(q) || t.note.toLowerCase().includes(q)))
}
const rank = (p: Priority) => PRIORITIES.indexOf(p)
/** Most late first, then high priority first, then customer name. */
export function sortTasks(tasks: Task[]): Task[] {
  return [...tasks].sort((a, b) => a.dueDate.localeCompare(b.dueDate) || rank(a.priority) - rank(b.priority) || a.customerName.localeCompare(b.customerName))
}
export function groupTasks(tasks: Task[], today: string): { section: Section; tasks: Task[] }[] {
  const sorted = sortTasks(tasks)
  return SECTIONS.map(section => ({ section, tasks: sorted.filter(t => sectionOf(t, today) === section) })).filter(g => g.tasks.length > 0)
}
export function taskTotals(tasks: Task[], today: string) {
  const open = tasks.filter(t => !t.completed), count = (s: Section) => open.filter(t => sectionOf(t, today) === s).length
  return { open: open.length, overdue: count("overdue"), today: count("today"), week: count("week"), escalated: open.filter(t => isEscalated(t, today)).length, high: open.filter(t => t.priority === "HIGH").length }
}

export type Snooze = "tomorrow" | "three-days" | "next-week"
export const SNOOZE_LABEL: Record<Snooze, string> = { tomorrow: "Tomorrow", "three-days": "In 3 days", "next-week": "In a week" }
/** Snoozing counts from today, so a task that is days late comes back soon rather than staying overdue. */
export const snoozeDate = (kind: Snooze, today: string) => addDays(today, kind === "tomorrow" ? 1 : kind === "three-days" ? 3 : 7)

export const assigneeText = (id: string | null, me: string | null, names: Record<string, string>) => (id == null ? "Unassigned" : id === me ? "You" : names[id] ?? "Colleague")

// ── New task ────────────────────────────────────────────────────────────────
export interface NewTask { customerId: string; note: string; dueDate: string; priority: Priority; assignedTo: string | null }
const validDate = (s: string) => /^\d{4}-\d{2}-\d{2}$/.test(s) && !Number.isNaN(Date.parse(s)) && new Date(s).toISOString().slice(0, 10) === s
export function newTaskProblem(t: NewTask, today: string): string | null {
  if (!t.customerId) return "Choose a customer."
  if (!t.note.trim()) return "Say what needs doing."
  if (!validDate(t.dueDate)) return "Choose a due date."
  if (t.dueDate < today) return "The due date cannot be in the past."
  return null
}
export const newTaskBody = (t: NewTask) => ({ dueDate: t.dueDate, note: t.note.trim(), priority: t.priority, assignedTo: t.assignedTo })
export const MAX_TASKS_SHOWN = 1000
