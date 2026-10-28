// How report statuses are worded and shown on the Reports page. No server calls.

import type { Tone } from "./auditUi.logic"

export const REPORT_STATUS: Record<string, { label: string; tone: Tone }> = {
  DRAFT: { label: "Draft", tone: "muted" },
  FACTUAL_REVIEW: { label: "In factual-accuracy review", tone: "warning" },
  READY: { label: "Ready to issue", tone: "info" },
  ISSUED: { label: "Issued", tone: "success" },
  SUPERSEDED: { label: "Replaced by a newer version", tone: "muted" },
}
export const statusText = (s: string) => REPORT_STATUS[s]?.label ?? s
export const statusTone = (s: string): Tone => REPORT_STATUS[s]?.tone ?? "muted"

/** The path a report follows, in order. A replaced report is outside it. */
export const REPORT_STEPS = [
  { status: "DRAFT", label: "Draft" }, { status: "FACTUAL_REVIEW", label: "Factual review" },
  { status: "READY", label: "Ready" }, { status: "ISSUED", label: "Issued" },
] as const

export type StepState = "done" | "current" | "upcoming"
export function stepStates(status: string): StepState[] {
  const at = REPORT_STEPS.findIndex(s => s.status === status)
  if (at < 0) return REPORT_STEPS.map(() => "upcoming")
  return REPORT_STEPS.map((_, i) => (i < at ? "done" : i === at ? "current" : "upcoming"))
}

export function reportCounts(list: { status: string; openItems: number }[]) {
  return {
    total: list.length,
    inReview: list.filter(r => r.status === "FACTUAL_REVIEW").length,
    ready: list.filter(r => r.status === "READY").length,
    issued: list.filter(r => r.status === "ISSUED").length,
    openComments: list.reduce((n, r) => n + (r.status === "SUPERSEDED" ? 0 : r.openItems), 0),
  }
}
