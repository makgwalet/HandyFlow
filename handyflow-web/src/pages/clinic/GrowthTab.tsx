// Growth charts. Measurements always show; curves and z-scores only come from an approved, active reference set.
import { useQuery } from "@tanstack/react-query"
import { AlertTriangle } from "lucide-react"
import { CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Scatter, Tooltip, XAxis, YAxis } from "recharts"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { ageLabel, chartRows, percentileText, sourceLine, zKey, zName, zText, type GrowthChart, type MeasureChart } from "./growthView"

const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)", padding: 16, marginBottom: 16 }
const th: React.CSSProperties = { textAlign: "left", fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 600, padding: "4px 8px" }
const td: React.CSSProperties = { fontSize: 13, padding: "4px 8px", borderTop: "1px solid var(--hf-border-subtle)" }

function MeasureCard({ m }: { m: MeasureChart }) {
  const rows = chartRows(m)
  const lines = m.curves?.zLines ?? []
  return (
    <section aria-label={m.label} style={card}>
      <div style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)", marginBottom: 6 }}>{m.label} <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>({m.unit})</span></div>
      {m.banner && (
        <div role="alert" style={{ display: "flex", gap: 8, alignItems: "center", padding: "8px 10px", marginBottom: 8, borderRadius: 8,
          border: "1px solid var(--hf-warning-border, var(--hf-border))", background: "var(--hf-warning-bg, var(--hf-surface-alt, transparent))", color: "var(--hf-warning-text, var(--hf-text))", fontSize: 12, fontWeight: 700 }}>
          <AlertTriangle size={14}/> {m.banner}</div>
      )}
      {m.measurements.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No {m.label.toLowerCase()} recorded for this patient.</div> : (
        <>
          <div style={{ width: "100%", height: 260 }}>
            <ResponsiveContainer>
              <ComposedChart data={rows} margin={{ top: 8, right: 16, bottom: 4, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--hf-border-subtle)"/>
                <XAxis type="number" dataKey="age" domain={["dataMin", "dataMax"]} tickFormatter={ageLabel} fontSize={11}/>
                <YAxis domain={["auto", "auto"]} fontSize={11}/>
                <Tooltip formatter={(v: any) => String(v)} labelFormatter={(a: any) => `Age ${ageLabel(Number(a))}`}/>
                {lines.length > 0 && <Legend/>}
                {lines.map(z => <Line key={z} type="monotone" dataKey={zKey(z)} name={zName(z)} dot={false} strokeWidth={z === 0 ? 2 : 1} stroke="var(--hf-text-muted)" strokeDasharray={z === 0 ? undefined : "4 3"} connectNulls/>)}
                <Line type="linear" dataKey="value" name="This child" stroke="var(--hf-primary)" strokeWidth={2} dot={{ r: 3 }} connectNulls isAnimationActive={false}/>
                <Scatter dataKey="value" fill="var(--hf-primary)" legendType="none"/>
              </ComposedChart>
            </ResponsiveContainer>
          </div>
          <div style={{ fontSize: 11, color: "var(--hf-text-muted)", margin: "4px 0 8px" }}>{sourceLine(m)}</div>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Date</th><th style={th}>Age</th><th style={th}>{m.label}</th><th style={th}>Z-score</th><th style={th}>Percentile</th></tr></thead>
            <tbody>{[...m.measurements].reverse().map(x => (
              <tr key={x.observationId}>
                <td style={td}>{new Date(x.takenAt).toLocaleDateString("en-ZA", { timeZone: "Africa/Johannesburg" })}</td>
                <td style={td}>{ageLabel(x.ageMonths)}</td>
                <td style={td}>{String(x.value)} {m.unit}</td>
                <td style={td}>{zText(x.zScore)}</td>
                <td style={td}>{percentileText(x.percentile)}</td>
              </tr>))}</tbody>
          </table>
        </>
      )}
    </section>
  )
}

export default function GrowthTab({ patientId }: { patientId: string }) {
  const canRead = usePermission("CLINIC_GROWTH_READ")
  const q = useQuery<GrowthChart>({ queryKey: ["growth", patientId], enabled: canRead, retry: false,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/clinic/patients/${patientId}/growth`); return (r?.data?.data ?? r?.data) as GrowthChart } })
  if (!canRead) return <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>You do not have permission to view growth charts.</div>
  if (q.isLoading) return <div style={{ fontSize: 13 }}>Loading growth…</div>
  if (q.isError || !q.data) return <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>Could not load the growth chart.</div>
  const g = q.data
  return (
    <div>
      {g.notes.map(n => <div key={n} role="note" style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 8 }}>{n}</div>)}
      {g.measures.map(m => <MeasureCard key={m.code} m={m}/>)}
    </div>
  )
}
