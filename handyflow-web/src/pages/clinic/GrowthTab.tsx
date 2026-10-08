// Growth charts. Measurements always show; curves and z-scores only come from an approved, active reference set.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertTriangle } from "lucide-react"
import { CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Scatter, Tooltip, XAxis, YAxis } from "recharts"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import ModalShell from "./ModalShell"
import { GROWTH_MEASURES, entryProblem, takenAtFor, todayZA } from "./growthEntry"
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

function PastMeasurement({ patientId, onClose }: { patientId: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState({ code: "WEIGHT", value: "", date: "", notes: "" })
  const problem = entryProblem(f.value, f.date)
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/clinic/patients/${patientId}/observations`,
      [{ code: f.code, value: Number(f.value.replace(",", ".")), takenAt: takenAtFor(f.date), notes: f.notes.trim() || undefined }]),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["growth", patientId] }); onClose() },
  })
  const err = (save.error as any)?.response?.data?.message ?? (save.isError ? "The measurement could not be saved." : null)
  const unit = GROWTH_MEASURES.find(m => m.code === f.code)?.unit
  const inp: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
  const lab: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", margin: "10px 0 4px" }
  return (
    <ModalShell title="Record a past measurement" onClose={onClose} width={440}
      footer={<><button type="button" onClick={onClose} style={{ ...inp, width: "auto", cursor: "pointer" }}>Cancel</button>
        <button type="button" disabled={!!problem || save.isPending} onClick={() => save.mutate()}
          style={{ padding: "8px 16px", borderRadius: 8, border: "none", background: "var(--hf-primary)", color: "var(--hf-on-primary, #fff)", fontWeight: 700, cursor: "pointer", opacity: problem || save.isPending ? 0.6 : 1 }}>Save measurement</button></>}>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Use this for a weight, length or head size measured at an earlier visit or at another clinic. It is added to the chart on the date it was measured.</div>
      <label style={lab} htmlFor="gm-code">Measurement</label>
      <select id="gm-code" style={inp} value={f.code} onChange={e => setF(x => ({ ...x, code: e.target.value }))}>
        {GROWTH_MEASURES.map(m => <option key={m.code} value={m.code}>{m.label} ({m.unit})</option>)}
      </select>
      <label style={lab} htmlFor="gm-value">Value{unit ? ` (${unit})` : ""}</label>
      <input id="gm-value" style={inp} inputMode="decimal" value={f.value} onChange={e => setF(x => ({ ...x, value: e.target.value }))} />
      <label style={lab} htmlFor="gm-date">Date measured</label>
      <input id="gm-date" type="date" max={todayZA()} style={inp} value={f.date} onChange={e => setF(x => ({ ...x, date: e.target.value }))} />
      <label style={lab} htmlFor="gm-notes">Where it was measured (optional)</label>
      <input id="gm-notes" style={inp} value={f.notes} onChange={e => setF(x => ({ ...x, notes: e.target.value }))} />
      {(f.value || f.date) && problem && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>{problem}</div>}
      {err && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", marginTop: 8 }}>{err}</div>}
    </ModalShell>
  )
}

export default function GrowthTab({ patientId }: { patientId: string }) {
  const canRead = usePermission("CLINIC_GROWTH_READ")
  const canWrite = usePermission("CLINIC_VITALS_WRITE")
  const [adding, setAdding] = useState(false)
  const q = useQuery<GrowthChart>({ queryKey: ["growth", patientId], enabled: canRead, retry: false,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/clinic/patients/${patientId}/growth`); return (r?.data?.data ?? r?.data) as GrowthChart } })
  if (!canRead) return <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>You do not have permission to view growth charts.</div>
  if (q.isLoading) return <div style={{ fontSize: 13 }}>Loading growth…</div>
  if (q.isError || !q.data) return <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>Could not load the growth chart.</div>
  const g = q.data
  return (
    <div>
      {canWrite && (
        <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 10 }}>
          <button type="button" onClick={() => setAdding(true)}
            style={{ padding: "7px 14px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Record past measurement</button>
        </div>)}
      {adding && <PastMeasurement patientId={patientId} onClose={() => setAdding(false)} />}
      {g.notes.map(n => <div key={n} role="note" style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 8 }}>{n}</div>)}
      {g.measures.map(m => <MeasureCard key={m.code} m={m}/>)}
    </div>
  )
}
