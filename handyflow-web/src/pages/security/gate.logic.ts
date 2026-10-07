// src/pages/security/gate.logic.ts
//
// Pure rules for the gate dashboard: how long someone has been on site, entry-type wording, filters and grouping.
// No React, no network. Whether someone has overstayed is decided by the server's overstay scheduler (status OVERSTAYED);
// this only shows it.
import type { Tone } from "./guard360.logic"

export interface OnSiteRow {
  id: string; siteId: string; siteName: string | null; accessPointName: string | null; entryType: string; personName: string
  company: string | null; hostName: string | null; vehicleRegistration: string | null; loggedInAt: string; status: string
}
export interface GateDashboard {
  counts: { onSiteNow: number; overstayed: number; enteredToday: number; departedToday: number; onSiteByType: Record<string, number>; vehiclesOnSite: number }
  onSite: OnSiteRow[]; onSiteTruncated: boolean
  bySite: { siteId: string; siteName: string; onSite: number; enteredToday: number }[]
  byGate: GateRow[]
  todayStartedAt: string
}
export interface GateRow { accessPointId: string; accessPointName: string; siteId: string | null; siteName: string | null; onSite: number; overstayed: number; enteredToday: number; departedToday: number }

export const TYPE_LABEL: Record<string, string> = { VISITOR: "Visitor", CONTRACTOR: "Contractor", DELIVERY: "Delivery", STAFF_VEHICLE: "Staff vehicle", OTHER: "Other" }
export const typeLabel = (t: string) => TYPE_LABEL[t] ?? t.charAt(0) + t.slice(1).toLowerCase().replace(/_/g, " ")
export const STATUS_LABEL: Record<string, string> = { ON_SITE: "On site", OVERSTAYED: "Overstayed", DEPARTED: "Departed" }
export const STATUS_TONE: Record<string, Tone> = { ON_SITE: "ok", OVERSTAYED: "bad", DEPARTED: "neutral" }

/** "45 min", "3 h 05 min", "2 d 4 h". Never negative: a clock that is slightly behind reads as "just now". */
export function onSiteFor(loggedInAt: string, now: Date = new Date()): string {
  const mins = Math.floor((now.getTime() - new Date(loggedInAt).getTime()) / 60_000)
  if (!Number.isFinite(mins) || mins < 1) return "just now"
  if (mins < 60) return `${mins} min`
  const h = Math.floor(mins / 60), m = mins % 60
  if (h < 24) return `${h} h ${String(m).padStart(2, "0")} min`
  return `${Math.floor(h / 24)} d ${h % 24} h`
}

/** A registration number was recorded at the gate. */
export const hasVehicle = (r: { vehicleRegistration: string | null }) => !!r.vehicleRegistration && r.vehicleRegistration.trim() !== ""

export function filterOnSite(rows: OnSiteRow[], f: { type: string; search: string; overstayedOnly: boolean; vehiclesOnly?: boolean }): OnSiteRow[] {
  const q = f.search.trim().toLowerCase()
  return rows.filter(r => (!f.type || r.entryType === f.type) && (!f.overstayedOnly || r.status === "OVERSTAYED") && (!f.vehiclesOnly || hasVehicle(r))
    && (!q || [r.personName, r.company, r.hostName, r.vehicleRegistration, r.siteName].some(v => (v ?? "").toLowerCase().includes(q))))
}

/** The entry types that appear, for the filter, in the order of the standard list and then any others. */
export function typesPresent(counts: Record<string, number>): string[] {
  const known = Object.keys(TYPE_LABEL).filter(t => counts[t])
  return [...known, ...Object.keys(counts).filter(t => !(t in TYPE_LABEL) && counts[t])]
}

/** Entries are oldest first from the server. */
export const longestOnSite = (rows: OnSiteRow[]): OnSiteRow | null => rows.length ? rows[0] : null

/** Gates that need a look first: overstays, then the most people on site. Quiet gates (nobody on site, nothing today) go last. */
export function sortGates(gates: GateRow[]): GateRow[] {
  const busy = (g: GateRow) => g.onSite + g.enteredToday + g.departedToday
  return [...gates].sort((a, b) => b.overstayed - a.overstayed || b.onSite - a.onSite || busy(b) - busy(a)
    || (a.siteName ?? "").localeCompare(b.siteName ?? "") || a.accessPointName.localeCompare(b.accessPointName))
}
