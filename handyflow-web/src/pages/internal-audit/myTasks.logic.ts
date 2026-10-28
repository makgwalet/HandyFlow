// How the My Tasks list is worded and grouped. No server calls.

import type { MyTask, MyTaskKind } from "../../api/auditMyTasks.api"
import type { Tone } from "./auditUi.logic"

export const KIND_LABEL: Record<MyTaskKind, { label: string; tone: Tone }> = {
  ACTION: { label: "Action", tone: "info" },
  FINDING: { label: "Finding", tone: "warning" },
  NOTE_ANSWER: { label: "Answer note", tone: "warning" },
  NOTE_CLEAR: { label: "Clear note", tone: "success" },
}

export type TaskGroupKey = "overdue" | "week" | "later" | "undated"
export const GROUP_TITLE: Record<TaskGroupKey, string> = { overdue: "Overdue", week: "Due in the next 7 days", later: "Later", undated: "No due date" }

const addDays = (iso: string, n: number) => {
  const d = new Date(`${iso}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + n)
  return d.toISOString().slice(0, 10)
}

/** Groups in the order they should be read, leaving out empty ones. `today` is a date as YYYY-MM-DD. */
export function groupTasks(tasks: MyTask[], today: string): { key: TaskGroupKey; title: string; tasks: MyTask[] }[] {
  const weekEnd = addDays(today, 7)
  const buckets: Record<TaskGroupKey, MyTask[]> = { overdue: [], week: [], later: [], undated: [] }
  for (const t of tasks) {
    if (t.overdue) buckets.overdue.push(t)
    else if (!t.dueDate) buckets.undated.push(t)
    else if (t.dueDate <= weekEnd) buckets.week.push(t)
    else buckets.later.push(t)
  }
  return (Object.keys(buckets) as TaskGroupKey[]).filter(k => buckets[k].length > 0).map(k => ({ key: k, title: GROUP_TITLE[k], tasks: buckets[k] }))
}

export function dueLabel(t: Pick<MyTask, "dueDate" | "overdue" | "daysOverdue">): string {
  if (t.overdue) return t.daysOverdue === 1 ? "1 day late" : `${t.daysOverdue} days late`
  return t.dueDate ? `Due ${t.dueDate}` : "No due date"
}
