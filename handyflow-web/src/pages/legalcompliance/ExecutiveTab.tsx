// src/pages/legalcompliance/ExecutiveTab.tsx
//
// Executive GRC overview. Endpoints (/api/v1/legalcompliance/executive): GET / (live indicators and attention list) · GET /history?months= · POST /snapshots
// Every figure is a count taken from a register; there is deliberately no composite "compliance score". A percentage shows "—" when there is nothing to
// measure (zero denominator) rather than 0% or 100%. Snapshots store the raw counts as they were on the day, one per day; the monthly job takes one on the 1st.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { one, unwrap, ghostBtn, input, fmtDate, errMsg } from "./lcHelpers"
import { Err, Badge } from "./LcUi"
import { Camera, ChevronRight } from "lucide-react"

const base = "/api/v1/legalcompliance/executive"

interface Indicator { key: string; label: string; formula: string; numerator: number; denominator: number; percent: number | null; tab: string }
interface Attention { key: string; severity: "CRITICAL" | "WARNING"; text: string; count: number; tab: string }
interface Overview { generatedAt: string; metrics: Record<string, number>; indicators: Indicator[]; attention: Attention[] }
interface Point { date: string; source: string; takenBy: string | null; metrics: Record<string, number>; indicators: Indicator[] }

// Colour is never the only signal: the figure and the words carry the meaning too.
const tone = (p: number | null) => p === null ? { color: "#475569", bar: "#CBD5E1" } : p >= 90 ? { color: "#166534", bar: "#16A34A" } : p >= 70 ? { color: "#92400E", bar: "#D97706" } : { color: "#991B1B", bar: "#DC2626" }

function Trend({ points, k }: { points: Point[]; k: string }) {
  const data = points.map(p => ({ d: p.date, v: p.indicators.find(i => i.key === k)?.percent ?? null }))
  const have = data.filter(x => x.v !== null)
  if (have.length < 2) return <p style={{ fontSize: 12, color: "#64748B" }}>Not enough snapshots with this indicator to draw a trend yet.</p>
  const W = 560, H = 140, pad = 28
  const x = (i: number) => pad + (data.length === 1 ? 0 : (i * (W - pad * 2)) / (data.length - 1))
  const y = (v: number) => H - pad - (v / 100) * (H - pad * 2)
  const path = data.map((p, i) => (p.v === null ? null : `${x(i)},${y(p.v)}`)).filter(Boolean).join(" ")
  return (
    <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Trend of the selected indicator across snapshots" style={{ width: "100%", maxWidth: W, height: "auto" }}>
      {[0, 50, 100].map(g => <g key={g}><line x1={pad} x2={W - pad} y1={y(g)} y2={y(g)} stroke="#E2E8F0" /><text x={pad - 6} y={y(g) + 4} fontSize={10} textAnchor="end" fill="#64748B">{g}%</text></g>)}
      <polyline points={path} fill="none" stroke="#4338CA" strokeWidth={2} />
      {data.map((p, i) => p.v === null ? null : <g key={p.d}><circle cx={x(i)} cy={y(p.v)} r={3.5} fill="#4338CA" /><title>{`${fmtDate(p.d)}: ${p.v}%`}</title></g>)}
      <text x={pad} y={H - 6} fontSize={10} fill="#64748B">{fmtDate(data[0].d)}</text><text x={W - pad} y={H - 6} fontSize={10} textAnchor="end" fill="#64748B">{fmtDate(data[data.length - 1].d)}</text>
    </svg>
  )
}

