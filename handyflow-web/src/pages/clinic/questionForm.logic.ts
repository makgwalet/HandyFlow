// src/pages/clinic/questionForm.logic.ts
// Pure helpers for the clinical question form: turning what a person types into the value shapes the API
// stores (see AnswerValidator on the server). No React in here so it can be tested directly.

export type AnswerValue = boolean | number | string | string[] | { value: number; unit: string } | null | undefined
export type Answers = Record<string, AnswerValue>

export interface QuestionOption { value: string; label?: string }

export const DURATION_UNITS = ["MINUTES", "HOURS", "DAYS", "WEEKS", "MONTHS", "YEARS"] as const

export const BODY_REGIONS: { value: string; label: string }[] = [
  { value: "HEAD", label: "Head" }, { value: "NECK", label: "Neck" }, { value: "CHEST", label: "Chest" },
  { value: "ABDOMEN", label: "Abdomen" }, { value: "BACK", label: "Back" },
  { value: "LEFT_ARM", label: "Left arm" }, { value: "RIGHT_ARM", label: "Right arm" },
  { value: "LEFT_LEG", label: "Left leg" }, { value: "RIGHT_LEG", label: "Right leg" },
  { value: "PELVIS", label: "Pelvis" }, { value: "SKIN", label: "Skin" },
]

/** "12" -> 12, "12.5" -> 12.5, "" or "abc" -> null. Whole numbers stay whole so NUMBER questions validate. */
export function toNumber(text: string): number | null {
  const t = text.trim()
  if (t === "") return null
  const n = Number(t)
  return Number.isFinite(n) ? n : null
}

/** Add or remove one value from a multi-select list, keeping the list free of duplicates. */
export function toggleInList(current: AnswerValue, value: string): string[] {
  const list = Array.isArray(current) ? current.filter(x => x !== value) : []
  const had = Array.isArray(current) && current.includes(value)
  return had ? list : [...list, value]
}

/** DURATION and MEASUREMENT share the {value, unit} shape. A blank number clears the answer. */
export function withUnit(current: AnswerValue, patch: { value?: string; unit?: string }, defaultUnit: string): AnswerValue {
  const prev = (current && typeof current === "object" && !Array.isArray(current)) ? current : { value: NaN, unit: defaultUnit }
  const unit = patch.unit ?? prev.unit ?? defaultUnit
  const num = patch.value !== undefined ? toNumber(patch.value) : (Number.isFinite(prev.value) ? prev.value : null)
  return num === null ? null : { value: num, unit }
}

/** Drops empty answers so the stored form only holds what was actually answered. */
export function cleanAnswers(a: Answers): Answers {
  const out: Answers = {}
  for (const [k, v] of Object.entries(a)) {
    if (v === null || v === undefined) continue
    if (typeof v === "string" && v.trim() === "") continue
    if (Array.isArray(v) && v.length === 0) continue
    out[k] = v
  }
  return out
}

/** Options for a MEASUREMENT question are its allowed units. */
export function unitOptions(options: QuestionOption[] | undefined, fallback: string[]): string[] {
  const vals = (options ?? []).map(o => o.value).filter(Boolean)
  return vals.length > 0 ? vals : fallback
}

export function scaleRange(min: number | null | undefined, max: number | null | undefined): number[] {
  const lo = min ?? 0, hi = max ?? 10
  const out: number[] = []
  for (let i = Math.ceil(lo); i <= Math.floor(hi) && out.length <= 101; i++) out.push(i)
  return out
}

/** Human-readable form of a stored answer, for read-only views. Option values are shown by their label. */
export function formatAnswer(answerType: string, value: AnswerValue, options: QuestionOption[] = []): string {
  if (value === null || value === undefined) return ""
  const labelOf = (v: string) => options.find(o => o.value === v)?.label ?? v
  if (typeof value === "boolean") return value ? "Yes" : "No"
  if (Array.isArray(value)) {
    if (answerType === "BODY") return value.map(v => BODY_REGIONS.find(r => r.value === v)?.label ?? v).join(", ")
    return value.map(labelOf).join(", ")
  }
  if (typeof value === "object") {
    const unit = answerType === "DURATION" ? value.unit.toLowerCase() : value.unit
    return `${value.value} ${unit}`
  }
  if (typeof value === "number") return String(value)
  return answerType === "SINGLE_SELECT" || answerType === "RADIO_GROUP" ? labelOf(value) : value
}
