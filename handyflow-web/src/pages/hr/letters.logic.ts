// Pure helpers for the letter centre. The server fills templates in, decides who may see a letter and what may be done to it; this
// only words things, finds the gaps still to fill, and catches obvious mistakes before a request is sent.

export interface LetterRights { read: boolean; manage: boolean; issue: boolean }

export function letterRights(permissions: string[]): LetterRights {
  const has = (p: string) => permissions.includes(p)
  return { read: has("HR_LETTER_READ") || has("HR_LETTER_MANAGE") || has("HR_LETTER_ISSUE"), manage: has("HR_LETTER_MANAGE"), issue: has("HR_LETTER_ISSUE") }
}

export const STATUS_LABEL: Record<string, string> = { DRAFT: "Draft", ISSUED: "Issued", VOID: "Void" }

/** Colour tokens for a status chip: [text, background]. */
export function statusColors(status: string): [string, string] {
  switch (status) {
    case "DRAFT": return ["var(--hf-warning-text-strong)", "var(--hf-warning-soft)"]
    case "ISSUED": return ["var(--hf-success-text-strong)", "var(--hf-success-soft-strong)"]
    default: return ["var(--hf-text-muted)", "var(--hf-surface-sunken)"]
  }
}

export const CATEGORY_LABEL: Record<string, string> = {
  EMPLOYMENT: "Employment", DISCIPLINARY: "Discipline", GRIEVANCE: "Grievance", INCAPACITY: "Incapacity", GENERAL: "General",
}

export const ACTION_LABEL: Record<string, string> = { EDIT: "Edit the wording", ISSUE: "Issue the letter", VOID: "Void", DOWNLOAD: "Download PDF" }

/** The gaps still marked in a letter: "[[Venue]]" gives "Venue". */
export function gapsIn(text: string): string[] {
  const out: string[] = []
  for (const m of text.matchAll(/\[\[([^[\]]{1,120})\]\]/g)) if (!out.includes(m[1])) out.push(m[1])
  return out
}

/** Splits text so gaps can be shown highlighted: [{ text, gap }]. */
export function segments(text: string): { text: string; gap: boolean }[] {
  const out: { text: string; gap: boolean }[] = []
  let last = 0
  for (const m of text.matchAll(/\[\[([^[\]]{1,120})\]\]/g)) {
    const at = m.index ?? 0
    if (at > last) out.push({ text: text.slice(last, at), gap: false })
    out.push({ text: m[0], gap: true })
    last = at + m[0].length
  }
  if (last < text.length) out.push({ text: text.slice(last), gap: false })
  return out
}

export function mergeFieldsIn(text: string): string[] {
  const out: string[] = []
  for (const m of text.matchAll(/\{\{\s*([A-Za-z0-9_.]+)\s*\}\}/g)) if (!out.includes(m[1])) out.push(m[1])
  return out
}

/** Null when a draft may be saved. */
export function draftProblem(subject: string, body: string): string | null {
  if (!subject.trim()) return "Give the letter a subject."
  if (subject.length > 200) return "The subject is too long (200 characters at most)."
  if (!body.trim()) return "The letter has no wording."
  if (mergeFieldsIn(subject).length > 0 || mergeFieldsIn(body).length > 0) return "The letter still contains {{merge fields}}. A draft is the final wording."
  return null
}

/** Null when a draft may be issued. */
export function issueProblem(subject: string, body: string): string | null {
  const p = draftProblem(subject, body)
  if (p) return p
  const gaps = [...new Set([...gapsIn(subject), ...gapsIn(body)])]
  return gaps.length ? `Fill in the gaps before issuing: ${gaps.join(", ")}.` : null
}

/** Null when a template may be saved. `known` is the list of merge field keys the server allows. */
export function templateProblem(f: { name: string; subject: string; body: string }, known: string[]): string | null {
  if (!f.name.trim()) return "Give the template a name."
  if (!f.subject.trim()) return "Give the template a subject line."
  if (!f.body.trim()) return "The template has no wording."
  if (f.subject.includes("[[") || f.body.includes("[[") || f.subject.includes("]]") || f.body.includes("]]"))
    return "Square double brackets [[ ]] mark gaps in a draft. Use {{field}} for merge fields."
  const unknown = [...mergeFieldsIn(f.subject), ...mergeFieldsIn(f.body)].filter(k => !known.includes(k))
  if (unknown.length) return `These merge fields do not exist: ${[...new Set(unknown)].join(", ")}.`
  const stripped = `${f.subject} ${f.body}`.replace(/\{\{\s*[A-Za-z0-9_.]+\s*\}\}/g, "")
  if (stripped.includes("{{") || stripped.includes("}}")) return "A merge field is not written correctly. Write it as {{employee.name}}."
  return null
}

/** Templates that make sense for a case of this kind, plus the general ones. */
export function templatesForCase<T extends { category: string; active: boolean }>(templates: T[], caseType: string): T[] {
  return templates.filter(t => t.active && (t.category === caseType || t.category === "GENERAL"))
}

export function voidProblem(reason: string): string | null {
  const r = reason.trim()
  if (r.length < 3) return "Give the reason for voiding the letter."
  if (r.length > 500) return "The reason is too long (500 characters at most)."
  return null
}

export const BULK_MAX = 200
export const BULK_ISSUE_MAX = 100
const CASE_GROUPS = ["Case", "Hearing", "Outcome", "Suspension"]

/** Null when the template can be made for many people at once. The server decides again; this only saves a trip. */
export function bulkTemplateProblem(t: { category: string; fields: string[] }, fields: { key: string; group: string }[]): string | null {
  if (t.category !== "EMPLOYMENT" && t.category !== "GENERAL") return "Letters about a case are made from the case, not in bulk."
  const bad = fields.find(f => t.fields.includes(f.key) && CASE_GROUPS.includes(f.group))
  return bad ? "This template uses case details, so it cannot be made for many people at once." : null
}

/** Null when the request may be sent. */
export function bulkPrepareProblem(templateOk: boolean, people: number): string | null {
  if (!templateOk) return "Choose a template that can be used for many people."
  if (people < 1) return "Choose at least one employee."
  if (people > BULK_MAX) return `Choose at most ${BULK_MAX} employees at a time.`
  return null
}

/** Drafts with no gaps and no refusal are ready to issue. */
export function readyToIssue(items: { letterId: string | null; gaps: string[]; problem: string | null }[]): string[] {
  return items.filter(i => i.letterId && !i.problem && i.gaps.length === 0).map(i => i.letterId as string)
}
