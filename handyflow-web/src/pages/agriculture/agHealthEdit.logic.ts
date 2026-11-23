// src/pages/agriculture/agHealthEdit.logic.ts
//
// The health-event edit form: its starting values, what is wrong with them, what changed, and the request to send.
// PUT /api/v1/agriculture/health-events/{id} replaces description (when given), product, dosage, vet, cost, the three withdrawal
// periods, next due date and notes. Type, date, status and who it was for are not editable. The server repeats the checks.
import { periodProblem } from "./agCompliance.logic"

export interface HealthEditValues {
  description: string; productUsed: string; dosage: string; veterinarian: string; cost: string
  withdrawalPeriodDays: string; milkWithdrawalDays: string; eggWithdrawalDays: string; nextDueDate: string; notes: string
}

export interface HealthEventLike {
  description: string; productUsed: string | null; dosage: string | null; veterinarian: string | null; cost: number | null
  withdrawalPeriodDays: number | null; milkWithdrawalDays?: number | null; eggWithdrawalDays?: number | null
  nextDueDate: string | null; notes: string | null
}

const s = (v: string | number | null | undefined) => (v == null ? "" : String(v))

export function editValuesFrom(e: HealthEventLike): HealthEditValues {
  return {
    description: e.description, productUsed: s(e.productUsed), dosage: s(e.dosage), veterinarian: s(e.veterinarian), cost: s(e.cost),
    withdrawalPeriodDays: s(e.withdrawalPeriodDays), milkWithdrawalDays: s(e.milkWithdrawalDays), eggWithdrawalDays: s(e.eggWithdrawalDays),
    nextDueDate: s(e.nextDueDate), notes: s(e.notes),
  }
}

/** Why this cannot be saved, or null. */
export function editError(v: HealthEditValues): string | null {
  if (!v.description.trim()) return "Describe what was done."
  if (v.cost.trim() !== "" && !(Number(v.cost) >= 0)) return "The cost cannot be negative."
  return periodProblem(v.withdrawalPeriodDays, "Withdrawal period") || periodProblem(v.milkWithdrawalDays, "Milk withdrawal") || periodProblem(v.eggWithdrawalDays, "Egg withdrawal") || null
}

const norm = (v: string) => v.trim()
const FIELDS: (keyof HealthEditValues)[] = ["description", "productUsed", "dosage", "veterinarian", "cost", "withdrawalPeriodDays", "milkWithdrawalDays", "eggWithdrawalDays", "nextDueDate", "notes"]

export function hasChanges(original: HealthEventLike, v: HealthEditValues): boolean {
  const o = editValuesFrom(original)
  return FIELDS.some(k => norm(o[k]) !== norm(v[k]))
}

/** Plain warnings for the changes that have knock-on effects. */
export function changeWarnings(original: HealthEventLike, v: HealthEditValues): string[] {
  const o = editValuesFrom(original)
  const out: string[] = []
  if (norm(o.nextDueDate) !== norm(v.nextDueDate)) out.push(v.nextDueDate.trim() ? "A new due date starts a new reminder." : "Clearing the due date removes the reminder.")
  if (["withdrawalPeriodDays", "milkWithdrawalDays", "eggWithdrawalDays"].some(k => norm(o[k as keyof HealthEditValues]) !== norm(v[k as keyof HealthEditValues])))
    out.push("Changing a withdrawal period changes when this animal or its produce can be sold.")
  return out
}

const num = (v: string) => (v.trim() === "" ? null : Number(v))
const text = (v: string) => (v.trim() === "" ? null : v.trim())

export function editPayload(v: HealthEditValues) {
  return {
    description: v.description.trim(), productUsed: text(v.productUsed), dosage: text(v.dosage), veterinarian: text(v.veterinarian), cost: num(v.cost),
    withdrawalPeriodDays: num(v.withdrawalPeriodDays), milkWithdrawalDays: num(v.milkWithdrawalDays), eggWithdrawalDays: num(v.eggWithdrawalDays),
    nextDueDate: text(v.nextDueDate), notes: text(v.notes),
  }
}
