// Pure helpers behind the portal dashboard: status groups for the donut, the activity list, the next invoice due, and the request filters.
import type { FmPortalInvoice, FmPortalQuote, FmPortalWo } from "../../api/fmPortal.api"

export interface Slice { key: "open" | "progress" | "completed" | "cancelled"; label: string; count: number; colour: string }
const GROUPS: { key: Slice["key"]; label: string; colour: string; statuses: string[] }[] = [
  { key: "open", label: "Open", colour: "var(--hf-info)", statuses: ["REQUESTED", "OPEN", "ASSIGNED"] },
  { key: "progress", label: "In progress", colour: "var(--hf-warning)", statuses: ["IN_PROGRESS", "ON_HOLD", "PENDING_VERIFICATION"] },
  { key: "completed", label: "Completed", colour: "var(--hf-success-solid-strong)", statuses: ["COMPLETED"] },
  { key: "cancelled", label: "Cancelled", colour: "var(--hf-text-faint)", statuses: ["CANCELLED", "REJECTED"] },
]
export const REQUEST_STATUS_FILTERS = GROUPS.map(g => ({ key: g.key, label: g.label }))

/** The server's per-status counts, folded into four groups a person can read at a glance. */
export function statusSlices(byStatus: Record<string, number> | null | undefined): { slices: Slice[]; total: number } {
  const slices = GROUPS.map(g => ({ key: g.key, label: g.label, colour: g.colour, count: g.statuses.reduce((n, s) => n + (byStatus?.[s] ?? 0), 0) }))
  return { slices, total: slices.reduce((n, s) => n + s.count, 0) }
}

/** Arc lengths for an SVG donut: each slice's dash and where it starts, around a circle of the given radius. */
export function donutArcs(slices: Slice[], radius: number): { key: string; colour: string; dash: number; gap: number; offset: number }[] {
  const total = slices.reduce((n, s) => n + s.count, 0)
  if (total === 0) return []
  const circ = 2 * Math.PI * radius
  let used = 0
  return slices.filter(s => s.count > 0).map(s => {
    const dash = (s.count / total) * circ
    const arc = { key: s.key, colour: s.colour, dash, gap: circ - dash, offset: -used }
    used += dash
    return arc
  })
}

export const groupOfStatus = (status: string): Slice["key"] | null => GROUPS.find(g => g.statuses.includes(status))?.key ?? null

export interface RequestFilter { search: string; siteId: string; group: string }
export function filterRequests(rows: FmPortalWo[], f: RequestFilter): FmPortalWo[] {
  const q = f.search.trim().toLowerCase()
  return rows.filter(w =>
    (!f.siteId || w.siteId === f.siteId) &&
    (!f.group || groupOfStatus(w.status) === f.group) &&
    (!q || `${w.workOrderNumber} ${w.description}`.toLowerCase().includes(q)))
}

export interface Activity { key: string; when: string; title: string; note: string; kind: "request" | "quote" | "invoice"; to: "requests" | "quotes" | "invoices" }
/** What happened lately, newest first, from the requests, quotes and invoices already loaded. */
export function activityFeed(orders: FmPortalWo[], quotes: FmPortalQuote[], invoices: FmPortalInvoice[], limit = 6): Activity[] {
  const out: Activity[] = []
  for (const w of orders) {
    const done = w.status === "COMPLETED" && w.completedAt
    out.push({ key: "w" + w.id, when: (done ? w.completedAt : w.createdAt) as string, kind: "request", to: "requests",
      title: `${w.workOrderNumber} ${done ? "completed" : "reported"}`, note: w.description })
  }
  for (const q of quotes) {
    const when = q.decidedAt ?? q.sentAt
    if (when) out.push({ key: "q" + q.id, when, kind: "quote", to: "quotes", title: `${q.quoteNumber} ${q.decidedAt ? "answered" : "sent to you"}`, note: q.title })
  }
  for (const i of invoices) if (i.issueDate) out.push({ key: "i" + i.id, when: i.issueDate, kind: "invoice", to: "invoices", title: `${i.invoiceNumber} issued`, note: `Due ${i.dueDate ?? "—"}` })
  return out.sort((a, b) => b.when.localeCompare(a.when)).slice(0, limit)
}

const UNPAYABLE = ["DRAFT", "CANCELLED", "CREDITED", "PAID"]
export const owingInvoices = (invoices: FmPortalInvoice[]) =>
  invoices.filter(i => i.balance > 0 && !UNPAYABLE.includes(i.status)).sort((a, b) => (a.dueDate ?? "9999").localeCompare(b.dueDate ?? "9999"))
/** The unpaid invoice that falls due first, if any. */
export const nextInvoiceDue = (invoices: FmPortalInvoice[]): FmPortalInvoice | null => owingInvoices(invoices)[0] ?? null
