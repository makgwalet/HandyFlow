// src/pages/security/LiveMapTab.tsx
//
// FIX: replaced the fabricated, fixed-position marker placeholder with
// a real Leaflet map fed by the real GET /sites/{id}/guards/locations
// endpoint — real GPS pings have been collected all along
// (GuardLocationService.recordPing()); this closes the missing read
// side, confirmed directly against security_guard_current_location's
// own table comment before writing anything.

import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { MapContainer, TileLayer, Marker, Popup } from "react-leaflet"
import L from "leaflet"
import "leaflet/dist/leaflet.css"
import { Radio, MapPin, QrCode, Bluetooth, Navigation, Clock } from "lucide-react"

const SCAN_TYPE_CONFIG: Record<string, { label: string; color: string; icon: React.ElementType }> = {
  QR:       { label: "QR Code",    color: "#1D4ED8", icon: QrCode },
  NFC:      { label: "NFC Tag",    color: "#7C3AED", icon: Radio },
  BLE:      { label: "BLE Beacon", color: "#0D9488", icon: Bluetooth },
  GPS_PING: { label: "GPS Ping",   color: "#166534", icon: Navigation },
  MANUAL:   { label: "Manual",     color: "#D97706", icon: Clock },
}

interface ScanLog {
  id: string; checkpointId: string; checkpointName: string
  guardId: string; shiftId: string; scannedAt: string
  latitude: number | null; longitude: number | null; scanType: string
}
interface SiteOption { id: string; name: string }
interface CurrentLocation {
  guardId: string; guardName: string; shiftId: string | null; siteId: string
  latitude: number; longitude: number; recordedAt: string; stale: boolean
}

// A branded div-icon rather than Leaflet's default marker images —
// avoids the well-known Leaflet/Vite asset-path bundling issue
// entirely, and matches the shield-in-a-circle look the original
// placeholder already used.
function guardIcon(stale: boolean, selected: boolean) {
  const bg = stale ? "#94A3B8" : "#1B3A6B"
  const ring = selected ? "0 0 0 4px rgba(13,148,136,0.35)" : "0 4px 12px rgba(27,58,107,0.4)"
  return L.divIcon({
    className: "",
    html: `<div style="width:36px;height:36px;border-radius:50%;background:${bg};border:3px solid #fff;display:flex;align-items:center;justify-content:center;box-shadow:${ring}">
             <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/></svg>
           </div>`,
    iconSize: [36, 36],
    iconAnchor: [18, 18],
    popupAnchor: [0, -18],
  })
}

