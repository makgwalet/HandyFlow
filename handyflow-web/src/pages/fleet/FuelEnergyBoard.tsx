// src/pages/fleet/FuelEnergyBoard.tsx
//
// Fuel at a glance: the fleet's spend, litres, consumption and cost per km for a period, a six-month trend, which vehicles spend and burn the most,
// the most serious fuel warnings, and where fuel is cheapest. Detail stays in Fuel Log and Fuel Analytics. Electric charging is not recorded in Fleet.
import { useState } from "react"
import { useQuery, useQueries } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { BarRow, Columns, Meter } from "./FleetBars"
import { TONE_COLOUR, type Tone } from "./fleetTones"
import { consumptionText, costPerKmText, fleetTotals, periodRange, premiumText, type FuelRow } from "./fuelAnalytics.logic"
import { cheapestStations, fleetCostPerKm, monthRanges, spendBars, thirstiestBars, topFuelAlerts, type FuelAlertRow, type StationRow } from "./fleetBoards.logic"

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const list = <T,>(r: { data: unknown }): T[] => { const p = unwrap<T[] | { content?: T[] } | null>(r); return (Array.isArray(p) ? p : p?.content) ?? [] }
const R = (n: number) => `R ${Math.round(n).toLocaleString("en-ZA")}`
const L = (n: number) => `${Math.round(n).toLocaleString("en-ZA")} L`
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14 }
const muted: React.CSSProperties = { color: "var(--hf-text-muted)", fontSize: 12.5 }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const link: React.CSSProperties = { background: "none", border: "none", padding: 0, color: "var(--hf-primary-text)", fontSize: 12, fontWeight: 600, cursor: "pointer" }
const Head = ({ title, to, label, go }: { title: string; to: string; label: string; go?: (s: string) => void }) => (
  <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}><strong style={{ fontSize: 13 }}>{title}</strong>{go && <button type="button" style={link} onClick={() => go(to)}>{label}</button>}</div>)
const Tile = ({ label, value, tone }: { label: string; value: string; tone?: Tone }) => (
  <div aria-label={label} style={{ flex: "1 1 150px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 14px" }}>
    <div style={{ fontSize: 21, fontWeight: 800, color: tone ? TONE_COLOUR[tone] : "var(--hf-text)" }}>{value}</div><div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{label}</div></div>)

