// src/pages/agriculture/AgFarmMap.tsx
// Farm locations on a map (react-leaflet with OpenStreetMap tiles, like the security Live Map). Loaded lazily by the dashboard
// so Leaflet is only downloaded when the dashboard is opened. Only farms with a GPS position are shown.
import { useEffect } from "react"
import { Link } from "react-router-dom"
import { MapContainer, TileLayer, CircleMarker, Popup, useMap } from "react-leaflet"
import L from "leaflet"
import "leaflet/dist/leaflet.css"
import type { DashboardFarm } from "./agDashboard.api"
import { AG_ACCENT_TEXT } from "./constants"

type Located = DashboardFarm & { latitude: number; longitude: number }

function FitToFarms({ farms }: { farms: Located[] }) {
  const map = useMap()
  useEffect(() => {
    if (farms.length > 1) map.fitBounds(L.latLngBounds(farms.map(f => [f.latitude, f.longitude] as [number, number])), { padding: [40, 40] })
  }, [farms, map])
  return null
}

export default function AgFarmMap({ farms }: { farms: DashboardFarm[] }) {
  const located = farms.filter((f): f is Located => f.latitude != null && f.longitude != null)
  if (located.length === 0) return null
  return (
    <MapContainer center={[located[0].latitude, located[0].longitude]} zoom={located.length === 1 ? 11 : 5} style={{ height: 320, width: "100%", borderRadius: 10 }} scrollWheelZoom={false}>
      <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      <FitToFarms farms={located} />
      {located.map(f => (
        <CircleMarker key={f.id} center={[f.latitude, f.longitude]} radius={f.urgentCount > 0 ? 11 : 8}
          pathOptions={{ color: f.urgentCount > 0 ? "var(--hf-danger)" : "var(--hf-accent)", fillColor: f.urgentCount > 0 ? "var(--hf-danger)" : "var(--hf-accent)", fillOpacity: 0.6 }}>
          <Popup>
            <strong>{f.name}</strong><br />
            {[f.province, f.totalHectares != null ? `${f.totalHectares} ha` : null].filter(Boolean).join(" · ")}<br />
            <Link to={`/agriculture/crop-cycles?farm=${f.id}`} style={{ color: AG_ACCENT_TEXT, fontWeight: 600 }}>Open crop cycles</Link>
          </Popup>
        </CircleMarker>
      ))}
    </MapContainer>
  )
}
