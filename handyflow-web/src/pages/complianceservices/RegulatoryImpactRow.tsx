// src/pages/complianceservices/RegulatoryImpactRow.tsx
//
// One client touched by a regulatory change, and what has been done about it. Every move needs a note, so the trail says what happened.
import { useState } from "react"
import { apiClient } from "../../api/client"
import { actionLabel, impactLabel, noteProblem, MIN_NOTE, type Impact } from "./regulatoryRules"

const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const ghost: React.CSSProperties = { padding: "5px 11px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const solid: React.CSSProperties = { padding: "6px 12px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 12, fontWeight: 600, cursor: "pointer" }

export default function RegulatoryImpactRow({ impact, canManage, showClient, onChanged }: { impact: Impact; canManage: boolean; showClient?: boolean; onChanged: () => void }) {
  const [action, setAction] = useState<string | null>(null)
  const [note, setNote] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    if (!action) return
    setBusy(true); setError("")
    try {
      await apiClient.post(`/api/v1/compliance-services/regulatory/impacts/${impact.id}/act`, { action, note: note.trim() })
      setAction(null); setNote(""); onChanged()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const problem = action && note ? noteProblem(note) : null

  return (
    <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "8px 10px", display: "grid", gap: 4 }}>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
        <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{showClient ? `${impact.clientName} · ${impact.obligationCode}` : `${impact.changeTitle} · ${impact.obligationCode}`}</span>
        <span style={{ flex: 1 }} />
        {impact.dueDate && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>In force from {impact.dueDate}</span>}
        <span style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)" }}>{impactLabel(impact.status)}</span>
      </div>
      {impact.note && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{impact.note}</div>}
      {canManage && impact.actions.length > 0 && !action && (
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {impact.actions.map(a => <button key={a} style={ghost} onClick={() => { setAction(a); setError("") }}>{actionLabel(a)}</button>)}
        </div>
      )}
      {action && (
        <div style={{ display: "grid", gap: 6 }}>
          <label htmlFor={`impact-note-${impact.id}`} style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)" }}>{actionLabel(action)}: what was done? (at least {MIN_NOTE} characters)</label>
          <textarea id={`impact-note-${impact.id}`} rows={2} value={note} onChange={e => setNote(e.target.value)}
            style={{ width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }} />
          {problem && <div style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }}>{problem}</div>}
          <div style={{ display: "flex", gap: 8 }}>
            <button style={{ ...solid, opacity: busy || noteProblem(note) ? 0.5 : 1 }} disabled={busy || !!noteProblem(note)} onClick={submit}>Save</button>
            <button style={ghost} onClick={() => { setAction(null); setNote("") }}>Cancel</button>
          </div>
        </div>
      )}
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }}>{error}</div>}
    </div>
  )
}
