// src/pages/events/eventActions.ts
//
// Which lifecycle actions an event offers in each status, and the checks the forms run before they call the server. These mirror the rules in the
// Event entity (publish only from DRAFT, edit and cancel until COMPLETED/CANCELLED, postpone only before it goes live) so a button is only shown when the
// server would accept it; the server stays the authority and its message is shown when it disagrees.

export type EventStatus = "DRAFT" | "PUBLISHED" | "SOLD_OUT" | "LIVE" | "COMPLETED" | "CANCELLED"

const CLOSED: string[] = ["COMPLETED", "CANCELLED"]

export const canEdit = (status: string) => !CLOSED.includes(status)
export const canCancel = (status: string) => !CLOSED.includes(status)
export const canPostpone = (status: string) => ["DRAFT", "PUBLISHED", "SOLD_OUT"].includes(status)
/** Copying never changes the source, so it works from any status. */
export const canDuplicate = (_status: string) => true
export const canPublish = (status: string) => status === "DRAFT"
export const canGoLive = (status: string) => status === "PUBLISHED" || status === "SOLD_OUT"
export const canComplete = (status: string) => status === "LIVE"

/** A datetime-local input wants "YYYY-MM-DDTHH:mm"; the API sends "YYYY-MM-DDTHH:mm:ss" (or with fractions). */
export function toInputValue(iso: string | null | undefined): string {
  return iso ? iso.slice(0, 16) : ""
}

/** The API wants a full local date-time; an input value has no seconds. */
export function toApiValue(input: string): string | null {
  if (!input) return null
  return input.length === 16 ? `${input}:00` : input
}

export interface EventForm {
  title: string; description: string; eventType: string; venueName: string; venueAddress: string; venueCapacity: string
  startDatetime: string; endDatetime: string; isFree: boolean; isPrivate: boolean; registrationDeadline: string; notes: string
}

export function formFromEvent(e: any): EventForm {
  return {
    title: e.title ?? "", description: e.description ?? "", eventType: e.eventType ?? "GENERAL", venueName: e.venueName ?? "", venueAddress: e.venueAddress ?? "",
    venueCapacity: e.venueCapacity == null ? "" : String(e.venueCapacity),
    startDatetime: toInputValue(e.startDatetime), endDatetime: toInputValue(e.endDatetime), isFree: !!e.isFree, isPrivate: !!e.isPrivate,
    registrationDeadline: toInputValue(e.registrationDeadline), notes: e.notes ?? "",
  }
}

/** The first thing wrong with an event form, in words for the person filling it in, or null when it is fine to send. */
export function eventFormProblem(f: EventForm): string | null {
  if (!f.title.trim()) return "Give the event a title."
  if (f.title.trim().length > 300) return "The title can be at most 300 characters."
  if (!f.startDatetime || !f.endDatetime) return "Set when the event starts and ends."
  if (f.endDatetime <= f.startDatetime) return "The event must end after it starts."
  if (f.registrationDeadline && f.registrationDeadline > f.startDatetime) return "Registration must close before the event starts."
  if (f.venueCapacity.trim() !== "") {
    const n = Number(f.venueCapacity)
    if (!Number.isInteger(n) || n <= 0) return "Venue capacity must be a whole number above zero, or left empty for no limit."
  }
  return null
}

/** The body for POST/PUT: empty optional fields go as null, the capacity as a number, dates with seconds. */
export function eventBody(f: EventForm) {
  return {
    title: f.title.trim(), description: f.description.trim() || null, eventType: f.eventType,
    venueName: f.venueName.trim() || null, venueAddress: f.venueAddress.trim() || null,
    venueCapacity: f.venueCapacity.trim() === "" ? null : Number(f.venueCapacity),
    startDatetime: toApiValue(f.startDatetime), endDatetime: toApiValue(f.endDatetime),
    isFree: f.isFree, isPrivate: f.isPrivate, registrationDeadline: toApiValue(f.registrationDeadline), notes: f.notes.trim() || null,
  }
}

export function postponeProblem(start: string, end: string, reason: string, current: { start: string; end: string }): string | null {
  if (!start || !end) return "Set the new start and end."
  if (end <= start) return "The event must end after it starts."
  if (start === current.start && end === current.end) return "The new dates are the same as the current dates."
  if (reason.trim().length < 5) return "Say why the event is moving (at least 5 characters)."
  return null
}

/** Keeps the length of the event when someone moves the start: the end follows by the same amount. */
export function shiftEnd(oldStart: string, oldEnd: string, newStart: string): string {
  if (!oldStart || !oldEnd || !newStart) return oldEnd
  const delta = new Date(oldEnd).getTime() - new Date(oldStart).getTime()
  const d = new Date(new Date(newStart).getTime() + delta)
  if (Number.isNaN(d.getTime())) return oldEnd
  const p = (n: number) => String(n).padStart(2, "0")
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

/** Audit actions are SHOUTING_SNAKE; show them as words. */
export function actionLabel(action: string): string {
  const s = action.toLowerCase().replace(/_/g, " ")
  return s.charAt(0).toUpperCase() + s.slice(1)
}
