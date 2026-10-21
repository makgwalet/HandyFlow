// Rules for the Fleet list and the dashboards built on it: the two list bars, document counts, searching and sorting.
// Pure functions; the components fetch and draw.
import { daysToDate, expiryState } from "./driverScores.logic"
import { isOpenOrder } from "./vehicleTabs.logic"
import { deviationText, deviationTone, type FuelRow } from "./fuelAnalytics.logic"
import type { Tone } from "./fleetTones"

export interface ListVehicle {
  id: string; registration: string; make: string | null; model: string | null; year: number | null; status: string
  assignedDriverName: string | null; currentOdometer: number | null; lastServiceKm: number | null; serviceIntervalKm: number | null
  licenceDiscExpiry: string | null; roadworthyExpiry: string | null; insuranceExpiry: string | null
  // Present on the real list; the screen shows them when it has them.
  assignedDriverId?: string | null; vehicleType?: string | null; colour?: string | null; fuelType?: string | null; dailyRate?: number | null
  notes?: string | null; vin?: string | null; engineNumber?: string | null; dueForService?: boolean
}
export interface Bar { fraction: number; tone: Tone; text: string }
const km = (n: number) => `${Math.abs(Math.round(n)).toLocaleString("en-ZA")} km`

/** Fuel efficiency against the thirstiest vehicle in the fleet; the colour is how far it is from its own usual consumption. There is no fuel-level data. */
export function fuelBar(l100: number | null | undefined, deviationPercent: number | null | undefined, fleetMaxL100: number): Bar {
  if (l100 == null) return { fraction: 0, tone: "none", text: "No fuel data" }
  const dev = deviationPercent ?? null
  return { fraction: fleetMaxL100 > 0 ? l100 / fleetMaxL100 : 0, tone: deviationTone(dev), text: `${l100.toFixed(1)} L/100 km${dev != null ? ` · ${deviationText(dev)}` : ""}` }
}
/** Distance since the last service as a share of the interval; red at or past the interval, amber from 85%. */
export function serviceBar(v: Pick<ListVehicle, "currentOdometer" | "lastServiceKm" | "serviceIntervalKm">): Bar {
  const interval = v.serviceIntervalKm ?? 0
  if (!interval || v.currentOdometer == null) return { fraction: 0, tone: "none", text: "No service interval set" }
  const used = v.currentOdometer - (v.lastServiceKm ?? 0), left = interval - used, fraction = used / interval
  return { fraction, tone: fraction >= 1 ? "bad" : fraction >= 0.85 ? "warn" : "ok", text: left < 0 ? `Overdue by ${km(left)}` : `${km(left)} to go` }
}
/** How many of the three vehicle documents are expired or expiring (30 days), by the one calendar-day rule. */
export function documentBadge(v: Pick<ListVehicle, "licenceDiscExpiry" | "roadworthyExpiry" | "insuranceExpiry">, today = new Date()): { expired: number; expiring: number; tone: Tone; text: string } {
  const dates = [v.licenceDiscExpiry, v.roadworthyExpiry, v.insuranceExpiry]
  const states = dates.map(d => expiryState(d, today)), expired = states.filter(s => s === "EXPIRED").length, expiring = states.filter(s => s === "EXPIRING").length
  if (expired > 0) return { expired, expiring, tone: "bad", text: `${expired} expired` }
  if (expiring > 0) return { expired, expiring, tone: "warn", text: `${expiring} expiring` }
  return states.every(s => s === "NOT_SET") ? { expired, expiring, tone: "none", text: "No dates" } : { expired, expiring, tone: "ok", text: "Clear" }
}

export interface FleetRow { vehicle: ListVehicle; fuel: Bar; service: Bar; docs: ReturnType<typeof documentBadge>; l100: number | null; remainingKm: number | null }
export function fleetRows(vehicles: ListVehicle[], fuel: FuelRow[], today = new Date()): FleetRow[] {
  const byId = new Map(fuel.map(f => [f.vehicleId, f])), max = Math.max(0, ...fuel.map(f => f.summary.litresPer100Km ?? 0))
  return vehicles.map(v => {
    const f = byId.get(v.id), l100 = f?.summary.litresPer100Km ?? null, interval = v.serviceIntervalKm ?? 0
    return { vehicle: v, fuel: fuelBar(l100, f?.latestDeviationPercent, max), service: serviceBar(v), docs: documentBadge(v, today), l100,
      remainingKm: interval && v.currentOdometer != null ? interval - (v.currentOdometer - (v.lastServiceKm ?? 0)) : null }
  })
}

