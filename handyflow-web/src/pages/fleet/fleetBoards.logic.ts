// Rules for the Fleet list and the dashboards built on it: the two list bars, document counts, searching and sorting.
// Pure functions; the components fetch and draw.
import { expiryState } from "./driverScores.logic"
import { deviationText, deviationTone, type FuelRow } from "./fuelAnalytics.logic"
import type { Tone } from "./fleetTones"

export interface ListVehicle {
  id: string; registration: string; make: string | null; model: string | null; year: number | null; status: string
  assignedDriverName: string | null; currentOdometer: number | null; lastServiceKm: number | null; serviceIntervalKm: number | null
  licenceDiscExpiry: string | null; roadworthyExpiry: string | null; insuranceExpiry: string | null
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

export type FleetSort = "registration" | "fuel" | "service" | "documents"
export const SORT_OPTIONS: { id: FleetSort; label: string }[] = [{ id: "registration", label: "Registration" }, { id: "fuel", label: "Thirstiest first" }, { id: "service", label: "Service most due" }, { id: "documents", label: "Documents most urgent" }]
/** Search over registration, make, model and driver (every word must match); status filter; then the chosen order, ties by registration. */
export function viewFleet(rows: FleetRow[], search: string, status: string, sort: FleetSort): FleetRow[] {
  const words = search.toLowerCase().split(/\s+/).filter(Boolean)
  const hay = (r: FleetRow) => `${r.vehicle.registration} ${r.vehicle.make ?? ""} ${r.vehicle.model ?? ""} ${r.vehicle.assignedDriverName ?? ""}`.toLowerCase()
  const nullsLast = (a: number | null, b: number | null, dir: 1 | -1) => (a == null && b == null ? 0 : a == null ? 1 : b == null ? -1 : dir * (a - b))
  return rows.filter(r => (status === "ALL" || r.vehicle.status === status) && words.every(w => hay(r).includes(w)))
    .sort((a, b) => (sort === "fuel" ? nullsLast(a.l100, b.l100, -1) : sort === "service" ? nullsLast(a.remainingKm, b.remainingKm, 1)
      : sort === "documents" ? b.docs.expired - a.docs.expired || b.docs.expiring - a.docs.expiring : 0) || a.vehicle.registration.localeCompare(b.vehicle.registration))
}
