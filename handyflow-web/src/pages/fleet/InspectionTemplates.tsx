// src/pages/fleet/InspectionTemplates.tsx
//
// Where inspection checklists are set up: a list of templates and an editor for one. A template says what is checked (sections and items),
// which failures need a photo, how serious a failure is, whether the inspector signs, and how often a vehicle should be inspected.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { INSPECTION_TYPES, SEVERITIES, VEHICLE_TYPES, emptyItem, emptyTemplate, formFrom, moveItem, templateBody, templateProblem, type TemplateForm } from "./inspectionTemplates.logic"

interface Row { id: string; name: string; inspectionType: string; active: boolean; intervalDays: number | null; appliesToVehicleType: string | null; signatureRequired: boolean; items: unknown[] }

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const btn: React.CSSProperties = { padding: "7px 14px", borderRadius: 8, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)", fontWeight: 700, fontSize: 12.5, cursor: "pointer" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14, marginBottom: 12 }
const small: React.CSSProperties = { ...field, padding: "4px 8px", fontSize: 12, cursor: "pointer" }
const Labelled = ({ label, children }: { label: string; children: React.ReactNode }) => <div style={{ display: "flex", flexDirection: "column", gap: 3, fontSize: 11.5, color: "var(--hf-text-muted)" }}><span>{label}</span>{children}</div>
const typeLabel = (k: string) => INSPECTION_TYPES.find(t => t[0] === k)?.[1] ?? k

function Editor({ id, onDone }: { id: string | null; onDone: () => void }) {
  const [f, setF] = useState<TemplateForm | null>(id ? null : emptyTemplate())
  const [error, setError] = useState<string | null>(null)
  useQuery({ queryKey: ["fleet", "inspection-template", id], enabled: !!id, queryFn: async () => { const t = unwrap<Parameters<typeof formFrom>[0]>(await apiClient.get(`/api/v1/fleet/inspection-templates/${id}`)); setF(formFrom(t)); return t } })
  const save = useMutation({
    mutationFn: () => id ? apiClient.put(`/api/v1/fleet/inspection-templates/${id}`, templateBody(f!)) : apiClient.post("/api/v1/fleet/inspection-templates", templateBody(f!)),
    onSuccess: onDone, onError: e => setError(errorOf(e)),
  })
  if (!f) return <p>Loading…</p>
  const set = <K extends keyof TemplateForm>(k: K, v: TemplateForm[K]) => { setF({ ...f, [k]: v }); setError(null) }
  const setItem = (i: number, patch: Partial<TemplateForm["items"][number]>) => set("items", f.items.map((x, n) => n === i ? { ...x, ...patch } : x))
  const problem = templateProblem(f)
  return (
    <div style={card}>
      <h3 style={{ marginTop: 0 }}>{id ? "Edit inspection" : "New inspection"}</h3>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 10 }}>
        <Labelled label="Name *"><input aria-label="Template name" style={{ ...field, minWidth: 240 }} value={f.name} onChange={e => set("name", e.target.value)} placeholder="Daily vehicle inspection" /></Labelled>
        <Labelled label="Kind"><select aria-label="Inspection kind" style={field} value={f.inspectionType} onChange={e => set("inspectionType", e.target.value)}>{INSPECTION_TYPES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></Labelled>
        <Labelled label="Repeat every (days)"><input aria-label="Repeat every (days)" type="number" min="1" style={{ ...field, width: 130 }} value={f.intervalDays} onChange={e => set("intervalDays", e.target.value)} placeholder="Not scheduled" /></Labelled>
        <Labelled label="Only for vehicle type"><select aria-label="Vehicle type" style={field} value={f.appliesToVehicleType} onChange={e => set("appliesToVehicleType", e.target.value)}><option value="">All vehicles</option>{VEHICLE_TYPES.map(v => <option key={v} value={v}>{v.charAt(0) + v.slice(1).toLowerCase()}</option>)}</select></Labelled>
      </div>
      <Labelled label="Description"><textarea aria-label="Template description" rows={2} style={{ ...field, width: "100%" }} value={f.description} onChange={e => set("description", e.target.value)} /></Labelled>
      <div style={{ display: "flex", gap: 18, margin: "10px 0", fontSize: 13 }}>
        <label><input type="checkbox" checked={f.signatureRequired} onChange={e => set("signatureRequired", e.target.checked)} /> The inspector must sign</label>
        <label><input type="checkbox" checked={f.active} onChange={e => set("active", e.target.checked)} /> In use</label>
      </div>
      <p style={{ fontSize: 12, fontWeight: 800, textTransform: "uppercase", letterSpacing: 0.5, color: "var(--hf-text-faint)", margin: "12px 0 6px" }}>Checklist</p>
      {f.items.map((it, i) => (
        <div key={i} style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", padding: "6px 0", borderBottom: "1px solid var(--hf-border-subtle)" }}>
          <input aria-label={`Section ${i + 1}`} placeholder="Section (Tyres, Lights…)" style={{ ...field, width: 170 }} value={it.section} onChange={e => setItem(i, { section: e.target.value })} />
          <input aria-label={`Item ${i + 1}`} placeholder="What is checked" style={{ ...field, flex: 1, minWidth: 220 }} value={it.label} onChange={e => setItem(i, { label: e.target.value })} />
          <select aria-label={`Severity ${i + 1}`} style={field} value={it.severity} onChange={e => setItem(i, { severity: e.target.value })}>{SEVERITIES.map(([k, l]) => <option key={k} value={k}>{l} if it fails</option>)}</select>
          <label style={{ fontSize: 12 }}><input type="checkbox" aria-label={`Required ${i + 1}`} checked={it.required} onChange={e => setItem(i, { required: e.target.checked })} /> Required</label>
          <label style={{ fontSize: 12 }}><input type="checkbox" aria-label={`Photo on fail ${i + 1}`} checked={it.photoRequiredOnFail} onChange={e => setItem(i, { photoRequiredOnFail: e.target.checked })} /> Photo if it fails</label>
          <button type="button" style={small} aria-label={`Move item ${i + 1} up`} disabled={i === 0} onClick={() => set("items", moveItem(f.items, i, i - 1))}>↑</button>
          <button type="button" style={small} aria-label={`Move item ${i + 1} down`} disabled={i === f.items.length - 1} onClick={() => set("items", moveItem(f.items, i, i + 1))}>↓</button>
          <button type="button" style={small} aria-label={`Remove item ${i + 1}`} disabled={f.items.length === 1} onClick={() => set("items", f.items.filter((_, n) => n !== i))}>Remove</button>
        </div>))}
      <button type="button" style={{ ...small, marginTop: 8 }} onClick={() => set("items", [...f.items, emptyItem(f.items[f.items.length - 1]?.section ?? "")])}>+ Add item</button>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5, marginTop: 8 }}>{error}</div>}
      <div style={{ marginTop: 12, display: "flex", gap: 8, alignItems: "center" }}>
        <button type="button" style={{ ...btn, opacity: problem || save.isPending ? 0.5 : 1 }} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>{save.isPending ? "Saving…" : "Save inspection"}</button>
        <button type="button" style={{ ...field, cursor: "pointer" }} onClick={onDone}>Cancel</button>
        {problem && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{problem}</span>}
      </div>
    </div>
  )
}