export default function ExecutiveTab({ onNavigate }: { onNavigate: (tab: string) => void }) {
  const qc = useQueryClient()
  const hasManage = usePermission("LEGALCOMPLIANCE_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canSnapshot = hasManage || hasAdmin
  const [months, setMonths] = useState(12)
  const [trendKey, setTrendKey] = useState("obligationsCompliant")
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: ov, isLoading } = useQuery<Overview>({ queryKey: ["lc-exec", "overview"], queryFn: async () => one(await apiClient.get(base)) })
  const { data: history = [] } = useQuery<Point[]>({ queryKey: ["lc-exec", "history", months], queryFn: async () => unwrap(await apiClient.get(`${base}/history`, { params: { months } })) })
  const snap = useMutation({
    mutationFn: () => apiClient.post(`${base}/snapshots`), onSuccess: () => { qc.invalidateQueries({ queryKey: ["lc-exec"] }); setApiError(""); setNotice("Snapshot taken") },
    onError: (e: unknown) => { setNotice(""); setApiError(errMsg(e, "Could not take a snapshot")) },
  })

  if (isLoading) return <p style={{ color: "#64748B" }}>Loading…</p>
  if (!ov) return <Err m="Could not load the overview" />
  const m = ov.metrics
  const critical = ov.attention.filter(a => a.severity === "CRITICAL")
  const warnings = ov.attention.filter(a => a.severity === "WARNING")
  const trendOptions = ov.indicators

  const group = (title: string, items: Attention[], badge: { color: string; bg: string }) => items.length > 0 && (
    <div style={{ marginBottom: 12 }}>
      <div style={{ fontSize: 12, fontWeight: 700, color: badge.color, marginBottom: 4 }}>{title}</div>
      {items.map(a => (
        <button key={a.key} onClick={() => onNavigate(a.tab)} style={{ all: "unset", cursor: "pointer", display: "flex", alignItems: "center", gap: 8, padding: "8px 12px", border: "1px solid #E2E8F0", borderRadius: 8, marginBottom: 4, width: "calc(100% - 24px)", background: "#fff" }}>
          <Badge text={a.severity === "CRITICAL" ? "Critical" : "Warning"} color={badge.color} bg={badge.bg} /><span style={{ flex: 1, fontSize: 13, color: "#0F172A" }}>{a.text}</span><ChevronRight size={14} color="#94A3B8" />
        </button>
      ))}
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 12, flexWrap: "wrap" }}>
        <div style={{ flex: 1, fontSize: 12, color: "#64748B" }}>Counts as of {new Date(ov.generatedAt).toLocaleString("en-ZA")}. FICA is not included here because it is a restricted area.</div>
        {canSnapshot && <button style={ghostBtn} disabled={snap.isPending} onClick={() => { setNotice(""); setApiError(""); snap.mutate() }}><Camera size={14} style={{ verticalAlign: -2 }} /> Take snapshot</button>}
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && <Err m={apiError} />}

      <div style={{ fontWeight: 700, color: "#0F172A", marginBottom: 8 }}>Indicators</div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(230px, 1fr))", gap: 12, marginBottom: 20 }}>
        {ov.indicators.map(i => {
          const t = tone(i.percent)
          return (
            <button key={i.key} onClick={() => onNavigate(i.tab)} title={i.formula} style={{ all: "unset", cursor: "pointer", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
              <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600, minHeight: 32 }}>{i.label}</div>
              <div style={{ fontSize: 26, fontWeight: 800, color: t.color }}>{i.percent === null ? "—" : `${i.percent}%`}</div>
              <div style={{ height: 6, background: "#F1F5F9", borderRadius: 3, margin: "4px 0" }}><div style={{ width: `${i.percent ?? 0}%`, height: 6, background: t.bar, borderRadius: 3 }} /></div>
              <div style={{ fontSize: 11, color: "#94A3B8" }}>{i.denominator === 0 ? "Nothing to measure yet" : `${i.numerator} of ${i.denominator}`}</div>
            </button>
          )
        })}
      </div>

      <div style={{ fontWeight: 700, color: "#0F172A", marginBottom: 8 }}>Needs attention</div>
      {ov.attention.length === 0 ? <p style={{ fontSize: 13, color: "#166534" }}>Nothing is currently overdue or flagged across the registers.</p> : <>{group("Critical", critical, { color: "#991B1B", bg: "#FEE2E2" })}{group("Warnings", warnings, { color: "#92400E", bg: "#FEF3C7" })}</>}

      <div style={{ fontWeight: 700, color: "#0F172A", margin: "20px 0 8px" }}>At a glance</div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 20 }}>
        {[["Obligations", m["obligations.total"]], ["Open risks", m["risks.open"]], ["Open findings", m["findings.open"]], ["Open incidents", m["incidents.open"]], ["Published policies", m["policies.active"]], ["Approved third parties", m["thirdParties.approved"]], ["Licences in force", m["licences.live"]]].map(([l, v]) => (
          <div key={String(l)} style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "8px 14px" }}><div style={{ fontSize: 11, color: "#64748B" }}>{l}</div><div style={{ fontSize: 18, fontWeight: 700, color: "#0F172A" }}>{v ?? "—"}</div></div>
        ))}
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 8, flexWrap: "wrap" }}>
        <div style={{ fontWeight: 700, color: "#0F172A" }}>History</div>
        <select style={{ ...input, width: 230 }} value={trendKey} onChange={e => setTrendKey(e.target.value)} aria-label="Indicator to chart">{trendOptions.map(i => <option key={i.key} value={i.key}>{i.label}</option>)}</select>
        <select style={{ ...input, width: 140 }} value={months} onChange={e => setMonths(+e.target.value)} aria-label="Period"><option value={6}>Last 6 months</option><option value={12}>Last 12 months</option><option value={24}>Last 24 months</option></select>
      </div>
      {history.length === 0 ? <p style={{ fontSize: 13, color: "#64748B" }}>No snapshots yet. {canSnapshot ? "Take one now to start the record; one is also taken automatically on the 1st of each month." : "One is taken automatically on the 1st of each month."}</p> : (
        <>
          <Trend points={history} k={trendKey} />
          <div style={{ overflowX: "auto", marginTop: 8 }}>
            <table style={{ borderCollapse: "collapse", fontSize: 12, width: "100%" }}>
              <thead><tr><th style={{ textAlign: "left", padding: "6px 8px", borderBottom: "1px solid #E2E8F0" }}>Date</th><th style={{ textAlign: "left", padding: "6px 8px", borderBottom: "1px solid #E2E8F0" }}>Source</th>{trendOptions.map(i => <th key={i.key} style={{ textAlign: "right", padding: "6px 8px", borderBottom: "1px solid #E2E8F0", fontWeight: 600 }}>{i.label}</th>)}</tr></thead>
              <tbody>{[...history].reverse().map(p => (
                <tr key={p.date}><td style={{ padding: "6px 8px", borderBottom: "1px solid #F1F5F9" }}>{fmtDate(p.date)}</td><td style={{ padding: "6px 8px", borderBottom: "1px solid #F1F5F9", color: "#64748B" }}>{p.source === "MONTHLY" ? "Monthly" : `Manual${p.takenBy ? ` (${p.takenBy})` : ""}`}</td>
                  {trendOptions.map(i => { const v = p.indicators.find(x => x.key === i.key)?.percent; return <td key={i.key} style={{ padding: "6px 8px", borderBottom: "1px solid #F1F5F9", textAlign: "right" }}>{v === undefined || v === null ? "—" : `${v}%`}</td> })}</tr>
              ))}</tbody>
            </table>
          </div>
        </>
      )}
    </div>
  )
}
