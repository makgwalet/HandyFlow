// Rules for customer segments: which conditions can be chosen, what each one needs, and the sentence that describes it.
// The server holds the real rules and works out who matches; this file only builds and checks what is sent.
import { LEAD_SOURCES, STAGE_LABEL, STAGES } from "./pipeline.logic"
import type { CustomField } from "./customFields.logic"

export type BuiltInFieldId = "TYPE" | "STATUS" | "SOURCE" | "STAGE" | "OWNER" | "TAG" | "DEAL_VALUE" | "CREATED" | "UPDATED" | "EMAIL" | "PHONE"
/** A custom field is written CF: followed by its key, like CF:fleet_size. */
export type FieldId = BuiltInFieldId | `CF:${string}`
export type Input = "choice" | "text" | "number" | "days" | "date" | "none"
export const CUSTOM_PREFIX = "CF:"
export interface Choice { id: string; label: string }
export interface FieldDef { id: FieldId; label: string; input: Input; ops: Choice[]; choices?: Choice[]; custom?: boolean }
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
const CUSTOM_SET = [{ id: "IS_SET", label: "is filled in" }, { id: "IS_NOT_SET", label: "is empty" }]
export const MAX_CUSTOM_VALUE = 500
const YES_NO_CHOICES = [{ id: "yes", label: "Yes" }, { id: "no", label: "No" }]
/** The conditions a custom field offers, by kind. Every kind can also be tested for filled in / empty. */
export function customDef(f: CustomField): FieldDef {
  const id = `${CUSTOM_PREFIX}${f.key}` as FieldId, options = f.options.map(o => ({ id: o, label: o }))
  const base = { id, label: f.label, custom: true }
  switch (f.type) {
    case "NUMBER": return { ...base, input: "number", ops: [{ id: "GREATER_THAN", label: "is more than" }, { id: "LESS_THAN", label: "is less than" }, { id: "EQUALS", label: "equals" }, ...CUSTOM_SET] }
    case "DATE": return { ...base, input: "date", ops: [{ id: "BEFORE", label: "is before" }, { id: "AFTER", label: "is after" }, ...CUSTOM_SET] }
    case "CHOICE": return { ...base, input: "choice", choices: options, ops: [...IS, ...CUSTOM_SET] }
    case "MULTI_CHOICE": return { ...base, input: "choice", choices: options, ops: [{ id: "HAS", label: "includes" }, { id: "NOT_HAS", label: "does not include" }, ...CUSTOM_SET] }
    case "YES_NO": return { ...base, input: "choice", choices: YES_NO_CHOICES, ops: [{ id: "IS", label: "is" }, ...CUSTOM_SET] }
    default: return { ...base, input: "text", ops: [{ id: "CONTAINS", label: "contains" }, { id: "IS", label: "is" }, ...CUSTOM_SET] }
  }
}
/** The fields a condition can be about: the built-in ones, then the account's active custom fields. */
export const allFields = (custom: CustomField[] = []): FieldDef[] => [...FIELDS, ...custom.filter(f => f.active).map(customDef)]
/** A saved condition on a custom field that has since been switched off. It can be read, not saved. */
const removedDef = (id: FieldId): FieldDef => ({ id, label: "A removed field", input: "text", ops: [{ id: "IS", label: "is" }], custom: true })
export const fieldDef = (id: FieldId, defs: FieldDef[] = FIELDS): FieldDef => defs.find(f => f.id === id) ?? (id.startsWith(CUSTOM_PREFIX) ? removedDef(id) : FIELDS[0])
const isSetOp = (op: string) => op === "IS_SET" || op === "IS_NOT_SET"
/** Whether the condition needs a value typed or chosen. */
export const needsValue = (c: Condition, defs: FieldDef[] = FIELDS): boolean => { const d = fieldDef(c.field, defs); return d.input !== "none" && !(d.custom && isSetOp(c.op)) }
export const isRemoved = (c: Condition, defs: FieldDef[] = FIELDS) => c.field.startsWith(CUSTOM_PREFIX) && !defs.some(f => f.id === c.field)

/** A fresh condition for a field, with its first comparison and no value. */
export const newCondition = (field: FieldId = "TYPE", defs: FieldDef[] = FIELDS): Condition => ({ field, op: fieldDef(field, defs).ops[0].id, value: "" })
/** Changing the field resets the comparison and value, which only make sense for the old field. */
export const withField = (_c: Condition, field: FieldId, defs: FieldDef[] = FIELDS): Condition => newCondition(field, defs)

