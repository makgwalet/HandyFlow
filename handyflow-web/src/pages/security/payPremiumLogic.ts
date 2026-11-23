// Pure rules for the Sunday and public-holiday pay panel. The server checks everything again; these only save a round trip and word the answers.

export const MIN_MULTIPLIER = 1.5
export const MAX_MULTIPLIER = 4

export interface PremiumSettings { enabled: boolean; sundayMultiplier: number; holidayMultiplier: number }
export type HolidaySource = "NATIONAL" | "ADDED" | "REMOVED"
export interface HolidayRow { date: string; name: string; source: HolidaySource }

export const PREMIUM_NOTE =
  "When this is on, hours worked on a Sunday are paid at the Sunday rate and hours on a public holiday at the holiday rate. " +
  "A holiday that falls on a Sunday is paid at the holiday rate only. Days run from midnight to midnight in your company's time zone. " +
  "It only applies to periods approved after you switch it on."

/** "1.5", "1,5" and " 2 " are all fine. Returns null for anything that is not a number with at most two decimals. */
export function parseMultiplier(text: string): number | null {
  const t = text.trim().replace(",", ".")
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return null
  return Number(t)
}

export function multiplierError(label: string, text: string): string | null {
  const v = parseMultiplier(text)
  if (v === null) return `${label}: enter a number such as 1.5 or 2 (at most two decimals).`
  if (v < MIN_MULTIPLIER || v > MAX_MULTIPLIER) return `${label}: must be between 1.50 and 4.00. It cannot be below the overtime rate.`
  return null
}

export function multiplierValid(text: string): boolean {
  const v = parseMultiplier(text)
  return v !== null && v >= MIN_MULTIPLIER && v <= MAX_MULTIPLIER
}

export function settingsError(sundayText: string, holidayText: string): string | null {
  return multiplierError("Sunday rate", sundayText) ?? multiplierError("Public holiday rate", holidayText)
}

export function toSaveRequest(enabled: boolean, sundayText: string, holidayText: string) {
  return { enabled, sundayMultiplier: parseMultiplier(sundayText), holidayMultiplier: parseMultiplier(holidayText) }
}

export function multiplierText(v: number): string { return v.toFixed(2) }

/** What the multiplier means in money, so a typing slip such as 15 instead of 1.5 is obvious. Rounded half up to the cent, as the server does. */
export function rateExample(baseCents: number, multiplier: number): string {
  const cents = Math.floor(baseCents * multiplier + 0.5 + 1e-9)
  return `R ${(baseCents / 100).toFixed(2)} an hour becomes R ${(cents / 100).toFixed(2)}`
}

export function sourceLabel(s: HolidaySource): string {
  return s === "ADDED" ? "Added by you" : s === "REMOVED" ? "Taken off by you" : "National"
}

/** The button a row gets: a national day can be taken off, a day taken off can be put back, a day you added can be deleted. */
export function rowAction(s: HolidaySource): { kind: "REMOVE" | "RESTORE" | "CLEAR"; label: string } {
  return s === "REMOVED" ? { kind: "RESTORE", label: "Put back" } : s === "ADDED" ? { kind: "CLEAR", label: "Delete" } : { kind: "REMOVE", label: "Take off" }
}

/** "Fri 25 Dec 2026", read from the date text so the viewer's time zone cannot move it a day. */
export function holidayDate(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  if (!m) return iso
  const d = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])))
  return d.toLocaleDateString("en-ZA", { weekday: "short", day: "numeric", month: "short", year: "numeric", timeZone: "UTC" })
}

export function yearChoices(now: Date): number[] {
  const y = now.getFullYear()
  return [y - 1, y, y + 1, y + 2]
}

export function addDayError(date: string, name: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return "Pick a date."
  const n = name.trim()
  if (n.length === 0) return "Give the day a name."
  if (n.length > 100) return "The name can be up to 100 characters."
  return null
}

export const SAVE_FAILED = "Could not save the premium settings. Try again."
export const DAY_FAILED = "Could not change that day. Try again."

export function failureText(e: unknown, fallback: string): string {
  const m = (e as { response?: { data?: { message?: unknown } } })?.response?.data?.message
  return typeof m === "string" && m.trim() !== "" ? m : fallback
}

export const PREMIUM_TYPE_LABELS = { SUNDAY: "Sunday", PUBLIC_HOLIDAY: "Public holiday" } as const
