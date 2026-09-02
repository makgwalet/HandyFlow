// src/pages/warehousing/WhseItemsTab.tsx
//
// A client's own SKU catalogue. CreateItemRequest(sku, description, uom,
// storageRatePerUnitPerMonth) and UpdateItemRequest(description, uom,
// storageRatePerUnitPerMonth) both confirmed via WhseItemController
// source — note UpdateItemRequest deliberately excludes sku (no rename
// once created, same as every other natural-key field in this codebase).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, Package, X, Power, PowerOff, Trash2, Pencil } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT } from "./constants"

interface ItemResponse {
  id: string; clientId: string; sku: string; description: string | null; uom: string | null
  storageRatePerUnitPerMonth: number | null; active: boolean
}

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }

function ItemFormModal({ clientId, initial, onClose }: { clientId: string; initial?: ItemResponse; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState({
    sku: initial?.sku ?? "", description: initial?.description ?? "", uom: initial?.uom ?? "each",
    storageRatePerUnitPerMonth: initial?.storageRatePerUnitPerMonth?.toString() ?? "",
  })

  const save = useMutation({
    mutationFn: async () => {
      const rate = form.storageRatePerUnitPerMonth.trim() === "" ? null : parseFloat(form.storageRatePerUnitPerMonth)
      return initial
        ? apiClient.put(`/api/v1/warehousing/items/${initial.id}`, { description: form.description || null, uom: form.uom || null, storageRatePerUnitPerMonth: rate })
        : apiClient.post(`/api/v1/warehousing/clients/${clientId}/items`, { sku: form.sku, description: form.description || null, uom: form.uom || null, storageRatePerUnitPerMonth: rate })
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-items", clientId] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 420 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>{initial ? "Edit item" : "Add an item"}</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <div style={{ display: "grid", gap: 12 }}>
          <div>
            <label style={labelStyle}>SKU *</label>
            <input style={inputStyle} value={form.sku} disabled={!!initial} onChange={e => setForm({ ...form, sku: e.target.value })} />
            {initial && <p style={{ fontSize: 10.5, color: "#94A3B8", margin: "4px 0 0" }}>SKU can't be changed once created.</p>}
          </div>
          <div><label style={labelStyle}>Description</label><input style={inputStyle} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><label style={labelStyle}>Unit of measure</label><input style={inputStyle} value={form.uom} onChange={e => setForm({ ...form, uom: e.target.value })} placeholder="each, box, pallet…" /></div>
            <div><label style={labelStyle}>Storage rate override</label><input type="number" step="0.01" style={inputStyle} value={form.storageRatePerUnitPerMonth} onChange={e => setForm({ ...form, storageRatePerUnitPerMonth: e.target.value })} placeholder="blank = use client default" /></div>
          </div>
        </div>
        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(save.error as any)?.response?.data?.message ?? "Could not save this item"}</p>}
        <button onClick={() => save.mutate()} disabled={!form.sku || save.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!form.sku || save.isPending) ? 0.6 : 1 }}>
          {save.isPending ? "Saving…" : initial ? "Save changes" : "Add item"}
        </button>
      </div>
    </div>
  )
}

export default function WhseItemsTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<ItemResponse | null>(null)

  const { data: items = [], isLoading } = useQuery<ItemResponse[]>({
    queryKey: ["whse-items", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/items`)).data,
  })

  const deactivate = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/warehousing/items/${id}/deactivate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["whse-items", clientId] }),
  })
  const reactivate = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/warehousing/items/${id}/reactivate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["whse-items", clientId] }),
  })
  const remove = useMutation({
    mutationFn: async (id: string) => apiClient.delete(`/api/v1/warehousing/items/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["whse-items", clientId] }),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>{items.length} item{items.length === 1 ? "" : "s"}</p>
        <button onClick={() => setShowForm(true)}
          style={{ display: "flex", alignItems: "center", gap: 6, background: WHSE_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Add item
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : items.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No items in this client's catalogue yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {items.map((it, i) => (
            <div key={it.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9", opacity: it.active ? 1 : 0.55 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ width: 30, height: 30, borderRadius: 8, background: "#F0FDFA", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <Package size={14} color={WHSE_ACCENT} />
                </div>
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{it.sku}</p>
                  <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>{it.description ?? "—"}{it.uom ? ` · ${it.uom}` : ""}</p>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                {it.storageRatePerUnitPerMonth != null && <span style={{ fontSize: 11.5, color: "#64748B" }}>R{Number(it.storageRatePerUnitPerMonth).toFixed(2)}/mo</span>}
                <button onClick={() => setEditing(it)} style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 6, padding: 5, cursor: "pointer" }}><Pencil size={12} color="#64748B" /></button>
                {it.active ? (
                  <button onClick={() => deactivate.mutate(it.id)} style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 6, padding: 5, cursor: "pointer" }}><PowerOff size={12} color="#94A3B8" /></button>
                ) : (
                  <button onClick={() => reactivate.mutate(it.id)} style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 6, padding: 5, cursor: "pointer" }}><Power size={12} color="#059669" /></button>
                )}
                <button onClick={() => { if (confirm(`Delete ${it.sku}?`)) remove.mutate(it.id) }} style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 6, padding: 5, cursor: "pointer" }}><Trash2 size={12} color="#DC2626" /></button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showForm && <ItemFormModal clientId={clientId} onClose={() => setShowForm(false)} />}
      {editing && <ItemFormModal clientId={clientId} initial={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
