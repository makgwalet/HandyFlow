// src/pages/complianceservices/ClientQualityTab.tsx
//
// Reviews of this client's finished work. Choose the work and a checklist; a second person marks each check and approves or sends it back.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import QualityReviewList from "./QualityReviewList"
import { SUBJECT_TYPES, subjectLabel, templatesFor, type ReviewSummary, type Template } from "./qualityRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }
const solid: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const bad: React.CSSProperties = { fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }

type Option = { id: string; label: string }

export default function ClientQualityTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const [starting, setStarting] = useState(false)
  const { data = [], isLoading } = useQuery<ReviewSummary[]>({
    queryKey: ["cs-quality-client", clientId],
    queryFn: async () => unwrap<ReviewSummary[]>(await apiClient.get(`/api/v1/compliance-services/clients/${clientId}/quality-reviews`)),
  })
  const changed = () => { qc.invalidateQueries({ queryKey: ["cs-quality-client", clientId] }); qc.invalidateQueries({ queryKey: ["cs-quality-all"] }); qc.invalidateQueries({ queryKey: ["cs-work-queue"] }) }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
        A second pair of eyes on finished work. Each check is marked, and the work is approved or sent back. A review checks the work; it never changes it.
      </div>
      {canManage && !starting && <div><button style={solid} onClick={() => setStarting(true)}>Start a review</button></div>}
      {starting && <StartPanel clientId={clientId} onDone={() => { setStarting(false); changed() }} onCancel={() => setStarting(false)} />}
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && data.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Nothing has been reviewed for this client yet.</div>}
      <QualityReviewList reviews={data} canManage={canManage} onChanged={changed} />
    </div>
  )
}

function StartPanel({ clientId, onDone, onCancel }: { clientId: string; onDone: () => void; onCancel: () => void }) {
  const base = `/api/v1/compliance-services/clients/${clientId}`
  const [type, setType] = useState("ASSESSMENT")
  const [subjectId, setSubjectId] = useState("")
  const [text, setText] = useState("")
  const [templateId, setTemplateId] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: all = [] } = useQuery<Template[]>({ queryKey: ["cs-quality-templates"], queryFn: async () => unwrap<Template[]>(await apiClient.get("/api/v1/compliance-services/quality/templates")) })
  const { data: options = [] } = useQuery<Option[]>({
    queryKey: ["cs-quality-subjects", clientId, type], enabled: type !== "OTHER",
    queryFn: async () => {
      if (type === "ASSESSMENT") return unwrap<{ id: string; status: string; asOf: string }[]>(await apiClient.get(`${base}/assessments`)).filter(a => a.status === "COMPLETED").map(a => ({ id: a.id, label: `Assessment as of ${a.asOf}` }))
      if (type === "CASE") return unwrap<{ id: string; title: string; status: string }[]>(await apiClient.get(`${base}/cases`)).filter(c => c.status === "RESOLVED" || c.status === "CLOSED").map(c => ({ id: c.id, label: c.title }))
      return unwrap<{ id: string; quoteNumber: string; title: string | null }[]>(await apiClient.get(`${base}/quotes`)).map(q => ({ id: q.id, label: `Quote ${q.quoteNumber}${q.title ? ` · ${q.title}` : ""}` }))
    },
  })
  const checklists = templatesFor(all, type)
  const ready = !!templateId && (type === "OTHER" ? text.trim().length > 0 : !!subjectId)
  const start = async () => {
    setBusy(true); setError("")
    try {
      await apiClient.post(`${base}/quality-reviews`, { subjectType: type, subjectId: type === "OTHER" ? null : subjectId, subjectLabel: type === "OTHER" ? text.trim() : null, templateId })
      onDone()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 8, border: "1px solid var(--hf-border)", borderRadius: 10, padding: 10 }}>
      <div>
        <label style={label} htmlFor="q-type">Kind of work</label>
        <select id="q-type" style={input} value={type} onChange={e => { setType(e.target.value); setSubjectId(""); setTemplateId("") }}>
          {SUBJECT_TYPES.map(t => <option key={t[0]} value={t[0]}>{t[1]}</option>)}
        </select>
      </div>
      {type === "OTHER" ? (
        <div><label style={label} htmlFor="q-text">Describe the work</label><input id="q-text" style={input} value={text} onChange={e => setText(e.target.value)} /></div>
      ) : (
        <div>
          <label style={label} htmlFor="q-subject">{subjectLabel(type)} to review</label>
          <select id="q-subject" style={input} value={subjectId} onChange={e => setSubjectId(e.target.value)}>
            <option value="">Choose...</option>
            {options.map(o => <option key={o.id} value={o.id}>{o.label}</option>)}
          </select>
          {options.length === 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 4 }}>{type === "ASSESSMENT" ? "No completed assessments." : type === "CASE" ? "No resolved or closed cases." : "No quotes."}</div>}
        </div>
      )}
      <div>
        <label style={label} htmlFor="q-template">Checklist</label>
        <select id="q-template" style={input} value={templateId} onChange={e => setTemplateId(e.target.value)}>
          <option value="">Choose...</option>
          {checklists.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
        </select>
        {checklists.length === 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 4 }}>No checklist for this kind of work yet. An administrator can add one on the Quality page.</div>}
      </div>
      {error && <div style={bad}>{error}</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button style={solid} disabled={busy || !ready} onClick={start}>Start review</button>
        <button style={ghost} onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}
