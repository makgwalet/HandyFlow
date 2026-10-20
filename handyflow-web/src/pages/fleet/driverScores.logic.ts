// Driver 360: score bands, the settings form (weights must total 100, thresholds must descend) and cost/summary wording.
export const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
export const BAND_LABEL: Record<string, string> = { EXCELLENT: "Excellent", GOOD: "Good", FAIR: "Fair", POOR: "Poor" }
export const bandTone = (band: string | null | undefined): "ok" | "warn" | "bad" | "none" =>
  band === "EXCELLENT" || band === "GOOD" ? "ok" : band === "FAIR" ? "warn" : band === "POOR" ? "bad" : "none"

/** A score built on too little data is not a verdict: show "Not rated" instead of a band. */
export const bandText = (band: string | null | undefined, reliable: boolean) => (!band ? "—" : reliable ? BAND_LABEL[band] : "Not rated")
export const bandToneFor = (band: string | null | undefined, reliable: boolean) => (reliable ? bandTone(band) : "none")

export const scoreText = (overall: number | null | undefined) => (overall == null ? "No data" : `${overall.toFixed(1)}%`)

export interface Settings {
  weightSafety: number; weightInspection: number; weightAccidents: number; weightFuel: number; weightTrips: number; weightCompliance: number
  lookbackDays: number; excellentThreshold: number; goodThreshold: number; fairThreshold: number
}
export type SettingsForm = Record<keyof Settings, string>
export const WEIGHT_FIELDS: { key: keyof Settings; label: string }[] = [
  { key: "weightSafety", label: "Safety" }, { key: "weightInspection", label: "Inspection compliance" }, { key: "weightAccidents", label: "Accidents" },
  { key: "weightFuel", label: "Fuel efficiency" }, { key: "weightTrips", label: "Trip compliance" }, { key: "weightCompliance", label: "Licence and PrDP compliance" },
]
export const toForm = (s: Settings): SettingsForm => Object.fromEntries(Object.entries(s).map(([k, v]) => [k, String(v)])) as SettingsForm
const n = (v: string) => Number(v)
export const weightTotal = (f: SettingsForm) => WEIGHT_FIELDS.reduce((t, w) => t + (n(f[w.key]) || 0), 0)

export function settingsProblem(f: SettingsForm): string | null {
  for (const w of WEIGHT_FIELDS) { const v = n(f[w.key]); if (!Number.isInteger(v) || v < 0 || v > 100) return `${w.label} must be a whole number from 0 to 100.` }
  const total = weightTotal(f)
  if (total !== 100) return `The weights add up to ${total}; they must add up to 100.`
  const days = n(f.lookbackDays)
  if (!Number.isInteger(days) || days < 7 || days > 730) return "Look back between 7 and 730 days."
  const [e, g, fa] = [n(f.excellentThreshold), n(f.goodThreshold), n(f.fairThreshold)]
  if (![e, g, fa].every(v => Number.isInteger(v) && v >= 1 && v <= 100)) return "Band thresholds must be whole numbers from 1 to 100."
  if (!(e > g && g > fa)) return "Thresholds must go down: excellent, then good, then fair."
  return null
}
export const settingsBody = (f: SettingsForm): Settings => Object.fromEntries(Object.entries(f).map(([k, v]) => [k, Number(v)])) as unknown as Settings

/** A low-coverage score is only a hint, and the screen says so. */
export const coverageNote = (reliable: boolean, coverage: number) => (reliable ? null : `Indicative only: only ${Math.round(coverage)}% of the score had data.`)

export type DriverSort = "name" | "score-desc" | "score-asc"
export const SORT_LABEL: Record<DriverSort, string> = { name: "Name", "score-desc": "Best score first", "score-asc": "Lowest score first" }
interface Scored { overall: number | null; reliable: boolean }
const group = (s: Scored | undefined) => (!s || s.overall == null ? 2 : s.reliable ? 0 : 1)
/** Rated drivers first, then indicative scores, then drivers with no data; ties by name. */
export function sortDrivers<T extends { id: string; firstName: string; lastName: string }>(list: T[], sort: DriverSort, scoreOf: Map<string, Scored>): T[] {
  const name = (d: T) => `${d.lastName} ${d.firstName}`.toLowerCase()
  const dir = sort === "score-asc" ? 1 : -1
  return [...list].sort((a, b) => {
    if (sort !== "name") {
      const sa = scoreOf.get(a.id), sb = scoreOf.get(b.id), ga = group(sa), gb = group(sb)
      if (ga !== gb) return ga - gb
      if (ga < 2 && sa!.overall !== sb!.overall) return dir * (sa!.overall! - sb!.overall!)
    }
    return name(a).localeCompare(name(b))
  })
}
/** 1-based rank among drivers with a reliable score, in the order given. */
export function rankMap(sorted: { id: string }[], scoreOf: Map<string, Scored>): Map<string, number> {
  const m = new Map<string, number>(); let n = 0
  for (const d of sorted) { const s = scoreOf.get(d.id); if (s && s.overall != null && s.reliable) m.set(d.id, ++n) }
  return m
}

// ── Document expiry, by calendar day ─────────────────────────────────────────
/** Whole calendar days from today to an ISO date (yyyy-mm-dd); negative once it has passed, 0 on the day itself. One rule for every badge and count. */
export function daysToDate(iso: string, today = new Date()): number {
  const [y, m, d] = iso.slice(0, 10).split("-").map(Number)
  return Math.round((Date.UTC(y, m - 1, d) - Date.UTC(today.getFullYear(), today.getMonth(), today.getDate())) / 86400000)
}
export type ExpiryState = "NOT_SET" | "EXPIRED" | "EXPIRING" | "VALID"
/** Expired is strictly before today; expiring is today up to (not including) 30 days out, the server's window. */
export function expiryState(iso: string | null | undefined, today = new Date()): ExpiryState {
  if (!iso) return "NOT_SET"
  const days = daysToDate(iso, today)
  return days < 0 ? "EXPIRED" : days < 30 ? "EXPIRING" : "VALID"
}
export function expiryWords(iso: string | null | undefined, today = new Date()): string {
  if (!iso) return "Not set"
  const d = daysToDate(iso, today)
  return d < 0 ? `Expired ${-d} ${d === -1 ? "day" : "days"} ago` : d === 0 ? "Expires today" : `${d} ${d === 1 ? "day" : "days"} left`
}
interface Credentialed { status: string; licenseExpiry: string | null; prdpRequired: boolean; prdpExpiry: string | null }
/** Active drivers (the unit is drivers, not documents): any expired document, else any expiring one. A PrDP only counts where it is required. */
export function driverDocumentCounts(drivers: Credentialed[], today = new Date()): { expired: number; expiring: number } {
  let expired = 0, expiring = 0
  for (const d of drivers.filter(x => x.status === "ACTIVE")) {
    const states = [expiryState(d.licenseExpiry, today), ...(d.prdpRequired ? [expiryState(d.prdpExpiry, today)] : [])]
    if (states.includes("EXPIRED")) expired++; else if (states.includes("EXPIRING")) expiring++
  }
  return { expired, expiring }
}
