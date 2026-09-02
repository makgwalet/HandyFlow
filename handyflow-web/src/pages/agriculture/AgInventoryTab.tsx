// src/pages/agriculture/AgInventoryTab.tsx
//
// Farm-scoped feed/seed/fertiliser/chemical/veterinary stock — confirmed
// via AgInventoryItemController: list/create (farm-scoped), get/update
// (item-scoped), receive/issue/adjust (each also appends a matching
// AgStockMovement row server-side, confirmed via AgInventoryItemService).
// Receive/issue/adjust field names (quantity, newUnitCost, referenceType,
// referenceId, newQuantity, performedBy, notes) are all confirmed directly
// from AgInventoryItemService's own call sites.
//
// ⚠ UNVERIFIED: deactivate/reactivate/delete endpoint PATHS weren't
// directly confirmed (only that AgInventoryItemService has matching
// deactivateItem/reactivateItem/deleteItem methods) — inferred to follow
// this module's own /deactivate, /reactivate, DELETE convention used by
// every other Increment-1 controller.
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Package, Plus, Pencil, ArrowDownToLine, ArrowUpFromLine, Scale, Ban, RotateCcw, Trash2, TriangleAlert } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, fmtMoney, statusBadge } from "./constants"

export interface InventoryItemResponse {
  id: string
  farmId: string
  itemName: string
  category: string
  unitOfMeasure: string
  currentQuantity: number
  reorderLevel: number | null
  unitCost: number | null
  supplier: string | null
  status: string
  belowReorderLevel: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}
interface Page<T> { content: T[]; totalElements: number }

const CATEGORIES = ["FEED", "SEED", "FERTILISER", "CHEMICAL", "VETERINARY", "OTHER"]
type ActionKind = "receive" | "issue" | "adjust" | null

