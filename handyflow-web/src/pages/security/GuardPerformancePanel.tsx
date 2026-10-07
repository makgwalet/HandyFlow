// src/pages/security/GuardPerformancePanel.tsx
//
// The Performance tab on Guard 360: the operational score out of 100 with every component explained, the risk
// recommendations (advice for a person to review, never a decision) and client or supervisor ratings.
import { useState } from "react"
import { Link } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Plus, Star } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { toneColor } from "../../components/ui/Chip"
import { todayIso } from "./guard360.logic"
import { BAND_LABEL, BAND_TONE, DIMENSIONS, chartGeometry, trendSummary, trendText, type HistoryPoint, LEVEL_TONE, RATING_SOURCES, percentTone, ratingFormError, sourceLabel, type Performance } from "./performance.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)", width: "100%", boxSizing: "border-box" }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 12px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }
const h: React.CSSProperties = { fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, marginBottom: 8 }
const fmt = (d: string) => new Date(d + "T00:00:00").toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
const errText = (e: any) => e?.response?.data?.message ?? "That did not work. Please try again."

function ScoreRing({ score, band }: { score: number | null; band: string }) {
  const color = toneColor(BAND_TONE[band] ?? "neutral")
  const r = 44, c = 2 * Math.PI * r
  return (
    <div role="img" aria-label={score === null ? "No operational score yet" : `Operational score ${score} out of 100, ${BAND_LABEL[band]}`} style={{ position: "relative", width: 112, height: 112, flexShrink: 0 }}>
      <svg width="112" height="112" viewBox="0 0 112 112">
        <circle cx="56" cy="56" r={r} fill="none" stroke="var(--hf-surface-sunken)" strokeWidth="10" />
        {score !== null && <circle cx="56" cy="56" r={r} fill="none" stroke={color} strokeWidth="10" strokeLinecap="round" strokeDasharray={c} strokeDashoffset={c * (1 - score / 100)} transform="rotate(-90 56 56)" />}
      </svg>
      <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", color }}>
        <div style={{ fontSize: 26, fontWeight: 800 }}>{score ?? "-"}</div>
        <div style={{ fontSize: 11, fontWeight: 700 }}>{score === null ? "NO SCORE" : "OUT OF 100"}</div>
      </div>
    </div>
  )
}

