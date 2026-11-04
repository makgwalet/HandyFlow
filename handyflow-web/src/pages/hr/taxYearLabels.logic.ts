// Pure helpers for the tax-year label correction screen.
export interface LabelRow {
  id: string; payRunNumber: string; payDate: string | null; status: string; finalisedOrPaid: boolean
  storedLabel: string; proposedLabel: string; verdict: "CORRECT" | "WRONG_LABEL" | "NEEDS_REVIEW"; reason: string
  payslips: number; payslipsOnOtherYear: number
}
export interface LabelPreview { total: number; correct: number; wrongLabel: number; needsReview: number; rows: LabelRow[] }

/** Runs that can be corrected safely: the label is wrong and the pay date settles it. */
export const fixable = (rows: LabelRow[]) => rows.filter(r => r.verdict === "WRONG_LABEL")
export const needReview = (rows: LabelRow[]) => rows.filter(r => r.verdict === "NEEDS_REVIEW")

export function labelSummary(p: LabelPreview): string {
  if (p.wrongLabel === 0 && p.needsReview === 0) return "Every pay run's tax-year label agrees with its pay date."
  const parts: string[] = []
  if (p.wrongLabel) parts.push(`${p.wrongLabel} pay run${p.wrongLabel === 1 ? " has" : "s have"} the wrong label`)
  if (p.needsReview) parts.push(`${p.needsReview} need${p.needsReview === 1 ? "s" : ""} a person to decide`)
  return parts.join("; ") + "."
}

export function applyProblem(ids: string[], reason: string): string | null {
  if (ids.length === 0) return "Choose at least one pay run"
  if (reason.trim().length < 5) return "Say why, for the record"
  return null
}
export const applyBody = (ids: string[], reason: string) => ({ payRunIds: ids, reason: reason.trim() })
