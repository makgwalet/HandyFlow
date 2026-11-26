// src/pages/trainingprovider/TrainProvChecklistsPanel.tsx
//
// The provider's own audit checklists. No checklist is seeded and none holds regulatory content unless the provider typed it. A changed checklist is a
// new version; audits already run keep the questions they were run against. Retired with a reason, never deleted. Administrators only change them.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { SCOPES, emptyTemplate, itemsProblem, label, noteProblem, templateProblem, type Template, type TemplateForm } from "./auditRules"
import { Badge, Field, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, unwrap } from "./tpStyle"
import { LRN } from "./tpApi"

const API = `${LRN}/quality-audits/templates`

function TemplateCard({ t, isAdmin, onChanged }: { t: Template; isAdmin: boolean; onChanged: () => void }) {
  const [mode, setMode] = useState<"" | "version" | "retire">("")
  const [items, setItems] = useState(t.items.join("\n"))
  const [note, setNote] = useState("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const run = async (fn: () => Promise<unknown>) => { setError(""); try { await fn(); setMode(""); onChanged() } catch (e) { setError(errText(e)) } }
  return (
    <div style={{ ...card, display: "grid", gap: 6 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div><strong>{t.name}</strong> <span style={muted}>{t.templateNo} · {label(SCOPES, t.scopeType)} · version {t.latestVersionNo}</span></div>
        <Badge text={t.status === "ACTIVE" ? "In use" : "Retired"} tone={t.status === "ACTIVE" ? "good" : "neutral"} />
      </div>
      <ol style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>{t.items.map((i, n) => <li key={n}>{i}</li>)}</ol>
      {t.retiredReason && <div style={muted}>Retired: {t.retiredReason}</div>}
      {isAdmin && t.status === "ACTIVE" && mode === "" && (
        <div style={{ display: "flex", gap: 8 }}>
          <button style={ghost} onClick={() => { setItems(t.items.join("\n")); setMode("version") }}>New version</button>
          <button style={ghost} onClick={() => setMode("retire")}>Retire</button>
        </div>
      )}
      {mode === "version" && (
        <div style={{ display: "grid", gap: 6 }}>
          <Field label="Items, one per line"><textarea aria-label="Version items" rows={6} style={input} value={items} onChange={e => setItems(e.target.value)} /></Field>
          <Field label="What changed (optional)"><input style={input} value={note} onChange={e => setNote(e.target.value)} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} onClick={() => { const p = itemsProblem(items); if (p) { setError(p); return } void run(() => apiClient.post(`${API}/${t.id}/versions`, { items, changeNote: note.trim() || null })) }}>Save new version</button>
            <button style={ghost} onClick={() => setMode("")}>Cancel</button>
          </div>
        </div>
      )}
      {mode === "retire" && (
        <div style={{ display: "grid", gap: 6 }}>
          <Field label="Why is it retired?"><input style={input} value={reason} onChange={e => setReason(e.target.value)} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} onClick={() => { const p = noteProblem(reason); if (p) { setError(p); return } void run(() => apiClient.post(`${API}/${t.id}/retire`, { reason: reason.trim() })) }}>Retire checklist</button>
            <button style={ghost} onClick={() => setMode("")}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}

export default function TrainProvChecklistsPanel() {
  const qc = useQueryClient()
  const isAdmin = usePermissions(["TRAININGPROVIDER_ADMIN"])
  const { data = [], isLoading } = useQuery<Template[]>({ queryKey: ["tp-audit-templates"], queryFn: async () => unwrap<Template[]>(await apiClient.get(API)) })
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState<TemplateForm>(emptyTemplate)
  const [error, setError] = useState("")
  const refresh = () => qc.invalidateQueries({ queryKey: ["tp-audit-templates"] })
  const save = async () => {
    const p = templateProblem(f); if (p) { setError(p); return }
    try { await apiClient.post(API, { name: f.name.trim(), scopeType: f.scopeType, items: f.items }); setAdding(false); setF(emptyTemplate); setError(""); void refresh() } catch (e) { setError(errText(e)) }
  }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>These are the provider's own checklists. None is seeded, and a checklist holds only what someone typed. A changed checklist is a new version, and audits already run keep the questions they were run against.</div>
      {isAdmin && !adding && <div><button style={solid} onClick={() => setAdding(true)}>Add a checklist</button></div>}
      {!isAdmin && <div style={muted}>Only an administrator can add or change a checklist.</div>}
      {adding && (
        <div style={{ ...card, display: "grid", gap: 8 }}>
          <Field label="Name"><input aria-label="Checklist name" style={input} value={f.name} onChange={e => setF({ ...f, name: e.target.value })} /></Field>
          <Field label="What is it for?"><select aria-label="Checklist scope" style={input} value={f.scopeType} onChange={e => setF({ ...f, scopeType: e.target.value })}>{Object.entries(SCOPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
          <Field label="Items, one per line"><textarea aria-label="Checklist items" rows={6} style={input} value={f.items} onChange={e => setF({ ...f, items: e.target.value })} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={() => void save()}>Save checklist</button><button style={ghost} onClick={() => setAdding(false)}>Cancel</button></div>
        </div>
      )}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No checklists yet.</div> : data.map(t => <TemplateCard key={t.id} t={t} isAdmin={isAdmin} onChanged={() => void refresh()} />)}
    </div>
  )
}
