// Back-entering a measurement taken at an earlier visit (or elsewhere), so an older patient's chart is complete.
export const GROWTH_MEASURES = [
  { code: "WEIGHT", label: "Weight", unit: "kg" },
  { code: "HEIGHT", label: "Height / length", unit: "cm" },
  { code: "HEAD_CIRCUMFERENCE", label: "Head circumference", unit: "cm" },
] as const

/** Noon South African time on the chosen day, or now when that day is today (the server refuses a future time). */
export function takenAtFor(date: string, now: Date = new Date()): string {
  const noon = new Date(`${date}T12:00:00+02:00`)
  const today = new Date(now.getTime() + 2 * 3600_000).toISOString().slice(0, 10)
  return (date === today || noon.getTime() > now.getTime() ? now : noon).toISOString()
}

export function todayZA(now: Date = new Date()): string { return new Date(now.getTime() + 2 * 3600_000).toISOString().slice(0, 10) }

/** The reason a measurement cannot be saved yet, or null. No clinical limits are applied: only that it is a number above zero and a real, non-future date. */
export function entryProblem(value: string, date: string, now: Date = new Date()): string | null {
  const v = Number(value.replace(",", "."))
  if (!value.trim() || !Number.isFinite(v) || v <= 0) return "Enter the measurement as a number above zero."
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || Number.isNaN(new Date(`${date}T12:00:00+02:00`).getTime())) return "Choose the date it was measured."
  if (date > todayZA(now)) return "The date cannot be in the future."
  return null
}
