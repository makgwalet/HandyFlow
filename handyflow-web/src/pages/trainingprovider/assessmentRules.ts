// Wording and checks for assessment instruments. The server enforces every rule.
// An instrument may carry a maximum mark. It never carries a pass mark: the mark and the competency decision are separate.

export type InstrumentEvent = { id: string; action: string; note: string | null; actedBy: string | null; actedAt: string }
export type Instrument = {
  id: string; instrumentNo: string; programmeId: string; programmeTitle: string | null; programmeVersionId: string; versionNo: number
  moduleId: string | null; moduleTitle: string | null; title: string; assessmentType: string; maxMark: number | null; guidance: string | null
  requiresModeration: boolean; status: string; approvedAt: string | null; approvalOverrideReason: string | null; retiredReason: string | null
  nextStatuses: string[]; history: InstrumentEvent[]
}

export const MIN_REASON = 10
export const TYPES: Record<string, string> = { KNOWLEDGE: "Knowledge", PRACTICAL: "Practical", WORKPLACE: "Workplace", INTEGRATED: "Integrated", PORTFOLIO: "Portfolio of evidence" }
export const STATUSES: Record<string, string> = { DRAFT: "Draft", APPROVED: "Approved", RETIRED: "Retired" }
export const ACTIONS: Record<string, string> = { CREATED: "Created", CHANGED: "Changed", APPROVED: "Approved", RETIRED: "Retired" }
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")

export function statusTone(s: string): "good" | "warn" | "bad" | "neutral" {
  return s === "APPROVED" ? "good" : s === "DRAFT" ? "warn" : "neutral"
}

const long = (s: string, n: number) => s.trim().length >= n

export type InstrumentForm = {
  programmeId: string; programmeVersionId: string; moduleId: string; title: string; assessmentType: string; maxMark: string; guidance: string; requiresModeration: boolean
}
export const emptyInstrument: InstrumentForm = {
  programmeId: "", programmeVersionId: "", moduleId: "", title: "", assessmentType: "KNOWLEDGE", maxMark: "", guidance: "", requiresModeration: true,
}
export const instrumentForm = (i: Instrument): InstrumentForm => ({
  programmeId: i.programmeId, programmeVersionId: i.programmeVersionId, moduleId: i.moduleId ?? "", title: i.title, assessmentType: i.assessmentType,
  maxMark: i.maxMark === null ? "" : String(i.maxMark), guidance: i.guidance ?? "", requiresModeration: i.requiresModeration,
})

export function instrumentProblem(f: InstrumentForm, isNew: boolean): string | null {
  if (isNew && !f.programmeVersionId) return "Choose the programme and the version this instrument belongs to"
  if (!long(f.title, 2)) return "Give the instrument a title"
  if (!TYPES[f.assessmentType]) return "Choose the type of assessment"
  if (f.maxMark.trim() !== "") {
    const n = Number(f.maxMark)
    if (!Number.isFinite(n) || n <= 0) return "The maximum mark must be more than 0, or left empty if the instrument is not marked"
  }
  return null
}

const blank = (s: string) => (s.trim() ? s.trim() : null)
export const instrumentBody = (f: InstrumentForm) => ({
  programmeVersionId: f.programmeVersionId, moduleId: f.moduleId || null, title: f.title.trim(), assessmentType: f.assessmentType,
  maxMark: f.maxMark.trim() === "" ? null : Number(f.maxMark), guidance: blank(f.guidance), requiresModeration: f.requiresModeration,
})
export const updateBody = (f: InstrumentForm) => {
  const { programmeVersionId: _v, ...rest } = instrumentBody(f)
  void _v
  return rest
}

export const reasonProblem = (r: string): string | null => (long(r, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`)

/** Plain statement shown on every instrument so nobody reads a mark scale as a pass rule. */
export const MARK_NOTE = "The maximum mark only sets the scale. Whether a learner is found competent is a decision an assessor records separately."

export function approvalNote(i: Pick<Instrument, "status" | "approvedAt" | "approvalOverrideReason">): string {
  if (i.status === "DRAFT") return "Not approved yet. A second person should approve it."
  const when = i.approvedAt ? i.approvedAt.slice(0, 10) : ""
  return i.approvalOverrideReason ? `Approved on ${when} by its author, who said: ${i.approvalOverrideReason}` : `Approved on ${when} by a second person.`
}
