// The company's letter templates: read them, change the wording, switch them off, put a standard one back, write a new one.
import { useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { CATEGORY_LABEL, templateProblem } from "./letters.logic"
import { card, errorText, input, plain, primary, unwrap, type FieldView, type TemplateView, type Templates } from "./letters.shared"
import { Chip, Field, Notice } from "./ErParts"

const BASE = "/api/v1/hr/letters"

export default function TemplatesPanel({ manage }: { manage: boolean }) {
  const q = useQuery<Templates>({ queryKey: ["letter-templates"], queryFn: async () => unwrap<Templates>(await apiClient.get(`${BASE}/templates`)) })
  const [openId, setOpenId] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)

  if (q.isLoading) return <div style={{ padding: 24, color: "var(--hf-text-faint)" }}>Loading…</div>
  if (q.isError || !q.data) return <Notice kind="error">{errorText(q.error, "The templates could not be loaded.")}</Notice>
  const { templates, fields } = q.data
  const current = templates.find(t => t.id === openId) ?? null

  return (
    <div style={{ display: "grid", gridTemplateColumns: "minmax(220px, 300px) 1fr", gap: 20, alignItems: "start" }}>
      <div style={{ ...card, padding: 8 }} aria-label="Templates">
        {templates.map(t => (
          <button key={t.id} onClick={() => { setOpenId(t.id); setCreating(false) }}
            style={{ display: "block", width: "100%", textAlign: "left", padding: "8px 10px", border: "none", borderRadius: 8, cursor: "pointer", marginBottom: 2,
              background: t.id === openId ? "var(--hf-surface-sunken)" : "transparent", color: "var(--hf-text)", fontSize: 13, opacity: t.active ? 1 : 0.55 }}>
            <strong>{t.name}</strong>
            <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{CATEGORY_LABEL[t.category] ?? t.category}{t.customised ? " · changed" : ""}{t.active ? "" : " · switched off"}</div>
          </button>
        ))}
        {manage && <button onClick={() => { setCreating(true); setOpenId(null) }} style={{ ...plain, width: "100%", marginTop: 6 }}>Write a new template</button>}
      </div>
      <div>
        {creating && <TemplateEditor key="new" template={null} fields={fields} manage={manage} onDone={id => { setCreating(false); setOpenId(id) }} />}
        {!creating && current && <TemplateEditor key={`${current.id}-${current.revision}`} template={current} fields={fields} manage={manage} onDone={setOpenId} />}
        {!creating && !current && <div style={{ ...card, padding: 16, fontSize: 13, color: "var(--hf-text-muted)" }}>
          Choose a template to read or change. The standard wording is a starting point: have your labour adviser read it before you rely on it.
        </div>}
      </div>
    </div>
  )
}

