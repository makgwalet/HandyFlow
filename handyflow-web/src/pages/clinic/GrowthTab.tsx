// Growth charts. Measurements always show; curves and z-scores only come from an approved, active reference set.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertTriangle } from "lucide-react"
import { Area, CartesianGrid, ComposedChart, Line, ResponsiveContainer, Scatter, Tooltip, XAxis, YAxis } from "recharts"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import ModalShell from "./ModalShell"
import { GROWTH_MEASURES, entryProblem, takenAtFor, todayZA } from "./growthEntry"
import { ageLabel, bandCount, bandRows, chartRows, chartTitle, latestOf, mergedTable, sourceLine, tabLabel, trendLine, zKey, zName, type GrowthChart, type MeasureChart } from "./growthView"

const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)", padding: 16, marginBottom: 16 }
const th: React.CSSProperties = { textAlign: "left", fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 600, padding: "4px 8px" }
const td: React.CSSProperties = { fontSize: 13, padding: "4px 8px", borderTop: "1px solid var(--hf-border-subtle)" }

const sideCard: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)", padding: 14, marginBottom: 12 }
const smallCaps: React.CSSProperties = { fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", textTransform: "uppercase", letterSpacing: "0.04em", marginBottom: 6 }
const dayFmt = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric", timeZone: "Africa/Johannesburg" })

function bandOpacity(i: number, n: number) { return 0.1 + 0.14 * (1 - Math.abs(i - 1 - (n - 1) / 2) / Math.max(1, n / 2)) }

function ChartPanel({ m, sex }: { m: MeasureChart; sex: string | null }) {
  const own = chartRows({ ...m, curves: null })
  const curveRows = (m.curves?.points ?? []).map((p, k) => ({ ...(bandRows(m)[k] ?? { age: p.ageMonths }), ...Object.fromEntries((m.curves?.zLines ?? []).map((z, i) => [zKey(z), p.values[i]])) }))
  const n = bandCount(m)
  const lines = m.curves?.zLines ?? []
  return (
    <section aria-label={m.label} style={{ ...card, marginBottom: 12 }}>
      <div style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)", marginBottom: 6 }}>{chartTitle(m, sex)} <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>({m.unit})</span></div>
      {m.banner && (
        <div role="alert" style={{ display: "flex", gap: 8, alignItems: "center", padding: "8px 10px", marginBottom: 8, borderRadius: 8,
          border: "1px solid var(--hf-warning-border, var(--hf-border))", background: "var(--hf-warning-bg, var(--hf-surface-alt, transparent))", color: "var(--hf-warning-text, var(--hf-text))", fontSize: 12, fontWeight: 700 }}>
          <AlertTriangle size={14}/> {m.banner}</div>
      )}
      {m.measurements.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No {m.label.toLowerCase()} recorded for this patient.</div> : (
        <>
          <div style={{ width: "100%", height: 300 }}>
            <ResponsiveContainer>
              <ComposedChart data={curveRows.length ? curveRows : own} margin={{ top: 8, right: 16, bottom: 4, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--hf-border-subtle)"/>
                <XAxis type="number" dataKey="age" domain={["dataMin", "dataMax"]} tickFormatter={ageLabel} fontSize={11}/>
                <YAxis domain={["auto", "auto"]} fontSize={11}/>
                <Tooltip formatter={(v: any) => String(v)} labelFormatter={(a: any) => `Age ${ageLabel(Number(a))}`}/>
                {n > 0 && <Area type="monotone" dataKey="base" stackId="bands" stroke="none" fill="none" legendType="none" isAnimationActive={false}/>}
                {Array.from({ length: n }, (_, k) => <Area key={k} type="monotone" dataKey={`band${k + 1}`} stackId="bands" stroke="none" fill="var(--hf-primary)" fillOpacity={bandOpacity(k + 1, n)} legendType="none" isAnimationActive={false}/>)}
                {lines.map(z => <Line key={z} type="monotone" dataKey={zKey(z)} name={zName(z)} dot={false} strokeWidth={z === 0 ? 2 : 1} stroke="var(--hf-text-muted)" strokeDasharray={z === 0 ? undefined : "4 3"} connectNulls isAnimationActive={false}/>)}
                <Line type="linear" data={own} dataKey="value" name="This patient" stroke="var(--hf-primary)" strokeWidth={2} dot={{ r: 3 }} connectNulls isAnimationActive={false}/>
                <Scatter data={own} dataKey="value" fill="var(--hf-primary)" legendType="none" tooltipType="none"/>
              </ComposedChart>
            </ResponsiveContainer>
          </div>
          {lines.length > 0 && <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 4 }}>Shaded bands run between the reference lines ({lines.map(zName).join(", ")}).</div>}
          <div style={{ fontSize: 11, color: "var(--hf-text-muted)", margin: "4px 0 0" }}>{sourceLine(m)}</div>
        </>
      )}
    </section>
  )
}

