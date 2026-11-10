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
import { WHSE_ACCENT, WHSE_ACCENT_TEXT } from "./constants"
import ItemTrackingModal from "./ItemTrackingModal"
import { buildItemPayload, validateItemForm, TEMPERATURE_OPTIONS, CURRENCY_OPTIONS, formatMoney, type ItemFormState } from "./itemForm"

interface ItemResponse {
  id: string; clientId: string; sku: string; description: string | null; uom: string | null
  storageRatePerUnitPerMonth: number | null; active: boolean
  barcode: string | null; lengthMm: number | null; widthMm: number | null; heightMm: number | null; volumeM3: number | null
  weightKg: number | null; unitCost: number | null; unitCostCurrency?: string; minQty: number | null; maxQty: number | null
  temperatureClass: string; hazardous: boolean; fragile: boolean; stackable: boolean
  lotTracked?: boolean; expiryTracked?: boolean; minShelfLifeDays?: number | null; serialTracked?: boolean
}

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5, display: "block" }

function ItemFormModal({ clientId, initial, onClose }: { clientId: string; initial?: ItemResponse; onClose: () => void }) {
  const qc = useQueryClient()
  const str = (v: number | null | undefined) => (v == null ? "" : String(v))
  const [form, setForm] = useState<ItemFormState>({
    sku: initial?.sku ?? "", description: initial?.description ?? "", uom: initial?.uom ?? "each",
    storageRate: str(initial?.storageRatePerUnitPerMonth),
    barcode: initial?.barcode ?? "", lengthMm: str(initial?.lengthMm), widthMm: str(initial?.widthMm), heightMm: str(initial?.heightMm),
    weightKg: str(initial?.weightKg), unitCost: str(initial?.unitCost), unitCostCurrency: initial?.unitCostCurrency ?? "ZAR", minQty: str(initial?.minQty), maxQty: str(initial?.maxQty),
    temperatureClass: initial?.temperatureClass ?? "AMBIENT", hazardous: initial?.hazardous ?? false,
    fragile: initial?.fragile ?? false, stackable: initial?.stackable ?? true,
  })
  const problem = validateItemForm(form)

  const save = useMutation({
    mutationFn: async () => {
      const body = buildItemPayload(form)
      return initial
        ? apiClient.put(`/api/v1/warehousing/items/${initial.id}`, body)
        : apiClient.post(`/api/v1/warehousing/clients/${clientId}/items`, { sku: form.sku, ...body })
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-items", clientId] }); onClose() },
  })
  const set = (k: keyof ItemFormState) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setForm({ ...form, [k]: e.target.value })
  const flag = (k: "hazardous" | "fragile" | "stackable", label: string) => (
    <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12.5, color: "var(--hf-text)" }}>
      <input type="checkbox" checked={form[k]} onChange={e => setForm({ ...form, [k]: e.target.checked })} /> {label}
    </label>
  )
  const field = (id: string, label: string, k: keyof ItemFormState, extra: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <div><label htmlFor={id} style={labelStyle}>{label}</label><input id={id} style={inputStyle} value={form[k] as string} onChange={set(k)} {...extra} /></div>
  )

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 560, maxWidth: "94vw" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>{initial ? "Edit item" : "Add an item"}</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} style={{ color: 'var(--hf-text-faint)' }} /></button>
        </div>
        <div style={{ display: "grid", gap: 12, maxHeight: "62vh", overflowY: "auto", paddingRight: 4 }}>
          <div>
            <label htmlFor="item-sku" style={labelStyle}>SKU *</label>
            <input id="item-sku" style={inputStyle} value={form.sku} disabled={!!initial} onChange={set("sku")} />
            {initial && <p style={{ fontSize: 10.5, color: "var(--hf-text-faint)", margin: "4px 0 0" }}>SKU can't be changed once created.</p>}
          </div>
          {field("item-desc", "Description", "description")}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            {field("item-uom", "Unit of measure", "uom", { placeholder: "each, box, pallet…" })}
            {field("item-rate", "Storage rate override", "storageRate", { type: "number", step: "0.01", placeholder: "blank = client default" })}
          </div>
          {field("item-barcode", "Barcode (GTIN)", "barcode", { inputMode: "numeric", placeholder: "EAN-13, UPC-A, EAN-8 or GTIN-14" })}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 1fr", gap: 8 }}>
            {field("item-l", "Length mm", "lengthMm", { type: "number", min: 0 })}
            {field("item-w", "Width mm", "widthMm", { type: "number", min: 0 })}
            {field("item-h", "Height mm", "heightMm", { type: "number", min: 0 })}
            {field("item-kg", "Weight kg", "weightKg", { type: "number", min: 0, step: "0.001" })}
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 1fr", gap: 8 }}>
            <div>
              <label htmlFor="item-currency" style={labelStyle}>Cost currency</label>
              <select id="item-currency" value={form.unitCostCurrency} onChange={set("unitCostCurrency")} style={inputStyle}>
                {(CURRENCY_OPTIONS.includes(form.unitCostCurrency) ? CURRENCY_OPTIONS : [form.unitCostCurrency, ...CURRENCY_OPTIONS]).map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
            {field("item-cost", "Unit cost", "unitCost", { type: "number", min: 0, step: "0.01" })}
            {field("item-min", "Minimum level", "minQty", { type: "number", min: 0 })}
            {field("item-max", "Maximum level", "maxQty", { type: "number", min: 0 })}
          </div>
          <div>
            <label htmlFor="item-temp" style={labelStyle}>Temperature</label>
            <select id="item-temp" style={inputStyle} value={form.temperatureClass} onChange={set("temperatureClass")}>
              {TEMPERATURE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </div>
          <div style={{ display: "flex", gap: 18, flexWrap: "wrap" }}>
            {flag("hazardous", "Hazardous")}{flag("fragile", "Fragile")}{flag("stackable", "Can be stacked")}
          </div>
        </div>
        {problem && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 12 }}>{problem}</p>}
        {save.isError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 12 }}>{(save.error as any)?.response?.data?.message ?? "Could not save this item"}</p>}
        <button onClick={() => save.mutate()} disabled={!form.sku || !!problem || save.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!form.sku || !!problem || save.isPending) ? 0.6 : 1 }}>
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
  const [tracking, setTracking] = useState<ItemResponse | null>(null)

  const { data: items = [], isLoading } = useQuery<ItemResponse[]>({
    queryKey: ["whse-items", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/items/all`)).data,
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
        <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0 }}>{items.length} item{items.length === 1 ? "" : "s"}</p>
        <button onClick={() => setShowForm(true)}
          style={{ display: "flex", alignItems: "center", gap: 6, background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Add item
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p>
      ) : items.length === 0 ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No items in this client's catalogue yet.</p>
      ) : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          {items.map((it, i) => (
            <div key={it.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)", opacity: it.active ? 1 : 0.55 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ width: 30, height: 30, borderRadius: 8, background: "var(--hf-accent-soft)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <Package size={14} style={{ color: WHSE_ACCENT_TEXT }} />
                </div>
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{it.sku}</p>
                  <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>{it.description ?? "—"}{it.uom ? ` · ${it.uom}` : ""}{it.barcode ? ` · ${it.barcode}` : ""}</p>
                  {it.lotTracked && (
                    <p style={{ fontSize: 10.5, color: "var(--hf-text-muted)", margin: "2px 0 0" }}>
                      lot tracked{it.expiryTracked ? `, expires${it.minShelfLifeDays ? ` (min ${it.minShelfLifeDays} days left to ship)` : ""}` : ""}
                    </p>
                  )}
                  {it.serialTracked && <p style={{ fontSize: 10.5, color: "var(--hf-text-muted)", margin: "2px 0 0" }}>serial tracked</p>}
                  {(it.hazardous || it.fragile || !it.stackable || it.temperatureClass !== "AMBIENT") && (
                    <p style={{ fontSize: 10.5, color: "var(--hf-text-muted)", margin: "2px 0 0" }}>
                      {[it.temperatureClass !== "AMBIENT" ? it.temperatureClass.replace("_", " ").toLowerCase() : null, it.hazardous ? "hazardous" : null, it.fragile ? "fragile" : null, !it.stackable ? "do not stack" : null].filter(Boolean).join(" · ")}
                    </p>
                  )}
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                {it.unitCost != null && <span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>cost {formatMoney(Number(it.unitCost), it.unitCostCurrency)}</span>}
                {it.storageRatePerUnitPerMonth != null && <span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>R{Number(it.storageRatePerUnitPerMonth).toFixed(2)}/mo</span>}
                <button onClick={() => setTracking(it)} title="Lot, expiry and serial tracking" style={{ background: "none", border: "1px solid var(--hf-border)", borderRadius: 6, padding: "4px 8px", cursor: "pointer", fontSize: 11, color: "var(--hf-text-muted)" }}>Lots</button>
                <button onClick={() => setEditing(it)} style={{ background: "none", border: "1px solid var(--hf-border)", borderRadius: 6, padding: 5, cursor: "pointer" }}><Pencil size={12} style={{ color: 'var(--hf-text-muted)' }} /></button>
                {it.active ? (
                  <button onClick={() => deactivate.mutate(it.id)} style={{ background: "none", border: "1px solid var(--hf-border)", borderRadius: 6, padding: 5, cursor: "pointer" }}><PowerOff size={12} style={{ color: 'var(--hf-text-faint)' }} /></button>
                ) : (
                  <button onClick={() => reactivate.mutate(it.id)} style={{ background: "none", border: "1px solid var(--hf-border)", borderRadius: 6, padding: 5, cursor: "pointer" }}><Power size={12} style={{ color: 'var(--hf-success-text)' }} /></button>
                )}
                <button onClick={() => { if (confirm(`Delete ${it.sku}?`)) remove.mutate(it.id) }} style={{ background: "none", border: "1px solid var(--hf-border)", borderRadius: 6, padding: 5, cursor: "pointer" }}><Trash2 size={12} style={{ color: 'var(--hf-danger-text)' }} /></button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showForm && <ItemFormModal clientId={clientId} onClose={() => setShowForm(false)} />}
      {editing && <ItemFormModal clientId={clientId} initial={editing} onClose={() => setEditing(null)} />}
      {tracking && <ItemTrackingModal clientId={clientId} item={tracking} onClose={() => setTracking(null)} />}
    </div>
  )
}