export default function LiveMapTab() {
  const [selectedGuard, setSelectedGuard] = useState<string | null>(null)
  const [siteId, setSiteId] = useState<string>("")

  const { data: sites = [] } = useQuery<SiteOption[]>({
    queryKey: ["live-map-sites"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/sites?size=100")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p ?? []).map((s: any) => ({ id: s.id, name: s.name }))
    },
  })

  const { data: guards = [] } = useQuery<any[]>({
    queryKey: ["guards"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/guards?size=100")
      const p = r.data?.data ?? r.data
      return p?.content ?? []
    },
    refetchInterval: 30000,
  })

  const { data: shifts = [] } = useQuery<any[]>({
    queryKey: ["active-shifts"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/shifts?size=100")
      const p = r.data?.data ?? r.data
      const all = p?.content ?? []
      return all.filter((s: any) => s.status === "ACTIVE")
    },
    refetchInterval: 30000,
  })

  // Real current positions — the fix itself. Refetches every 30s to
  // stay reasonably live without hammering the endpoint on every render.
  const { data: locations = [], isLoading: locationsLoading } = useQuery<CurrentLocation[]>({
    queryKey: ["live-locations", siteId],
    queryFn: async () => (await apiClient.get(`/api/v1/security/sites/${siteId}/guards/locations`)).data.data ?? [],
    enabled: !!siteId,
    refetchInterval: 30000,
  })

  const { data: shiftScans = {} } = useQuery<Record<string, ScanLog | null>>({
    queryKey: ["shift-scans", shifts.map((s: any) => s.id).join(",")],
    queryFn: async () => {
      if (shifts.length === 0) return {}
      const results = await Promise.all(
        shifts.map(async (shift: any) => {
          try {
            const r = await apiClient.get(`/api/v1/security/shifts/${shift.id}/scans`)
            const p = r.data?.data ?? r.data
            const scans: ScanLog[] = Array.isArray(p) ? p : []
            return [shift.id, scans.length > 0 ? scans[scans.length - 1] : null]
          } catch {
            return [shift.id, null]
          }
        })
      )
      return Object.fromEntries(results)
    },
    enabled: shifts.length > 0,
    refetchInterval: 30000,
  })

  const activeCount = shifts.length
  const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })
  const fmtRelative = (iso: string) => {
    const mins = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
    return mins < 1 ? "just now" : `${mins} min ago`
  }

  // South Africa's rough centre as a sensible default when no guard has
  // a position yet — not a made-up guard location, just a starting
  // viewport with nothing plotted on it.
  const center: [number, number] = locations.length > 0
    ? [locations.reduce((s, l) => s + l.latitude, 0) / locations.length, locations.reduce((s, l) => s + l.longitude, 0) / locations.length]
    : [-28.4793, 24.6727]

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h3 style={{ margin: "0 0 4px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Live Operations Map</h3>
          <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-faint)" }}>Real-time guard positions and checkpoint scans</p>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8, padding: "6px 14px", background: "var(--hf-success-soft-strong)", border: "1px solid var(--hf-success-border)", borderRadius: 20 }}>
          <div style={{ width: 8, height: 8, borderRadius: "50%", background: "var(--hf-success)" }} />
          <span style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-success-text-strong)" }}>{activeCount} guards on duty</span>
        </div>
      </div>

      <div style={{ marginBottom: 16, maxWidth: 320 }}>
        <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }}>Site</label>
        <select value={siteId} onChange={e => setSiteId(e.target.value)}
          style={{ width: "100%", padding: "8px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)" }}>
          <option value="">Select a site to view live positions…</option>
          {sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </div>

      {/* Supported scan types */}
      <div style={{ marginBottom: 20, padding: "14px 18px", background: "var(--hf-info-soft)", border: "1px solid var(--hf-info-border)", borderRadius: 10 }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-info-text)", marginBottom: 6 }}>Supported scan types</div>
        <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
          {Object.entries(SCAN_TYPE_CONFIG).map(([key, cfg]) => (
            <div key={key} style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "var(--hf-text-tertiary)" }}>
              <div style={{ width: 22, height: 22, borderRadius: 6, background: `${cfg.color}18`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                <cfg.icon size={12} color={cfg.color} />
              </div>
              {cfg.label}
            </div>
          ))}
        </div>
        <div style={{ marginTop: 8, fontSize: 12, color: "var(--hf-text-muted)" }}>
          GPS pings every ~5 minutes during active shifts. A pin greys out if a guard hasn't pinged in over 5 minutes.
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 300px", gap: 16 }}>
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden", minHeight: 500 }}>
          {!siteId ? (
            <div style={{ minHeight: 500, display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 10, background: "var(--hf-surface-muted)", color: "var(--hf-text-faint)" }}>
              <MapPin size={36} color="#CBD5E1" />
              <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>Select a site above</div>
            </div>
          ) : locationsLoading ? (
            <div style={{ minHeight: 500, display: "flex", alignItems: "center", justifyContent: "center", color: "var(--hf-text-faint)" }}>Loading positions…</div>
          ) : locations.length === 0 ? (
            <div style={{ minHeight: 500, display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 10, background: "var(--hf-surface-muted)", color: "var(--hf-text-faint)" }}>
              <MapPin size={36} color="#CBD5E1" />
              <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No guard positions yet at this site</div>
              <div style={{ fontSize: 13 }}>Positions appear once a guard's app records a GPS ping during an open shift</div>
            </div>
          ) : (
            <MapContainer center={center} zoom={13} style={{ height: 500, width: "100%" }}>
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />
              {locations.map(loc => (
                <Marker key={loc.guardId} position={[loc.latitude, loc.longitude]}
                  icon={guardIcon(loc.stale, selectedGuard === loc.guardId)}
                  eventHandlers={{ click: () => setSelectedGuard(selectedGuard === loc.guardId ? null : loc.guardId) }}>
                  <Popup>
                    <strong>{loc.guardName}</strong><br />
                    {loc.stale ? <span style={{ color: "var(--hf-warning-text-strong)" }}>Stale — </span> : null}
                    Last ping {fmtRelative(loc.recordedAt)}
                  </Popup>
                </Marker>
              ))}
            </MapContainer>
          )}
        </div>

        {/* Active guards list */}
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 4 }}>Active Guards</div>

          {shifts.length === 0 ? (
            <div style={{ textAlign: "center", padding: "30px 16px", border: "1px dashed var(--hf-border)", borderRadius: 10, color: "var(--hf-text-faint)", fontSize: 13 }}>
              No guards currently on shift
            </div>
          ) : (
            shifts.map((shift: any, i: number) => {
              const guard   = guards.find((g: any) => g.id === shift.guardId)
              const lastScan: ScanLog | null = shiftScans[shift.id] ?? null
              const loc = locations.find(l => l.guardId === shift.guardId)

              return (
                <div key={shift.id}
                  onClick={() => setSelectedGuard(selectedGuard === shift.guardId ? null : shift.guardId)}
                  style={{ padding: "14px 16px", border: `2px solid ${selectedGuard === shift.guardId ? "#0D9488" : "#E2E8F0"}`, borderRadius: 10, background: selectedGuard === shift.guardId ? "var(--hf-success-soft)" : "var(--hf-surface)", cursor: "pointer" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 8 }}>
                    <div style={{ position: "relative", flexShrink: 0 }}>
                      <div style={{ width: 36, height: 36, borderRadius: "50%", background: "var(--hf-info-soft)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, color: "var(--hf-info-text)", fontSize: 13 }}>
                        {guard ? `${guard.firstName?.[0]}${guard.lastName?.[0]}` : "?"}
                      </div>
                      <div style={{ position: "absolute", bottom: 0, right: 0, width: 10, height: 10, borderRadius: "50%", background: loc && !loc.stale ? "var(--hf-success)" : "#CBD5E1", border: "2px solid var(--hf-surface)" }} />
                    </div>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: 13, color: "var(--hf-text)" }}>{guard?.fullName ?? `Guard ${i + 1}`}</div>
                      <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Grade {guard?.grade ?? "—"}</div>
                    </div>
                  </div>

                  <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>
                    Shift: {fmtTime(shift.startAt)} – {fmtTime(shift.endAt)}
                  </div>

                  <div style={{ marginTop: 6, fontSize: 11, color: loc ? (loc.stale ? "var(--hf-warning-text-strong)" : "var(--hf-success-text-strong)") : "var(--hf-text-faint)" }}>
                    {loc ? `Position: ${fmtRelative(loc.recordedAt)}${loc.stale ? " (stale)" : ""}` : "No GPS ping yet"}
                  </div>

                  <div style={{ marginTop: 8, padding: "6px 10px", background: "var(--hf-surface-muted)", borderRadius: 6, fontSize: 11, color: "var(--hf-text-muted)" }}>
                    {lastScan ? (
                      <span>
                        Last: <strong style={{ color: "var(--hf-text)" }}>{lastScan.checkpointName}</strong>
                        {" · "}{fmtTime(lastScan.scannedAt)}
                        {lastScan.scanType && lastScan.scanType !== "QR" && (
                          <span style={{ marginLeft: 6, background: `${SCAN_TYPE_CONFIG[lastScan.scanType]?.color ?? "#64748B"}18`, color: SCAN_TYPE_CONFIG[lastScan.scanType]?.color ?? "var(--hf-text-muted)", padding: "1px 5px", borderRadius: 10, fontSize: 10, fontWeight: 600 }}>
                            {SCAN_TYPE_CONFIG[lastScan.scanType]?.label ?? lastScan.scanType}
                          </span>
                        )}
                      </span>
                    ) : (
                      <span>No checkpoints scanned yet</span>
                    )}
                  </div>
                </div>
              )
            })
          )}
        </div>
      </div>
    </div>
  )
}
