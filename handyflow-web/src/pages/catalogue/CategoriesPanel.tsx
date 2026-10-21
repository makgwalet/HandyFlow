// src/pages/catalogue/CategoriesPanel.tsx — categories with the number of items in each; add, rename and delete (only when empty).
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Plus, Tag } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { CategoryMenu, DeleteModal, ErrorBanner, Field, Modal } from "./CatalogueKit"
import { inp, inpErr, sel } from "./catalogue.styles"
import { errorMessage, fieldErrors, indented, parentChoices, type Category } from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const primary: React.CSSProperties = { padding: "9px 18px", background: "var(--hf-primary)", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 600, color: "var(--hf-text-on-solid)", cursor: "pointer", display: "inline-flex", alignItems: "center", gap: 6 }
const cancelBtn: React.CSSProperties = { padding: "9px 16px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer", color: "var(--hf-text-secondary)" }

export default function CategoriesPanel() {
  const qc = useQueryClient()
  const canWrite = usePermissions(["SETTINGS_MANAGE", "CATALOGUE_WRITE"])
  const canArchive = usePermissions(["SETTINGS_MANAGE", "CATALOGUE_ARCHIVE"])
  const [editing, setEditing] = useState<Category | { parentId: string } | "new" | null>(null)
  const [deleting, setDeleting] = useState<Category | null>(null)
  const [deleteError, setDeleteError] = useState("")
  const cats = useQuery<Category[]>({ queryKey: ["catalogue", "categories"], queryFn: async () => inner<Category[]>(await apiClient.get("/api/v1/catalogue/categories")) ?? [] })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["catalogue", "categories"] }); qc.invalidateQueries({ queryKey: ["catalogue", "items"] }) }
  const del = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/catalogue/categories/${id}`),
    onSuccess: () => { refresh(); setDeleting(null); setDeleteError("") },
    onError: e => setDeleteError(errorMessage(e, "Failed to delete the category")),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-muted)" }}>Group items so they are easy to find. Categories can sit inside each other, up to 5 levels.</p>
        {canWrite && <button type="button" style={primary} onClick={() => setEditing("new")}><Plus size={15} /> Add category</button>}
      </div>
      {cats.isLoading && <p role="status">Loading…</p>}
      {cats.isError && <ErrorBanner message={errorMessage(cats.error, "Couldn't load the categories.")} />}
      {cats.data && cats.data.length === 0 && (
        <div style={{ border: "1px solid var(--hf-primary-border)", borderRadius: 14, padding: "48px 24px", textAlign: "center" }}>
          <Tag size={34} aria-hidden style={{ color: "var(--hf-text-disabled)", marginBottom: 10 }} />
          <p style={{ fontSize: 16, fontWeight: 700, color: "var(--hf-text-muted)", margin: "0 0 6px" }}>No categories yet</p>
          <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0 }}>{canWrite ? "Add a category to start grouping your items." : "No categories have been set up."}</p>
        </div>
      )}
      {cats.data && cats.data.length > 0 && (
        <div style={{ display: "grid", gap: 10 }}>
          {cats.data.map(c => (
            <div key={c.id} data-depth={c.depth} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, border: "1px solid var(--hf-primary-border)", borderRadius: 12, padding: "12px 16px", marginLeft: (c.depth - 1) * 24 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10, minWidth: 0 }}>
                <div style={{ width: 32, height: 32, borderRadius: 8, background: "var(--hf-violet-soft-strong)", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}><Tag size={15} aria-hidden style={{ color: "var(--hf-violet-text)" }} /></div>
                <div style={{ minWidth: 0 }}>
                  <p style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{c.name}</p>
                  {c.description && <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>{c.description}</p>}
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={{ fontSize: 12, color: "var(--hf-text-faint)", fontWeight: 500 }}>{c.itemCount ?? 0} {c.itemCount === 1 ? "item" : "items"}</span>
                {canWrite && c.depth < 5 && <button type="button" style={{ ...cancelBtn, padding: "4px 10px", fontSize: 12 }} aria-label={`Add a sub-category to ${c.name}`} onClick={() => setEditing({ parentId: c.id })}>+ Sub-category</button>}
                {(canWrite || canArchive) && <CategoryMenu cat={c} onEdit={() => setEditing(c)} onDelete={() => { setDeleting(c); setDeleteError("") }} />}
              </div>
            </div>
          ))}
        </div>
      )}
      {editing && <CategoryModal cat={editing === "new" || !("id" in editing) ? null : editing} startParent={editing !== "new" && !("id" in editing) ? editing.parentId : ""} all={cats.data ?? []} onClose={() => setEditing(null)} onSaved={() => { refresh(); setEditing(null) }} />}
      {deleting && (
        <DeleteModal title="Delete category?" badge={deleting.name} badgeIcon={<Tag size={14} />} isPending={del.isPending} error={deleteError}
          description={(deleting.itemCount ?? 0) > 0
            ? <>This category still has <strong>{deleting.itemCount}</strong> {deleting.itemCount === 1 ? "item" : "items"}. Move or delete them first, then try again.</>
            : <>This category will be removed. If it has sub-categories or attributes, it will be refused until those are moved or deleted.</>}
          onConfirm={() => del.mutate(deleting.id)} onCancel={() => { setDeleting(null); setDeleteError("") }} />
      )}
    </div>
  )
}

function CategoryModal({ cat, startParent, all, onClose, onSaved }: { cat: Category | null; startParent: string; all: Category[]; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(cat?.name ?? "")
  const [parentId, setParentId] = useState(cat ? cat.parentId ?? "" : startParent)
  const [description, setDescription] = useState(cat?.description ?? "")
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState("")
  const save = useMutation({
    mutationFn: () => { const body = { name: name.trim(), description: description.trim() || undefined, parentId: parentId || undefined }; return cat ? apiClient.put(`/api/v1/catalogue/categories/${cat.id}`, body) : apiClient.post("/api/v1/catalogue/categories", body) },
    onSuccess: onSaved,
    onError: e => {
      const msg = errorMessage(e, "Couldn't save the category."), server = fieldErrors(e)
      if (/already exists/.test(msg)) { setErrors({ name: msg }); setGeneral("") } else { setErrors(server); setGeneral(Object.keys(server).length ? "" : msg) }
    },
  })
  const submit = () => {
    const n = name.trim(), errs: Record<string, string> = {}
    if (!n) errs.name = "Category name is required"; else if (n.length > 100) errs.name = "The category name can be at most 100 characters"
    setErrors(errs); setGeneral("")
    if (Object.keys(errs).length === 0) save.mutate()
  }
  return (
    <Modal title={cat ? `Edit — ${cat.name}` : "New category"} onClose={onClose}>
      <Field label="Category name" required error={errors.name} id="cc-name"><input id="cc-name" style={errors.name ? inpErr : inp} value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Heavy equipment" autoFocus /></Field>
      <Field label="Inside (optional)" id="cc-parent">
        <select id="cc-parent" style={sel} value={parentId} onChange={e => setParentId(e.target.value)}>
          <option value="">— Top level —</option>
          {parentChoices(all, cat?.id ?? null).map(c => <option key={c.id} value={c.id}>{indented(c)}</option>)}
        </select>
      </Field>
      <Field label="Description (optional)" id="cc-desc"><input id="cc-desc" style={inp} value={description} onChange={e => setDescription(e.target.value)} placeholder="Brief description" /></Field>
      {general && <ErrorBanner message={general} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
        <button type="button" style={cancelBtn} onClick={onClose}>Cancel</button>
        <button type="button" style={primary} disabled={save.isPending} onClick={submit}>{save.isPending ? "Saving…" : cat ? "Save changes" : "Create category"}</button>
      </div>
    </Modal>
  )
}
