// F02: helpers for the actual-versus-estimate view of routes.
export interface RouteCostRow {
  routeId: string; routeDate: string; label: string | null; tankerRegistration: string | null; driverName: string | null; state: string
  stops: number; deliveredCount: number; litresDelivered: number
  estimateVehicle: number | null; estimateDriver: number | null; estimateTotal: number | null
  actualVehicle: number | null; actualDriver: number | null; actualTolls: number | null; actualOther: number | null; actualTotal: number | null
  variance: number | null; variancePct: number | null; actualCostPerLitre: number | null; reconciled: boolean; flags: string[]
}
export interface Rollup { key: string; label: string; routes: number; reconciledRoutes: number; litres: number; estimate: number; comparableActual: number; variance: number; actualTotal: number; actualCostPerLitre: number | null }
export interface RouteCostReport { from: string; to: string; ratesSet: boolean; unreconciledRoutes: number; routes: RouteCostRow[]; byTanker: Rollup[]; byDepot: Rollup[] }
export interface CostEntry { id: string; routeId: string; category: string; amount: number; quantity: number | null; unit: string | null; note: string | null; status: string; voidReason: string | null; recordedAt: string }

export const CATEGORIES: { code: string; label: string; hint: string }[] = [
  { code: "VEHICLE", label: "Vehicle", hint: "Compared with the estimate" },
  { code: "DRIVER", label: "Driver", hint: "Compared with the estimate" },
  { code: "TOLLS", label: "Tolls", hint: "Shown, not compared" },
  { code: "OTHER", label: "Other (describe)", hint: "Shown, not compared" },
]
export const categoryLabel = (c: string) => CATEGORIES.find(x => x.code === c)?.label ?? c

const FLAGS: Record<string, string> = {
  RATES_NOT_SET: "Cost rates not set in Settings",
  NO_DISTANCE: "No site distance, so no vehicle estimate",
  NO_ACTUALS: "No actual costs recorded",
  PARTIAL_DELIVERY: "Not every stop delivered",
}
export const flagText = (f: string) => FLAGS[f] ?? f

/** "+R300 (+30.0%)" or "-R50 (-5.0%)"; a dash when there is nothing to compare. */
export function varianceText(variance: number | null, pct: number | null, fmt: (n: number) => string): string {
  if (variance == null) return "–"
  const sign = variance > 0 ? "+" : variance < 0 ? "-" : ""
  return `${sign}${fmt(Math.abs(variance))}${pct == null ? "" : ` (${sign}${Math.abs(pct).toFixed(1)}%)`}`
}

/** Null when the cost entry is fine to send, otherwise the message to show. */
export function validateCost(category: string, amount: string, quantity: string, note: string): string | null {
  if (!category) return "Pick a cost type."
  if (!amount.trim() || Number.isNaN(Number(amount)) || Number(amount) < 0) return "Enter the amount (zero or more)."
  if (quantity.trim() && (Number.isNaN(Number(quantity)) || Number(quantity) < 0)) return "The quantity must be zero or more."
  if (category === "OTHER" && !note.trim()) return "Say what the cost was for."
  return null
}

/** The period ending today, as ISO dates, for a number of days. */
export function periodDates(days: number, today = new Date()): { from: string; to: string } {
  const iso = (d: Date) => d.toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })
  const start = new Date(today.getTime() - (days - 1) * 86400000)
  return { from: iso(start), to: iso(today) }
}
