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
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAgToastMutation } from "./agMutation"
import { Package, Plus, Pencil, ArrowDownToLine, ArrowUpFromLine, Scale, Ban, RotateCcw, Trash2, TriangleAlert } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT_TEXT, fmtMoney, statusBadge, inp, btnPrimary, btnGhost } from "./constants"
import { agConfirm } from "./agConfirm"
import { Field, QueryError } from "./agUi"
import AgManage from "./AgManage"
import AgAuditLine from "./AgAuditLine"
import AgStockImport from "./AgStockImport"
import { STOCK_UNITS } from "./agLookups"
import { isCountUnit, unitSingular } from "./agPickers.logic"
import { packSizeError, packSizeToSend } from "./agStock.logic"
import { adjustError, adjustmentText, averageCostAfterReceipt, receiveError } from "./agStock.logic"

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
  packSizeKg?: number | null    // kg in one bag/bale/sack; only for items counted in those units
  costPerKg?: number | null
}
interface Page<T> { content: T[]; totalElements: number }

const CATEGORIES = ["FEED", "SEED", "FERTILISER", "CHEMICAL", "VETERINARY", "OTHER"]
type ActionKind = "receive" | "issue" | "adjust" | null

export default function AgInventoryTab({ farmId }: { farmId: string }) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [showImport, setShowImport] = useState(false)
  const [editing, setEditing] = useState<InventoryItemResponse | null>(null)
  const [acting, setActing] = useState<{ item: InventoryItemResponse; kind: ActionKind } | null>(null)
  const [qty, setQty] = useState("")
  const [notes, setNotes] = useState("")
  const [deliveryCost, setDeliveryCost] = useState("")
  const [actionError, setActionError] = useState("")

  const [itemName, setItemName] = useState(""); const [category, setCategory] = useState("FEED")
  const [uom, setUom] = useState("kg"); const [reorderLevel, setReorderLevel] = useState("")
  const [unitCost, setUnitCost] = useState(""); const [supplier, setSupplier] = useState("")
  const [packSize, setPackSize] = useState("")

  const { data, isLoading, isError, refetch } = useQuery<Page<InventoryItemResponse>>({
    queryKey: ["ag-inventory", farmId],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/inventory-items`, { params: { size: 200 } })).data,
  })
  const invalidate = () => qc.invalidateQueries({ queryKey: ["ag-inventory", farmId] })

  const createMut = useAgToastMutation({
    mutationFn: () => apiClient.post(`/api/v1/agriculture/farms/${farmId}/inventory-items`, {
      farmId, itemName, category, unitOfMeasure: uom,
      reorderLevel: reorderLevel ? Number(reorderLevel) : null,
      unitCost: unitCost ? Number(unitCost) : null, supplier: supplier || null,
      packSizeKg: packSizeToSend(uom, packSize),
    }),
    onSuccess: () => {
      invalidate(); setShowCreate(false)
      setItemName(""); setCategory("FEED"); setUom("kg"); setReorderLevel(""); setUnitCost(""); setSupplier(""); setPackSize("")
    },
  })
  const updateMut = useAgToastMutation({
    mutationFn: (v: { itemName: string; reorderLevel: string; unitCost: string; supplier: string; notes: string; packSize: string }) =>
      apiClient.put(`/api/v1/agriculture/inventory-items/${editing!.id}`, {
        itemName: v.itemName, reorderLevel: v.reorderLevel ? Number(v.reorderLevel) : null,
        unitCost: v.unitCost ? Number(v.unitCost) : null, supplier: v.supplier || null, notes: v.notes || null,
        packSizeKg: packSizeToSend(editing!.unitOfMeasure, v.packSize),
      }),
    onSuccess: () => { invalidate(); setEditing(null) },
  })
  const deactivateMut = useAgToastMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/inventory-items/${id}/deactivate`), onSuccess: invalidate })
  const reactivateMut = useAgToastMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/inventory-items/${id}/reactivate`), onSuccess: invalidate })
  const deleteMut = useAgToastMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/agriculture/inventory-items/${id}`), onSuccess: invalidate })

  const actionMut = useAgToastMutation({
    mutationFn: async () => {
      if (!acting) return
      const { item, kind } = acting
      if (kind === "receive") return apiClient.post(`/api/v1/agriculture/inventory-items/${item.id}/receive`, { quantity: Number(qty), newUnitCost: deliveryCost ? Number(deliveryCost) : null, notes: notes || null })
      if (kind === "issue") return apiClient.post(`/api/v1/agriculture/inventory-items/${item.id}/issue`, { quantity: Number(qty), referenceType: null, referenceId: null, notes: notes || null })
      if (kind === "adjust") return apiClient.post(`/api/v1/agriculture/inventory-items/${item.id}/adjust`, { newQuantity: Number(qty), notes: notes || null })
    },
    onSuccess: () => { invalidate(); setActing(null); setQty(""); setNotes(""); setDeliveryCost(""); setActionError("") },
  })

  const items = data?.content ?? []
  const submitAction = () => {
    if (!acting) return
    const problem = acting.kind === "adjust" ? adjustError(qty, notes, acting.item.currentQuantity)
      : acting.kind === "receive" ? receiveError(qty, deliveryCost)
      : Number(qty) > 0 ? null : "Enter a quantity greater than 0."
    if (problem) { setActionError(problem); return }
    setActionError(""); actionMut.mutate()
  }
  const closeAction = () => { setActing(null); setQty(""); setNotes(""); setDeliveryCost(""); setActionError("") }
  const lowStock = items.filter(i => i.belowReorderLevel)

  return (
    <div>
      {lowStock.length > 0 && (
        <div style={{ display: "flex", alignItems: "center", gap: 10, background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 12, padding: "12px 16px", marginBottom: 14 }}>
          <TriangleAlert size={16} style={{ color: 'var(--hf-warning-text)' }} />
          <p style={{ fontSize: 12.5, color: "var(--hf-warning-text-deep)", margin: 0 }}>{lowStock.length} item{lowStock.length === 1 ? "" : "s"} below reorder level: {lowStock.map(i => i.itemName).join(", ")}</p>
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>{items.length} stock item{items.length === 1 ? "" : "s"}.</p>
        {!showCreate && !showImport && <AgManage><div style={{ display: "flex", gap: 8 }}>
          <button onClick={() => setShowImport(true)} style={btnGhost}>Import CSV</button>
          <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} style={{ marginRight: 5, verticalAlign: -2 }} />Add item</button>
        </div></AgManage>}
      </div>

      {showImport && <AgStockImport farmId={farmId} onClose={() => setShowImport(false)} />}

      {showCreate && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 10, marginBottom: 10 }}>
            <Field label="Item name"><input value={itemName} onChange={e => setItemName(e.target.value)} style={inp} /></Field>
            <Field label="Category"><select value={category} onChange={e => setCategory(e.target.value)} style={inp}>{CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}</select></Field>
            <Field label="Unit of measure"><select value={uom} onChange={e => setUom(e.target.value)} style={inp}>{STOCK_UNITS.map(u => <option key={u} value={u}>{u}</option>)}</select></Field>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 10, marginBottom: 12 }}>
            <Field label="Reorder level"><input type="number" min={0} value={reorderLevel} onChange={e => setReorderLevel(e.target.value)} style={inp} /></Field>
            <Field label="Unit cost (R)"><input type="number" min={0} step="0.01" value={unitCost} onChange={e => setUnitCost(e.target.value)} style={inp} /></Field>
            <Field label="Supplier"><input value={supplier} onChange={e => setSupplier(e.target.value)} style={inp} /></Field>
            {isCountUnit(uom) && (
              <Field label={`Kg per ${unitSingular(uom)} (optional)`}><input type="number" min={0} step="0.1" value={packSize} onChange={e => setPackSize(e.target.value)} placeholder="e.g. 50" style={inp} /></Field>
            )}
          </div>
          {packSizeError(uom, packSize) && <p role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", margin: "0 0 8px" }}>{packSizeError(uom, packSize)}</p>}
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending || !itemName.trim() || !!packSizeError(uom, packSize)} onClick={() => createMut.mutate()}
              style={{ ...btnPrimary, opacity: createMut.isPending || !itemName.trim() || packSizeError(uom, packSize) ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : isError ? <QueryError onRetry={() => void refetch()} /> :
        items.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No stock items yet.</p> : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          {items.map((it, i) => (
            <div key={it.id}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <Package size={15} style={{ color: it.belowReorderLevel ? "var(--hf-warning-text)" : AG_ACCENT_TEXT }} />
                  <div>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{it.itemName}</p>
                    <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0 }}>
                      {it.category} · {it.currentQuantity} {it.unitOfMeasure}{it.reorderLevel != null ? ` (reorder at ${it.reorderLevel})` : ""}
                      {it.unitCost != null ? ` · ${fmtMoney(it.unitCost)}/${it.unitOfMeasure}` : ""}{it.supplier ? ` · ${it.supplier}` : ""}
                      {it.packSizeKg ? ` · ${it.packSizeKg} kg per ${unitSingular(it.unitOfMeasure)}${it.costPerKg != null ? ` (${fmtMoney(it.costPerKg)}/kg)` : ""}` : isCountUnit(it.unitOfMeasure) ? ` · kg per ${unitSingular(it.unitOfMeasure)} not set` : ""}
                    </p>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                  <span style={statusBadge(it.status)}>{it.status}</span>
                  <AgManage>
                  <button onClick={() => setActing({ item: it, kind: "receive" })} title="Receive stock" style={iconBtn}><ArrowDownToLine size={13} /></button>
                  <button onClick={() => setActing({ item: it, kind: "issue" })} title="Issue stock" style={iconBtn}><ArrowUpFromLine size={13} /></button>
                  <button onClick={() => setActing({ item: it, kind: "adjust" })} title="Adjust quantity" style={iconBtn}><Scale size={13} /></button>
                  <button onClick={() => setEditing(it)} title="Edit" style={iconBtn}><Pencil size={13} /></button>
                  {it.status === "ACTIVE" ? (
                    <button onClick={() => deactivateMut.mutate(it.id)} title="Deactivate" style={iconBtn}><Ban size={13} /></button>
                  ) : (
                    <button onClick={() => reactivateMut.mutate(it.id)} title="Reactivate" style={iconBtn}><RotateCcw size={13} /></button>
                  )}
                  <button onClick={async () => { if (await agConfirm({ title: `Delete "${it.itemName}"?`, confirmLabel: "Delete", danger: true })) deleteMut.mutate(it.id) }} title="Delete" style={{ ...iconBtn, color: "var(--hf-danger-text)" }}><Trash2 size={13} /></button>
                  </AgManage>
                </div>
              </div>

              {editing?.id === it.id && (
                <div style={{ padding: "0 16px 16px" }}>
                  <EditItemForm item={editing} saving={updateMut.isPending} onCancel={() => setEditing(null)} onSave={v => updateMut.mutate(v)} />
                </div>
              )}

              {acting?.item.id === it.id && (
                <div style={{ padding: "0 16px 16px" }}>
                  <div style={{ display: "flex", gap: 8, alignItems: "flex-end" }}>
                    <Field label={`${acting.kind === "adjust" ? "Counted quantity" : "Quantity"} (${it.unitOfMeasure})`}><input type="number" min={0} step="0.01" value={qty} onChange={e => setQty(e.target.value)} style={{ ...inp, width: 130 }} /></Field>
                    {acting.kind === "receive" && (
                      <Field label={`Cost per ${it.unitOfMeasure} this delivery (R)`}><input type="number" min={0} step="0.01" value={deliveryCost} onChange={e => setDeliveryCost(e.target.value)} style={{ ...inp, width: 150 }} /></Field>
                    )}
                    <div style={{ flex: 1 }}>
                      <Field label={acting.kind === "adjust" ? "Reason (required)" : "Notes"}>
                        <input value={notes} onChange={e => setNotes(e.target.value)} placeholder={acting.kind === "adjust" ? "e.g. Stock count, spilled, found" : ""} style={inp} />
                      </Field>
                    </div>
                    <button disabled={actionMut.isPending} onClick={submitAction}
                      style={{ ...btnPrimary, opacity: actionMut.isPending ? 0.6 : 1, textTransform: "capitalize" }}>
                      {actionMut.isPending ? "Saving…" : acting.kind}
                    </button>
                    <button onClick={closeAction} style={btnGhost}>Cancel</button>
                  </div>
                  {acting.kind === "adjust" && qty.trim() !== "" && Number(qty) >= 0 && (
                    <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "8px 0 0" }}>{adjustmentText(it.currentQuantity, Number(qty), it.unitOfMeasure)}</p>
                  )}
                  {acting.kind === "receive" && deliveryCost !== "" && Number(qty) > 0 && Number(deliveryCost) >= 0 && (
                    <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "8px 0 0" }}>
                      Average cost becomes R{averageCostAfterReceipt(it.currentQuantity, it.unitCost, Number(qty), Number(deliveryCost))?.toFixed(2)} per {it.unitOfMeasure}.
                    </p>
                  )}
                  {acting.kind === "receive" && deliveryCost === "" && <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "8px 0 0" }}>Leave the cost empty to keep the item's current cost.</p>}
                  {actionError && <p role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", margin: "8px 0 0" }}>{actionError}</p>}
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
  onSave: (v: { itemName: string; reorderLevel: string; unitCost: string; supplier: string; notes: string; packSize: string }) => void
}) {
  const [itemName, setItemName] = useState(item.itemName)
  const [reorderLevel, setReorderLevel] = useState(item.reorderLevel != null ? String(item.reorderLevel) : "")
  const [unitCost, setUnitCost] = useState(item.unitCost != null ? String(item.unitCost) : "")
  const [supplier, setSupplier] = useState(item.supplier ?? "")
  const [notes, setNotes] = useState(item.notes ?? "")
  const [packSize, setPackSize] = useState(item.packSizeKg != null ? String(item.packSizeKg) : "")
  const problem = packSizeError(item.unitOfMeasure, packSize)
  return (
    <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }}>
      <div style={{ marginBottom: 8 }}><AgAuditLine type="inventory-items" id={item.id} /></div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 10, marginBottom: 12 }}>
        <Field label="Item name"><input value={itemName} onChange={e => setItemName(e.target.value)} style={inp} /></Field>
        <Field label="Reorder level"><input type="number" min={0} value={reorderLevel} onChange={e => setReorderLevel(e.target.value)} style={inp} /></Field>
        <Field label="Unit cost (R)"><input type="number" min={0} step="0.01" value={unitCost} onChange={e => setUnitCost(e.target.value)} style={inp} /></Field>
        <Field label="Supplier"><input value={supplier} onChange={e => setSupplier(e.target.value)} style={inp} /></Field>
        {isCountUnit(item.unitOfMeasure) && (
          <Field label={`Kg per ${unitSingular(item.unitOfMeasure)}`}><input type="number" min={0} step="0.1" value={packSize} onChange={e => setPackSize(e.target.value)} placeholder="e.g. 50" style={inp} /></Field>
        )}
      </div>
      {problem && <p role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", margin: "0 0 8px" }}>{problem}</p>}
      <div style={{ marginBottom: 12 }}><Field label="Notes"><input value={notes} onChange={e => setNotes(e.target.value)} style={inp} /></Field></div>
      <div style={{ display: "flex", gap: 8 }}>
        <button disabled={saving || !itemName.trim() || !!problem} onClick={() => onSave({ itemName, reorderLevel, unitCost, supplier, notes, packSize })}
          style={{ ...btnPrimary, opacity: saving || !itemName.trim() || problem ? 0.6 : 1 }}>{saving ? "Saving…" : "Save"}</button>
        <button onClick={onCancel} style={btnGhost}>Cancel</button>
      </div>
    </div>
  )
}

const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 28, height: 28, borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", cursor: "pointer" }
