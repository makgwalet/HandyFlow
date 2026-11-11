// Pure helpers for dock appointments. The server checks everything again (overlaps, statuses); these catch the common slips before a round trip.

/** The start and end (ISO instants) of a local calendar day given as yyyy-mm-dd. */
export function dayBounds(day: string): { from: string; to: string } {
  const [y, m, d] = day.split("-").map(Number)
  const from = new Date(y, m - 1, d, 0, 0, 0, 0)
  const to = new Date(y, m - 1, d + 1, 0, 0, 0, 0)
  return { from: from.toISOString(), to: to.toISOString() }
}

/** A value from a datetime-local input (local time, no zone) as an ISO instant; null when blank or not a date. */
export function toInstant(local: string): string | null {
  if (!local) return null
  const t = new Date(local)
  return Number.isNaN(t.getTime()) ? null : t.toISOString()
}

/** Null when the slot is fine, else what is wrong. Mirrors the server: 5 minutes to 12 hours, ending after it starts. */
export function slotProblem(start: string, end: string): string | null {
  const s = toInstant(start), e = toInstant(end)
  if (!s || !e) return "Enter when the slot starts and ends."
  const mins = (Date.parse(e) - Date.parse(s)) / 60000
  if (mins <= 0) return "The slot must end after it starts."
  if (mins < 5) return "A slot is at least 5 minutes."
  if (mins > 720) return "A slot is at most 12 hours."
  return null
}

export type DockStatus = "SCHEDULED" | "ARRIVED" | "AT_DOCK" | "COMPLETED" | "CANCELLED" | "NO_SHOW"

export const DOCK_STATUS: Record<DockStatus, { bg: string; fg: string; label: string }> = {
  SCHEDULED: { bg: "var(--hf-info-soft)", fg: "var(--hf-info-text)", label: "Scheduled" },
  ARRIVED: { bg: "var(--hf-warning-soft-strong)", fg: "var(--hf-warning-text-deep)", label: "Arrived" },
  AT_DOCK: { bg: "var(--hf-warning-soft-strong)", fg: "var(--hf-warning-text-deep)", label: "At dock" },
  COMPLETED: { bg: "var(--hf-success-soft)", fg: "var(--hf-success-text-strong)", label: "Completed" },
  CANCELLED: { bg: "var(--hf-surface-muted)", fg: "var(--hf-text-muted)", label: "Cancelled" },
  NO_SHOW: { bg: "var(--hf-danger-soft)", fg: "var(--hf-danger-text)", label: "No-show" },
}

/** The next step for an appointment, or null when it is finished. */
export function nextStep(status: DockStatus): { path: string; label: string } | null {
  switch (status) {
    case "SCHEDULED": return { path: "arrived", label: "Vehicle arrived" }
    case "ARRIVED": return { path: "at-dock", label: "On the door" }
    case "AT_DOCK": return { path: "complete", label: "Finished" }
    default: return null
  }
}

export function lateLabel(minutes: number): string | null {
  if (minutes <= 0) return null
  return minutes >= 60 ? `${Math.floor(minutes / 60)} h ${minutes % 60} min late` : `${minutes} min late`
}

/** yyyy-mm-dd of a local date. */
export function localDay(d: Date = new Date()): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
}
