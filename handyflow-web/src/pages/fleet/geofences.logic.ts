// Geofence drawing rules: the form, polygon editing, shape checks and the request body. The map only collects clicks and drags; every rule lives here.
export type LatLng = [number, number]
export interface Geofence { id: string; name: string; shape: string; centerLatitude: number | null; centerLongitude: number | null; radiusMeters: number | null; polygon: { latitude: number; longitude: number }[] | null; alertOnEnter: boolean; alertOnExit: boolean; speedLimitKph: number | null; active: boolean }
export interface GeofenceForm { id: string | null; name: string; shape: "CIRCLE" | "POLYGON"; centre: LatLng | null; radiusMeters: string; polygon: LatLng[]; alertOnEnter: boolean; alertOnExit: boolean; speedLimitKph: string; active: boolean }

export const MAX_POINTS = 200
export const emptyGeofence = (shape: "CIRCLE" | "POLYGON" = "CIRCLE"): GeofenceForm => ({ id: null, name: "", shape, centre: null, radiusMeters: "500", polygon: [], alertOnEnter: true, alertOnExit: true, speedLimitKph: "", active: true })
export const toGeofenceForm = (g: Geofence): GeofenceForm => ({
  id: g.id, name: g.name, shape: g.shape === "POLYGON" ? "POLYGON" : "CIRCLE",
  centre: g.centerLatitude != null && g.centerLongitude != null ? [g.centerLatitude, g.centerLongitude] : null,
  radiusMeters: g.radiusMeters == null ? "500" : String(g.radiusMeters), polygon: (g.polygon ?? []).map(p => [p.latitude, p.longitude] as LatLng),
  alertOnEnter: g.alertOnEnter, alertOnExit: g.alertOnExit, speedLimitKph: g.speedLimitKph == null ? "" : String(g.speedLimitKph), active: g.active,
})

// ── Drawing: what a click on the map does, and editing the corners ──────────────────────────────────────
/** A click sets the centre of a circle, or adds the next corner of a polygon (up to 200). */
export function clickMap(f: GeofenceForm, p: LatLng): GeofenceForm {
  if (f.shape === "CIRCLE") return { ...f, centre: p }
  return f.polygon.length >= MAX_POINTS ? f : { ...f, polygon: [...f.polygon, p] }
}
export const movePoint = (f: GeofenceForm, i: number, p: LatLng): GeofenceForm => (f.shape === "CIRCLE" ? { ...f, centre: p } : { ...f, polygon: f.polygon.map((q, k) => (k === i ? p : q)) })
export const removePoint = (f: GeofenceForm, i: number): GeofenceForm => ({ ...f, polygon: f.polygon.filter((_, k) => k !== i) })
export const undoPoint = (f: GeofenceForm): GeofenceForm => (f.shape === "CIRCLE" ? { ...f, centre: null } : { ...f, polygon: f.polygon.slice(0, -1) })
export const clearShape = (f: GeofenceForm): GeofenceForm => ({ ...f, centre: null, polygon: [] })
/** Switching shape keeps the name and alerts but starts the outline again, so a stray circle centre is never saved with a polygon. */
export const switchShape = (f: GeofenceForm, shape: "CIRCLE" | "POLYGON"): GeofenceForm => (f.shape === shape ? f : { ...clearShape(f), shape })

