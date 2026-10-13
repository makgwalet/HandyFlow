// The hours the Schedule week grid shows (patch 0202). The grid used to show 07:00 to 19:00 only and silently left out
// anything booked outside those hours, so a busy day could look empty. It now stretches to cover every appointment in view.

export const DEFAULT_START = 7
export const DEFAULT_END = 19

/** First and last-plus-one hour to draw, in the browser's local time, covering 07:00 to 19:00 and every appointment given. */
export function gridHours(appts: { scheduledAt: string; durationMinutes?: number | null }[]): { start: number; end: number; hours: number[] } {
  let start = DEFAULT_START, end = DEFAULT_END
  for (const a of appts) {
    const d = new Date(a.scheduledAt)
    if (Number.isNaN(d.getTime())) continue
    const from = d.getHours() + d.getMinutes() / 60
    const to = Math.min(24, from + Math.max(a.durationMinutes ?? 30, 1) / 60)
    start = Math.min(start, Math.floor(from))
    end = Math.max(end, Math.ceil(to))
  }
  return { start, end, hours: Array.from({ length: end - start }, (_, i) => start + i) }
}

/** Pixels from the top of the grid for a moment, given the grid's first hour. */
export function offsetFor(iso: string, start: number, hourHeight: number): number {
  const d = new Date(iso)
  return (d.getHours() + d.getMinutes() / 60 - start) * hourHeight
}