export default function AgInventoryTab({ farmId }: { farmId: string }) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<InventoryItemResponse | null>(null)
  const [acting, setActing] = useState<{ item: InventoryItemResponse; kind: ActionKind } | null>(null)
  const [qty, setQty] = useState("")
  const [notes, setNotes] = useState("")

  const [itemName, setItemName] = useState(""); const [category, setCategory] = useState("FEED")
  const [uom, setUom] = useState("kg"); const [reorderLevel, setReorderLevel] = useState("")
  const [unitCost, setUnitCost] = useState(""); const [supplier, setSupplier] = useState("")

  const { data, isLoading } = useQuery<Page<InventoryItemResponse>>({
    queryKey: ["ag-inventory", farmId],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/inventory-items`, { params: { size: 200 } })).data,
  })
  const invalidate = () => qc.invalidateQueries({ queryKey: ["ag-inventory", farmId] })

  const createMut = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/agriculture/farms/${farmId}/inventory-items`, {
      farmId, itemName, category, unitOfMeasure: uom,
      reorderLevel: reorderLevel ? Number(reorderLevel) : null,
      unitCost: unitCost ? Number(unitCost) : null, supplier: supplier || null,
    }),
    onSuccess: () => {
      invalidate(); setShowCreate(false)
      setItemName(""); setCategory("FEED"); setUom("kg"); setReorderLevel(""); setUnitCost(""); setSupplier("")
    },
  })
  const updateMut = useMutation({
    mutationFn: (v: { itemName: string; reorderLevel: string; unitCost: string; supplier: string; notes: string }) =>
      apiClient.put(`/api/v1/agriculture/inventory-items/${editing!.id}`, {
        itemName: v.itemName, reorderLevel: v.reorderLevel ? Number(v.reorderLevel) : null,
        unitCost: v.unitCost ? Number(v.unitCost) : null, supplier: v.supplier || null, notes: v.notes || null,
      }),
    onSuccess: () => { invalidate(); setEditing(null) },
  })
  const deactivateMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/inventory-items/${id}/deactivate`), onSuccess: invalidate })
  const reactivateMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/inventory-items/${id}/reactivate`), onSuccess: invalidate })
  const deleteMut = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/agriculture/inventory-items/${id}`), onSuccess: invalidate })

  const actionMut = useMutation({
    mutationFn: async () => {
      if (!acting) return
      const { item, kind } = acting
      if (kind === "receive") return apiClient.post(`/api/v1/agriculture/inventory-items/${item.id}/receive`, { quantity: Number(qty), newUnitCost: null, notes: notes || null })
      if (kind === "issue") return apiClient.post(`/api/v1/agriculture/inventory-items/${item.id}/issue`, { quantity: Number(qty), referenceType: null, referenceId: null, notes: notes || null })
      if (kind === "adjust") return apiClient.post(`/api/v1/agriculture/inventory-items/${item.id}/adjust`, { newQuantity: Number(qty), notes: notes || null })
    },
    onSuccess: () => { invalidate(); setActing(null); setQty(""); setNotes("") },
  })

  const items = data?.content ?? []
  const lowStock = items.filter(i => i.belowReorderLevel)

  return (
    <div>
      {lowStock.length > 0 && (
        <div style={{ display: "flex", alignItems: "center", gap: 10, background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 12, padding: "12px 16px", marginBottom: 14 }}>
          <TriangleAlert size={16} color="#D97706" />
          <p style={{ fontSize: 12.5, color: "#92400E", margin: 0 }}>{lowStock.length} item{lowStock.length === 1 ? "" : "s"} below reorder level: {lowStock.map(i => i.itemName).join(", ")}</p>
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
        <p style={{ fontSize: 13, color: "#64748B", margin: 0 }}>{items.length} stock item{items.length === 1 ? "" : "s"}.</p>
        {!showCreate && <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} style={{ marginRight: 5, verticalAlign: -2 }} />Add item</button>}
      </div>

      {showCreate && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr", gap: 10, marginBottom: 10 }}>
            <div><label style={lbl}>Item name</label><input value={itemName} onChange={e => setItemName(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Category</label><select value={category} onChange={e => setCategory(e.target.value)} style={inp}>{CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}</select></div>
            <div><label style={lbl}>Unit of measure</label><input value={uom} onChange={e => setUom(e.target.value)} style={inp} /></div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginBottom: 12 }}>
            <div><label style={lbl}>Reorder level</label><input type="number" min={0} value={reorderLevel} onChange={e => setReorderLevel(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Unit cost (R)</label><input type="number" min={0} step="0.01" value={unitCost} onChange={e => setUnitCost(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Supplier</label><input value={supplier} onChange={e => setSupplier(e.target.value)} style={inp} /></div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending || !itemName.trim()} onClick={() => createMut.mutate()}
              style={{ ...btnPrimary, opacity: createMut.isPending || !itemName.trim() ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
        items.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No stock items yet.</p> : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {items.map((it, i) => (
            <div key={it.id}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <Package size={15} color={it.belowReorderLevel ? "#D97706" : AG_ACCENT} />
                  <div>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{it.itemName}</p>
                    <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>
                      {it.category} · {it.currentQuantity} {it.unitOfMeasure}{it.reorderLevel != null ? ` (reorder at ${it.reorderLevel})` : ""}
                      {it.unitCost != null ? ` · ${fmtMoney(it.unitCost)}/${it.unitOfMeasure}` : ""}{it.supplier ? ` · ${it.supplier}` : ""}
                    </p>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                  <span style={statusBadge(it.status)}>{it.status}</span>
                  <button onClick={() => setActing({ item: it, kind: "receive" })} title="Receive stock" style={iconBtn}><ArrowDownToLine size={13} /></button>
                  <button onClick={() => setActing({ item: it, kind: "issue" })} title="Issue stock" style={iconBtn}><ArrowUpFromLine size={13} /></button>
                  <button onClick={() => setActing({ item: it, kind: "adjust" })} title="Adjust quantity" style={iconBtn}><Scale size={13} /></button>
                  <button onClick={() => setEditing(it)} title="Edit" style={iconBtn}><Pencil size={13} /></button>
                  {it.status === "ACTIVE" ? (
                    <button onClick={() => deactivateMut.mutate(it.id)} title="Deactivate" style={iconBtn}><Ban size={13} /></button>
                  ) : (
                    <button onClick={() => reactivateMut.mutate(it.id)} title="Reactivate" style={iconBtn}><RotateCcw size={13} /></button>
                  )}
                  <button onClick={() => { if (confirm(`Delete "${it.itemName}"?`)) deleteMut.mutate(it.id) }} title="Delete" style={{ ...iconBtn, color: "#DC2626" }}><Trash2 size={13} /></button>
                </div>
              </div>

              {editing?.id === it.id && (
                <div style={{ padding: "0 16px 16px" }}>
                  <EditItemForm item={editing} saving={updateMut.isPending} onCancel={() => setEditing(null)} onSave={v => updateMut.mutate(v)} />
                </div>
              )}

              {acting?.item.id === it.id && (
                <div style={{ padding: "0 16px 16px", display: "flex", gap: 8, alignItems: "flex-end" }}>
                  <div>
                    <label style={lbl}>{acting.kind === "adjust" ? "New quantity" : "Quantity"}</label>
                    <input type="number" min={0} step="0.01" value={qty} onChange={e => setQty(e.target.value)} style={{ ...inp, width: 130 }} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <label style={lbl}>Notes</label>
                    <input value={notes} onChange={e => setNotes(e.target.value)} style={inp} />
                  </div>
                  <button disabled={actionMut.isPending || !qty} onClick={() => actionMut.mutate()}
                    style={{ ...btnPrimary, opacity: actionMut.isPending || !qty ? 0.6 : 1, textTransform: "capitalize" }}>
                    {actionMut.isPending ? "Saving…" : acting.kind}
                  </button>
                  <button onClick={() => { setActing(null); setQty(""); setNotes("") }} style={btnGhost}>Cancel</button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function EditItemForm({ item, saving, onCancel, onSave }: {
  item: InventoryItemResponse; saving: boolean; onCancel: () => void
  onSave: (v: { itemName: string; reorderLevel: string; unitCost: string; supplier: string; notes: string }) => void
}) {
  const [itemName, setItemName] = useState(item.itemName)
  const [reorderLevel, setReorderLevel] = useState(item.reorderLevel != null ? String(item.reorderLevel) : "")
  const [unitCost, setUnitCost] = useState(item.unitCost != null ? String(item.unitCost) : "")
  const [supplier, setSupplier] = useState(item.supplier ?? "")
  const [notes, setNotes] = useState(item.notes ?? "")
  return (
    <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16 }}>
      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr 1fr", gap: 10, marginBottom: 12 }}>
        <div><label style={lbl}>Item name</label><input value={itemName} onChange={e => setItemName(e.target.value)} style={inp} /></div>
        <div><label style={lbl}>Reorder level</label><input type="number" min={0} value={reorderLevel} onChange={e => setReorderLevel(e.target.value)} style={inp} /></div>
        <div><label style={lbl}>Unit cost (R)</label><input type="number" min={0} step="0.01" value={unitCost} onChange={e => setUnitCost(e.target.value)} style={inp} /></div>
        <div><label style={lbl}>Supplier</label><input value={supplier} onChange={e => setSupplier(e.target.value)} style={inp} /></div>
      </div>
      <div style={{ marginBottom: 12 }}><label style={lbl}>Notes</label><input value={notes} onChange={e => setNotes(e.target.value)} style={inp} /></div>
      <div style={{ display: "flex", gap: 8 }}>
        <button disabled={saving || !itemName.trim()} onClick={() => onSave({ itemName, reorderLevel, unitCost, supplier, notes })}
          style={{ ...btnPrimary, opacity: saving || !itemName.trim() ? 0.6 : 1 }}>{saving ? "Saving…" : "Save"}</button>
        <button onClick={onCancel} style={btnGhost}>Cancel</button>
      </div>
    </div>
  )
}

const lbl: React.CSSProperties = { fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4, display: "block" }
const inp: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12.5, boxSizing: "border-box" }
const btnPrimary: React.CSSProperties = { display: "inline-flex", alignItems: "center", padding: "8px 14px", borderRadius: 8, border: "none", background: AG_ACCENT, color: "#fff", fontSize: 12.5, fontWeight: 700, cursor: "pointer" }
const btnGhost: React.CSSProperties = { padding: "8px 14px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }
const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 28, height: 28, borderRadius: 7, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", cursor: "pointer" }