function TemplateEditor({ template, fields, manage, onDone }: { template: TemplateView | null; fields: FieldView[]; manage: boolean; onDone: (id: string) => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState({
    name: template?.name ?? "", category: template?.category ?? "GENERAL", subject: template?.subject ?? "", body: template?.body ?? "",
    requiresAck: template?.requiresAck ?? false, active: template?.active ?? true,
  })
  const [error, setError] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const bodyRef = useRef<HTMLTextAreaElement>(null)
  const problem = templateProblem(f, fields.map(x => x.key))
  const standard = template?.standard ?? false

  const save = useMutation({
    mutationFn: async () => unwrap<TemplateView>(template
      ? await apiClient.put(`${BASE}/templates/${template.id}`, f)
      : await apiClient.post(`${BASE}/templates`, f)),
    onSuccess: t => { qc.invalidateQueries({ queryKey: ["letter-templates"] }); setError(null); setMsg("Saved."); onDone(t.id) },
    onError: e => { setMsg(null); setError(errorText(e, "The template could not be saved.")) },
  })
  const restore = useMutation({
    mutationFn: async () => unwrap<TemplateView>(await apiClient.post(`${BASE}/templates/${template?.id}/restore`, {})),
    onSuccess: t => { qc.invalidateQueries({ queryKey: ["letter-templates"] }); setError(null); setMsg("The standard wording is back."); onDone(t.id) },
    onError: e => { setMsg(null); setError(errorText(e, "The standard wording could not be restored.")) },
  })

  const insert = (key: string) => {
    const el = bodyRef.current
    const token = `{{${key}}}`
    const at = el ? el.selectionStart ?? f.body.length : f.body.length
    const end = el ? el.selectionEnd ?? at : at
    setF(x => ({ ...x, body: x.body.slice(0, at) + token + x.body.slice(end) }))
  }
  const groups = [...new Set(fields.map(x => x.group))]

  return (
    <form aria-label="Template" onSubmit={e => { e.preventDefault(); setMsg(null); if (!problem && manage) save.mutate() }} style={{ ...card, padding: 16 }}>
      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 10, flexWrap: "wrap" }}>
        <h3 style={{ margin: 0, fontSize: 15, flex: 1 }}>{template ? template.name : "A new template"}</h3>
        {template && <Chip text={`Revision ${template.revision}`} color="var(--hf-text-muted)" bg="var(--hf-surface-sunken)" />}
        {standard && <Chip text={template?.customised ? "Standard, changed" : "Standard wording"} color="var(--hf-text-secondary)" bg="var(--hf-surface-sunken)" />}
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 12 }}>
        <Field id="tp-name" text="Name"><input id="tp-name" value={f.name} disabled={!manage} onChange={e => setF({ ...f, name: e.target.value })} style={input} /></Field>
        <Field id="tp-cat" text="Category" hint={standard ? "A standard template stays where the case screens look for it." : undefined}>
          <select id="tp-cat" value={f.category} disabled={!manage || standard} onChange={e => setF({ ...f, category: e.target.value })} style={input}>
            {Object.entries(CATEGORY_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </Field>
      </div>
      <Field id="tp-subject" text="Subject"><input id="tp-subject" value={f.subject} disabled={!manage} onChange={e => setF({ ...f, subject: e.target.value })} style={input} /></Field>
      <Field id="tp-body" text="Wording" hint="Merge fields such as {{employee.name}} are filled in when a letter is prepared. A blank line starts a new paragraph.">
        <textarea id="tp-body" ref={bodyRef} rows={16} value={f.body} disabled={!manage} onChange={e => setF({ ...f, body: e.target.value })} style={{ ...input, fontFamily: "inherit", lineHeight: 1.5 }} />
      </Field>
      <div style={{ display: "flex", gap: 20, flexWrap: "wrap", marginBottom: 12 }}>
        <label style={{ fontSize: 13, display: "flex", gap: 8, alignItems: "center" }}>
          <input type="checkbox" checked={f.requiresAck} disabled={!manage} onChange={e => setF({ ...f, requiresAck: e.target.checked })} /> Ask the employee to confirm they received it
        </label>
        <label style={{ fontSize: 13, display: "flex", gap: 8, alignItems: "center" }}>
          <input type="checkbox" checked={f.active} disabled={!manage} onChange={e => setF({ ...f, active: e.target.checked })} /> In use
        </label>
      </div>
      {manage && (
        <details style={{ marginBottom: 12 }}>
          <summary style={{ fontSize: 12, fontWeight: 600, cursor: "pointer" }}>Merge fields you can add</summary>
          {groups.map(g => (
            <div key={g} style={{ margin: "8px 0" }}>
              <div style={{ fontSize: 11, color: "var(--hf-text-faint)", fontWeight: 700, textTransform: "uppercase" }}>{g}</div>
              <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 4 }}>
                {fields.filter(x => x.group === g).map(x => <button type="button" key={x.key} onClick={() => insert(x.key)} style={{ ...plain, padding: "3px 8px" }} title={x.key}>{x.label}</button>)}
              </div>
            </div>
          ))}
          <p style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Pay, ID numbers, tax numbers and bank details are never available as merge fields. Fields marked "type in" are left as gaps for the person preparing the letter.</p>
        </details>
      )}
      {manage && problem && <Notice kind="error">{problem}</Notice>}
      {error && <Notice kind="error">{error}</Notice>}
      {msg && <Notice kind="ok">{msg}</Notice>}
      {manage ? (
        <div style={{ display: "flex", gap: 8 }}>
          <button type="submit" disabled={!!problem || save.isPending} style={primary}>{save.isPending ? "Saving…" : "Save template"}</button>
          {standard && template?.customised && <button type="button" onClick={() => restore.mutate()} disabled={restore.isPending} style={plain}>Restore the standard wording</button>}
        </div>
      ) : <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Only people who manage letter templates can change them.</div>}
    </form>
  )
}
