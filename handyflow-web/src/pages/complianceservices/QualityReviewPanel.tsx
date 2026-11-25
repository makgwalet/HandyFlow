// src/pages/complianceservices/QualityReviewPanel.tsx
//
// One review: the checklist, marked one check at a time, then finished as approved or sent back. A failed check or one that does not apply says why.
// The person who prepared the work can review it only with a recorded reason. Approving changes nothing in the work itself.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useAuth } from "../../hooks/useAuth"
import { RESULTS, completeProblem, markProblem, needsNote, progress, resultLabel, statusLabel, MIN_NOTE, type ReviewDetail, type ReviewItem } from "./qualityRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const B = "/api/v1/compliance-services/quality/reviews"
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const solid: React.CSSProperties = { padding: "7px 13px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 12, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "5px 11px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const chip = (on: boolean): React.CSSProperties => ({ padding: "4px 11px", borderRadius: 999, border: "1px solid var(--hf-border)", background: on ? "var(--hf-success-solid-strong)" : "transparent", color: on ? "#fff" : "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" })
const bad: React.CSSProperties = { fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }

export default function QualityReviewPanel({ reviewId, canManage, onChanged }: { reviewId: string; canManage: boolean; onChanged: () => void }) {
  const { user } = useAuth()
  const [outcome, setOutcome] = useState("APPROVED")
  const [note, setNote] = useState("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const key = ["cs-quality-detail", reviewId]
  const { data: d, refetch } = useQuery<ReviewDetail>({ queryKey: key, queryFn: async () => unwrap<ReviewDetail>(await apiClient.get(`${B}/${reviewId}`)) })
  if (!d) return <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>

  const r = d.review
  const open = r.actions.includes("RECORD")
  const sameAsPreparer = !!d.preparedBy && d.preparedBy === user?.userId
  const run = async (work: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await work(); await refetch(); onChanged(); return true } catch (e) { setError(errText(e)); return false } finally { setBusy(false) }
  }
  const mark = (position: number, result: string, text: string) => run(() => apiClient.put(`${B}/${reviewId}/checks/${position}`, { result, note: text.trim() || null }))
  const problem = completeProblem(outcome, d.items, note, sameAsPreparer, reason)
  const finish = async () => {
    const ok = await run(() => apiClient.post(`${B}/${reviewId}/complete`, { outcome, note: note.trim(), exceptionReason: sameAsPreparer ? reason.trim() : null }))
    if (ok) { setNote(""); setReason("") }
  }

  return (
    <div style={{ display: "grid", gap: 8, borderTop: "1px solid var(--hf-border)", paddingTop: 8 }}>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
        {statusLabel(r.status)} · round {r.round} · {progress(r)}{r.sample ? " · picked in a random sample" : ""}
      </div>
      {d.items.map(i => <CheckRow key={i.position + ":" + i.result + ":" + (i.note ?? "")} item={i} editable={open && canManage} busy={busy} onSave={mark} />)}
      {d.outcomeNote && (
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
          Outcome: {d.outcomeNote}
          {d.exceptionReason && <div>Reviewed by the person who prepared the work. Reason: {d.exceptionReason}</div>}
        </div>
      )}
      {open && canManage && (
        <div style={{ display: "grid", gap: 6, borderTop: "1px dashed var(--hf-border)", paddingTop: 8 }}>
          <div style={{ display: "flex", gap: 6 }}>
            <button style={chip(outcome === "APPROVED")} onClick={() => setOutcome("APPROVED")}>Approve</button>
            <button style={chip(outcome === "REWORK_REQUIRED")} onClick={() => setOutcome("REWORK_REQUIRED")}>Send back for rework</button>
          </div>
          <textarea style={input} rows={2} aria-label="Summary of the review" placeholder="Summary of the review" value={note} onChange={e => setNote(e.target.value)} />
          {sameAsPreparer && <textarea style={input} rows={2} aria-label="Why you are reviewing your own work" placeholder="You prepared this work. Why are you reviewing it yourself?" value={reason} onChange={e => setReason(e.target.value)} />}
          {note && problem && <div style={bad}>{problem}</div>}
          <div><button style={solid} disabled={busy || !!problem} onClick={finish}>{outcome === "APPROVED" ? "Approve" : "Send back"}</button></div>
        </div>
      )}
      {r.actions.includes("NEXT_ROUND") && canManage && (
        <div><button style={solid} disabled={busy} onClick={() => run(() => apiClient.post(`${B}/${reviewId}/next-round`, {}))}>Start the next round</button></div>
      )}
      {error && <div style={bad}>{error}</div>}
    </div>
  )
}

function CheckRow({ item, editable, busy, onSave }: { item: ReviewItem; editable: boolean; busy: boolean; onSave: (position: number, result: string, note: string) => Promise<unknown> }) {
  const [result, setResult] = useState(item.result)
  const [note, setNote] = useState(item.note ?? "")
  const problem = result !== item.result || note !== (item.note ?? "") ? markProblem(result, note) : null
  const pick = (r: string) => { setResult(r); if (!needsNote(r)) onSave(item.position, r, "") }
  return (
    <div style={{ display: "grid", gap: 4, padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8 }}>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <span style={{ fontSize: 13, color: "var(--hf-text)", flex: 1 }}>{item.text}{item.critical && <span style={{ fontSize: 11, fontWeight: 700, marginLeft: 6, color: "var(--hf-text-muted)" }}>Critical</span>}</span>
        {editable
          ? RESULTS.map(x => <button key={x[0]} style={chip(result === x[0])} aria-pressed={result === x[0]} disabled={busy} onClick={() => pick(x[0])}>{x[1]}</button>)
          : <span style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)" }}>{resultLabel(item.result)}</span>}
      </div>
      {editable && needsNote(result) && (
        <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
          <input style={input} aria-label={`Why, for ${item.text}`} placeholder="Why?" value={note} onChange={e => setNote(e.target.value)} />
          <button style={ghost} disabled={busy || note.trim().length < MIN_NOTE} onClick={() => onSave(item.position, result, note)}>Save</button>
        </div>
      )}
      {editable && problem && note && <div style={bad}>{problem}</div>}
      {!editable && item.note && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{item.note}</div>}
    </div>
  )
}
