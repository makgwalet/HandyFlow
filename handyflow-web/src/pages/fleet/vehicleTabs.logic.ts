// Rules for the Location, Maintenance, Fuel & Usage and History tabs on a vehicle: track ranges, service due, trip and fuel wording,
// and the merged history timeline. Pure functions; the components only fetch and draw.
import { STATUS_LABEL as ACCIDENT_LABEL, blocksVehicle } from "./accidents.logic"
import { STATUS_LABEL as ORDER_LABEL } from "./workOrders.logic"
import { LIFECYCLE_LABEL } from "./lifecycle.logic"
import { hasFix } from "./liveMap.logic"

// ── Location ────────────────────────────────────────────────────────────────
export type TrackRange = "24h" | "3d" | "7d"
export const TRACK_RANGES: { id: TrackRange; label: string; hours: number }[] = [
  { id: "24h", label: "Last 24 hours", hours: 24 }, { id: "3d", label: "Last 3 days", hours: 72 }, { id: "7d", label: "Last 7 days", hours: 168 },
]
/** ISO instants for the server (it allows at most 7 days). */
export function trackWindow(range: TrackRange, now = new Date()): { from: string; to: string } {
  const hours = TRACK_RANGES.find(r => r.id === range)?.hours ?? 24
  return { from: new Date(now.getTime() - hours * 3600000).toISOString(), to: now.toISOString() }
}
export interface TrackPoint { recordedAt: string; latitude: number; longitude: number; speedKph: number | null }
export interface Track { totalPoints: number; returnedPoints: number; distanceKm: number; points: TrackPoint[] }
export const trackLine = (t: Track | undefined | null): [number, number][] => (t?.points ?? []).filter(hasFix).map(p => [p.latitude, p.longitude])
export const topSpeed = (t: Track | undefined | null): number | null => {
  const s = (t?.points ?? []).map(p => p.speedKph).filter((x): x is number => x != null)
  return s.length ? Math.max(...s) : null
}
/** "12.4 km by GPS, 214 fixes" — the distance is a straight-line estimate, never the odometer. */
export function trackSummary(t: Track | undefined | null): string {
  if (!t || t.totalPoints === 0) return "No movement recorded in this period."
  return `${t.distanceKm.toFixed(1)} km by GPS (an estimate, not the odometer) · ${t.totalPoints} fixes`
}

// ── Maintenance ─────────────────────────────────────────────────────────────
export interface ServiceInputs { currentOdometer?: number | null; lastServiceKm?: number | null; serviceIntervalKm?: number | null; latestNextServiceKm?: number | null }
export interface ServiceDue { dueKm: number | null; remainingKm: number | null; tone: "ok" | "warn" | "bad" | "none"; text: string }
/** The service the vehicle is due for: the date the last service booked, else last service + interval. Amber inside 1 000 km. */
export function serviceDue(i: ServiceInputs): ServiceDue {
  const odo = i.currentOdometer ?? null
  const dueKm = i.latestNextServiceKm ?? (i.lastServiceKm != null && i.serviceIntervalKm ? i.lastServiceKm + i.serviceIntervalKm : null)
  if (dueKm == null || odo == null) return { dueKm, remainingKm: null, tone: "none", text: "No service interval set." }
  const remainingKm = dueKm - odo, km = (n: number) => `${Math.abs(n).toLocaleString("en-ZA")} km`
  if (remainingKm < 0) return { dueKm, remainingKm, tone: "bad", text: `Overdue by ${km(remainingKm)} (was due at ${dueKm.toLocaleString("en-ZA")} km)` }
  return { dueKm, remainingKm, tone: remainingKm <= 1000 ? "warn" : "ok", text: `Due in ${km(remainingKm)} (at ${dueKm.toLocaleString("en-ZA")} km)` }
}
export const OPEN_ORDER_STATUSES = ["OPEN", "QUOTED", "APPROVED", "IN_PROGRESS", "ON_HOLD"]
export const isOpenOrder = (status: string) => OPEN_ORDER_STATUSES.includes(status)
/** Money spent on services in the last 12 months. */
export function serviceSpend(services: { serviceDate: string; cost: number | null }[], now = new Date()): number {
  const since = new Date(now); since.setFullYear(since.getFullYear() - 1)
  return services.filter(s => new Date(s.serviceDate) >= since).reduce((t, s) => t + Number(s.cost ?? 0), 0)
}

// ── Fuel & usage ────────────────────────────────────────────────────────────
export const SEGMENT_LABEL: Record<string, string> = { NORMAL: "Normal", HIGH_CONSUMPTION: "High", LOW_CONSUMPTION: "Low", NO_BASELINE: "No baseline", NOT_ASSESSED: "Not assessed", DATA_SUSPECT: "Data suspect" }
export const segmentTone = (s: string): "ok" | "warn" | "bad" | "none" => (s === "NORMAL" ? "ok" : s === "HIGH_CONSUMPTION" ? "bad" : s === "LOW_CONSUMPTION" || s === "DATA_SUSPECT" ? "warn" : "none")
export const INDICATOR_LABEL: Record<string, string> = { OVERFILL: "Fill above tank size", SAME_DAY_MULTIPLE: "Several fills in a day", DUPLICATE_RECEIPT: "Duplicate receipt", NO_DISTANCE: "Fill with no distance", ODOMETER_REGRESSION: "Odometer went backwards" }
export const indicatorTone = (sev: string): "warn" | "bad" | "none" => (sev === "ALERT" ? "bad" : sev === "WARNING" ? "warn" : "none")

