// What the top of a vehicle panel says at a glance: when it is next due a service and where its three documents stand.
import { daysToDate, expiryState, type ExpiryState } from "./driverScores.logic"
import { serviceBar, type Bar, type ListVehicle } from "./fleetBoards.logic"
import type { Tone } from "./fleetTones"

export interface DocChip { label: string; tone: Tone; text: string }
export interface VehicleSummary {
  service: Bar
  /** Kilometres to the next service (negative when overdue), or null when no interval or odometer is known. */
  nextServiceKm: number | null
  lastServiceKm: number | null
  odometer: number | null
  docs: DocChip[]
}
type Source = Pick<ListVehicle, "currentOdometer" | "lastServiceKm" | "serviceIntervalKm" | "licenceDiscExpiry" | "roadworthyExpiry" | "insuranceExpiry">

const TONE_OF: Record<ExpiryState, Tone> = { NOT_SET: "none", EXPIRED: "bad", EXPIRING: "warn", VALID: "ok" }
function docText(iso: string | null | undefined, today: Date): string {
  if (!iso) return "Not set"
  const d = daysToDate(iso, today)
  return d < 0 ? `Expired ${-d} ${d === -1 ? "day" : "days"} ago` : d === 0 ? "Expires today" : `${d} ${d === 1 ? "day" : "days"} left`
}

export function vehicleSummary(v: Source, today = new Date()): VehicleSummary {
  const interval = v.serviceIntervalKm ?? 0, known = interval > 0 && v.currentOdometer != null
  return {
    service: serviceBar(v),
    nextServiceKm: known ? interval - (v.currentOdometer! - (v.lastServiceKm ?? 0)) : null,
    lastServiceKm: v.lastServiceKm ?? null,
    odometer: v.currentOdometer ?? null,
    docs: ([["Licence disc", v.licenceDiscExpiry], ["Roadworthy", v.roadworthyExpiry], ["Insurance", v.insuranceExpiry]] as const)
      .map(([label, iso]) => ({ label, tone: TONE_OF[expiryState(iso, today)], text: docText(iso, today) })),
  }
}
