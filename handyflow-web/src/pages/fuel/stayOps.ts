// Phase 7d helpers: overnight stays at the truck stop. Display and checks only; the server counts nights and enforces bays.
export type StayStatus = "BOOKED" | "CHECKED_IN" | "CHECKED_OUT" | "CANCELLED" | "NO_SHOW"
export interface Stay {
  id: string; vehicleRegistration: string; driverName: string | null; driverPhone: string | null; arrivalDate: string; departureDate: string; actualDepartureDate: string | null
  status: StayStatus; ratePerNight: number; nights: number; amount: number; note: string | null; cancelledReason: string | null; overstaying: boolean; checkedInAt: string | null; checkedOutAt: string | null
}
export interface ParkingSettings { nightlyRate: number | null; bays: number | null }
export interface Occupancy { date: string; inYard: number; arrivals: number; departures: number; bays: number | null; free: number | null }
export interface Night { date: string; used: number; arrivals: number; departures: number; free: number | null }
export interface Overstay { id: string; vehicleRegistration: string; plannedDeparture: string; daysOver: number }
export interface Capacity { from: string; bays: number | null; nights: Night[]; overstays: Overstay[] }
export interface StayForm { vehicleRegistration: string; driverName: string; driverPhone: string; arrivalDate: string; departureDate: string; note: string }
export const emptyStay = (today: string): StayForm => ({ vehicleRegistration: "", driverName: "", driverPhone: "", arrivalDate: today, departureDate: "", note: "" })

export const STATUS_LABEL: Record<StayStatus, string> = { BOOKED: "Booked", CHECKED_IN: "In the yard", CHECKED_OUT: "Left", CANCELLED: "Cancelled", NO_SHOW: "No-show" }

/** Message for the booking form, or null when it can be sent. */
export function validateStay(f: StayForm, today: string, rateSet: boolean): string | null {
  if (!rateSet) return "Set the nightly rate first."
  if (!f.vehicleRegistration.trim()) return "Give the truck's registration."
  if (!f.arrivalDate || !f.departureDate) return "Give the arrival and departure dates."
  if (f.arrivalDate < today) return "The arrival cannot be in the past. For a truck already here, use today's date."
  if (f.departureDate <= f.arrivalDate) return "The departure must be after the arrival."
  return null
}

/** Message for the settings form, or null. Both boxes may be blank. */
export function validateSettings(rate: string, bays: string): string | null {
  if (rate.trim() !== "") { const r = Number(rate); if (!Number.isFinite(r) || r < 0) return "The nightly rate must be zero or more." }
  if (bays.trim() !== "") { const b = Number(bays); if (!Number.isInteger(b) || b <= 0) return "Bays must be a whole number above zero, or left empty for no limit." }
  return null
}

export const nightsText = (s: Stay): string => `${s.nights} ${s.nights === 1 ? "night" : "nights"}`

/** Which buttons a stay offers, by status. Check-in only from the arrival date. */
export function stayActions(s: Stay, today: string): ("checkIn" | "checkOut" | "extend" | "cancel" | "noShow")[] {
  if (s.status === "BOOKED") return [...(s.arrivalDate <= today ? ["checkIn" as const] : []), "extend", "cancel", ...(s.arrivalDate <= today ? ["noShow" as const] : [])]
  if (s.status === "CHECKED_IN") return ["checkOut", "extend"]
  return []
}

/** How full a night is, for colouring: "full" when no bay is free, "tight" when one or two are, "ok" otherwise, "open" with no bay limit. */
export function nightState(n: Night, bays: number | null): "open" | "ok" | "tight" | "full" {
  if (bays == null || n.free == null) return "open"
  if (n.free === 0) return "full"
  return n.free <= 2 ? "tight" : "ok"
}

/** The first night with no free bay, or null. Used for the one line summary above the table. */
export function firstFullNight(c: Capacity): string | null {
  const n = c.nights.find(x => nightState(x, c.bays) === "full")
  return n ? n.date : null
}
