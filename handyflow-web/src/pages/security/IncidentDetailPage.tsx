// src/pages/security/IncidentDetailPage.tsx
//
// One incident (/security/incidents/:id): summary, assignee, evidence, the timeline and the actions available now.
// The buttons come from the server's allowedActions, so the page never offers something the server would refuse.
// The report is the incident PDF, which includes the timeline and the evidence on file.
import { useState } from "react"
import { Link, useParams } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertTriangle, ArrowLeft, FileText } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import Chip from "../../components/ui/Chip"
import { PageHeader } from "../../components/ui/PageHeader"
import EvidenceFiles from "./EvidenceFiles"
import {
  EVENT_LABELS, SEVERITY_TONE, STATUS_TONE, actionFormError, availableActions, elapsed, severitiesAbove, titleCase, type IncidentCase,
} from "./incident.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)", width: "100%", boxSizing: "border-box" }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 12px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer", textDecoration: "none" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }
const h: React.CSSProperties = { fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, marginBottom: 8 }
const fmtT = (d: string) => new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })
const errText = (e: any) => e?.response?.data?.message ?? "That did not work. Please try again."

const TEXT_LABEL: Record<string, string> = {
  ASSIGN: "Who is dealing with this", ESCALATE: "Why is it being escalated", NOTE: "Note", REOPEN: "Why is it being reopened", RESOLVE: "How it was dealt with (optional)",
}
const PATH: Record<string, string> = { ASSIGN: "assign", ESCALATE: "escalate", NOTE: "notes", REOPEN: "reopen", RESOLVE: "resolve" }

