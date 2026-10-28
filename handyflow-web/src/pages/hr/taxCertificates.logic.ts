// Pure helpers for the tax certificate screen. The server builds, checks and issues; this only decides what to offer
// and how to word it. Tax years are named by the year they end in, as SARS names them (2027 = 1 March 2026 to 28 February 2027).

export type Period = "INTERIM" | "ANNUAL"

export interface TaxRights {
  /** See the reconciliation, the certificates and the spreadsheet. */
  view: boolean
  /** Issue, cancel and release certificates. */
  issue: boolean
  /** Change the employer's tax numbers. */
  configure: boolean
}

export function taxRights(permissions: string[], today = new Date()): TaxRights {
  const has = (...p: string[]) => p.some(x => permissions.includes(x))
  // PAYROLL_READ and PAYROLL_RUN still work for viewing until the end of 2026, as they do for the rest of payroll.
  const legacy = today <= new Date(2026, 11, 31)
  return {
    view: has("PAYROLL_STATUTORY_EXPORT") || (legacy && has("PAYROLL_READ", "PAYROLL_RUN")),
    issue: has("PAYROLL_CERTIFICATE_ISSUE"),
    configure: has("PAYROLL_CONFIGURATION_ADMIN"),
  }
}

/** The tax year a date falls in: March to December belong to the next calendar year's name. */
export function taxYearOf(d: Date): number {
  return d.getMonth() >= 2 ? d.getFullYear() + 1 : d.getFullYear()
}

/**
 * Which reconciliation is most likely wanted. From September to February the interim one (March to August) of the current
 * tax year; from March to August the annual one of the year that just ended.
 */
export function defaultSelection(today: Date): { taxYear: number; period: Period } {
  const m = today.getMonth() // 0 = January
  if (m >= 2 && m <= 7) return { taxYear: taxYearOf(today) - 1, period: "ANNUAL" }
  return { taxYear: taxYearOf(today), period: "INTERIM" }
}

export function periodLabel(taxYear: number, period: Period): string {
  return period === "INTERIM"
    ? `Interim ${taxYear}: 1 March ${taxYear - 1} to 31 August ${taxYear - 1}`
    : `Annual ${taxYear}: 1 March ${taxYear - 1} to the end of February ${taxYear}`
}

export const rand = (n: number | string | null | undefined) =>
  n == null ? "—" : `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

export interface Problem { severity: string; subject: string; message: string }

export function countBySeverity(problems: Problem[]): { errors: number; warnings: number } {
  return {
    errors: problems.filter(p => p.severity === "ERROR").length,
    warnings: problems.filter(p => p.severity === "WARNING").length,
  }
}

export const SEVERITY_STYLE: Record<string, { color: string; bg: string; label: string }> = {
  ERROR: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", label: "Must fix" },
  WARNING: { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", label: "Check" },
  INFO: { color: "var(--hf-info-text)", bg: "var(--hf-info-soft)", label: "Note" },
}

export interface DraftState { state: string }

/** Some certificates were issued before with other figures, so replacing them needs a reason. */
export function needsReason(certs: DraftState[]): boolean {
  return certs.some(c => c.state === "CHANGED")
}

export function issueSummary(certs: DraftState[]): string {
  const n = (s: string) => certs.filter(c => c.state === s).length
  const parts: string[] = []
  if (n("NEW")) parts.push(`${n("NEW")} new`)
  if (n("CHANGED")) parts.push(`${n("CHANGED")} to replace`)
  if (n("UNCHANGED")) parts.push(`${n("UNCHANGED")} already issued`)
  return parts.length ? parts.join(", ") : "nothing to issue"
}

export function reasonProblem(reason: string, what: string): string | null {
  return reason.trim() ? null : `Give a reason for ${what}.`
}

export function settingsProblem(payeRef: string): string | null {
  const v = payeRef.replace(/\s/g, "")
  if (v && !/^\d{10}$/.test(v)) return "The PAYE reference number is 10 digits."
  return null
}

/** The SIC7 industry code is five characters, for example 41000. Blank is allowed until the e@syFile file is wanted. */
export function sic7Problem(code: string): string | null {
  const v = code.replace(/\s/g, "")
  if (v && !/^[0-9A-Za-z]{5}$/.test(v)) return "The SIC7 code is 5 characters, for example 41000."
  return null
}

export const FUND_LABEL: Record<string, string> = { PENSION: "Pension fund", PROVIDENT: "Provident fund", RETIREMENT_ANNUITY: "Retirement annuity" }

export const TYPE_LABEL: Record<string, string> = { IRP5: "IRP5", IT3A: "IT3(a)" }

/** Years offered in the selector: the current tax year and the five before it. */
export function yearOptions(today: Date): number[] {
  const now = taxYearOf(today)
  return Array.from({ length: 6 }, (_, i) => now - i)
}