export interface Trip { id: string; status: string; tripType: string; distanceKm: number | null; startAt: string | null; endAt: string | null; startLocation: string | null; endLocation: string | null; purpose: string | null; driverName: string | null }
/** Business and private km over completed trips — the SARS logbook split — and how many trips there were. */
export function tripTotals(trips: Trip[]): { count: number; businessKm: number; privateKm: number; totalKm: number; businessPercent: number | null } {
  const done = trips.filter(t => t.status === "COMPLETED")
  const km = (type: string) => done.filter(t => t.tripType === type).reduce((s, t) => s + (t.distanceKm ?? 0), 0)
  const businessKm = km("BUSINESS"), privateKm = km("PRIVATE"), totalKm = businessKm + privateKm
  return { count: done.length, businessKm, privateKm, totalKm, businessPercent: totalKm > 0 ? Math.round((businessKm / totalKm) * 100) : null }
}
export const inRange = (iso: string | null, from: string, to: string) => !!iso && iso.slice(0, 10) >= from && iso.slice(0, 10) <= to

// ── History ────────────────────────────────────────────────────────────────
export type HistoryKind = "LIFECYCLE" | "SERVICE" | "WORK_ORDER" | "ACCIDENT" | "INSPECTION"
export const HISTORY_KINDS: { id: HistoryKind; label: string }[] = [
  { id: "LIFECYCLE", label: "Lifecycle" }, { id: "SERVICE", label: "Services" }, { id: "WORK_ORDER", label: "Work orders" }, { id: "ACCIDENT", label: "Accidents" }, { id: "INSPECTION", label: "Inspections" },
]
export interface HistoryItem { key: string; at: string; kind: HistoryKind; title: string; detail: string; tone: "ok" | "warn" | "bad" | "none" }
export interface HistorySources {
  lifecycle?: { id: string; eventType: string; toStatus: string | null; occurredAt: string; reason: string | null }[]
  services?: { id: string; type: string; description: string | null; serviceDate: string; cost: number | null; supplier: string | null }[]
  orders?: { id: string; referenceNumber: string; title: string; status: string; createdAt: string; completedAt: string | null; actualTotal: number | null }[]
  accidents?: { id: string; referenceNumber: string; occurredAt: string; description: string; status: string; vehicleDrivable: boolean }[]
  inspections?: { id: string; inspectionType: string; status: string; verdict: string | null; failedCount: number; startedAt: string; completedAt: string | null }[]
}
const humanise = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, " ")
const money = (n: number | null | undefined) => (n == null ? "" : `R ${Number(n).toLocaleString("en-ZA")}`)
const dot = (...parts: (string | null | undefined | false)[]) => parts.filter(Boolean).join(" · ")
const EVENT_NAME: Record<string, string> = { STATUS_CHANGE: "Status change", THEFT_REPORTED: "Theft reported", RECOVERED: "Recovered", WRITE_OFF: "Written off", DISPOSAL: "Disposal" }

/** Everything that has happened to the vehicle, newest first. */
export function buildTimeline(s: HistorySources): HistoryItem[] {
  const items: HistoryItem[] = []
  for (const e of s.lifecycle ?? []) items.push({ key: `L${e.id}`, at: e.occurredAt, kind: "LIFECYCLE", title: EVENT_NAME[e.eventType] ?? humanise(e.eventType),
    detail: dot(e.toStatus && (LIFECYCLE_LABEL[e.toStatus] ?? humanise(e.toStatus)), e.reason), tone: e.eventType === "THEFT_REPORTED" || e.eventType === "WRITE_OFF" ? "bad" : e.eventType === "RECOVERED" ? "ok" : "none" })
  for (const x of s.services ?? []) items.push({ key: `S${x.id}`, at: x.serviceDate, kind: "SERVICE", title: `${humanise(x.type)} service`, detail: dot(x.description, x.supplier, money(x.cost)), tone: "none" })
  for (const o of s.orders ?? []) items.push({ key: `W${o.id}`, at: o.completedAt ?? o.createdAt, kind: "WORK_ORDER", title: `${o.referenceNumber} · ${o.title}`,
    detail: dot(ORDER_LABEL[o.status] ?? humanise(o.status), o.status === "COMPLETED" && money(o.actualTotal)), tone: o.status === "COMPLETED" ? "ok" : o.status === "CANCELLED" ? "none" : "warn" })
  for (const a of s.accidents ?? []) items.push({ key: `A${a.id}`, at: a.occurredAt, kind: "ACCIDENT", title: `Accident ${a.referenceNumber}`,
    detail: dot(ACCIDENT_LABEL[a.status] ?? humanise(a.status), a.description, blocksVehicle(a) && "cannot be driven"), tone: blocksVehicle(a) ? "bad" : a.status === "CLOSED" ? "none" : "warn" })
  for (const i of s.inspections ?? []) items.push({ key: `I${i.id}`, at: i.completedAt ?? i.startedAt, kind: "INSPECTION", title: `${humanise(i.inspectionType)} inspection`,
    detail: i.status === "COMPLETED" ? dot(i.verdict === "FAIL" ? "Failed" : "Passed", i.failedCount > 0 && `${i.failedCount} failed ${i.failedCount === 1 ? "item" : "items"}`) : humanise(i.status), tone: i.verdict === "FAIL" ? "bad" : i.verdict === "PASS" ? "ok" : "none" })
  return items.sort((a, b) => new Date(b.at).getTime() - new Date(a.at).getTime() || a.key.localeCompare(b.key))
}
export const filterTimeline = (items: HistoryItem[], kinds: HistoryKind[]): HistoryItem[] => (kinds.length ? items.filter(i => kinds.includes(i.kind)) : items)
export const kindCounts = (items: HistoryItem[]) => HISTORY_KINDS.map(k => ({ ...k, count: items.filter(i => i.kind === k.id).length }))
