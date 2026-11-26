// src/pages/trainingprovider/TrainProvAuditsPanel.tsx
//
// Audits: planned, in progress, completed or cancelled. An audit records what the auditor saw in their own judgement and shows nothing is compliant.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { AUDIT_NOTE, AUDIT_STATUS, SCOPES, auditBody, auditProblem, auditTone, emptyAudit, label, type AuditForm, type AuditSummary, type Template } from "./auditRules"
import { Badge, Field, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { LRN, useActiveProgrammes, useSites } from "./tpApi"
import TrainProvAuditDetail from "./TrainProvAuditDetail"

const API = `${LRN}/quality-audits`

function PlanForm({ onDone, onCancel }: { onDone: (id: string) => void; onCancel: () => void }) {
  const { data: templates = [] } = useQuery<Template[]>({ queryKey: ["tp-audit-templates"], queryFn: async () => unwrap<Template[]>(await apiClient.get(`${API}/templates`)) })
  const { data: sites = [] } = useSites()
  const programmes = useActiveProgrammes()
  const { data: cohorts = [] } = useQuery<{ id: string; cohortNo: string; name: string }[]>({ queryKey: ["tp-cohorts-all"], queryFn: async () => unwrap<{ id: string; cohortNo: string; name: string }[]>(await apiClient.get(`${LRN}/cohorts`)) })
  const [f, setF] = useState<AuditForm>(emptyAudit(todayIso()))
  const [error, setError] = useState("")
  const tpl = templates.find(t => t.id === f.templateId)
  const save = async () => {
    const p = auditProblem(f, tpl?.scopeType ?? null); if (p) { setError(p); return }
    try { const r = unwrap<{ id: string }>(await apiClient.post(API, auditBody(f))); onDone(r.id) } catch (e) { setError(errText(e)) }
  }
  const targets = f.scopeType === "SITE" ? sites.map(s => ({ id: s.id, name: s.name })) : f.scopeType === "COHORT" ? cohorts.map(c => ({ id: c.id, name: `${c.cohortNo} · ${c.name}` }))
    : f.scopeType === "PROGRAMME" ? (programmes.data ?? []).map(p => ({ id: p.id, name: `${p.code} · ${p.title}` })) : []
  return (
    <div style={{ ...card, display: "grid", gap: 8 }}>
      <Field label="Checklist"><select aria-label="Audit checklist" style={input} value={f.templateId} onChange={e => { const t = templates.find(x => x.id === e.target.value); setF({ ...f, templateId: e.target.value, scopeType: t && t.scopeType !== "GENERAL" ? t.scopeType : f.scopeType, targetId: "" }) }}><option value="">Choose…</option>{templates.filter(t => t.status === "ACTIVE").map(t => <option key={t.id} value={t.id}>{t.name} · {t.templateNo}</option>)}</select></Field>
      <Field label="Title"><input aria-label="Audit title" style={input} value={f.title} onChange={e => setF({ ...f, title: e.target.value })} /></Field>
      <Field label="What is being audited?"><select aria-label="Audit scope" style={input} value={f.scopeType} onChange={e => setF({ ...f, scopeType: e.target.value, targetId: "" })}>{Object.entries(SCOPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
      {f.scopeType !== "GENERAL" && <Field label="Which one?"><select aria-label="Audit target" style={input} value={f.targetId} onChange={e => setF({ ...f, targetId: e.target.value })}><option value="">Choose…</option>{targets.map(x => <option key={x.id} value={x.id}>{x.name}</option>)}</select></Field>}
      <Field label="Planned for"><input aria-label="Planned date" type="date" style={input} value={f.plannedOn} onChange={e => setF({ ...f, plannedOn: e.target.value })} /></Field>
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={() => void save()}>Plan audit</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

export default function TrainProvAuditsPanel() {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [status, setStatus] = useState("")
  const [planning, setPlanning] = useState(false)
  const [openId, setOpenId] = useState<string | null>(null)
  const { data = [], isLoading } = useQuery<AuditSummary[]>({
    queryKey: ["tp-audits", status], queryFn: async () => unwrap<AuditSummary[]>(await apiClient.get(API, { params: { status: status || undefined } })),
  })
  if (openId) return <TrainProvAuditDetail id={openId} canManage={canManage} onBack={() => { setOpenId(null); void qc.invalidateQueries({ queryKey: ["tp-audits"] }) }} />
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>{AUDIT_NOTE}</div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <select aria-label="Filter audits by state" style={{ ...input, maxWidth: 200 }} value={status} onChange={e => setStatus(e.target.value)}><option value="">All states</option>{Object.entries(AUDIT_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
        {canManage && !planning && <button style={solid} onClick={() => setPlanning(true)}>Plan an audit</button>}
      </div>
      {planning && <PlanForm onCancel={() => setPlanning(false)} onDone={id => { setPlanning(false); void qc.invalidateQueries({ queryKey: ["tp-audits"] }); setOpenId(id) }} />}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No audits yet.</div> : data.map(a => (
        <button key={a.id} onClick={() => setOpenId(a.id)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{a.title}</strong> <span style={muted}>{a.auditNo}</span>
              <div style={muted}>{label(SCOPES, a.scopeType)}{a.targetName ? ` · ${a.targetName}` : ""} · planned {a.plannedOn} · {a.answered} of {a.items} answered · {a.issues} issue(s) · {a.openActions} open action(s)</div></div>
            <Badge text={label(AUDIT_STATUS, a.status)} tone={auditTone(a.status)} />
          </div>
        </button>
      ))}
    </div>
  )
}
