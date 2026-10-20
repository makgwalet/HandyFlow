// src/pages/fleet/GeofenceMap.tsx
// The drawing surface (react-leaflet, lazy loaded): saved fences in grey, the one being drawn in blue with draggable corners.
// Clicks and drags are handed to the logic file; this component holds no rules.
import { useEffect, useRef } from "react"
import { MapContainer, TileLayer, Circle, Polygon, Marker, Polyline, Tooltip, useMap, useMapEvents } from "react-leaflet"
import L from "leaflet"
import "leaflet/dist/leaflet.css"
import { DEFAULT_CENTRE } from "./liveMap.logic"
import { focusPoints, type Geofence, type GeofenceForm, type LatLng } from "./geofences.logic"

const corner = L.divIcon({ className: "", html: '<div style="width:14px;height:14px;border-radius:50%;background:#2563eb;border:2px solid #fff;box-shadow:0 0 3px rgba(0,0,0,.5)"></div>', iconSize: [14, 14], iconAnchor: [7, 7] })
const GREY = { color: "#6b7280", weight: 2, fillOpacity: 0.12 }
const BLUE = { color: "#2563eb", weight: 3, fillOpacity: 0.2 }

function Clicks({ onClick }: { onClick: (p: LatLng) => void }) { useMapEvents({ click: e => onClick([e.latlng.lat, e.latlng.lng]) }); return null }
function Fit({ points, token }: { points: LatLng[]; token: string }) {
  const map = useMap()
  const fitted = useRef<string | null>(null)   // refit only when what is being drawn changes, not on every corner added
  useEffect(() => {
    if (fitted.current === token) return
    fitted.current = token
    if (points.length > 1) map.fitBounds(L.latLngBounds(points), { padding: [40, 40], maxZoom: 16 }); else if (points.length === 1) map.setView(points[0], 15)
  }, [token, points, map])
  return null
}

export default function GeofenceMap({ fences, draft, onClick, onMove, onRemove }: { fences: Geofence[]; draft: GeofenceForm | null; onClick: (p: LatLng) => void; onMove: (i: number, p: LatLng) => void; onRemove: (i: number) => void }) {
  const others = fences.filter(g => g.id !== draft?.id)
  const radius = Number(draft?.radiusMeters)
  return (
    <MapContainer center={DEFAULT_CENTRE} zoom={5} style={{ height: 460, width: "100%", borderRadius: 10, cursor: draft ? "crosshair" : undefined }} scrollWheelZoom>
      <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      <Fit points={focusPoints(draft, fences)} token={draft ? `${draft.id ?? "new"}:${draft.shape}` : "list"} />
      {draft && <Clicks onClick={onClick} />}
      {others.map(g => g.shape === "POLYGON"
        ? <Polygon key={g.id} positions={(g.polygon ?? []).map(p => [p.latitude, p.longitude] as LatLng)} pathOptions={g.active ? GREY : { ...GREY, dashArray: "6" }}><Tooltip sticky>{g.name}</Tooltip></Polygon>
        : g.centerLatitude != null && g.centerLongitude != null && g.radiusMeters != null ? <Circle key={g.id} center={[g.centerLatitude, g.centerLongitude]} radius={g.radiusMeters} pathOptions={g.active ? GREY : { ...GREY, dashArray: "6" }}><Tooltip sticky>{g.name}</Tooltip></Circle> : null)}
      {draft?.shape === "CIRCLE" && draft.centre && <>
        {Number.isFinite(radius) && radius > 0 && <Circle center={draft.centre} radius={radius} pathOptions={BLUE} />}
        <Marker position={draft.centre} icon={corner} draggable eventHandlers={{ dragend: e => { const { lat, lng } = (e.target as L.Marker).getLatLng(); onMove(0, [lat, lng]) } }} /></>}
      {draft?.shape === "POLYGON" && <>
        {draft.polygon.length >= 3 ? <Polygon positions={draft.polygon} pathOptions={BLUE} /> : draft.polygon.length === 2 ? <Polyline positions={draft.polygon} pathOptions={BLUE} /> : null}
        {draft.polygon.map((p, i) => (
          <Marker key={i} position={p} icon={corner} draggable eventHandlers={{ dragend: e => { const { lat, lng } = (e.target as L.Marker).getLatLng(); onMove(i, [lat, lng]) }, contextmenu: () => onRemove(i) }}>
            <Tooltip direction="top" offset={[0, -8]}>Corner {i + 1}: drag to move, right-click to remove</Tooltip></Marker>))}</>}
    </MapContainer>
  )
}
