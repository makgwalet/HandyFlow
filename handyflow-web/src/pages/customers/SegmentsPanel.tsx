// src/pages/customers/SegmentsPanel.tsx
// Saved groups of customers. Each segment is a name and conditions; the server works out who is in it, so the count is always current.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { responseMessage } from "./apiError"
import { FIELDS, MAX_CONDITIONS, UNASSIGNED, allFields, countText, describe, fieldDef, needsValue, newCondition, segmentProblem, toRequest, withField, type Condition, type FieldId, type Members, type SegmentRow } from "./segments.logic"
import type { CustomField } from "./customFields.logic"
import { Loading, ErrorNote } from "./CrmKit"

interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
const inner = (r: { data: unknown }): unknown => { const o = r.data as { data?: unknown } | null; return o && !Array.isArray(o) && typeof o === "object" && "data" in o ? o.data : r.data }
const field: React.CSSProperties = { padding: "5px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const button: React.CSSProperties = { ...field, cursor: "pointer", fontWeight: 600 }
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 12, padding: "12px 14px", background: "var(--hf-surface)" }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const failure = (e: unknown) => responseMessage(e) ?? "Something went wrong. Try again."

export default function SegmentsPanel() {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE")
  const [editing, setEditing] = useState<{ id: string | null; name: string; conditions: Condition[] } | null>(null)
  const [viewing, setViewing] = useState<string | null>(null), [error, setError] = useState<string | null>(null), [preview, setPreview] = useState<Members | null>(null)
  const segments = useQuery<SegmentRow[]>({ queryKey: ["crm", "segments"], queryFn: async () => { const d = inner(await apiClient.get("/api/v1/crm/segments")); return Array.isArray(d) ? (d as SegmentRow[]) : [] } })
  const users = useQuery<UserRow[]>({ queryKey: ["crm", "segment-users"], retry: false, queryFn: async () => { const d = inner(await apiClient.get("/api/v1/identity/users", { params: { size: 200 } })) as { content?: UserRow[] } | UserRow[] | null; return Array.isArray(d) ? d : d?.content ?? [] } })
  const customFields = useQuery<CustomField[]>({ queryKey: ["crm", "custom-fields"], retry: false, queryFn: async () => { const d = inner(await apiClient.get("/api/v1/crm/custom-fields")); return Array.isArray(d) ? (d as CustomField[]) : [] } })
  const defs = allFields(customFields.data ?? [])
  const members = useQuery<Members | null>({ queryKey: ["crm", "segment-members", viewing], enabled: !!viewing, queryFn: async () => (inner(await apiClient.get(`/api/v1/crm/segments/${viewing}/members`)) as Members | null) ?? null })
  const owners = Object.fromEntries((users.data ?? []).map(u => [u.id, [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email || "Colleague"]))
  const done = () => { setError(null); setEditing(null); setPreview(null); qc.invalidateQueries({ queryKey: ["crm", "segments"] }); qc.invalidateQueries({ queryKey: ["crm", "segment-members"] }) }
  const save = useMutation({ mutationFn: (v: { id: string | null; name: string; conditions: Condition[] }) => v.id ? apiClient.put(`/api/v1/crm/segments/${v.id}`, toRequest(v.name, v.conditions, defs)) : apiClient.post("/api/v1/crm/segments", toRequest(v.name, v.conditions, defs)), onSuccess: done, onError: e => setError(failure(e)) })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/segments/${id}`), onSuccess: (_r, id) => { if (viewing === id) setViewing(null); done() }, onError: e => setError(failure(e)) })
  const check = useMutation({ mutationFn: (v: { name: string; conditions: Condition[] }) => apiClient.post("/api/v1/crm/segments/preview", toRequest(v.name || "preview", v.conditions, defs)), onSuccess: r => { setError(null); setPreview((inner(r) as Members | null) ?? null) }, onError: e => { setPreview(null); setError(failure(e)) } })

  const change = (patch: Partial<NonNullable<typeof editing>>) => { setEditing(e => (e ? { ...e, ...patch } : e)); setPreview(null) }
  const setCondition = (i: number, c: Condition) => change({ conditions: editing!.conditions.map((x, j) => (j === i ? c : x)) })
  const submit = () => { const p = segmentProblem(editing!.name, editing!.conditions, defs); if (p) { setError(p); return } setError(null); save.mutate(editing!) }
  const verify = () => { const p = segmentProblem(editing!.name || "preview", editing!.conditions, defs); if (p) { setError(p); return } setError(null); check.mutate(editing!) }

  const valueInput = (c: Condition, i: number) => {
    const d = fieldDef(c.field, defs), label = `Value for condition ${i + 1}`
    if (!needsValue(c, defs)) return null
    if (d.input === "date") return <input aria-label={label} type="date" style={field} value={c.value} onChange={e => setCondition(i, { ...c, value: e.target.value })} />
    if (c.field === "OWNER") return <select aria-label={label} style={field} value={c.value} onChange={e => setCondition(i, { ...c, value: e.target.value })}><option value="">Choose…</option><option value={UNASSIGNED}>Nobody (unassigned)</option>{(users.data ?? []).map(u => <option key={u.id} value={u.id}>{owners[u.id]}</option>)}</select>
    if (d.input === "choice") return <select aria-label={label} style={field} value={c.value} onChange={e => setCondition(i, { ...c, value: e.target.value })}><option value="">Choose…</option>{d.choices?.map(o => <option key={o.id} value={o.id}>{o.label}</option>)}</select>
    return <input aria-label={label} style={{ ...field, width: d.input === "text" ? 160 : 110 }} inputMode={d.input === "text" ? "text" : "numeric"} value={c.value} onChange={e => setCondition(i, { ...c, value: e.target.value })} />
  }

  if (segments.isLoading) return <Loading />
  if (segments.isError) return <ErrorNote>Couldn't load the segments.</ErrorNote>
  const rows = segments.data ?? []
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <p style={{ ...muted, margin: 0 }}>A segment is a saved group of customers. Add conditions; a customer is in the segment when it meets all of them.</p>
        {canEdit && !editing && <button type="button" style={button} onClick={() => { setEditing({ id: null, name: "", conditions: [newCondition("TYPE", defs)] }); setPreview(null); setError(null) }}>New segment</button>}
      </div>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 8 }}>{error}</div>}
      {editing && (
        <form aria-label="Segment editor" onSubmit={e => { e.preventDefault(); submit() }} style={{ ...card, marginBottom: 12, display: "grid", gap: 8 }}>
          <input aria-label="Segment name" placeholder="Segment name, for example High value customers" style={field} value={editing.name} onChange={e => change({ name: e.target.value })} />
          {editing.conditions.map((c, i) => (
            <div key={i} style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
              <select aria-label={`Field for condition ${i + 1}`} style={field} value={c.field} onChange={e => setCondition(i, withField(c, e.target.value as FieldId, defs))}>{FIELDS.map(f => <option key={f.id} value={f.id}>{f.label}</option>)}{defs.length > FIELDS.length && <optgroup label="Custom fields">{defs.slice(FIELDS.length).map(f => <option key={f.id} value={f.id}>{f.label}</option>)}</optgroup>}{!defs.some(f => f.id === c.field) && <option value={c.field}>{fieldDef(c.field, defs).label}</option>}</select>
              <select aria-label={`Comparison for condition ${i + 1}`} style={field} value={c.op} onChange={e => setCondition(i, { ...c, op: e.target.value })}>{fieldDef(c.field, defs).ops.map(o => <option key={o.id} value={o.id}>{o.label}</option>)}</select>
              {valueInput(c, i)}
              <button type="button" style={button} aria-label={`Remove condition ${i + 1}`} disabled={editing.conditions.length === 1} onClick={() => change({ conditions: editing.conditions.filter((_, j) => j !== i) })}>✕</button>
            </div>))}
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            <button type="button" style={button} disabled={editing.conditions.length >= MAX_CONDITIONS} onClick={() => change({ conditions: [...editing.conditions, newCondition("TYPE", defs)] })}>Add condition</button>
            <button type="button" style={button} disabled={check.isPending} onClick={verify}>Check who matches</button>
            <button type="submit" style={{ ...button, background: "var(--hf-primary-text)", color: "var(--hf-surface)" }} disabled={save.isPending}>{editing.id ? "Save changes" : "Save segment"}</button>
            <button type="button" style={button} onClick={() => { setEditing(null); setPreview(null); setError(null) }}>Cancel</button>
          </div>
          {preview && <div role="status" style={{ fontSize: 13 }}><strong>{countText(preview.total)}</strong> match{preview.total === 1 ? "es" : ""} now{preview.members.length > 0 && `, for example ${preview.members.slice(0, 3).map(m => m.name).join(", ")}`}.</div>}
        </form>)}
      {rows.length === 0 && !editing ? <p style={muted}>No segments yet.{canEdit ? " Choose New segment to make one." : ""}</p> : (
        <div style={{ display: "grid", gap: 10 }}>
          {rows.map(s => (
            <section key={s.id} aria-label={s.name} style={card}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
                <strong>{s.name}</strong><span style={{ fontWeight: 700 }}>{countText(s.count)}</span>
              </div>
              <p style={{ ...muted, margin: "4px 0 8px" }}>{s.conditions.map(c => describe(c, owners, defs)).join(" · ")}</p>
              <div style={{ display: "flex", gap: 6 }}>
                <button type="button" style={button} aria-expanded={viewing === s.id} onClick={() => setViewing(viewing === s.id ? null : s.id)}>{viewing === s.id ? "Hide customers" : "Show customers"}</button>
                {canEdit && <button type="button" style={button} onClick={() => { setEditing({ id: s.id, name: s.name, conditions: s.conditions.map(c => ({ ...c })) }); setPreview(null); setError(null) }}>Edit</button>}
                {canEdit && <button type="button" style={button} disabled={remove.isPending} onClick={() => { if (window.confirm(`Delete the segment "${s.name}"? The customers in it are not affected.`)) remove.mutate(s.id) }}>Delete</button>}
              </div>
              {viewing === s.id && (members.isLoading ? <p style={muted}>Loading…</p> : members.isError || !members.data ? <div role="alert" style={{ fontSize: 13 }}>Couldn't load the customers.</div> : members.data.members.length === 0 ? <p style={muted}>Nobody matches right now.</p> : (
                <table aria-label={`Customers in ${s.name}`} style={{ width: "100%", borderCollapse: "collapse", fontSize: 13, marginTop: 8 }}>
                  <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Name</th><th>Email</th><th>Phone</th><th>Type</th><th>Status</th></tr></thead>
                  <tbody>{members.data.members.map(m => <tr key={m.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}><td style={{ padding: "5px 0" }}>{m.name}</td><td>{m.email ?? "—"}</td><td>{m.phone ?? "—"}</td><td>{m.customerType ?? "—"}</td><td>{m.status ?? "—"}</td></tr>)}</tbody>
                  {members.data.truncated && <caption style={{ captionSide: "bottom", ...muted, textAlign: "left", paddingTop: 6 }}>Showing the first {members.data.members.length} of {members.data.total}.</caption>}
                </table>))}
            </section>))}
        </div>)}
    </div>
  )
}
