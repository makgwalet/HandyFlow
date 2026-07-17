// src/pages/supply-chain/InventoryTab.tsx
import React, { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Package, AlertTriangle, TrendingUp, ChevronDown, ChevronUp } from "lucide-react"
import { Modal, ErrBox, ModalFooter, Field } from "./scm.shared"

interface Location { id: string; name: string; locationType: string; isDefault: boolean }
interface InventoryItem {
  id: string; catalogueItemId: string; locationId: string; qtyOnHand: number; qtyReserved: number
  qtyInTransit: number; reorderPoint: number; reorderQty: number; avgCost: number; lastCost: number
  binLocation: string | null; updatedAt: string
}
interface Movement {
  id: string; movementType: string; qtyChange: number; qtyBefore: number; qtyAfter: number
  unitCost: number | null; referenceType: string | null; referenceNumber: string | null
  createdByName: string | null; createdAt: string; notes: string | null
}
// NEW: backs the catalogue-item search picker below.
interface CatalogueItem { id: string; name: string; description: string | null; unit: string | null; defaultPrice: number | null; categoryName: string | null }

const ACCENT = "#D97706"
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 9, fontSize: 14, boxSizing: "border-box", outline: "none", background: "#fff" }
const fmtR = (n: number) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const fmtD = (d: string) => new Date(d).toLocaleDateString("en-ZA")

const MOVE_TYPE_COLOR: Record<string, { bg: string; color: string }> = {
  OPENING:         { bg: "#F1F5F9", color: "#475569" },
  PURCHASE:        { bg: "#DCFCE7", color: "#166534" },
  SALE:            { bg: "#FEE2E2", color: "#DC2626" },
  TRANSFER_IN:     { bg: "#DBEAFE", color: "#1D4ED8" },
  TRANSFER_OUT:    { bg: "#EDE9FE", color: "#7C3AED" },
  ADJUSTMENT_UP:   { bg: "#D1FAE5", color: "#065F46" },
  ADJUSTMENT_DOWN: { bg: "#FEF3C7", color: "#92400E" },
  WASTE:           { bg: "#FEE2E2", color: "#DC2626" },
  RETURN_IN:       { bg: "#DCFCE7", color: "#166534" },
  RETURN_OUT:      { bg: "#FEF3C7", color: "#92400E" },
}

