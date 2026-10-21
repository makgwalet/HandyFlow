// Rules for customer segments: which conditions can be chosen, what each one needs, and the sentence that describes it.
// The server holds the real rules and works out who matches; this file only builds and checks what is sent.
import { LEAD_SOURCES, STAGE_LABEL, STAGES } from "./pipeline.logic"

export type FieldId = "TYPE" | "STATUS" | "SOURCE" | "STAGE" | "OWNER" | "TAG" | "DEAL_VALUE" | "CREATED" | "UPDATED" | "EMAIL" | "PHONE"
export type Input = "choice" | "text" | "number" | "days" | "none"
export interface Choice { id: string; label: string }
export interface FieldDef { id: FieldId; label: string; input: Input; ops: Choice[]; choices?: Choice[] }
export interface Condition { field: FieldId; op: string; value: string }
export interface SegmentRow { id: string; name: string; conditions: Condition[]; count: number }
export interface Member { id: string; name: string; email: string | null; phone: string | null; customerType: string | null; status: string | null }
export interface Members { total: number; truncated: boolean; members: Member[] }

export const MAX_CONDITIONS = 10
export const MAX_NAME = 60
export const UNASSIGNED = "UNASSIGNED"
const IS = [{ id: "IS", label: "is" }, { id: "IS_NOT", label: "is not" }]
const AGE = [{ id: "WITHIN_DAYS", label: "in the last (days)" }, { id: "OLDER_THAN_DAYS", label: "more than (days) ago" }]
const SET = [{ id: "IS_SET", label: "is filled in" }, { id: "IS_NOT_SET", label: "is empty" }]

export const FIELDS: FieldDef[] = [
  { id: "TYPE", label: "Type", input: "choice", ops: IS, choices: [{ id: "LEAD", label: "Lead" }, { id: "CUSTOMER", label: "Customer" }] },
  { id: "STATUS", label: "Status", input: "choice", ops: IS, choices: [{ id: "ACTIVE", label: "Active" }, { id: "INACTIVE", label: "Inactive" }, { id: "BLOCKED", label: "Blocked" }] },
  { id: "SOURCE", label: "Lead source", input: "choice", ops: IS, choices: LEAD_SOURCES },
  { id: "STAGE", label: "Pipeline stage", input: "choice", ops: IS, choices: STAGES.map(s => ({ id: s, label: STAGE_LABEL[s] })) },
  { id: "OWNER", label: "Owner", input: "choice", ops: IS },
  { id: "TAG", label: "Tag", input: "text", ops: [{ id: "HAS", label: "has" }, { id: "NOT_HAS", label: "does not have" }] },
  { id: "DEAL_VALUE", label: "Deal value (R)", input: "number", ops: [{ id: "GREATER_THAN", label: "is more than" }, { id: "LESS_THAN", label: "is less than" }] },
  { id: "CREATED", label: "Added", input: "days", ops: AGE },
  { id: "UPDATED", label: "Last changed", input: "days", ops: AGE },
  { id: "EMAIL", label: "Email", input: "none", ops: SET },
  { id: "PHONE", label: "Phone", input: "none", ops: SET },
]
export const fieldDef = (id: FieldId) => FIELDS.find(f => f.id === id)!

/** A fresh condition for a field, with its first comparison and no value. */
export const newCondition = (field: FieldId = "TYPE"): Condition => ({ field, op: fieldDef(field).ops[0].id, value: "" })
/** Changing the field resets the comparison and value, which only make sense for the old field. */
export const withField = (_c: Condition, field: FieldId): Condition => newCondition(field)

export function conditionProblem(c: Condition): string | null {
  const d = fieldDef(c.field), v = c.value.trim()
  switch (d.input) {
    case "none": return null
    case "choice": return v ? null : `Choose a value for ${d.label.toLowerCase()}.`
    case "text": return v ? (v.length > 40 ? "A tag can be at most 40 characters." : null) : "Enter a tag."
    case "number": { if (!v) return "Enter a deal value."; const n = Number(v.replace(/\s/g, "")); return !Number.isFinite(n) ? "Deal value must be a number." : n < 0 ? "A deal value cannot be negative." : null }
    case "days": { if (!v) return "Enter a number of days."; const n = Number(v); return !Number.isInteger(n) || n < 1 || n > 3650 ? "Days must be a whole number from 1 to 3650." : null }
  }
}
export function segmentProblem(name: string, conditions: Condition[]): string | null {
  if (!name.trim()) return "Give the segment a name."
  if (name.trim().length > MAX_NAME) return `The name can be at most ${MAX_NAME} characters.`
  if (conditions.length === 0) return "Add at least one condition."
  if (conditions.length > MAX_CONDITIONS) return `A segment can have at most ${MAX_CONDITIONS} conditions.`
  for (const c of conditions) { const p = conditionProblem(c); if (p) return p }
  return null
}
export const toRequest = (name: string, conditions: Condition[]) => ({
  name: name.trim(),
  conditions: conditions.map(c => ({ field: c.field, op: c.op, value: fieldDef(c.field).input === "none" ? "" : c.field === "DEAL_VALUE" ? c.value.replace(/\s/g, "") : c.value.trim() })),
})

/** A condition as a sentence: "Lead source is Referral". */
export function describe(c: Condition, owners: Record<string, string> = {}): string {
  const d = fieldDef(c.field), op = d.ops.find(o => o.id === c.op)?.label ?? c.op
  if (d.input === "none") return `${d.label} ${op}`
  if (d.input === "days") return `${d.label} ${c.op === "WITHIN_DAYS" ? "in the last" : "more than"} ${c.value} ${c.value === "1" ? "day" : "days"}${c.op === "WITHIN_DAYS" ? "" : " ago"}`
  if (c.field === "OWNER") return `${d.label} ${op} ${c.value === UNASSIGNED ? "nobody" : owners[c.value] ?? "a former colleague"}`
  const shown = d.choices?.find(x => x.id === c.value)?.label ?? c.value
  return `${d.label} ${op} ${c.field === "DEAL_VALUE" ? `R ${Number(c.value).toLocaleString("en-ZA")}` : shown}`
}
export const countText = (n: number) => `${n} ${n === 1 ? "customer" : "customers"}`
