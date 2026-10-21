// src/pages/catalogue/UnitsPanel.tsx — the units of measure items can be sold in. Managed by catalogue administrators.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Plus, Ruler } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { DeleteModal, ErrorBanner, Field, Modal } from "./CatalogueKit"
import { cancelBtn, inp, inpErr, linkBtn, primaryBtn, sel, td, th } from "./catalogue.styles"
import { EMPTY_UNIT_FORM, UNIT_KINDS, errorMessage, fieldErrors, kindLabel, toUnitForm, unitBody, unitProblems, type Unit, type UnitForm } from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }

export default function UnitsPanel() {
  const qc = useQueryClient()
  const canManage = usePermissions(["CATALOGUE_ADMIN"])
  const [editing, setEditing] = useState<Unit | "new" | null>(null)
  const [deleting, setDeleting] = useState<Unit | null>(null)
  const [deleteError, setDeleteError] = useState("")
  const units = useQuery<Unit[]>({ queryKey: ["catalogue", "units"], queryFn: async () => inner<Unit[]>(await apiClient.get("/api/v1/catalogue/units")) ?? [] })
  const refresh = () => qc.invalidateQueries({ queryKey: ["catalogue", "units"] })
  const del = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/catalogue/units/${id}`),
    onSuccess: () => { refresh(); setDeleting(null); setDeleteError("") },
    onError: e => setDeleteError(errorMessage(e, "Couldn't delete the unit")),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 12 }}>
        <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-muted)" }}>Items are priced per unit. Decimals is how many decimal places a quantity in that unit may have.</p>
        {canManage && <button type="button" style={primaryBtn} onClick={() => setEditing("new")}><Plus size={15} /> Add unit</button>}
      </div>
      <div aria-live="polite" aria-busy={units.isLoading}>{units.isLoading && <p>Loading…</p>}</div>
      {units.isError && <ErrorBanner message={errorMessage(units.error, "Couldn't load the units.")} />}
      {units.data && units.data.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No units yet.</p>}
      {units.data && units.data.length > 0 && (
        <div style={{ overflowX: "auto", border: "1px solid var(--hf-primary-border)", borderRadius: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Code</th><th style={th}>Name</th><th style={th}>Measures</th><th style={th}>Decimals</th><th style={th}>Items</th><th style={th}>Status</th><th style={th} /></tr></thead>
            <tbody>
              {units.data.map(u => (
                <tr key={u.id}>
                  <td style={{ ...td, fontWeight: 700 }}>{u.code}</td>
                  <td style={td}>{u.name}</td>
                  <td style={td}>{kindLabel(u.kind)}</td>
                  <td style={td}>{u.decimals}</td>
                  <td style={td}>{u.itemCount ?? 0}</td>
                  <td style={td}>{u.active ? "On" : "Off"}</td>
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                    {canManage && <>
                      <button type="button" style={linkBtn} aria-label={`Edit ${u.code}`} onClick={() => setEditing(u)}>Edit</button>{" "}
                      <button type="button" style={linkBtn} aria-label={`Delete ${u.code}`} onClick={() => { setDeleting(u); setDeleteError("") }}>Delete</button>
                    </>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && <UnitModal unit={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={() => { refresh(); setEditing(null) }} />}
      {deleting && (
        <DeleteModal title="Delete unit?" badge={deleting.code} badgeIcon={<Ruler size={14} />} isPending={del.isPending} error={deleteError}
          description={(deleting.itemCount ?? 0) > 0
            ? <>This unit is used by <strong>{deleting.itemCount}</strong> {deleting.itemCount === 1 ? "item" : "items"}. Switch it off instead of deleting it, so those items keep their unit.</>
            : <>No item uses this unit. It will be removed.</>}
          onConfirm={() => del.mutate(deleting.id)} onCancel={() => { setDeleting(null); setDeleteError("") }} />
      )}
    </div>
  )
}

function UnitModal({ unit, onClose, onSaved }: { unit: Unit | null; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<UnitForm>(unit ? toUnitForm(unit) : EMPTY_UNIT_FORM)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState("")
  const set = <K extends keyof UnitForm>(k: K, v: UnitForm[K]) => setForm(f => ({ ...f, [k]: v }))
  const save = useMutation({
    mutationFn: () => unit ? apiClient.put(`/api/v1/catalogue/units/${unit.id}`, unitBody(form)) : apiClient.post("/api/v1/catalogue/units", unitBody(form)),
    onSuccess: onSaved,
    onError: e => {
      const msg = errorMessage(e, "Couldn't save the unit."), server = fieldErrors(e)
      if (/already exists/.test(msg)) { setErrors({ code: msg }); setGeneral("") } else { setErrors(server); setGeneral(Object.keys(server).length ? "" : msg) }
    },
  })
  const submit = () => {
    const errs = unitProblems(form, !unit)
    setErrors(errs); setGeneral("")
    if (Object.keys(errs).length === 0) save.mutate()
  }
  return (
    <Modal title={unit ? `Edit — ${unit.code}` : "New unit"} onClose={onClose}>
      <Field label="Code" required error={errors.code} id="un-code">
        <input id="un-code" style={errors.code ? inpErr : inp} value={form.code} readOnly={!!unit} title={unit ? "The code cannot change once created" : undefined}
          onChange={e => set("code", e.target.value)} placeholder="e.g. bag" autoFocus={!unit} />
      </Field>
      <Field label="Name" error={errors.name} id="un-name"><input id="un-name" style={errors.name ? inpErr : inp} value={form.name} onChange={e => set("name", e.target.value)} placeholder="e.g. Bag" /></Field>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <Field label="Measures" id="un-kind">
          <select id="un-kind" style={sel} value={form.kind} onChange={e => set("kind", e.target.value as UnitForm["kind"])}>{UNIT_KINDS.map(k => <option key={k.id} value={k.id}>{k.label}</option>)}</select>
        </Field>
        <Field label="Decimals" error={errors.decimals} id="un-dec">
          <input id="un-dec" style={errors.decimals ? inpErr : inp} value={form.decimals} onChange={e => set("decimals", e.target.value)} inputMode="numeric" />
        </Field>
      </div>
      <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, marginBottom: 16 }}>
        <input type="checkbox" checked={form.active} onChange={e => set("active", e.target.checked)} /> Available for new items
      </label>
      {general && <ErrorBanner message={general} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
        <button type="button" style={cancelBtn} onClick={onClose}>Cancel</button>
        <button type="button" style={primaryBtn} disabled={save.isPending} onClick={submit}>{save.isPending ? "Saving…" : unit ? "Save changes" : "Create unit"}</button>
      </div>
    </Modal>
  )
}
