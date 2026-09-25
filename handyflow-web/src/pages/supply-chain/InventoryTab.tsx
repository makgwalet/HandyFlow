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
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 9, fontSize: 14, boxSizing: "border-box", outline: "none", background: "var(--hf-surface)" }
const fmtR = (n: number) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const fmtD = (d: string) => new Date(d).toLocaleDateString("en-ZA")

const MOVE_TYPE_COLOR: Record<string, { bg: string; color: string }> = {
  OPENING:         { bg: "var(--hf-surface-sunken)", color: "var(--hf-text-tertiary)" },
  PURCHASE:        { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)" },
  SALE:            { bg: "var(--hf-danger-soft-strong)", color: "var(--hf-danger-text)" },
  TRANSFER_IN:     { bg: "var(--hf-info-soft-strong)", color: "var(--hf-info-text)" },
  TRANSFER_OUT:    { bg: "var(--hf-violet-soft-strong)", color: "var(--hf-violet-text)" },
  ADJUSTMENT_UP:   { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)" },
  ADJUSTMENT_DOWN: { bg: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text-deep)" },
  WASTE:           { bg: "var(--hf-danger-soft-strong)", color: "var(--hf-danger-text)" },
  RETURN_IN:       { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)" },
  RETURN_OUT:      { bg: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text-deep)" },
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
            style={{ padding: "6px 14px", borderRadius: 20, fontSize: 12, cursor: "pointer", fontWeight: !selectedLocation ? 700 : 400, border: !selectedLocation ? `1.5px solid ${ACCENT}` : "1px solid var(--hf-border)", background: !selectedLocation ? "var(--hf-warning-soft-strong)" : "var(--hf-surface)", color: !selectedLocation ? ACCENT : "var(--hf-text-muted)" }}>
            All Locations
          </button>
          {locations.map(l => (
            <button key={l.id} onClick={() => setSelectedLocation(l.id)}
              style={{ padding: "6px 14px", borderRadius: 20, fontSize: 12, cursor: "pointer", fontWeight: selectedLocation === l.id ? 700 : 400, border: selectedLocation === l.id ? `1.5px solid ${ACCENT}` : "1px solid var(--hf-border)", background: selectedLocation === l.id ? "var(--hf-warning-soft-strong)" : "var(--hf-surface)", color: selectedLocation === l.id ? ACCENT : "var(--hf-text-muted)" }}>
              {l.name}
            </button>
          ))}
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          {lowCount > 0 && (
            <div style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-warning-soft-strong)", borderRadius: 8, fontSize: 12, fontWeight: 700, color: "var(--hf-warning-text-deep)" }}>
              <AlertTriangle size={13} /> {lowCount} low stock
            </div>
          )}
          <button onClick={() => { setShowOpening(true); setErr(""); setItemQuery(""); setShowItemDropdown(false) }}
            style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 14px", background: ACCENT, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={14} /> Set Opening Stock
          </button>
        </div>
      </div>

      {/* Inventory table */}
      {isLoading
        ? <div style={{ padding: 40, textAlign: "center", color: "var(--hf-text-faint)" }}>Loading…</div>
        : inventory.length === 0
          ? <div style={{ textAlign: "center", padding: "50px 0", color: "var(--hf-text-faint)" }}>
              <Package size={36} style={{ opacity: .3, marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No inventory</div>
              <div style={{ fontSize: 13 }}>Set opening stock to start tracking quantities</div>
            </div>
          : <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
              {inventory.map((item, i) => {
                const isLow = item.reorderPoint > 0 && item.qtyOnHand <= item.reorderPoint
                const isCritical = isLow && item.qtyOnHand <= 0
                const isOpen = expandedItem === item.id
                const stockPct = item.reorderPoint > 0 ? Math.min((item.qtyOnHand / item.reorderPoint) * 100, 200) : 100
                return (
                  <div key={item.id} style={{ borderTop: i > 0 ? "1px solid var(--hf-border-subtle)" : "none" }}>
                    <div
                      onClick={() => setExpandedItem(isOpen ? null : item.id)}
                      style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", cursor: "pointer", background: isCritical ? "var(--hf-danger-soft)" : isLow ? "var(--hf-warning-soft)" : i % 2 === 0 ? "var(--hf-surface)" : "var(--hf-surface-muted)" }}
                    >
                      {/* Status indicator */}
                      <div style={{ width: 6, height: 6, borderRadius: "50%", flexShrink: 0, background: isCritical ? "var(--hf-danger)" : isLow ? "var(--hf-warning)" : "var(--hf-success)" }} />

                      {/* Item identity */}
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)", marginBottom: 2 }}>
                          {itemNameById.get(item.catalogueItemId) ?? `Item ${item.catalogueItemId.slice(0, 12)}…`}
                          {item.binLocation && <span style={{ fontSize: 11, color: "var(--hf-text-faint)", marginLeft: 8 }}>Bin: {item.binLocation}</span>}
                          {isLow && <span style={{ marginLeft: 8, fontSize: 10, fontWeight: 700, background: isCritical ? "var(--hf-danger-soft-strong)" : "var(--hf-warning-soft-strong)", color: isCritical ? "var(--hf-danger-text)" : "var(--hf-warning-text-deep)", padding: "1px 6px", borderRadius: 20 }}>{isCritical ? "OUT OF STOCK" : "LOW STOCK"}</span>}
                        </div>
                        {/* Stock bar */}
                        <div style={{ height: 4, background: "var(--hf-surface-sunken)", borderRadius: 2, marginTop: 4 }}>
                          <div style={{ height: "100%", width: `${Math.min(stockPct, 100)}%`, background: isCritical ? "var(--hf-danger)" : isLow ? "var(--hf-warning)" : "var(--hf-success)", borderRadius: 2 }} />
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
                      <div style={{ padding: "12px 20px", background: "var(--hf-surface-muted)", borderTop: "1px solid var(--hf-border)" }}>
                        <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-tertiary)", marginBottom: 10, display: "flex", alignItems: "center", gap: 6 }}>
                          <TrendingUp size={13} /> Movement History (last 20)
                        </div>
                        {movements.length === 0
                          ? <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No movements recorded</div>
                          : <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
                              <thead><tr>
                                {["Date", "Type", "Change", "Before", "After", "Unit Cost", "Reference", "By"].map(h => (
                                  <th key={h} style={{ textAlign: "left", padding: "5px 10px", color: "var(--hf-text-faint)", fontWeight: 600, fontSize: 10, textTransform: "uppercase", letterSpacing: "0.04em" }}>{h}</th>
                                ))}
                              </tr></thead>
                              <tbody>
                                {movements.map(m => {
                                  const mc = MOVE_TYPE_COLOR[m.movementType] ?? { bg: "var(--hf-surface-sunken)", color: "var(--hf-text-tertiary)" }
                                  return (
                                    <tr key={m.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                                      <td style={{ padding: "6px 10px" }}>{fmtD(m.createdAt)}</td>
                                      <td style={{ padding: "6px 10px" }}><span style={{ background: mc.bg, color: mc.color, fontSize: 9, fontWeight: 700, padding: "1px 6px", borderRadius: 12 }}>{m.movementType.replace(/_/g," ")}</span></td>
                                      <td style={{ padding: "6px 10px", fontWeight: 700, color: m.qtyChange > 0 ? "var(--hf-success-text)" : "var(--hf-danger-text)" }}>{m.qtyChange > 0 ? "+" : ""}{m.qtyChange.toFixed(2)}</td>
                                      <td style={{ padding: "6px 10px", color: "var(--hf-text-muted)" }}>{m.qtyBefore.toFixed(2)}</td>
                                      <td style={{ padding: "6px 10px", fontWeight: 600 }}>{m.qtyAfter.toFixed(2)}</td>
                                      <td style={{ padding: "6px 10px", color: "var(--hf-text-muted)" }}>{m.unitCost != null ? fmtR(m.unitCost) : "—"}</td>
                                      <td style={{ padding: "6px 10px", color: "var(--hf-text-muted)" }}>{m.referenceNumber ?? "—"}</td>
                                      <td style={{ padding: "6px 10px", color: "var(--hf-text-faint)" }}>{m.createdByName ?? "—"}</td>
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
                  <div style={{ position: "absolute", top: "100%", left: 0, right: 0, zIndex: 10, background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, marginTop: 4, maxHeight: 220, overflowY: "auto", boxShadow: "0 8px 24px rgba(0,0,0,0.1)" }}>
                    {filteredItems.length === 0
                      ? <div style={{ padding: "10px 12px", fontSize: 13, color: "var(--hf-text-faint)" }}>No matching items</div>
                      : filteredItems.slice(0, 30).map(i => (
                        <div key={i.id}
                          onMouseDown={() => { sf("catalogueItemId", i.id); setItemQuery(""); setShowItemDropdown(false) }}
                          style={{ padding: "9px 12px", fontSize: 13, cursor: "pointer", borderBottom: "1px solid var(--hf-border-subtle)" }}
                          onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = "var(--hf-warning-soft)"}
                          onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = "var(--hf-surface)"}>
                          <div style={{ fontWeight: 600, color: "var(--hf-text)" }}>{i.name}</div>
                          {(i.categoryName || i.unit) && (
                            <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{[i.categoryName, i.unit].filter(Boolean).join(" · ")}</div>
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
      <div style={{ fontSize: 10, color: "var(--hf-text-faint)", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.04em", marginBottom: 2 }}>{label}</div>
      <div style={{ fontSize: 13, fontWeight: 700, color: color ?? "var(--hf-text)" }}>{value}</div>
    </div>
  )
}
