// src/pages/catalogue/AttributesPanel.tsx — custom fields for items, per category (and the categories below it) or for every item.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Plus, SlidersHorizontal } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { DeleteModal, ErrorBanner, Field, Modal } from "./CatalogueKit"
import { cancelBtn, inp, inpErr, linkBtn, primaryBtn, sel, td, th } from "./catalogue.styles"
import {
  ATTRIBUTE_TYPES, EMPTY_ATTRIBUTE_FORM, attributeBody, attributeFormProblems, attributeTypeLabel, errorMessage, fieldErrors, indented, toAttributeForm,
  type AttributeDef, type AttributeForm, type Category,
} from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }

export default function AttributesPanel() {
  const qc = useQueryClient()
  const canManage = usePermissions(["CATALOGUE_ADMIN"])
  const [editing, setEditing] = useState<AttributeDef | "new" | null>(null)
  const [deleting, setDeleting] = useState<AttributeDef | null>(null)
  const [deleteError, setDeleteError] = useState("")
  const defs = useQuery<AttributeDef[]>({ queryKey: ["catalogue", "attributes"], queryFn: async () => inner<AttributeDef[]>(await apiClient.get("/api/v1/catalogue/attributes")) ?? [] })
  const cats = useQuery<Category[]>({ queryKey: ["catalogue", "categories"], queryFn: async () => inner<Category[]>(await apiClient.get("/api/v1/catalogue/categories")) ?? [] })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["catalogue", "attributes"] }); qc.invalidateQueries({ queryKey: ["catalogue", "categories"] }) }
  const del = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/catalogue/attributes/${id}`),
    onSuccess: () => { refresh(); setDeleting(null); setDeleteError("") },
    onError: e => setDeleteError(errorMessage(e, "Couldn't delete the attribute")),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 12 }}>
        <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-muted)" }}>Extra fields on an item, such as colour or pack size. An attribute on a category also applies to the categories below it.</p>
        {canManage && <button type="button" style={primaryBtn} onClick={() => setEditing("new")}><Plus size={15} /> Add attribute</button>}
      </div>
      <div aria-live="polite" aria-busy={defs.isLoading}>{defs.isLoading && <p>Loading…</p>}</div>
      {defs.isError && <ErrorBanner message={errorMessage(defs.error, "Couldn't load the attributes.")} />}
      {defs.data && defs.data.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No attributes yet.</p>}
      {defs.data && defs.data.length > 0 && (
        <div style={{ overflowX: "auto", border: "1px solid var(--hf-primary-border)", borderRadius: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Name</th><th style={th}>Type</th><th style={th}>Applies to</th><th style={th}>Required</th><th style={th}>Items</th><th style={th}>Status</th><th style={th} /></tr></thead>
            <tbody>
              {defs.data.map(d => (
                <tr key={d.id}>
                  <td style={td}><span style={{ fontWeight: 700 }}>{d.label}</span><div style={{ fontSize: 11.5, color: "var(--hf-text-faint)" }}>{d.code}</div></td>
                  <td style={td}>{attributeTypeLabel(d.type)}</td>
                  <td style={td}>{d.categoryPath ?? "All items"}</td>
                  <td style={td}>{d.required ? "Yes" : "No"}</td>
                  <td style={td}>{d.valueCount ?? 0}</td>
                  <td style={td}>{d.active ? "On" : "Off"}</td>
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                    {canManage && <>
                      <button type="button" style={linkBtn} aria-label={`Edit ${d.label}`} onClick={() => setEditing(d)}>Edit</button>{" "}
                      <button type="button" style={linkBtn} aria-label={`Delete ${d.label}`} onClick={() => { setDeleting(d); setDeleteError("") }}>Delete</button>
                    </>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && <AttributeModal def={editing === "new" ? null : editing} categories={cats.data ?? []} onClose={() => setEditing(null)} onSaved={() => { refresh(); setEditing(null) }} />}
      {deleting && (
        <DeleteModal title="Delete attribute?" badge={deleting.label} badgeIcon={<SlidersHorizontal size={14} />} isPending={del.isPending} error={deleteError}
          description={(deleting.valueCount ?? 0) > 0
            ? <>This attribute has a value on <strong>{deleting.valueCount}</strong> {deleting.valueCount === 1 ? "item" : "items"}. Switch it off instead of deleting it, so those values are kept.</>
            : <>No item has a value for this attribute. It will be removed.</>}
          onConfirm={() => del.mutate(deleting.id)} onCancel={() => { setDeleting(null); setDeleteError("") }} />
      )}
    </div>
  )
}

function AttributeModal({ def, categories, onClose, onSaved }: { def: AttributeDef | null; categories: Category[]; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<AttributeForm>(def ? toAttributeForm(def) : EMPTY_ATTRIBUTE_FORM)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState("")
  const set = <K extends keyof AttributeForm>(k: K, v: AttributeForm[K]) => setForm(f => ({ ...f, [k]: v }))
  const fixed = !!def  // code, type and category cannot change once created
  const save = useMutation({
    mutationFn: () => def ? apiClient.put(`/api/v1/catalogue/attributes/${def.id}`, attributeBody(form)) : apiClient.post("/api/v1/catalogue/attributes", attributeBody(form)),
    onSuccess: onSaved,
    onError: e => {
      const msg = errorMessage(e, "Couldn't save the attribute."), server = fieldErrors(e)
      if (/already exists/.test(msg)) { setErrors({ code: msg }); setGeneral("") } else { setErrors(server); setGeneral(Object.keys(server).length ? "" : msg) }
    },
  })
  const submit = () => {
    const errs = attributeFormProblems(form, !def)
    setErrors(errs); setGeneral("")
    if (Object.keys(errs).length === 0) save.mutate()
  }
  const s = (k: string) => (errors[k] ? inpErr : inp)
  return (
    <Modal title={def ? `Edit — ${def.label}` : "New attribute"} onClose={onClose}>
      <Field label="Name" required error={errors.label} id="at-label"><input id="at-label" style={s("label")} value={form.label} onChange={e => set("label", e.target.value)} placeholder="e.g. Pack size" autoFocus /></Field>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <Field label="Code" required error={errors.code} id="at-code">
          <input id="at-code" style={s("code")} value={form.code} readOnly={fixed} title={fixed ? "The code cannot change once created" : undefined} onChange={e => set("code", e.target.value)} placeholder="e.g. pack_size" />
        </Field>
        <Field label="Type" id="at-type">
          <select id="at-type" style={sel} value={form.type} disabled={fixed} onChange={e => set("type", e.target.value as AttributeForm["type"])}>{ATTRIBUTE_TYPES.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}</select>
        </Field>
      </div>
      <Field label="Applies to" id="at-cat">
        <select id="at-cat" style={sel} value={form.categoryId} disabled={fixed} onChange={e => set("categoryId", e.target.value)}>
          <option value="">All items</option>
          {categories.map(c => <option key={c.id} value={c.id}>{indented(c)}</option>)}
        </select>
      </Field>
      {form.type === "LIST" && (
        <Field label="Options (one per line)" required error={errors.options} id="at-options">
          <textarea id="at-options" style={{ ...s("options"), minHeight: 90, fontFamily: "inherit" }} value={form.options} onChange={e => set("options", e.target.value)} />
        </Field>
      )}
      {form.type === "NUMBER" && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <Field label="Minimum" error={errors.min} id="at-min"><input id="at-min" style={s("min")} value={form.min} onChange={e => set("min", e.target.value)} inputMode="decimal" /></Field>
          <Field label="Maximum" error={errors.max} id="at-max"><input id="at-max" style={s("max")} value={form.max} onChange={e => set("max", e.target.value)} inputMode="decimal" /></Field>
        </div>
      )}
      {form.type === "TEXT" && (
        <Field label="Maximum length" error={errors.maxLength} id="at-len"><input id="at-len" style={s("maxLength")} value={form.maxLength} onChange={e => set("maxLength", e.target.value)} inputMode="numeric" placeholder="500" /></Field>
      )}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, alignItems: "end" }}>
        <Field label="Order" error={errors.sortOrder} id="at-order"><input id="at-order" style={s("sortOrder")} value={form.sortOrder} onChange={e => set("sortOrder", e.target.value)} inputMode="numeric" /></Field>
        <div>
          <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, marginBottom: 8 }}><input type="checkbox" checked={form.required} onChange={e => set("required", e.target.checked)} /> Required (not for drafts)</label>
          <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, marginBottom: 16 }}><input type="checkbox" checked={form.active} onChange={e => set("active", e.target.checked)} /> On</label>
        </div>
      </div>
      {general && <ErrorBanner message={general} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
        <button type="button" style={cancelBtn} onClick={onClose}>Cancel</button>
        <button type="button" style={primaryBtn} disabled={save.isPending} onClick={submit}>{save.isPending ? "Saving…" : def ? "Save changes" : "Create attribute"}</button>
      </div>
    </Modal>
  )
}
