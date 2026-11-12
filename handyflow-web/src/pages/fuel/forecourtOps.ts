// Stage 5 helpers: forecourt shift maths and validation that mirror the server, so attendants get an instant message.
export interface Pump {
  id: string; name: string; tankId: string; tankName: string | null; fuelType: string | null; pricePerLitre: number
  lastMeter: number | null; active: boolean; onShift: boolean
  dispenserLabel?: string | null; nozzleNumber?: number | null
}
export interface ShiftReading {
  pumpId: string; pumpName: string; fuelType: string; openingMeter: number; openingGap: number; closingMeter: number | null
  testLitres: number; litresSold: number | null; pricePerLitre: number; salesValue: number | null
}
export interface Shift {
  id: string; shiftNumber: string; attendantName: string; status: "OPEN" | "CLOSED" | "REVERSED"; openedAt: string; closedAt: string | null
  cash: number | null; card: number | null; account: number | null; other: number | null; expected: number | null; litresSold: number | null
  variance: number | null; varianceReason: string | null; notes: string | null; reverseReason: string | null; readings: ShiftReading[]
  fleetCard?: number | null; adjustments?: number | null; tolerance?: number | null
  reviewStatus?: "NONE" | "PENDING_REVIEW" | "REVIEWED"; reviewNote?: string | null; reviewedAt?: string | null
}
export interface ShiftAdjustment {
  id: string; shiftId: string; kind: "REFUND" | "VOID" | "DISCOUNT"; amount: number; reason: string; status: "PENDING" | "APPROVED" | "REJECTED" | "WITHDRAWN"
  recordedBy: string | null; recordedAt: string; decidedBy?: string | null; decidedAt?: string | null; decisionNote?: string | null
}
export interface ForecourtSummary {
  days: number; shifts: number; litres: number; sales: number; variance: number; flaggedShifts: number; byFuel: { fuelType: string; litres: number; sales: number }[]
}

/** Same as the server: a shortfall or surplus above this needs a reason. */
export const VARIANCE_TOLERANCE = 5