export default function FuelEnergyBoard({ onNavigate }: { onNavigate?: (section: string) => void }) {
  const [period, setPeriod] = useState<"month" | "30" | "90">("30")
  const { from, to } = periodRange(period), q = `?from=${from}&to=${to}`
  const board = useQuery<FuelRow[]>({ queryKey: ["fleet", "fe", "vehicles", period], queryFn: async () => list<FuelRow>(await apiClient.get(`/api/v1/fleet/fuel-analytics/vehicles${q}`)) })
  const alerts = useQuery<FuelAlertRow[]>({ queryKey: ["fleet", "fe", "alerts", period], queryFn: async () => list<FuelAlertRow>(await apiClient.get(`/api/v1/fleet/fuel-analytics/alerts${q}`)) })
  const stations = useQuery<StationRow[]>({ queryKey: ["fleet", "fe", "stations", period], queryFn: async () => list<StationRow>(await apiClient.get(`/api/v1/fleet/fuel-analytics/stations${q}`)) })
  const months = monthRanges()
  const trend = useQueries({ queries: months.map(m => ({ queryKey: ["fleet", "fe", "month", m.key], queryFn: async () => fleetTotals(list<FuelRow>(await apiClient.get(`/api/v1/fleet/fuel-analytics/vehicles?from=${m.from}&to=${m.to}`))) })) })
  const rows = board.data ?? [], t = fleetTotals(rows), cpk = fleetCostPerKm(rows)
  const trendItems = (pick: (x: ReturnType<typeof fleetTotals>) => number) => months.map((m, i) => ({ key: m.key, label: m.label, value: trend[i].data ? pick(trend[i].data as ReturnType<typeof fleetTotals>) : 0 }))
  const trendLoading = trend.some(x => x.isLoading)
  return (
    <div>
      <div style={{ marginBottom: 12 }}><select aria-label="Period" style={field} value={period} onChange={e => setPeriod(e.target.value as typeof period)}><option value="month">This month</option><option value="30">Last 30 days</option><option value="90">Last 90 days</option></select></div>
      {board.isError && <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Couldn't load fuel analytics.</p>}
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 14 }}>
        <Tile label="Fuel spend" value={R(t.spend)} /><Tile label="Litres" value={L(t.litres)} /><Tile label="Fleet consumption" value={consumptionText(t.l100)} />
        <Tile label="Fuel cost per km" value={costPerKmText(cpk)} /><Tile label="Flags to review" value={String(t.flagged)} tone={t.flagged ? "bad" : "ok"} />
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(340px, 1fr))", gap: 12 }}>
        <div style={card}><Head title="Fuel spend, last 6 months" to="fuel-analytics" label="Fuel analytics" go={onNavigate} />{trendLoading ? <p>Loading…</p> : <Columns label="Fuel spend by month" items={trendItems(x => x.spend)} format={R} />}</div>
        <div style={card}><Head title="Litres, last 6 months" to="fuel" label="Fuel log" go={onNavigate} />{trendLoading ? <p>Loading…</p> : <Columns label="Litres by month" items={trendItems(x => x.litres)} format={L} />}</div>
        <div style={card}><Head title="Biggest spenders" to="fuel-analytics" label="All vehicles" go={onNavigate} />
          {board.isLoading ? <p>Loading…</p> : spendBars(rows).length === 0 ? <p style={muted}>No fuel recorded in this period.</p> : spendBars(rows).map(b => <BarRow key={b.id} name={b.registration} fraction={b.fraction} valueText={R(b.spend)} />)}</div>
        <div style={card}><Head title="Thirstiest vehicles" to="vehicles" label="Vehicles" go={onNavigate} />
          {board.isLoading ? <p>Loading…</p> : thirstiestBars(rows).length === 0 ? <p style={muted}>No consumption could be measured yet. It needs two full-tank fills.</p> : thirstiestBars(rows).map(b => (
            <div key={b.id} aria-label={b.registration} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, marginBottom: 8 }}><strong style={{ fontSize: 12.5 }}>{b.registration}</strong><Meter label="Fuel" {...b.bar} /></div>))}</div>
        <div style={card}><Head title="Fuel warnings" to="fuel-analytics" label="See all" go={onNavigate} />
          {alerts.isLoading ? <p>Loading…</p> : topFuelAlerts(alerts.data ?? []).length === 0 ? <p style={muted}>No warnings in this period.</p> : <ul style={{ margin: 0, paddingLeft: 18 }}>{topFuelAlerts(alerts.data ?? []).map((a, i) => (
            <li key={`${a.registration}${a.date}${i}`} aria-label={`Warning ${a.registration}`} style={{ fontSize: 12.5, marginBottom: 3 }}><strong style={{ color: TONE_COLOUR[a.severity === "ALERT" ? "bad" : a.severity === "WARNING" ? "warn" : "none"] }}>{a.registration}</strong> <span style={muted}>· {a.message}</span></li>))}</ul>}</div>
        <div style={card}><Head title="Cheapest stations" to="fuel-analytics" label="All stations" go={onNavigate} />
          {stations.isLoading ? <p>Loading…</p> : cheapestStations(stations.data ?? []).length === 0 ? <p style={muted}>No station prices recorded in this period.</p> : cheapestStations(stations.data ?? []).map(s => (
            <div key={s.station + s.fuelType} aria-label={s.station} style={{ display: "flex", justifyContent: "space-between", fontSize: 12.5, marginBottom: 6 }}><span>{s.station} <span style={muted}>· {s.fillups} {s.fillups === 1 ? "fill" : "fills"}</span></span><span><strong>R {Number(s.avgPricePerLitre).toFixed(2)}/L</strong> <span style={muted}>{premiumText(s.premiumPercent)}</span></span></div>))}</div>
      </div>
      <p style={{ ...muted, marginTop: 12 }}>Fleet records fuel only. Electric charging is not tracked yet, so electric vehicles do not appear in these figures.</p>
    </div>)
}