function ActionForm({ id, action, severity, onDone, onCancel }: { id: string; action: string; severity: string; onDone: () => void; onCancel: () => void }) {
  const [text, setText] = useState("")
  const [level, setLevel] = useState("")
  const [err, setErr] = useState("")
  const above = severitiesAbove(severity)
  const send = useMutation({
    mutationFn: () => {
      const body = action === "ASSIGN" ? { assigneeName: text.trim() }
        : action === "ESCALATE" ? { severity: level || null, reason: text.trim() }
        : action === "NOTE" ? { note: text.trim() }
        : action === "REOPEN" ? { reason: text.trim() } : { note: text.trim() || null }
      return apiClient.post(`/api/v1/security/incidents/${id}/${PATH[action]}`, body)
    },
    onSuccess: onDone, onError: e => setErr(errText(e)),
  })
  const submit = () => { const m = actionFormError(action, { text }); if (m) setErr(m); else { setErr(""); send.mutate() } }
  return (
    <div style={{ ...card, display: "grid", gap: 10, marginTop: 12 }}>
      {action === "ESCALATE" && (
        <label style={{ fontSize: 12 }}>New severity
          <select value={level} onChange={e => setLevel(e.target.value)} style={input}>
            <option value="">One level up ({titleCase(above[0])})</option>
            {above.map(s => <option key={s} value={s}>{titleCase(s)}</option>)}</select></label>
      )}
      <label style={{ fontSize: 12 }}>{TEXT_LABEL[action]}
        {action === "ASSIGN" ? <input value={text} onChange={e => setText(e.target.value)} style={input} />
          : <textarea value={text} onChange={e => setText(e.target.value)} rows={3} style={{ ...input, resize: "vertical" }} />}</label>
      {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button onClick={submit} disabled={send.isPending} style={primary}>Confirm</button>
        <button onClick={onCancel} style={btn}>Cancel</button>
      </div>
    </div>
  )
}

export default function IncidentDetailPage() {
  const { id } = useParams<{ id: string }>()
  const qc = useQueryClient()
  const canManage = usePermission("SECURITY_MANAGE") || usePermission("SECURITY_ADMIN")
  const [action, setAction] = useState<string | null>(null)
  const [msg, setMsg] = useState("")
  const { data, isLoading, error } = useQuery<IncidentCase>({
    queryKey: ["incident", id],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/incidents/${id}`); return r.data?.data ?? r.data },
    enabled: !!id,
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["incident", id] }); qc.invalidateQueries({ queryKey: ["incidents"] }) }
  const ack = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/security/incidents/${id}/acknowledge`),
    onSuccess: () => { setMsg(""); refresh() }, onError: e => setMsg(errText(e)),
  })
  async function report() {
    try {
      const r = await apiClient.get(`/api/v1/security/incidents/${id}/pdf`, { responseType: "blob" })
      window.open(URL.createObjectURL(new Blob([r.data], { type: "application/pdf" })), "_blank")
    } catch (e) { setMsg(errText(e)) }
  }
  const header = (title: string) => <PageHeader title={title} icon={AlertTriangle} breadcrumbs={[{ label: "Security", to: "/security/dashboard" }, { label: "Incidents", to: "/security/incidents" }, { label: title }]} />

  if (isLoading) return <div>{header("Incident")}<div style={{ color: "var(--hf-text-muted)" }}>Loading incident...</div></div>
  if (error || !data) return (
    <div>{header("Incident")}<div style={{ ...card, color: "var(--hf-danger-text)" }}>This incident could not be loaded. It may not exist, or you may not have access.</div>
      <Link to="/security/incidents" style={{ ...btn, marginTop: 12 }}><ArrowLeft size={14} /> Back to incidents</Link></div>
  )

  const i = data.incident
  const buttons = canManage ? availableActions(data.allowedActions) : []
  const open = i.status !== "RESOLVED"
  const toAck = elapsed(i.reportedAt, i.acknowledgedAt), toRes = elapsed(i.reportedAt, i.resolvedAt)
  const row = (label: string, value: React.ReactNode) => (
    <div style={{ minWidth: 0 }}><div style={{ fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 600 }}>{label}</div>
      <div style={{ fontSize: 14, marginTop: 2, overflowWrap: "anywhere" }}>{value || "-"}</div></div>
  )

  return (
    <div>
      {header(i.title)}
      <div style={{ ...card, marginBottom: 16 }}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
          <div style={{ fontSize: 18, fontWeight: 800 }}>{i.title}</div>
          <Chip tone={SEVERITY_TONE[i.severity] ?? "neutral"}>{titleCase(i.severity)}</Chip>
          <Chip tone={STATUS_TONE[i.status] ?? "neutral"}>{titleCase(i.status)}</Chip>
          {i.type && i.type !== "GENERAL" && <Chip tone="accent">{titleCase(i.type)}</Chip>}
        </div>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "6px 0 12px" }}>
          Reported {fmtT(i.reportedAt)}{i.siteName ? ` · ${i.siteName}` : ""}
          {i.guardId && <> · by <Link to={`/security/guards/${i.guardId}`} style={{ color: "var(--hf-accent-text)", fontWeight: 600 }}>{i.guardName ?? "guard"}</Link></>}
        </div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {buttons.map(b => b.action === "ACKNOWLEDGE"
            ? <button key={b.action} onClick={() => ack.mutate()} disabled={ack.isPending} style={primary}>{b.label}</button>
            : !action && <button key={b.action} onClick={() => setAction(b.action)} style={b.action === "RESOLVE" ? primary : btn}>{b.label}</button>)}
          <button onClick={report} style={btn}><FileText size={14} /> Report (PDF)</button>
        </div>
        {msg && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginTop: 8 }}>{msg}</div>}
        {action && <ActionForm id={i.id} action={action} severity={i.severity} onDone={() => { setAction(null); refresh() }} onCancel={() => setAction(null)} />}
      </div>

      <div style={{ display: "grid", gap: 16, gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))" }}>
        <div style={card}>
          <div style={h}>What happened</div>
          <div style={{ fontSize: 14, whiteSpace: "pre-wrap", overflowWrap: "anywhere", marginBottom: 12 }}>{i.description || "No description recorded."}</div>
          <div style={{ display: "grid", gap: 10, gridTemplateColumns: "1fr 1fr" }}>
            {row("Type", titleCase(i.type))}
            {row("Location", i.latitude != null && i.longitude != null ? `${i.latitude}, ${i.longitude}` : null)}
          </div>
        </div>
        <div style={card}>
          <div style={h}>Handling</div>
          <div style={{ display: "grid", gap: 10, gridTemplateColumns: "1fr 1fr" }}>
            {row("Assigned to", data.assigneeName)}
            {row("Status", titleCase(i.status))}
            {row("Time to acknowledge", toAck)}
            {row("Time to resolve", toRes)}
          </div>
        </div>
      </div>

      <div style={{ ...card, marginTop: 16 }}>
        <div style={h}>Evidence</div>
        <EvidenceFiles baseUrl={`/api/v1/security/incidents/${i.id}/evidence`} items={data.evidence} canManage={canManage && open}
          emptyHint="No photos or files attached yet." onChanged={refresh} onError={setMsg} />
      </div>

      <div style={{ ...card, marginTop: 16 }}>
        <div style={h}>Timeline</div>
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 10 }}>
          {[...data.events].reverse().map(e => (
            <li key={e.id} style={{ fontSize: 13 }}>
              <b>{EVENT_LABELS[e.eventType] ?? e.eventType}</b> <span style={{ color: "var(--hf-text-muted)" }}>· {fmtT(e.at)}{e.byName ? ` · ${e.byName}` : ""}</span>
              {e.note && <div style={{ color: "var(--hf-text-secondary)", overflowWrap: "anywhere" }}>{e.note}</div>}
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
