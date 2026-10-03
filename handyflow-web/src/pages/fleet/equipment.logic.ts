// src/pages/fleet/equipment.logic.ts
//
// Parsing for the Fleet equipment screen: the engine-hours meter and the operating rate per hour. A blank clears the value; anything else must be
// a number of zero or more. Same rule as the server, which refuses negatives.
export type Parsed = { ok: true; value: number | null } | { ok: false; message: string }

export function parseNonNegative(text: string, what: string): Parsed {
  const t = text.trim().replace(",", ".")
  if (t === "") return { ok: true, value: null }
  const n = Number(t)
  if (!Number.isFinite(n) || n < 0) return { ok: false, message: `${what} must be a number, zero or more.` }
  return { ok: true, value: n }
}

/** The first problem with the two inputs, or null. */
export function equipmentProblem(hours: string, rate: string): string | null {
  const h = parseNonNegative(hours, "Engine hours")
  if (!h.ok) return h.message
  const r = parseNonNegative(rate, "The operating rate")
  return r.ok ? null : r.message
}

/** True when the inputs differ from what is saved. Numbers are compared as numbers, so "85.50" equals a saved 85.5. */
export function equipmentChanged(hours: string, rate: string, saved: { engineHours: number | null; operatingRatePerHour: number | null }): boolean {
  const h = parseNonNegative(hours, ""), r = parseNonNegative(rate, "")
  if (!h.ok || !r.ok) return true
  return h.value !== saved.engineHours || r.value !== saved.operatingRatePerHour
}
