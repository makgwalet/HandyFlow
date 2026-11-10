// Pure form logic for receiving, returns, inspection and lots. The server checks everything again; these catch the common slips before a round trip.

export interface Tracking { lotTracked: boolean; expiryTracked: boolean; minShelfLifeDays?: number | null }

export interface ReceiptForm {
  qtyGood: string; qtyDamaged: string; qtyHeld: string; locationId: string; reason: string
  lotNumber: string; expiryDate: string; manufacturedDate: string
}
export const emptyReceipt: ReceiptForm = { qtyGood: "", qtyDamaged: "", qtyHeld: "", locationId: "", reason: "", lotNumber: "", expiryDate: "", manufacturedDate: "" }

/** Blank is 0; a decimal comma is accepted; anything else that is not a number is null. */
export function parseQty(s: string): number | null {
  const t = s.trim().replace(",", ".")
  if (t === "") return 0
  if (!/^-?\d*\.?\d+$/.test(t)) return null
  return Number(t)
}

function quantities(parts: string[]): { nums: number[] } | { error: string } {
  const nums = parts.map(parseQty)
  if (nums.some(n => n == null)) return { error: "Enter quantities as numbers." }
  if ((nums as number[]).some(n => n < 0)) return { error: "Quantities cannot be negative." }
  if ((nums as number[]).reduce((a, b) => a + b, 0) === 0) return { error: "Enter a quantity to receive." }
  return { nums: nums as number[] }
}

function lotProblem(f: { lotNumber: string; expiryDate?: string; manufacturedDate?: string }, t: Tracking): string | null {
  if (t.lotTracked && !f.lotNumber.trim()) return "This item is lot tracked: enter the lot number."
  if (t.expiryTracked && !f.expiryDate) return "This item expires: enter the expiry date."
  if (f.expiryDate && f.manufacturedDate && f.expiryDate < f.manufacturedDate) return "The expiry date is before the manufactured date."
  return null
}

export function validateReceipt(f: ReceiptForm, t: Tracking): string | null {
  const q = quantities([f.qtyGood, f.qtyDamaged, f.qtyHeld])
  if ("error" in q) return q.error
  if (q.nums[1] + q.nums[2] > 0 && f.reason.trim().length < 5) return "Say what is wrong with the damaged or held stock (at least 5 characters)."
  if (!f.locationId) return "Choose where the stock is put away."
  return lotProblem(f, t)
}

export function buildReceiptPayload(f: ReceiptForm, t: Tracking) {
  return {
    qtyGood: parseQty(f.qtyGood) ?? 0, qtyDamaged: parseQty(f.qtyDamaged) ?? 0, qtyHeld: parseQty(f.qtyHeld) ?? 0,
    locationId: f.locationId, reason: f.reason.trim() || null,
    lotNumber: t.lotTracked ? f.lotNumber.trim() : null,
    expiryDate: t.expiryTracked ? f.expiryDate || null : null,
    manufacturedDate: t.lotTracked ? f.manufacturedDate || null : null,
  }
}

/** A heads-up (not an error) when the expiry date leaves less shelf life than the item's minimum. */
export function shelfLifeWarning(expiry: string, today: string, minDays: number | null | undefined): string | null {
  if (!expiry) return null
  const days = Math.round((Date.parse(expiry + "T00:00:00Z") - Date.parse(today + "T00:00:00Z")) / 86_400_000)
  if (days < 0) return "This lot has already expired."
  if (minDays != null && minDays > 0 && days < minDays) return `Only ${days} days of shelf life; the minimum is ${minDays}. It cannot be allocated to orders.`
  return null
}

export interface ReturnReceiveForm { qtyRestock: string; qtyHeld: string; qtyDamaged: string; locationId: string; reason: string; lotNumber: string }
export const emptyReturnReceive: ReturnReceiveForm = { qtyRestock: "", qtyHeld: "", qtyDamaged: "", locationId: "", reason: "", lotNumber: "" }

export function validateReturnReceive(f: ReturnReceiveForm, t: Tracking): string | null {
  const q = quantities([f.qtyRestock, f.qtyHeld, f.qtyDamaged])
  if ("error" in q) return q.error
  if (q.nums[1] + q.nums[2] > 0 && f.reason.trim().length < 5) return "Say what is wrong with the held or damaged goods (at least 5 characters)."
  if (!f.locationId) return "Choose where the goods are put away."
  if (t.lotTracked && !f.lotNumber.trim()) return "This item is lot tracked: enter the lot it came from."
  return null
}

