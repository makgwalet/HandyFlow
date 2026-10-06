// src/pages/compliancetender/tendersView.logic.ts
//
// Pure helpers for the tender list: headline numbers, search, sort, and the wording and colour of a closing-date countdown. No React, no network.
import { daysUntil } from "./package.logic"
import { PREPARING_STATUSES, CLOSED_STATUSES, fmtZarShort } from "./dashboard.logic"
import type { ChipTone } from "../../components/ui/Chip"

export interface ListTender {
  id: string; tenderNumber: string; name: string; tenderAuthority: string | null
  closingDate: string | null; estimatedValue: number | null; status: string
}

const AWAITING = ["SUBMITTED", "CLARIFICATION", "SHORTLISTED", "NEGOTIATION"]

export interface TenderKpis { active: number; closingSoon: number; pipelineValue: number; pipelineText: string; awaiting: number }

/** Active = anything not yet awarded, lost or withdrawn. Closing soon = still being prepared and closing within `horizon` days (or today). */
export function tenderKpis(tenders: ListTender[], today: Date, horizon = 14): TenderKpis {
  const active = tenders.filter(t => !CLOSED_STATUSES.includes(t.status))
  const closingSoon = active.filter(t => {
    if (!PREPARING_STATUSES.includes(t.status)) return false
    const n = daysUntil(t.closingDate, today)
    return n !== null && n >= 0 && n <= horizon
  }).length
  const value = active.reduce((a, t) => a + (t.estimatedValue ?? 0), 0)
  return { active: active.length, closingSoon, pipelineValue: value, pipelineText: fmtZarShort(value), awaiting: tenders.filter(t => AWAITING.includes(t.status)).length }
}

export type TenderSort = "closing" | "value" | "name"

/** Case-insensitive search over name, number and authority; then sort. Closing sort puts the soonest first, undated last. */
export function filterAndSort(tenders: ListTender[], search: string, sort: TenderSort): ListTender[] {
  const q = search.trim().toLowerCase()
  const hit = q ? tenders.filter(t => [t.name, t.tenderNumber, t.tenderAuthority ?? ""].some(s => s.toLowerCase().includes(q))) : tenders.slice()
  return hit.sort((a, b) => {
    if (sort === "value") return (b.estimatedValue ?? -1) - (a.estimatedValue ?? -1)
    if (sort === "name") return a.name.localeCompare(b.name)
    const x = a.closingDate ?? "9999-12-31", y = b.closingDate ?? "9999-12-31"
    return x < y ? -1 : x > y ? 1 : 0
  })
}

export interface Countdown { text: string; tone: ChipTone }

/** The chip beside a tender. Only tenders still being prepared are urgent; sent-in and finished tenders just show the date. */
export function countdown(t: Pick<ListTender, "closingDate" | "status">, today: Date): Countdown | null {
  const n = daysUntil(t.closingDate, today)
  if (n === null) return { text: "No closing date", tone: "neutral" }
  if (!PREPARING_STATUSES.includes(t.status)) return null
  if (n < 0) return { text: n === -1 ? "Closed yesterday" : `Closed ${-n} days ago`, tone: "bad" }
  if (n === 0) return { text: "Closes today", tone: "bad" }
  if (n === 1) return { text: "Closes tomorrow", tone: "bad" }
  return { text: `Closes in ${n} days`, tone: n <= 3 ? "bad" : n <= 14 ? "warn" : "ok" }
}

/** Left-edge colour of a card: red when closing is urgent, amber when near, otherwise the neutral border. */
export function edgeTone(c: Countdown | null): ChipTone { return c ? (c.tone === "ok" ? "neutral" : c.tone) : "neutral" }

export interface StepperStep { label: string; state: "done" | "current" | "todo" }

const STEPS: { label: string; statuses: string[] }[] = [
  { label: "Preparing", statuses: ["DRAFT", "IN_PREPARATION"] },
  { label: "Review", statuses: ["INTERNAL_REVIEW", "READY_TO_SUBMIT"] },
  { label: "Submitted", statuses: ["SUBMITTED", "CLARIFICATION"] },
  { label: "Evaluation", statuses: ["SHORTLISTED", "NEGOTIATION"] },
  { label: "Outcome", statuses: ["AWARDED", "UNSUCCESSFUL", "WITHDRAWN"] },
]

/** The progress track on the tender page. A withdrawn tender shows every step as not reached except "Outcome". */
export function stepper(status: string): StepperStep[] {
  const cur = STEPS.findIndex(s => s.statuses.includes(status))
  return STEPS.map((s, i) => ({ label: s.label, state: i < cur ? "done" : i === cur ? "current" : "todo" }))
}
