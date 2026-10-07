// src/pages/security/GuardReviewsCard.tsx
//
// Supervisor reviews on the Performance tab: when the next one is due, the past reviews, and the form for a new one.
// A saved review also counts as a supervisor rating in the operational score. Reviews are never edited or deleted.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ClipboardList, Star } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { DIMENSIONS } from "./performance.logic"
import { todayIso } from "./guard360.logic"
import { OVERALL, defaultReviewForm, dueLine, overallLabel, overallTone, reviewFormError, type ReviewList } from "./reviews.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)", width: "100%", boxSizing: "border-box" }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 12px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }
const h: React.CSSProperties = { fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4 }
const fmt = (d: string) => new Date(d + "T00:00:00").toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
const errText = (e: any) => e?.response?.data?.message ?? "That did not work. Please try again."

function ReviewFormView({ guardId, onClose }: { guardId: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState(defaultReviewForm(todayIso()))
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/security/guards/${guardId}/reviews`, {
      periodFrom: f.periodFrom, periodTo: f.periodTo, reviewDate: f.reviewDate, overall: f.overall, ...f.scores,
      strengths: f.strengths.trim() || null, improvements: f.improvements.trim() || null, trainingNeeds: f.trainingNeeds.trim() || null,
      actionsAgreed: f.actionsAgreed.trim() || null, followUpDate: f.followUpDate || null,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["guard-reviews", guardId] }); qc.invalidateQueries({ queryKey: ["guard-performance", guardId] }); onClose() },
    onError: e => setErr(errText(e)),
  })
  const submit = () => { const m = reviewFormError(f, todayIso()); if (m) setErr(m); else { setErr(""); save.mutate() } }
  const text = (key: "strengths" | "improvements" | "trainingNeeds" | "actionsAgreed", label: string) => (
    <label style={{ fontSize: 12 }}>{label}<textarea value={f[key]} onChange={e => setF({ ...f, [key]: e.target.value })} rows={2} style={{ ...input, resize: "vertical" }} /></label>
  )
  return (
    <div style={{ ...card, display: "grid", gap: 10, background: "var(--hf-surface-sunken)" }}>
      <div style={{ fontWeight: 800 }}>New supervisor review</div>
      <div style={{ display: "grid", gap: 10, gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))" }}>
        <label style={{ fontSize: 12 }}>Period from<input type="date" value={f.periodFrom} onChange={e => setF({ ...f, periodFrom: e.target.value })} style={input} /></label>
        <label style={{ fontSize: 12 }}>Period to<input type="date" value={f.periodTo} onChange={e => setF({ ...f, periodTo: e.target.value })} style={input} /></label>
        <label style={{ fontSize: 12 }}>Review date<input type="date" value={f.reviewDate} onChange={e => setF({ ...f, reviewDate: e.target.value })} style={input} /></label>
        <label style={{ fontSize: 12 }}>Overall
          <select value={f.overall} onChange={e => setF({ ...f, overall: e.target.value })} style={input}>
            <option value="">Choose</option>{OVERALL.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label>
      </div>
      <div style={{ display: "grid", gap: 8, gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))" }}>
        {DIMENSIONS.map(d => (
          <div key={d.key} role="group" aria-label={`Review ${d.label}`} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8 }}>
            <span style={{ fontSize: 13 }}>{d.label}</span>
            <span style={{ display: "inline-flex", gap: 2 }}>
              {[1, 2, 3, 4, 5].map(n => (
                <button key={n} type="button" aria-label={`Review ${d.label} ${n} of 5`} aria-pressed={f.scores[d.key] === n} onClick={() => setF({ ...f, scores: { ...f.scores, [d.key]: n } })}
                  style={{ background: "none", border: "none", cursor: "pointer", padding: 2, color: (f.scores[d.key] ?? 0) >= n ? "var(--hf-warning-text)" : "var(--hf-border)" }}>
                  <Star size={18} fill={(f.scores[d.key] ?? 0) >= n ? "currentColor" : "none"} /></button>
              ))}
            </span>
          </div>
        ))}
      </div>
      {text("strengths", "What went well")}
      {text("improvements", "What needs to improve")}
      {text("trainingNeeds", "Training needs")}
      {text("actionsAgreed", "Actions agreed with the guard")}
      <label style={{ fontSize: 12, maxWidth: 220 }}>Follow-up date (optional)<input type="date" value={f.followUpDate} onChange={e => setF({ ...f, followUpDate: e.target.value })} style={input} /></label>
      {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Saving also records a supervisor rating with these six scores, which counts in the operational score. A saved review cannot be edited.</div>
      <div style={{ display: "flex", gap: 8 }}>
        <button onClick={submit} disabled={save.isPending} style={primary}>Save review</button>
        <button onClick={onClose} style={btn}>Cancel</button>
      </div>
    </div>
  )
}

export default function GuardReviewsCard({ guardId, canManage }: { guardId: string; canManage: boolean }) {
  const [adding, setAdding] = useState(false)
  const { data, isError } = useQuery<ReviewList>({
    queryKey: ["guard-reviews", guardId],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/guards/${guardId}/reviews`); return r.data?.data ?? r.data },
    retry: false,
  })
  if (isError || !data || !Array.isArray(data.reviews)) return null
  const due = dueLine(data)
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap", marginBottom: 6 }}>
        <div style={{ ...h, display: "flex", gap: 6, alignItems: "center" }}><ClipboardList size={14} /> Supervisor reviews</div>
        {canManage && !adding && <button onClick={() => setAdding(true)} style={btn}>New review</button>}
      </div>
      <div style={{ marginBottom: 10 }}><Chip tone={due.tone}>{due.text}</Chip></div>
      {adding && <div style={{ marginBottom: 12 }}><ReviewFormView guardId={guardId} onClose={() => setAdding(false)} /></div>}
      {data.reviews.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No reviews recorded.</div>
        : <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 14 }}>
            {data.reviews.map(r => (
              <li key={r.id} style={{ fontSize: 13 }}>
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
                  <b>{fmt(r.reviewDate)}</b><Chip tone={overallTone(r.overall)}>{overallLabel(r.overall)}</Chip>
                  <span style={{ color: "var(--hf-text-muted)" }}>{r.average.toFixed(1)} / 5 · {r.reviewerName} · covers {fmt(r.periodFrom)} to {fmt(r.periodTo)}</span>
                </div>
                {r.strengths && <div><b>Went well:</b> {r.strengths}</div>}
                {r.improvements && <div><b>To improve:</b> {r.improvements}</div>}
                {r.trainingNeeds && <div><b>Training:</b> {r.trainingNeeds}</div>}
                {r.actionsAgreed && <div><b>Agreed:</b> {r.actionsAgreed}</div>}
                {r.followUpDate && <div style={{ color: "var(--hf-text-muted)" }}>Follow-up {fmt(r.followUpDate)}</div>}
              </li>
            ))}
          </ul>}
    </div>
  )
}
