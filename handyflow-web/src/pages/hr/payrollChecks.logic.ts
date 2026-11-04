// Pure helpers for the payroll checks screen and the medical scheme form.

export type Verdict = "NO_DIFFERENCE" | "POTENTIAL_DIFFERENCE" | "CONFIRMED_DIFFERENCE" | "REVIEW_REQUIRED"

export const VERDICT_TEXT: Record<Verdict, string> = {
  NO_DIFFERENCE: "No difference",
  POTENTIAL_DIFFERENCE: "Possible difference",
  CONFIRMED_DIFFERENCE: "Confirmed difference",
  REVIEW_REQUIRED: "Needs review",
}

export const VERDICT_STYLE: Record<Verdict, { color: string; bg: string }> = {
  NO_DIFFERENCE:        { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  POTENTIAL_DIFFERENCE: { color: "var(--hf-warning-text)",        bg: "var(--hf-warning-soft)" },
  CONFIRMED_DIFFERENCE: { color: "var(--hf-danger-text)",         bg: "var(--hf-danger-soft)" },
  REVIEW_REQUIRED:      { color: "var(--hf-info-text)",           bg: "var(--hf-info-soft)" },
}

export interface TaxYear {
  taxYear: number; label: string; effectiveFrom: string; effectiveTo: string; status: string; usable: boolean
  problems: string[]; bracketCount: number
  primaryRebate: number | null; secondaryRebate: number | null; tertiaryRebate: number | null
  mtcTaxpayer: number; mtcFirstDependant: number; mtcAdditional: number
  uifMonthlyCeiling: number; retirementCapAmount: number; retirementCapPct: number
  sourceNote: string; approvedAt: string | null; approvalNote: string | null
}

export interface ImpactLine {
  payslipId: string; employeeId: string; employeeName: string; employeeNumber: string; verdict: Verdict
  recordedPaye: number; recalculatedPaye: number | null; payeDifference: number | null
  uifDifference: number | null; sdlDifference: number | null
  medicalMembers: number | null; medicalAssumed: boolean; reasons: string[]
}

export interface ImpactRun {
  runId: string; runNumber: string; periodStart: string; periodEnd: string; payDate: string
  recordedTaxYear: number | null; correctTaxYear: number; status: string; emp201Status: string | null; payslips: number
  noDifference: number; potentialDifference: number; confirmedDifference: number; reviewRequired: number
  recordedPaye: number; recalculatedPaye: number; payeDifference: number
  verdict: Verdict; action: string; lines: ImpactLine[] | null
}

export interface Impact { generatedAt: string; runs: ImpactRun[]; runsWithDifferences: number; notes: string[] }

export interface MedicalScheme {
  id: string; schemeName: string; memberNumber: string | null; dependants: number; peopleCovered: number
  effectiveFrom: string; effectiveTo: string | null; current: boolean; notes: string | null
  spouses?: number; children?: number; otherDependants?: number; employeeContribution?: number | null; employerContribution?: number | null
}

/** The tax year is named by the year it ends, as SARS names it: 2027 is 2026/27. */
export function taxYearName(taxYear: number): string {
  return `${taxYear - 1}/${String(taxYear).slice(-2)}`
}

export function rand(n: number | null | undefined): string {
  if (n == null) return "—"
  return `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

/** A difference with its sign shown, so "+" means the recalculation is higher than what was paid. */
export function signedRand(n: number | null | undefined): string {
  if (n == null) return "—"
  if (Math.abs(n) < 0.005) return rand(0)
  return `${n > 0 ? "+" : "−"}${rand(Math.abs(n))}`
}

export function impactSummary(i: Pick<Impact, "runs" | "runsWithDifferences">): string {
  if (i.runs.length === 0) return "No processed pay runs on record, so there is nothing to compare."
  if (i.runsWithDifferences === 0) return `All ${i.runs.length} processed ${i.runs.length === 1 ? "run matches" : "runs match"} the recalculation. Nothing has been changed.`
  return `${i.runsWithDifferences} of ${i.runs.length} processed ${i.runs.length === 1 ? "run differs" : "runs differ"} from the recalculation. Nothing has been changed.`
}

/**
 * A scheme membership as typed. Cover is a spouse (0 or 1), children and other dependants; the contributions are monthly
 * amounts. {@code dependants} is the older single count: it is used only when none of the three split fields is filled.
 */
export interface SchemeForm {
  schemeName: string; memberNumber: string; dependants: string; effectiveFrom: string; notes: string
  spouses: string; children: string; other: string; employeeContribution: string; employerContribution: string
}
export const EMPTY_SCHEME_FORM: SchemeForm = {
  schemeName: "", memberNumber: "", dependants: "0", effectiveFrom: "", notes: "",
  spouses: "", children: "", other: "", employeeContribution: "", employerContribution: "",
}
/** What a person sees when starting a new membership: the split shown as zeros. */
export const NEW_SCHEME_FORM: SchemeForm = { ...EMPTY_SCHEME_FORM, spouses: "0", children: "0", other: "0" }

const usesSplit = (f: SchemeForm) => [f.spouses, f.children, f.other].some(v => v.trim() !== "")
const whole = (v: string) => (v.trim() === "" ? 0 : /^\d+$/.test(v.trim()) ? Number(v) : NaN)
const money = (v: string) => v.trim() === "" || (Number.isFinite(Number(v)) && Number(v) >= 0 && /^\d+(\.\d{1,2})?$/.test(v.trim()))

/** People covered besides the employee, from the split when it is used, otherwise from the single count. */
export function schemeDependants(f: SchemeForm): number {
  return usesSplit(f) ? whole(f.spouses) + whole(f.children) + whole(f.other) : whole(f.dependants)
}

/** First thing wrong with the form, in words for the user, or "" when it can be sent. The server checks again. */
export function schemeProblem(f: SchemeForm): string {
  if (!f.schemeName.trim()) return "Enter the scheme name."
  if (f.schemeName.trim().length > 120) return "The scheme name can be at most 120 characters."
  if (usesSplit(f)) {
    if (Number.isNaN(whole(f.spouses)) || whole(f.spouses) > 1) return "A member can cover one spouse or partner, or none."
    if (Number.isNaN(whole(f.children)) || Number.isNaN(whole(f.other))) return "Children and other dependants must be whole numbers, 0 or more."
  } else if (!/^\d+$/.test(f.dependants.trim())) return "Dependants must be a whole number, 0 or more."
  if (schemeDependants(f) > 20) return "Dependants can be at most 20."
  if (!money(f.employeeContribution)) return "The employee contribution must be an amount of 0 or more, with at most two decimals."
  if (!money(f.employerContribution)) return "The employer contribution must be an amount of 0 or more, with at most two decimals."
  if (!f.effectiveFrom) return "Choose the date the membership starts."
  return ""
}

export function schemeBody(f: SchemeForm) {
  return {
    schemeName: f.schemeName.trim(),
    memberNumber: f.memberNumber.trim() || null,
    dependants: schemeDependants(f),
    effectiveFrom: f.effectiveFrom,
    notes: f.notes.trim() || null,
    ...(usesSplit(f) ? { spouses: whole(f.spouses), children: whole(f.children), otherDependants: whole(f.other) } : {}),
    ...(f.employeeContribution.trim() ? { employeeContribution: Number(f.employeeContribution) } : {}),
    ...(f.employerContribution.trim() ? { employerContribution: Number(f.employerContribution) } : {}),
  }
}