export default function InspectionTemplates({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermission("FLEET_MANAGE")
  const [editing, setEditing] = useState<string | "new" | null>(null)
  const [error, setError] = useState<string | null>(null)
  const { data: rows = [], isLoading } = useQuery<Row[]>({ queryKey: ["fleet", "inspection-templates"], queryFn: async () => { const p = unwrap<Row[] | { content?: Row[] }>(await apiClient.get("/api/v1/fleet/inspection-templates")); return (Array.isArray(p) ? p : p?.content) ?? [] } })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fleet", "inspection-templates"] }); qc.invalidateQueries({ queryKey: ["fleet", "inspection-template"] }) }
  const starter = useMutation({ mutationFn: () => apiClient.post("/api/v1/fleet/inspection-templates/starter"), onSuccess: refresh, onError: e => setError(errorOf(e)) })
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <h3 style={{ margin: 0 }}>Inspection templates</h3>
        <div style={{ display: "flex", gap: 8 }}>
          {canManage && editing === null && <button type="button" style={btn} onClick={() => setEditing("new")}>New inspection</button>}
          {canManage && editing === null && <button type="button" style={{ ...field, cursor: "pointer" }} disabled={starter.isPending} onClick={() => starter.mutate()}>Add the daily vehicle inspection</button>}
          <button type="button" style={{ ...field, cursor: "pointer" }} onClick={onClose}>Close</button>
        </div>
      </div>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5, marginBottom: 8 }}>{error}</div>}
      {editing !== null && <Editor id={editing === "new" ? null : editing} onDone={() => { setEditing(null); refresh() }} />}
      {isLoading && <p>Loading…</p>}
      {!isLoading && rows.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No inspection templates yet.{canManage && " Create one, or add the ready-made daily vehicle inspection."}</p>}
      {rows.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
          <thead><tr>{["Inspection", "Kind", "Items", "Repeats", "Applies to", "Status", ""].map(h => <th key={h} style={{ textAlign: "left", padding: "8px 10px", fontSize: 11, textTransform: "uppercase", color: "var(--hf-text-faint)" }}>{h}</th>)}</tr></thead>
          <tbody>{rows.map(t => (
            <tr key={t.id} aria-label={t.name} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
              <td style={{ padding: "9px 10px" }}><strong>{t.name}</strong>{t.signatureRequired && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Signed by the inspector</div>}</td>
              <td style={{ padding: "9px 10px" }}>{typeLabel(t.inspectionType)}</td><td style={{ padding: "9px 10px" }}>{t.items?.length ?? 0}</td>
              <td style={{ padding: "9px 10px" }}>{t.intervalDays ? `Every ${t.intervalDays} days` : "—"}</td>
              <td style={{ padding: "9px 10px" }}>{t.appliesToVehicleType ? t.appliesToVehicleType.charAt(0) + t.appliesToVehicleType.slice(1).toLowerCase() : "All vehicles"}</td>
              <td style={{ padding: "9px 10px" }}>{t.active ? "In use" : "Not in use"}</td>
              <td style={{ padding: "9px 10px" }}>{canManage && <button type="button" style={small} onClick={() => setEditing(t.id)}>Edit</button>}</td>
            </tr>))}</tbody>
        </table>)}
    </div>
  )
}
