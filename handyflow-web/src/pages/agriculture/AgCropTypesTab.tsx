// src/pages/agriculture/AgCropTypesTab.tsx
// Tenant-wide crop catalogue (maize, soybeans, ...). Category cannot be changed after creation (the update request has no
// category). Category and unit are free text on the server, so the form offers a consistent vocabulary.
import { useState } from "react"
import { Plus } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import { agKeys, api, useAgMutation, useCropTypes } from "./agCrops.api"
import type { CropType } from "./agCrops.types"
import { Empty, Field, Th } from "./agCropsUi"
import { btnDanger, btnGhost, btnPrimary, grid, inp, panel, statusBadge, td, theadStyle } from "./constants"

const CATEGORIES = ["CEREAL", "OILSEED", "LEGUME", "VEGETABLE", "FRUIT", "FODDER", "OTHER"]
const UNITS = ["kg", "t", "bags", "bales", "crates"]

function TypeForm({ initial, onDone }: { initial?: CropType; onDone: () => void }) {
  const [f, setF] = useState({ name: initial?.name ?? "", category: initial?.category ?? "CEREAL", days: initial?.typicalGrowingDays != null ? String(initial.typicalGrowingDays) : "", unit: initial?.defaultUnitOfMeasure ?? "kg" })
  const keys = [["ag", "crop-types"] as const]
  const days = f.days ? Number(f.days) : undefined
  const valid = !!f.name.trim() && (days === undefined || (Number.isInteger(days) && days > 0))
  const create = useAgMutation(() => api.post("/crop-types", { name: f.name.trim(), category: f.category, typicalGrowingDays: days, defaultUnitOfMeasure: f.unit.trim() || "kg" }), { invalidate: keys.map(k => [...k]), success: "Crop type added", failure: "Couldn't add the crop type." })
  const update = useAgMutation(() => api.put(`/crop-types/${initial?.id}`, { name: f.name.trim(), typicalGrowingDays: days, defaultUnitOfMeasure: f.unit.trim() || undefined }), { invalidate: keys.map(k => [...k]), success: "Crop type saved", failure: "Couldn't save the crop type." })
  const run = initial ? update : create
  return (
    <div style={panel}>
      <div style={grid}>
        <Field label="Name *" htmlFor="ct-name"><input id="ct-name" style={inp} value={f.name} onChange={e => setF({ ...f, name: e.target.value })} placeholder="e.g. Maize" /></Field>
        {!initial && <Field label="Category *" htmlFor="ct-cat"><select id="ct-cat" style={inp} value={f.category} onChange={e => setF({ ...f, category: e.target.value })}>{CATEGORIES.map(c => <option key={c}>{c}</option>)}</select></Field>}
        <Field label="Typical growing days" htmlFor="ct-days"><input id="ct-days" type="number" min="1" step="1" style={inp} value={f.days} onChange={e => setF({ ...f, days: e.target.value })} placeholder="Used to suggest a harvest date" /></Field>
        <Field label="Yield unit" htmlFor="ct-unit"><input id="ct-unit" list="ct-units" style={inp} value={f.unit} onChange={e => setF({ ...f, unit: e.target.value })} /><datalist id="ct-units">{UNITS.map(u => <option key={u} value={u} />)}</datalist></Field>
      </div>
      <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "8px 0 0" }}>Harvests are summed in this unit, so pick the one you weigh in.</p>
      <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
        <button type="button" style={{ ...btnPrimary, opacity: valid && !run.isPending ? 1 : 0.5 }} disabled={!valid || run.isPending} onClick={() => run.mutate(undefined, { onSuccess: onDone })}>{initial ? "Save" : "Add crop type"}</button>
        <button type="button" style={btnGhost} onClick={onDone}>Cancel</button>
      </div>
    </div>
  )
}

export default function AgCropTypesTab() {
  const canManage = usePermission("AGRICULTURE_MANAGE")
  const canDelete = usePermission("AGRICULTURE_ADMIN")
  const [category, setCategory] = useState("")
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<CropType | null>(null)
  const [deleting, setDeleting] = useState<CropType | null>(null)
  const { data: types = [], isLoading } = useCropTypes(category || undefined)
  const keys = [agKeys.cropTypes(category || undefined), ["ag", "crop-types"]]
  const deactivate = useAgMutation((id: string) => api.patch(`/crop-types/${id}/deactivate`), { invalidate: keys, success: "Deactivated", failure: "Couldn't deactivate it." })
  const reactivate = useAgMutation((id: string) => api.patch(`/crop-types/${id}/reactivate`), { invalidate: keys, success: "Reactivated", failure: "Couldn't reactivate it." })
  const remove = useAgMutation((id: string) => api.del(`/crop-types/${id}`), { invalidate: keys, success: "Crop type deleted", failure: "Couldn't delete it." })
  const small: React.CSSProperties = { ...btnGhost, padding: "4px 10px", fontSize: 11.5 }
  return (
    <div>
      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 14, flexWrap: "wrap" }}>
        <select aria-label="Filter by category" value={category} onChange={e => setCategory(e.target.value)} style={{ ...inp, width: "auto" }}><option value="">All categories</option>{CATEGORIES.map(c => <option key={c}>{c}</option>)}</select>
        <div style={{ flex: 1 }} />
        {canManage && !adding && !editing && <button type="button" style={btnPrimary} onClick={() => setAdding(true)}><Plus size={14} />Add crop type</button>}
      </div>
      {adding && <TypeForm onDone={() => setAdding(false)} />}
      {editing && <TypeForm initial={editing} onDone={() => setEditing(null)} />}
      {deleting && (
        <div style={panel}>
          <p style={{ fontSize: 13, margin: "0 0 10px" }}>Delete "{deleting.name}"? Crop cycles that use it will show "Unknown crop". Deactivating it instead keeps them intact and just hides it from new cycles.</p>
          <div style={{ display: "flex", gap: 8 }}>
            <button type="button" style={btnDanger} onClick={() => { remove.mutate(deleting.id); setDeleting(null) }}>Delete crop type</button>
            <button type="button" style={btnGhost} onClick={() => setDeleting(null)}>Cancel</button>
          </div>
        </div>
      )}
      {isLoading ? <Empty>Loading crop types…</Empty> : types.length === 0 ? <Empty>No crop types yet. Add the crops you grow (maize, soybeans…) before creating crop cycles.</Empty> : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
            <thead><tr style={theadStyle}>{["Crop", "Category", "Growing days", "Yield unit", "Status", ""].map((h, i) => <Th key={i}>{h}</Th>)}</tr></thead>
            <tbody>
              {types.map(t => (
                <tr key={t.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <td style={{ ...td, fontWeight: 700 }}>{t.name}</td><td style={td}>{t.category}</td><td style={td}>{t.typicalGrowingDays ?? "—"}</td><td style={td}>{t.defaultUnitOfMeasure}</td>
                  <td style={td}><span style={statusBadge(t.status)}>{t.status === "ACTIVE" ? "Active" : "Inactive"}</span></td>
                  <td style={td}>{canManage && (
                    <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                      <button type="button" style={small} onClick={() => { setAdding(false); setEditing(t) }}>Edit</button>
                      {t.status === "ACTIVE" ? <button type="button" style={small} onClick={() => deactivate.mutate(t.id)}>Deactivate</button> : <button type="button" style={small} onClick={() => reactivate.mutate(t.id)}>Reactivate</button>}
                      {canDelete && <button type="button" style={{ ...small, ...btnDanger, padding: "4px 10px", fontSize: 11.5 }} onClick={() => setDeleting(t)}>Delete</button>}
                    </div>)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
