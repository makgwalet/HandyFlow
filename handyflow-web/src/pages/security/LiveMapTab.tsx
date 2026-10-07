// src/pages/security/LiveMapTab.tsx
//
// Live Operations: every guard on an active shift, on a map and in a list, with filters. One call
// (GET /live/guards) supplies the guards, their last GPS position and last checkpoint scan. Real data only: a guard
// with no position since the shift began is listed as "No GPS" and is not plotted.
//
// History: this screen used to read /sites/{id}/guards/locations without unwrapping the response and the route was
// mounted at the wrong URL (see LiveOperationsController), so it failed outright.
import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { MapContainer, TileLayer, Marker, Popup } from "react-leaflet"
import L from "leaflet"
import "leaflet/dist/leaflet.css"
import { Activity, MapPin, Radio, Satellite, TimerOff, Users } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import StatTile from "../../components/ui/StatTile"
import { GPS_FILTERS, GPS_LABEL, GPS_TONE, ago, filterGuards, mapCenter, summarise, withPosition, type LiveGuard } from "./liveops.logic"

// A div-icon, not Leaflet's default marker images, which avoids the well-known Leaflet/Vite asset-path problem.
// Its HTML is inserted into this document, so the CSS variables follow the theme.
function guardIcon(state: string, selected: boolean) {
  const bg = state === "LIVE" ? "var(--hf-primary)" : "var(--hf-text-faint)"
  const ring = selected ? "0 0 0 4px color-mix(in srgb, var(--hf-accent) 35%, transparent)" : "0 4px 12px color-mix(in srgb, var(--hf-primary) 40%, transparent)"
  return L.divIcon({
    className: "",
    html: `<div style="width:36px;height:36px;border-radius:50%;background:${bg};border:3px solid var(--hf-text-on-solid);color:var(--hf-text-on-solid);display:flex;align-items:center;justify-content:center;box-shadow:${ring}">
             <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/></svg></div>`,
    iconSize: [36, 36], iconAnchor: [18, 18], popupAnchor: [0, -18],
  })
}

const sel: React.CSSProperties = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)" }
const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })

