// src/pages/security/GateDashboardTab.tsx
//
// Gate dashboard: who is on site now across sites, how many entered and left today, and which entries the overstay
// scheduler has flagged. People are shown by name, company, host and vehicle; ID and phone numbers stay on the gate log.
// Real register entries only; "Overstayed" is the server's own status, not something decided on this screen.
import { useState } from "react"
import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { AlertTriangle, Car, DoorOpen, LogIn, LogOut, Users } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import StatTile from "../../components/ui/StatTile"
import { STATUS_LABEL, STATUS_TONE, filterOnSite, onSiteFor, sortGates, typeLabel, typesPresent, type GateDashboard } from "./gate.logic"

const sel: React.CSSProperties = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }
const th: React.CSSProperties = { textAlign: "left", padding: 10, fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4 }
const td: React.CSSProperties = { padding: 10, fontSize: 13, borderTop: "1px solid var(--hf-border)", verticalAlign: "top" }
const fmtT = (d: string) => new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })
const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : p?.content ?? [] }

export default function GateDashboardTab() {
  const [siteId, setSiteId] = useState("")
  const [type, setType] = useState("")
  const [search, setSearch] = useState("")
  const [overstayedOnly, setOverstayedOnly] = useState(false)
  const [vehiclesOnly, setVehiclesOnly] = useState(false)

  const { data: sites = [] } = useQuery<{ id: string; name: string }[]>({ queryKey: ["gate-sites"], queryFn: async () => unwrap(await apiClient.get("/api/v1/security/sites?size=200")) })
  const { data, isLoading, error, dataUpdatedAt } = useQuery<GateDashboard>({
    queryKey: ["gate-dashboard", siteId],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/gate/dashboard${siteId ? `?siteId=${siteId}` : ""}`); return r.data?.data ?? r.data },
    refetchInterval: 30000,
  })
  const c = data?.counts
  const rows = filterOnSite(data?.onSite ?? [], { type, search, overstayedOnly, vehiclesOnly })
  const now = dataUpdatedAt ? new Date(dataUpdatedAt) : new Date()

  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
        <select aria-label="Site" value={siteId} onChange={e => setSiteId(e.target.value)} style={sel}><option value="">All sites</option>{sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
        <select aria-label="Type" value={type} onChange={e => setType(e.target.value)} style={sel}><option value="">Any type</option>{typesPresent(c?.onSiteByType ?? {}).map(t => <option key={t} value={t}>{typeLabel(t)}</option>)}</select>
        <input aria-label="Search" placeholder="Search name, company, host or vehicle" value={search} onChange={e => setSearch(e.target.value)} style={{ ...sel, width: 260 }} />
        <label style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={overstayedOnly} onChange={e => setOverstayedOnly(e.target.checked)} />Overstayed only</label>
        <label style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={vehiclesOnly} onChange={e => setVehiclesOnly(e.target.checked)} />Vehicles only</label>
        <Link to="/security/gate-access" style={{ marginLeft: "auto", color: "var(--hf-primary-text)", fontWeight: 600, fontSize: 13, textDecoration: "none" }}>Open gate log and access points</Link>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 12, marginBottom: 14 }}>
        <StatTile label="On site now" value={c?.onSiteNow ?? "-"} icon={<Users size={18} />} />
        <StatTile label="Overstayed" value={c?.overstayed ?? "-"} hint="flagged by the overstay check" icon={<AlertTriangle size={18} />} tone={c?.overstayed ? "bad" : "neutral"} />
        <StatTile label="Vehicles on site" value={c?.vehiclesOnSite ?? "-"} hint="entries with a registration" icon={<Car size={18} />} />
        <StatTile label="Entered today" value={c?.enteredToday ?? "-"} icon={<LogIn size={18} />} />
        <StatTile label="Left today" value={c?.departedToday ?? "-"} icon={<LogOut size={18} />} />
      </div>

      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", marginBottom: 10 }}>The gate dashboard could not be loaded.</div>}

      {!siteId && (data?.bySite.length ?? 0) > 0 && (
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 14 }}>
          {data!.bySite.map(s => (
            <button key={s.siteId} onClick={() => setSiteId(s.siteId)} style={{ ...card, padding: "10px 14px", textAlign: "left", cursor: "pointer", minWidth: 180 }} aria-label={`Show ${s.siteName} only`}>
              <div style={{ fontWeight: 700, fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}><DoorOpen size={14} />{s.siteName}</div>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>{s.onSite} on site · {s.enteredToday} entered today</div>
            </button>
          ))}
        </div>
      )}

      {(data?.byGate?.length ?? 0) > 0 && (
        <div style={{ ...card, overflowX: "auto", marginBottom: 14 }}>
          <table aria-label="By gate" style={{ width: "100%", borderCollapse: "collapse", minWidth: 560 }}>
            <thead><tr><th style={th}>Gate</th><th style={th}>Site</th><th style={th}>On site</th><th style={th}>Overstayed</th><th style={th}>Entered today</th><th style={th}>Left today</th></tr></thead>
            <tbody>
              {sortGates(data!.byGate ?? []).map(g => (
                <tr key={g.accessPointId}>
                  <td style={{ ...td, fontWeight: 600 }}>{g.accessPointName}</td>
                  <td style={td}>{g.siteName ?? "-"}</td>
                  <td style={td}>{g.onSite}</td>
                  <td style={td}>{g.overstayed > 0 ? <Chip tone="bad">{g.overstayed}</Chip> : 0}</td>
                  <td style={td}>{g.enteredToday}</td>
                  <td style={td}>{g.departedToday}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div style={{ ...card, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 860 }}>
          <thead><tr><th style={th}>Person</th><th style={th}>Type</th><th style={th}>Site and gate</th><th style={th}>Host</th><th style={th}>Vehicle</th><th style={th}>Signed in</th><th style={th}>Status</th></tr></thead>
          <tbody>
            {isLoading && <tr><td colSpan={7} style={{ ...td, color: "var(--hf-text-muted)" }}>Loading…</td></tr>}
            {!isLoading && rows.length === 0 && <tr><td colSpan={7} style={{ ...td, color: "var(--hf-text-muted)" }}>{(data?.onSite.length ?? 0) === 0 ? "Nobody is signed in on site right now." : "No one on site matches these filters."}</td></tr>}
            {rows.map(r => (
              <tr key={r.id}>
                <td style={td}><div style={{ fontWeight: 600 }}>{r.personName}</div>{r.company && <div style={{ color: "var(--hf-text-muted)" }}>{r.company}</div>}</td>
                <td style={td}>{typeLabel(r.entryType)}</td>
                <td style={td}>{r.siteName ?? "-"}<div style={{ color: "var(--hf-text-muted)" }}>{r.accessPointName ?? ""}</div></td>
                <td style={td}>{r.hostName ?? "-"}</td>
                <td style={td}>{r.vehicleRegistration ?? "-"}</td>
                <td style={td}>{fmtT(r.loggedInAt)}<div style={{ color: "var(--hf-text-muted)" }}>{onSiteFor(r.loggedInAt, now)} on site</div></td>
                <td style={td}><Chip tone={STATUS_TONE[r.status] ?? "neutral"}>{STATUS_LABEL[r.status] ?? r.status}</Chip></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>
        {data?.onSiteTruncated ? "Showing the 200 longest-staying entries; the counts above include everyone. " : ""}
        "Today" is the South African calendar day. Oldest sign-in first. Refreshes every 30 seconds.
      </div>
    </div>
  )
}
