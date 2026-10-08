// Display rules for clinic tasks. The server decides what is overdue; this only words it.
export interface ClinicTask {
  id: string; patientId?: string | null; patientName?: string | null; assignedTo?: string | null; kind: string
  title: string; detail?: string | null; dueDate?: string | null; overdue: boolean; status: string
  sourceType?: string | null; sourceId?: string | null
}

export type DueTone = "overdue" | "today" | "soon" | "none"

/** Johannesburg calendar day of an instant, yyyy-mm-dd. */
export const clinicDay = (d: Date = new Date()) => d.toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })

export function dueLabel(due: string | null | undefined, overdue: boolean, today: string): { text: string; tone: DueTone } {
  if (!due) return { text: "No due date", tone: "none" }
  if (overdue || due < today) return { text: `Overdue since ${due}`, tone: "overdue" }
  if (due === today) return { text: "Due today", tone: "today" }
  return { text: `Due ${due}`, tone: "soon" }
}

/** Overdue first, then by due date, undated last, then oldest title order is kept. */
export function sortTasks<T extends Pick<ClinicTask, "dueDate" | "overdue">>(tasks: T[]): T[] {
  const rank = (t: T) => (t.overdue ? 0 : t.dueDate ? 1 : 2)
  return [...tasks].sort((a, b) => rank(a) - rank(b) || (a.dueDate ?? "").localeCompare(b.dueDate ?? ""))
}

export function titleProblem(title: string): string | null {
  const t = title.trim()
  if (t.length < 3) return "Give the task a title of at least 3 characters"
  if (t.length > 200) return "Keep the title under 200 characters"
  return null
}

export function dismissProblem(reason: string): string | null {
  const t = reason.trim()
  if (t.length < 5) return "Say why (at least 5 characters)"
  if (t.length > 500) return "Keep the reason under 500 characters"
  return null
}

/** The task a clinician starts from a lab result. One open follow-up per result; asking again returns the same one. */
export function resultFollowUp(result: { id: string; patientId?: string | null; patientName?: string | null }) {
  return {
    kind: "RESULT_FOLLOW_UP", patientId: result.patientId ?? undefined, sourceType: "LAB_RESULT", sourceId: result.id,
    title: `Follow up result${result.patientName ? ` for ${result.patientName}` : ""}`,
  }
}
