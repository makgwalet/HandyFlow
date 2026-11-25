// src/pages/complianceservices/QualityPage.tsx
//
// Quality across all clients: every review, the checklists (administrators), and random sampling of finished work (administrators).
// Nothing here scores a client or changes the work being checked.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission, usePermissions } from "../../hooks/usePermission"
import QualityReviewList from "./QualityReviewList"
import {
  SAMPLEABLE, STATUS_FILTERS, SUBJECT_TYPES, blankTemplate, sampleProblem, subjectLabel, templateBody, templateForm, templateProblem, templatesFor,
  type ReviewSummary, type Template, type TemplateForm,
} from "./qualityRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const B = "/api/v1/compliance-services/quality"
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }
const solid: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const chip = (on: boolean): React.CSSProperties => ({ padding: "5px 12px", borderRadius: 999, border: "1px solid var(--hf-border)", background: on ? "var(--hf-success-solid-strong)" : "transparent", color: on ? "#fff" : "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" })
const bad: React.CSSProperties = { fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }

export default function QualityPage() {
  const isAdmin = usePermission("COMPLIANCE_SERVICES_ADMIN")
  const [view, setView] = useState<"reviews" | "checklists" | "sample">("reviews")
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div style={{ display: "flex", gap: 6 }}>
        <button style={chip(view === "reviews")} onClick={() => setView("reviews")}>Reviews</button>
        {isAdmin && <button style={chip(view === "checklists")} onClick={() => setView("checklists")}>Checklists</button>}
        {isAdmin && <button style={chip(view === "sample")} onClick={() => setView("sample")}>Random sample</button>}
      </div>
      {view === "reviews" && <Reviews />}
      {view === "checklists" && isAdmin && <Checklists />}
      {view === "sample" && isAdmin && <Sample />}
    </div>
  )
}

function Reviews() {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const [status, setStatus] = useState("")
  const { data = [], isLoading } = useQuery<ReviewSummary[]>({
    queryKey: ["cs-quality-all", status],
    queryFn: async () => unwrap<ReviewSummary[]>(await apiClient.get(`${B}/reviews`, { params: status ? { status } : {} })),
  })
  const changed = () => { qc.invalidateQueries({ queryKey: ["cs-quality-all"] }); qc.invalidateQueries({ queryKey: ["cs-quality-client"] }); qc.invalidateQueries({ queryKey: ["cs-work-queue"] }) }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
        Reviews of finished work across all clients. Start one from the client's Quality tab, or ask an administrator to pick a random sample.
      </div>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
        {STATUS_FILTERS.map(f => <button key={f[0]} style={chip(status === f[0])} onClick={() => setStatus(f[0])}>{f[1]}</button>)}
      </div>
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && data.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No reviews here.</div>}
      <QualityReviewList reviews={data} canManage={canManage} showClient onChanged={changed} />
    </div>
  )
}

function Checklists() {
  const qc = useQueryClient()
  const [editing, setEditing] = useState<{ id: string | null; form: TemplateForm } | null>(null)
  const { data = [], isLoading } = useQuery<Template[]>({ queryKey: ["cs-quality-templates"], queryFn: async () => unwrap<Template[]>(await apiClient.get(`${B}/templates`)) })
  const [error, setError] = useState("")
  const refresh = () => qc.invalidateQueries({ queryKey: ["cs-quality-templates"] })
  const toggle = async (t: Template) => {
    setError("")
    try { await apiClient.put(`${B}/templates/${t.id}/active`, { active: !t.active }); refresh() } catch (e) { setError(errText(e)) }
  }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
        Checklists for each kind of work. Changing one never changes reviews already done; they keep the wording they were done with.
      </div>
      {!editing && <div><button style={solid} onClick={() => setEditing({ id: null, form: blankTemplate() })}>New checklist</button></div>}
      {editing && <TemplateEditor id={editing.id} start={editing.form} onDone={() => { setEditing(null); refresh() }} onCancel={() => setEditing(null)} />}
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && data.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No checklists yet.</div>}
      {data.map(t => (
        <div key={t.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "8px 10px", display: "grid", gap: 4 }}>
          <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
            <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{t.name}</span>
            <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{subjectLabel(t.subjectType)} · {t.items.length} checks{t.active ? "" : " · switched off"}</span>
            <span style={{ flex: 1 }} />
            <button style={ghost} onClick={() => setEditing({ id: t.id, form: templateForm(t) })}>Edit</button>
            <button style={ghost} onClick={() => toggle(t)}>{t.active ? "Switch off" : "Switch on"}</button>
          </div>
          {t.description && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{t.description}</div>}
        </div>
      ))}
      {error && <div style={bad}>{error}</div>}
    </div>
  )
}

