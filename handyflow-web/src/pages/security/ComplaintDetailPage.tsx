// src/pages/security/ComplaintDetailPage.tsx
//
// One complaint (/security/complaints/:id): the five-step path, details, finding and action, evidence and the
// timeline. The buttons shown come from the server's allowedSteps, so the page never offers a step the server
// would refuse. The system records what people decide; it never changes the guard's employment status.
import { useState } from "react"
import { Link, useParams } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertOctagon, ArrowLeft, Check, Pencil } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import Chip from "../../components/ui/Chip"
import { PageHeader } from "../../components/ui/PageHeader"
import EvidenceFiles from "./EvidenceFiles"
import ComplaintForm from "./ComplaintForm"
import {
  COMPLAINT_SEVERITY_TONE, EVENT_LABELS, FINDINGS, PATH_LABELS, actionLabel, actionsFor, availableSteps, categoryLabel,
  complainantLabel, findingLabel, pathPosition, severityLabel, statusLabel, statusTone, stepFormError, type ComplaintDetail,
} from "./complaints.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)", width: "100%", boxSizing: "border-box" }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 12px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer", textDecoration: "none" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }
const h: React.CSSProperties = { fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, marginBottom: 6 }
const fmt = (d: string) => new Date(d.length === 10 ? d + "T00:00:00" : d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric", timeZone: "Africa/Johannesburg" })
const fmtT = (d: string) => new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })
const errText = (e: any) => e?.response?.data?.message ?? "That did not work. Please try again."

function Path({ status }: { status: string }) {
  const at = pathPosition(status)
  if (at < 0) return <Chip tone="neutral">Withdrawn</Chip>
  return (
    <ol aria-label="Progress" style={{ display: "flex", gap: 6, listStyle: "none", margin: 0, padding: 0, flexWrap: "wrap" }}>
      {PATH_LABELS.map((l, i) => (
        <li key={l} aria-current={i === at ? "step" : undefined} style={{ display: "inline-flex", alignItems: "center", gap: 5, fontSize: 12, fontWeight: 700, padding: "4px 10px", borderRadius: 20,
          background: i <= at ? "var(--hf-accent-soft)" : "var(--hf-surface-sunken)", color: i <= at ? "var(--hf-accent-text)" : "var(--hf-text-muted)" }}>
          {i < at && <Check size={12} />}{l}</li>
      ))}
    </ol>
  )
}

