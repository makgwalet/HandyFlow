// src/pages/events/dashboardView.ts
//
// Wording and small calculations for the events dashboard, the per-event analytics and ticket management. The server computes the numbers (they are exact
// at any event size); these only turn them into text, tones and bar widths.

export interface AttentionItem { type: string; severity: string; message: string; eventId: string | null; eventNumber: string | null }
export interface UpcomingEvent {
  id: string; eventNumber: string; title: string; status: string; startDatetime: string; daysUntil: number; venueCapacity: number | null
  registered: number; fillPercent: number | null; budgetStatus: string | null
}
export interface Overview { statusCounts: Record<string, number>; upcoming: UpcomingEvent[]; attention: AttentionItem[] }

export const STATUS_ORDER = ["DRAFT", "PUBLISHED", "SOLD_OUT", "LIVE", "COMPLETED", "CANCELLED"]
export const statusLabel = (s: string) => ({ DRAFT: "Draft", PUBLISHED: "Open for registration", SOLD_OUT: "Sold out", LIVE: "Live", COMPLETED: "Completed", CANCELLED: "Cancelled" } as Record<string, string>)[s] ?? s

/** Warnings first (the server sends them that way, this keeps it so if it ever changes), then everything else in the order given. */
export function sortAttention(items: AttentionItem[]): AttentionItem[] {
  return [...items.keys()].sort((a, b) => (rank(items[a]) - rank(items[b])) || a - b).map(i => items[i])
}
const rank = (i: AttentionItem) => (i.severity === "WARNING" ? 0 : 1)

export const attentionTitle = (type: string) => ({
  DRAFT_STARTING_SOON: "Still a draft", NOT_LIVE_ON_TIME: "Should be live", BUDGET_NOT_APPROVED: "Budget not approved",
  LOW_REGISTRATION: "Low registration", REGISTRATION_CLOSING: "Registration closing", NEAR_CAPACITY: "Nearly full",
} as Record<string, string>)[type] ?? type

export function daysText(days: number): string {
  if (days === 0) return "Today"
  if (days === 1) return "Tomorrow"
  if (days === -1) return "Started yesterday"
  return days > 0 ? `In ${days} days` : `Started ${-days} days ago`
}

/** "120 of 200 (60%)", or just the count when the event has no limit. */
export function fillText(registered: number, capacity: number | null, percent: number | null): string {
  if (capacity == null) return `${registered} registered`
  return `${registered} of ${capacity}${percent == null ? "" : ` (${Math.round(Number(percent))}%)`}`
}

/** Width for a bar, kept within 0-100 and never NaN. */
export function barPercent(value: number, max: number): number {
  if (!max || max <= 0 || !Number.isFinite(value)) return 0
  return Math.max(0, Math.min(100, Math.round((value / max) * 100)))
}

export const budgetStatusText = (s: string | null) => (s == null ? "No budget" : ({ DRAFT: "Draft", SUBMITTED: "Awaiting approval", APPROVED: "Approved", REJECTED: "Rejected" } as Record<string, string>)[s] ?? s)

export interface TierRow { id: string; name: string; description?: string | null; price: number; quantity: number; quantitySold: number; quantityCheckedIn: number; available: number; saleStart: string | null; saleEnd: string | null; active: boolean }

/** What a ticket type is doing right now, from its own window and stock. */
export function tierState(t: Pick<TierRow, "active" | "available" | "saleStart" | "saleEnd">, now: Date = new Date()): { label: string; tone: "ok" | "warn" | "bad" | "muted" } {
  if (!t.active) return { label: "Off sale", tone: "muted" }
  if (t.available <= 0) return { label: "Sold out", tone: "warn" }
  if (t.saleStart && new Date(t.saleStart) > now) return { label: "Not on sale yet", tone: "muted" }
  if (t.saleEnd && new Date(t.saleEnd) < now) return { label: "Sale ended", tone: "muted" }
  return { label: "On sale", tone: "ok" }
}

export function tierProblem(f: { name: string; price: string; quantity: string; saleStart: string; saleEnd: string }): string | null {
  if (!f.name.trim()) return "Give the ticket type a name."
  if (f.price.trim() !== "" && !/^\d{1,8}(\.\d{1,2})?$/.test(f.price.trim())) return "Price must be an amount like 250 or 250.00 (0 for free)."
  if (!/^\d+$/.test(f.quantity.trim()) || Number(f.quantity) < 1) return "Quantity must be a whole number of at least 1."
  if (Number(f.quantity) > 1_000_000) return "Quantity can be at most 1 000 000."
  if (f.saleStart && f.saleEnd && f.saleEnd <= f.saleStart) return "The sale must end after it starts."
  return null
}

export function tierBody(f: { name: string; description: string; price: string; quantity: string; saleStart: string; saleEnd: string }) {
  const dt = (v: string) => (v ? (v.length === 16 ? `${v}:00` : v) : null)
  return { name: f.name.trim(), description: f.description.trim() || null, price: f.price.trim() === "" ? 0 : Number(f.price), quantity: Number(f.quantity), saleStart: dt(f.saleStart), saleEnd: dt(f.saleEnd) }
}

/** The start of a 15-minute arrival bucket as a local clock time, e.g. "08:15". */
export function bucketLabel(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? "" : d.toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", hour12: false })
}

/** Total of all tiers' unsold places, ignoring tiers that are off sale. */
export const placesLeft = (tiers: Pick<TierRow, "active" | "available">[]) => tiers.filter(t => t.active).reduce((a, t) => a + Math.max(0, t.available), 0)