function RatingForm({ guardId, onClose }: { guardId: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState({ source: "CLIENT", raterName: "", ratedOn: todayIso(), comment: "", scores: {} as Record<string, number> })
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/security/guards/${guardId}/ratings`, { source: f.source, raterName: f.raterName.trim() || null, ratedOn: f.ratedOn, comment: f.comment.trim() || null, ...f.scores }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["guard-performance", guardId] }); onClose() },
    onError: e => setErr(errText(e)),
  })
  const submit = () => { const m = ratingFormError(f, todayIso()); if (m) setErr(m); else { setErr(""); save.mutate() } }
  return (
    <div style={{ ...card, display: "grid", gap: 10 }}>
      <div style={{ fontWeight: 800 }}>Add a rating</div>
      <div style={{ display: "grid", gap: 10, gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))" }}>
        <label style={{ fontSize: 12 }}>Given by
          <select value={f.source} onChange={e => setF({ ...f, source: e.target.value })} style={input}>{RATING_SOURCES.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}</select></label>
        <label style={{ fontSize: 12 }}>Name<input value={f.raterName} onChange={e => setF({ ...f, raterName: e.target.value })} style={input} /></label>
        <label style={{ fontSize: 12 }}>Date<input type="date" value={f.ratedOn} onChange={e => setF({ ...f, ratedOn: e.target.value })} style={input} /></label>
      </div>
      <div style={{ display: "grid", gap: 8, gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))" }}>
        {DIMENSIONS.map(d => (
          <div key={d.key} role="group" aria-label={d.label} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8 }}>
            <span style={{ fontSize: 13 }}>{d.label}</span>
            <span style={{ display: "inline-flex", gap: 2 }}>
              {[1, 2, 3, 4, 5].map(n => (
                <button key={n} type="button" aria-label={`${d.label} ${n} of 5`} aria-pressed={f.scores[d.key] === n} onClick={() => setF({ ...f, scores: { ...f.scores, [d.key]: n } })}
                  style={{ background: "none", border: "none", cursor: "pointer", padding: 2, color: (f.scores[d.key] ?? 0) >= n ? "var(--hf-warning-text)" : "var(--hf-border)" }}>
                  <Star size={18} fill={(f.scores[d.key] ?? 0) >= n ? "currentColor" : "none"} /></button>
              ))}
            </span>
          </div>
        ))}
      </div>
      <label style={{ fontSize: 12 }}>Comment<input value={f.comment} onChange={e => setF({ ...f, comment: e.target.value })} style={input} /></label>
      {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button onClick={submit} disabled={save.isPending} style={primary}>Save rating</button>
        <button onClick={onClose} style={btn}>Cancel</button>
      </div>
    </div>
  )
}

function ScoreTrend({ guardId }: { guardId: string }) {
  const [days, setDays] = useState(30)
  const { data } = useQuery<HistoryPoint[]>({
    queryKey: ["guard-performance-history", guardId, days],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/guards/${guardId}/performance/history`, { params: { days } }); const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : [] },
  })
  const points = data ?? []
  const W = 560, H = 140
  const g = chartGeometry(points, W, H)
  const summary = trendSummary(points)
  const color = summary?.direction === "down" ? "var(--hf-danger-text)" : "var(--hf-accent)"
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
        <div style={h}>Score trend</div>
        <div role="group" aria-label="Trend period" style={{ display: "flex", gap: 6 }}>
          {[30, 90].map(n => <button key={n} onClick={() => setDays(n)} aria-pressed={days === n} style={{ ...btn, padding: "4px 10px", ...(days === n ? { background: "var(--hf-surface-sunken)", color: "var(--hf-text-primary)" } : {}) }}>{n} days</button>)}
        </div>
      </div>
      {g.dots.length === 0
        ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No history yet. A score is saved for each guard every night, so the trend builds up from tomorrow.</div>
        : <>
            <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label={`Operational score over the last ${days} days. ${trendText(summary, fmt)}`} style={{ maxHeight: 180 }}>
              {[0, 50, 70, 85, 100].map(v => { const y = 8 + (1 - v / 100) * (H - 16); return <g key={v}><line x1="0" x2={W} y1={y} y2={y} stroke="var(--hf-border)" strokeDasharray={v === 0 || v === 100 ? undefined : "3 4"} strokeWidth="1" /><text x="2" y={y - 2} fontSize="9" fill="var(--hf-text-muted)">{v}</text></g> })}
              <path d={g.line} fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" />
              {g.dots.map(d => <circle key={d.date} cx={d.x} cy={d.y} r="2.5" fill={color}><title>{`${fmt(d.date)}: ${d.score}`}</title></circle>)}
            </svg>
            <div style={{ fontSize: 13, marginTop: 6 }}>{trendText(summary, fmt)}</div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Guide lines mark the band edges: 50, 70 and 85. Days without enough data are left out.</div>
          </>}
    </div>
  )
}