function TemplateEditor({ id, start, onDone, onCancel }: { id: string | null; start: TemplateForm; onDone: () => void; onCancel: () => void }) {
  const [f, setF] = useState<TemplateForm>(start)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const problem = templateProblem(f)
  const setItem = (i: number, patch: Partial<TemplateForm["items"][number]>) => setF({ ...f, items: f.items.map((x, n) => (n === i ? { ...x, ...patch } : x)) })
  const save = async () => {
    setBusy(true); setError("")
    try { if (id) await apiClient.put(`${B}/templates/${id}`, templateBody(f)); else await apiClient.post(`${B}/templates`, templateBody(f)); onDone() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 8, border: "1px solid var(--hf-border)", borderRadius: 10, padding: 10 }}>
      <div><label style={label} htmlFor="t-name">Name</label><input id="t-name" style={input} value={f.name} onChange={e => setF({ ...f, name: e.target.value })} /></div>
      <div>
        <label style={label} htmlFor="t-type">Kind of work</label>
        <select id="t-type" style={input} value={f.subjectType} onChange={e => setF({ ...f, subjectType: e.target.value })}>
          {SUBJECT_TYPES.map(t => <option key={t[0]} value={t[0]}>{t[1]}</option>)}
        </select>
      </div>
      <div><label style={label} htmlFor="t-desc">What it is for (optional)</label><input id="t-desc" style={input} value={f.description} onChange={e => setF({ ...f, description: e.target.value })} /></div>
      <div style={{ display: "grid", gap: 6 }}>
        <span style={label}>Checks</span>
        {f.items.map((it, i) => (
          <div key={i} style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <input style={input} aria-label={`Check ${i + 1}`} value={it.text} onChange={e => setItem(i, { text: e.target.value })} />
            <label style={{ fontSize: 12, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }}><input type="checkbox" checked={it.critical} onChange={e => setItem(i, { critical: e.target.checked })} /> Critical</label>
            <button style={ghost} aria-label={`Remove check ${i + 1}`} onClick={() => setF({ ...f, items: f.items.filter((_, n) => n !== i) })}>Remove</button>
          </div>
        ))}
        <div><button style={ghost} onClick={() => setF({ ...f, items: [...f.items, { text: "", critical: false }] })}>Add a check</button></div>
        <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>A critical check that fails stops the work being approved.</div>
      </div>
      {error && <div style={bad}>{error}</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button style={solid} disabled={busy || !!problem} title={problem ?? undefined} onClick={save}>Save checklist</button>
        <button style={ghost} onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}

function Sample() {
  const qc = useQueryClient()
  const [type, setType] = useState("ASSESSMENT")
  const [from, setFrom] = useState(() => { const d = new Date(); d.setDate(d.getDate() - 30); return d.toISOString().slice(0, 10) })
  const [to, setTo] = useState(() => new Date().toISOString().slice(0, 10))
  const [rate, setRate] = useState("10")
  const [templateId, setTemplateId] = useState("")
  const [error, setError] = useState("")
  const [result, setResult] = useState<{ pool: number; started: number } | null>(null)
  const [busy, setBusy] = useState(false)
  const { data: all = [] } = useQuery<Template[]>({ queryKey: ["cs-quality-templates"], queryFn: async () => unwrap<Template[]>(await apiClient.get(`${B}/templates`)) })
  const checklists = templatesFor(all, type)
  const problem = sampleProblem(from, to, Number(rate), templateId)
  const run = async () => {
    setBusy(true); setError(""); setResult(null)
    try {
      setResult(unwrap<{ pool: number; started: number }>(await apiClient.post(`${B}/sample`, { subjectType: type, from, to, ratePercent: Number(rate), templateId })))
      qc.invalidateQueries({ queryKey: ["cs-quality-all"] }); qc.invalidateQueries({ queryKey: ["cs-work-queue"] })
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 8, maxWidth: 520 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
        Picks finished work at random so a share of it gets a second look. Work that has been reviewed before, and work for clients who are no longer active, is left out.
      </div>
      <div>
        <label style={label} htmlFor="s-type">Kind of work</label>
        <select id="s-type" style={input} value={type} onChange={e => { setType(e.target.value); setTemplateId("") }}>{SAMPLEABLE.map(t => <option key={t[0]} value={t[0]}>{t[1]}</option>)}</select>
      </div>
      <div style={{ display: "flex", gap: 8 }}>
        <div style={{ flex: 1 }}><label style={label} htmlFor="s-from">Finished from</label><input id="s-from" type="date" style={input} value={from} onChange={e => setFrom(e.target.value)} /></div>
        <div style={{ flex: 1 }}><label style={label} htmlFor="s-to">to</label><input id="s-to" type="date" style={input} value={to} onChange={e => setTo(e.target.value)} /></div>
      </div>
      <div><label style={label} htmlFor="s-rate">Share to pick (percent)</label><input id="s-rate" type="number" min={1} max={100} style={input} value={rate} onChange={e => setRate(e.target.value)} /></div>
      <div>
        <label style={label} htmlFor="s-template">Checklist</label>
        <select id="s-template" style={input} value={templateId} onChange={e => setTemplateId(e.target.value)}>
          <option value="">Choose...</option>
          {checklists.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
        </select>
      </div>
      {error && <div style={bad}>{error}</div>}
      {result && <div style={{ fontSize: 13, color: "var(--hf-text)" }}>{result.started === 0 ? `Nothing to pick: ${result.pool} finished ${result.pool === 1 ? "item" : "items"} were available.` : `Picked ${result.started} of ${result.pool} finished items. They are now in the reviews list.`}</div>}
      <div><button style={solid} disabled={busy || !!problem} title={problem ?? undefined} onClick={run}>Pick a sample</button></div>
    </div>
  )
}
