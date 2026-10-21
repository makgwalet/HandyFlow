// What the Overview tab of a vehicle adds to the summary band: the key facts and the last 30 days of fuel as a column chart.
import { STATUS_CFG } from "./vehicleStatus"

export interface Fill { filledAt: string; litres: number | string; totalCost?: number | string | null }
export interface DayBar { date: string; litres: number; fraction: number }
export interface Fuel30 { litres: number; cost: number; fillups: number; bars: DayBar[] }

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`

/** Litres and cost over the last `days` days (today included), and one bar per day, scaled to the biggest day. Days with no fill-up are empty bars. */
export function fuelLastDays(fills: Fill[], today = new Date(), days = 30): Fuel30 {
  const dates = Array.from({ length: days }, (_, i) => iso(new Date(today.getFullYear(), today.getMonth(), today.getDate() - (days - 1 - i))))
  const byDay = new Map<string, number>(dates.map(d => [d, 0]))
  let litres = 0, cost = 0, fillups = 0
  for (const f of fills) {
    const day = f.filledAt.slice(0, 10)
    if (!byDay.has(day)) continue
    const l = Number(f.litres) || 0
    byDay.set(day, (byDay.get(day) ?? 0) + l); litres += l; cost += Number(f.totalCost) || 0; fillups++
  }
  const max = Math.max(0, ...byDay.values())
  return { litres, cost, fillups, bars: dates.map(date => ({ date, litres: byDay.get(date) ?? 0, fraction: max === 0 ? 0 : (byDay.get(date) ?? 0) / max })) }
}

export interface KeyFacts {
  registration?: string; year?: number | null; colour?: string | null; fuelType?: string | null; vehicleType?: string | null; status?: string
  currentOdometer?: number | null; engineHours?: number | null; assignedDriverName?: string | null; dailyRate?: number | null
}
/** The "Key information" panel: always the same rows, with "Not recorded" for gaps so missing data is visible. */
export function keyFacts(v: KeyFacts): { label: string; value: string; missing: boolean }[] {
  const n = (x: number | null | undefined) => (x == null ? null : Number(x).toLocaleString("en-ZA"))
  const rows: [string, string | null | undefined][] = [
    ["Operational status", v.status ? (STATUS_CFG[v.status]?.label ?? v.status) : null],
    ["Assigned driver", v.assignedDriverName],
    ["Vehicle type", v.vehicleType], ["Fuel type", v.fuelType], ["Year", v.year == null ? null : String(v.year)], ["Colour", v.colour],
    ["Odometer", v.currentOdometer == null ? null : `${n(v.currentOdometer)} km`], ["Engine hours", v.engineHours == null ? null : `${n(v.engineHours)} h`],
    ["Daily rate", v.dailyRate == null ? null : `R ${Number(v.dailyRate).toLocaleString("en-ZA")}`],
  ]
  return rows.map(([label, value]) => ({ label, value: value || "Not recorded", missing: !value }))
}

/** "Santam · Policy ST123456", or null when no policy details are on file. */
export function insuranceLine(i: { insurer: string | null; policyNumber: string | null } | null | undefined): string | null {
  if (!i || (!i.insurer && !i.policyNumber)) return null
  return [i.insurer, i.policyNumber ? `Policy ${i.policyNumber}` : null].filter(Boolean).join(" · ")
}