export function buildReturnReceivePayload(f: ReturnReceiveForm, t: Tracking) {
  return {
    qtyRestock: parseQty(f.qtyRestock) ?? 0, qtyHeld: parseQty(f.qtyHeld) ?? 0, qtyDamaged: parseQty(f.qtyDamaged) ?? 0,
    locationId: f.locationId, reason: f.reason.trim() || null, lotNumber: t.lotTracked ? f.lotNumber.trim() : null,
  }
}

export interface ReturnLineForm { itemId: string; qty: string }

export function validateNewReturn(reason: string, lines: ReturnLineForm[]): string | null {
  if (reason.trim().length < 3) return "Say why the goods are coming back."
  const filled = lines.filter(l => l.itemId || l.qty.trim())
  if (filled.length === 0) return "Add at least one line."
  const seen = new Set<string>()
  for (const l of filled) {
    const n = parseQty(l.qty)
    if (!l.itemId) return "Choose an item on every line."
    if (n == null || n <= 0) return "Each line needs a quantity above zero."
    if (seen.has(l.itemId)) return "An item can appear once per return."
    seen.add(l.itemId)
  }
  return null
}

export function validateInspection(qty: string, reason: string, pending: number): string | null {
  const n = parseQty(qty)
  if (n == null || n <= 0) return "Enter the quantity inspected."
  if (n > pending) return `Only ${pending} is still waiting for inspection.`
  if (reason.trim().length < 5) return "Give a reason (at least 5 characters)."
  return null
}

export function validateClose(needsSignOff: boolean, note: string): string | null {
  if (needsSignOff && note.trim().length < 5) return "This has discrepancies. Add a note explaining them (at least 5 characters)."
  return null
}

export function validateReason(reason: string, what: string): string | null {
  return reason.trim().length < 5 ? `Give a reason for ${what} (at least 5 characters).` : null
}

export function daysLabel(days: number): string {
  if (days < 0) return `expired ${-days} day${days === -1 ? "" : "s"} ago`
  if (days === 0) return "expires today"
  return `${days} day${days === 1 ? "" : "s"} left`
}

export type ExpiryStatus = "EXPIRED" | "EXPIRING" | "OK" | "NO_EXPIRY"
export const EXPIRY_STYLE: Record<string, { bg: string; fg: string; label: string }> = {
  EXPIRED: { bg: "var(--hf-danger-soft)", fg: "var(--hf-danger-text)", label: "Expired" },
  EXPIRING: { bg: "var(--hf-warning-soft-strong)", fg: "var(--hf-warning-text-deep)", label: "Expiring" },
  OK: { bg: "var(--hf-success-soft)", fg: "var(--hf-success-text-strong)", label: "OK" },
  NO_EXPIRY: { bg: "var(--hf-surface-muted)", fg: "var(--hf-text-muted)", label: "No expiry" },
}

export interface TrackingForm { lotTracked: boolean; expiryTracked: boolean; minShelfLifeDays: string }

/** Expiry tracking needs lot tracking; the minimum shelf life only means something for an expiring item. */
export function validateTracking(f: TrackingForm): string | null {
  if (f.expiryTracked && !f.lotTracked) return "An item that expires must also be lot tracked."
  if (f.minShelfLifeDays.trim() !== "") {
    const n = Number(f.minShelfLifeDays)
    if (!Number.isInteger(n) || n < 0) return "Minimum shelf life is a whole number of days."
    if (!f.expiryTracked && n > 0) return "Minimum shelf life only applies to an item that expires."
  }
  return null
}

export function buildTrackingPayload(f: TrackingForm) {
  return { lotTracked: f.lotTracked, expiryTracked: f.expiryTracked, minShelfLifeDays: f.minShelfLifeDays.trim() === "" ? null : Number(f.minShelfLifeDays) }
}

export interface TaskRowLite { qty: number; qtyDone?: number }
export function remaining(t: TaskRowLite): number { return Math.max(0, t.qty - (t.qtyDone ?? 0)) }
