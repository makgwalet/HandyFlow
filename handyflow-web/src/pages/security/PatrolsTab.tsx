// src/pages/security/PatrolsTab.tsx
//
// Patrols: every patrol round that was due in the chosen period, across shifts and sites, with how many checkpoints were
// scanned. Missed and partial rounds that nobody has acknowledged are called out. Real rounds only: the list is what the
// system generated for shifts and what scans were counted towards them.
import { useState } from "react"
import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { AlertTriangle, CheckCircle2, Footprints, Route as RouteIcon, TimerOff } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import StatTile from "../../components/ui/StatTile"
import { clock } from "./schedule.logic"
import { PRESETS, STATUS_FILTERS, STATUS_LABEL, STATUS_TONE, filterRounds, needsAttention, presetWindow, progress, summarise, type PatrolRound } from "./patrol.logic"

const sel: React.CSSProperties = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }
const th: React.CSSProperties = { textAlign: "left", padding: 10, fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4 }
const td: React.CSSProperties = { padding: 10, fontSize: 13, borderTop: "1px solid var(--hf-border)", verticalAlign: "top" }
const fmtDay = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { weekday: "short", day: "numeric", month: "short", timeZone: "Africa/Johannesburg" })
const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : p?.content ?? [] }

export default function PatrolsTab() {
  const [preset, setPreset] = useState<string>("7")
  const [siteId, setSiteId] = useState("")
  const [status, setStatus] = useState("")
  const [search, setSearch] = useState("")
  const [attention, setAttention] = useState(false)
  const days = PRESETS.find(p => p.id === preset)?.days ?? 7

  const { data: sites = [] } = useQuery<{ id: string; name: string }[]>({ queryKey: ["patrol-sites"], queryFn: async () => unwrap(await apiClient.get("/api/v1/security/sites?size=200")) })
  const { data, isLoading, error } = useQuery<PatrolRound[]>({
    queryKey: ["patrols", preset, siteId, status],
    queryFn: async () => {
      const w = presetWindow(days)
      const q = new URLSearchParams({ from: w.from.toISOString(), to: w.to.toISOString() })
      if (siteId) q.set("siteId", siteId)
      if (status) q.set("status", status)
      return unwrap(await apiClient.get(`/api/v1/security/patrols?${q}`))
    },
    refetchInterval: 60000,
  })
  const all = data ?? []
  const sum = summarise(all)
  const shown = filterRounds(all, { search, attention })

  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
        <select aria-label="Period" value={preset} onChange={e => setPreset(e.target.value)} style={sel}>{PRESETS.map(p => <option key={p.id} value={p.id}>{p.label}</option>)}</select>
        <select aria-label="Site" value={siteId} onChange={e => setSiteId(e.target.value)} style={sel}><option value="">All sites</option>{sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
        <select aria-label="Status" value={status} onChange={e => setStatus(e.target.value)} style={sel}>{STATUS_FILTERS.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}</select>
        <input aria-label="Search" placeholder="Search guard, site or route" value={search} onChange={e => setSearch(e.target.value)} style={{ ...sel, width: 220 }} />
        <label style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={attention} onChange={e => setAttention(e.target.checked)} />Needs attention only</label>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 12, marginBottom: 14 }}>
        <StatTile label="Rounds in period" value={sum.total} icon={<Footprints size={18} />} />
        <StatTile label="Completion rate" value={sum.completionRate === null ? "-" : `${sum.completionRate}%`} hint={sum.completionRate === null ? "Nothing has finished yet" : "of finished rounds"} icon={<CheckCircle2 size={18} />} tone={sum.completionRate !== null && sum.completionRate < 80 ? "warn" : "neutral"} />
        <StatTile label="Missed" value={sum.missed} icon={<TimerOff size={18} />} tone={sum.missed ? "bad" : "neutral"} />
        <StatTile label="Needs attention" value={sum.needAttention} hint="missed or partial, not acknowledged" icon={<AlertTriangle size={18} />} tone={sum.needAttention ? "warn" : "neutral"} />
        <StatTile label="Off schedule" value={sum.offSchedule} icon={<RouteIcon size={18} />} tone={sum.offSchedule ? "warn" : "neutral"} />
      </div>

      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", marginBottom: 10 }}>The patrols could not be loaded.</div>}

      <div style={{ ...card, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 860 }}>
          <thead><tr><th style={th}>Due</th><th style={th}>Site and route</th><th style={th}>Guard</th><th style={th}>Round</th><th style={th}>Checkpoints</th><th style={th}>Status</th></tr></thead>
          <tbody>
            {isLoading && <tr><td colSpan={6} style={{ ...td, color: "var(--hf-text-muted)" }}>Loading…</td></tr>}
            {!isLoading && shown.length === 0 && <tr><td colSpan={6} style={{ ...td, color: "var(--hf-text-muted)" }}>{all.length === 0 ? "No patrol rounds were due in this period. Rounds are created when a guard starts a shift at a site that has a patrol route." : "No rounds match these filters."}</td></tr>}
            {shown.map(r => (
              <tr key={r.id}>
                <td style={td}>{r.expectedStartAt ? <>{fmtDay(r.expectedStartAt)}<div style={{ color: "var(--hf-text-muted)" }}>{clock(r.expectedStartAt)}{r.expectedEndAt ? ` to ${clock(r.expectedEndAt)}` : ""}</div></> : "-"}</td>
                <td style={td}>{r.siteName ?? "-"}<div style={{ color: "var(--hf-text-muted)" }}>{r.routeName ?? "No route"}</div></td>
                <td style={td}>{r.guardName ?? "-"}</td>
                <td style={td}><Link to={`/security/patrols/${r.id}`} style={{ color: "var(--hf-primary-text)", fontWeight: 600, textDecoration: "none" }}>Round {r.roundNumber}</Link></td>
                <td style={td}>{r.checkpointsScanned} of {r.checkpointsExpected}<div style={{ height: 4, background: "var(--hf-surface-sunken)", borderRadius: 4, marginTop: 4, width: 90 }}><div style={{ height: 4, borderRadius: 4, width: `${progress(r)}%`, background: r.status === "MISSED" ? "var(--hf-danger-text)" : "var(--hf-accent)" }} /></div></td>
                <td style={td}>
                  <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                    <Chip tone={STATUS_TONE[r.status] ?? "neutral"}>{STATUS_LABEL[r.status] ?? r.status}</Chip>
                    {r.offSchedule && <Chip tone="warn" title={r.offScheduleReason ?? undefined}>Off schedule</Chip>}
                    {needsAttention(r) && <Chip tone="warn">Needs attention</Chip>}
                    {r.acknowledged && <Chip tone="neutral">Acknowledged</Chip>}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>
        A scan counts towards the shift's earliest unfinished round whose route includes that checkpoint; scanning the same checkpoint again in the same round does not count twice. Times are South African time. Showing up to 500 rounds.
      </div>
    </div>
  )
}
