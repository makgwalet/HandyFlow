// src/pages/fleet/GeofencesTab.tsx
//
// Geofences: draw a circle (click for the centre, set the radius) or a polygon (click each corner, drag to adjust), then choose the
// alerts and an optional lower speed limit inside the zone. Saved zones are listed and can be edited, switched off or deleted.
import { Suspense, lazy, useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { areaText, circleAreaM2, clearShape, clickMap, emptyGeofence, geofenceBody, geofenceProblem, parsePlaces, searchUrl, geofenceSummary, movePoint, polygonAreaM2, removePoint, switchShape, toGeofenceForm, undoPoint, type Geofence, type GeofenceForm, type LatLng, type Place } from "./geofences.logic"

const GeofenceMap = lazy(() => import("./GeofenceMap"))

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const btn: React.CSSProperties = { ...field, cursor: "pointer", fontWeight: 700, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)" }
const ghost: React.CSSProperties = { ...field, cursor: "pointer" }

export default function GeofencesTab() {
  const qc = useQueryClient()
  const canManage = usePermission("FLEET_MANAGE")
  const [draft, setDraft] = useState<GeofenceForm | null>(null), [error, setError] = useState<string | null>(null), [confirmId, setConfirmId] = useState<string | null>(null)
  const { data: fences = [], isLoading, isError } = useQuery<Geofence[]>({ queryKey: ["fleet", "geofences"], queryFn: async () => unwrap<Geofence[]>(await apiClient.get("/api/v1/fleet/telematics/geofences")) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["fleet", "geofences"] })
  const save = useMutation({
    mutationFn: (f: GeofenceForm) => f.id ? apiClient.put(`/api/v1/fleet/telematics/geofences/${f.id}`, geofenceBody(f)) : apiClient.post("/api/v1/fleet/telematics/geofences", geofenceBody(f)),
    onSuccess: () => { setDraft(null); setError(null); refresh() }, onError: e => setError(errorOf(e)),
  })
  const toggle = useMutation({ mutationFn: (g: Geofence) => apiClient.put(`/api/v1/fleet/telematics/geofences/${g.id}`, geofenceBody({ ...toGeofenceForm(g), active: !g.active })), onSuccess: refresh, onError: e => setError(errorOf(e)) })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/fleet/telematics/geofences/${id}`), onSuccess: () => { setConfirmId(null); refresh() }, onError: e => setError(errorOf(e)) })

  const [query, setQuery] = useState(""), [places, setPlaces] = useState<Place[] | null>(null), [searching, setSearching] = useState(false), [searchError, setSearchError] = useState<string | null>(null), [goTo, setGoTo] = useState<Place | null>(null)
  const search = async () => {
    const url = searchUrl(query)
    if (!url) { setSearchError("Type at least 3 characters of an address or place."); setPlaces(null); return }
    setSearching(true); setSearchError(null)
    try {
      const res = await fetch(url, { headers: { Accept: "application/json" } })   // a public map service, not our API: no sign-in token goes with it
      if (!res.ok) throw new Error(String(res.status))
      setPlaces(parsePlaces(await res.json()))
    } catch { setPlaces(null); setSearchError("Address search is unavailable right now. You can still pan and zoom the map.") }
    finally { setSearching(false) }
  }
  const change = (f: GeofenceForm) => { setDraft(f); setError(null) }
  const problem = draft ? geofenceProblem(draft) : null
  const area = !draft ? null : draft.shape === "CIRCLE" ? (draft.centre && Number(draft.radiusMeters) > 0 ? circleAreaM2(Number(draft.radiusMeters)) : 0) : polygonAreaM2(draft.polygon)
  const onClick = (p: LatLng) => draft && change(clickMap(draft, p))

  return (
    <div>
      {isLoading && <p>Loading…</p>}
      {isError && <div role="alert">Couldn't load geofences.</div>}
      {!draft && canManage && <div style={{ display: "flex", gap: 8, marginBottom: 10 }}>
        <button type="button" style={btn} onClick={() => change(emptyGeofence("CIRCLE"))}>New circle</button>
        <button type="button" style={btn} onClick={() => change(emptyGeofence("POLYGON"))}>New polygon</button></div>}
      {draft && (
        <form aria-label="Geofence editor" onSubmit={e => { e.preventDefault(); if (!problem) save.mutate(draft) }} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12, marginBottom: 10 }}>
          <strong>{draft.id ? "Edit geofence" : draft.shape === "CIRCLE" ? "New circular geofence" : "New polygon geofence"}</strong>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", margin: "8px 0", alignItems: "center" }}>
            <input aria-label="Geofence name" placeholder="Name (e.g. Depot, Customer site)" style={{ ...field, minWidth: 240 }} value={draft.name} onChange={e => change({ ...draft, name: e.target.value })} />
            <select aria-label="Shape" style={field} value={draft.shape} onChange={e => change(switchShape(draft, e.target.value as "CIRCLE" | "POLYGON"))}><option value="CIRCLE">Circle</option><option value="POLYGON">Polygon</option></select>
            {draft.shape === "CIRCLE" && <label style={{ fontSize: 12.5 }}>Radius (m) <input aria-label="Radius in metres" type="number" min="10" max="100000" style={{ ...field, width: 100 }} value={draft.radiusMeters} onChange={e => change({ ...draft, radiusMeters: e.target.value })} /></label>}
            <label style={{ fontSize: 12.5 }}>Speed limit inside (km/h) <input aria-label="Speed limit inside" type="number" min="5" max="200" placeholder="none" style={{ ...field, width: 90 }} value={draft.speedLimitKph} onChange={e => change({ ...draft, speedLimitKph: e.target.value })} /></label>
          </div>
          <div style={{ display: "flex", gap: 16, flexWrap: "wrap", fontSize: 13, marginBottom: 8 }}>
            <label><input type="checkbox" aria-label="Alert on enter" checked={draft.alertOnEnter} onChange={e => change({ ...draft, alertOnEnter: e.target.checked })} /> Alert when a vehicle enters</label>
            <label><input type="checkbox" aria-label="Alert on exit" checked={draft.alertOnExit} onChange={e => change({ ...draft, alertOnExit: e.target.checked })} /> Alert when a vehicle leaves</label>
            <label><input type="checkbox" aria-label="Active" checked={draft.active} onChange={e => change({ ...draft, active: e.target.checked })} /> Active</label>
          </div>
          <div style={{ fontSize: 12.5, color: "var(--hf-text-muted)", marginBottom: 8 }}>
            {draft.shape === "CIRCLE" ? "Click the map to place the centre; drag the dot to adjust." : `Click the map to add each corner in order (${draft.polygon.length} so far); drag a corner to move it, right-click to remove it.`}
            {area ? <strong aria-label="Area" style={{ marginLeft: 8 }}>Area {areaText(area)}</strong> : null}
          </div>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            <button type="submit" style={{ ...btn, opacity: problem || save.isPending ? 0.5 : 1 }} disabled={!!problem || save.isPending}>{save.isPending ? "Saving…" : "Save geofence"}</button>
            <button type="button" style={ghost} onClick={() => change(undoPoint(draft))}>{draft.shape === "CIRCLE" ? "Remove centre" : "Undo last corner"}</button>
            <button type="button" style={ghost} onClick={() => change(clearShape(draft))}>Clear outline</button>
            {draft.shape === "POLYGON" && draft.polygon.length > 0 && <button type="button" style={ghost} aria-label="Remove the first corner" onClick={() => change(removePoint(draft, 0))}>Remove first corner</button>}
            <button type="button" style={ghost} onClick={() => { setDraft(null); setError(null) }}>Cancel</button>
            {problem && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{problem}</span>}
          </div>
          {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
        </form>)}
      <div role="search" aria-label="Find an address" style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 8 }}>
        <input aria-label="Search address" placeholder="Find an address or place on the map" style={{ ...field, minWidth: 280 }} value={query}
          onChange={e => { setQuery(e.target.value); setSearchError(null) }} onKeyDown={e => { if (e.key === "Enter") { e.preventDefault(); void search() } }} />
        <button type="button" style={ghost} disabled={searching} onClick={() => void search()}>{searching ? "Searching…" : "Search"}</button>
        {searchError && <span role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)" }}>{searchError}</span>}
      </div>
      {places && (places.length === 0
        ? <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>No place found. Try a suburb or town name.</p>
        : <ul aria-label="Search results" style={{ listStyle: "none", margin: "0 0 8px", padding: 0, fontSize: 12.5, border: "1px solid var(--hf-border)", borderRadius: 8 }}>{places.map((p, i) => (
          <li key={i}><button type="button" style={{ ...ghost, border: "none", width: "100%", textAlign: "left" }} onClick={() => { setGoTo(p); setPlaces(null) }}>{p.name}</button></li>))}</ul>)}
      <Suspense fallback={<p>Loading map…</p>}>
        <GeofenceMap fences={fences} draft={draft} goTo={goTo} onClick={onClick} onMove={(i, p) => draft && change(movePoint(draft, i, p))} onRemove={i => draft && change(removePoint(draft, i))} />
      </Suspense>
      {!draft && error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
      <h4 style={{ margin: "14px 0 4px" }}>Geofences ({fences.length})</h4>
      {!isLoading && fences.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No geofences yet. Draw one around a depot, a customer site or a no-go area.</p>}
      <ul style={{ listStyle: "none", margin: 0, padding: 0, fontSize: 13 }}>{fences.map(g => (
        <li key={g.id} aria-label={g.name} style={{ padding: "8px 0", borderBottom: "1px solid var(--hf-border-subtle)", display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", opacity: g.active ? 1 : 0.6 }}>
          <strong>{g.name}</strong>{!g.active && <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>switched off</span>}
          <span style={{ color: "var(--hf-text-muted)", fontSize: 12.5 }}>{geofenceSummary(g)}</span>
          {canManage && <span style={{ marginLeft: "auto", display: "flex", gap: 6 }}>
            <button type="button" style={ghost} aria-label={`Edit ${g.name}`} onClick={() => change(toGeofenceForm(g))}>Edit</button>
            <button type="button" style={ghost} aria-label={`${g.active ? "Switch off" : "Switch on"} ${g.name}`} onClick={() => toggle.mutate(g)}>{g.active ? "Switch off" : "Switch on"}</button>
            {confirmId === g.id
              ? <><button type="button" style={{ ...ghost, color: "var(--hf-danger-text)", fontWeight: 700 }} aria-label={`Confirm delete ${g.name}`} onClick={() => remove.mutate(g.id)}>Confirm delete</button><button type="button" style={ghost} onClick={() => setConfirmId(null)}>Keep</button></>
              : <button type="button" style={ghost} aria-label={`Delete ${g.name}`} onClick={() => setConfirmId(g.id)}>Delete</button>}
          </span>}
        </li>))}</ul>
    </div>
  )
}
