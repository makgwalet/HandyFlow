// src/pages/fleet/LiveMapTab.tsx
//
// Live map: where every tracked vehicle was last seen, filtered by status, with a side list, a 24 h track for the chosen vehicle,
// and the driving events (speeding, harsh braking, idling, zone crossings) that still need someone to acknowledge them.
import { Suspense, lazy, useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { EVENT_LABEL, LIVE_COLOUR, LIVE_LABEL, eventText, fixText, liveCounts, speedText, visiblePositions, type LivePosition } from "./liveMap.logic"

const FleetMap = lazy(() => import("./FleetMap"))

interface TrackResponse { distanceKm: number; totalPoints: number; points: { latitude: number; longitude: number }[] }
interface Event { id: string; registration: string; driverName: string | null; type: string; severity: string; occurredAt: string; value: number | null; limitValue: number | null; durationSeconds: number | null; geofenceName: string | null; acknowledged: boolean }

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const list = <T,>(r: { data: unknown }): T[] => { const p = unwrap<T[] | { content?: T[] }>(r); return (Array.isArray(p) ? p : p?.content) ?? [] }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }

export default function LiveMapTab({ onNavigate }: { onNavigate?: (section: string) => void }) {
  const qc = useQueryClient()
  const canManage = usePermission("FLEET_MANAGE")
  const [status, setStatus] = useState("ALL"), [search, setSearch] = useState(""), [selected, setSelected] = useState<string | null>(null)
  const live = useQuery<LivePosition[]>({ queryKey: ["fleet", "live"], refetchInterval: 30000, queryFn: async () => list<LivePosition>(await apiClient.get("/api/v1/fleet/telematics/live")) })
  const track = useQuery<TrackResponse>({ queryKey: ["fleet", "track", selected], enabled: !!selected, queryFn: async () => unwrap<TrackResponse>(await apiClient.get(`/api/v1/fleet/vehicles/${selected}/telematics/track`)) })
  const events = useQuery<Event[]>({ queryKey: ["fleet", "telematics-events"], queryFn: async () => list<Event>(await apiClient.get("/api/v1/fleet/telematics/events?unacknowledgedOnly=true&size=10")) })
  const ack = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/fleet/telematics/events/${id}/acknowledge`), onSuccess: () => qc.invalidateQueries({ queryKey: ["fleet", "telematics-events"] }) })
  const all = live.data ?? []
  const shown = visiblePositions(all, status, search)
  const trackPoints: [number, number][] = (track.data?.points ?? []).map(p => [p.latitude, p.longitude])
  return (
    <div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12, alignItems: "center" }}>
        <button type="button" aria-pressed={status === "ALL"} onClick={() => setStatus("ALL")} style={{ ...field, cursor: "pointer", fontWeight: status === "ALL" ? 800 : 500 }}>All ({all.length})</button>
        {liveCounts(all).map(c => (
          <button key={c.status} type="button" aria-pressed={status === c.status} onClick={() => setStatus(c.status)} style={{ ...field, cursor: "pointer", fontWeight: status === c.status ? 800 : 500 }}>
            <span style={{ display: "inline-block", width: 9, height: 9, borderRadius: 99, background: LIVE_COLOUR[c.status], marginRight: 6 }} />{LIVE_LABEL[c.status]} ({c.count})</button>))}
        <input aria-label="Search vehicles" placeholder="Search vehicle" style={{ ...field, minWidth: 180 }} value={search} onChange={e => setSearch(e.target.value)} />
      </div>
      {live.isError && <div role="alert">Couldn't load vehicle positions.</div>}
      {!live.isLoading && !live.isError && all.length === 0 && (
        <div role="status" aria-label="No tracked vehicles" style={{ border: "1px dashed var(--hf-border)", borderRadius: 12, padding: 18, marginBottom: 14 }}>
          <strong>No tracked vehicles yet.</strong>
          <p style={{ margin: "6px 0 10px", color: "var(--hf-text-muted)", fontSize: 13 }}>A vehicle appears here once a tracking device is registered to it and has sent a position.</p>
          {onNavigate && <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button type="button" style={{ ...field, cursor: "pointer", fontWeight: 700 }} onClick={() => onNavigate("tracking-setup")}>Register a tracking device</button>
            <button type="button" style={{ ...field, cursor: "pointer" }} onClick={() => onNavigate("geofences")}>Set up geofences</button></div>}
        </div>)}
      {all.length > 0 && (
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0,1fr) 300px", gap: 14 }}>
          <Suspense fallback={<p>Loading map…</p>}><FleetMap positions={shown} selectedId={selected} track={trackPoints} onSelect={setSelected} /></Suspense>
          <ul style={{ listStyle: "none", margin: 0, padding: 0, maxHeight: 420, overflow: "auto" }}>
            {shown.length === 0 && <li style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No vehicles match.</li>}
            {shown.map(p => (
              <li key={p.vehicleId}>
                <button type="button" aria-label={`Show ${p.registration}`} aria-pressed={selected === p.vehicleId} onClick={() => setSelected(s => (s === p.vehicleId ? null : p.vehicleId))}
                  style={{ width: "100%", textAlign: "left", padding: "8px 10px", marginBottom: 6, borderRadius: 8, border: `1px solid ${selected === p.vehicleId ? "var(--hf-brand)" : "var(--hf-border)"}`, background: "var(--hf-surface)", cursor: "pointer" }}>
                  <span style={{ display: "inline-block", width: 9, height: 9, borderRadius: 99, background: LIVE_COLOUR[p.status], marginRight: 6 }} /><strong>{p.registration}</strong>
                  <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{LIVE_LABEL[p.status]} · {speedText(p.speedKph)} · {fixText(p)}</div>
                </button>
              </li>))}
          </ul>
        </div>)}
      {selected && track.data && <p role="status" style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>Last 24 hours: about {track.data.distanceKm.toFixed(1)} km (a GPS estimate, not the odometer), {track.data.totalPoints} positions.</p>}
      <h3 style={{ margin: "18px 0 6px" }}>Driving events to review</h3>
      {(events.data ?? []).length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No events waiting.</p>}
      <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>{(events.data ?? []).map(e => (
        <li key={e.id} aria-label={`${e.registration} ${EVENT_LABEL[e.type] ?? e.type}`} style={{ display: "flex", gap: 10, alignItems: "center", padding: "8px 0", borderBottom: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
          <div style={{ flex: 1 }}><strong>{e.registration}</strong>{e.driverName ? ` · ${e.driverName}` : ""} · {new Date(e.occurredAt).toLocaleString("en-ZA")}<div style={{ color: "var(--hf-text-muted)" }}>{eventText(e)}</div></div>
          {canManage && <button type="button" style={{ ...field, cursor: "pointer" }} disabled={ack.isPending} onClick={() => ack.mutate(e.id)}>Acknowledge</button>}
        </li>))}</ul>
    </div>
  )
}
