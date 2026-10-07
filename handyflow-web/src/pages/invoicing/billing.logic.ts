// src/pages/invoicing/billing.logic.ts
//
// Pure rules for the billing screens (quotes, invoices, recurring schedules, retainers, credit notes): the shapes the
// server returns, status wording and tones, headline figures, filtering, searching, sorting, paging and the small
// calculations behind the forms. No React, no network, so every rule can be tested on its own.
import type { ChipTone } from "../../components/ui/Chip"

// ── Shapes ────────────────────────────────────────────────────────────────────
export type QuoteStatus = "DRAFT" | "SENT" | "ACCEPTED" | "REJECTED" | "EXPIRED" | "INVOICED"
export type RecurringStatus = "ACTIVE" | "PAUSED" | "CANCELLED" | "COMPLETED"
export type InvoiceType = "STANDARD" | "RECURRING_INSTANCE" | "RETAINER"

export interface LineItem { id?: string; description: string; unit?: string; quantity: number; unitPrice: number; vatRate?: number; lineTotal: number }
export interface Quote {
  id: string; quoteNumber: string; title: string; status: QuoteStatus; total: number; subtotal?: number; vatTotal?: number
  expiresAt: string | null; createdAt: string; sentAt?: string | null; acceptedAt?: string | null; customerId: string | null
  walkinClientName?: string | null; walkinClientEmail?: string | null; walkinClientPhone?: string | null; firstViewedAt?: string | null; lastViewedAt?: string | null; viewCount?: number
  notes?: string | null; lineItems?: LineItem[]
}
export interface Invoice {
  id: string; invoiceNumber: string; customerId: string | null; status: string; quoteId?: string | null; title?: string | null
  issuedAt: string | null; dueDate: string | null; subtotal: number; vatTotal: number; total: number; amountPaid: number
  lineItems: LineItem[]; createdAt: string; invoiceType: InvoiceType; recurringScheduleId: string | null
  committedHours: number | null; ratePerHour: number | null; hoursConsumed: number | null; creditAmount?: number | null
  walkinClientName: string | null; walkinClientEmail?: string | null; walkinClientPhone?: string | null
}
export interface CreditNote {
  id: string; creditNoteNumber: string; invoiceId: string; invoiceNumber: string; reason: string; description: string | null
  subtotal: number; vatTotal: number; total: number; currency: string; issuedAt: string; createdAt: string
}
export interface RecurringSchedule {
  id: string; title: string; status: RecurringStatus; frequency: string; customIntervalDays: number | null
  nextRunAt: string; lastRunAt: string | null; total: number; subtotal: number; vatTotal: number
  customerId: string | null; lineItems: LineItem[]; walkinClientName: string | null; createdAt: string
  variableHours: boolean; ratePerHour: number | null; minimumHoursPerCycle: number | null; hoursVatRate: number | null
  contractStartDate: string | null; contractEndDate: string | null; contractedTotalHours: number | null
  totalHoursBilled: number; remainingCycles: number
}

// ── Wording and tones ─────────────────────────────────────────────────────────
export const QUOTE_LABEL: Record<string, string> = { DRAFT: "Draft", SENT: "Sent", ACCEPTED: "Accepted", REJECTED: "Rejected", EXPIRED: "Expired", INVOICED: "Invoiced" }
export const QUOTE_TONE: Record<string, ChipTone> = { DRAFT: "neutral", SENT: "info", ACCEPTED: "ok", REJECTED: "bad", EXPIRED: "warn", INVOICED: "accent" }
export const INVOICE_LABEL: Record<string, string> = { DRAFT: "Draft", ISSUED: "Issued", PARTIALLY_PAID: "Part paid", PAID: "Paid", OVERDUE: "Overdue", CANCELLED: "Cancelled" }
export const INVOICE_TONE: Record<string, ChipTone> = { DRAFT: "neutral", ISSUED: "info", PARTIALLY_PAID: "warn", PAID: "ok", OVERDUE: "bad", CANCELLED: "neutral" }
export const RECURRING_LABEL: Record<string, string> = { ACTIVE: "Active", PAUSED: "Paused", CANCELLED: "Cancelled", COMPLETED: "Completed" }
export const RECURRING_TONE: Record<string, ChipTone> = { ACTIVE: "ok", PAUSED: "warn", CANCELLED: "bad", COMPLETED: "accent" }
export const TYPE_LABEL: Record<string, string> = { STANDARD: "Standard", RECURRING_INSTANCE: "Recurring", RETAINER: "Retainer" }
export const FREQ_LABEL: Record<string, string> = { DAILY: "Daily", WEEKLY: "Weekly", MONTHLY: "Monthly", CUSTOM: "Custom" }
export const OPEN_INVOICE_STATUSES = ["ISSUED", "PARTIALLY_PAID", "OVERDUE"]