// ── Geometry (small areas: a flat local projection is accurate to well under 1%) ────────────────────────
const M_PER_DEG = 111_320
const project = (pts: LatLng[]): [number, number][] => { const lat0 = pts.reduce((s, p) => s + p[0], 0) / pts.length; return pts.map(p => [p[1] * M_PER_DEG * Math.cos((lat0 * Math.PI) / 180), p[0] * M_PER_DEG]) }
export function polygonAreaM2(pts: LatLng[]): number {
  if (pts.length < 3) return 0
  const q = project(pts); let a = 0
  for (let i = 0; i < q.length; i++) { const [x1, y1] = q[i], [x2, y2] = q[(i + 1) % q.length]; a += x1 * y2 - x2 * y1 }
  return Math.abs(a) / 2
}
export const circleAreaM2 = (radius: number) => Math.PI * radius * radius
export function areaText(m2: number): string {
  if (m2 <= 0) return "—"
  return m2 >= 1_000_000 ? `${(m2 / 1_000_000).toFixed(2)} km²` : m2 >= 10_000 ? `${(m2 / 10_000).toFixed(1)} ha` : `${Math.round(m2).toLocaleString("en-ZA")} m²`
}
const cross = (o: [number, number], a: [number, number], b: [number, number]) => (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
/** True when two edges of the outline cross each other (a bow-tie), which makes "inside" ambiguous. */
export function selfIntersects(pts: LatLng[]): boolean {
  if (pts.length < 4) return false
  const q = project(pts), n = q.length
  for (let i = 0; i < n; i++) for (let j = i + 1; j < n; j++) {
    if (j === i + 1 || (i === 0 && j === n - 1)) continue
    const [a, b] = [q[i], q[(i + 1) % n]], [c, d] = [q[j], q[(j + 1) % n]]
    if (cross(a, b, c) * cross(a, b, d) < 0 && cross(c, d, a) * cross(c, d, b) < 0) return true
  }
  return false
}

// ── Saving ────────────────────────────────────────────────────────────────────────────────────────────
export function geofenceProblem(f: GeofenceForm): string | null {
  if (!f.name.trim()) return "Name the geofence."
  if (f.name.trim().length > 150) return "The name is too long (150 characters at most)."
  if (f.shape === "CIRCLE") {
    if (!f.centre) return "Click the map to place the centre."
    const r = Number(f.radiusMeters)
    if (!Number.isInteger(r) || r < 10 || r > 100000) return "The radius must be a whole number of metres from 10 to 100 000."
  } else {
    if (f.polygon.length < 3) return `Click the map to add corners (${f.polygon.length} of at least 3).`
    if (selfIntersects(f.polygon)) return "The outline crosses itself. Move a corner so the edges do not cross."
  }
  if (f.speedLimitKph.trim() !== "") { const s = Number(f.speedLimitKph); if (!Number.isInteger(s) || s < 5 || s > 200) return "The speed limit must be a whole number from 5 to 200 km/h." }
  if (!f.alertOnEnter && !f.alertOnExit && f.speedLimitKph.trim() === "") return "Turn on an alert or set a speed limit, or this zone does nothing."
  return null
}
const r6 = (n: number) => Math.round(n * 1e6) / 1e6
export function geofenceBody(f: GeofenceForm) {
  const circle = f.shape === "CIRCLE"
  return {
    name: f.name.trim(), shape: f.shape,
    centerLatitude: circle && f.centre ? r6(f.centre[0]) : null, centerLongitude: circle && f.centre ? r6(f.centre[1]) : null, radiusMeters: circle ? Number(f.radiusMeters) : null,
    polygon: circle ? null : f.polygon.map(p => ({ latitude: r6(p[0]), longitude: r6(p[1]) })),
    alertOnEnter: f.alertOnEnter, alertOnExit: f.alertOnExit, speedLimitKph: f.speedLimitKph.trim() === "" ? null : Number(f.speedLimitKph), active: f.active,
  }
}

export function geofenceSummary(g: Geofence): string {
  const size = g.shape === "POLYGON" ? `${g.polygon?.length ?? 0} corners · ${areaText(polygonAreaM2((g.polygon ?? []).map(p => [p.latitude, p.longitude] as LatLng)))}` : `${(g.radiusMeters ?? 0).toLocaleString("en-ZA")} m radius`
  const alerts = [g.alertOnEnter && "enter", g.alertOnExit && "exit"].filter(Boolean).join(" and ")
  return [size, alerts ? `alerts on ${alerts}` : "no alerts", g.speedLimitKph ? `${g.speedLimitKph} km/h limit` : null].filter(Boolean).join(" · ")
}
/** Corners to centre the map on: the draft outline, else the circle centre, else every saved fence. */
export function focusPoints(f: GeofenceForm | null, fences: Geofence[]): LatLng[] {
  if (f?.shape === "POLYGON" && f.polygon.length) return f.polygon
  if (f?.shape === "CIRCLE" && f.centre) return [f.centre]
  return fences.flatMap(g => (g.shape === "POLYGON" ? (g.polygon ?? []).map(p => [p.latitude, p.longitude] as LatLng) : g.centerLatitude != null && g.centerLongitude != null ? [[g.centerLatitude, g.centerLongitude] as LatLng] : []))
}

// ── Address search (OpenStreetMap Nominatim) ───────────────────────────────
export interface Place { name: string; point: LatLng; box: [LatLng, LatLng] | null }
export const SEARCH_MIN_CHARS = 3
/** The search request, or null when there is too little to search for. */
export function searchUrl(query: string): string | null {
  const q = query.trim()
  return q.length < SEARCH_MIN_CHARS ? null : `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=${encodeURIComponent(q)}`
}
/** Turns the service's answer into places, dropping anything without a usable position. Never throws on odd input. */
export function parsePlaces(raw: unknown): Place[] {
  if (!Array.isArray(raw)) return []
  const out: Place[] = []
  for (const r of raw as Record<string, unknown>[]) {
    const lat = Number(r?.lat), lon = Number(r?.lon)
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) continue
    const b = Array.isArray(r.boundingbox) ? r.boundingbox.map(Number) : []
    const box: Place["box"] = b.length === 4 && b.every(Number.isFinite) ? [[b[0], b[2]], [b[1], b[3]]] : null   // service order: south, north, west, east
    out.push({ name: String(r.display_name ?? "Unnamed place"), point: [lat, lon], box })
  }
  return out
}
