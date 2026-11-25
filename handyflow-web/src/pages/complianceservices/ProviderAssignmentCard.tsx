// src/pages/complianceservices/ProviderAssignmentCard.tsx
//
// One piece of work given to a provider, with everything that can be done to it. Nothing is deleted: work is cancelled or finished with a note.
// Records say "handed over" and are never described as delivered or read. A provider is never called accredited here; only a checked, in-date accreditation counts.
import { useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { assignmentStatus, conflictText, deliverableStatus, noteProblem, ratingProblem, type AssignmentDetail } from "./providerRules"

const B = "/api/v1/compliance-services"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const solid: React.CSSProperties = { padding: "6px 12px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 12, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }

type Ask = { kind: "conflict" | "approve" | "complete" | "cancel" | "deliverable" | "review" | "waive"; target?: string }

export default function ProviderAssignmentCard({ initial, showClient }: { initial: AssignmentDetail; showClient?: boolean }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_PROVIDERS_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const isAdmin = usePermissions(["COMPLIANCE_SERVICES_ADMIN"])
  const [detail, setDetail] = useState(initial)
  const [ask, setAsk] = useState<Ask | null>(null)
  const [note, setNote] = useState("")
  const [flag, setFlag] = useState(false)
  const [rating, setRating] = useState("")
  const [title, setTitle] = useState("")
  const [due, setDue] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const a = detail.assignment

  const run = async (path: string, body?: unknown) => {
    setBusy(true); setError("")
    try {
      const r = await apiClient.post(`${B}${path}`, body ?? {})
      setDetail(unwrap<AssignmentDetail>(r)); setAsk(null); setNote(""); setFlag(false); setRating(""); setTitle(""); setDue("")
      qc.invalidateQueries({ queryKey: ["cs-prov-assign"] }); qc.invalidateQueries({ queryKey: ["cs-prov-perf"] }); qc.invalidateQueries({ queryKey: ["cs-work-queue"] })
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const submit = () => {
    if (!ask) return
    if (ask.kind === "deliverable") { if (!title.trim()) { setError("Say what is expected"); return } return run(`/provider-assignments/${a.id}/deliverables`, { title: title.trim(), dueDate: due || null }) }
    if (ask.kind === "complete") { const r = ratingProblem(Number(rating)); if (r) { setError(r); return } }
    const p = noteProblem(note); if (p) { setError(p); return }
    const n = note.trim()
    switch (ask.kind) {
      case "conflict": return run(`/provider-assignments/${a.id}/conflict`, { conflict: flag, note: n })
      case "approve": return run(`/provider-assignments/${a.id}/conflict/approve`, { note: n })
      case "complete": return run(`/provider-assignments/${a.id}/complete`, { rating: Number(rating), note: n })
      case "cancel": return run(`/provider-assignments/${a.id}/cancel`, { note: n })
      case "review": return run(`/provider-deliverables/${ask.target}/review`, { accepted: !flag, note: n })
      case "waive": return run(`/provider-deliverables/${ask.target}/waive`, { note: n })
    }
  }
  const open = a.status === "PROPOSED" || a.status === "ACTIVE"
  const heading: Record<Ask["kind"], string> = {
    conflict: "Record the conflict declaration", approve: "Approve the declared conflict", complete: "Finish this work", cancel: "Cancel this work",
    deliverable: "Add something expected from the provider", review: "Review what was handed over", waive: "This is not needed",
  }

  return (
    <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12, display: "grid", gap: 8, fontSize: 13 }}>
      <div><strong>{a.title}</strong> · {a.providerName ?? "Provider"}{showClient && a.clientName ? ` · ${a.clientName}` : ""} · {assignmentStatus(a.status)}{a.dueDate ? ` · due ${a.dueDate}` : ""}</div>
      {a.description && <div style={{ color: "var(--hf-text-muted)" }}>{a.description}</div>}
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
        {conflictText(a)}{a.conflictNote ? `: ${a.conflictNote}` : ""}
        {!a.accreditationRequired && <div>No accreditation needed: {a.waiverNote}</div>}
        {a.problemStarting && <div style={{ color: "var(--hf-danger-text)" }}>Cannot start yet: {a.problemStarting}</div>}
        {a.status === "COMPLETED" && <div>Finished{a.onTime === false ? " late" : a.onTime ? " on time" : ""} · rated {a.rating} out of 5 · {a.completionNote}</div>}
        {a.status === "CANCELLED" && <div>Cancelled: {a.cancelNote}</div>}
      </div>
      {detail.deliverables.map(d => (
        <div key={d.id} style={{ borderTop: "1px solid var(--hf-border)", paddingTop: 6, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
          <span style={{ flex: 1, minWidth: 160 }}>{d.title}{d.dueDate ? ` (due ${d.dueDate})` : ""} · {deliverableStatus(d.status)}{d.reviewNote ? ` · ${d.reviewNote}` : ""}</span>
          {canManage && open && (d.status === "EXPECTED" || d.status === "REJECTED") && a.status === "ACTIVE" && <button style={ghost} disabled={busy} onClick={() => run(`/provider-deliverables/${d.id}/received`)}>Mark handed over</button>}
          {canManage && open && d.status === "RECEIVED" && <button style={ghost} onClick={() => { setAsk({ kind: "review", target: d.id }); setError("") }}>Review</button>}
          {canManage && open && (d.status === "EXPECTED" || d.status === "REJECTED") && <button style={ghost} onClick={() => { setAsk({ kind: "waive", target: d.id }); setError("") }}>Not needed</button>}
        </div>
      ))}
      {canManage && open && !ask && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {a.status === "PROPOSED" && a.conflictStatus === "PENDING" && <button style={solid} onClick={() => { setAsk({ kind: "conflict" }); setError("") }}>Record conflict declaration</button>}
          {a.status === "PROPOSED" && a.conflictStatus === "DECLARED" && !a.conflictApproved && isAdmin && <button style={solid} onClick={() => { setAsk({ kind: "approve" }); setError("") }}>Approve conflict</button>}
          {a.status === "PROPOSED" && !a.problemStarting && <button style={solid} disabled={busy} onClick={() => run(`/provider-assignments/${a.id}/activate`)}>Start the work</button>}
          <button style={ghost} onClick={() => { setAsk({ kind: "deliverable" }); setError("") }}>Add expected item</button>
          {a.status === "ACTIVE" && <button style={solid} onClick={() => { setAsk({ kind: "complete" }); setError("") }}>Finish</button>}
          <button style={ghost} onClick={() => { setAsk({ kind: "cancel" }); setError("") }}>Cancel</button>
        </div>
      )}
      {ask && (
        <div style={{ display: "grid", gap: 8, background: "var(--hf-surface-muted)", borderRadius: 8, padding: 10 }}>
          <strong>{heading[ask.kind]}</strong>
          {ask.kind === "conflict" && <label style={{ display: "flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={flag} onChange={e => setFlag(e.target.checked)} /> A conflict of interest exists</label>}
          {ask.kind === "review" && <label style={{ display: "flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={flag} onChange={e => setFlag(e.target.checked)} /> Send it back (not accepted)</label>}
          {ask.kind === "complete" && <input aria-label="Rating from 1 to 5" style={input} placeholder="Rating, 1 to 5" value={rating} onChange={e => setRating(e.target.value)} />}
          {ask.kind === "deliverable" ? (
            <>
              <input aria-label="What is expected" style={input} placeholder="What is expected" value={title} onChange={e => setTitle(e.target.value)} />
              <input aria-label="Due date" type="date" style={input} value={due} onChange={e => setDue(e.target.value)} />
            </>
          ) : (
            <textarea aria-label="Note" style={{ ...input, minHeight: 60 }} placeholder="Reason or note (at least 10 characters)" value={note} onChange={e => setNote(e.target.value)} />
          )}
          {ask.kind === "review" && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Someone other than the person who received it must review it.</div>}
          {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</div>}
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={submit}>Save</button><button style={ghost} onClick={() => { setAsk(null); setError("") }}>Close</button></div>
        </div>
      )}
      {!ask && error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</div>}
    </div>
  )
}