export const fmtL = (n: number | null | undefined) => n == null || Number.isNaN(Number(n)) ? "–" : `${Number(n).toLocaleString("en-ZA", { maximumFractionDigits: 2 })} L`
export const fmtR = (n: number | null | undefined) => n == null || Number.isNaN(Number(n)) ? "–" : `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

const num = (s: string) => (s.trim() === "" ? NaN : Number(s))
const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100

export function validatePump(name: string, tankId: string, price: string): string | null {
  if (!name.trim()) return "Give the pump a name."
  if (!tankId) return "Choose the tank this pump draws from."
  const p = num(price); if (Number.isNaN(p) || p <= 0) return "Enter a pump price greater than zero."
  return null
}

/** D01: optional dispenser/nozzle grouping. Mirrors the server: a nozzle number (1 to 20) needs a dispenser. */
export function validateNozzle(label: string, number: string): string | null {
  if (number.trim() === "") return null
  const n = Number(number)
  if (!Number.isInteger(n) || n < 1 || n > 20) return "The nozzle number must be a whole number from 1 to 20."
  if (!label.trim()) return "Name the dispenser this nozzle is on."
  return null
}

/** What to add to the save body. Nothing is sent for an ungrouped pump (so older behaviour is unchanged); clearing a grouped pump sends an empty label. */
export function nozzleFields(label: string, number: string, hadGrouping: boolean): { dispenserLabel?: string; nozzleNumber?: number } {
  const l = label.trim()
  if (!l) return hadGrouping ? { dispenserLabel: "" } : {}
  return number.trim() === "" ? { dispenserLabel: l } : { dispenserLabel: l, nozzleNumber: Number(number) }
}

export const nozzleLabel = (p: Pick<Pump, "dispenserLabel" | "nozzleNumber">): string =>
  p.dispenserLabel ? (p.nozzleNumber ? `${p.dispenserLabel}, nozzle ${p.nozzleNumber}` : p.dispenserLabel) : "–"

export function validateOpen(attendant: string, rows: { pumpId: string; meter: string; name: string }[]): string | null {
  if (!attendant.trim()) return "Enter the attendant's name."
  if (rows.length === 0) return "Choose at least one pump."
  for (const r of rows) {
    const m = num(r.meter)
    if (Number.isNaN(m) || m < 0) return `Enter the opening meter for ${r.name}.`
  }
  return null
}

export interface CloseRow { pumpId: string; name: string; opening: number; price: number; closing: string; test: string }

/** Litres and value per pump from what has been typed so far; null while a row is not valid. */
export function pumpTotals(r: CloseRow): { litres: number; value: number } | null {
  const c = num(r.closing); if (Number.isNaN(c) || c < r.opening) return null
  const t = r.test.trim() === "" ? 0 : num(r.test); if (Number.isNaN(t) || t < 0 || t > c - r.opening) return null
  const litres = round2(c - r.opening - t)
  return { litres, value: round2(litres * r.price) }
}

export function expectedTotals(rows: CloseRow[]): { litres: number; value: number } {
  return rows.reduce((a, r) => { const t = pumpTotals(r); return t ? { litres: round2(a.litres + t.litres), value: round2(a.value + t.value) } : a }, { litres: 0, value: 0 })
}

export const takings = (cash: string, card: string, account: string, other: string, fleetCard = "") =>
  round2([cash, card, account, other, fleetCard].reduce((a, s) => a + (s.trim() === "" ? 0 : Number(s) || 0), 0))

/** tolerance defaults to the original R5.00; callers pass the company's own figure. */
export const needsReason = (variance: number, tolerance: number = VARIANCE_TOLERANCE) => Math.abs(variance) > tolerance

export function varianceText(v: number | null | undefined): string {
  if (v == null) return "–"
  if (Math.abs(v) < 0.005) return "Balanced"
  return v < 0 ? `${fmtR(Math.abs(v))} short` : `${fmtR(v)} over`
}

export function validateClose(rows: CloseRow[], amounts: { cash: string; card: string; account: string; other: string; fleetCard?: string }, reason: string,
  opts: { tolerance?: number; adjustments?: number } = {}): string | null {
  for (const r of rows) {
    const c = num(r.closing)
    if (Number.isNaN(c)) return `Enter the closing meter for ${r.name}.`
    if (c < r.opening) return `The closing meter for ${r.name} is below the opening meter (${r.opening}).`
    const t = r.test.trim() === "" ? 0 : num(r.test)
    if (Number.isNaN(t) || t < 0) return `Test litres for ${r.name} must be zero or more.`
    if (t > c - r.opening) return `Test litres for ${r.name} are more than the meter moved.`
  }
  for (const [k, v] of Object.entries(amounts)) {
    if (v !== undefined && v.trim() !== "" && (Number.isNaN(Number(v)) || Number(v) < 0)) return `${k.charAt(0).toUpperCase() + k.slice(1)} must be zero or more.`
  }
  const variance = round2(takings(amounts.cash, amounts.card, amounts.account, amounts.other, amounts.fleetCard ?? "") - (expectedTotals(rows).value - (opts.adjustments ?? 0)))
  if (needsReason(variance, opts.tolerance) && reason.trim().length < 5) return `The takings are ${varianceText(variance)}. Give a reason (at least 5 characters) to close the shift.`
  return null
}

export const ADJ_LABEL: Record<string, string> = { REFUND: "Refund", VOID: "Void", DISCOUNT: "Discount" }
export const ADJ_STATUS_LABEL: Record<string, string> = { PENDING: "Waiting for a supervisor", APPROVED: "Approved", REJECTED: "Rejected", WITHDRAWN: "Withdrawn" }

export function validateAdjustment(amount: string, reason: string): string | null {
  const a = num(amount)
  if (Number.isNaN(a) || a <= 0) return "Enter an amount above R 0."
  if (reason.trim().length < 5) return "Give a reason of at least 5 characters."
  return null
}
export const pendingAdjustments = (l: ShiftAdjustment[] | undefined) => (l ?? []).filter(a => a.status === "PENDING").length
export const approvedAdjustmentTotal = (l: ShiftAdjustment[] | undefined) => round2((l ?? []).filter(a => a.status === "APPROVED").reduce((t, a) => t + a.amount, 0))
/** A supervisor may decide an entry only if they did not record it. */
export const canDecideAdjustment = (a: ShiftAdjustment, me: string | undefined) => a.status === "PENDING" && !!me && a.recordedBy !== me
export const canWithdrawAdjustment = (a: ShiftAdjustment, me: string | undefined) => a.status === "PENDING" && !!me && a.recordedBy === me
export const awaitingReview = (l: Shift[] | undefined) => (l ?? []).filter(s => s.status === "CLOSED" && s.reviewStatus === "PENDING_REVIEW").length
