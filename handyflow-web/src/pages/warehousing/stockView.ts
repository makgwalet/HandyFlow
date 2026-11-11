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
