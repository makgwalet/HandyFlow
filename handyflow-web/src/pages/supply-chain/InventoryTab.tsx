// src/pages/supply-chain/InventoryTab.tsx
import { useState, useEffect } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, Search, AlertTriangle, CheckCircle, Clock, RotateCcw } from "lucide-react"
import { apiClient } from "../../api/client"
import { unwrap, fmtR, fmtDate, inp, TH, TD, Badge, Modal, ModalFooter, Field, ErrBox, Spinner, EmptyState, Banner, filterPill, type InventoryItem, type StockMovement, type StockLocation, type CatalogueItem } from "./scm.shared"

export function InventoryTab() {
  const qc = useQueryClient()
  const [locationId, setLocationId] = useState("")
  const [showOpening, setShowOpening] = useState(false)
  const [historyItem, setHistoryItem] = useState<InventoryItem | null>(null)
  const [err, setErr] = useState("")
  const [catSearch, setCatSearch] = useState("")
  const [catResults, setCatResults] = useState<CatalogueItem[]>([])
  const blank = () => ({ locationId: "", catalogueItemId: "", qty: "", unitCost: "", reorderPoint: "", reorderQty: "", binLocation: "" })
  const [form, setForm] = useState(blank())
  const sf = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))
  const [selectedCat, setSelectedCat] = useState<CatalogueItem | null>(null)

  const { data: locations = [] } = useQuery<StockLocation[]>({ queryKey: ["scm-locations"], queryFn: async () => { const r = await apiClient.get("/api/v1/supply-chain/locations"); return unwrap<StockLocation>(r) }, staleTime: 60_000 })
  const { data: inventory = [], isLoading } = useQuery<InventoryItem[]>({ queryKey: ["scm-inventory", locationId], queryFn: async () => { const r = await apiClient.get(locationId ? `/api/v1/supply-chain/inventory?locationId=${locationId}` : "/api/v1/supply-chain/inventory"); return unwrap<InventoryItem>(r) }, staleTime: 30_000 })
  const { data: lowStock = [] } = useQuery<InventoryItem[]>({ queryKey: ["scm-low-stock"], queryFn: async () => { const r = await apiClient.get("/api/v1/supply-chain/inventory/low-stock"); return unwrap<InventoryItem>(r) }, staleTime: 30_000 })

  useEffect(() => {
    if (catSearch.length < 2) { setCatResults([]); return }
    const t = setTimeout(async () => {
      try { const r = await apiClient.get(`/api/v1/catalogue/items?search=${encodeURIComponent(catSearch)}&size=8`); setCatResults(unwrap<CatalogueItem>(r)) } catch { setCatResults([]) }
    }, 300)
    return () => clearTimeout(t)
  }, [catSearch])

  const openMut = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/supply-chain/inventory/opening", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["scm-inventory"] }); qc.invalidateQueries({ queryKey: ["scm-low-stock"] }); qc.invalidateQueries({ queryKey: ["scm-summary"] }); setShowOpening(false); setForm(blank()); setCatSearch(""); setSelectedCat(null); setErr("") },
    onError: (e: any) => setErr(e.response?.data?.message || "Failed to set stock"),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 10, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <select value={locationId} onChange={e => setLocationId(e.target.value)} style={{ ...inp, width: "auto", minWidth: 180 }}>
            <option value="">All locations</option>
            {locations.map(l => <option key={l.id} value={l.id}>{l.name}</option>)}
          </select>
          {lowStock.length > 0 && <span style={{ background: "#FEF2F2", color: "#DC2626", fontSize: 11, fontWeight: 700, padding: "3px 9px", borderRadius: 20 }}>{lowStock.length} low stock</span>}
        </div>
        <button onClick={() => { setShowOpening(true); setErr("") }} style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 14px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> Set Opening Stock
        </button>
      </div>

      {lowStock.length > 0 && <Banner variant="error">{lowStock.length} item{lowStock.length !== 1 ? "s" : ""} at or below reorder point — consider raising purchase orders</Banner>}

      {isLoading ? <Spinner /> : inventory.length === 0 ? (
        <EmptyState icon={Plus} title="No inventory" sub="Set opening stock or receive a goods receipt to populate inventory" />
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr style={{ background: "#F8FAFC" }}>
              {["Catalogue Item", "Bin", "Qty On Hand", "Reorder Point", "Reorder Qty", "Avg Cost", "Status", ""].map(h => <th key={h} style={TH}>{h}</th>)}
            </tr></thead>
            <tbody>
              {inventory.map((item, i) => (
                <tr key={item.id} style={{ background: i % 2 === 0 ? "#fff" : "#FAFAFA", borderTop: "1px solid #F1F5F9" }}>
                  <td style={TD}><span style={{ fontFamily: "monospace", fontSize: 12, color: "#475569" }}>{item.catalogueItemId.slice(0, 12)}…</span></td>
                  <td style={{ ...TD, color: "#64748B" }}>{item.binLocation || "—"}</td>
                  <td style={TD}><strong style={{ color: item.lowStock ? "#DC2626" : "#0F172A", fontSize: 15 }}>{item.qtyOnHand}</strong></td>
                  <td style={{ ...TD, color: "#64748B" }}>{item.reorderPoint}</td>
                  <td style={{ ...TD, color: "#64748B" }}>{item.reorderQty}</td>
                  <td style={TD}>{fmtR(item.avgCost)}</td>
                  <td style={TD}>
                    {item.lowStock
                      ? <span style={{ display: "flex", alignItems: "center", gap: 5, color: "#DC2626", fontSize: 12, fontWeight: 600 }}><AlertTriangle size={13} />Low Stock</span>
                      : <span style={{ display: "flex", alignItems: "center", gap: 5, color: "#059669", fontSize: 12, fontWeight: 600 }}><CheckCircle size={13} />OK</span>
                    }
                  </td>
                  <td style={TD}>
                    <button onClick={() => setHistoryItem(item)} style={{ background: "none", border: "none", cursor: "pointer", color: "#D97706", fontSize: 12, fontWeight: 600, display: "flex", alignItems: "center", gap: 4 }}>
                      <RotateCcw size={12} /> History
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Set Opening Stock Modal */}
      {showOpening && (
        <Modal title="Set Opening Stock" onClose={() => setShowOpening(false)}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Location *" span={2}>
              <select value={form.locationId} onChange={e => sf("locationId", e.target.value)} style={inp}>
                <option value="">Select location…</option>
                {locations.map(l => <option key={l.id} value={l.id}>{l.name}</option>)}
              </select>
            </Field>
            <Field label="Catalogue Item *" span={2}>
              <div style={{ position: "relative" }}>
                <Search size={13} style={{ position: "absolute", left: 9, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
                <input value={catSearch} onChange={e => { setCatSearch(e.target.value); setSelectedCat(null); sf("catalogueItemId", "") }} placeholder="Search by item name or code…" style={{ ...inp, paddingLeft: 28 }} />
                {catResults.length > 0 && (
                  <div style={{ position: "absolute", top: "100%", left: 0, right: 0, zIndex: 50, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, boxShadow: "0 8px 24px rgba(0,0,0,0.1)", maxHeight: 200, overflowY: "auto" }}>
                    {catResults.map(c => (
                      <div key={c.id} onClick={() => { setCatSearch(c.name); sf("catalogueItemId", c.id); if (c.unitPrice) sf("unitCost", String(c.unitPrice)); setSelectedCat(c); setCatResults([]) }}
                        style={{ padding: "8px 12px", cursor: "pointer", fontSize: 13, borderBottom: "1px solid #F1F5F9" }}
                        onMouseEnter={e => (e.currentTarget.style.background = "#F8FAFC")} onMouseLeave={e => (e.currentTarget.style.background = "#fff")}>
                        <strong>{c.name}</strong> {c.code && <span style={{ color: "#94A3B8", fontSize: 11 }}>({c.code})</span>}
                        {c.unitPrice && <span style={{ float: "right", color: "#D97706", fontSize: 12, fontWeight: 700 }}>{fmtR(c.unitPrice)}</span>}
                      </div>
                    ))}
                  </div>
                )}
              </div>
              {selectedCat && <div style={{ fontSize: 11, color: "#059669", marginTop: 4 }}>Selected: {selectedCat.name}</div>}
            </Field>
            <Field label="Opening Qty *"><input type="number" value={form.qty} onChange={e => sf("qty", e.target.value)} placeholder="0" style={inp} /></Field>
            <Field label="Unit Cost (R)"><input type="number" step="0.01" value={form.unitCost} onChange={e => sf("unitCost", e.target.value)} placeholder="0.00" style={inp} /></Field>
            <Field label="Reorder Point"><input type="number" value={form.reorderPoint} onChange={e => sf("reorderPoint", e.target.value)} placeholder="0" style={inp} /></Field>
            <Field label="Reorder Qty"><input type="number" value={form.reorderQty} onChange={e => sf("reorderQty", e.target.value)} placeholder="0" style={inp} /></Field>
            <Field label="Bin Location" span={2}><input value={form.binLocation} onChange={e => sf("binLocation", e.target.value)} placeholder="e.g. A3-S2" style={inp} /></Field>
          </div>
          {err && <ErrBox msg={err} />}
          <ModalFooter onCancel={() => setShowOpening(false)} loading={openMut.isPending} label={openMut.isPending ? "Saving…" : "Set Stock"}
            onConfirm={() => { if (!form.locationId || !form.catalogueItemId || !form.qty) { setErr("Location, item and quantity are required"); return } openMut.mutate({ locationId: form.locationId, catalogueItemId: form.catalogueItemId, qty: parseFloat(form.qty), unitCost: parseFloat(form.unitCost) || 0, reorderPoint: parseFloat(form.reorderPoint) || 0, reorderQty: parseFloat(form.reorderQty) || 0, binLocation: form.binLocation || null }) }} />
        </Modal>
      )}

      {historyItem && <StockHistoryModal item={historyItem} onClose={() => setHistoryItem(null)} />}
    </div>
  )
}

function StockHistoryModal({ item, onClose }: { item: InventoryItem; onClose: () => void }) {
  const { data: movements = [], isLoading } = useQuery<StockMovement[]>({
    queryKey: ["scm-movements", item.id],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/supply-chain/inventory/${item.id}/movements?size=50`); return unwrap<StockMovement>(r) },
  })
  return (
    <Modal title="Stock Movement History" onClose={onClose}>
      <div style={{ fontSize: 12, color: "#64748B", marginBottom: 12 }}>
        Current stock: <strong style={{ color: "#0F172A" }}>{item.qtyOnHand}</strong> · Avg cost: <strong>{fmtR(item.avgCost)}</strong>
      </div>
      {isLoading ? <Spinner /> : movements.length === 0 ? (
        <EmptyState icon={RotateCcw} title="No movements" sub="Stock movements appear when goods are received or adjustments are made" />
      ) : (
        <div style={{ maxHeight: 380, overflowY: "auto", border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr style={{ background: "#F8FAFC" }}>
              {["Date", "Type", "Qty", "Cost / Unit", "Reference", "By"].map(h => <th key={h} style={{ ...TH, fontSize: 10 }}>{h}</th>)}
            </tr></thead>
            <tbody>
              {movements.map((m, i) => (
                <tr key={m.id} style={{ borderTop: "1px solid #F1F5F9", background: i % 2 === 0 ? "#fff" : "#FAFAFA" }}>
                  <td style={{ ...TD, fontSize: 12 }}>{fmtDate(m.movedAt)}</td>
                  <td style={{ ...TD, fontSize: 12 }}><Badge status={m.movementType} /></td>
                  <td style={{ ...TD, fontSize: 13, fontWeight: 700, color: m.qty > 0 ? "#059669" : "#DC2626" }}>{m.qty > 0 ? "+" : ""}{m.qty}</td>
                  <td style={{ ...TD, fontSize: 12 }}>{fmtR(m.costPerUnit)}</td>
                  <td style={{ ...TD, fontSize: 12, color: "#64748B" }}>{m.reference || "—"}</td>
                  <td style={{ ...TD, fontSize: 12, color: "#64748B" }}>{m.movedByName || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 16 }}>
        <button onClick={onClose} style={{ padding: "8px 16px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 13, cursor: "pointer" }}>Close</button>
      </div>
    </Modal>
  )
}
