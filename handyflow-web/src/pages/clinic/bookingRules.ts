/** How far back a booking time may be: a walk-in is booked "now", so a few minutes ago is fine. */
export const WALK_IN_GRACE_MS = 15 * 60 * 1000

/** Why a booking cannot be sent yet, or null when it can. Mirrors the server's own checks. */
export function bookingProblem(form: { patientId: string; scheduledAt: string; durationMinutes: string }, now: number): string | null {
  if (!form.patientId || !form.scheduledAt) return "Patient and date/time are required"
  const t = new Date(form.scheduledAt).getTime()
  if (Number.isNaN(t)) return "Choose a valid date and time"
  if (t < now - WALK_IN_GRACE_MS) return "Cannot book an appointment in the past"
  if (form.durationMinutes) {
    const m = parseInt(form.durationMinutes)
    if (Number.isNaN(m) || m < 5 || m > 480) return "Appointment length must be between 5 and 480 minutes"
  }
  return null
}

/** The server answers 409 when the practitioner already has an overlapping booking. */
export function clashMessage(e: unknown): string | null {
  const r = (e as { response?: { status?: number; data?: { message?: string } } })?.response
  return r?.status === 409 ? (r.data?.message ?? "This practitioner already has an overlapping appointment.") : null
}

/** Value of the room filter that keeps only appointments with no room. */
export const NO_ROOM_FILTER = "none"

/** Whether an appointment passes the practitioner and room filters of the schedule ("all" keeps everything). */
export function passesFilters(
  a: { practitionerId?: string | null; roomId?: string | null },
  doctorFilter: string,
  roomFilter: string,
): boolean {
  if (doctorFilter !== "all" && a.practitionerId !== doctorFilter) return false
  if (roomFilter === "all") return true
  if (roomFilter === NO_ROOM_FILTER) return !a.roomId
  return a.roomId === roomFilter
}