function StepForm({ id, step, finding, onDone, onCancel }: { id: string; step: string; finding: string | null; onDone: () => void; onCancel: () => void }) {
  const [f, setF] = useState({ finding: "", action: "", note: "", investigator: "" })
  const [err, setErr] = useState("")
  const path = { START: "start", FINDING: "finding", ACTION: "action", CLOSE: "close", WITHDRAW: "withdraw" }[step]
  const send = useMutation({
    mutationFn: () => {
      const body = step === "START" ? { investigator: f.investigator.trim() || null }
        : step === "FINDING" ? { finding: f.finding, note: f.note }
        : step === "ACTION" ? { action: f.action, note: f.note.trim() || null }
        : step === "CLOSE" ? { resolutionNote: f.note } : { reason: f.note }
      return apiClient.post(`/api/v1/security/complaints/${id}/${path}`, body)
    },
    onSuccess: onDone, onError: e => setErr(errText(e)),
  })
  const submit = () => { const m = stepFormError(step, { ...f, finding: step === "ACTION" ? finding ?? "" : f.finding }); if (m) setErr(m); else { setErr(""); send.mutate() } }
  const noteLabel = { FINDING: "What the investigation found", ACTION: "Note (required if no action is taken on a substantiated complaint)", CLOSE: "How it was resolved", WITHDRAW: "Reason for withdrawing" }[step]
  return (
    <div style={{ ...card, display: "grid", gap: 10, marginTop: 12 }}>
      {step === "START" && <label style={{ fontSize: 12 }}>Investigator (optional)<input value={f.investigator} onChange={e => setF({ ...f, investigator: e.target.value })} style={input} /></label>}
      {step === "FINDING" && <label style={{ fontSize: 12 }}>Finding
        <select value={f.finding} onChange={e => setF({ ...f, finding: e.target.value })} style={input}>
          <option value="">Choose</option>{FINDINGS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label>}
      {step === "ACTION" && <label style={{ fontSize: 12 }}>Action taken
        <select value={f.action} onChange={e => setF({ ...f, action: e.target.value })} style={input}>
          <option value="">Choose</option>{actionsFor(finding).map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label>}
      {step !== "START" && <label style={{ fontSize: 12 }}>{noteLabel}<textarea value={f.note} onChange={e => setF({ ...f, note: e.target.value })} rows={3} style={{ ...input, resize: "vertical" }} /></label>}
      {step === "ACTION" && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>This records what people have decided. Suspensions and hearings are carried out outside this page, and the guard's status is not changed here.</div>}
      {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button onClick={submit} disabled={send.isPending} style={primary}>Confirm</button>
        <button onClick={onCancel} style={btn}>Cancel</button>
      </div>
    </div>
  )
}

export default function ComplaintDetailPage() {
  const { id } = useParams<{ id: string }>()
  const qc = useQueryClient()
  const canManage = usePermission("SECURITY_MANAGE") || usePermission("SECURITY_ADMIN")
  const [step, setStep] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [fileErr, setFileErr] = useState("")
  const { data, isLoading, error } = useQuery<ComplaintDetail>({
    queryKey: ["complaint", id],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/complaints/${id}`); return r.data?.data ?? r.data },
    enabled: !!id,
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["complaint", id] }); qc.invalidateQueries({ queryKey: ["complaints"] }); qc.invalidateQueries({ queryKey: ["guard-overview"] }) }
  const header = (title: string) => <PageHeader title={title} icon={AlertOctagon} breadcrumbs={[{ label: "Security", to: "/security/dashboard" }, { label: "Complaints", to: "/security/complaints" }, { label: title }]} />

  if (isLoading) return <div>{header("Complaint")}<div style={{ color: "var(--hf-text-muted)" }}>Loading complaint...</div></div>
  if (error || !data) return (
    <div>{header("Complaint")}<div style={{ ...card, color: "var(--hf-danger-text)" }}>This complaint could not be loaded. It may not exist, or you may not have access.</div>
      <Link to="/security/complaints" style={{ ...btn, marginTop: 12 }}><ArrowLeft size={14} /> Back to complaints</Link></div>
  )

  const s = data.summary
  const steps = canManage ? availableSteps(data.allowedSteps) : []
  const row = (label: string, value: React.ReactNode) => (
    <div style={{ minWidth: 0 }}><div style={{ fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 600 }}>{label}</div>
      <div style={{ fontSize: 14, marginTop: 2, overflowWrap: "anywhere" }}>{value || "-"}</div></div>
  )

  return (
    <div>
      {header(s.complaintNumber)}
      <div style={{ ...card, marginBottom: 16 }}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
          <div style={{ fontSize: 18, fontWeight: 800 }}>{categoryLabel(s.category)}</div>
          <Chip tone={COMPLAINT_SEVERITY_TONE[s.severity] ?? "neutral"}>{severityLabel(s.severity)}</Chip>
          <Chip tone={statusTone(s.status, s.finding)}>{statusLabel(s.status)}</Chip>
          {s.urgent && <Chip tone="bad" icon={<AlertOctagon size={11} />}>urgent review</Chip>}
        </div>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "6px 0 12px" }}>
          About <Link to={`/security/guards/${s.guardId}`} style={{ color: "var(--hf-accent-text)", fontWeight: 600 }}>{s.guardName ?? "the guard"}</Link>
          {s.siteName ? ` at ${s.siteName}` : ""} · happened {fmt(s.occurredOn)}</div>
        <Path status={s.status} />
        {canManage && (steps.length > 0 || data.editable) && !step && !editing && (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 14 }}>
            {steps.map(x => <button key={x.step} onClick={() => setStep(x.step)} style={x.step === "WITHDRAW" ? btn : primary}>{x.label}</button>)}
            {data.editable && <button onClick={() => setEditing(true)} style={btn}><Pencil size={14} /> Edit details</button>}
          </div>
        )}
        {step && <StepForm id={s.id} step={step} finding={s.finding} onDone={() => { setStep(null); refresh() }} onCancel={() => setStep(null)} />}
      </div>

      {editing && <div style={{ marginBottom: 16 }}><ComplaintForm existing={data} onClose={() => setEditing(false)} /></div>}

      <div style={{ display: "grid", gap: 16, gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))" }}>
        <div style={card}>
          <div style={h}>What was reported</div>
          <div style={{ fontSize: 14, whiteSpace: "pre-wrap", overflowWrap: "anywhere", marginBottom: 12 }}>{data.description}</div>
          <div style={{ display: "grid", gap: 10, gridTemplateColumns: "1fr 1fr" }}>
            {row("Complainant", `${complainantLabel(data.complainantType)}${data.complainantName ? `, ${data.complainantName}` : ""}`)}
            {row("Contact", data.complainantContact)}
            {row("Witnesses", data.witnesses)}
            {row("Logged by", data.createdByName)}
          </div>
        </div>

        <div style={card}>
          <div style={h}>Investigation and outcome</div>
          <div style={{ display: "grid", gap: 10 }}>
            {row("Investigator", data.investigatorName)}
            {row("Finding", s.finding ? <><b>{findingLabel(s.finding)}</b>{data.findingNote ? `: ${data.findingNote}` : ""}{data.findingByName ? ` (${data.findingByName}, ${data.findingAt ? fmt(data.findingAt) : ""})` : ""}</> : null)}
            {row("Action", s.action ? <><b>{actionLabel(s.action)}</b>{data.actionNote ? `: ${data.actionNote}` : ""}{data.actionByName ? ` (${data.actionByName})` : ""}</> : null)}
            {row("Resolution", data.resolutionNote ? `${data.resolutionNote}${data.closedByName ? ` (${data.closedByName}, ${data.closedAt ? fmt(data.closedAt) : ""})` : ""}` : null)}
            {data.withdrawnReason && row("Withdrawn because", data.withdrawnReason)}
          </div>
        </div>
      </div>

      <div style={{ ...card, marginTop: 16 }}>
        <div style={h}>Evidence</div>
        {fileErr && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 6 }}>{fileErr}</div>}
        <EvidenceFiles baseUrl={`/api/v1/security/complaints/${s.id}/evidence`} items={data.evidence} canManage={canManage && s.open}
          emptyHint="No evidence attached yet." onChanged={() => { setFileErr(""); refresh() }} onError={setFileErr} />
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
