// Fleet reports: each report turns data the Fleet module already serves into one table (columns, rows, optional totals row), and any table
// becomes a CSV. Pure functions; ReportsTab fetches and draws.
import { daysToDate, expiryWords, bandText, scoreText } from "./driverScores.logic"
import { deviationText, fleetTotals, periodRange, type FuelRow } from "./fuelAnalytics.logic"
import { inRange } from "./vehicleTabs.logic"
import type { ListVehicle } from "./fleetBoards.logic"

export type Kind = "text" | "number" | "decimal" | "money"
export interface Column { key: string; label: string; kind: Kind }
export type Cell = string | number | null
export interface ReportTable { columns: Column[]; rows: Record<string, Cell>[]; totals?: Record<string, Cell> }

export type ReportId = "cost-per-km" | "fuel" | "maintenance" | "compliance" | "service-due" | "driver-scores"
export type ReportCategory = "operational" | "compliance" | "financial" | "safety"
export const CATEGORIES: { id: ReportCategory; label: string }[] = [{ id: "operational", label: "Operational" }, { id: "compliance", label: "Compliance" }, { id: "financial", label: "Financial" }, { id: "safety", label: "Safety" }]
export const REPORTS: { id: ReportId; title: string; description: string; period: boolean; category: ReportCategory }[] = [
  { id: "cost-per-km", title: "Cost per km", description: "Service and fuel cost over distance driven, for every vehicle, whole life.", period: false, category: "financial" },
  { id: "fuel", title: "Fuel efficiency", description: "Litres, spend and consumption per vehicle for the period.", period: true, category: "operational" },
  { id: "maintenance", title: "Maintenance cost", description: "Completed work orders in the period, quoted against actual.", period: true, category: "financial" },
  { id: "compliance", title: "Compliance expiry", description: "Licence disc, roadworthy and insurance dates, most urgent first.", period: false, category: "compliance" },
  { id: "service-due", title: "Service due", description: "Distance to the next service, most overdue first.", period: false, category: "operational" },
  { id: "driver-scores", title: "Driver scores", description: "Rated drivers first; thin data is shown as not rated.", period: false, category: "safety" },
]
/** The reports on show: all of them, or one category's. */
export const reportsIn = (category: ReportCategory | "all") => (category === "all" ? REPORTS : REPORTS.filter(r => r.category === category))
export type PeriodKind = "month" | "30" | "90" | "year"
export const PERIOD_LABEL: Record<PeriodKind, string> = { month: "This month", "30": "Last 30 days", "90": "Last 90 days", year: "This year" }
export function reportPeriod(kind: PeriodKind, today = new Date()): { from: string; to: string } {
  if (kind !== "year") return periodRange(kind, today)
  const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
  return { from: iso(new Date(today.getFullYear(), 0, 1)), to: iso(today) }
}

const name = (v: { make?: string | null; model?: string | null }) => [v.make, v.model].filter(Boolean).join(" ")
const nullsLast = (a: number | null, b: number | null, dir: 1 | -1) => (a == null && b == null ? 0 : a == null ? 1 : b == null ? -1 : dir * (a - b))
const round = (n: number, dp = 2) => Math.round(n * 10 ** dp) / 10 ** dp

