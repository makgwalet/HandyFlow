// src/pages/hr/eti.logic.ts
//
// The employment tax incentive on screen. The server works it out; these are the words and the checks for the two things a person enters.
export interface EmployeeEti { employeeId: string; priorMonths: number; excludedReason: string | null; identityProblem?: string | null }
export type EtiForm = { priorMonths: string; excludedReason: string }

export const MAX_ETI_MONTHS = 24
export const MAX_ETI_REASON = 200

export const etiForm = (e: EmployeeEti | undefined): EtiForm => ({ priorMonths: String(e?.priorMonths ?? 0), excludedReason: e?.excludedReason ?? "" })

export function etiProblem(f: EtiForm): string | null {
  const n = Number(f.priorMonths)
  if (f.priorMonths.trim() === "" || !Number.isInteger(n) || n < 0 || n > MAX_ETI_MONTHS) return `Months already used must be a whole number from 0 to ${MAX_ETI_MONTHS}.`
  if (f.excludedReason.trim().length > MAX_ETI_REASON) return `Keep the reason under ${MAX_ETI_REASON} characters.`
  return null
}

export const etiBody = (f: EtiForm) => ({ priorMonths: Number(f.priorMonths), excludedReason: f.excludedReason.trim() || null })

/** The settings body: the incentive switch alone, with the approval choice the server needs. */
export const etiSettingsBody = (allowSelfApproval: boolean, enabled: boolean) => ({ allowSelfApproval, etiEnabled: enabled })

const rand = (n: number) => `R${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/** The lines under the PAYE figure of an EMP201: what the incentive took off, and what is carried forward. */
export function emp201EtiNotes(e: { totalEti?: number | null; etiCarried?: number | null }): string[] {
  const out: string[] = []
  if (Number(e.totalEti ?? 0) > 0) out.push(`less ETI ${rand(Number(e.totalEti))}`)
  if (Number(e.etiCarried ?? 0) > 0) out.push(`ETI ${rand(Number(e.etiCarried))} carried forward`)
  return out
}

/** On a payslip row for payroll staff. */
export const payslipEtiNote = (p: { etiAmount?: number | string | null; etiMonth?: number | null }): string | null =>
  Number(p.etiAmount ?? 0) > 0 ? `ETI ${rand(Number(p.etiAmount))} claimed (month ${p.etiMonth ?? "?"} of 24)` : null

/** One line on whether the person's papers qualify for the incentive, from what the server found. Empty until it has answered. */
export const identityLine = (e: EmployeeEti | undefined): string =>
  !e || e.identityProblem === undefined ? "" : e.identityProblem ? `Papers on record: ${e.identityProblem}.` : "Papers on record: they qualify (a valid South African ID, asylum seeker permit or refugee ID)."
