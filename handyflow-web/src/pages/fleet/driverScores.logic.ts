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

// ── Driver profile page ──────────────────────────────────────────────────────
/** Colour for one score component: green from 80, amber from 60, red below, grey when there is no data. */
export const componentTone = (score: number | null | undefined): "ok" | "warn" | "bad" | "none" => (score == null ? "none" : score >= 80 ? "ok" : score >= 60 ? "warn" : "bad")
export const initials = (first: string, last: string) => `${first.trim()[0] ?? ""}${last.trim()[0] ?? ""}`.toUpperCase()
const MONTH = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
const day = (iso: string) => { const [y, m, d] = iso.slice(0, 10).split("-").map(Number); return { y, m: m - 1, d } }
/** "22 Jul – 20 Oct 2026" (the year once when both ends share it). */
export function periodText(from: string, to: string): string {
  const a = day(from), b = day(to)
  return a.y === b.y ? `${a.d} ${MONTH[a.m]} – ${b.d} ${MONTH[b.m]} ${b.y}` : `${a.d} ${MONTH[a.m]} ${a.y} – ${b.d} ${MONTH[b.m]} ${b.y}`
}
/** The vehicles currently assigned to a driver. */
export const driverVehicles = <V extends { assignedDriverId?: string | null }>(vehicles: V[], driverId: string): V[] => vehicles.filter(v => v.assignedDriverId === driverId)
/** Share of the ring to fill, 0 to 1; no score fills nothing. */
export const ringFraction = (overall: number | null | undefined) => (overall == null ? 0 : Math.max(0, Math.min(1, overall / 100)))

// ── The driver register: searching, filtering by documents, and paging ───────
export type DocFilter = "ALL" | "EXPIRED" | "EXPIRING"
export const DOC_FILTER_LABEL: Record<DocFilter, string> = { ALL: "Any documents", EXPIRED: "Expired documents", EXPIRING: "Expiring in 30 days" }
interface Searchable { firstName: string; lastName: string; phone: string | null; email: string | null; idNumber: string | null; licenseNumber: string | null }
interface Documented { licenseExpiry: string | null; prdpRequired: boolean; prdpExpiry: string | null }
/** The worst state of a driver's documents, by the same rule as the counts above the list. */
export function documentState(d: Documented, today = new Date()): "EXPIRED" | "EXPIRING" | "OK" {
  const states = [expiryState(d.licenseExpiry, today), ...(d.prdpRequired ? [expiryState(d.prdpExpiry, today)] : [])]
  return states.includes("EXPIRED") ? "EXPIRED" : states.includes("EXPIRING") ? "EXPIRING" : "OK"
}
export interface DriverView { search: string; status: string; docs: DocFilter }
/** Every word of the search must appear in the name, phone, email, ID or licence number. */
export function filterDrivers<T extends Searchable & Documented & { status: string }>(list: T[], v: DriverView, today = new Date()): T[] {
  const words = v.search.toLowerCase().split(/\s+/).filter(Boolean)
  return list.filter(d => {
    if (v.status !== "ALL" && d.status !== v.status) return false
    if (v.docs !== "ALL" && documentState(d, today) !== v.docs) return false
    const hay = [d.firstName, d.lastName, d.phone, d.email, d.idNumber, d.licenseNumber].filter(Boolean).join(" ").toLowerCase()
    return words.every(w => hay.includes(w))
  })
}
export const DRIVER_PAGE_SIZE = 25
export interface Paged<T> { rows: T[]; page: number; pages: number; from: number; to: number; total: number }
/** One page of a list. The page number is clamped, so a page that no longer exists (after a filter) shows the last real one. */
export function paginate<T>(items: T[], page: number, size = DRIVER_PAGE_SIZE): Paged<T> {
  const pages = Math.max(1, Math.ceil(items.length / size)), at = Math.max(0, Math.min(page, pages - 1)), start = at * size
  return { rows: items.slice(start, start + size), page: at, pages, from: items.length === 0 ? 0 : start + 1, to: Math.min(items.length, start + size), total: items.length }
}
