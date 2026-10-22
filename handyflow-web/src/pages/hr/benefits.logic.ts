// src/pages/hr/benefits.logic.ts
//
// Employer-paid benefits that are taxed but not paid in cash. Mirrors BenefitRules on the server, which has the last word.
export type BenefitKind = "EMPLOYER_MEDICAL" | "EMPLOYER_RETIREMENT" | "OTHER_FRINGE"

export type Benefit = {
  id: string; employeeId: string; kind: BenefitKind; kindLabel: string; monthlyAmount: number
  effectiveFrom: string; effectiveTo: string | null; description: string | null; reason: string | null; inForce: boolean
}

export type BenefitForm = { kind: BenefitKind | ""; amount: string; from: string; to: string; description: string; reason: string }

export const EMPTY_BENEFIT_FORM: BenefitForm = { kind: "", amount: "", from: "", to: "", description: "", reason: "" }

export const BENEFIT_KINDS: { value: BenefitKind; label: string }[] = [
  { value: "EMPLOYER_MEDICAL", label: "Employer medical scheme contribution" },
  { value: "EMPLOYER_RETIREMENT", label: "Employer retirement fund contribution" },
  { value: "OTHER_FRINGE", label: "Other taxable benefit" },
]

/** Prepare rights only: the server also checks. The old permissions stay open for now, as in payroll. */
export function canEditBenefits(permissions: string[]): boolean {
  return ["PAYROLL_PREPARE", "PAYROLL_RUN", "USER_UPDATE"].some(p => permissions.includes(p))
}

export function benefitProblem(f: BenefitForm, existing: Benefit[]): string {
  if (!f.kind) return "Choose the kind of benefit."
  if (!/^\d+(\.\d{1,2})?$/.test(f.amount.trim()) || Number(f.amount) <= 0) return "Enter the monthly amount, in rand, with at most two decimals."
  if (!f.from) return "Say from which date it applies."
  if (f.to && f.to < f.from) return "The end date cannot be before the start date."
  if (f.kind === "OTHER_FRINGE" && !f.description.trim()) return "Describe the benefit (for example, the company car)."
  if (f.description.trim().length > 200) return "The description is too long (200 characters at most)."
  if (!f.reason.trim()) return "Give the reason for the benefit or the change."
  if (f.reason.trim().length > 300) return "The reason is too long (300 characters at most)."
  if (f.kind !== "OTHER_FRINGE") {
    const to = f.to || "9999-12-31"
    if (existing.some(b => b.kind === f.kind && b.effectiveFrom <= to && (b.effectiveTo ?? "9999-12-31") >= f.from))
      return "This employee already has this benefit for part of that time. End it first, then add the new amount."
  }
  return ""
}

export function benefitBody(f: BenefitForm) {
  return {
    kind: f.kind, monthlyAmount: Number(f.amount), effectiveFrom: f.from, effectiveTo: f.to || null,
    description: f.description.trim() || null, reason: f.reason.trim(),
  }
}

export function endProblem(b: Benefit, lastDay: string): string {
  if (!lastDay) return "Say the last day the benefit applies."
  if (lastDay < b.effectiveFrom) return `The last day cannot be before the benefit started (${b.effectiveFrom}).`
  return ""
}

export const money = (n: number) => n.toLocaleString("en-ZA", { style: "currency", currency: "ZAR" })
