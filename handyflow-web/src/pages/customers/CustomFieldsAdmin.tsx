// src/pages/customers/CustomFieldsAdmin.tsx
// Define the extra fields this account keeps on its customers. Fields are switched off, never deleted, so stored values are not lost.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { FIELD_TYPES, definitionProblem, parseOptions, typeLabel, type CustomField, type CustomFieldType } from "./customFields.logic"

const inner = (r: { data: unknown }): unknown => { const o = r.data as { data?: unknown } | null; return o && !Array.isArray(o) && typeof o === "object" && "data" in o ? o.data : r.data }
const errorText = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const small: React.CSSProperties = { ...field, cursor: "pointer", padding: "3px 8px", fontSize: 12 }

export default function CustomFieldsAdmin() {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE")
  const [label, setLabel] = useState(""), [type, setType] = useState<CustomFieldType | "">(""), [options, setOptions] = useState(""), [required, setRequired] = useState(false), [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<string | null>(null), [editLabel, setEditLabel] = useState(""), [editOptions, setEditOptions] = useState("")
  const { data, isLoading, isError } = useQuery<CustomField[]>({ queryKey: ["crm", "custom-fields", "all"], queryFn: async () => { const d = inner(await apiClient.get("/api/v1/crm/custom-fields", { params: { includeInactive: true } })); return Array.isArray(d) ? (d as CustomField[]) : [] } })
  const refresh = () => { setError(null); qc.invalidateQueries({ queryKey: ["crm", "custom-fields"] }) }
  const create = useMutation({ mutationFn: () => apiClient.post("/api/v1/crm/custom-fields", { label: label.trim(), type, required, options: type === "CHOICE" ? parseOptions(options) : undefined }), onSuccess: () => { setLabel(""); setType(""); setOptions(""); setRequired(false); refresh() }, onError: e => setError(errorText(e)) })
  const change = useMutation({ mutationFn: (v: { id: string; body: object }) => apiClient.put(`/api/v1/crm/custom-fields/${v.id}`, v.body), onSuccess: () => { setEditing(null); refresh() }, onError: e => setError(errorText(e)) })
  if (isLoading) return <p>Loading…</p>
  if (isError || !data) return <div role="alert">Couldn't load the custom fields.</div>
  const add = () => { const p = definitionProblem(label, type, options); if (p) { setError(p); return } setError(null); create.mutate() }
  const saveEdit = (f: CustomField) => { const p = definitionProblem(editLabel, f.type, editOptions); if (p) { setError(p); return } setError(null); change.mutate({ id: f.id, body: { label: editLabel.trim(), options: f.type === "CHOICE" ? parseOptions(editOptions) : undefined } }) }
  return (
    <div>
      <p style={{ fontSize: 13, margin: "0 0 10px" }}>Extra details this account keeps on every customer, such as fleet size or contract end date. They appear on the customer form and overview. Switching a field off hides it but keeps what was entered.</p>
      {error && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</p>}
      {data.length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No custom fields yet.</p>
        : <table aria-label="Custom fields" style={{ width: "100%", borderCollapse: "collapse", fontSize: 13, marginBottom: 14 }}>
          <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Name</th><th>Kind</th><th>Required</th><th>Status</th><th /></tr></thead>
          <tbody>{data.map(f => (
            <tr key={f.id} aria-label={f.label} style={{ borderTop: "1px solid var(--hf-border-subtle)", opacity: f.active ? 1 : 0.6 }}>
              <td style={{ padding: "6px 0" }}>{editing === f.id
                ? <span role="group" aria-label={`Edit ${f.label}`}><input aria-label="Field name" style={field} value={editLabel} onChange={e => setEditLabel(e.target.value)} />{f.type === "CHOICE" && <> <input aria-label="Field options" style={{ ...field, width: 220 }} placeholder="Options, separated by commas" value={editOptions} onChange={e => setEditOptions(e.target.value)} /></>}</span>
                : <>{f.label}{f.type === "CHOICE" && <span style={{ color: "var(--hf-text-muted)" }}> ({f.options.join(", ")})</span>}</>}</td>
              <td>{typeLabel(f.type)}</td><td>{f.required ? "Yes" : "No"}</td><td>{f.active ? "In use" : "Switched off"}</td>
              <td style={{ textAlign: "right", whiteSpace: "nowrap" }}>{canEdit && (editing === f.id
                ? <><button type="button" style={small} disabled={change.isPending} onClick={() => saveEdit(f)}>Save</button> <button type="button" style={small} onClick={() => { setEditing(null); setError(null) }}>Cancel</button></>
                : <><button type="button" style={small} aria-label={`Rename ${f.label}`} onClick={() => { setEditing(f.id); setEditLabel(f.label); setEditOptions(f.options.join(", ")); setError(null) }}>Edit</button>{" "}
                  <button type="button" style={small} aria-label={`${f.required ? "Make optional" : "Make required"}: ${f.label}`} disabled={change.isPending} onClick={() => change.mutate({ id: f.id, body: { required: !f.required } })}>{f.required ? "Make optional" : "Make required"}</button>{" "}
                  <button type="button" style={small} aria-label={`${f.active ? "Switch off" : "Switch on"} ${f.label}`} disabled={change.isPending} onClick={() => change.mutate({ id: f.id, body: { active: !f.active } })}>{f.active ? "Switch off" : "Switch on"}</button></>)}</td>
            </tr>))}</tbody></table>}
      {canEdit && (
        <div role="group" aria-label="Add a custom field" style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
          <input aria-label="New field name" style={field} placeholder="Field name, e.g. Fleet size" value={label} onChange={e => setLabel(e.target.value)} />
          <select aria-label="New field kind" style={field} value={type} onChange={e => setType(e.target.value as CustomFieldType | "")}><option value="">Kind…</option>{FIELD_TYPES.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}</select>
          {type === "CHOICE" && <input aria-label="New field options" style={{ ...field, minWidth: 240 }} placeholder="Options, separated by commas" value={options} onChange={e => setOptions(e.target.value)} />}
          <label style={{ fontSize: 13 }}><input type="checkbox" checked={required} onChange={e => setRequired(e.target.checked)} /> Required</label>
          <button type="button" style={{ ...field, cursor: "pointer", fontWeight: 700 }} disabled={create.isPending} onClick={add}>Add field</button>
        </div>)}
    </div>
  )
}
