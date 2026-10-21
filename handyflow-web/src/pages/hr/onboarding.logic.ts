// Pure helpers for the onboarding screen. The server decides what is done, overdue or allowed; this only decides what to
// offer and how to word it.

export interface OnboardingRights {
  view: boolean
  /** Add, waive, reopen, assign, finish, cancel, and edit the task list. */
  manage: boolean
}

export function onboardingRights(permissions: string[]): OnboardingRights {
  const has = (...p: string[]) => p.some(x => permissions.includes(x))
  const manage = has("HR_MANAGE", "HR_ONBOARDING_MANAGE")
  return { manage, view: manage || has("HR_READ", "HR_TEAM_READ") }
}

export const CATEGORY_ORDER = ["CONTRACT", "DOCUMENTS", "PAYROLL", "COMPLIANCE", "ACCESS", "INDUCTION"] as const

export const CATEGORY_LABEL: Record<string, string> = {
  CONTRACT: "Contract", DOCUMENTS: "Documents", PAYROLL: "Payroll set-up", COMPLIANCE: "Compliance", ACCESS: "Access and equipment", INDUCTION: "Induction",
}

export const CHECK_LABEL: Record<string, string> = {
  ID_NUMBER: "ID number on the record", TAX_NUMBER: "Tax number on the record", BANK_DETAILS: "Bank details on the record", SALARY: "Salary on the record",
  MANAGER: "Manager on the record", LOGIN: "Login linked to the record", EMERGENCY_CONTACT: "Emergency contact on the record",
}

export const STATUS_LABEL: Record<string, string> = { IN_PROGRESS: "In progress", COMPLETE: "Complete", CANCELLED: "Cancelled" }

export interface TaskView {
  id: string; title: string; description: string | null; category: string; required: boolean; dueDate: string | null
  automatic: boolean; check: string | null; assigneeId: string | null; status: string; overdue: boolean; note: string | null
}

export function categoryLabel(c: string): string { return CATEGORY_LABEL[c] ?? c }

/** Tasks grouped in the usual order, empty groups left out. Order within a group is kept. */
export function groupByCategory<T extends { category: string }>(tasks: T[]): { category: string; label: string; tasks: T[] }[] {
  const known = [...CATEGORY_ORDER] as string[]
  const cats = [...known, ...tasks.map(t => t.category).filter(c => !known.includes(c))]
  return Array.from(new Set(cats))
    .map(category => ({ category, label: categoryLabel(category), tasks: tasks.filter(t => t.category === category) }))
    .filter(g => g.tasks.length > 0)
}

/** A person can tick a task if they manage onboarding or it is assigned to them, it is open, and it is not checked against the record. */
export function canTick(t: Pick<TaskView, "status" | "automatic" | "assigneeId">, manage: boolean, userId?: string | null): boolean {
  if (t.status !== "OPEN" || t.automatic) return false
  return manage || (!!userId && t.assigneeId === userId)
}

export function canWaive(t: Pick<TaskView, "status">, manage: boolean): boolean { return manage && t.status === "OPEN" }
export function canReopen(t: Pick<TaskView, "status" | "automatic">, manage: boolean): boolean { return manage && !t.automatic && t.status !== "OPEN" }

/** "Due in 3 days", "2 days overdue", "Due today", or "" when there is no date or the task is finished. */
export function dueLabel(t: Pick<TaskView, "dueDate" | "status">, today: Date): string {
  if (!t.dueDate || t.status !== "OPEN") return ""
  const [y, m, d] = t.dueDate.split("-").map(Number)
  const due = new Date(y, m - 1, d)
  const base = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const days = Math.round((due.getTime() - base.getTime()) / 86_400_000)
  if (days === 0) return "Due today"
  if (days > 0) return `Due in ${days} day${days === 1 ? "" : "s"}`
  return `${-days} day${days === -1 ? "" : "s"} overdue`
}

export function progressText(s: { finished: number; total: number; requiredOpen: number }): string {
  const req = s.requiredOpen === 0 ? "all required tasks done" : `${s.requiredOpen} required still open`
  return `${s.finished} of ${s.total} done, ${req}`
}

export function reasonProblem(reason: string, what: string): string | null {
  if (!reason.trim()) return `Give the reason ${what}.`
  if (reason.trim().length > 500) return "Keep the reason under 500 characters."
  return null
}

export function taskProblem(title: string): string | null {
  if (!title.trim()) return "Give the task a title."
  if (title.trim().length > 200) return "Keep the title under 200 characters."
  return null
}

export function todayIso(d = new Date()): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
}
