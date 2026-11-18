// Variance investigation: what the open variances add up to and where they cluster. Patterns are a reason to look, not a finding.
import type { Variance } from "./distributionOps"

export type StatusFilter = "ALL" | "OPEN" | "INVESTIGATING" | "CLOSED"
export interface Totals { open: number; investigating: number; closed: number; shortLitres: number; overLitres: number; writtenOffLitres: number }
export interface Group { key: string; count: number; open: number; shortLitres: number; overLitres: number; net: number; repeats: boolean }

/** Short and over litres across variances that are not closed; written-off litres across closed ones. Short and over are kept apart so one cannot hide the other. */
export function totals(vs: Variance[]): Totals {
  const t: Totals = { open: 0, investigating: 0, closed: 0, shortLitres: 0, overLitres: 0, writtenOffLitres: 0 }
  for (const v of vs) {
    if (v.status === "CLOSED") { t.closed++; if (v.decision === "WRITTEN_OFF" && v.varianceLitres < 0) t.writtenOffLitres += -v.varianceLitres; continue }
    if (v.status === "OPEN") t.open++; else t.investigating++
    if (v.varianceLitres < 0) t.shortLitres += -v.varianceLitres; else t.overLitres += v.varianceLitres
  }
  return t
}

export function filterVariances(vs: Variance[], status: StatusFilter, search: string): Variance[] {
  const q = search.trim().toLowerCase()
  return vs.filter(v => (status === "ALL" || v.status === status)
    && (!q || [v.receiptNumber, v.driverName, v.vehicleReg].some(f => (f ?? "").toLowerCase().includes(q))))
}

/** Variances grouped by driver or vehicle, the most litres short first. Two or more in a group is marked as repeating. Loads with no driver or vehicle are grouped under "Not recorded". */
export function groupBy(vs: Variance[], by: "driver" | "vehicle"): Group[] {
  const m = new Map<string, Group>()
  for (const v of vs) {
    const key = (by === "driver" ? v.driverName : v.vehicleReg)?.trim() || "Not recorded"
    const g = m.get(key) ?? { key, count: 0, open: 0, shortLitres: 0, overLitres: 0, net: 0, repeats: false }
    g.count++; if (v.status !== "CLOSED") g.open++
    if (v.varianceLitres < 0) g.shortLitres += -v.varianceLitres; else g.overLitres += v.varianceLitres
    g.net += v.varianceLitres
    m.set(key, g)
  }
  return [...m.values()].map(g => ({ ...g, repeats: g.count >= 2 && g.key !== "Not recorded" }))
    .sort((a, b) => b.shortLitres - a.shortLitres || b.count - a.count || a.key.localeCompare(b.key))
}

export const litresText = (n: number) => `${Math.round(n).toLocaleString("en-ZA")} L`
