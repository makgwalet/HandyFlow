// src/pages/agriculture/AgTrendsTab.tsx
//
// Month-by-month costs, harvest and livestock events, with last-30-days comparisons, for all farms or one. Everything comes from
// dated records. Herd size over time, revenue, labour and equipment cost are NOT recorded, so there is no head-count line and no
// margin trend; the server's `limitations` say so and are shown at the bottom.
import { useState } from "react"
import { useSearchParams } from "react-router-dom"
import { BarChart, Bar, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"
import { useActiveFarms } from "./agCrops.api"
import { useAgTrends } from "./agTrends.api"
import { ComparisonCard } from "./AgTrendParts"
import { costChartData, hasCosts, hasHarvest, hasLivestockEvents, livestockChartData, monthLabel, tonnesChartData } from "./agTrends.logic"
import { fmtNum } from "./agDashboard.logic"
import { card, fmtMoney, inp, lbl } from "./constants"

const COST_COLOURS: Record<string, string> = { Seed: "var(--hf-success)", Inputs: "var(--hf-info)", Feed: "var(--hf-warning)", Health: "var(--hf-danger)", "Animal purchases": "var(--hf-violet)" }
const MONTH_CHOICES = [6, 12, 24]
const title: React.CSSProperties = { fontSize: 13, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 12px" }
const muted: React.CSSProperties = { fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }
const axis = { fontSize: 11, fill: "var(--hf-text-muted)" }

function Panel({ label, empty, children }: { label: string; empty: string | null; children: React.ReactNode }) {
  return (
    <section style={card} aria-label={label}>
      <h3 style={title}>{label}</h3>
      {empty ? <p style={{ ...muted, padding: "26px 0", textAlign: "center" }}>{empty}</p> : <div style={{ width: "100%", height: 250 }}>{children}</div>}
    </section>
  )
}

export default function AgTrendsTab() {
  const [params, setParams] = useSearchParams()
  const [months, setMonths] = useState(12)
  const { data: farms = [] } = useActiveFarms()
  const farmParam = params.get("farm")
  const farmId = farms.some(f => f.id === farmParam) ? farmParam : null          // an unknown id falls back to all farms
  const { data, isLoading, isError, refetch } = useAgTrends(farmId, months)

  const setFarm = (id: string) => setParams(prev => { const next = new URLSearchParams(prev); if (id) next.set("farm", id); else next.delete("farm"); return next }, { replace: true })

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap", marginBottom: 18 }}>
        <label htmlFor="tr-farm" style={{ ...lbl, margin: 0 }}>Farm</label>
        <select id="tr-farm" style={{ ...inp, width: "auto", minWidth: 200 }} value={farmId ?? ""} onChange={e => setFarm(e.target.value)}>
          <option value="">All farms</option>{farms.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}
        </select>
        <label htmlFor="tr-months" style={{ ...lbl, margin: 0 }}>Period</label>
        <select id="tr-months" style={{ ...inp, width: "auto" }} value={months} onChange={e => setMonths(Number(e.target.value))}>
          {MONTH_CHOICES.map(m => <option key={m} value={m}>Last {m} months</option>)}
        </select>
      </div>

      {isLoading ? <p style={muted}>Loading trends…</p> : isError || !data ? (
        <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load the trends. <button type="button" onClick={() => refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>
      ) : (
        <>
          <p style={{ ...muted, marginBottom: 10 }}>Last 30 days compared with the 30 days before ({data.comparisons[0]?.currentFrom} to {data.comparisons[0]?.currentTo}).</p>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 12, marginBottom: 18 }}>
            {data.comparisons.map(c => <ComparisonCard key={c.key} c={c} />)}
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(340px, 1fr))", gap: 14, marginBottom: 14 }}>
            <Panel label="Harvested (tonnes)" empty={hasHarvest(data) ? null : "No harvests recorded in this period."}>
              <ResponsiveContainer>
                <BarChart data={tonnesChartData(data)}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--hf-border-subtle)" />
                  <XAxis dataKey="name" tick={axis} /><YAxis tick={axis} width={44} />
                  <Tooltip formatter={(v) => [`${fmtNum(Number(v), 2)} t`, "Harvested"]} />
                  <Bar dataKey="Tonnes" fill="var(--hf-accent)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </Panel>
            <Panel label="Costs by month" empty={hasCosts(data) ? null : "No costs recorded in this period."}>
              <ResponsiveContainer>
                <BarChart data={costChartData(data)}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--hf-border-subtle)" />
                  <XAxis dataKey="name" tick={axis} /><YAxis tick={axis} width={52} />
                  <Tooltip formatter={(v, n) => [fmtMoney(Number(v)), n]} /><Legend wrapperStyle={{ fontSize: 11 }} />
                  {Object.entries(COST_COLOURS).map(([k, c]) => <Bar key={k} dataKey={k} stackId="cost" fill={c} />)}
                </BarChart>
              </ResponsiveContainer>
            </Panel>
            <Panel label="Births and deaths" empty={hasLivestockEvents(data) ? null : "No births or deaths recorded in this period."}>
              <ResponsiveContainer>
                <BarChart data={livestockChartData(data)}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--hf-border-subtle)" />
                  <XAxis dataKey="name" tick={axis} /><YAxis tick={axis} width={36} allowDecimals={false} />
                  <Tooltip /><Legend wrapperStyle={{ fontSize: 11 }} />
                  <Bar dataKey="Births" fill="var(--hf-success)" radius={[4, 4, 0, 0]} /><Bar dataKey="Deaths" fill="var(--hf-danger)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </Panel>
          </div>
          <p style={{ ...muted, fontSize: 11.5, marginBottom: 14 }}>* The current month, so far ({monthLabel(data.months[data.months.length - 1]?.key ?? "")} to date).</p>

          <section style={{ ...card, marginBottom: 14, overflowX: "auto" }} aria-label="Harvest by crop">
            <h3 style={title}>Harvest by crop</h3>
            {data.production.byCrop.length === 0 ? <p style={muted}>No harvests recorded in this period.</p> : (
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
                <thead><tr style={{ textAlign: "left", color: "var(--hf-text-faint)", fontSize: 11, textTransform: "uppercase", letterSpacing: 0.4 }}>
                  {["Crop", "Unit", "Total", "Latest month", ""].map(h => <th key={h} style={{ padding: "8px 10px", fontWeight: 700 }}>{h}</th>)}
                </tr></thead>
                <tbody>
                  {data.production.byCrop.map(c => (
                    <tr key={c.cropTypeId} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                      <td style={{ padding: "9px 10px", fontWeight: 700 }}>{c.cropName}</td><td style={{ padding: "9px 10px" }}>{c.unit}</td>
                      <td style={{ padding: "9px 10px" }}>{fmtNum(c.total, 2)}</td><td style={{ padding: "9px 10px" }}>{fmtNum(c.values[c.values.length - 1], 2)}</td>
                      <td style={{ padding: "9px 10px", color: "var(--hf-warning-text)" }}>{c.excludedRecords > 0 ? `${c.excludedRecords} harvest(s) in a unit that can't convert are not counted` : ""}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <p style={{ ...muted, fontSize: 11.5, marginTop: 10 }}>Each crop is shown in its own unit, so crops are never added together across units.</p>
          </section>

          <section style={{ ...card, background: "var(--hf-surface-muted)" }} aria-label="What is not shown">
            <h3 style={title}>What these trends can't tell you</h3>
            <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12.5, color: "var(--hf-text-muted)", lineHeight: 1.7 }}>
              {data.limitations.map(l => <li key={l}>{l}</li>)}
            </ul>
          </section>
        </>
      )}
    </div>
  )
}
