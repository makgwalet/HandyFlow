// src/pages/security/SiteDetailPage.tsx
//
// One site (/security/sites/:id): who is on it now, the next seven days of shifts, recent incidents, checkpoint scan
// health, and where it is on a map. Everything is a real record from the site overview; empty sections say so.
// A site without coordinates shows no map rather than a guessed location.
import { Link, useParams } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { MapContainer, TileLayer, Marker, Popup } from "react-leaflet"
import L from "leaflet"
import "leaflet/dist/leaflet.css"
import { AlertTriangle, CalendarClock, MapPin, QrCode, Route, Users } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import StatTile from "../../components/ui/StatTile"
import { PageHeader } from "../../components/ui/PageHeader"
import { GPS_LABEL, GPS_TONE, withPosition } from "./liveops.logic"
import { CONTRACT_LABEL, CONTRACT_TONE, checkpointState, contractNote, formatAddress, hasPosition, unscannedCount, type SiteOverview } from "./site.logic"
import { SEVERITY_TONE, STATUS_TONE, titleCase } from "./incident.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const h: React.CSSProperties = { fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, marginBottom: 8 }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const link: React.CSSProperties = { color: "var(--hf-primary-text)", fontWeight: 600, textDecoration: "none" }
const fmtT = (d: string) => new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })

const pin = (bg: string) => L.divIcon({
  className: "",
  html: `<div style="width:30px;height:30px;border-radius:50%;background:${bg};border:3px solid var(--hf-text-on-solid);box-shadow:0 4px 12px color-mix(in srgb, var(--hf-primary) 40%, transparent)"></div>`,
  iconSize: [30, 30], iconAnchor: [15, 15], popupAnchor: [0, -15],
})

