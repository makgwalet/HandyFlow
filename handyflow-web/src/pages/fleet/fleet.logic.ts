// Numbers behind the dashboard charts and the fleet list search.
export interface VehicleLike { registration: string; make?: string | null; model?: string | null; status: string; assignedDriverName?: string | null; vin?: string | null; engineNumber?: string | null }

export const STATUS_ORDER = ["AVAILABLE", "ON_TRIP", "MAINTENANCE", "BREAKDOWN", "RETIRED"] as const

/** How many vehicles are in each status, in a fixed order, zero rows kept so the legend never jumps. */
export function statusCounts(vehicles: { status: string }[]): { status: string; count: number }[] {
  return STATUS_ORDER.map(status => ({ status, count: vehicles.filter(v => v.status === status).length }))
}

export interface Slice { status: string; count: number; start: number; length: number }
/** Donut slices as fractions of the circle (0..1). Empty statuses are dropped; nothing at all gives no slices. */
export function donutSlices(counts: { status: string; count: number }[]): Slice[] {
  const total = counts.reduce((s, c) => s + c.count, 0)
  if (total === 0) return []
  let at = 0
  return counts.filter(c => c.count > 0).map(c => { const length = c.count / total; const s = { ...c, start: at, length }; at += length; return s })
}

/** Search across registration, make, model, driver, VIN and engine number (case-insensitive, every word must match) plus a status filter. */
export function filterVehicles<T extends VehicleLike>(vehicles: T[], search: string, status: string): T[] {
  const words = search.toLowerCase().split(/\s+/).filter(Boolean)
  return vehicles.filter(v => {
    if (status !== "ALL" && v.status !== status) return false
    const hay = [v.registration, v.make, v.model, v.assignedDriverName, v.vin, v.engineNumber].filter(Boolean).join(" ").toLowerCase()
    return words.every(w => hay.includes(w))
  })
}

export interface CostRow { registration: string; totalCost: number }
/** The costliest vehicles with each bar as a share of the largest (0..1). */
export function costBars(rows: CostRow[], limit = 5): { registration: string; totalCost: number; share: number }[] {
  const top = [...rows].filter(r => r.totalCost > 0).sort((a, b) => b.totalCost - a.totalCost).slice(0, limit)
  const max = top[0]?.totalCost ?? 0
  return top.map(r => ({ ...r, share: max > 0 ? r.totalCost / max : 0 }))
}
