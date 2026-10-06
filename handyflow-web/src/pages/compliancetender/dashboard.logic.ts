// src/pages/compliancetender/dashboard.logic.ts
//
// Pure helpers for the Compliance & Tender dashboard: what to count, what is urgent, and how to say it. No React, no network.
import { daysUntil } from "./package.logic"

export interface DashRegistration { id: string; authority: string; registrationType: string; status: string; expiryDate: string | null; expiringSoon: boolean }
export interface DashDeadline { id: string; deadlineType: string; description: string | null; dueDate: string; status: string; dueSoon: boolean }
export interface DashTender { id: string; tenderNumber: string; name: string; tenderAuthority: string | null; closingDate: string | null; estimatedValue: number | null; status: string }

export const CLOSED_STATUSES = ["AWARDED", "UNSUCCESSFUL", "WITHDRAWN"]
/** Tenders still ours to work on, i.e. not yet sent in. */
export const PREPARING_STATUSES = ["DRAFT", "IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT"]

/** The pipeline, left to right. */
export const STAGES: { key: string; label: string; statuses: string[] }[] = [
  { key: "prepare", label: "Preparing", statuses: ["DRAFT", "IN_PREPARATION"] },
  { key: "review", label: "Review & ready", statuses: ["INTERNAL_REVIEW", "READY_TO_SUBMIT"] },
  { key: "submitted", label: "Submitted", statuses: ["SUBMITTED", "CLARIFICATION"] },
  { key: "evaluation", label: "Evaluation", statuses: ["SHORTLISTED", "NEGOTIATION"] },
  { key: "awarded", label: "Awarded", statuses: ["AWARDED"] },
]

export function isActive(t: DashTender): boolean { return !CLOSED_STATUSES.includes(t.status) }

export function pipeline(tenders: DashTender[]): { key: string; label: string; count: number; value: number }[] {
  return STAGES.map(s => {
    const inStage = tenders.filter(t => s.statuses.includes(t.status))
    return { key: s.key, label: s.label, count: inStage.length, value: inStage.reduce((a, t) => a + (t.estimatedValue ?? 0), 0) }
  })
}

export function pipelineValue(tenders: DashTender[]): number {
  return tenders.filter(isActive).reduce((a, t) => a + (t.estimatedValue ?? 0), 0)
}

/** The open tender still being prepared that closes soonest and has not closed yet. */
export function nextClosing(tenders: DashTender[], today: Date): DashTender | null {
  let best: DashTender | null = null
  let bestDays = Infinity
  for (const t of tenders) {
    if (!PREPARING_STATUSES.includes(t.status)) continue
    const n = daysUntil(t.closingDate, today)
    if (n === null || n < 0) continue
    if (n < bestDays) { best = t; bestDays = n }
  }
  return best
}

export interface RegistrationHealth { valid: number; expiringSoon: number; expired: number; other: number; total: number }

export function registrationHealth(regs: DashRegistration[]): RegistrationHealth {
  let valid = 0, expiringSoon = 0, expired = 0, other = 0
  for (const r of regs) {
    if (r.status === "EXPIRED" || r.status === "LAPSED") expired++
    else if (r.status === "ACTIVE" && r.expiringSoon) expiringSoon++
    else if (r.status === "ACTIVE") valid++
    else other++
  }
  return { valid, expiringSoon, expired, other, total: regs.length }
}

export type UrgentKind = "Registration" | "Deadline" | "Tender"
export interface UrgentItem { key: string; kind: UrgentKind; title: string; detail: string; days: number; when: string; tone: "bad" | "warn"; tab: string; tenderId?: string }

export function whenText(days: number): string {
  if (days < -1) return `Overdue by ${-days} days`
  if (days === -1) return "Overdue by 1 day"
  if (days === 0) return "Due today"
  if (days === 1) return "Due tomorrow"
  return `In ${days} days`
}

const humanise = (s: string) => s.replace(/_/g, " ").toLowerCase().replace(/^\w/, c => c.toUpperCase())

/** Everything that needs a person soon, most urgent first. Tenders and deadlines within `horizon` days, registrations that expired or are about to. */
export function urgentItems(regs: DashRegistration[], deadlines: DashDeadline[], tenders: DashTender[], today: Date, horizon = 14): UrgentItem[] {
  const out: UrgentItem[] = []
  for (const r of regs) {
    const n = daysUntil(r.expiryDate, today)
    const expired = r.status === "EXPIRED" || r.status === "LAPSED" || (n !== null && n < 0 && r.status === "ACTIVE")
    if (!expired && !(r.status === "ACTIVE" && r.expiringSoon)) continue
    const days = n ?? -1
    out.push({ key: `r-${r.id}`, kind: "Registration", title: `${r.authority} ${humanise(r.registrationType)}`, detail: expired ? "Registration expired" : "Expiring soon",
      days, when: expired ? (n !== null && n < 0 ? `Expired ${-n} days ago` : "Expired") : whenText(days), tone: expired ? "bad" : "warn", tab: "registrations" })
  }
  for (const d of deadlines) {
    const n = daysUntil(d.dueDate, today)
    if (n === null || n > horizon || d.status === "DONE" || d.status === "COMPLETED") continue
    out.push({ key: `d-${d.id}`, kind: "Deadline", title: d.description || humanise(d.deadlineType), detail: humanise(d.deadlineType), days: n, when: whenText(n), tone: n <= 3 ? "bad" : "warn", tab: "deadlines" })
  }
  for (const t of tenders) {
    if (!PREPARING_STATUSES.includes(t.status)) continue
    const n = daysUntil(t.closingDate, today)
    if (n === null || n > horizon) continue
    out.push({ key: `t-${t.id}`, kind: "Tender", title: t.name, detail: `${t.tenderNumber} · ${humanise(t.status)}`, days: n, when: n < 0 ? `Closed ${-n} days ago` : n === 0 ? "Closes today" : n === 1 ? "Closes tomorrow" : `Closes in ${n} days`,
      tone: n <= 3 ? "bad" : "warn", tab: "tenders", tenderId: t.id })
  }
  return out.sort((a, b) => a.days - b.days)
}

export function fmtZarShort(v: number): string {
  if (v >= 1_000_000) return `R ${(v / 1_000_000).toLocaleString("en-ZA", { maximumFractionDigits: 1 })}m`
  if (v >= 1_000) return `R ${Math.round(v / 1_000).toLocaleString("en-ZA")}k`
  return `R ${Math.round(v).toLocaleString("en-ZA")}`
}
