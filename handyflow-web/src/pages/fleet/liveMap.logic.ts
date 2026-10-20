// Live map rules: status wording and colours, filtering, how stale a fix is, and map bounds.
export interface LivePosition { vehicleId: string; registration: string; make: string | null; model: string | null; latitude: number; longitude: number; speedKph: number | null; heading: number | null; ignitionOn: boolean | null; recordedAt: string; ageMinutes: number; status: string }
export const LIVE_STATUSES = ["MOVING", "IDLE", "STOPPED", "PARKED", "OFFLINE"] as const
export const LIVE_LABEL: Record<string, string> = { MOVING: "Moving", IDLE: "Idling", STOPPED: "Stopped", PARKED: "Parked", OFFLINE: "Offline" }
export const LIVE_COLOUR: Record<string, string> = { MOVING: "#16a34a", IDLE: "#d97706", STOPPED: "#dc2626", PARKED: "#2563eb", OFFLINE: "#6b7280" }

export function liveCounts(ps: { status: string }[]): { status: string; count: number }[] {
  return LIVE_STATUSES.map(status => ({ status, count: ps.filter(p => p.status === status).length }))
}
/** Positions to show for the chosen status filter, searched by registration / make / model; moving ones first. */
export function visiblePositions(ps: LivePosition[], status: string, search: string): LivePosition[] {
  const words = search.toLowerCase().split(/\s+/).filter(Boolean)
  const order: Record<string, number> = { MOVING: 0, IDLE: 1, STOPPED: 2, PARKED: 3, OFFLINE: 4 }
  return ps.filter(p => (status === "ALL" || p.status === status) && words.every(w => `${p.registration} ${p.make ?? ""} ${p.model ?? ""}`.toLowerCase().includes(w)))
    .sort((a, b) => (order[a.status] ?? 9) - (order[b.status] ?? 9) || a.registration.localeCompare(b.registration))
}
/** "Just now", "12 min ago", "3 h ago", "2 days ago". */
export function ageText(minutes: number): string {
  if (minutes < 1) return "Just now"
  if (minutes < 60) return `${Math.round(minutes)} min ago`
  if (minutes < 1440) return `${Math.floor(minutes / 60)} h ago`
  const d = Math.floor(minutes / 1440); return `${d} ${d === 1 ? "day" : "days"} ago`
}
/** "3 min ago", or "Last known 2 days ago" when the unit has gone quiet: an old fix is never presented as live. */
export const fixText = (p: { status: string; ageMinutes: number }): string => (p.status === "OFFLINE" ? `Last known ${ageText(p.ageMinutes)}` : ageText(p.ageMinutes))
export const speedText = (s: number | null) => (s == null ? "—" : `${Math.round(s)} km/h`)
export const hasFix = (p: { latitude: number; longitude: number }) => Number.isFinite(p.latitude) && Number.isFinite(p.longitude) && !(p.latitude === 0 && p.longitude === 0)

/** Where to centre when there is nothing to fit: roughly the middle of South Africa. */
export const DEFAULT_CENTRE: [number, number] = [-28.5, 25]

export const EVENT_LABEL: Record<string, string> = {
  SPEEDING: "Speeding", HARSH_BRAKING: "Harsh braking", HARSH_ACCELERATION: "Harsh acceleration", HARSH_CORNERING: "Harsh cornering",
  EXCESSIVE_IDLING: "Excessive idling", GEOFENCE_ENTER: "Entered zone", GEOFENCE_EXIT: "Left zone", DEVICE_UNPLUGGED: "Tracker unplugged",
}
/** "Speeding: 112 km/h in a 80 km/h limit", or the type alone. */
export function eventText(e: { type: string; value: number | null; limitValue: number | null; geofenceName: string | null; durationSeconds: number | null }): string {
  const base = EVENT_LABEL[e.type] ?? e.type
  if (e.type === "SPEEDING" && e.value != null) return `${base}: ${Math.round(e.value)} km/h${e.limitValue != null ? ` in a ${Math.round(e.limitValue)} km/h limit` : ""}`
  if (e.type === "EXCESSIVE_IDLING" && e.durationSeconds != null) return `${base}: ${Math.round(e.durationSeconds / 60)} min`
  if (e.geofenceName) return `${base}: ${e.geofenceName}`
  return base
}