function SidePanel({ g, m, onPick }: { g: GrowthChart; m: MeasureChart; onPick: (code: string) => void }) {
  const l = latestOf(m)
  const others = g.measures.filter(x => x.code !== m.code && x.measurements.length > 0)
  const trend = trendLine(m)
  return (
    <aside style={{ flex: "0 1 270px", minWidth: 220 }} aria-label="Latest measurements">
      <div style={sideCard}>
        <div style={smallCaps}>Latest measurement</div>
        {!l ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Nothing recorded yet.</div> : (
          <>
            <div style={{ fontSize: 26, fontWeight: 800, color: "var(--hf-text)" }}>{l.value} <span style={{ fontSize: 14, fontWeight: 600 }}>{l.unit}</span></div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{dayFmt(l.takenAt)} · age {ageLabel(l.ageMonths)}</div>
            {l.change && l.since && <div style={{ fontSize: 12, color: "var(--hf-text)", marginTop: 6 }}>{l.change} since {l.since}</div>}
            {(l.z !== "—" || l.percentile !== "—") && <div style={{ fontSize: 12, color: "var(--hf-text)", marginTop: 4 }}>Z-score {l.z} · percentile {l.percentile}</div>}
          </>)}
      </div>
      {others.length > 0 && (
        <div style={sideCard}>
          <div style={smallCaps}>Other measurements</div>
          {others.map(o => { const x = latestOf(o)!; return (
            <button key={o.code} type="button" onClick={() => onPick(o.code)} aria-label={`Show ${tabLabel(o)}`}
              style={{ display: "flex", justifyContent: "space-between", width: "100%", background: "none", border: "none", padding: "4px 0", fontSize: 13, color: "var(--hf-text)", cursor: "pointer" }}>
              <span style={{ color: "var(--hf-text-muted)" }}>{o.label}</span><strong>{x.value} {x.unit}</strong></button>) })}
        </div>)}
      {trend && <div style={sideCard}><div style={smallCaps}>Trend</div><div style={{ fontSize: 13, color: "var(--hf-text)" }}>{trend}</div>
        <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 4 }}>From the first to the latest measurement. It is a difference, not a clinical judgement.</div></div>}
    </aside>
  )
}

function MeasuresTable({ g, activeCode, active }: { g: GrowthChart; activeCode: string; active: MeasureChart }) {
  const rows = mergedTable(g, activeCode)
  const cols = g.measures.filter(x => x.measurements.length > 0)
  if (rows.length === 0) return null
  return (
    <section aria-label="Measurements" style={card}>
      <div style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)", marginBottom: 6 }}>Measurements</div>
      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Date</th><th style={th}>Age</th>{cols.map(c => <th key={c.code} style={th}>{c.label}</th>)}<th style={th}>Z-score ({active.label.toLowerCase()})</th><th style={th}>Percentile</th></tr></thead>
          <tbody>{rows.map(r => (
            <tr key={r.day}>
              <td style={td}>{dayFmt(r.takenAt)}</td><td style={td}>{ageLabel(r.ageMonths)}</td>
              {cols.map(c => <td key={c.code} style={td}>{r.cells[c.code] ?? "—"}</td>)}
              <td style={td}>{r.z}</td><td style={td}>{r.percentile}</td>
            </tr>))}</tbody>
        </table>
      </div>
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

const SEX: Record<string, string> = { MALE: "Male", FEMALE: "Female" }

export default function GrowthTab({ patientId, patientName }: { patientId: string; patientName?: string }) {
  const canRead = usePermission("CLINIC_GROWTH_READ")
  const canWrite = usePermission("CLINIC_VITALS_WRITE")
  const [adding, setAdding] = useState(false)
  const [picked, setPicked] = useState<string | null>(null)
  const q = useQuery<GrowthChart>({ queryKey: ["growth", patientId], enabled: canRead, retry: false,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/clinic/patients/${patientId}/growth`); return (r?.data?.data ?? r?.data) as GrowthChart } })
  if (!canRead) return <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>You do not have permission to view growth charts.</div>
  if (q.isLoading) return <div style={{ fontSize: 13 }}>Loading growth…</div>
  if (q.isError || !q.data) return <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>Could not load the growth chart.</div>
  const g = q.data
  const active = g.measures.find(m => m.code === picked) ?? g.measures[0]
  const who = [g.currentAgeMonths != null ? ageLabel(g.currentAgeMonths) : null, g.sexAtBirth ? SEX[g.sexAtBirth] ?? null : null].filter(Boolean).join(" · ")
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 12, flexWrap: "wrap" }}>
        <div aria-hidden style={{ width: 40, height: 40, borderRadius: "50%", background: "var(--hf-accent-soft, var(--hf-surface-sunken))", color: "var(--hf-accent-text, var(--hf-text))", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800 }}>
          {(patientName ?? "?").trim().charAt(0).toUpperCase()}</div>
        <div style={{ flex: 1, minWidth: 160 }}>
          <div style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>{patientName ?? "Growth"}</div>
          {who && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{who}</div>}
        </div>
        {canWrite && <button type="button" onClick={() => setAdding(true)}
          style={{ padding: "7px 14px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Record past measurement</button>}
      </div>
      {adding && <PastMeasurement patientId={patientId} onClose={() => setAdding(false)} />}
      {g.notes.map(n => <div key={n} role="note" style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 8 }}>{n}</div>)}
      {active && (
        <>
          <div role="tablist" aria-label="Growth measure" style={{ display: "flex", gap: 4, borderBottom: "1px solid var(--hf-border)", marginBottom: 14, overflowX: "auto" }}>
            {g.measures.map(m => {
              const on = m.code === active.code
              return <button key={m.code} role="tab" type="button" aria-selected={on} onClick={() => setPicked(m.code)}
                style={{ padding: "8px 14px", background: "none", border: "none", borderBottom: `2px solid ${on ? "var(--hf-primary)" : "transparent"}`, marginBottom: -1,
                  fontSize: 13, fontWeight: on ? 700 : 500, color: on ? "var(--hf-primary)" : "var(--hf-text-muted)", cursor: "pointer", whiteSpace: "nowrap" }}>{tabLabel(m)}</button>
            })}
          </div>
          <div style={{ display: "flex", gap: 16, alignItems: "flex-start", flexWrap: "wrap" }}>
            <div style={{ flex: "1 1 460px", minWidth: 0 }}><ChartPanel m={active} sex={g.sexAtBirth} /></div>
            <SidePanel g={g} m={active} onPick={setPicked} />
          </div>
          <MeasuresTable g={g} activeCode={active.code} active={active} />
        </>)}
    </div>
  )
}
