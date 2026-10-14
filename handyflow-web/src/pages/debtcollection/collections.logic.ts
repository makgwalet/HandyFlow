// Debt Collection front end: choosing a customer and invoices instead of typing IDs, the case queue, and the dashboard figures.

export interface Customer { id: string; name: string; email: string | null; phone: string | null }
export interface InvoiceRow { id: string; invoiceNumber: string; status: string; customerId: string | null; total: number; amountPaid: number | null; creditedAmount: number | null; balanceDue: number | null; dueDate: string | null; walkinClientName: string | null; walkinClientEmail: string | null; walkinClientPhone: string | null }
export interface OutstandingInvoice { id: string; invoiceNumber: string; dueDate: string | null; total: number; amountPaid: number; outstanding: number }
export interface Collector { userId: string; userName: string; active: boolean; openCases: number | null; utilisationPercent: number | null; awayUntil: string | null }
export interface CaseRow { id: string; caseNumber: string; debtorName: string; status: string; totalOutstanding: number; assignedToUserId: string | null; assignedToUserName: string | null; nextActionDate: string | null; openedDate: string }
export interface AgingBucket { label: string; amount: number; invoiceCount: number; caseCount: number; share: number }
export interface Aging { asOf: string; totalOutstanding: number; buckets: AgingBucket[] }
export interface PeriodStats { from: string; to: string; casesOpened: number; casesClosed: number; casesClosedSuccessfully: number; successRate: number | null }
export interface Summary { asOf: string; totalOutstanding: number; activeCases: number; casesByStatus: Record<string, number>; current: PeriodStats; previous: PeriodStats; openWorkItems: number; overdueWorkItems: number; writtenOffToDate: number }
export interface Recovery { netRecovered: number; feesCharged: number }
export interface TrendPoint { date: string; outstanding: number; activeCases: number; recoveredNet: number }
export interface TopDebtor { caseId: string; caseNumber: string; debtorName: string; status: string; outstanding: number; maxDaysOverdue: number; assignedToUserName: string | null; nextActionDate: string | null }

export const money = (n: number | null | undefined) => (n == null ? "—" : `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`)
export const moneyShort = (n: number) => (Math.abs(n) >= 1e6 ? `R ${(n / 1e6).toFixed(1)}m` : Math.abs(n) >= 1e3 ? `R ${(n / 1e3).toFixed(0)}k` : `R ${n.toFixed(0)}`)
export const rowsOf = <T,>(r: any): T[] => { const p = r?.data?.data ?? r?.data; return Array.isArray(p) ? p : (p?.content ?? []) }
export const objOf = <T,>(r: any): T => (r?.data?.data ?? r?.data) as T
export const errorOf = (e: any, fallback = "Something went wrong. Please try again.") => e?.response?.data?.message ?? fallback

export const TERMINAL = new Set(["SETTLED", "WRITTEN_OFF", "CLOSED"])

// ── choosing customer and invoices ──────────────────────────────────────
export const MIN_SEARCH = 2
export const searchReady = (q: string) => q.trim().length >= MIN_SEARCH

const UNPAID = new Set(["ISSUED", "PARTIALLY_PAID", "OVERDUE"])
/** What a customer still owes on an invoice (credit notes count as settled, as the server does). */
export const owing = (i: Pick<InvoiceRow, "total" | "amountPaid" | "creditedAmount" | "balanceDue">) =>
  Math.max(0, i.balanceDue ?? Math.round((i.total - (i.amountPaid ?? 0) - (i.creditedAmount ?? 0)) * 100) / 100)

/** Unpaid invoices matching the words typed (number, customer name), oldest due date first. */
export function unpaidInvoices(list: InvoiceRow[], q: string, customerNames: Record<string, string> = {}): InvoiceRow[] {
  const needle = q.trim().toLowerCase()
  return list.filter(i => UNPAID.has(i.status) && owing(i) > 0)
    .filter(i => !needle || `${i.invoiceNumber} ${i.walkinClientName ?? ""} ${customerNames[i.customerId ?? ""] ?? ""}`.toLowerCase().includes(needle))
    .sort((a, b) => (a.dueDate ?? "9999").localeCompare(b.dueDate ?? "9999"))
}

export const pickedTotal = (list: InvoiceRow[], picked: Set<string>) => Math.round(list.filter(i => picked.has(i.id)).reduce((t, i) => t + owing(i), 0) * 100) / 100

/** Debtor details to pre-fill from what we already know: the chosen customer, else the walk-in details on the first picked invoice. */
export function debtorFrom(customer: Customer | null, picked: InvoiceRow[]) {
  const w = picked.find(i => i.walkinClientName)
  return { name: customer?.name ?? w?.walkinClientName ?? "", email: customer?.email ?? w?.walkinClientEmail ?? "", phone: customer?.phone ?? w?.walkinClientPhone ?? "" }
}

