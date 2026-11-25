// Wording and checks for regulatory changes. The server decides every rule; this only gives the person an early, readable answer.

export type Change = {
  id: string; sourceId: string; sourceName: string; title: string; changeType: string; status: string; origin: string; publicationDate: string | null
  effectiveDate: string | null; raisedAt: string; linkedObligations: number; impactsOpen: number; impactsTotal: number; actions: string[]
}
export type Impact = {
  id: string; changeId: string; changeTitle: string; clientId: string; clientName: string; obligationCode: string; status: string; dueDate: string | null
  note: string | null; handledAt: string | null; noticeGivenAt: string | null; actions: string[]
}
export type Detail = {
  change: Change; summary: string | null; referenceUrl: string | null; obligations: { id: string; code: string; name: string | null }[]; reviewedAt: string | null
  reviewNote: string | null; reviewExceptionReason: string | null; affected: { clientId: string; clientName: string; obligationCode: string; state: string }[]; impacts: Impact[]
}
export type Source = { id: string; name: string; authority: string | null; url: string | null; kind: string; active: boolean }

export const MIN_NOTE = 15
export const CHANGE_TYPES: [string, string][] = [["NEW", "New rule"], ["AMENDED", "Amended"], ["REPEALED", "Repealed"], ["PROPOSED", "Proposed"], ["GUIDANCE", "Guidance"]]
export const KINDS: [string, string][] = [["LEGISLATION", "Legislation"], ["REGULATION", "Regulation"], ["NOTICE", "Notice"], ["GUIDANCE", "Guidance"], ["OTHER", "Other"]]
export const STATUS_FILTERS: [string, string][] = [["", "All"], ["DETECTED", "Flagged"], ["IN_REVIEW", "In review"], ["CONFIRMED", "Confirmed"], ["DISMISSED", "Dismissed"]]
const DATELESS = ["PROPOSED", "GUIDANCE"]

export const changeTypeLabel = (t: string) => CHANGE_TYPES.find(x => x[0] === t)?.[1] ?? t
export const statusLabel = (s: string) => STATUS_FILTERS.find(x => x[0] === s)?.[1] ?? s.toLowerCase().replace(/_/g, " ")
export const IMPACT_STATUS: Record<string, string> = { OPEN: "Not dealt with", NOTICE_GIVEN: "Client told", NO_ACTION: "No action needed", DONE: "Done" }
export const impactLabel = (s: string) => IMPACT_STATUS[s] ?? s
export const ACTION_LABELS: Record<string, string> = { NOTICE_GIVEN: "Client told", NO_ACTION: "No action needed", DONE: "Mark done", REOPEN: "Reopen" }
export const actionLabel = (a: string) => ACTION_LABELS[a] ?? a

export const needsEffectiveDate = (type: string) => !DATELESS.includes(type)

export type ChangeForm = { sourceId: string; title: string; summary: string; changeType: string; publicationDate: string; effectiveDate: string; referenceUrl: string; obligationIds: string[] }
export const blankForm = (): ChangeForm => ({ sourceId: "", title: "", summary: "", changeType: "AMENDED", publicationDate: "", effectiveDate: "", referenceUrl: "", obligationIds: [] })
export const formOf = (d: Detail): ChangeForm => ({
  sourceId: d.change.sourceId, title: d.change.title, summary: d.summary ?? "", changeType: d.change.changeType, publicationDate: d.change.publicationDate ?? "",
  effectiveDate: d.change.effectiveDate ?? "", referenceUrl: d.referenceUrl ?? "", obligationIds: d.obligations.map(o => o.id),
})

export const urlProblem = (u: string): string | null => {
  const t = u.trim()
  if (!t) return null
  return /^https?:\/\/\S+$/i.test(t) ? null : "The web address must start with http:// or https:// and have no spaces"
}

export function changeProblem(f: ChangeForm, today: string): string | null {
  if (!f.sourceId) return "Choose the source"
  if (!f.title.trim()) return "Give the change a title"
  if (f.publicationDate && f.publicationDate > today) return "The publication date cannot be in the future"
  return urlProblem(f.referenceUrl)
}

export function sourceProblem(name: string, url: string): string | null {
  if (!name.trim()) return "Give the source a name"
  return urlProblem(url)
}

/** What stops a flagged change being confirmed, so the button can say so before the server does. */
export function confirmBlock(c: Pick<Change, "changeType" | "effectiveDate">): string | null {
  return needsEffectiveDate(c.changeType) && !c.effectiveDate ? "Add the date the change takes effect before confirming it" : null
}

export const noteProblem = (note: string) => (note.trim().length >= MIN_NOTE ? null : `Write at least ${MIN_NOTE} characters`)

/** Awaiting a decision first, then by when it takes effect. */
export function ordered(changes: Change[]): Change[] {
  const rank = (s: string) => (s === "IN_REVIEW" ? 0 : s === "DETECTED" ? 1 : s === "CONFIRMED" ? 2 : 3)
  return [...changes].sort((a, b) => rank(a.status) - rank(b.status) || (a.effectiveDate ?? "9999").localeCompare(b.effectiveDate ?? "9999") || b.raisedAt.localeCompare(a.raisedAt))
}

/** Open work first on a client's list; finished impacts go to the bottom. */
export function impactsOrdered(impacts: Impact[]): Impact[] {
  const live = (s: string) => (s === "OPEN" || s === "NOTICE_GIVEN" ? 0 : 1)
  return [...impacts].sort((a, b) => live(a.status) - live(b.status) || (a.dueDate ?? "9999").localeCompare(b.dueDate ?? "9999") || a.changeTitle.localeCompare(b.changeTitle))
}

export const progress = (c: Pick<Change, "impactsOpen" | "impactsTotal">) =>
  c.impactsTotal === 0 ? "No clients affected" : `${c.impactsTotal - c.impactsOpen} of ${c.impactsTotal} clients dealt with`