// ── the six reports ─────────────────────────────────────────────────────────
export interface CostRow { registration: string; make: string | null; model: string | null; totalServiceCost: number; totalFuelCost: number; totalCost: number; totalKm: number; costPerKm: number | null }
export function costPerKmReport(rows: CostRow[]): ReportTable {
  const km = rows.reduce((s, r) => s + r.totalKm, 0), cost = rows.reduce((s, r) => s + Number(r.totalCost), 0)
  return {
    columns: [{ key: "vehicle", label: "Vehicle", kind: "text" }, { key: "model", label: "Make and model", kind: "text" }, { key: "service", label: "Service cost", kind: "money" }, { key: "fuel", label: "Fuel cost", kind: "money" },
      { key: "total", label: "Total cost", kind: "money" }, { key: "km", label: "Km driven", kind: "number" }, { key: "perKm", label: "Cost per km", kind: "money" }],
    rows: [...rows].sort((a, b) => nullsLast(a.costPerKm, b.costPerKm, -1) || a.registration.localeCompare(b.registration)).map(r => ({ vehicle: r.registration, model: name(r), service: round(Number(r.totalServiceCost)), fuel: round(Number(r.totalFuelCost)), total: round(Number(r.totalCost)), km: r.totalKm, perKm: r.costPerKm == null ? null : round(Number(r.costPerKm)) })),
    totals: { vehicle: "Fleet", model: null, service: round(rows.reduce((s, r) => s + Number(r.totalServiceCost), 0)), fuel: round(rows.reduce((s, r) => s + Number(r.totalFuelCost), 0)), total: round(cost), km, perKm: km > 0 ? round(cost / km) : null },
  }
}
export function fuelReport(rows: FuelRow[]): ReportTable {
  const t = fleetTotals(rows), fills = rows.reduce((s, r) => s + r.summary.fillups, 0)
  return {
    columns: [{ key: "vehicle", label: "Vehicle", kind: "text" }, { key: "fills", label: "Fill-ups", kind: "number" }, { key: "litres", label: "Litres", kind: "decimal" }, { key: "spend", label: "Spend", kind: "money" }, { key: "km", label: "Measured km", kind: "number" },
      { key: "l100", label: "L/100 km", kind: "decimal" }, { key: "perKm", label: "Cost per km", kind: "money" }, { key: "usual", label: "Against its usual", kind: "text" }, { key: "flags", label: "Flags", kind: "number" }],
    rows: [...rows].sort((a, b) => nullsLast(a.summary.litresPer100Km, b.summary.litresPer100Km, -1) || a.registration.localeCompare(b.registration)).map(r => ({ vehicle: r.registration, fills: r.summary.fillups, litres: round(Number(r.summary.totalLitres), 1), spend: round(Number(r.summary.totalSpend)), km: r.summary.measuredKm,
      l100: r.summary.litresPer100Km == null ? null : round(r.summary.litresPer100Km, 1), perKm: r.summary.costPerKm == null ? null : round(Number(r.summary.costPerKm)), usual: deviationText(r.latestDeviationPercent), flags: r.summary.flaggedSegments + r.summary.indicators })),
    totals: { vehicle: "Fleet", fills, litres: round(t.litres, 1), spend: round(t.spend), km: t.km, l100: t.l100 == null ? null : round(t.l100, 1), perKm: null, usual: null, flags: t.flagged },
  }
}
export interface ReportOrder { referenceNumber: string; registration: string; type: string; title: string; status: string; completedAt: string | null; quotedAmount: number | null; actualTotal: number | null }
export function maintenanceReport(orders: ReportOrder[], from: string, to: string): ReportTable {
  const done = orders.filter(o => o.status === "COMPLETED" && inRange(o.completedAt, from, to)).sort((a, b) => (b.completedAt as string).localeCompare(a.completedAt as string))
  const actual = done.reduce((s, o) => s + Number(o.actualTotal ?? 0), 0), quoted = done.reduce((s, o) => s + Number(o.quotedAmount ?? 0), 0)
  return {
    columns: [{ key: "ref", label: "Work order", kind: "text" }, { key: "vehicle", label: "Vehicle", kind: "text" }, { key: "type", label: "Type", kind: "text" }, { key: "job", label: "Job", kind: "text" }, { key: "completed", label: "Completed", kind: "text" },
      { key: "quoted", label: "Quoted", kind: "money" }, { key: "actual", label: "Actual", kind: "money" }, { key: "variance", label: "Over / (under) quote", kind: "money" }],
    rows: done.map(o => ({ ref: o.referenceNumber, vehicle: o.registration, type: o.type.charAt(0) + o.type.slice(1).toLowerCase().replace(/_/g, " "), job: o.title, completed: (o.completedAt as string).slice(0, 10),
      quoted: o.quotedAmount == null ? null : round(Number(o.quotedAmount)), actual: o.actualTotal == null ? null : round(Number(o.actualTotal)), variance: o.quotedAmount == null || o.actualTotal == null ? null : round(Number(o.actualTotal) - Number(o.quotedAmount)) })),
    totals: { ref: `${done.length} completed`, vehicle: null, type: null, job: null, completed: null, quoted: round(quoted), actual: round(actual), variance: null },
  }
}
export function complianceReport(vehicles: ListVehicle[], today = new Date()): ReportTable {
  const cell = (iso: string | null) => (iso ? `${iso.slice(0, 10)} · ${expiryWords(iso, today)}` : "Not set")
  const worst = (v: ListVehicle) => Math.min(...[v.licenceDiscExpiry, v.roadworthyExpiry, v.insuranceExpiry].map(d => (d ? daysToDate(d, today) : 99999)))
  return {
    columns: [{ key: "vehicle", label: "Vehicle", kind: "text" }, { key: "model", label: "Make and model", kind: "text" }, { key: "licence", label: "Licence disc", kind: "text" }, { key: "roadworthy", label: "Roadworthy", kind: "text" }, { key: "insurance", label: "Insurance", kind: "text" }],
    rows: vehicles.filter(v => v.status !== "RETIRED").sort((a, b) => worst(a) - worst(b) || a.registration.localeCompare(b.registration)).map(v => ({ vehicle: v.registration, model: name(v), licence: cell(v.licenceDiscExpiry), roadworthy: cell(v.roadworthyExpiry), insurance: cell(v.insuranceExpiry) })),
  }
}
export function serviceDueReport(vehicles: ListVehicle[]): ReportTable {
  const due = vehicles.filter(v => v.status !== "RETIRED" && v.serviceIntervalKm && v.currentOdometer != null)
    .map(v => { const interval = v.serviceIntervalKm as number, odo = v.currentOdometer as number, last = v.lastServiceKm ?? 0; return { v, remaining: last + interval - odo, dueAt: last + interval } })
    .sort((a, b) => a.remaining - b.remaining || a.v.registration.localeCompare(b.v.registration))
  return {
    columns: [{ key: "vehicle", label: "Vehicle", kind: "text" }, { key: "model", label: "Make and model", kind: "text" }, { key: "odo", label: "Odometer km", kind: "number" }, { key: "last", label: "Last service km", kind: "number" }, { key: "dueAt", label: "Next service at km", kind: "number" }, { key: "remaining", label: "Km remaining", kind: "number" }, { key: "state", label: "State", kind: "text" }],
    rows: due.map(({ v, remaining, dueAt }) => ({ vehicle: v.registration, model: name(v), odo: v.currentOdometer, last: v.lastServiceKm ?? 0, dueAt, remaining, state: remaining < 0 ? "Overdue" : remaining <= 2000 ? "Due soon" : "OK" })),
  }
}
export interface ScoreLine { driverName: string; overall: number | null; band: string | null; coverage: number; reliable: boolean; tripCount: number }
export function driverScoresReport(scores: ScoreLine[]): ReportTable {
  const rank = (s: ScoreLine) => (s.overall == null ? 2 : s.reliable ? 0 : 1)
  return {
    columns: [{ key: "driver", label: "Driver", kind: "text" }, { key: "score", label: "Score", kind: "text" }, { key: "rating", label: "Rating", kind: "text" }, { key: "coverage", label: "Data coverage %", kind: "number" }, { key: "trips", label: "Trips", kind: "number" }],
    rows: [...scores].sort((a, b) => rank(a) - rank(b) || (b.overall ?? 0) - (a.overall ?? 0) || a.driverName.localeCompare(b.driverName)).map(s => ({ driver: s.driverName, score: scoreText(s.overall), rating: s.overall == null ? "No data" : bandText(s.band, s.reliable), coverage: Math.round(s.coverage), trips: s.tripCount })),
  }
}

// ── display and CSV ─────────────────────────────────────────────────────────
export function cellText(kind: Kind, v: Cell): string {
  if (v == null) return "—"
  if (typeof v === "string" || kind === "text") return String(v)
  if (kind === "money") return `R ${v.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
  return v.toLocaleString("en-ZA", { maximumFractionDigits: kind === "decimal" ? 1 : 0 })
}
/** One CSV field. Quotes, commas and line breaks are quoted; a text value that a spreadsheet would run as a formula (= + - @) is made inert. */
export function csvField(v: Cell): string {
  if (v == null) return ""
  const s = typeof v === "string" && /^[=+\-@\t\r]/.test(v) ? `'${v}` : String(v)
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}
export function toCsv(t: ReportTable): string {
  const line = (r: Record<string, Cell>) => t.columns.map(c => csvField(r[c.key] ?? null)).join(",")
  return [t.columns.map(c => csvField(c.label)).join(","), ...t.rows.map(line), ...(t.totals ? [line(t.totals)] : [])].join("\r\n") + "\r\n"
}
export const csvFileName = (id: ReportId, period: { from: string; to: string } | null, today = new Date()) =>
  `fleet-${id}-${period ? `${period.from}_to_${period.to}` : `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`}.csv`
