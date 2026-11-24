// What the assessments tab asks of a person judging an obligation. The server enforces the same rules.

export const MIN_NOTE = 15

export const CONTROLS: [string, string][] = [["UNKNOWN", "Not judged"], ["YES", "In place"], ["PARTIAL", "Partly in place"], ["NO", "Not in place"]]
export const WORKFLOWS: [string, string][] = [["NONE", "Nothing under way"], ["REMEDIATION", "Being fixed"], ["AUTHORITY", "Waiting on an authority"]]

const STATUS_LABELS: Record<string, string> = {
  NOT_APPLICABLE: "Does not apply", NOT_ASSESSED: "Not assessed", COMPLIANT: "Compliant", PARTIALLY_COMPLIANT: "Partly compliant",
  NON_COMPLIANT: "Not compliant", EVIDENCE_MISSING: "Evidence missing", EXPIRED: "Evidence expired", UNDER_REVIEW: "Evidence under review",
  REMEDIATION_IN_PROGRESS: "Being fixed", AWAITING_AUTHORITY: "Waiting on an authority",
}
export const statusLabel = (s: string) => STATUS_LABELS[s] ?? s
export const controlLabel = (c: string) => CONTROLS.find(x => x[0] === c)?.[1] ?? c

/** Good, middling, bad or neutral, so the colour never has to carry the meaning alone. */
export function statusTone(s: string): "good" | "warn" | "bad" | "neutral" {
  if (s === "COMPLIANT") return "good"
  if (s === "PARTIALLY_COMPLIANT" || s === "UNDER_REVIEW" || s === "REMEDIATION_IN_PROGRESS" || s === "AWAITING_AUTHORITY") return "warn"
  if (s === "NON_COMPLIANT" || s === "EVIDENCE_MISSING" || s === "EXPIRED") return "bad"
  return "neutral"
}

export type JudgementDraft = { control: string; workflow: string; note: string }

/** Why the judgement cannot be saved yet, or null. */
export function judgementProblem(d: JudgementDraft): string | null {
  const note = d.note.trim()
  if ((d.control === "NO" || d.control === "PARTIAL") && note.length < MIN_NOTE) return `Say what is missing, in at least ${MIN_NOTE} characters`
  if (d.workflow !== "NONE" && note.length < MIN_NOTE) return `Say what is being done, in at least ${MIN_NOTE} characters`
  return null
}

export type WeightsDraft = { low: number; medium: number; high: number; critical: number }

export function weightsProblem(w: WeightsDraft): string | null {
  const all = [w.low, w.medium, w.high, w.critical]
  if (all.some(n => !Number.isInteger(n) || n < 1 || n > 100)) return "Each weight must be a whole number from 1 to 100"
  if (!(w.low <= w.medium && w.medium <= w.high && w.high <= w.critical)) return "A riskier obligation cannot weigh less than a safer one"
  return null
}

/** How to show a score. No percentage means nothing could be scored, which is not the same as 0%. */
export function scoreText(percent: number | null, scored: number, unscored: number, provisional: boolean): string {
  const base = percent === null ? "Nothing scored yet" : `${percent.toFixed(1)}%`
  const parts = [base, `${scored} scored`]
  if (unscored > 0) parts.push(`${unscored} not assessed`)
  if (provisional) parts.push("provisional")
  return parts.join(" · ")
}

export type Change = { code: string; from: string | null; to: string | null; reason: string | null }

export function changeText(c: Change): string {
  const from = c.from ? statusLabel(c.from) : "new"
  const to = c.to ? statusLabel(c.to) : "no longer assigned"
  return `${c.code}: ${from} → ${to}${c.reason ? ` (${c.reason})` : ""}`
}
