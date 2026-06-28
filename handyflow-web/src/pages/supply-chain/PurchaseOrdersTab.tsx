// src/pages/supply-chain/PurchaseOrdersTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, ChevronDown, ChevronRight, Search } from "lucide-react"
import { apiClient } from "../../api/client"
import { unwrap, fmtR, fmtDate, inp, TH, TD, Badge, Modal, ModalFooter, Field, ErrBox, Spinner, EmptyState, ActionChip, filterPill, type PurchaseOrder, type PoLine, type Supplier, type StockLocation, type CatalogueItem } from "./scm.shared"

export function PurchaseOrdersTab() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState("")
  const [showCreate, setShowCreate]     = useState(false)
  const [expandedId, setExpandedId]     = useState<string | null>(null)
  const [addLinesFor, setAddLinesFor]   = useState<string | null>(null)
  const [grFor, setGrFor]               = useState<PurchaseOrder | null>(null)
  const [err, setErr] = useState("")

  const blank = () => ({ supplierId: "", deliverToLocation: "", requiredByDate: "", currency: "ZAR", projectRef: "", notes: "" })
  const [form, setForm] = useState(blank())
  const sf = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: suppliers = [] } = useQuery<Supplier[]>({ queryKey: ["scm-suppliers-active"], queryFn: async () => { const r = await apiClient.get("/api/v1/supply-chain/suppliers?status=ACTIVE&size=200"); return unwrap<Supplier>(r) }, staleTime: 60_000 })
  const { data: locations = [] } = useQuery<StockLocation[]>({ queryKey: ["scm-locations"],       queryFn: async () => { const r = await apiClient.get("/api/v1/supply-chain/locations"); return unwrap<StockLocation>(r) },           staleTime: 60_000 })
  const { data: orders = [], isLoading } = useQuery<PurchaseOrder[]>({
    queryKey: ["scm-pos", statusFilter],
    queryFn: async () => { const url = statusFilter ? `/api/v1/supply-chain/purchase-orders?status=${statusFilter}&size=50` : "/api/v1/supply-chain/purchase-orders?size=50"; const r = await apiClient.get(url); return unwrap<PurchaseOrder>(r) },
    staleTime: 30_000,
  })
  const { data: lines = [] } = useQuery<PoLine[]>({
    queryKey: ["scm-po-lines", expandedId],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/supply-chain/purchase-orders/${expandedId}/lines`); return unwrap<PoLine>(r) },
    enabled: !!expandedId,
    staleTime: 15_000,
  })

  const createMut = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/supply-chain/purchase-orders", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["scm-pos"] }); qc.invalidateQueries({ queryKey: ["scm-summary"] }); setShowCreate(false); setForm(blank()); setErr("") },
    onError: (e: any) => setErr(e.response?.data?.message || "Failed to create PO"),
  })
  const actionMut = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) => apiClient.post(`/api/v1/supply-chain/purchase-orders/${id}/${action}`),
    onSuccess: (_, { id }) => { qc.invalidateQueries({ queryKey: ["scm-pos"] }); qc.invalidateQueries({ queryKey: ["scm-po-lines", id] }); qc.invalidateQueries({ queryKey: ["scm-summary"] }) },
  })

  const STATUSES = ["", "DRAFT", "PENDING_APPROVAL", "APPROVED", "SENT", "PARTIALLY_RECEIVED", "FULLY_RECEIVED"]

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 10, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {STATUSES.map(s => <button key={s} onClick={() => setStatusFilter(s)} style={filterPill(statusFilter === s)}>{s ? s.replace(/_/g, " ") : "All"}</button>)}
        </div>
        <button onClick={() => { setShowCreate(true); setErr("") }} style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 14px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> New PO
        </button>
      </div>

      {isLoading ? <Spinner /> : orders.length === 0 ? (
        <EmptyState icon={Plus} title="No purchase orders" sub="Create a purchase order to begin procurement" />
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr style={{ background: "#F8FAFC" }}>
              {["", "PO Number", "Supplier", "Project", "Status", "Total (excl)", "Required By", "Actions"].map(h => <th key={h} style={TH}>{h}</th>)}
            </tr></thead>
            <tbody>
              {orders.map((po, i) => (
                <>
                  <tr key={po.id} style={{ background: i % 2 === 0 ? "#fff" : "#FAFAFA", borderTop: "1px solid #F1F5F9" }}>
                    <td style={{ ...TD, width: 36, padding: "11px 6px 11px 14px" }}>
                      <button onClick={() => setExpandedId(expandedId === po.id ? null : po.id)}
                        style={{ background: "none", border: "none", cursor: "pointer", color: "#64748B", display: "flex" }}>
                        {expandedId === po.id ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                      </button>
                    </td>
                    <td style={TD}><span style={{ fontWeight: 700, color: "#1B3A6B" }}>{po.orderNumber}</span></td>
                    <td style={TD}>{po.supplierName}</td>
                    <td style={{ ...TD, color: "#94A3B8" }}>{po.projectRef || "—"}</td>
                    <td style={TD}><Badge status={po.status} /></td>
                    <td style={TD}><strong>{fmtR(po.totalAmount)}</strong></td>
                    <td style={{ ...TD, color: "#64748B" }}>{fmtDate(po.requiredByDate)}</td>
                    <td style={TD}>
                      <div style={{ display: "flex", gap: 5, flexWrap: "wrap" }}>
                        {po.status === "DRAFT"            && <ActionChip label="Submit"    color="#92400E" bg="#FEF3C7" border="#FCD34D" onClick={() => actionMut.mutate({ id: po.id, action: "submit" })} />}
                        {po.status === "PENDING_APPROVAL" && <ActionChip label="Approve"   color="#166534" bg="#DCFCE7" border="#86EFAC" onClick={() => actionMut.mutate({ id: po.id, action: "approve" })} />}
                        {po.status === "APPROVED"         && <ActionChip label="Mark Sent" color="#1D4ED8" bg="#DBEAFE" border="#BFDBFE" onClick={() => actionMut.mutate({ id: po.id, action: "send" })} />}
                        {po.status === "SENT"             && <ActionChip label="Receive"   color="#7C3AED" bg="#EDE9FE" border="#DDD6FE" onClick={() => setGrFor(po)} />}
                        {po.status === "DRAFT"            && <ActionChip label="Add Lines" color="#0D9488" bg="#F0FDFA" border="#99F6E4" onClick={() => setAddLinesFor(po.id)} />}
                      </div>
                    </td>
                  </tr>
                  {expandedId === po.id && (
                    <tr key={`${po.id}-lines`} style={{ background: "#F8FAFC", borderTop: "1px solid #F1F5F9" }}>
                      <td colSpan={8} style={{ padding: "12px 16px 16px 50px" }}>
                        <div style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: 8 }}>Line Items</div>
                        {lines.length === 0 ? (
                          <div style={{ fontSize: 13, color: "#94A3B8" }}>
                            No lines yet.{po.status === "DRAFT" && <button onClick={() => setAddLinesFor(po.id)} style={{ marginLeft: 8, background: "none", border: "none", cursor: "pointer", color: "#D97706", fontWeight: 600, fontSize: 13 }}>Add line items →</button>}
                          </div>
                        ) : (
                          <table style={{ width: "100%", borderCollapse: "collapse", border: "1px solid #E2E8F0", borderRadius: 8, overflow: "hidden" }}>
                            <thead><tr style={{ background: "#F1F5F9" }}>
                              {["Item", "SKU", "Qty", "Unit Cost", "VAT", "Line Total"].map(h => <th key={h} style={{ ...TH, fontSize: 10 }}>{h}</th>)}
                            </tr></thead>
                            <tbody>
                              {lines.map(l => (
                                <tr key={l.id} style={{ borderTop: "1px solid #F1F5F9" }}>
                                  <td style={{ ...TD, fontSize: 12, fontWeight: 600 }}>{l.itemName}</td>
                                  <td style={{ ...TD, fontSize: 12, color: "#94A3B8" }}>{l.supplierSku || "—"}</td>
                                  <td style={{ ...TD, fontSize: 12 }}>{l.qtyOrdered}</td>
                                  <td style={{ ...TD, fontSize: 12 }}>{fmtR(l.unitCost)}</td>
                                  <td style={{ ...TD, fontSize: 12, color: "#64748B" }}>{l.vatRate}%</td>
                                  <td style={{ ...TD, fontSize: 12, fontWeight: 700 }}>{fmtR(l.lineTotal)}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        )}
                      </td>
                    </tr>
                  )}
                </>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create PO */}
      {showCreate && (
        <Modal title="New Purchase Order" onClose={() => setShowCreate(false)}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Supplier *" span={2}>
              <select value={form.supplierId} onChange={e => sf("supplierId", e.target.value)} style={inp}>
                <option value="">Select supplier…</option>
                {suppliers.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </Field>
            <Field label="Deliver To">
              <select value={form.deliverToLocation} onChange={e => sf("deliverToLocation", e.target.value)} style={inp}>
                <option value="">Select location…</option>
                {locations.map(l => <option key={l.id} value={l.id}>{l.name}</option>)}
              </select>
            </Field>
            <Field label="Required By"><input type="date" value={form.requiredByDate} onChange={e => sf("requiredByDate", e.target.value)} style={inp} /></Field>
            <Field label="Currency">
              <select value={form.currency} onChange={e => sf("currency", e.target.value)} style={inp}>
                {["ZAR", "USD", "EUR", "GBP"].map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </Field>
            <Field label="Project Ref"><input value={form.projectRef} onChange={e => sf("projectRef", e.target.value)} placeholder="e.g. SITE-A-2026" style={inp} /></Field>
            <Field label="Notes" span={2}><textarea value={form.notes} onChange={e => sf("notes", e.target.value)} style={{ ...inp, minHeight: 56, resize: "vertical" }} placeholder="Instructions for supplier…" /></Field>
          </div>
          {err && <ErrBox msg={err} />}
          <ModalFooter onCancel={() => setShowCreate(false)} loading={createMut.isPending}
            label={createMut.isPending ? "Creating…" : "Create PO"}
            onConfirm={() => { if (!form.supplierId) { setErr("Select a supplier"); return } createMut.mutate({ ...form, deliverToLocation: form.deliverToLocation || null, requiredByDate: form.requiredByDate || null, projectRef: form.projectRef || null, notes: form.notes || null }) }} />
        </Modal>
      )}

      {addLinesFor && <AddLinesModal poId={addLinesFor} onClose={() => { setAddLinesFor(null); qc.invalidateQueries({ queryKey: ["scm-po-lines", addLinesFor] }); qc.invalidateQueries({ queryKey: ["scm-pos"] }) }} />}
      {grFor && <GoodsReceiptModal po={grFor} locations={locations} onClose={() => { setGrFor(null); qc.invalidateQueries({ queryKey: ["scm-pos"] }); qc.invalidateQueries({ queryKey: ["scm-summary"] }) }} />}
    </div>
  )
}

// ── Add Line Items ─────────────────────────────────────────────────────────────
function AddLinesModal({ poId, onClose }: { poId: string; onClose: () => void }) {
  const [rows, setRows] = useState([{ description: "", qty: "1", unitPrice: "", catId: "", catName: "" }])
  const [err, setErr] = useState("")
  const [searches, setSearches] = useState<string[]>([""])
  const [results, setResults] = useState<CatalogueItem[][]>([[]])

  const addRow = () => { setRows(r => [...r, { description: "", qty: "1", unitPrice: "", catId: "", catName: "" }]); setSearches(s => [...s, ""]); setResults(r => [...r, []]) }
  const upd = (i: number, k: string, v: string) => setRows(r => r.map((x, j) => j === i ? { ...x, [k]: v } : x))

  const searchCat = async (i: number, q: string) => {
    setSearches(s => s.map((x, j) => j === i ? q : x))
    if (q.length < 2) { setResults(r => r.map((x, j) => j === i ? [] : x)); return }
    try { const r = await apiClient.get(`/api/v1/catalogue/items?search=${encodeURIComponent(q)}&size=8`); setResults(res => res.map((x, j) => j === i ? unwrap<CatalogueItem>(r) : x)) } catch {}
  }

  const pickCat = (i: number, item: CatalogueItem) => {
    upd(i, "description", item.name); upd(i, "catId", item.id); upd(i, "catName", item.name)
    if (item.unitPrice) upd(i, "unitPrice", String(item.unitPrice))
    setSearches(s => s.map((x, j) => j === i ? item.name : x))
    setResults(r => r.map((x, j) => j === i ? [] : x))
  }

  const saveMut = useMutation({
    mutationFn: async () => {
      for (const r of rows.filter(r => r.description.trim())) {
        await apiClient.post(`/api/v1/supply-chain/purchase-orders/${poId}/lines`, { itemName: r.description, qtyOrdered: parseFloat(r.qty) || 1, unitCost: parseFloat(r.unitPrice) || 0, vatRate: null, catalogueItemId: r.catId || null, supplierSku: null })
      }
    },
    onSuccess: onClose,
    onError: (e: any) => setErr(e.response?.data?.message || "Failed to save lines"),
  })

  return (
    <Modal title="Add Line Items" onClose={onClose} wide>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {rows.map((row, i) => (
          <div key={i} style={{ padding: "12px 14px", background: "#F8FAFC", borderRadius: 10, border: "1px solid #E2E8F0" }}>
            <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr", gap: 10, marginBottom: 6 }}>
              <div style={{ position: "relative" }}>
                <Search size={12} style={{ position: "absolute", left: 9, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
                <input value={searches[i]} onChange={e => searchCat(i, e.target.value)} placeholder="Search catalogue or type description…" style={{ ...inp, paddingLeft: 28, fontSize: 13, padding: "7px 10px 7px 28px" }} />
                {results[i]?.length > 0 && (
                  <div style={{ position: "absolute", top: "100%", left: 0, right: 0, zIndex: 50, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, boxShadow: "0 8px 24px rgba(0,0,0,0.1)", maxHeight: 180, overflowY: "auto" }}>
                    {results[i].map(c => (
                      <div key={c.id} onClick={() => pickCat(i, c)} style={{ padding: "8px 12px", cursor: "pointer", fontSize: 13, borderBottom: "1px solid #F1F5F9" }}
                        onMouseEnter={e => (e.currentTarget.style.background = "#F8FAFC")} onMouseLeave={e => (e.currentTarget.style.background = "#fff")}>
                        <strong>{c.name}</strong> {c.code && <span style={{ color: "#94A3B8", fontSize: 11 }}>({c.code})</span>}
                        {c.unitPrice && <span style={{ float: "right", color: "#D97706", fontSize: 12, fontWeight: 700 }}>{fmtR(c.unitPrice)}</span>}
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <input type="number" value={row.qty} onChange={e => upd(i, "qty", e.target.value)} placeholder="Qty" style={{ ...inp, fontSize: 13, padding: "7px 10px" }} />
              <input type="number" step="0.01" value={row.unitPrice} onChange={e => upd(i, "unitPrice", e.target.value)} placeholder="Unit price" style={{ ...inp, fontSize: 13, padding: "7px 10px" }} />
            </div>
            <div style={{ fontSize: 12, color: "#94A3B8", display: "flex", justifyContent: "space-between" }}>
              <span>Line total: <strong style={{ color: "#0F172A" }}>{fmtR((parseFloat(row.qty) || 0) * (parseFloat(row.unitPrice) || 0))}</strong></span>
              {rows.length > 1 && <button onClick={() => { setRows(r => r.filter((_, j) => j !== i)); setSearches(s => s.filter((_, j) => j !== i)); setResults(r => r.filter((_, j) => j !== i)) }} style={{ background: "none", border: "none", cursor: "pointer", color: "#DC2626", fontSize: 12 }}>Remove</button>}
            </div>
          </div>
        ))}
        <button onClick={addRow} style={{ padding: "8px 14px", border: "1px dashed #E2E8F0", borderRadius: 9, background: "#F8FAFC", color: "#64748B", fontSize: 13, cursor: "pointer" }}>+ Add another line</button>
      </div>
      {err && <ErrBox msg={err} />}
      <ModalFooter onCancel={onClose} onConfirm={() => saveMut.mutate()} label={saveMut.isPending ? "Saving…" : "Save Lines"} loading={saveMut.isPending} />
    </Modal>
  )
}

// ── Goods Receipt ──────────────────────────────────────────────────────────────
function GoodsReceiptModal({ po, locations, onClose }: { po: PurchaseOrder; locations: StockLocation[]; onClose: () => void }) {
  const qc = useQueryClient()
  const [locationId, setLocationId] = useState(locations.find(l => l.isDefault)?.id || "")
  const [deliveryRef, setDeliveryRef] = useState("")
  const [err, setErr] = useState("")

  const createGR = useMutation({
    mutationFn: () => apiClient.post("/api/v1/supply-chain/goods-receipts", { purchaseOrderId: po.id, receivedToLocation: locationId, deliveryNoteRef: deliveryRef || null }),
    onSuccess: r => { const grId = r.data?.data?.id ?? r.data?.id; if (grId) postGR.mutate(grId); else onClose() },
    onError: (e: any) => setErr(e.response?.data?.message || "Failed to create GR"),
  })
  const postGR = useMutation({
    mutationFn: (grId: string) => apiClient.post(`/api/v1/supply-chain/goods-receipts/${grId}/post`, []),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["scm-inventory"] }); onClose() },
    onError: (e: any) => setErr(e.response?.data?.message || "Failed to post GR"),
  })
  const loading = createGR.isPending || postGR.isPending

  return (
    <Modal title="Receive Goods" onClose={onClose}>
      <div style={{ padding: "10px 14px", background: "#EFF6FF", borderRadius: 10, marginBottom: 16, fontSize: 13, color: "#1D4ED8" }}>
        Receiving against <strong>{po.orderNumber}</strong> — {po.supplierName}
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        <Field label="Deliver To Location *">
          <select value={locationId} onChange={e => setLocationId(e.target.value)} style={inp}>
            <option value="">Select location…</option>
            {locations.map(l => <option key={l.id} value={l.id}>{l.name}</option>)}
          </select>
        </Field>
        <Field label="Delivery Note / Waybill Ref">
          <input value={deliveryRef} onChange={e => setDeliveryRef(e.target.value)} placeholder="DN-2026-4521" style={inp} />
        </Field>
      </div>
      <p style={{ fontSize: 12, color: "#94A3B8", marginTop: 10 }}>All PO line items will be fully received and stock will be updated at the selected location.</p>
      {err && <ErrBox msg={err} />}
      <ModalFooter onCancel={onClose} onConfirm={() => { if (!locationId) { setErr("Select a delivery location"); return } createGR.mutate() }} label={loading ? "Posting…" : "Confirm Receipt"} loading={loading} />
    </Modal>
  )
}
