// Pure helpers for the Stock section: words for movement types and stock statuses, and the query string for the lists.

export const MOVEMENT_TYPES: { value: string; label: string }[] = [
  { value: "RECEIPT", label: "Receipt" }, { value: "PUTAWAY", label: "Put-away" }, { value: "PICK", label: "Pick" }, { value: "SHIP", label: "Shipped" },
  { value: "TRANSFER", label: "Transfer" }, { value: "RETURN", label: "Return" }, { value: "ADJUSTMENT", label: "Adjustment" },
  { value: "COUNT_VARIANCE", label: "Count variance" }, { value: "DAMAGE", label: "Damaged" }, { value: "LOSS", label: "Loss" }, { value: "FOUND", label: "Found" },
  { value: "QUARANTINE", label: "Quarantine" }, { value: "RELEASE", label: "Released" },
]

export const STOCK_STATUSES: { value: string; label: string }[] = [
  { value: "AVAILABLE", label: "Available" }, { value: "QUARANTINE", label: "Quarantine" }, { value: "DAMAGED", label: "Damaged" }, { value: "BLOCKED", label: "Blocked" },
]

const label = (list: { value: string; label: string }[], v: string | null | undefined) => (v ? list.find(x => x.value === v)?.label ?? v : "")
export const movementText = (type: string | null | undefined) => label(MOVEMENT_TYPES, type)
export const statusText = (status: string | null | undefined) => label(STOCK_STATUSES, status)

/** A signed quantity: +5 for stock in, −3 for stock out (a real minus sign), 0 as it is. */
export function signedQty(n: number | null | undefined): string {
  if (n == null) return "–"
  const abs = Math.abs(n).toLocaleString("en-ZA", { maximumFractionDigits: 3 })
  return n > 0 ? `+${abs}` : n < 0 ? `−${abs}` : abs
}

export interface PageInfo { total: number; page: number; size: number }

/** "1–50 of 312", "0 results" when empty. */
export function rangeText(p: PageInfo): string {
  if (p.total <= 0) return "0 results"
  const from = p.page * p.size + 1
  const to = Math.min(p.total, (p.page + 1) * p.size)
  return `${from.toLocaleString("en-ZA")}–${to.toLocaleString("en-ZA")} of ${p.total.toLocaleString("en-ZA")}`
}

export const hasNext = (p: PageInfo) => (p.page + 1) * p.size < p.total

/** Only the filters that are set, so an empty filter is never sent. */
export function listParams(f: Record<string, string | number | boolean | null | undefined>): Record<string, string | number | boolean> {
  const out: Record<string, string | number | boolean> = {}
  for (const [k, v] of Object.entries(f)) {
    if (v === null || v === undefined || v === "" || v === false) continue
    out[k] = typeof v === "string" ? v.trim() : v
    if (out[k] === "") delete out[k]
  }
  return out
}

// ── Stock alerts ─────────────────────────────────────────────────────────

export const ALERT_KINDS: { value: string; label: string; tone: "bad" | "warn" | "info" }[] = [
  { value: "OUT_OF_STOCK", label: "Out of stock", tone: "bad" },
  { value: "BELOW_MIN", label: "Below minimum", tone: "warn" },
  { value: "ABOVE_MAX", label: "Above maximum", tone: "info" },
]

export const alertKindText = (k: string | null | undefined) => ALERT_KINDS.find(x => x.value === k)?.label ?? (k ?? "")
export const alertTone = (k: string | null | undefined) => ALERT_KINDS.find(x => x.value === k)?.tone ?? "info"

export interface AlertRow {
  id: string; warehouseId: string; warehouseCode: string; clientName: string; itemId: string; sku: string; description: string | null
  kind: string; status: string; physical: number | null; unavailable: number | null; reserved: number | null; available: number
  incoming: number | null; minQty: number | null; minSource: string | null; maxQty: number | null; maxSource: string | null
  recommendedOrder: number | null; acknowledgedAt: string | null; resolution: string | null; resolutionNote: string | null
}

const n = (v: number) => v.toLocaleString("en-ZA", { maximumFractionDigits: 3 })

/** The line that keeps the kinds of stock apart: 100 on the floor is not 100 to sell. */
export function stockBreakdown(a: Pick<AlertRow, "physical" | "available" | "reserved" | "unavailable" | "incoming">): string {
  const parts = [`${n(a.available)} available`]
  if (a.physical != null) parts.push(`${n(a.physical)} physical`)
  if (a.reserved) parts.push(`${n(a.reserved)} reserved`)
  if (a.unavailable) parts.push(`${n(a.unavailable)} quarantined, damaged or blocked`)
  if (a.incoming) parts.push(`${n(a.incoming)} incoming`)
  return parts.join(" · ")
}

/** "min 100 · max 500", with a note where a level comes from the item rather than the warehouse. */
export function levelsText(a: Pick<AlertRow, "minQty" | "minSource" | "maxQty" | "maxSource">): string {
  const part = (label: string, v: number | null, src: string | null) => (v == null ? null : `${label} ${n(v)}${src === "ITEM" ? " (item default)" : ""}`)
  return [part("min", a.minQty, a.minSource), part("max", a.maxQty, a.maxSource)].filter(Boolean).join(" · ")
}

/** What to do about the alert: an order for a shortage, nothing but a look for overstock. */
export function recommendationText(a: Pick<AlertRow, "kind" | "recommendedOrder" | "incoming">): string | null {
  if (a.kind === "ABOVE_MAX") return "Consider moving or returning the excess."
  if (a.recommendedOrder == null) return null
  if (a.recommendedOrder === 0) return "Incoming stock already covers this."
  return `Reorder ${n(a.recommendedOrder)}.`
}

/** Null when the levels can be saved, else what is wrong. Mirrors the server. */
export function thresholdProblem(min: string, max: string): string | null {
  const lo = min.trim() === "" ? null : Number(min), hi = max.trim() === "" ? null : Number(max)
  if (lo === null && hi === null) return "Enter a minimum, a maximum, or both."
  if ((lo !== null && !Number.isFinite(lo)) || (hi !== null && !Number.isFinite(hi))) return "Levels must be numbers."
  if (lo !== null && lo < 0) return "The minimum cannot be negative."
  if (hi !== null && hi <= 0) return "The maximum must be above zero."
  if (lo !== null && hi !== null && hi <= lo) return "The maximum must be more than the minimum."
  return null
}
