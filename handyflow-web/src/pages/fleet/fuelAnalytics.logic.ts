// Fuel analytics wording and ordering, and the unified fleet alert feed.
export interface FuelRow { vehicleId: string; registration: string; make?: string | null; model?: string | null; summary: { fillups: number; totalLitres: number; totalSpend: number; measuredKm: number; litresPer100Km: number | null; kmPerLitre: number | null; costPerKm: number | null; flaggedSegments: number; indicators: number }; latestDeviationPercent: number | null }

const f1 = (n: number) => n.toFixed(1)
export const consumptionText = (l100: number | null | undefined) => (l100 == null ? "—" : `${f1(l100)} L/100 km`)
export const costPerKmText = (c: number | null | undefined) => (c == null ? "—" : `R ${Number(c).toFixed(2)}/km`)

/** "12% thirstier than usual" / "8% lighter than usual" / "As usual" / "—" with no baseline. */
export function deviationText(d: number | null | undefined): string {
  if (d == null) return "—"
  const r = Math.round(Math.abs(d))
  return d > 0 && r > 0 ? `${r}% thirstier than usual` : d < 0 && r > 0 ? `${r}% lighter than usual` : "As usual"
}
export const deviationTone = (d: number | null | undefined, tolerance = 20): "ok" | "warn" | "bad" | "none" =>
  d == null ? "none" : d > tolerance ? "bad" : d > tolerance / 2 ? "warn" : "ok"

/** Fleet totals across the vehicle rows: litres, spend, km, and overall L/100 km and cost per km over measured km. */
export function fleetTotals(rows: FuelRow[]) {
  const litres = rows.reduce((s, r) => s + Number(r.summary.totalLitres), 0)
  const spend = rows.reduce((s, r) => s + Number(r.summary.totalSpend), 0)
  const km = rows.reduce((s, r) => s + r.summary.measuredKm, 0)
  const measured = rows.filter(r => r.summary.litresPer100Km != null && r.summary.measuredKm > 0)
  const l100 = measured.length ? measured.reduce((s, r) => s + (r.summary.litresPer100Km as number) * r.summary.measuredKm, 0) / measured.reduce((s, r) => s + r.summary.measuredKm, 0) : null
  return { litres, spend, km, l100, flagged: rows.reduce((s, r) => s + r.summary.flaggedSegments + r.summary.indicators, 0) }
}

export const premiumText = (p: number | null | undefined) => (p == null ? "—" : Math.abs(p) < 0.5 ? "At the average" : p > 0 ? `${Math.round(p)}% dearer than average` : `${Math.round(-p)}% cheaper than average`)

/** Dates for the period picker: the first of this month to today, or the last N days. */
export function periodRange(kind: "month" | "30" | "90", today = new Date()): { from: string; to: string } {
  const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
  const to = iso(today)
  if (kind === "month") return { from: iso(new Date(today.getFullYear(), today.getMonth(), 1)), to }
  const from = new Date(today); from.setDate(from.getDate() - Number(kind))
  return { from: iso(from), to }
}

export type Severity = "high" | "medium" | "low"
export interface FleetAlert { key: string; severity: Severity; source: "Fuel" | "Accident" | "Compliance" | "Service"; registration: string; message: string; section: string }
const RANK: Record<Severity, number> = { high: 0, medium: 1, low: 2 }
const SOURCE_RANK: Record<FleetAlert["source"], number> = { Accident: 0, Fuel: 1, Compliance: 2, Service: 3 }

export interface AlertInputs {
  fuel: { kind: string; type: string; severity: string; registration: string; date: string; message: string }[]
  accidents: { id: string; registration: string; status: string; vehicleDrivable: boolean; referenceNumber: string }[]
  vehicles: { id: string; registration: string; status: string; dueForService: boolean; licenceExpiringSoon: boolean; roadworthyExpiringSoon: boolean }[]
}
const BLOCKING = new Set(["REPORTED", "UNDER_ASSESSMENT", "IN_REPAIR", "WRITTEN_OFF"])

/** One feed from fuel flags, vehicles that cannot be driven, expiring papers and services due; most urgent first (within a level: accidents, fuel, compliance, service). */
export function buildAlerts(i: AlertInputs): FleetAlert[] {
  const out: FleetAlert[] = []
  for (const a of i.accidents) if (!a.vehicleDrivable && BLOCKING.has(a.status))
    out.push({ key: `acc-${a.id}`, severity: "high", source: "Accident", registration: a.registration, message: `Cannot be driven after accident ${a.referenceNumber}; blocked from trips.`, section: "accidents" })
  for (const v of i.vehicles) {
    if (v.status === "RETIRED") continue
    if (v.licenceExpiringSoon) out.push({ key: `lic-${v.id}`, severity: "medium", source: "Compliance", registration: v.registration, message: "Licence disc expires soon.", section: "compliance" })
    if (v.roadworthyExpiringSoon) out.push({ key: `rwc-${v.id}`, severity: "medium", source: "Compliance", registration: v.registration, message: "Roadworthy certificate expires soon.", section: "compliance" })
    if (v.dueForService) out.push({ key: `svc-${v.id}`, severity: "medium", source: "Service", registration: v.registration, message: "Service is due.", section: "services" })
  }
  i.fuel.forEach((f, n) => out.push({ key: `fuel-${n}`, severity: f.severity === "ALERT" || f.severity === "HIGH" ? "high" : f.severity === "WARNING" || f.severity === "MEDIUM" ? "medium" : "low", source: "Fuel", registration: f.registration, message: f.message, section: "fuel-analytics" }))
  return out.sort((a, b) => RANK[a.severity] - RANK[b.severity] || SOURCE_RANK[a.source] - SOURCE_RANK[b.source] || a.registration.localeCompare(b.registration))
}
