// The fleet-wide fuel log: default period, the request address, and how a row and the totals read.
export const FUEL_LOG_PAGE_SIZE = 25
export const DEFAULT_DAYS = 30

export interface FuelLogRow { id: string; vehicleId: string; registration: string; filledAt: string; litres: number; pricePerLitre: number | null; totalCost: number | null; odometerAtFillup: number | null; station: string | null; receiptRef: string | null; fullTank: boolean }
export interface FuelLogTotals { fillups: number; litres: number; spend: number; avgPricePerLitre: number }
export interface FuelLogFilter { from: string; to: string; q: string; vehicleId?: string }

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
/** The last 30 days ending today, the same window the server uses when no dates are sent. */
export function defaultPeriod(today = new Date()): { from: string; to: string } {
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (DEFAULT_DAYS - 1))
  return { from: iso(start), to: iso(today) }
}

/** What is wrong with the chosen dates, or null. The server refuses the same cases. */
export function periodProblem(from: string, to: string): string | null {
  if (!from || !to) return "Choose both dates."
  if (from > to) return "The start date is after the end date."
  const days = (Date.parse(to) - Date.parse(from)) / 86400000
  return days > 366 ? "Choose a period of a year or less." : null
}

function params(f: FuelLogFilter): URLSearchParams {
  const p = new URLSearchParams({ from: f.from, to: f.to })
  if (f.q.trim()) p.set("q", f.q.trim())
  if (f.vehicleId) p.set("vehicleId", f.vehicleId)
  return p
}
export const logUrl = (f: FuelLogFilter, page: number) => `/api/v1/fleet/fuel-log?${params(f)}&page=${page}&size=${FUEL_LOG_PAGE_SIZE}`
export const summaryUrl = (f: FuelLogFilter) => `/api/v1/fleet/fuel-log/summary?${params(f)}`

export const litresText = (n: number) => `${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 1, maximumFractionDigits: 1 })} L`
export const randText = (n: number | null | undefined) => n == null ? "—" : `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
export const priceText = (n: number | null | undefined) => n == null ? "—" : `R ${Number(n).toFixed(3)}`