function customProblem(c: Condition, d: FieldDef, defs: FieldDef[]): string | null {
  if (isRemoved(c, defs)) return "That custom field is no longer in use. Remove this condition."
  if (isSetOp(c.op)) return null
  const v = c.value.trim()
  if (!v) return d.input === "choice" ? `Choose a value for ${d.label}.` : d.input === "date" ? `Choose a date for ${d.label}.` : `Enter a value for ${d.label}.`
  if (v.length > MAX_CUSTOM_VALUE) return `The value can be at most ${MAX_CUSTOM_VALUE} characters.`
  if (d.input === "number" && !Number.isFinite(Number(v.replace(/\s/g, "")))) return `${d.label} must be compared with a number.`
  if (d.input === "date" && !(/^\d{4}-\d{2}-\d{2}$/.test(v) && new Date(v).toISOString().slice(0, 10) === v)) return "Use a real date."
  return null
}
export function conditionProblem(c: Condition, defs: FieldDef[] = FIELDS): string | null {
  const d = fieldDef(c.field, defs), v = c.value.trim()
  if (d.custom) return customProblem(c, d, defs)
  switch (d.input) {
    case "none": return null
    case "date": return null
    case "choice": return v ? null : `Choose a value for ${d.label.toLowerCase()}.`
    case "text": return v ? (v.length > 40 ? "A tag can be at most 40 characters." : null) : "Enter a tag."
    case "number": { if (!v) return "Enter a deal value."; const n = Number(v.replace(/\s/g, "")); return !Number.isFinite(n) ? "Deal value must be a number." : n < 0 ? "A deal value cannot be negative." : null }
    case "days": { if (!v) return "Enter a number of days."; const n = Number(v); return !Number.isInteger(n) || n < 1 || n > 3650 ? "Days must be a whole number from 1 to 3650." : null }
  }
}
export function segmentProblem(name: string, conditions: Condition[], defs: FieldDef[] = FIELDS): string | null {
  if (!name.trim()) return "Give the segment a name."
  if (name.trim().length > MAX_NAME) return `The name can be at most ${MAX_NAME} characters.`
  if (conditions.length === 0) return "Add at least one condition."
  if (conditions.length > MAX_CONDITIONS) return `A segment can have at most ${MAX_CONDITIONS} conditions.`
  for (const c of conditions) { const p = conditionProblem(c, defs); if (p) return p }
  return null
}
export const toRequest = (name: string, conditions: Condition[], defs: FieldDef[] = FIELDS) => ({
  name: name.trim(),
  conditions: conditions.map(c => ({ field: c.field, op: c.op, value: !needsValue(c, defs) ? "" : c.field === "DEAL_VALUE" || fieldDef(c.field, defs).input === "number" ? c.value.replace(/\s/g, "") : c.value.trim() })),
})

/** A condition as a sentence: "Lead source is Referral". */
export function describe(c: Condition, owners: Record<string, string> = {}, defs: FieldDef[] = FIELDS): string {
  const d = fieldDef(c.field, defs), op = d.ops.find(o => o.id === c.op)?.label ?? [...CUSTOM_SET, ...d.ops].find(o => o.id === c.op)?.label ?? c.op
  if (d.input === "none" || (d.custom && isSetOp(c.op))) return `${d.label} ${op}`
  if (d.custom) return `${d.label} ${op} ${d.choices?.find(x => x.id === c.value)?.label ?? c.value}`
  if (d.input === "days") return `${d.label} ${c.op === "WITHIN_DAYS" ? "in the last" : "more than"} ${c.value} ${c.value === "1" ? "day" : "days"}${c.op === "WITHIN_DAYS" ? "" : " ago"}`
  if (c.field === "OWNER") return `${d.label} ${op} ${c.value === UNASSIGNED ? "nobody" : owners[c.value] ?? "a former colleague"}`
  const shown = d.choices?.find(x => x.id === c.value)?.label ?? c.value
  return `${d.label} ${op} ${c.field === "DEAL_VALUE" ? `R ${Number(c.value).toLocaleString("en-ZA")}` : shown}`
}
export const countText = (n: number) => `${n} ${n === 1 ? "customer" : "customers"}`