export default function SiteDetailPage() {
  const { id = "" } = useParams()
  const { data, isLoading, error } = useQuery<SiteOverview>({
    queryKey: ["site-overview", id],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/sites/${id}/overview`); return r.data?.data ?? r.data },
    refetchInterval: 30000,
  })
  const crumbs = (t: string) => [{ label: "Security", to: "/security/dashboard" }, { label: "Sites", to: "/security/sites" }, { label: t }]

  if (isLoading) return <div><PageHeader title="Site" icon={MapPin} breadcrumbs={crumbs("Site")} /><div style={muted}>Loading…</div></div>
  if (error || !data) return <div><PageHeader title="Site" icon={MapPin} breadcrumbs={crumbs("Site")} /><div role="alert" style={{ color: "var(--hf-danger-text)" }}>This site could not be loaded.</div></div>

  const { site, counts, onSite, recentIncidents, checkpoints, upcoming } = data
  const address = formatAddress(site.address)
  const plotted = withPosition(onSite)
  const mapped = hasPosition(site)
  const quiet = unscannedCount(checkpoints)

  return (
    <div>
      <PageHeader title={site.name} icon={MapPin} breadcrumbs={crumbs(site.name)} />

      <div style={{ ...card, marginBottom: 16, display: "flex", gap: 16, flexWrap: "wrap", justifyContent: "space-between" }}>
        <div>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 6 }}>
            <Chip tone={CONTRACT_TONE[site.contractStatus] ?? "neutral"}>{CONTRACT_LABEL[site.contractStatus] ?? site.contractStatus}</Chip>
            <span style={muted}>{contractNote(site.contractStatus, site.contractEnd)}</span>
            {site.requireSignedQr && <Chip tone="info" icon={<QrCode size={12} />}>Signed QR required</Chip>}
            {!site.active && <Chip tone="neutral">Inactive</Chip>}
          </div>
          <div style={{ fontSize: 13 }}>{address || <span style={muted}>No address on record</span>}</div>
          {site.terminationReason && <div style={{ ...muted, marginTop: 4 }}>Reason: {site.terminationReason}</div>}
        </div>
        <div style={{ fontSize: 13 }}>
          <div style={h}>Site contact</div>
          {site.contactName || site.contactPhone ? <div>{[site.contactName, site.contactPhone].filter(Boolean).join(" · ")}</div> : <span style={muted}>None on record</span>}
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 12, marginBottom: 16 }}>
        <StatTile label="On site now" value={counts.guardsOnSite} icon={<Users size={18} />} />
        <StatTile label="Shifts, next 7 days" value={counts.upcomingShifts7d} icon={<CalendarClock size={18} />} />
        <StatTile label="Open incidents" value={counts.openIncidents} icon={<AlertTriangle size={18} />} />
        <StatTile label="Patrol routes" value={counts.activePatrolRoutes} icon={<Route size={18} />} />
        <StatTile label="Checkpoints" value={counts.checkpoints} icon={<QrCode size={18} />} />
      </div>

      <div style={{ display: "flex", gap: 16, flexWrap: "wrap", alignItems: "flex-start", marginBottom: 16 }}>
        <div style={{ ...card, flex: "2 1 460px", padding: 0, overflow: "hidden" }}>
          {mapped ? (
            <MapContainer center={[Number(site.latitude), Number(site.longitude)]} zoom={16} style={{ height: 340, width: "100%" }}>
              <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
              <Marker position={[Number(site.latitude), Number(site.longitude)]} icon={pin("var(--hf-accent)")}><Popup>{site.name}</Popup></Marker>
              {plotted.map(g => (
                <Marker key={g.guardId} position={[g.latitude, g.longitude]} icon={pin(g.gpsState === "LIVE" ? "var(--hf-primary)" : "var(--hf-text-faint)")}>
                  <Popup>{g.guardName} · {GPS_LABEL[g.gpsState]}</Popup>
                </Marker>
              ))}
            </MapContainer>
          ) : (
            <div style={{ padding: 24, ...muted }}>This site has no coordinates, so it is not shown on a map. Add its latitude and longitude on the Sites screen.</div>
          )}
        </div>

        <div style={{ ...card, flex: "1 1 300px" }}>
          <div style={h}>On site now</div>
          {onSite.length === 0 ? <div style={muted}>No guard is on an active shift here.</div> : onSite.map(g => (
            <div key={g.shiftId} style={{ display: "flex", justifyContent: "space-between", gap: 8, padding: "8px 0", borderBottom: "1px solid var(--hf-border)" }}>
              <div>
                <Link to={`/security/guards/${g.guardId}`} style={link}>{g.guardName}</Link>
                <div style={muted}>until {new Date(g.shiftEnd).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })}{g.overrunning ? " (past end)" : ""}</div>
              </div>
              <Chip tone={GPS_TONE[g.gpsState] ?? "neutral"}>{GPS_LABEL[g.gpsState] ?? g.gpsState}</Chip>
            </div>
          ))}
        </div>
      </div>

      <div style={{ display: "flex", gap: 16, flexWrap: "wrap", alignItems: "flex-start" }}>
        <div style={{ ...card, flex: "1 1 320px" }}>
          <div style={h}>Next 7 days</div>
          {upcoming.length === 0 ? <div style={muted}>No shifts scheduled in the next 7 days.</div> : upcoming.map(s => (
            <div key={s.shiftId} style={{ padding: "6px 0", fontSize: 13 }}>
              <Link to={`/security/guards/${s.guardId}`} style={link}>{s.guardName}</Link>
              <span style={muted}> · {fmtT(s.startAt)} to {new Date(s.endAt).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })}</span>
            </div>
          ))}
          {counts.upcomingShifts7d > upcoming.length && <div style={{ ...muted, marginTop: 6 }}>Showing the first {upcoming.length} of {counts.upcomingShifts7d}.</div>}
        </div>

        <div style={{ ...card, flex: "1 1 320px" }}>
          <div style={h}>Recent incidents</div>
          {recentIncidents.length === 0 ? <div style={muted}>No incidents recorded at this site.</div> : recentIncidents.map(i => (
            <div key={i.id} style={{ padding: "6px 0", display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              <Link to={`/security/incidents/${i.id}`} style={link}>{i.title}</Link>
              <Chip tone={SEVERITY_TONE[i.severity] ?? "neutral"}>{titleCase(i.severity)}</Chip>
              <Chip tone={STATUS_TONE[i.status] ?? "neutral"}>{titleCase(i.status)}</Chip>
              <span style={muted}>{fmtT(i.reportedAt)}</span>
            </div>
          ))}
        </div>

        <div style={{ ...card, flex: "1 1 320px" }}>
          <div style={h}>Checkpoints{quiet > 0 ? ` · ${quiet} with no scans in 30 days` : ""}</div>
          {checkpoints.length === 0 ? <div style={muted}>No checkpoints set up for this site.</div> : checkpoints.map(c => {
            const st = checkpointState(c)
            return (
              <div key={c.id} style={{ display: "flex", justifyContent: "space-between", gap: 8, padding: "6px 0", fontSize: 13 }}>
                <div>{c.name}{c.lastScanAt && <div style={muted}>last scan {fmtT(c.lastScanAt)}</div>}</div>
                <Chip tone={st.tone}>{st.label}</Chip>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