/** The headline counts above the table. */
export interface FleetTile { id: "total" | "available" | "on-trip" | "service" | "breakdown"; label: string; value: number; tone: "ok" | "warn" | "bad" | "none" }
export function fleetTiles(vehicles: ListVehicle[]): FleetTile[] {
  const n = (f: (v: ListVehicle) => boolean) => vehicles.filter(f).length
  return [
    { id: "total", label: "Total vehicles", value: vehicles.length, tone: "none" },
    { id: "available", label: "Available", value: n(v => v.status === "AVAILABLE"), tone: "ok" },
    { id: "on-trip", label: "On trip", value: n(v => v.status === "ON_TRIP"), tone: "none" },
    { id: "service", label: "Service due", value: n(v => v.dueForService === true), tone: "warn" },
    { id: "breakdown", label: "Breakdown", value: n(v => v.status === "BREAKDOWN"), tone: "bad" },
  ]
}

export type FleetSort = "registration" | "fuel" | "service" | "documents"
export const SORT_OPTIONS: { id: FleetSort; label: string }[] = [{ id: "registration", label: "Registration" }, { id: "fuel", label: "Thirstiest first" }, { id: "service", label: "Service most due" }, { id: "documents", label: "Documents most urgent" }]
/** Search over registration, make, model, driver, VIN and engine number (every word must match); status filter; then the chosen order, ties by registration. */
export function viewFleet(rows: FleetRow[], search: string, status: string, sort: FleetSort): FleetRow[] {
  const words = search.toLowerCase().split(/\s+/).filter(Boolean)
  const hay = (r: FleetRow) => `${r.vehicle.registration} ${r.vehicle.make ?? ""} ${r.vehicle.model ?? ""} ${r.vehicle.assignedDriverName ?? ""} ${r.vehicle.vin ?? ""} ${r.vehicle.engineNumber ?? ""}`.toLowerCase()
  const nullsLast = (a: number | null, b: number | null, dir: 1 | -1) => (a == null && b == null ? 0 : a == null ? 1 : b == null ? -1 : dir * (a - b))
  return rows.filter(r => (status === "ALL" || r.vehicle.status === status) && words.every(w => hay(r).includes(w)))
    .sort((a, b) => (sort === "fuel" ? nullsLast(a.l100, b.l100, -1) : sort === "service" ? nullsLast(a.remainingKm, b.remainingKm, 1)
      : sort === "documents" ? b.docs.expired - a.docs.expired || b.docs.expiring - a.docs.expiring : 0) || a.vehicle.registration.localeCompare(b.vehicle.registration))
}

// ── Maintenance dashboard ──────────────────────────────────────────────────

