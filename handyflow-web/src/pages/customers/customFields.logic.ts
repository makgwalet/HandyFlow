// Custom fields an account defines for its customers: labels, checking what is typed, showing stored values. Pure functions.
export type CustomFieldType = "TEXT" | "NUMBER" | "DATE" | "CHOICE" | "YES_NO"
export interface CustomField { id: string; key: string; label: string; type: CustomFieldType; required: boolean; options: string[]; sortOrder: number; active: boolean }
export const FIELD_TYPES: { id: CustomFieldType; label: string }[] = [
  { id: "TEXT", label: "Text" }, { id: "NUMBER", label: "Number" }, { id: "DATE", label: "Date" }, { id: "CHOICE", label: "Choice from a list" }, { id: "YES_NO", label: "Yes / No" },
]
export const typeLabel = (t: CustomFieldType) => FIELD_TYPES.find(x => x.id === t)?.label ?? t
export const MAX_TEXT = 500
export const MAX_LABEL = 60

/** "North, South, , north" becomes ["North", "South"]: trimmed, blanks and repeats (ignoring case) dropped. */
export function parseOptions(text: string): string[] {
  const seen = new Set<string>(), out: string[] = []
  for (const part of text.split(/[\n,]/)) { const t = part.trim(); if (t && !seen.has(t.toLowerCase())) { seen.add(t.toLowerCase()); out.push(t) } }
  return out
}
/** Why a field definition cannot be saved yet, or null. The server checks the same rules. */
export function definitionProblem(label: string, type: CustomFieldType | "", optionsText: string): string | null {
  if (!label.trim()) return "Give the field a name."
  if (label.trim().length > MAX_LABEL) return `The name can be at most ${MAX_LABEL} characters.`
  if (!type) return "Choose what kind of field this is."
  if (type === "CHOICE") { const n = parseOptions(optionsText).length; if (n < 2) return "A choice field needs at least two options."; if (n > 30) return "A choice field can have at most 30 options." }
  return null
}
const isoDate = (s: string) => /^\d{4}-\d{2}-\d{2}$/.test(s) && !Number.isNaN(Date.parse(s)) && new Date(s).toISOString().slice(0, 10) === s
/** Why a typed value is not acceptable for the field, or null. A blank is fine unless the field is required. */
export function valueProblem(f: CustomField, raw: string): string | null {
  const v = (raw ?? "").trim()
  if (!v) return f.required ? `${f.label} is required.` : null
  if (f.type === "TEXT" && v.length > MAX_TEXT) return `${f.label} can be at most ${MAX_TEXT} characters.`
  if (f.type === "NUMBER") { const n = Number(v.replace(/\s/g, "")); if (!Number.isFinite(n)) return `${f.label} must be a number.` }
  if (f.type === "DATE" && !isoDate(v)) return `${f.label} must be a date like 2026-11-30.`
  if (f.type === "CHOICE" && !f.options.includes(v)) return `${f.label} must be one of the options.`
  if (f.type === "YES_NO" && !["yes", "no"].includes(v.toLowerCase())) return `${f.label} must be yes or no.`
  return null
}
/** The first problem among the active fields' values, or null. */
export function firstValueProblem(fields: CustomField[], values: Record<string, string>): string | null {
  for (const f of fields.filter(x => x.active)) { const p = valueProblem(f, values[f.key] ?? ""); if (p) return p }
  return null
}
/** Only the active fields' values, blanks removed, as the server expects. */
export function valuesBody(fields: CustomField[], values: Record<string, string>): Record<string, string> {
  const out: Record<string, string> = {}
  for (const f of fields.filter(x => x.active)) { const v = (values[f.key] ?? "").trim(); if (v) out[f.key] = v }
  return out
}
/** How a stored value reads on screen. */
export function displayValue(f: CustomField, v: string | undefined): string {
  if (v == null || v === "") return "—"
  if (f.type === "YES_NO") return v === "yes" ? "Yes" : v === "no" ? "No" : v
  return v
}
/** Active fields only, in the order the account chose. */
export const activeFields = (fs: CustomField[]) => [...fs].filter(f => f.active).sort((a, b) => a.sortOrder - b.sortOrder || a.label.localeCompare(b.label))
