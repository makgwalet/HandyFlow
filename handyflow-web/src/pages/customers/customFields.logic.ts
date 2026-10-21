// Custom fields an account defines for its customers: labels, checking what is typed, showing stored values. Pure functions.
export type CustomFieldType = "TEXT" | "NUMBER" | "DATE" | "CHOICE" | "YES_NO" | "MULTI_CHOICE" | "EMAIL" | "URL"
export interface CustomField { id: string; key: string; label: string; type: CustomFieldType; required: boolean; options: string[]; sortOrder: number; active: boolean }
export const FIELD_TYPES: { id: CustomFieldType; label: string }[] = [
  { id: "TEXT", label: "Text" }, { id: "NUMBER", label: "Number" }, { id: "DATE", label: "Date" }, { id: "CHOICE", label: "Choice from a list" }, { id: "YES_NO", label: "Yes / No" },
  { id: "MULTI_CHOICE", label: "Several choices from a list" }, { id: "EMAIL", label: "Email address" }, { id: "URL", label: "Web address" },
]
/** Choice fields are defined with a list of options. */
export const hasOptions = (t: CustomFieldType | "") => t === "CHOICE" || t === "MULTI_CHOICE"
export const MULTI_SEPARATOR = "|"
/** The choices ticked in a several-choices value ("Tyres|Wash"). */
export const multiValues = (v: string | undefined): string[] => (v ?? "").split(MULTI_SEPARATOR).map(x => x.trim()).filter(Boolean)
/** Ticks or unticks one option and returns the value to store, in the order of the field's options. */
export function toggleMulti(f: CustomField, current: string | undefined, option: string, on: boolean): string {
  const picked = new Set(multiValues(current))
  if (on) picked.add(option); else picked.delete(option)
  return f.options.filter(o => picked.has(o)).join(MULTI_SEPARATOR)
}
const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s.]+$/
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
  if (hasOptions(type)) {
    const opts = parseOptions(optionsText), n = opts.length
    if (n < 2) return "A choice field needs at least two options."
    if (n > 30) return "A choice field can have at most 30 options."
    if (type === "MULTI_CHOICE" && opts.some(o => o.includes(MULTI_SEPARATOR))) return "Options cannot contain the | character."
  }
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
  if (f.type === "MULTI_CHOICE") { const picked = multiValues(v); if (picked.length === 0) return `${f.label} needs at least one choice.`; if (picked.some(x => !f.options.includes(x))) return `${f.label} must only include the options.` }
  if (f.type === "EMAIL" && (v.length > 254 || !EMAIL_RE.test(v))) return `${f.label} must be an email address.`
  if (f.type === "URL" && (v.length > 500 || !/^https?:\/\/\S+$/i.test(v))) return `${f.label} must be a web address starting with http:// or https://.`
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
  if (f.type === "MULTI_CHOICE") return multiValues(v).join(", ")
  return v
}
/** Active fields only, in the order the account chose. */
export const activeFields = (fs: CustomField[]) => [...fs].filter(f => f.active).sort((a, b) => a.sortOrder - b.sortOrder || a.label.localeCompare(b.label))