export function InventoryTab() {
  const qc = useQueryClient()
  const [selectedLocation, setSelectedLocation] = useState<string>("")
  const [expandedItem, setExpandedItem] = useState<string | null>(null)
  const [showOpening, setShowOpening] = useState(false)
  const [err, setErr] = useState("")
  // NEW (Tier 1 gap analysis): drives the catalogue-item search picker —
  // previously "Set Opening Stock" required typing a raw catalogue-item
  // UUID by hand, unlike Location right next to it in the same modal,
  // which was already a proper dropdown.
  const [itemQuery, setItemQuery] = useState("")
  const [showItemDropdown, setShowItemDropdown] = useState(false)

  const initF = () => ({ catalogueItemId: "", locationId: "", qty: "", unitCost: "", reorderPoint: "", reorderQty: "", binLocation: "" })
  const [form, setForm] = useState(initF())
  const sf = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: locations = [] } = useQuery<Location[]>({
    queryKey: ["scm-locations"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/supply-chain/locations"); const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : [] },
    staleTime: 120_000,
  })

  // NEW (Tier 1 gap analysis): fetched once (empty query = all items),
  // filtered client-side as the person types — same pattern already used
  // elsewhere in this module (e.g. suppliers fetched at size=200 and
  // filtered in the UI) rather than a search-as-you-type network call
  // per keystroke.
  //
  // NOTE: GET /api/v1/catalogue/items is currently permission-gated to
  // INVOICE_CREATE/POS_READ/POS_MANAGE/POS_SELL — none of which are SCM
  // permissions. A warehouse/SCM-only user may get a 403 here until
  // that's resolved on the Catalogue module's own side.
  const { data: catalogueItems = [] } = useQuery<CatalogueItem[]>({
    queryKey: ["catalogue-items-all"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/catalogue/items"); const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : [] },
    staleTime: 60_000,
  })
  const itemNameById = new Map(catalogueItems.map(i => [i.id, i.name]))
  const filteredItems = itemQuery.trim()
    ? catalogueItems.filter(i => i.name.toLowerCase().includes(itemQuery.trim().toLowerCase()))
    : catalogueItems

  const { data: inventory = [], isLoading } = useQuery<InventoryItem[]>({
    queryKey: ["scm-inventory", selectedLocation],
    queryFn: async () => {
      const url = selectedLocation ? `/api/v1/supply-chain/inventory?locationId=${selectedLocation}` : "/api/v1/supply-chain/inventory"
      const r = await apiClient.get(url); const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : []
    },
    staleTime: 30_000,
  })

  const { data: movements = [] } = useQuery<Movement[]>({
    queryKey: ["scm-movements", expandedItem],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/supply-chain/inventory/${expandedItem}/movements?size=20`); const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : [] },
    enabled: !!expandedItem,
    staleTime: 30_000,
  })

  const openingMut = useMutation({
    mutationFn: (b: any) => apiClient.post("/api/v1/supply-chain/inventory/opening", b),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["scm-inventory"] }); setShowOpening(false); setForm(initF()); setErr(""); setItemQuery("") },
    onError: (e: any) => setErr(e.response?.data?.message || "Failed to set opening stock"),
  })

  const lowCount = inventory.filter(i => i.reorderPoint > 0 && i.qtyOnHand <= i.reorderPoint).length

  return (
    <div>
      {/* Location tabs + actions */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          <button onClick={() => setSelectedLocation("")}
            style={{ padding: "6px 14px", borderRadius: 20, fontSize: 12, cursor: "pointer", fontWeight: !selectedLocation ? 700 : 400, border: !selectedLocation ? `1.5px solid ${ACCENT}` : "1px solid #E2E8F0", background: !selectedLocation ? "#FEF3C7" : "#fff", color: !selectedLocation ? ACCENT : "#64748B" }}>
            All Locations
          </button>
          {locations.map(l => (
            <button key={l.id} onClick={() => setSelectedLocation(l.id)}
              style={{ padding: "6px 14px", borderRadius: 20, fontSize: 12, cursor: "pointer", fontWeight: selectedLocation === l.id ? 700 : 400, border: selectedLocation === l.id ? `1.5px solid ${ACCENT}` : "1px solid #E2E8F0", background: selectedLocation === l.id ? "#FEF3C7" : "#fff", color: selectedLocation === l.id ? ACCENT : "#64748B" }}>
              {l.name}
            </button>
          ))}
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          {lowCount > 0 && (
            <div style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "#FEF3C7", borderRadius: 8, fontSize: 12, fontWeight: 700, color: "#92400E" }}>
              <AlertTriangle size={13} /> {lowCount} low stock
            </div>
          )}
          <button onClick={() => { setShowOpening(true); setErr(""); setItemQuery(""); setShowItemDropdown(false) }}
            style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 14px", background: ACCENT, color: "#fff", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={14} /> Set Opening Stock
          </button>
        </div>
      </div>

      {/* Inventory table */}
      {isLoading
        ? <div style={{ padding: 40, textAlign: "center", color: "#94A3B8" }}>Loading…</div>
        : inventory.length === 0
          ? <div style={{ textAlign: "center", padding: "50px 0", color: "#94A3B8" }}>
              <Package size={36} style={{ opacity: .3, marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "#475569" }}>No inventory</div>
              <div style={{ fontSize: 13 }}>Set opening stock to start tracking quantities</div>
            </div>
          : <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              {inventory.map((item, i) => {
                const isLow = item.reorderPoint > 0 && item.qtyOnHand <= item.reorderPoint
                const isCritical = isLow && item.qtyOnHand <= 0
                const isOpen = expandedItem === item.id
                const stockPct = item.reorderPoint > 0 ? Math.min((item.qtyOnHand / item.reorderPoint) * 100, 200) : 100
                return (
                  <div key={item.id} style={{ borderTop: i > 0 ? "1px solid #F1F5F9" : "none" }}>
                    <div
                      onClick={() => setExpandedItem(isOpen ? null : item.id)}
                      style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", cursor: "pointer", background: isCritical ? "#FEF2F2" : isLow ? "#FFFBEB" : i % 2 === 0 ? "#fff" : "#FAFAFA" }}
                    >
                      {/* Status indicator */}
                      <div style={{ width: 6, height: 6, borderRadius: "50%", flexShrink: 0, background: isCritical ? "#EF4444" : isLow ? "#F59E0B" : "#22C55E" }} />

                      {/* Item identity */}
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", marginBottom: 2 }}>
                          {itemNameById.get(item.catalogueItemId) ?? `Item ${item.catalogueItemId.slice(0, 12)}…`}
                          {item.binLocation && <span style={{ fontSize: 11, color: "#94A3B8", marginLeft: 8 }}>Bin: {item.binLocation}</span>}
                          {isLow && <span style={{ marginLeft: 8, fontSize: 10, fontWeight: 700, background: isCritical ? "#FEE2E2" : "#FEF3C7", color: isCritical ? "#DC2626" : "#92400E", padding: "1px 6px", borderRadius: 20 }}>{isCritical ? "OUT OF STOCK" : "LOW STOCK"}</span>}
                        </div>
                        {/* Stock bar */}
                        <div style={{ height: 4, background: "#F1F5F9", borderRadius: 2, marginTop: 4 }}>
                          <div style={{ height: "100%", width: `${Math.min(stockPct, 100)}%`, background: isCritical ? "#EF4444" : isLow ? "#F59E0B" : "#22C55E", borderRadius: 2 }} />
                        </div>
                      </div>

                      {/* Metrics */}
                      <div style={{ display: "flex", gap: 24, flexShrink: 0 }}>
                        <Metric label="On Hand"    value={item.qtyOnHand.toFixed(2)}    color={isCritical ? "#DC2626" : isLow ? ACCENT : "#0F172A"} />
                        <Metric label="Reserved"   value={item.qtyReserved.toFixed(2)} />
                        <Metric label="Reorder At" value={item.reorderPoint > 0 ? item.reorderPoint.toFixed(2) : "—"} />
                        <Metric label="Avg Cost"   value={fmtR(item.avgCost)} />
                        <Metric label="Stock Value" value={fmtR(item.qtyOnHand * item.avgCost)} />
                      </div>
                      {isOpen ? <ChevronUp size={15} color="#94A3B8" /> : <ChevronDown size={15} color="#94A3B8" />}
                    </div>

                    {/* Movement history */}
                    {isOpen && (
                      <div style={{ padding: "12px 20px", background: "#F8FAFC", borderTop: "1px solid #E2E8F0" }}>
                        <div style={{ fontSize: 12, fontWeight: 700, color: "#475569", marginBottom: 10, display: "flex", alignItems: "center", gap: 6 }}>
                          <TrendingUp size={13} /> Movement History (last 20)
                        </div>
                        {movements.length === 0
                          ? <div style={{ fontSize: 12, color: "#94A3B8" }}>No movements recorded</div>
                          : <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
                              <thead><tr>
                                {["Date", "Type", "Change", "Before", "After", "Unit Cost", "Reference", "By"].map(h => (
                                  <th key={h} style={{ textAlign: "left", padding: "5px 10px", color: "#94A3B8", fontWeight: 600, fontSize: 10, textTransform: "uppercase", letterSpacing: "0.04em" }}>{h}</th>
                                ))}
                              </tr></thead>
                              <tbody>
                                {movements.map(m => {
                                  const mc = MOVE_TYPE_COLOR[m.movementType] ?? { bg: "#F1F5F9", color: "#475569" }
                                  return (
                                    <tr key={m.id} style={{ borderTop: "1px solid #F1F5F9" }}>
                                      <td style={{ padding: "6px 10px" }}>{fmtD(m.createdAt)}</td>
                                      <td style={{ padding: "6px 10px" }}><span style={{ background: mc.bg, color: mc.color, fontSize: 9, fontWeight: 700, padding: "1px 6px", borderRadius: 12 }}>{m.movementType.replace(/_/g," ")}</span></td>
                                      <td style={{ padding: "6px 10px", fontWeight: 700, color: m.qtyChange > 0 ? "#059669" : "#DC2626" }}>{m.qtyChange > 0 ? "+" : ""}{m.qtyChange.toFixed(2)}</td>
                                      <td style={{ padding: "6px 10px", color: "#64748B" }}>{m.qtyBefore.toFixed(2)}</td>
                                      <td style={{ padding: "6px 10px", fontWeight: 600 }}>{m.qtyAfter.toFixed(2)}</td>
                                      <td style={{ padding: "6px 10px", color: "#64748B" }}>{m.unitCost != null ? fmtR(m.unitCost) : "—"}</td>
                                      <td style={{ padding: "6px 10px", color: "#64748B" }}>{m.referenceNumber ?? "—"}</td>
                                      <td style={{ padding: "6px 10px", color: "#94A3B8" }}>{m.createdByName ?? "—"}</td>
                                    </tr>
                                  )
                                })}
                              </tbody>
                            </table>
                        }
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
      }

      {/* Opening Stock Modal */}
      {showOpening && (
        <Modal title="Set Opening Stock" onClose={() => { setShowOpening(false); setItemQuery(""); setShowItemDropdown(false) }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Location *" span={2}>
              <select value={form.locationId} onChange={e => sf("locationId", e.target.value)} style={inp}>
                <option value="">Select location…</option>
                {locations.map(l => <option key={l.id} value={l.id}>{l.name}</option>)}
              </select>
            </Field>
            <Field label="Catalogue Item *" span={2}>
              <div style={{ position: "relative" }}>
                <input
                  value={form.catalogueItemId ? (itemNameById.get(form.catalogueItemId) ?? "") : itemQuery}
                  onChange={e => { setItemQuery(e.target.value); sf("catalogueItemId", ""); setShowItemDropdown(true) }}
                  onFocus={() => setShowItemDropdown(true)}
                  onBlur={() => setTimeout(() => setShowItemDropdown(false), 150)}
                  placeholder="Search catalogue items…"
                  style={inp}
                />
                {showItemDropdown && (
                  <div style={{ position: "absolute", top: "100%", left: 0, right: 0, zIndex: 10, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, marginTop: 4, maxHeight: 220, overflowY: "auto", boxShadow: "0 8px 24px rgba(0,0,0,0.1)" }}>
                    {filteredItems.length === 0
                      ? <div style={{ padding: "10px 12px", fontSize: 13, color: "#94A3B8" }}>No matching items</div>
                      : filteredItems.slice(0, 30).map(i => (
                        <div key={i.id}
                          onMouseDown={() => { sf("catalogueItemId", i.id); setItemQuery(""); setShowItemDropdown(false) }}
                          style={{ padding: "9px 12px", fontSize: 13, cursor: "pointer", borderBottom: "1px solid #F1F5F9" }}
                          onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = "#FFFBEB"}
                          onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = "#fff"}>
                          <div style={{ fontWeight: 600, color: "#0F172A" }}>{i.name}</div>
                          {(i.categoryName || i.unit) && (
                            <div style={{ fontSize: 11, color: "#94A3B8" }}>{[i.categoryName, i.unit].filter(Boolean).join(" · ")}</div>
                          )}
                        </div>
                      ))
                    }
                  </div>
                )}
              </div>
            </Field>
            <Field label="Opening Qty *">
              <input type="number" value={form.qty} onChange={e => sf("qty", e.target.value)} placeholder="0.00" style={inp} />
            </Field>
            <Field label="Unit Cost (R)">
              <input type="number" value={form.unitCost} onChange={e => sf("unitCost", e.target.value)} placeholder="0.00" style={inp} />
            </Field>
            <Field label="Reorder Point">
              <input type="number" value={form.reorderPoint} onChange={e => sf("reorderPoint", e.target.value)} placeholder="10" style={inp} />
            </Field>
            <Field label="Reorder Qty">
              <input type="number" value={form.reorderQty} onChange={e => sf("reorderQty", e.target.value)} placeholder="50" style={inp} />
            </Field>
            <Field label="Bin Location" span={2}>
              <input value={form.binLocation} onChange={e => sf("binLocation", e.target.value)} placeholder="A-12-3" style={inp} />
            </Field>
          </div>
          {err && <ErrBox msg={err} />}
          <ModalFooter
            onCancel={() => { setShowOpening(false); setItemQuery(""); setShowItemDropdown(false) }}
            onConfirm={() => {
              if (!form.locationId || !form.catalogueItemId || !form.qty) { setErr("Location, catalogue item and quantity are required"); return }
              openingMut.mutate({ locationId: form.locationId, catalogueItemId: form.catalogueItemId, qty: parseFloat(form.qty), unitCost: form.unitCost ? parseFloat(form.unitCost) : null, reorderPoint: form.reorderPoint ? parseFloat(form.reorderPoint) : null, reorderQty: form.reorderQty ? parseFloat(form.reorderQty) : null, binLocation: form.binLocation || null })
            }}
            label={openingMut.isPending ? "Saving…" : "Set Stock"}
            loading={openingMut.isPending}
            accent={ACCENT}
          />
        </Modal>
      )}
    </div>
  )
}

function Metric({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div style={{ textAlign: "right" }}>
      <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.04em", marginBottom: 2 }}>{label}</div>
      <div style={{ fontSize: 13, fontWeight: 700, color: color ?? "#0F172A" }}>{value}</div>
    </div>
  )
}
