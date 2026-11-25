// src/pages/complianceservices/ClientRequestsTab.tsx
//
// Contact with this client: what they have asked us, and the documents we have asked them for. Nothing here is deleted; requests are closed or cancelled with a reason.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import QueryThread from "./QueryThread"
import { isLiveRequest, noteProblem, queryStatusLabel, requestStatusLabel, sortQueries, type StaffQuery, type StaffRequest } from "./contactRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }
const solid: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12, display: "grid", gap: 8 }

export default function ClientRequestsTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const [open, setOpen] = useState<string | null>(null)
  const [asking, setAsking] = useState(false)
  const base = `/api/v1/compliance-services/clients/${clientId}`
  const queries = useQuery<StaffQuery[]>({ queryKey: ["cs-portal-queries", clientId], queryFn: async () => unwrap<StaffQuery[]>(await apiClient.get(`${base}/portal-queries`)) })
  const requests = useQuery<StaffRequest[]>({ queryKey: ["cs-doc-requests", clientId], queryFn: async () => unwrap<StaffRequest[]>(await apiClient.get(`${base}/document-requests`)) })
  const changed = () => {
    qc.invalidateQueries({ queryKey: ["cs-portal-queries", clientId] }); qc.invalidateQueries({ queryKey: ["cs-doc-requests", clientId] })
    qc.invalidateQueries({ queryKey: ["cs-portal-queries-all"] }); qc.invalidateQueries({ queryKey: ["cs-work-queue"] })
  }
  return (
    <div style={{ display: "grid", gap: 18 }}>
      <section style={{ display: "grid", gap: 8 }}>
        <h3 style={{ margin: 0, fontSize: 14 }}>Queries from the client</h3>
        {queries.isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
        {!queries.isLoading && (queries.data ?? []).length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>The client has not written to you through the portal.</div>}
        {sortQueries(queries.data ?? []).map(q => (
          <div key={q.id} style={card}>
            <button onClick={() => setOpen(open === q.id ? null : q.id)} aria-expanded={open === q.id} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 10, alignItems: "baseline" }}>
              <strong style={{ fontSize: 13 }}>{q.subject}</strong>
              <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{queryStatusLabel(q.status)}{q.raisedByName ? ` · ${q.raisedByName}` : ""}</span>
            </button>
            {open === q.id && <QueryThread id={q.id} canManage={canManage} onChanged={changed} />}
          </div>
        ))}
      </section>
      <section style={{ display: "grid", gap: 8 }}>
        <h3 style={{ margin: 0, fontSize: 14 }}>Documents we have asked for</h3>
        {canManage && !asking && <div><button style={solid} onClick={() => setAsking(true)}>Ask for a document</button></div>}
        {asking && <AskPanel base={base} onDone={() => { setAsking(false); changed() }} onCancel={() => setAsking(false)} />}
        {!requests.isLoading && (requests.data ?? []).length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>You have not asked this client for anything.</div>}
        {(requests.data ?? []).map(r => <RequestRow key={r.id} r={r} canManage={canManage} onChanged={changed} />)}
      </section>
    </div>
  )
}

function AskPanel({ base, onDone, onCancel }: { base: string; onDone: () => void; onCancel: () => void }) {
  const [title, setTitle] = useState("")
  const [description, setDescription] = useState("")
  const [dueDate, setDueDate] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const submit = async () => {
    if (title.trim() === "") { setError("Say what you are asking for"); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${base}/document-requests`, { title: title.trim(), description: description.trim() || null, dueDate: dueDate || null }); onDone() }
    catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, background: "var(--hf-surface-muted)" }}>
      <div><label htmlFor="req-title" style={label}>What do you need?</label><input id="req-title" style={input} value={title} onChange={e => setTitle(e.target.value)} placeholder="For example: Bank statements for March" /></div>
      <div><label htmlFor="req-desc" style={label}>Anything they should know (optional)</label><textarea id="req-desc" rows={2} style={input} value={description} onChange={e => setDescription(e.target.value)} /></div>
      <div><label htmlFor="req-due" style={label}>Needed by (optional)</label><input id="req-due" type="date" style={input} value={dueDate} onChange={e => setDueDate(e.target.value)} /></div>
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }}>{error}</div>}
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={submit}>Ask</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function RequestRow({ r, canManage, onChanged }: { r: StaffRequest; canManage: boolean; onChanged: () => void }) {
  const [action, setAction] = useState<"" | "close" | "cancel">("")
  const [note, setNote] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const go = async () => {
    const p = noteProblem(note); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`/api/v1/compliance-services/document-requests/${r.id}/${action}`, { note: note.trim() }); setAction(""); setNote(""); onChanged() }
    catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={card}>
      <div style={{ display: "flex", gap: 10, alignItems: "baseline", flexWrap: "wrap" }}>
        <strong style={{ fontSize: 13 }}>{r.title}</strong>
        <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{requestStatusLabel(r.status)}{r.dueDate ? ` · needed by ${r.dueDate}` : ""}{r.late ? " · late" : ""}</span>
      </div>
      {r.description && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{r.description}</div>}
      {r.status === "RECEIVED" && <div style={{ fontSize: 12 }}>The client has uploaded a file. Look at it under Documents, then close this request.</div>}
      {r.closeNote && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Note: {r.closeNote}</div>}
      {canManage && isLiveRequest(r) && action === "" && (
        <div style={{ display: "flex", gap: 8 }}><button style={ghost} onClick={() => setAction("close")}>Close</button><button style={ghost} onClick={() => setAction("cancel")}>Cancel request</button></div>
      )}
      {action !== "" && (
        <div style={{ display: "grid", gap: 6 }}>
          <label htmlFor={`note-${r.id}`} style={label}>{action === "close" ? "Why is this done?" : "Why is this no longer needed?"}</label>
          <textarea id={`note-${r.id}`} rows={2} style={input} value={note} onChange={e => setNote(e.target.value)} />
          {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }}>{error}</div>}
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={go}>Confirm</button><button style={ghost} onClick={() => { setAction(""); setError("") }}>Back</button></div>
        </div>
      )}
    </div>
  )
}
