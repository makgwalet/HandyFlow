// src/pages/security/liveops.logic.ts
//
// Pure rules for Live Operations: how a guard's GPS state is described, the filters, the summary numbers and where to
// centre the map. No React, no network. The server decides the GPS state; this only presents it.
import type { Tone } from "./guard360.logic"

export interface LiveGuard {
  guardId: string; guardName: string; grade: string | null
  shiftId: string; shiftStart: string; shiftEnd: string; overrunning: boolean
  siteId: string; siteName: string | null
  latitude: number | null; longitude: number | null; recordedAt: string | null; gpsState: string
  lastScanAt: string | null; lastScanCheckpoint: string | null
}

export const GPS_LABEL: Record<string, string> = { LIVE: "Live", STALE: "Stale", NO_GPS: "No GPS" }
export const GPS_TONE: Record<string, Tone> = { LIVE: "ok", STALE: "warn", NO_GPS: "neutral" }
export const GPS_FILTERS = [{ value: "", label: "Any GPS state" }, { value: "LIVE", label: "Live" }, { value: "STALE", label: "Stale" }, { value: "NO_GPS", label: "No GPS" }]

export function filterGuards(list: LiveGuard[], f: { gps: string; search: string }): LiveGuard[] {
  const q = f.search.trim().toLowerCase()
  return list.filter(g => (!f.gps || g.gpsState === f.gps) && (!q || g.guardName.toLowerCase().includes(q) || (g.siteName ?? "").toLowerCase().includes(q)))
}

export function summarise(list: LiveGuard[]) {
  return {
    onDuty: list.length,
    live: list.filter(g => g.gpsState === "LIVE").length,
    stale: list.filter(g => g.gpsState === "STALE").length,
    noGps: list.filter(g => g.gpsState === "NO_GPS").length,
    overrunning: list.filter(g => g.overrunning).length,
  }
}

export interface Positioned extends LiveGuard { latitude: number; longitude: number }
export const withPosition = (list: LiveGuard[]): Positioned[] => list.filter((g): g is Positioned => g.latitude !== null && g.longitude !== null)

/** South Africa's rough centre when nobody has a position: a starting view with nothing plotted, not a made-up location. */
export const DEFAULT_CENTER: [number, number] = [-28.4793, 24.6727]
export function mapCenter(list: LiveGuard[]): [number, number] {
  const p = withPosition(list)
  if (p.length === 0) return DEFAULT_CENTER
  return [p.reduce((s, g) => s + g.latitude, 0) / p.length, p.reduce((s, g) => s + g.longitude, 0) / p.length]
}

/** "just now", "5 min ago", "2 h ago", "3 d ago". */
export function ago(iso: string | null, now: number = Date.now()): string {
  if (!iso) return "never"
  const mins = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60000))
  if (mins < 1) return "just now"
  if (mins < 60) return `${mins} min ago`
  if (mins < 1440) return `${Math.floor(mins / 60)} h ago`
  return `${Math.floor(mins / 1440)} d ago`
}
