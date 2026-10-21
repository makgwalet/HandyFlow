// Merging two duplicate customer records: the preview the server sends, the choices the person makes, and the words used.
// The server holds the real rules (what is refused, what moves); this file shapes what is sent and shown.
import { LEAD_SOURCES, money } from "./pipeline.logic"

export interface MergeSide { id: string; name: string; email: string | null; phone: string | null; customerType: "LEAD" | "CUSTOMER"; createdAt: string }
export interface MergeConflict { field: string; label: string; keep: string; other: string }
export interface MergeMoves { contacts: number; tasks: number; communications: number; opportunities: number; campaigns: number; branches: number; activities: number }
export interface MergePreview { keep: MergeSide; merge: MergeSide; conflicts: MergeConflict[]; moves: MergeMoves; willFill: string[]; blockers: string[]; canMerge: boolean }
export interface MergeResult { id: string; name: string; filled: string[]; moved: MergeMoves; consentNote: string | null }

/** For each field where both records differ: keep the kept record's value (the default) or use the other's. */
export type Choices = Record<string, "keep" | "other">
export const NO_CHOICES: Choices = {}
/** The fields to take from the other record, in the order the server listed them. */
export const takeBody = (conflicts: MergeConflict[], choices: Choices) => conflicts.filter(c => choices[c.field] === "other").map(c => c.field)
export const mergeBody = (keepId: string, mergeId: string, conflicts: MergeConflict[], choices: Choices) => ({ keepId, mergeId, take: takeBody(conflicts, choices) })

const FIELD_NAMES: Record<string, string> = {
  name: "name", email: "email", phone: "phone", taxNumber: "tax number", address: "address", owner: "owner", source: "lead source", dealValue: "deal value", closeDate: "expected close date", notes: "notes",
}
/** "email, phone and 2 more" style list of fields, using the account's own labels for custom fields. */
export function fieldList(fields: string[], labels: Record<string, string> = {}): string {
  const names = fields.map(f => (f.startsWith("custom.") ? labels[f.slice(7)] ?? f.slice(7) : FIELD_NAMES[f] ?? f))
  return names.length <= 1 ? names.join("") : `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`
}

/** How a differing value reads: owners by name, sources by label, money as money, yes/no as words. */
export function valueText(field: string, v: string, owners: Record<string, string> = {}): string {
  if (field === "owner") return owners[v] ?? "A former colleague"
  if (field === "source") return LEAD_SOURCES.find(s => s.id === v)?.label ?? v
  if (field === "dealValue") return money(Number(v))
  if (v === "yes") return "Yes"
  if (v === "no") return "No"
  return v.split("|").join(", ")
}

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`
/** What will be moved onto the kept record, as a short list. Only things that exist are listed. */
export function movesList(m: MergeMoves): string[] {
  const out: string[] = []
  if (m.contacts) out.push(plural(m.contacts, "contact person", "contact people"))
  if (m.tasks) out.push(plural(m.tasks, "task", "tasks"))
  if (m.communications) out.push(plural(m.communications, "logged message", "logged messages"))
  if (m.opportunities) out.push(plural(m.opportunities, "opportunity", "opportunities"))
  if (m.campaigns) out.push(plural(m.campaigns, "campaign place", "campaign places"))
  if (m.branches) out.push(plural(m.branches, "branch", "branches"))
  if (m.activities) out.push(plural(m.activities, "timeline entry", "timeline entries"))
  return out
}

export const sameRecord = (keepId: string, mergeId: string) => !keepId || !mergeId || keepId === mergeId
/** The sentence that makes plain what merging does, naming both records. */
export const mergeSentence = (p: MergePreview) => `${p.merge.name} will be merged into ${p.keep.name} and removed. ${p.keep.name} stays.`
/** After a merge: what happened, in one or two sentences. */
export function resultText(r: MergeResult, labels: Record<string, string> = {}): string {
  const filled = r.filled.length ? ` It gained ${fieldList(r.filled, labels)}.` : ""
  return `Merged into ${r.name}.${filled}`
}
