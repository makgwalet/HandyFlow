// D1: tolerances by site and product, the repeated-variance rule, and the log of automatic acceptances. Every number is the company's own.
export type ToleranceOverride = { scope: "DEPOT" | "PRODUCT"; key: string; label: string; amount: number }
export type RepeatPolicy = { repeatCount: number | null; repeatDays: number | null }
export type ShiftControls = { overrides: ToleranceOverride[]; policy: RepeatPolicy }
export type AcceptLogRow = { id: string; shiftId: string; shiftNumber: string; attendantName: string; varianceAmount: number | null; toleranceApplied: number | null; actor: string; acceptedAt: string }

export const NO_CONTROLS: ShiftControls = { overrides: [], policy: { repeatCount: null, repeatDays: null } }

/** Other tests answer every URL with generic data; accept only a real answer. */
export function asControls(raw: any): ShiftControls {
  if (!raw || !Array.isArray(raw.overrides) || !raw.policy) return NO_CONTROLS
  return { overrides: raw.overrides.filter((o: any) => o && typeof o.key === "string" && typeof o.amount === "number"), policy: { repeatCount: raw.policy.repeatCount ?? null, repeatDays: raw.policy.repeatDays ?? null } }
}

export function onlyAcceptLog(raw: unknown): AcceptLogRow[] {
  if (!Array.isArray(raw)) return []
  return raw.filter((r: any) => r && typeof r.shiftNumber === "string" && typeof r.attendantName === "string") as AcceptLogRow[]
}

export function amountProblem(v: string): string | null {
  const n = Number(v.replace(/\s/g, "").replace(",", "."))
  if (v.trim() === "" || !Number.isFinite(n) || n < 0) return "Enter an amount of 0 or more."
  return null
}

export function repeatProblem(count: string, days: string): string | null {
  const c = Number(count), d = Number(days)
  if (!Number.isInteger(c) || c < 2) return "Repeated means at least 2 shifts."
  if (!Number.isInteger(d) || d < 1 || d > 365) return "Choose between 1 and 365 days."
  return null
}

export function overrideText(o: ToleranceOverride): string {
  return o.scope === "DEPOT" ? `${o.label}: cash variance tolerance R ${o.amount.toFixed(2)}` : `${o.label}: gauge difference up to ${o.amount.toLocaleString("en-ZA")} L`
}

export function repeatSummary(p: RepeatPolicy): string {
  return p.repeatCount == null ? "Off: repeated variances are not flagged" : `Flagged when one attendant closes ${p.repeatCount} shifts outside tolerance within ${p.repeatDays} days`
}

export function acceptLogLine(r: AcceptLogRow): string {
  const v = r.varianceAmount == null ? "no variance recorded" : `variance R ${r.varianceAmount.toFixed(2)}`
  const t = r.toleranceApplied == null ? "" : ` against a tolerance of R ${r.toleranceApplied.toFixed(2)}`
  return `${r.shiftNumber} · ${r.attendantName} · ${v}${t} · accepted by the system ${(r.acceptedAt ?? "").slice(0, 16).replace("T", " ")}`
}
