// Wording and checks for quality reviews. The server decides every rule; this only gives the person an early, readable answer.

export type ReviewSummary = {
  id: string; clientId: string; clientName: string | null; subjectType: string; subjectLabel: string; templateName: string; round: number; status: string
  sample: boolean; startedAt: string; completedAt: string | null; checksDone: number; checksTotal: number; failed: number; actions: string[]
}
export type ReviewItem = { position: number; text: string; critical: boolean; result: string; note: string | null }
export type ReviewDetail = {
  review: ReviewSummary; subjectId: string | null; preparedBy: string | null; reviewedBy: string | null; outcomeNote: string | null; exceptionReason: string | null
  previousReviewId: string | null; items: ReviewItem[]
}
export type TemplateLine = { position: number; text: string; critical: boolean }
export type Template = { id: string; name: string; subjectType: string; description: string | null; active: boolean; items: TemplateLine[] }

export const MIN_NOTE = 10
export const MIN_OUTCOME_NOTE = 15
export const MAX_ITEMS = 40

export const SUBJECT_TYPES: [string, string][] = [["ASSESSMENT", "Assessment"], ["CASE", "Case"], ["QUOTE", "Quote"], ["OTHER", "Other work"]]
export const SAMPLEABLE: [string, string][] = [["ASSESSMENT", "Assessments"], ["CASE", "Cases"]]
export const STATUS_FILTERS: [string, string][] = [["", "All"], ["IN_PROGRESS", "In progress"], ["REWORK_REQUIRED", "Sent back"], ["APPROVED", "Approved"]]
export const RESULTS: [string, string][] = [["PASS", "Pass"], ["FAIL", "Fail"], ["NA", "Not applicable"]]

export const subjectLabel = (t: string) => SUBJECT_TYPES.find(x => x[0] === t)?.[1] ?? t
export const statusLabel = (s: string) => ({ IN_PROGRESS: "In progress", REWORK_REQUIRED: "Sent back for rework", APPROVED: "Approved" } as Record<string, string>)[s] ?? s
export const resultLabel = (r: string) => (r === "PENDING" ? "Not marked" : RESULTS.find(x => x[0] === r)?.[1] ?? r)

/** A failed check, or one that does not apply, says why. */
export const needsNote = (result: string) => result === "FAIL" || result === "NA"

export function markProblem(result: string, note: string): string | null {
  if (needsNote(result) && note.trim().length < MIN_NOTE) return `Say why in at least ${MIN_NOTE} characters`
  return null
}

/** Open reviews first (sent back, then in progress), newest first within each; approved last. */
export function ordered(list: ReviewSummary[]): ReviewSummary[] {
  const rank = (s: string) => (s === "REWORK_REQUIRED" ? 0 : s === "IN_PROGRESS" ? 1 : 2)
  return [...list].sort((a, b) => rank(a.status) - rank(b.status) || b.startedAt.localeCompare(a.startedAt))
}

export const progress = (r: Pick<ReviewSummary, "checksDone" | "checksTotal" | "failed">): string =>
  `${r.checksDone} of ${r.checksTotal} checked${r.failed > 0 ? `, ${r.failed} failed` : ""}`

export const hasCriticalFail = (items: ReviewItem[]) => items.some(i => i.critical && i.result === "FAIL")

/** What is in the way of finishing with this outcome, or null when nothing is. */
export function completeProblem(outcome: string, items: ReviewItem[], note: string, sameAsPreparer: boolean, reason: string): string | null {
  if (outcome === "APPROVED") {
    if (items.some(i => i.result === "PENDING")) return "Mark every check first"
    if (hasCriticalFail(items)) return "A critical check failed, so this cannot be approved"
  } else if (!items.some(i => i.result === "FAIL")) return "Fail at least one check to send the work back"
  if (note.trim().length < MIN_OUTCOME_NOTE) return `Write a summary of at least ${MIN_OUTCOME_NOTE} characters`
  if (sameAsPreparer && reason.trim().length < MIN_OUTCOME_NOTE) return `You prepared this work. Give a reason of at least ${MIN_OUTCOME_NOTE} characters for reviewing it yourself`
  return null
}

export type TemplateForm = { name: string; subjectType: string; description: string; items: { text: string; critical: boolean }[] }
export const blankTemplate = (): TemplateForm => ({ name: "", subjectType: "ASSESSMENT", description: "", items: [{ text: "", critical: false }] })
export const templateForm = (t: Template): TemplateForm => ({
  name: t.name, subjectType: t.subjectType, description: t.description ?? "", items: [...t.items].sort((a, b) => a.position - b.position).map(i => ({ text: i.text, critical: i.critical })),
})
export function templateProblem(f: TemplateForm): string | null {
  if (!f.name.trim()) return "Give the checklist a name"
  const lines = f.items.filter(i => i.text.trim())
  if (lines.length === 0) return "Add at least one check"
  if (lines.length > MAX_ITEMS) return `A checklist can have at most ${MAX_ITEMS} checks`
  return null
}
export const templateBody = (f: TemplateForm) => ({
  name: f.name.trim(), subjectType: f.subjectType, description: f.description.trim() || null, items: f.items.filter(i => i.text.trim()).map(i => ({ text: i.text.trim(), critical: i.critical })),
})

export function sampleProblem(from: string, to: string, rate: number, templateId: string): string | null {
  if (!from || !to) return "Choose the period to sample from"
  if (to < from) return "The end of the period is before its start"
  if (!Number.isInteger(rate) || rate < 1 || rate > 100) return "Choose a rate from 1 to 100 percent"
  if (!templateId) return "Choose a checklist"
  return null
}

/** Checklists that fit the work being reviewed, switched on only. */
export const templatesFor = (all: Template[], subjectType: string) => all.filter(t => t.active && t.subjectType === subjectType)