export default function GuardPerformancePanel({ guardId, canManage }: { guardId: string; canManage: boolean }) {
  const [adding, setAdding] = useState(false)
  const { data, isLoading, error } = useQuery<Performance>({
    queryKey: ["guard-performance", guardId],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/guards/${guardId}/performance`); return r.data?.data ?? r.data },
  })
  if (isLoading) return <div style={{ color: "var(--hf-text-muted)" }}>Loading performance...</div>
  if (error || !data) return <div role="alert" style={{ ...card, color: "var(--hf-danger-text)" }}>Performance could not be loaded.</div>

  return (
    <div style={{ display: "grid", gap: 16 }}>
      <div style={{ ...card, display: "flex", gap: 20, alignItems: "center", flexWrap: "wrap" }}>
        <ScoreRing score={data.score} band={data.band} />
        <div style={{ flex: "1 1 260px", minWidth: 0 }}>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 6 }}>
            <div style={{ fontWeight: 800 }}>Operational score</div>
            <Chip tone={BAND_TONE[data.band] ?? "neutral"}>{BAND_LABEL[data.band] ?? data.band}</Chip>
          </div>
          <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
            {data.score === null
              ? `Not enough records yet to score this guard fairly (${data.coverage} of 100 points of evidence, 40 needed).`
              : data.coverage < 100
                ? `Based on ${data.coverage} of 100 points of evidence. Components without enough data are left out, not counted as zero.`
                : "Based on all seven components over the last 90 days (ratings over 180 days)."}
          </div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 6 }}>The score describes records. It does not decide anything about employment.</div>
        </div>
      </div>

      <ScoreTrend guardId={guardId} />

      <div style={card}>
        <div style={h}>Recommendations for review</div>
        {data.recommendations.length === 0
          ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Nothing to flag against the current rules ({data.basis.complaintsCounted} complaint{data.basis.complaintsCounted === 1 ? "" : "s"} counted in the last {data.basis.days} days).</div>
          : <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 10 }}>
              {data.recommendations.map(r => (
                <li key={r.code} style={{ display: "flex", gap: 10, alignItems: "flex-start", flexWrap: "wrap" }}>
                  <Chip tone={LEVEL_TONE[r.level] ?? "neutral"}>{r.level.toLowerCase()}</Chip>
                  <div style={{ flex: "1 1 240px", minWidth: 0 }}><div style={{ fontWeight: 700, fontSize: 14 }}>{r.title}</div>
                    <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{r.reason}</div></div>
                </li>
              ))}
            </ul>}
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 10 }}>
          These are suggestions for a manager to consider. Nothing here suspends, warns or dismisses anyone. Thresholds are set under <Link to="/security/risk-rules" style={{ color: "var(--hf-accent-text)" }}>Risk rules</Link>.</div>
      </div>

      <div style={{ ...card, overflowX: "auto" }}>
        <div style={h}>How the score was worked out</div>
        <div style={{ display: "grid", gap: 12 }}>
          {data.components.map(c => (
            <div key={c.key}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 8, fontSize: 13, flexWrap: "wrap" }}>
                <span style={{ fontWeight: 700 }}>{c.label} <span style={{ color: "var(--hf-text-muted)", fontWeight: 500 }}>(worth {c.weight})</span></span>
                <span style={{ color: c.hasData ? "var(--hf-text-primary)" : "var(--hf-text-muted)" }}>{c.hasData ? `${c.points} of ${c.weight}` : "not counted"}</span>
              </div>
              <div role="progressbar" aria-label={c.label} aria-valuenow={c.hasData ? c.percent : undefined} aria-valuemin={0} aria-valuemax={100}
                style={{ height: 8, borderRadius: 4, background: "var(--hf-surface-sunken)", margin: "5px 0", overflow: "hidden" }}>
                {c.hasData && <div style={{ width: `${c.percent}%`, height: "100%", background: toneColor(percentTone(c.percent)) }} />}
              </div>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{c.detail}</div>
            </div>
          ))}
        </div>
      </div>

      <div style={card}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap", marginBottom: 8 }}>
          <div style={{ ...h, marginBottom: 0 }}>Ratings{data.ratingAverage !== null ? `, ${data.ratingAverage} out of 5 from ${data.ratingCount}` : ""}</div>
          {canManage && !adding && <button onClick={() => setAdding(true)} style={btn}><Plus size={14} /> Add rating</button>}
        </div>
        {adding && <div style={{ marginBottom: 12 }}><RatingForm guardId={guardId} onClose={() => setAdding(false)} /></div>}
        {data.ratings.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No ratings in the last 180 days.</div>
          : <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 10 }}>
              {data.ratings.map(r => (
                <li key={r.id} style={{ fontSize: 13 }}>
                  <b>{r.average.toFixed(1)} / 5</b> <span style={{ color: "var(--hf-text-muted)" }}>· {sourceLabel(r.source)}{r.raterName ? `, ${r.raterName}` : ""} · {fmt(r.ratedOn)}{r.siteName ? ` · ${r.siteName}` : ""}</span>
                  {r.comment && <div style={{ color: "var(--hf-text-secondary)", overflowWrap: "anywhere" }}>{r.comment}</div>}
                </li>
              ))}
            </ul>}
      </div>
    </div>
  )
}