export const label = (map: Record<string, string>, key: string) => map[key] ?? key.charAt(0) + key.slice(1).toLowerCase().replace(/_/g, " ")

export const fmtR = (n: number | null | undefined) =>
  n == null || Number.isNaN(Number(n)) ? "—" : `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
/** A rand figure without cents, for tiles. */
export const fmtRShort = (n: number) => `R ${Math.round(n).toLocaleString("en-ZA")}`
export const fmtDate = (d: string | null | undefined) =>
  d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
export const num = (n: number | null | undefined) => Number(n ?? 0)

export function frequencyLabel(s: Pick<RecurringSchedule, "frequency" | "customIntervalDays">): string {
  if (s.frequency === "CUSTOM" && s.customIntervalDays) return `Every ${s.customIntervalDays} days`
  return label(FREQ_LABEL, s.frequency)
}

// ── Dates ─────────────────────────────────────────────────────────────────────
const DAY = 86_400_000
const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
/** Whole days from `now` to the date (negative when it has passed). Dates are compared as calendar days. */
export function daysUntil(date: string | null | undefined, now: Date = new Date()): number | null {
  if (!date) return null
  const d = new Date(date)
  if (Number.isNaN(d.getTime())) return null
  return Math.round((startOfDay(d) - startOfDay(now)) / DAY)
}

/** "Due in 5 days", "Due today", "3 days overdue". Paid, cancelled and draft invoices have nothing to say. */
export function dueLabel(inv: Pick<Invoice, "status" | "dueDate">, now: Date = new Date()): string {
  if (!OPEN_INVOICE_STATUSES.includes(inv.status)) return ""
  const d = daysUntil(inv.dueDate, now)
  if (d == null) return ""
  if (d === 0) return "Due today"
  if (d > 0) return `Due in ${d} day${d === 1 ? "" : "s"}`
  return `${-d} day${d === -1 ? "" : "s"} overdue`
}

/** A sent quote is "expiring" in the last week before it lapses; one that has lapsed reads as expired. */
export function quoteExpiryLabel(q: Pick<Quote, "status" | "expiresAt">, now: Date = new Date()): string {
  if (q.status !== "SENT") return ""
  const d = daysUntil(q.expiresAt, now)
  if (d == null) return ""
  if (d < 0) return `Lapsed ${-d} day${d === -1 ? "" : "s"} ago`
  if (d === 0) return "Expires today"
  if (d <= 7) return `Expires in ${d} day${d === 1 ? "" : "s"}`
  return ""
}

// ── Money ─────────────────────────────────────────────────────────────────────
export const balanceOf = (inv: Pick<Invoice, "total" | "amountPaid">) => Math.max(0, Math.round((num(inv.total) - num(inv.amountPaid)) * 100) / 100)

/** The amount a payment form may take: more than zero and not more than what is still owing. */
export function paymentError(amount: string, inv: Pick<Invoice, "total" | "amountPaid">): string | null {
  const a = Number(amount)
  if (!amount.trim() || !Number.isFinite(a) || a <= 0) return "Enter the amount received."
  if (a > balanceOf(inv) + 0.005) return `That is more than the ${fmtR(balanceOf(inv))} still owing.`
  return null
}

export function creditNoteTotals(amount: string, vatRate: string): { subtotal: number; vat: number; total: number } {
  const sub = Math.max(0, Number(amount) || 0)
  const rate = Math.max(0, Number(vatRate) || 0)
  const vat = Math.round(sub * rate) / 100
  return { subtotal: sub, vat, total: Math.round((sub + vat) * 100) / 100 }
}
export function creditNoteError(reason: string, amount: string, vatRate: string): string | null {
  if (!reason.trim()) return "Give a reason for the credit note."
  if (!(Number(amount) > 0)) return "Enter the amount to credit, excluding VAT."
  const r = Number(vatRate)
  if (vatRate.trim() !== "" && (!Number.isFinite(r) || r < 0 || r > 100)) return "VAT rate must be between 0 and 100."
  return null
}

// ── Retainers ─────────────────────────────────────────────────────────────────
export interface RetainerUse { committed: number; consumed: number; remaining: number; percent: number; overage: boolean; overBy: number; level: "ok" | "low" | "over" }
/** Hours used against the commitment. `low` is 80% and above, `over` is past the commitment. */
export function retainerUse(inv: Pick<Invoice, "committedHours" | "hoursConsumed">): RetainerUse | null {
  if (inv.committedHours == null) return null
  const committed = num(inv.committedHours), consumed = num(inv.hoursConsumed)
  const percent = committed > 0 ? Math.min(100, (consumed / committed) * 100) : 0
  const overage = consumed > committed
  return {
    committed, consumed, remaining: Math.max(0, Math.round((committed - consumed) * 100) / 100), percent,
    overage, overBy: overage ? Math.round((consumed - committed) * 100) / 100 : 0,
    level: overage ? "over" : percent >= 80 ? "low" : "ok",
  }
}
export const retainerBarColour = (level: RetainerUse["level"]) =>
  level === "over" ? "var(--hf-danger)" : level === "low" ? "var(--hf-warning)" : "var(--hf-accent)"

export const hoursError = (hours: string): string | null => (Number(hours) > 0 ? null : "Enter the hours worked, more than zero.")
/** Warns before a logged amount pushes the retainer past its commitment. */
export const wouldExceed = (hours: string, inv: Pick<Invoice, "committedHours" | "hoursConsumed">) =>
  Number(hours) > 0 && inv.committedHours != null && num(inv.hoursConsumed) + Number(hours) > num(inv.committedHours)

// ── Recurring ─────────────────────────────────────────────────────────────────
/** Monthly equivalent of a schedule's invoice, so weekly, daily and custom schedules count towards recurring revenue too. */
export function monthlyValue(s: Pick<RecurringSchedule, "frequency" | "customIntervalDays" | "total">): number {
  const t = num(s.total)
  switch (s.frequency) {
    case "MONTHLY": return t
    case "WEEKLY": return (t * 52) / 12
    case "DAILY": return (t * 365) / 12
    case "CUSTOM": return s.customIntervalDays && s.customIntervalDays > 0 ? (t * 365) / s.customIntervalDays / 12 : 0
    default: return 0
  }
}
/** "Next run" wording: today, tomorrow, in n days, or overdue when the date has passed without a run. */
export function nextRunLabel(s: Pick<RecurringSchedule, "status" | "nextRunAt">, now: Date = new Date()): string {
  if (s.status !== "ACTIVE") return s.status === "PAUSED" ? "Paused" : ""
  const d = daysUntil(s.nextRunAt, now)
  if (d == null) return ""
  if (d < 0) return "Run overdue"
  if (d === 0) return "Runs today"
  if (d === 1) return "Runs tomorrow"
  return `Runs in ${d} days`
}
/** What a variable-hours cycle will actually be billed for: the minimum applies when fewer hours were worked. */
export function billableHours(actual: string, minimum: number | null): number {
  const a = Math.max(0, Number(actual) || 0)
  return Math.max(a, num(minimum))
}
export function cycleHoursError(actual: string, period: string): string | null {
  if (actual.trim() === "" || !(Number(actual) >= 0) || !Number.isFinite(Number(actual))) return "Enter the hours worked this cycle."
  if (!period.trim()) return "Give the period, for example June 2026."
  return null
}

// ── Headline figures ──────────────────────────────────────────────────────────
export function quoteStats(quotes: Quote[]) {
  const by = (s: QuoteStatus) => quotes.filter(q => q.status === s)
  const open = quotes.filter(q => q.status === "DRAFT" || q.status === "SENT")
  const decided = by("ACCEPTED").length + by("REJECTED").length + by("INVOICED").length
  const won = by("ACCEPTED").length + by("INVOICED").length
  return {
    total: quotes.length, draft: by("DRAFT").length, sent: by("SENT").length, accepted: by("ACCEPTED").length,
    openValue: open.reduce((s, q) => s + num(q.total), 0),
    awaitingInvoice: by("ACCEPTED").length, awaitingInvoiceValue: by("ACCEPTED").reduce((s, q) => s + num(q.total), 0),
    /** Share of decided quotes that were accepted; null until something has been decided. */
    winRate: decided === 0 ? null : Math.round((won / decided) * 100),
    unopened: by("SENT").filter(q => !q.firstViewedAt).length,
  }
}
export function invoiceStats(invoices: Invoice[]) {
  const live = invoices.filter(i => i.status !== "CANCELLED" && i.status !== "DRAFT")
  const open = invoices.filter(i => OPEN_INVOICE_STATUSES.includes(i.status))
  const overdue = invoices.filter(i => i.status === "OVERDUE")
  return {
    total: invoices.length, drafts: invoices.filter(i => i.status === "DRAFT").length,
    outstanding: open.reduce((s, i) => s + balanceOf(i), 0), outstandingCount: open.length,
    overdueValue: overdue.reduce((s, i) => s + balanceOf(i), 0), overdueCount: overdue.length,
    collected: live.reduce((s, i) => s + num(i.amountPaid), 0),
    billed: live.reduce((s, i) => s + num(i.total), 0),
    paidCount: invoices.filter(i => i.status === "PAID").length,
  }
}
export function recurringStats(schedules: RecurringSchedule[]) {
  const active = schedules.filter(s => s.status === "ACTIVE")
  return {
    total: schedules.length, active: active.length, paused: schedules.filter(s => s.status === "PAUSED").length,
    mrr: active.reduce((s, x) => s + monthlyValue(x), 0),
    variable: active.filter(s => s.variableHours).length,
    dueThisWeek: active.filter(s => { const d = daysUntil(s.nextRunAt); return d != null && d <= 7 }).length,
  }
}
export function retainerStats(invoices: Invoice[]) {
  const r = invoices.filter(i => i.invoiceType === "RETAINER" && i.status !== "CANCELLED")
  const uses = r.map(retainerUse).filter((u): u is RetainerUse => u !== null)
  return {
    count: r.length, committed: uses.reduce((s, u) => s + u.committed, 0), consumed: uses.reduce((s, u) => s + u.consumed, 0),
    low: uses.filter(u => u.level === "low").length, over: uses.filter(u => u.level === "over").length,
    value: r.reduce((s, i) => s + num(i.total), 0),
  }
}

// ── Filtering, searching, sorting, paging ─────────────────────────────────────
export const norm = (s: unknown) => String(s ?? "").trim().toLowerCase()
export const matches = (q: string, ...fields: unknown[]) => { const n = norm(q); return n === "" || fields.some(f => norm(f).includes(n)) }

export type SortDir = "asc" | "desc"
export function sortBy<T>(rows: T[], key: (r: T) => string | number | null | undefined, dir: SortDir): T[] {
  const m = dir === "asc" ? 1 : -1
  return [...rows].sort((a, b) => {
    const x = key(a), y = key(b)
    if (x == null && y == null) return 0
    if (x == null) return 1            // empty values always sink, whichever way it is sorted
    if (y == null) return -1
    return (typeof x === "number" && typeof y === "number" ? x - y : String(x).localeCompare(String(y), "en", { numeric: true })) * m
  })
}

export const PAGE_SIZE = 20
export function paginate<T>(rows: T[], page: number, size: number = PAGE_SIZE): { rows: T[]; page: number; pages: number; from: number; to: number; total: number } {
  const pages = Math.max(1, Math.ceil(rows.length / size))
  const p = Math.min(Math.max(0, page), pages - 1)
  const start = p * size
  return { rows: rows.slice(start, start + size), page: p, pages, from: rows.length === 0 ? 0 : start + 1, to: Math.min(rows.length, start + size), total: rows.length }
}

/** Counts per key, for the numbers on the filter pills. */
export function countBy<T>(rows: T[], key: (r: T) => string): Record<string, number> {
  const out: Record<string, number> = {}
  for (const r of rows) out[key(r)] = (out[key(r)] ?? 0) + 1
  return out
}

/** The customer's name, or the walk-in name, or a placeholder; never a raw id. */
export function partyName(customerId: string | null, walkin: string | null | undefined, names: Record<string, string>): string {
  if (!customerId) return walkin ? `${walkin} (walk-in)` : "Walk-in client"
  return names[customerId] ?? "Customer"
}

/** The server pages its lists; the screens load the newest `limit` and say so when there are more. */
export const LIST_LIMIT = 200
export const isTruncated = (totalElements: number | undefined, loaded: number) => totalElements != null && totalElements > loaded
