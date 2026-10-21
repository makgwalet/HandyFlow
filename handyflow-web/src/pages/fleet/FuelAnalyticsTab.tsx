// src/pages/fleet/FuelAnalyticsTab.tsx
//
// Fuel analytics: the fleet board (consumption, spend, cost per km, deviation from each vehicle's own baseline), the alert feed
// (abnormal consumption and possible theft indicators: reasons to look, not proof) and spend by station.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Pager, SearchBox } from "./ListControls"
import { useListView } from "./listKit"
import { consumptionText, costPerKmText, deviationText, deviationTone, fleetTotals, periodRange, premiumText, type FuelRow } from "./fuelAnalytics.logic"

interface Alert { kind: string; type: string; severity: string; registration: string; date: string; message: string }
interface Station { station: string; fuelType: string; fillups: number; litres: number; totalSpend: number; avgPricePerLitre: number; premiumPercent: number | null }

const list = <T,>(r: { data: unknown }): T[] => { const o = r.data as { data?: unknown }; const p = (o && typeof o === "object" && "data" in o ? o.data : r.data) as T[] | { content?: T[] }; return (Array.isArray(p) ? p : p?.content) ?? [] }
const R = (n: number) => `R ${Math.round(n).toLocaleString("en-ZA")}`
const TONE = { ok: "var(--hf-success-text-strong)", warn: "var(--hf-warning-text)", bad: "var(--hf-danger-text)", none: "var(--hf-text-faint)" }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, textTransform: "uppercase", color: "var(--hf-text-faint)" }
const td: React.CSSProperties = { padding: "9px 10px", fontSize: 12.5 }
const tile: React.CSSProperties = { background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 14px", minWidth: 140 }

export default function FuelAnalyticsTab() {
  const [period, setPeriod] = useState<"month" | "30" | "90">("month")
  const [view, setView] = useState<"board" | "alerts" | "stations">("board")
  const { from, to } = periodRange(period)
  const q = `?from=${from}&to=${to}`
  const board = useQuery<FuelRow[]>({ queryKey: ["fleet", "fuel-board", period], queryFn: async () => list<FuelRow>(await apiClient.get(`/api/v1/fleet/fuel-analytics/vehicles${q}`)) })
  const alerts = useQuery<Alert[]>({ queryKey: ["fleet", "fuel-alerts", period], queryFn: async () => list<Alert>(await apiClient.get(`/api/v1/fleet/fuel-analytics/alerts${q}`)) })
  const stations = useQuery<Station[]>({ queryKey: ["fleet", "fuel-stations", period], enabled: view === "stations", queryFn: async () => list<Station>(await apiClient.get(`/api/v1/fleet/fuel-analytics/stations${q}`)) })
  const rows = board.data ?? []
  const boardView = useListView(rows, r => [r.registration, r.make, r.model].filter(Boolean).join(" "))
  const alertView = useListView(alerts.data ?? [], a => `${a.registration} ${a.type.replace(/_/g, " ")} ${a.message}`)
  const stationView = useListView(stations.data ?? [], s => `${s.station} ${s.fuelType}`)
  const t = fleetTotals(rows)
  return (
    <div>
      <div style={{ display: "flex", gap: 10, marginBottom: 12, alignItems: "center", flexWrap: "wrap" }}>
        <select aria-label="Period" style={field} value={period} onChange={e => setPeriod(e.target.value as typeof period)}><option value="month">This month</option><option value="30">Last 30 days</option><option value="90">Last 90 days</option></select>
        {(["board", "alerts", "stations"] as const).map(v => (
          <button key={v} type="button" aria-pressed={view === v} onClick={() => setView(v)} style={{ ...field, cursor: "pointer", fontWeight: view === v ? 800 : 500 }}>
            {v === "board" ? "Vehicles" : v === "alerts" ? `Alerts${alerts.data?.length ? ` (${alerts.data.length})` : ""}` : "Stations"}</button>))}
      </div>
      {view === "board" && <>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 12 }}>
          <div style={tile}><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>LITRES</div><strong aria-label="Total litres">{Math.round(t.litres).toLocaleString("en-ZA")} L</strong></div>
          <div style={tile}><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>SPEND</div><strong aria-label="Total spend">{R(t.spend)}</strong></div>
          <div style={tile}><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>FLEET CONSUMPTION</div><strong aria-label="Fleet consumption">{consumptionText(t.l100)}</strong></div>
          <div style={tile}><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>FLAGS</div><strong style={{ color: t.flagged ? TONE.bad : undefined }} aria-label="Flags">{t.flagged}</strong></div>
        </div>
        {board.isLoading && <p>Loading…</p>}{board.isError && <div role="alert">Couldn't load fuel analytics.</div>}
        {!board.isLoading && !board.isError && rows.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No fuel recorded in this period.</p>}
        {rows.length > 0 && <div style={{ marginBottom: 10 }}><SearchBox value={boardView.search} onChange={boardView.setSearch} label="Search vehicles" placeholder="Search registration, make or model" /></div>}
        {rows.length > 0 && <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr>{["Vehicle", "Fill-ups", "Litres", "Spend", "Consumption", "Cost per km", "Against its usual", "Flags"].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
          <tbody>{boardView.paged.rows.map(r => (
            <tr key={r.vehicleId} aria-label={r.registration} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
              <td style={td}><strong>{r.registration}</strong><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{[r.make, r.model].filter(Boolean).join(" ")}</div></td>
              <td style={td}>{r.summary.fillups}</td><td style={td}>{Math.round(Number(r.summary.totalLitres))} L</td><td style={td}>{R(Number(r.summary.totalSpend))}</td>
              <td style={td}>{consumptionText(r.summary.litresPer100Km)}</td><td style={td}>{costPerKmText(r.summary.costPerKm)}</td>
              <td style={{ ...td, color: TONE[deviationTone(r.latestDeviationPercent)], fontWeight: 700 }}>{deviationText(r.latestDeviationPercent)}</td>
              <td style={td}>{r.summary.flaggedSegments + r.summary.indicators || "—"}</td>
            </tr>))}</tbody></table>}
        {rows.length > 0 && boardView.shown.length === 0 && <p role="status" style={{ color: "var(--hf-text-muted)" }}>No vehicles match.</p>}
        {rows.length > 0 && <Pager paged={boardView.paged} onPage={boardView.setPage} />}
      </>}
      {view === "alerts" && <>
        {alerts.isLoading && <p>Loading…</p>}
        {!alerts.isLoading && (alerts.data ?? []).length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No fuel alerts in this period.</p>}
        {(alerts.data ?? []).length > 0 && <div style={{ marginBottom: 10 }}><SearchBox value={alertView.search} onChange={alertView.setSearch} label="Search alerts" placeholder="Search registration or kind of alert" /></div>}
        <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>{alertView.paged.rows.map((a, i) => (
          <li key={i} style={{ padding: "8px 0", borderBottom: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
            <strong>{a.registration}</strong> · {new Date(a.date).toLocaleDateString("en-ZA")} · <span style={{ color: a.severity === "ALERT" ? TONE.bad : TONE.warn, fontWeight: 700 }}>{a.type.replace(/_/g, " ").toLowerCase()}</span>
            <div style={{ color: "var(--hf-text-muted)" }}>{a.message}</div></li>))}</ul>
        {(alerts.data ?? []).length > 0 && alertView.shown.length === 0 && <p role="status" style={{ color: "var(--hf-text-muted)" }}>No alerts match.</p>}
        {(alerts.data ?? []).length > 0 && <Pager paged={alertView.paged} onPage={alertView.setPage} />}
        <p style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>These are reasons to look, not proof of theft.</p>
      </>}
      {view === "stations" && <>
        {stations.isLoading && <p>Loading…</p>}
        {!stations.isLoading && (stations.data ?? []).length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No station spend in this period.</p>}
        {(stations.data ?? []).length > 0 && <div style={{ marginBottom: 10 }}><SearchBox value={stationView.search} onChange={stationView.setSearch} label="Search stations" placeholder="Search station or fuel" /></div>}
        {(stations.data ?? []).length > 0 && <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr>{["Station", "Fuel", "Fill-ups", "Litres", "Spend", "Per litre", "Against average"].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
          <tbody>{stationView.paged.rows.map(s => (
            <tr key={`${s.station}-${s.fuelType}`} aria-label={`${s.station} ${s.fuelType}`} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
              <td style={td}><strong>{s.station}</strong></td><td style={td}>{s.fuelType.toLowerCase()}</td><td style={td}>{s.fillups}</td><td style={td}>{Math.round(Number(s.litres))} L</td><td style={td}>{R(Number(s.totalSpend))}</td>
              <td style={td}>R {Number(s.avgPricePerLitre).toFixed(2)}</td><td style={{ ...td, color: (s.premiumPercent ?? 0) > 5 ? TONE.bad : undefined }}>{premiumText(s.premiumPercent)}</td></tr>))}</tbody></table>}
        {(stations.data ?? []).length > 0 && stationView.shown.length === 0 && <p role="status" style={{ color: "var(--hf-text-muted)" }}>No stations match.</p>}
        {(stations.data ?? []).length > 0 && <Pager paged={stationView.paged} onPage={stationView.setPage} />}
      </>}
    </div>
  )
}
