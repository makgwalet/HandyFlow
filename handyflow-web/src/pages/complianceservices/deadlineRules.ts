// What the deadline generator asks of a person. The server enforces the same rules.

export const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]

/** Why the key dates cannot be saved yet, or null. */
export function keyDatesProblem(month: string, registeredOn: string, today: string): string | null {
  if (month !== "" && !(Number(month) >= 1 && Number(month) <= 12)) return "Pick a month from the list"
  if (registeredOn && registeredOn > today) return "The registration date cannot be in the future"
  return null
}

export type Skipped = { code: string; reason: string }
export type Result = { created: number; cancelled: number; unchanged: number; skipped: Skipped[] }

/** One plain sentence on what generating did. */
export function resultText(r: Result): string {
  const parts: string[] = []
  parts.push(r.created === 0 ? "No new dates" : `${r.created} new date${r.created === 1 ? "" : "s"}`)
  if (r.cancelled > 0) parts.push(`${r.cancelled} cancelled because the rule or applicability changed`)
  if (r.unchanged > 0) parts.push(`${r.unchanged} already there`)
  if (r.skipped.length > 0) parts.push(`${r.skipped.length} could not be worked out`)
  return parts.join(", ")
}

export const sourceText = (code: string | null, version: number | null) => code ? `From ${code}${version ? ` v${version}` : ""}` : "Added by hand"