export type NewCaseMode = "customer" | "invoices"
/** Null when the case can be opened; otherwise what is missing. */
export function newCaseProblem(mode: NewCaseMode, customer: Customer | null, outstanding: OutstandingInvoice[], pickedInvoices: number, debtorName: string): string | null {
  if (mode === "customer") {
    if (!customer) return "Choose the customer."
    if (outstanding.length === 0) return "This customer has no outstanding invoices."
    return null
  }
  if (pickedInvoices === 0) return "Tick at least one unpaid invoice."
  if (!debtorName.trim()) return "Enter the debtor's name."
  return null
}

export const collectorLabel = (c: Collector) => `${c.userName}${c.openCases != null ? ` · ${c.openCases} open` : ""}${c.awayUntil ? " · away" : ""}`

// ── the queue ───────────────────────────────────────────────────────────
export type Queue = "mine" | "all" | "due" | "disputed"
export const QUEUES: { id: Queue; label: string }[] = [{ id: "mine", label: "My cases" }, { id: "all", label: "All cases" }, { id: "due", label: "Follow-up due" }, { id: "disputed", label: "Disputed" }]

const isOpen = (c: CaseRow) => !TERMINAL.has(c.status)
export function inQueue(c: CaseRow, q: Queue, myId: string | null, today: string): boolean {
  switch (q) {
    case "mine": return isOpen(c) && !!myId && c.assignedToUserId === myId
    case "due": return isOpen(c) && !!c.nextActionDate && c.nextActionDate <= today
    case "disputed": return c.status === "DISPUTED"
    default: return true
  }
}
export function queueCounts(cases: CaseRow[], myId: string | null, today: string): Record<Queue, number> {
  return { mine: cases.filter(c => inQueue(c, "mine", myId, today)).length, all: cases.length, due: cases.filter(c => inQueue(c, "due", myId, today)).length, disputed: cases.filter(c => inQueue(c, "disputed", myId, today)).length }
}
export function searchCases(cases: CaseRow[], q: string): CaseRow[] {
  const n = q.trim().toLowerCase()
  return n ? cases.filter(c => `${c.caseNumber} ${c.debtorName} ${c.assignedToUserName ?? ""}`.toLowerCase().includes(n)) : cases
}

// ── dashboard ───────────────────────────────────────────────────────────
/** "+12% vs last period", or null when there is nothing to compare with. */
export function changeText(current: number | null | undefined, previous: number | null | undefined): { text: string; up: boolean } | null {
  if (current == null || previous == null || previous === 0) return null
  const pct = Math.round(((current - previous) / Math.abs(previous)) * 100)
  return { text: `${pct > 0 ? "+" : ""}${pct}% vs last period`, up: pct >= 0 }
}

export const AGING_COLOURS = ["#22c55e", "#84cc16", "#f59e0b", "#f97316", "#ef4444", "#b91c1c"]

/** Arc lengths for a donut drawn with stroke-dasharray, on a circle of the given radius. Zero total gives no arcs. */
export function donutArcs(buckets: AgingBucket[], radius: number): { label: string; dash: number; gap: number; offset: number; colour: string; share: number }[] {
  const total = buckets.reduce((t, b) => t + Math.max(0, b.amount), 0)
  if (total <= 0) return []
  const circ = 2 * Math.PI * radius
  let used = 0
  return buckets.map((b, i) => {
    const len = (Math.max(0, b.amount) / total) * circ
    const arc = { label: b.label, dash: len, gap: circ - len, offset: -used, colour: AGING_COLOURS[i % AGING_COLOURS.length], share: Math.round((Math.max(0, b.amount) / total) * 100) }
    used += len
    return arc
  })
}

/** Points for an SVG polyline, scaled into width x height. Several series share one vertical scale. */
export function linePoints(series: number[][], width: number, height: number): string[] {
  const n = Math.max(0, ...series.map(s => s.length))
  const max = Math.max(1, ...series.flat())
  return series.map(s => s.map((v, i) => `${n <= 1 ? 0 : Math.round((i / (n - 1)) * width * 10) / 10},${Math.round((height - (v / max) * height) * 10) / 10}`).join(" "))
}

export const dayText = (iso: string) => new Date(iso + "T00:00:00").toLocaleDateString("en-ZA", { day: "numeric", month: "short" })
export const overdueText = (days: number) => (days <= 0 ? "Not due" : `${days} ${days === 1 ? "day" : "days"} overdue`)

export type Period = "month" | "30" | "year"
export const PERIODS: { id: Period; label: string }[] = [{ id: "month", label: "This month" }, { id: "30", label: "Last 30 days" }, { id: "year", label: "This year" }]
export function periodRange(p: Period, today: Date): [string, string] {
  const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
  if (p === "month") return [iso(new Date(today.getFullYear(), today.getMonth(), 1)), iso(today)]
  if (p === "year") return [iso(new Date(today.getFullYear(), 0, 1)), iso(today)]
  const from = new Date(today); from.setDate(from.getDate() - 29)
  return [iso(from), iso(today)]
}
