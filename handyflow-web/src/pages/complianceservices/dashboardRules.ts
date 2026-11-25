// What the practice dashboard shows and how it is worded. The server counts; this only turns the counts into tiles and bar sizes.
import { formatMoney } from "./pricingRules"
import type { QueueItem } from "./queueRules"

export type Kpis = {
  activeClients: number; overdueDeadlines: number; deadlinesNext14: number; documentsToReview: number; documentsExpiring: number; openCases: number
  regulatoryOpen: number; quotesOut: number; quotesOutValue: number; activeAgreements: number; billsToRaise: number; billsToRaiseValue: number
}
export type Stage = { status: string; count: number; value: number }
export type Week = { start: string; count: number }
export type Month = { month: string; amount: number; count: number }
export type Upcoming = { clientId: string; clientName: string; title: string; dueDate: string }
export type Summary = {
  generatedOn: string; kpis: Kpis; queue: { total: number; high: number; medium: number; low: number }; clientsByStatus: { status: string; count: number }[]
  quotePipeline: Stage[]; tenderPipeline: Stage[]; deadlineWeeks: Week[]; deadlinesOverdue: number; billingForecast: Month[]; billingBehind: number
  billingBehindCount: number; urgent: QueueItem[]; upcoming: Upcoming[]
}

export type Tile = { key: string; label: string; value: string; hint?: string; tone: "neutral" | "ok" | "warn" | "bad" | "info"; to: string }

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

/** The headline tiles, most pressing first. A tile is red or amber only when something is actually wrong. */
export function tiles(k: Kpis): Tile[] {
  return [
    { key: "overdue", label: "Overdue deadlines", value: String(k.overdueDeadlines), hint: `${k.deadlinesNext14} due in the next 14 days`, tone: k.overdueDeadlines > 0 ? "bad" : "ok", to: "queue" },
    { key: "cases", label: "Open cases", value: String(k.openCases), tone: k.openCases > 0 ? "warn" : "neutral", to: "queue" },
    { key: "documents", label: "Documents to deal with", value: String(k.documentsToReview + k.documentsExpiring), hint: `${k.documentsToReview} to review, ${k.documentsExpiring} expiring`, tone: k.documentsExpiring > 0 ? "warn" : "neutral", to: "queue" },
    { key: "regulatory", label: "Regulatory changes open", value: String(k.regulatoryOpen), tone: k.regulatoryOpen > 0 ? "warn" : "neutral", to: "regulatory" },
    { key: "quotes", label: "Quotes out", value: String(k.quotesOut), hint: k.quotesOut > 0 ? `${formatMoney(k.quotesOutValue)} waiting for an answer` : undefined, tone: "info", to: "quotes" },
    { key: "bills", label: "Bills to raise", value: String(k.billsToRaise), hint: k.billsToRaise > 0 ? formatMoney(k.billsToRaiseValue) : undefined, tone: k.billsToRaise > 0 ? "warn" : "neutral", to: "billing" },
    { key: "agreements", label: "Active agreements", value: String(k.activeAgreements), tone: "neutral", to: "agreements" },
    { key: "clients", label: "Active clients", value: String(k.activeClients), tone: "neutral", to: "clients" },
  ]
}

/** Bar lengths as a share of the longest, never below a sliver for a non-zero value so it stays visible. */
export function shares(values: number[]): number[] {
  const max = Math.max(0, ...values)
  if (max <= 0) return values.map(() => 0)
  return values.map(v => (v <= 0 ? 0 : Math.max(0.03, v / max)))
}

export const weekLabel = (start: string) => {
  const [, m, d] = start.split("-")
  return `${Number(d)} ${["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"][Number(m) - 1]}`
}
export const monthLabel = (month: string) => ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"][Number(month.split("-")[1]) - 1]

export const QUOTE_STAGE_LABELS: Record<string, string> = {
  DRAFT: "Draft", PENDING_APPROVAL: "Awaiting approval", APPROVED: "Approved", SENT: "Sent", EXPIRED: "Expired", ACCEPTED: "Accepted", DECLINED: "Declined", WITHDRAWN: "Withdrawn",
}
export const TENDER_STAGE_LABELS: Record<string, string> = {
  DRAFT: "Draft", IN_PREPARATION: "In preparation", INTERNAL_REVIEW: "Internal review", READY_TO_SUBMIT: "Ready to submit", SUBMITTED: "Submitted", CLARIFICATION: "Clarification",
  SHORTLISTED: "Shortlisted", NEGOTIATION: "Negotiation", AWARDED: "Awarded", UNSUCCESSFUL: "Unsuccessful", WITHDRAWN: "Withdrawn",
}
export const stageLabel = (labels: Record<string, string>, s: string) => labels[s] ?? s.toLowerCase().replace(/_/g, " ")

export const CLIENT_STATUS_LABELS: Record<string, string> = { ACTIVE: "Active", INACTIVE: "Inactive" }
export const clientStatusLabel = (s: string) => CLIENT_STATUS_LABELS[s] ?? s.toLowerCase()

export const weeksNote = (overdue: number, weeks: Week[]) => {
  const total = weeks.reduce((n, w) => n + w.count, 0)
  return `${plural(total, "deadline", "deadlines")} in the next 8 weeks${overdue > 0 ? `, and ${overdue} already overdue` : ""}`
}
export const billingNote = (behind: number, behindCount: number) =>
  behindCount > 0 ? `${plural(behindCount, "bill", "bills")} from earlier months (${formatMoney(behind)}) still waiting` : "Nothing waiting from earlier months"