export interface BoardOrder { id: string; referenceNumber: string; registration: string; title: string; priority: string; status: string; scheduledFor: string | null; createdAt: string; completedAt: string | null; actualTotal: number | null }
export interface OrderSummary { open: number; awaitingApproval: number; overdue: number; avgDaysToComplete: number | null }
/** Work in flight. Overdue = still open and scheduled before today. Average days is created-to-completed over the last 90 days. */
export function orderSummary(orders: BoardOrder[], today = new Date()): OrderSummary {
  const open = orders.filter(o => isOpenOrder(o.status))
  const done = orders.filter(o => o.status === "COMPLETED" && o.completedAt && daysToDate(o.completedAt, today) >= -90)
  const days = done.map(o => (new Date(o.completedAt as string).getTime() - new Date(o.createdAt).getTime()) / 86400000)
  return {
    open: open.length, awaitingApproval: orders.filter(o => o.status === "QUOTED").length,
    overdue: open.filter(o => o.scheduledFor && daysToDate(o.scheduledFor, today) < 0).length,
    avgDaysToComplete: days.length ? Math.round((days.reduce((s, d) => s + d, 0) / days.length) * 10) / 10 : null,
  }
}
export const FLIGHT_STATUSES = ["OPEN", "QUOTED", "APPROVED", "IN_PROGRESS", "ON_HOLD"] as const
export function statusBars(orders: BoardOrder[]): { status: string; count: number; fraction: number }[] {
  const counts = FLIGHT_STATUSES.map(status => ({ status, count: orders.filter(o => o.status === status).length })), max = Math.max(0, ...counts.map(c => c.count))
  return counts.map(c => ({ ...c, fraction: max > 0 ? c.count / max : 0 }))
}
/** Completed work by the month it was finished, for the last `months` months including this one (oldest first). */
export function spendByMonth(orders: BoardOrder[], now = new Date(), months = 6): { key: string; label: string; value: number }[] {
  const slots = Array.from({ length: months }, (_, i) => { const d = new Date(now.getFullYear(), now.getMonth() - (months - 1 - i), 1); return { key: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`, label: d.toLocaleDateString("en-ZA", { month: "short" }), value: 0 } })
  for (const o of orders) { if (o.status !== "COMPLETED" || !o.completedAt) continue; const slot = slots.find(s => s.key === o.completedAt!.slice(0, 7)); if (slot) slot.value += Number(o.actualTotal ?? 0) }
  return slots
}
export const SERVICE_WINDOW_KM = 2000
/** Vehicles overdue or within 2 000 km of their service, most overdue first. Retired vehicles and those with no interval are left out. */
export function serviceQueue(vehicles: ListVehicle[], limit = 8): { vehicle: ListVehicle; bar: Bar; remainingKm: number }[] {
  return vehicles.filter(v => v.status !== "RETIRED" && v.serviceIntervalKm && v.currentOdometer != null)
    .map(v => ({ vehicle: v, bar: serviceBar(v), remainingKm: (v.serviceIntervalKm as number) - ((v.currentOdometer as number) - (v.lastServiceKm ?? 0)) }))
    .filter(x => x.remainingKm <= SERVICE_WINDOW_KM).sort((a, b) => a.remainingKm - b.remainingKm).slice(0, limit)
}
export interface TyreItem { id: string; registration: string | null; position: string | null; brand: string; size: string; lastTreadDepthMm: number | null; treadStatus: string }
export interface BatteryItem { id: string; registration: string | null; position: string | null; brand: string; status: string; lastTestResult: string | null; lastVoltage: number | null }
export const tyreAttention = (tyres: TyreItem[]): TyreItem[] => tyres.filter(t => t.treadStatus === "REPLACE_NOW" || t.treadStatus === "REPLACE_SOON").sort((a, b) => (a.treadStatus === b.treadStatus ? (a.lastTreadDepthMm ?? 99) - (b.lastTreadDepthMm ?? 99) : a.treadStatus === "REPLACE_NOW" ? -1 : 1))
export const batteryAttention = (bs: BatteryItem[]): BatteryItem[] => bs.filter(b => b.lastTestResult === "FAILED" || b.lastTestResult === "WEAK" || b.status === "FAULTY").sort((a, b) => Number(b.lastTestResult === "FAILED" || b.status === "FAULTY") - Number(a.lastTestResult === "FAILED" || a.status === "FAULTY"))

// ── Fuel & Energy dashboard ────────────────────────────────────────────────
const isoDay = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
/** The last `months` calendar months, oldest first; the current month ends today. */
export function monthRanges(now = new Date(), months = 6): { key: string; label: string; from: string; to: string }[] {
  return Array.from({ length: months }, (_, i) => {
    const first = new Date(now.getFullYear(), now.getMonth() - (months - 1 - i), 1), last = new Date(first.getFullYear(), first.getMonth() + 1, 0)
    return { key: isoDay(first).slice(0, 7), label: first.toLocaleDateString("en-ZA", { month: "short" }), from: isoDay(first), to: isoDay(last > now ? now : last) }
  })
}
/** Fuel cost per km across the fleet, each vehicle weighted by the km it was measured over. */
export function fleetCostPerKm(rows: FuelRow[]): number | null {
  const m = rows.filter(r => r.summary.costPerKm != null && r.summary.measuredKm > 0), km = m.reduce((s, r) => s + r.summary.measuredKm, 0)
  return km > 0 ? m.reduce((s, r) => s + Number(r.summary.costPerKm) * r.summary.measuredKm, 0) / km : null
}
export const spendBars = (rows: FuelRow[], limit = 8): { id: string; registration: string; spend: number; fraction: number }[] => {
  const top = rows.filter(r => Number(r.summary.totalSpend) > 0).sort((a, b) => Number(b.summary.totalSpend) - Number(a.summary.totalSpend)).slice(0, limit), max = top.length ? Number(top[0].summary.totalSpend) : 0
  return top.map(r => ({ id: r.vehicleId, registration: r.registration, spend: Number(r.summary.totalSpend), fraction: max > 0 ? Number(r.summary.totalSpend) / max : 0 }))
}
/** Thirstiest first, scaled to the thirstiest; vehicles with no measured consumption are left out rather than shown as zero. */
export const thirstiestBars = (rows: FuelRow[], limit = 8): { id: string; registration: string; bar: Bar }[] => {
  const withData = rows.filter(r => r.summary.litresPer100Km != null).sort((a, b) => (b.summary.litresPer100Km as number) - (a.summary.litresPer100Km as number)), max = withData.length ? (withData[0].summary.litresPer100Km as number) : 0
  return withData.slice(0, limit).map(r => ({ id: r.vehicleId, registration: r.registration, bar: fuelBar(r.summary.litresPer100Km, r.latestDeviationPercent, max) }))
}
export interface StationRow { station: string; fuelType: string; fillups: number; litres: number; totalSpend: number; avgPricePerLitre: number; premiumPercent: number | null }
export const cheapestStations = (st: StationRow[], limit = 5): StationRow[] => [...st].sort((a, b) => a.avgPricePerLitre - b.avgPricePerLitre || a.station.localeCompare(b.station)).slice(0, limit)
export interface FuelAlertRow { kind: string; type: string; severity: string; registration: string; date: string; message: string }
const SEVERITY_RANK: Record<string, number> = { ALERT: 0, WARNING: 1, INFO: 2 }
export const topFuelAlerts = (a: FuelAlertRow[], limit = 5): FuelAlertRow[] => [...a].sort((x, y) => (SEVERITY_RANK[x.severity] ?? 9) - (SEVERITY_RANK[y.severity] ?? 9) || y.date.localeCompare(x.date)).slice(0, limit)
