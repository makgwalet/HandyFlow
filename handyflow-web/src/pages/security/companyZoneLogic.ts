// Company time zone: wording and checks. The server validates again and has the last word; this only avoids pointless clicks.

export interface ZoneView { timeZone: string; abbreviation: string; now: string; today: string }
export interface ZoneEffect { from: string; to: string; offsetChangeMinutes: number; todayChangesNow: boolean; warning: string }

/** Zones people in southern Africa and their usual partners are most likely to need. Any other region name can still be typed. */
export const COMMON_ZONES = [
  "Africa/Johannesburg", "Africa/Windhoek", "Africa/Gaborone", "Africa/Maseru", "Africa/Mbabane", "Africa/Harare", "Africa/Maputo",
  "Africa/Lusaka", "Africa/Luanda", "Africa/Nairobi", "Africa/Lagos", "Africa/Cairo", "Indian/Mauritius", "Europe/London", "Asia/Dubai", "UTC",
]

const SHAPE = /^[A-Za-z][A-Za-z0-9_+-]*(\/[A-Za-z0-9_+-]+){0,2}$/

function knownToBrowser(id: string): boolean {
  try { new Intl.DateTimeFormat("en", { timeZone: id }); return true } catch { return false }
}

/** Why the typed zone cannot be used, or null. */
export function zoneProblem(raw: string, current: string | null): string | null {
  const id = raw.trim()
  if (id === "") return "Choose a time zone."
  if (id.length > 60 || !SHAPE.test(id) || !knownToBrowser(id)) return "That is not a known time zone. Use a region name such as Africa/Johannesburg."
  if (/^(Etc\/)?GMT[+-]/.test(id) || /^[+-]\d/.test(id)) return "Use a region name such as Africa/Windhoek, not an offset."
  if (current !== null && id === current) return "The company is already on that time zone."
  return null
}

export function validView(d: unknown): ZoneView | null {
  const v = d as Partial<ZoneView> | null | undefined
  if (!v || typeof v.timeZone !== "string" || typeof v.now !== "string" || typeof v.today !== "string") return null
  return { timeZone: v.timeZone, abbreviation: typeof v.abbreviation === "string" ? v.abbreviation : "", now: v.now, today: v.today }
}

export function validEffect(d: unknown): ZoneEffect | null {
  const e = d as Partial<ZoneEffect> | null | undefined
  if (!e || typeof e.to !== "string" || typeof e.from !== "string" || typeof e.warning !== "string" || typeof e.offsetChangeMinutes !== "number") return null
  return { from: e.from, to: e.to, offsetChangeMinutes: e.offsetChangeMinutes, todayChangesNow: e.todayChangesNow === true, warning: e.warning }
}

/** "11 hours forward", "2 hours back", "no change in offset right now". */
export function shiftText(minutes: number): string {
  if (minutes === 0) return "no change in the clock offset right now"
  const abs = Math.abs(minutes), h = Math.floor(abs / 60), m = abs % 60
  const amount = m === 0 ? `${h} ${h === 1 ? "hour" : "hours"}` : h === 0 ? `${m} minutes` : `${h} h ${m} min`
  return `${amount} ${minutes > 0 ? "forward" : "back"}`
}

export function errorText(e: unknown, fallback: string): string {
  const m = (e as { response?: { data?: { message?: unknown } } })?.response?.data?.message
  return typeof m === "string" && m.length > 0 && m.length < 300 ? m : fallback
}
