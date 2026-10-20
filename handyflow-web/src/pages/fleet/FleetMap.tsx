// src/pages/fleet/FleetMap.tsx
// The map itself (react-leaflet with OpenStreetMap tiles, as in Agriculture and Security). Loaded lazily so Leaflet is only
// downloaded when the live map is opened.
import { useEffect } from "react"
import { MapContainer, TileLayer, CircleMarker, Polyline, Popup, useMap } from "react-leaflet"
import L from "leaflet"
import "leaflet/dist/leaflet.css"
import { DEFAULT_CENTRE, LIVE_COLOUR, LIVE_LABEL, fixText, hasFix, speedText, type LivePosition } from "./liveMap.logic"

function Fit({ points }: { points: [number, number][] }) {
  const map = useMap()
  useEffect(() => { if (points.length > 1) map.fitBounds(L.latLngBounds(points), { padding: [40, 40] }); else if (points.length === 1) map.setView(points[0], 13) }, [points, map])
  return null
}

export default function FleetMap({ positions, selectedId, track, onSelect }: { positions: LivePosition[]; selectedId: string | null; track: [number, number][]; onSelect: (id: string) => void }) {
  const shown = positions.filter(hasFix)
  const fit: [number, number][] = track.length > 1 ? track : shown.map(p => [p.latitude, p.longitude])
  return (
    <MapContainer center={DEFAULT_CENTRE} zoom={5} style={{ height: 420, width: "100%", borderRadius: 10 }} scrollWheelZoom={false}>
      <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      <Fit points={fit} />
      {track.length > 1 && <Polyline positions={track} pathOptions={{ color: "#2563eb", weight: 4, opacity: 0.8 }} />}
      {shown.map(p => (
        <CircleMarker key={p.vehicleId} center={[p.latitude, p.longitude]} radius={p.vehicleId === selectedId ? 12 : 8}
          pathOptions={{ color: LIVE_COLOUR[p.status] ?? "#6b7280", fillColor: LIVE_COLOUR[p.status] ?? "#6b7280", fillOpacity: 0.75, weight: p.vehicleId === selectedId ? 4 : 2 }}
          eventHandlers={{ click: () => onSelect(p.vehicleId) }}>
          <Popup><strong>{p.registration}</strong><br />{LIVE_LABEL[p.status] ?? p.status} · {speedText(p.speedKph)}<br />{fixText(p)}</Popup>
        </CircleMarker>))}
    </MapContainer>
  )
}
