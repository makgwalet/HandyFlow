// Wording and checks for booking site resources into timetable slots. Booking says nothing about whether a resource meets any standard.
// The server enforces every rule and does the clash check.

export type Booking = {
  id: string; slotId: string; resourceId: string; resourceNo: string | null; resourceName: string | null; resourceType: string | null; resourceStatus: string | null
  quantity: number; status: string; clashNote: string | null; overrideReason: string | null; overrideAt: string | null; releasedReason: string | null; releasedAt: string | null
  slotStatus: string; clashesNow: boolean; clashNowNote: string | null; venueNote: string | null; createdAt: string
}
export type CheckResult = { ok: boolean; clash: string | null }
export type ResourceOption = { id: string; resourceNo: string; name: string; quantity: number; shareable: boolean; status: string }

export const MIN_NOTE = 10
export const BOOKING_NOTE = "Booking a resource does not show that it meets any standard. Equipment that is not shareable cannot be booked beyond the quantity the site holds at the same time without an administrator's written approval."

export type BookForm = { resourceId: string; quantity: string; overrideReason: string }
export const emptyBooking = (): BookForm => ({ resourceId: "", quantity: "1", overrideReason: "" })

export function bookProblem(f: BookForm): string | null {
  if (!f.resourceId) return "Choose the resource"
  const q = Number(f.quantity)
  if (!Number.isInteger(q) || q < 1) return "Book at least one"
  return null
}
export const bookBody = (f: BookForm) => ({ resourceId: f.resourceId, quantity: Number(f.quantity), overrideReason: f.overrideReason.trim() || null })
export const overrideProblem = (reason: string): string | null => (reason.trim().length >= MIN_NOTE ? null : `Say why the clash is acceptable (at least ${MIN_NOTE} characters)`)
export const releaseProblem = (reason: string): string | null => (reason.trim().length >= MIN_NOTE ? null : `Give a reason of at least ${MIN_NOTE} characters`)
export const bookingState = (b: Pick<Booking, "status" | "slotStatus">): string => (b.status === "RELEASED" ? "Released" : b.slotStatus !== "SCHEDULED" ? "Slot cancelled" : "Booked")
export const bookingWarning = (b: Booking): string | null => {
  if (b.status !== "BOOKED" || b.slotStatus !== "SCHEDULED") return null
  if (b.clashesNow) return `${b.clashNowNote}. This is a prompt to check, not a finding`
  if (b.resourceStatus && b.resourceStatus !== "IN_SERVICE") return "That resource is no longer in service. Check the slot still has what it needs"
  return null
}
