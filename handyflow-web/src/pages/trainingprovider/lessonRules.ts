// Wording and checks for lessons and learner progress. The server enforces every rule.
// Finishing a lesson is progress only. It is not attendance, a grade or an assessment outcome.

export type Lesson = {
  id: string; lessonNo: string; programmeVersionId: string; versionNo: number | null; moduleId: string | null; moduleTitle: string | null; title: string; kind: string
  contentText: string | null; url: string | null; position: number; status: string; approvedAt: string | null; approvalOverrideReason: string | null
  retiredReason: string | null; nextStatuses: string[]
}
export type ProgressRow = { lessonId: string; lessonNo: string; title: string; kind: string; moduleId: string | null; position: number; progressId: string | null; finishedOn: string | null; note: string | null }
export type Progress = { enrolmentId: string; enrolmentNo: string; learnerName: string | null; programmeTitle: string | null; versionNo: number | null; rows: ProgressRow[]; finished: number; total: number; summary: string }

export const MIN_REASON = 10
export const KINDS: Record<string, string> = { READING: "Reading", LINK: "Link", DOCUMENT: "Document link" }
export const STATUSES: Record<string, string> = { DRAFT: "Draft", APPROVED: "Approved", RETIRED: "Retired" }
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")
export const PROGRESS_NOTE = "Marking a lesson as finished records progress only. It is not attendance and not an assessment outcome."

export function statusTone(s: string): "good" | "warn" | "bad" | "neutral" {
  return s === "APPROVED" ? "good" : s === "DRAFT" ? "warn" : "neutral"
}

const long = (s: string, n: number) => s.trim().length >= n
export const validUrl = (u: string) => /^https?:\/\/\S+$/i.test(u.trim())

export type LessonForm = { programmeId: string; programmeVersionId: string; moduleId: string; title: string; kind: string; contentText: string; url: string; position: string }
export const emptyLesson: LessonForm = { programmeId: "", programmeVersionId: "", moduleId: "", title: "", kind: "READING", contentText: "", url: "", position: "1" }
export const lessonForm = (l: Lesson): LessonForm => ({
  programmeId: "", programmeVersionId: l.programmeVersionId, moduleId: l.moduleId ?? "", title: l.title, kind: l.kind, contentText: l.contentText ?? "", url: l.url ?? "", position: String(l.position),
})

export function lessonProblem(f: LessonForm, isNew: boolean): string | null {
  if (isNew && !f.programmeVersionId) return "Choose the programme and version"
  if (!long(f.title, 2)) return "Give the lesson a title"
  if (!KINDS[f.kind]) return "Choose reading, link or document link"
  const pos = Number(f.position)
  if (!Number.isInteger(pos) || pos < 1) return "The position must be a whole number, 1 or more"
  if (f.kind === "READING" && !long(f.contentText, 20)) return "Write the reading, in at least 20 characters"
  if (f.kind !== "READING" && !validUrl(f.url)) return "Enter a link that starts with http:// or https://"
  return null
}
const content = (f: LessonForm) => ({
  moduleId: f.moduleId || null, title: f.title.trim(), kind: f.kind, contentText: f.kind === "READING" ? f.contentText.trim() : null, url: f.kind === "READING" ? null : f.url.trim(),
  position: Number(f.position),
})
export const lessonBody = (f: LessonForm) => ({ programmeVersionId: f.programmeVersionId, ...content(f) })
export const updateBody = (f: LessonForm) => content(f)

export const reasonProblem = (r: string): string | null => (long(r, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`)

export function approvalNote(l: Pick<Lesson, "status" | "approvedAt" | "approvalOverrideReason">): string {
  if (l.status === "DRAFT") return "Not approved yet. A second person should approve it."
  const when = l.approvedAt ? l.approvedAt.slice(0, 10) : ""
  return l.approvalOverrideReason ? `Approved on ${when} by its author, who said: ${l.approvalOverrideReason}` : `Approved on ${when} by a second person.`
}

export type FinishForm = { finishedOn: string; note: string }
export function finishProblem(f: FinishForm, startDate: string | null, today: string): string | null {
  if (!f.finishedOn) return "Enter the day it was finished"
  if (f.finishedOn > today) return "The day cannot be in the future"
  if (startDate && f.finishedOn < startDate) return "The day cannot be before the enrolment started"
  return null
}
