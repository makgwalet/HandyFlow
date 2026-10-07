// src/pages/security/site.logic.ts
//
// Pure rules for the site detail page: address and contract wording, and how a checkpoint's scan history is described.
// No React, no network. The numbers come from the server's site overview.
import type { Tone } from "./guard360.logic"
import type { LiveGuard } from "./liveops.logic"

export interface SiteOverview {
  site: {
    id: string; name: string; address: Record<string, string> | null
    latitude: number | null; longitude: number | null
    contactName: string | null; contactPhone: string | null; active: boolean
    contractStatus: string; contractStart: string | null; contractEnd: string | null
    terminationReason: string | null; requireSignedQr: boolean
  }
  counts: { guardsOnSite: number; upcomingShifts7d: number; openIncidents: number; activePatrolRoutes: number; checkpoints: number }
  onSite: LiveGuard[]
  recentIncidents: { id: string; title: string; severity: string; status: string; reportedAt: string }[]
  checkpoints: { id: string; name: string; scans30d: number; lastScanAt: string | null }[]
  upcoming: { shiftId: string; guardId: string; guardName: string; startAt: string; endAt: string }[]
}

export const CONTRACT_LABEL: Record<string, string> = { ACTIVE: "Contract active", EXPIRING_SOON: "Contract expiring soon", EXPIRED: "Contract expired", TERMINATED: "Contract terminated" }
export const CONTRACT_TONE: Record<string, Tone> = { ACTIVE: "ok", EXPIRING_SOON: "warn", EXPIRED: "bad", TERMINATED: "neutral" }

/** Address parts that exist, joined in reading order. Nothing is invented when the address is empty. */
export function formatAddress(a: Record<string, string> | null | undefined): string {
  if (!a) return ""
  const order = ["street", "line1", "line2", "suburb", "city", "province", "postalCode"]
  const known = order.map(k => a[k]).filter(Boolean)
  const rest = Object.entries(a).filter(([k, v]) => !order.includes(k) && v).map(([, v]) => v)
  return [...known, ...rest].join(", ")
}

/** Whole days from `now` to the contract end (negative once past). Null when there is no end date. */
export function contractDaysLeft(end: string | null | undefined, now: Date = new Date()): number | null {
  if (!end) return null
  const e = new Date(`${end}T00:00:00`)
  if (Number.isNaN(e.getTime())) return null
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  return Math.round((e.getTime() - start.getTime()) / 86_400_000)
}

export function contractNote(status: string, end: string | null | undefined, now: Date = new Date()): string {
  if (status === "TERMINATED") return "Terminated"
  const d = contractDaysLeft(end, now)
  if (d === null) return "No end date on record"
  if (d < 0) return `Ended ${-d} day${d === -1 ? "" : "s"} ago`
  if (d === 0) return "Ends today"
  return `${d} day${d === 1 ? "" : "s"} left`
}

/** A checkpoint nobody has scanned in 30 days is worth a look: the code may be damaged, moved or skipped. */
export function checkpointState(c: { scans30d: number; lastScanAt: string | null }): { label: string; tone: Tone } {
  if (!c.lastScanAt) return { label: "Never scanned", tone: "warn" }
  if (c.scans30d === 0) return { label: "No scans in 30 days", tone: "warn" }
  return { label: `${c.scans30d} scan${c.scans30d === 1 ? "" : "s"} in 30 days`, tone: "ok" }
}

export const hasPosition = (s: { latitude: number | null; longitude: number | null }): s is { latitude: number; longitude: number } =>
  s.latitude !== null && s.longitude !== null && Number.isFinite(Number(s.latitude)) && Number.isFinite(Number(s.longitude))

export const unscannedCount = (cps: SiteOverview["checkpoints"]) => cps.filter(c => c.scans30d === 0).length
