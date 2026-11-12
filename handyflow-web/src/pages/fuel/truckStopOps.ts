// E01: truck stop services and reservations. Reservations are Bookings records; these helpers only shape what the screen shows and checks.
export const KINDS = ["PARKING", "SHOWER", "WASH", "WEIGHBRIDGE", "WORKSHOP", "FOOD", "OTHER"] as const
export type Kind = typeof KINDS[number]
export const KIND_LABEL: Record<string, string> = { PARKING: "Parking", SHOWER: "Showers", WASH: "Truck wash", WEIGHBRIDGE: "Weighbridge", WORKSHOP: "Workshop", FOOD: "Food", OTHER: "Other" }

export interface Resource { id: string; name: string }
export interface Offering { id: string; bookingsServiceId: string; serviceName: string; kind: Kind; depotId: string | null; active: boolean; durationMinutes: number; price: number | null; resources: Resource[]; serviceMissing: boolean }
export interface ServiceOption { id: string; name: string; durationMinutes: number; price: number | null }
export interface Slot { start: string; end: string; label: string }
export interface Reservation {
  id: string; offeringId: string; kind: Kind | null; serviceName: string | null; bookingId: string; bookingNumber: string | null; vehicleRegistration: string
  driverName: string | null; driverPhone: string | null; resourceName: string | null; date: string | null; start: string | null; end: string | null; status: string; price: number | null
}

export const STATUS_LABEL: Record<string, string> = { PENDING: "Pending", CONFIRMED: "Confirmed", IN_PROGRESS: "In progress", COMPLETED: "Completed", CANCELLED: "Cancelled", NO_SHOW: "No-show", UNKNOWN: "Not found in Bookings" }
export const statusLabel = (s: string) => STATUS_LABEL[s] ?? s

/** Cancel, complete and no-show only make sense while the booking is still live. */
export const isOpen = (s: string) => s === "PENDING" || s === "CONFIRMED" || s === "IN_PROGRESS"

export const timeText = (t: string | null) => (t ? t.slice(0, 5) : "–")

/** A service with bays needs one chosen, as Bookings can only stop double booking per bay. */
export function validateReservation(o: Offering | undefined, resourceId: string, date: string, start: string, registration: string): string | null {
  if (!o) return "Choose the service."
  if (!registration.trim()) return "Enter the vehicle registration."
  if (o.resources.length > 0 && !resourceId) return "Choose which bay or station to reserve."
  if (!date) return "Choose the date."
  if (!start) return "Choose a time slot."
  return null
}