export default function LiveMapTab() {
  const navigate = useNavigate()
  const [siteId, setSiteId] = useState("")
  const [gps, setGps] = useState("")
  const [search, setSearch] = useState("")
  const [selected, setSelected] = useState<string | null>(null)

  const { data: sites = [] } = useQuery<{ id: string; name: string }[]>({
    queryKey: ["live-map-sites"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/sites?size=100"); const p = r.data?.data ?? r.data; return (p?.content ?? p ?? []).map((s: any) => ({ id: s.id, name: s.name })) },
  })
  const { data, isLoading, error, dataUpdatedAt } = useQuery<LiveGuard[]>({
    queryKey: ["live-guards", siteId],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/live/guards${siteId ? `?siteId=${siteId}` : ""}`); const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : [] },
    refetchInterval: 30000,
  })
  const all = data ?? []
  const shown = filterGuards(all, { gps, search })
  const plotted = withPosition(shown)
  const sum = summarise(all)

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        <StatTile label="On duty" value={sum.onDuty} icon={<Users size={18} />} />
        <StatTile label="Live GPS" value={sum.live} tone={sum.onDuty > 0 && sum.live === 0 ? "warn" : "neutral"} icon={<Satellite size={18} />} />
        <StatTile label="Stale GPS" value={sum.stale} tone={sum.stale > 0 ? "warn" : "neutral"} icon={<Activity size={18} />} />
        <StatTile label="No GPS" value={sum.noGps} tone={sum.noGps > 0 ? "warn" : "neutral"} icon={<MapPin size={18} />} />
        <StatTile label="Past shift end" value={sum.overrunning} tone={sum.overrunning > 0 ? "warn" : "neutral"} icon={<TimerOff size={18} />} />
      </div>

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 12 }}>
        <select aria-label="Site" value={siteId} onChange={e => setSiteId(e.target.value)} style={sel}>
          <option value="">All sites</option>{sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
        <select aria-label="GPS state" value={gps} onChange={e => setGps(e.target.value)} style={sel}>
          {GPS_FILTERS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select>
        <input aria-label="Search guards" placeholder="Search guard or site" value={search} onChange={e => setSearch(e.target.value)} style={{ ...sel, minWidth: 180 }} />
        <span style={{ marginLeft: "auto", fontSize: 12, color: "var(--hf-text-muted)" }}>
          Refreshes every 30 seconds{dataUpdatedAt ? `, updated ${fmtTime(new Date(dataUpdatedAt).toISOString())}` : ""}. Pins grey out after 5 minutes without a GPS ping.</span>
      </div>

      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 12 }}>Live positions could not be loaded.</div>}

      <div style={{ display: "flex", gap: 16, flexWrap: "wrap", alignItems: "flex-start" }}>
        <div style={{ flex: "2 1 460px", minWidth: 0, border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden", minHeight: 480 }}>
          {isLoading ? (
            <div style={{ minHeight: 480, display: "flex", alignItems: "center", justifyContent: "center", color: "var(--hf-text-faint)" }}>Loading positions...</div>
          ) : plotted.length === 0 ? (
            <div style={{ minHeight: 480, display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 8, background: "var(--hf-surface-muted)", color: "var(--hf-text-faint)", textAlign: "center", padding: 16 }}>
              <MapPin size={36} style={{ color: "var(--hf-text-disabled)" }} />
              <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>{all.length === 0 ? "No guards are on shift right now" : "No positions to show for these filters"}</div>
              <div style={{ fontSize: 13 }}>Positions appear once a guard's app records a GPS ping during an active shift.</div>
            </div>
          ) : (
            <MapContainer center={mapCenter(plotted)} zoom={13} style={{ height: 480, width: "100%" }}>
              <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
              {plotted.map(g => (
                <Marker key={g.guardId} position={[g.latitude, g.longitude]} icon={guardIcon(g.gpsState, selected === g.guardId)}
                  eventHandlers={{ click: () => setSelected(selected === g.guardId ? null : g.guardId) }}>
                  <Popup><strong>{g.guardName}</strong><br />{g.siteName}<br />{GPS_LABEL[g.gpsState]}, last ping {ago(g.recordedAt)}</Popup>
                </Marker>
              ))}
            </MapContainer>
          )}
        </div>

        <div style={{ flex: "1 1 300px", display: "grid", gap: 10, minWidth: 0 }}>
          <div style={{ fontSize: 13, fontWeight: 700 }}>Active guards ({shown.length})</div>
          {shown.length === 0 ? (
            <div style={{ textAlign: "center", padding: "30px 16px", border: "1px dashed var(--hf-border)", borderRadius: 10, color: "var(--hf-text-faint)", fontSize: 13 }}>
              {all.length === 0 ? "No guards currently on shift" : "No guards match these filters"}</div>
          ) : shown.map(g => (
            <div key={g.shiftId} role="button" tabIndex={0} aria-pressed={selected === g.guardId}
              onClick={() => setSelected(selected === g.guardId ? null : g.guardId)} onKeyDown={e => { if (e.key === "Enter") setSelected(selected === g.guardId ? null : g.guardId) }}
              style={{ padding: "12px 14px", border: `2px solid ${selected === g.guardId ? "var(--hf-accent)" : "var(--hf-border)"}`, borderRadius: 10, background: "var(--hf-surface)", cursor: "pointer" }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                <span style={{ fontWeight: 700, fontSize: 14 }}>{g.guardName}</span>
                <Chip tone={GPS_TONE[g.gpsState] ?? "neutral"}>{GPS_LABEL[g.gpsState] ?? g.gpsState}</Chip>
              </div>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>{g.siteName ?? "Unknown site"}{g.grade ? ` · Grade ${g.grade}` : ""}</div>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 6 }}>
                Shift {fmtTime(g.shiftStart)} to {fmtTime(g.shiftEnd)}{g.overrunning && <span style={{ marginLeft: 6 }}><Chip tone="warn">past end</Chip></span>}</div>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>
                {g.recordedAt ? `Last ping ${ago(g.recordedAt)}` : "No GPS ping this shift"}
                {" · "}{g.lastScanAt ? <>Last scan <b style={{ color: "var(--hf-text-primary)" }}>{g.lastScanCheckpoint}</b>, {ago(g.lastScanAt)}</> : "No checkpoint scanned yet"}</div>
              <button onClick={e => { e.stopPropagation(); navigate(`/security/guards/${g.guardId}`) }}
                style={{ marginTop: 8, background: "none", border: "none", padding: 0, color: "var(--hf-accent-text)", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>Open guard</button>
            </div>
          ))}
        </div>
      </div>
      <div style={{ marginTop: 12, fontSize: 12, color: "var(--hf-text-muted)", display: "flex", gap: 6, alignItems: "center" }}><Radio size={12} /> Real positions only. Nothing on this screen is sample data.</div>
    </div>
  )
}
