// Stage 2 helpers: order statuses, validation that mirrors the server, credit wording and loading variance.
export interface DeliveryLine {
  id: string; status: string; litresOrdered: number; litresDelivered: number | null; scheduledAt: string
  loadedLitres: number | null; sealNumbers: string | null; receiptNumber: string | null
  invoiceState: string; invoiceNumber: string | null; orderId: string | null
}
export interface Order {
  id: string; orderNumber: string; accountId: string; customerId: string; customerName: string; siteId: string | null; siteName: string | null
  fuelType: string; litresOrdered: number; pricePerLitre: number; value: number; status: string
  requestedDate: string | null; quoteValidUntil: string | null; tankId: string | null; tankName: string | null
  litresDelivered: number; litresScheduled: number; litresRemaining: number
  creditOverrideReason: string | null; cancelReason: string | null; customerReference: string | null; notes: string | null
  deliveries: DeliveryLine[] | null
}
export interface CreditCheck {
  creditLimit: number; invoicesOutstanding: number; deliveredNotInvoiced: number; otherOpenOrders: number
  thisOrder: number; exposureAfter: number; withinLimit: boolean; onCreditHold: boolean
}
export interface QueueLine {
  deliveryId: string; receiptNumber: string | null; customerId: string | null; customerName: string | null; fuelType: string
  litresDelivered: number; pricePerLitre: number; totalAmount: number; deliveredAt: string; invoiceState: string; invoiceError: string | null
}

export const ORDER_STATUS: Record<string, string> = {
  QUOTE: "Quotation", CONFIRMED: "Confirmed", PART_DELIVERED: "Part delivered", DELIVERED: "Delivered", CANCELLED: "Cancelled",
}
export const statusLabel = (s: string) => ORDER_STATUS[s] ?? s

export const fmtL = (n: number | null | undefined) =>
  n == null || Number.isNaN(Number(n)) ? "–" : `${Number(n).toLocaleString("en-ZA", { maximumFractionDigits: 2 })} L`
export const fmtR = (n: number | null | undefined) =>
  n == null || Number.isNaN(Number(n)) ? "–" : `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

const num = (s: string) => (s.trim() === "" ? NaN : Number(s))

export function validateOrder(accountId: string, fuelType: string, litres: string, price: string, validUntil: string, today = new Date()): string | null {
  if (!accountId) return "Choose the customer account."
  if (!fuelType) return "Choose the fuel type."
  const l = num(litres); if (Number.isNaN(l) || l <= 0) return "Litres must be greater than zero."
  const p = num(price); if (Number.isNaN(p) || p < 0) return "Price per litre cannot be negative."
  if (validUntil && new Date(validUntil + "T23:59:59") < today) return "The quotation validity date is in the past."
  return null
}

export function validateConfirm(tankId: string, check: CreditCheck | undefined, canOverride: boolean, reason: string): string | null {
  if (!tankId) return "Choose the tank to supply this order from."
  if (check?.onCreditHold) return "This customer is on credit hold. Release the hold first."
  if (check && !check.withinLimit) {
    if (!canOverride) return "This order is over the customer's credit limit and you do not have permission to override it."
    if (reason.trim().length < 5) return "Give a reason of at least 5 characters to confirm an order over the credit limit."
  }
  return null
}

export function validateLoad(litres: string, remaining: number, scheduledAt: string): string | null {
  const l = num(litres)
  if (Number.isNaN(l) || l <= 0) return "Litres must be greater than zero."
  if (l > remaining) return `Only ${fmtL(remaining)} of this order is not yet delivered or scheduled.`
  if (!scheduledAt) return "Choose when the load is scheduled."
  return null
}

export function validateLoading(litres: string): string | null {
  const l = num(litres); return Number.isNaN(l) || l <= 0 ? "Loaded litres must be greater than zero." : null
}

export const TOLERANCE = 0.005 // 0.5%
/** Loaded vs delivered difference. Beyond tolerance it needs a person to look at it: it is never written off automatically. */
export function loadVariance(loaded: number | null, delivered: number | null) {
  if (loaded == null || delivered == null || loaded <= 0) return null
  const diff = delivered - loaded
  const pct = diff / loaded
  return { diff, pct, needsReview: Math.abs(pct) > TOLERANCE }
}

/** Customers with at least one delivery that can be invoiced together (one customer per invoice). */
export function groupByCustomer(lines: QueueLine[]): { customerId: string | null; name: string; lines: QueueLine[]; total: number }[] {
  const m = new Map<string, { customerId: string | null; name: string; lines: QueueLine[]; total: number }>()
  for (const l of lines) {
    const k = l.customerId ?? "none"
    const g = m.get(k) ?? { customerId: l.customerId, name: l.customerName ?? "No customer (walk-in)", lines: [], total: 0 }
    g.lines.push(l); g.total += Number(l.totalAmount ?? 0); m.set(k, g)
  }
  return [...m.values()]
}
