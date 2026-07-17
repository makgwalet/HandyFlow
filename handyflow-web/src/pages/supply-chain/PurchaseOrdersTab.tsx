// src/pages/supply-chain/PurchaseOrdersTab.tsx
import React, { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, ChevronLeft, Send, CheckCircle, XCircle, Package, AlertTriangle, Download } from "lucide-react"
import { Modal, ErrBox, ModalFooter, Field } from "./scm.shared"

interface Supplier { id: string; name: string; paymentTermsDays: number }
interface Location { id: string; name: string; locationType: string }
interface PoLine { id: string; itemName: string; supplierSku: string | null; qtyOrdered: number; qtyReceived: number; unitCost: number; vatRate: number; lineTotal: number; lineTotalIncl: number; isFullyReceived: boolean }
interface PO {
  id: string; orderNumber: string; supplierName: string; supplierId: string; status: string
  totalAmount: number; subtotal: number; vatAmount: number; orderDate: string
  requiredByDate: string | null; projectRef: string | null; notes: string | null
  createdByName: string | null; approvedByName: string | null; rejectionReason: string | null
}

const ACCENT = "#D97706"
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 9, fontSize: 14, boxSizing: "border-box", outline: "none", background: "#fff" }
const fmtR = (n: number) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const fmtD = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"

const STATUS_CFG: Record<string, { bg: string; color: string; label: string }> = {
  DRAFT:              { bg: "#F1F5F9", color: "#475569",  label: "Draft" },
  PENDING_APPROVAL:   { bg: "#FEF3C7", color: "#92400E",  label: "Pending Approval" },
  APPROVED:           { bg: "#DBEAFE", color: "#1D4ED8",  label: "Approved" },
  SENT:               { bg: "#EDE9FE", color: "#7C3AED",  label: "Sent to Supplier" },
  ACKNOWLEDGED:       { bg: "#D1FAE5", color: "#065F46",  label: "Acknowledged" },
  PARTIALLY_RECEIVED: { bg: "#FEF9C3", color: "#713F12",  label: "Partially Received" },
  FULLY_RECEIVED:     { bg: "#DCFCE7", color: "#166534",  label: "Fully Received" },
  INVOICED:           { bg: "#DBEAFE", color: "#1E40AF",  label: "Invoiced" },
  CANCELLED:          { bg: "#FEE2E2", color: "#DC2626",  label: "Cancelled" },
}

const STATUS_FILTERS = ["", "DRAFT", "PENDING_APPROVAL", "APPROVED", "SENT", "PARTIALLY_RECEIVED", "FULLY_RECEIVED"]

export function PurchaseOrdersTab() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState("")
  const [detail, setDetail]   = useState<PO | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [showAddLine, setShowAddLine] = useState(false)
  const [showReject, setShowReject]  = useState(false)
  const [rejectReason, setRejectReason] = useState("")
  const [err, setErr]         = useState("")

  const initPO = () => ({ supplierId: "", deliverToLocation: "", requiredByDate: "", projectRef: "", notes: "", internalNotes: "" })
  const initLine = () => ({ itemName: "", supplierSku: "", qtyOrdered: "", unitCost: "", vatRate: "15" })
  const [poForm, setPoForm]   = useState(initPO())
  const [lineForm, setLineForm] = useState(initLine())
  const spf = (k: string, v: string) => setPoForm(p => ({ ...p, [k]: v }))
  const slf = (k: string, v: string) => setLineForm(p => ({ ...p, [k]: v }))

  const { data: pos = [], isLoading } = useQuery<PO[]>({
    queryKey: ["scm-pos", statusFilter],
    queryFn: async () => {
      const url = statusFilter ? `/api/v1/supply-chain/purchase-orders?status=${statusFilter}&size=50` : "/api/v1/supply-chain/purchase-orders?size=50"
      const r = await apiClient.get(url); const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : d?.content ?? []
    }, staleTime: 30_000,
  })

  const { data: lines = [] } = useQuery<PoLine[]>({
    queryKey: ["scm-po-lines", detail?.id],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/supply-chain/purchase-orders/${detail!.id}/lines`); const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : [] },
    enabled: !!detail,
    staleTime: 30_000,
  })

  const { data: suppliers = [] } = useQuery<Supplier[]>({
    queryKey: ["scm-suppliers-list"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/supply-chain/suppliers?size=200"); const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : d?.content ?? [] },
    staleTime: 120_000,
  })

  const { data: locations = [] } = useQuery<Location[]>({
    queryKey: ["scm-locations"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/supply-chain/locations"); const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : [] },
    staleTime: 120_000,
  })

  const invalidate = () => { qc.invalidateQueries({ queryKey: ["scm-pos"] }); qc.invalidateQueries({ queryKey: ["scm-summary"] }) }
  const invalidateLines = () => qc.invalidateQueries({ queryKey: ["scm-po-lines", detail?.id] })

  const createMut  = useMutation({ mutationFn: (b: any) => apiClient.post("/api/v1/supply-chain/purchase-orders", b), onSuccess: (r) => { invalidate(); setShowCreate(false); setPoForm(initPO()); setErr(""); const po = r.data?.data ?? r.data; if (po) setDetail(po) }, onError: (e: any) => setErr(e.response?.data?.message || "Failed to create PO") })
  const addLineMut = useMutation({ mutationFn: (b: any) => apiClient.post(`/api/v1/supply-chain/purchase-orders/${detail!.id}/lines`, b), onSuccess: (r) => { invalidateLines(); invalidate(); setShowAddLine(false); setLineForm(initLine()); setErr(""); const po = r.data?.data ?? r.data; if (po) setDetail(po) }, onError: (e: any) => setErr(e.response?.data?.message || "Failed to add line") })
  const actionMut  = useMutation({ mutationFn: ({ action, body }: { action: string; body?: any }) => apiClient.post(`/api/v1/supply-chain/purchase-orders/${detail!.id}/${action}`, body), onSuccess: (r) => { invalidate(); const po = r.data?.data ?? r.data; if (po?.id) setDetail(po); setShowReject(false); setRejectReason("") }, onError: (e: any) => setErr(e.response?.data?.message || "Action failed") })

  // NEW (gap analysis): "the single most common missing artifact" for a
  // procurement system. Blob download rather than a plain <a href> —
  // this endpoint requires the Bearer auth header apiClient already
  // attaches, which a plain anchor link can't carry.
  const [downloadingPdf, setDownloadingPdf] = useState(false)
  const downloadPdf = async (po: PO) => {
    setDownloadingPdf(true)
    try {
      const res = await apiClient.get(`/api/v1/supply-chain/purchase-orders/${po.id}/pdf`, { responseType: "blob" })
      const blob = new Blob([res.data], { type: "application/pdf" })
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url
      a.download = `${po.orderNumber}.pdf`
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch (e: any) {
      setErr(e.response?.data?.message || "Failed to download PDF")
    } finally {
      setDownloadingPdf(false)
    }
  }

  if (detail) {
    const st = STATUS_CFG[detail.status] ?? STATUS_CFG.DRAFT
    return (
      <div>
        <button onClick={() => setDetail(null)} style={{ display: "flex", alignItems: "center", gap: 4, background: "none", border: "none", cursor: "pointer", color: "#64748B", fontSize: 13, marginBottom: 16, padding: 0 }}>
          <ChevronLeft size={15} /> All Purchase Orders
        </button>

        {/* PO Header */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 6 }}>
              <span style={{ fontSize: 11, fontWeight: 700, color: ACCENT }}>{detail.orderNumber}</span>
              <span style={{ background: st.bg, color: st.color, fontSize: 11, fontWeight: 700, padding: "2px 9px", borderRadius: 20 }}>{st.label}</span>
            </div>
            <div style={{ fontSize: 18, fontWeight: 800, color: "#0F172A", marginBottom: 4 }}>{detail.supplierName}</div>
            <div style={{ fontSize: 13, color: "#64748B" }}>
              Order date: {fmtD(detail.orderDate)}
              {detail.requiredByDate && ` · Required by: ${fmtD(detail.requiredByDate)}`}
              {detail.projectRef && ` · Ref: ${detail.projectRef}`}
            </div>
            {detail.rejectionReason && (
              <div style={{ marginTop: 8, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 12, color: "#DC2626" }}>
                ✕ Returned: {detail.rejectionReason}
              </div>
            )}
          </div>
          {/* Action buttons */}
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", justifyContent: "flex-end" }}>
            {/* NEW (gap analysis): available regardless of status, unlike
                the lifecycle-transition buttons below — this is a
                reference/download action, not a state change. */}
            <ActionBtn onClick={() => downloadPdf(detail)} color="#374151" bg="#F8FAFC" border="#E2E8F0" icon={Download}>
              {downloadingPdf ? "Downloading…" : "Download PDF"}
            </ActionBtn>
            {detail.status === "DRAFT" && (
              <>
                <ActionBtn onClick={() => { setShowAddLine(true); setErr("") }} color={ACCENT} bg="#FEF3C7" border="#FCD34D" icon={Plus}>Add Line</ActionBtn>
                <ActionBtn onClick={() => { setErr(""); actionMut.mutate({ action: "submit" }) }} color="#1D4ED8" bg="#DBEAFE" border="#93C5FD" icon={Send}>Submit for Approval</ActionBtn>
              </>
            )}
            {detail.status === "PENDING_APPROVAL" && (
              <>
                <ActionBtn onClick={() => { setErr(""); actionMut.mutate({ action: "approve" }) }} color="#166534" bg="#DCFCE7" border="#86EFAC" icon={CheckCircle}>Approve</ActionBtn>
                <ActionBtn onClick={() => { setShowReject(true); setErr("") }} color="#DC2626" bg="#FEE2E2" border="#FECACA" icon={XCircle}>Reject</ActionBtn>
              </>
            )}
            {detail.status === "APPROVED" && (
              <ActionBtn onClick={() => { setErr(""); actionMut.mutate({ action: "send" }) }} color="#7C3AED" bg="#EDE9FE" border="#C4B5FD" icon={Send}>Mark as Sent</ActionBtn>
            )}
          </div>
        </div>

        {/* NEW: previously nothing rendered here at all — Submit for
            Approval / Approve / Mark as Sent all mutate directly from
            this view (it's a full inline detail view, not a modal, so
            it fell outside the Modal/ErrBox consolidation pass, which
            only touched the actual popup modals below). A failed
            action set the err state but had nothing to display it —
            confirmed via a real "Cannot submit a PO with no lines"
            response that produced no visible warning at all. Placed
            outside the header's flex row on purpose — that row is
            justifyContent: space-between with two children already;
            adding this as a third child there would squeeze it in
            sideways instead of showing as a proper full-width block. */}
        {err && <ErrBox msg={err} />}

        {/* Lines table */}
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden", marginBottom: 16 }}>
          <div style={{ background: "#F8FAFC", padding: "10px 16px", fontSize: 12, fontWeight: 700, color: "#475569" }}>Line Items</div>
          {lines.length === 0
            ? <div style={{ padding: "24px 16px", textAlign: "center", color: "#94A3B8", fontSize: 13 }}>
                No lines yet — {detail.status === "DRAFT" ? "add items using the button above" : "lines will appear once added"}
              </div>
            : <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr style={{ background: "#F8FAFC", borderTop: "1px solid #E2E8F0" }}>
                  {["Item", "SKU", "Qty Ordered", "Qty Received", "Unit Cost", "Subtotal", "VAT", "Total Incl.", ""].map(h => (
                    <th key={h} style={{ padding: "8px 12px", textAlign: "left", fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.04em" }}>{h}</th>
                  ))}
                </tr></thead>
                <tbody>
                  {lines.map((l, i) => (
                    <tr key={l.id} style={{ borderTop: "1px solid #F1F5F9", background: i % 2 === 0 ? "#fff" : "#FAFAFA" }}>
                      <td style={{ padding: "10px 12px", fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{l.itemName}</td>
                      <td style={{ padding: "10px 12px", fontSize: 12, color: "#64748B" }}>{l.supplierSku ?? "—"}</td>
                      <td style={{ padding: "10px 12px", fontSize: 13 }}>{l.qtyOrdered.toFixed(2)}</td>
                      <td style={{ padding: "10px 12px", fontSize: 13, color: l.isFullyReceived ? "#059669" : l.qtyReceived > 0 ? ACCENT : "#94A3B8" }}>{l.qtyReceived.toFixed(2)}{l.isFullyReceived && " ✓"}</td>
                      <td style={{ padding: "10px 12px", fontSize: 13 }}>{fmtR(l.unitCost)}</td>
                      <td style={{ padding: "10px 12px", fontSize: 13 }}>{fmtR(l.lineTotal)}</td>
                      <td style={{ padding: "10px 12px", fontSize: 12, color: "#64748B" }}>{l.vatRate}%</td>
                      <td style={{ padding: "10px 12px", fontSize: 13, fontWeight: 600 }}>{fmtR(l.lineTotalIncl)}</td>
                      <td />
                    </tr>
                  ))}
                </tbody>
              </table>
          }
        </div>

        {/* Totals */}
        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "16px 20px", minWidth: 260 }}>
            {[["Subtotal (excl. VAT)", fmtR(detail.subtotal)], ["VAT", fmtR(detail.vatAmount)]].map(([k, v]) => (
              <div key={k} style={{ display: "flex", justifyContent: "space-between", fontSize: 13, color: "#64748B", marginBottom: 8 }}>
                <span>{k}</span><span>{v}</span>
              </div>
            ))}
            <div style={{ borderTop: "1px solid #E2E8F0", paddingTop: 8, display: "flex", justifyContent: "space-between", fontSize: 15, fontWeight: 800, color: "#0F172A" }}>
              <span>Total (incl. VAT)</span><span style={{ color: ACCENT }}>{fmtR(detail.totalAmount)}</span>
            </div>
          </div>
        </div>

        {/* Reject modal */}
        {showReject && (
          <Modal title="Return PO for Revision" onClose={() => setShowReject(false)}>
            <p style={{ fontSize: 13, color: "#64748B", marginTop: 0 }}>Provide a reason so the buyer knows what to fix.</p>
            <textarea value={rejectReason} onChange={e => setRejectReason(e.target.value)}
              placeholder="e.g. Unit costs don't match the agreed quotation." autoFocus
              style={{ ...inp, minHeight: 80, resize: "vertical" }} />
            <ModalFooter onCancel={() => setShowReject(false)} onConfirm={() => actionMut.mutate({ action: "reject", body: { reason: rejectReason } })} label="Return PO" accent="#DC2626" />
          </Modal>
        )}

        {/* Add line modal */}
        {showAddLine && (
          <Modal title="Add Line Item" onClose={() => { setShowAddLine(false); setErr("") }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <Field label="Item Name *" span={2}><input value={lineForm.itemName} onChange={e => slf("itemName", e.target.value)} placeholder="Concrete blocks 190mm" style={inp} autoFocus /></Field>
              <Field label="Supplier SKU"><input value={lineForm.supplierSku} onChange={e => slf("supplierSku", e.target.value)} placeholder="CB-190" style={inp} /></Field>
              <Field label="VAT Rate (%)"><input type="number" value={lineForm.vatRate} onChange={e => slf("vatRate", e.target.value)} style={inp} /></Field>
              <Field label="Qty Ordered *"><input type="number" value={lineForm.qtyOrdered} onChange={e => slf("qtyOrdered", e.target.value)} style={inp} /></Field>
              <Field label="Unit Cost (R) *"><input type="number" value={lineForm.unitCost} onChange={e => slf("unitCost", e.target.value)} style={inp} /></Field>
            </div>
            {err && <ErrBox msg={err} />}
            <ModalFooter onCancel={() => { setShowAddLine(false); setErr("") }} onConfirm={() => {
              if (!lineForm.itemName.trim() || !lineForm.qtyOrdered || !lineForm.unitCost) { setErr("Item name, quantity and unit cost are required"); return }
              addLineMut.mutate({ itemName: lineForm.itemName.trim(), supplierSku: lineForm.supplierSku || null, qtyOrdered: parseFloat(lineForm.qtyOrdered), unitCost: parseFloat(lineForm.unitCost), vatRate: parseFloat(lineForm.vatRate) || 15 })
            }} label={addLineMut.isPending ? "Adding…" : "Add Line"} loading={addLineMut.isPending} accent={ACCENT} />
          </Modal>
        )}
      </div>
    )
  }

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 10, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {STATUS_FILTERS.map(s => (
            <button key={s} onClick={() => setStatusFilter(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", fontWeight: statusFilter === s ? 700 : 400, border: statusFilter === s ? `1.5px solid ${ACCENT}` : "1px solid #E2E8F0", background: statusFilter === s ? "#FEF3C7" : "#fff", color: statusFilter === s ? ACCENT : "#64748B" }}>
              {s ? STATUS_CFG[s]?.label : "All"}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowCreate(true); setErr("") }}
          style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 14px", background: ACCENT, color: "#fff", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> New Order
        </button>
      </div>

      {/* PO table */}
      {isLoading
        ? <div style={{ padding: 40, textAlign: "center", color: "#94A3B8" }}>Loading…</div>
        : pos.length === 0
          ? <div style={{ textAlign: "center", padding: "50px 0", color: "#94A3B8" }}><Package size={36} style={{ opacity: .3, marginBottom: 10 }} /><div style={{ fontWeight: 600, color: "#475569" }}>No purchase orders</div></div>
          : <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr style={{ background: "#F8FAFC" }}>
                  {["PO #", "Supplier", "Amount", "Order Date", "Required By", "Status", ""].map(h => (
                    <th key={h} style={{ padding: "10px 14px", textAlign: "left", fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.05em" }}>{h}</th>
                  ))}
                </tr></thead>
                <tbody>
                  {pos.map((po, i) => {
                    const st = STATUS_CFG[po.status] ?? STATUS_CFG.DRAFT
                    return (
                      <tr key={po.id} onClick={() => setDetail(po)} style={{ borderTop: "1px solid #F1F5F9", background: i % 2 === 0 ? "#fff" : "#FAFAFA", cursor: "pointer" }}
                        onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = "#F0F7FF"}
                        onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = i % 2 === 0 ? "#fff" : "#FAFAFA"}>
                        <td style={{ padding: "11px 14px", fontSize: 12, fontWeight: 700, color: ACCENT }}>{po.orderNumber}</td>
                        <td style={{ padding: "11px 14px", fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{po.supplierName}</td>
                        <td style={{ padding: "11px 14px", fontSize: 13, fontWeight: 600 }}>{fmtR(po.totalAmount)}</td>
                        <td style={{ padding: "11px 14px", fontSize: 12, color: "#64748B" }}>{fmtD(po.orderDate)}</td>
                        <td style={{ padding: "11px 14px", fontSize: 12, color: "#64748B" }}>{fmtD(po.requiredByDate)}</td>
                        <td style={{ padding: "11px 14px" }}><span style={{ background: st.bg, color: st.color, fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20 }}>{st.label}</span></td>
                        <td style={{ padding: "11px 14px", fontSize: 12, color: "#1D4ED8", fontWeight: 600 }}>Open →</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
      }

      {/* Create PO Modal */}
      {showCreate && (
        <Modal title="New Purchase Order" onClose={() => { setShowCreate(false); setErr("") }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Supplier *" span={2}>
              <select value={poForm.supplierId} onChange={e => spf("supplierId", e.target.value)} style={inp}>
                <option value="">Select supplier…</option>
                {suppliers.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </Field>
            <Field label="Deliver To">
              <select value={poForm.deliverToLocation} onChange={e => spf("deliverToLocation", e.target.value)} style={inp}>
                <option value="">Select location…</option>
                {locations.map(l => <option key={l.id} value={l.id}>{l.name} ({l.locationType})</option>)}
              </select>
            </Field>
            <Field label="Required By Date"><input type="date" value={poForm.requiredByDate} onChange={e => spf("requiredByDate", e.target.value)} style={inp} /></Field>
            <Field label="Project Reference"><input value={poForm.projectRef} onChange={e => spf("projectRef", e.target.value)} placeholder="PRJ-001" style={inp} /></Field>
            <Field label="Notes"><input value={poForm.notes} onChange={e => spf("notes", e.target.value)} style={inp} /></Field>
            <Field label="Internal Notes" span={2}><textarea value={poForm.internalNotes} onChange={e => spf("internalNotes", e.target.value)} style={{ ...inp, minHeight: 48, resize: "vertical" }} /></Field>
          </div>
          <div style={{ marginTop: 12, padding: "10px 12px", background: "#FFFBEB", borderRadius: 8, fontSize: 12, color: "#92400E" }}>
            ℹ After creating the PO you can add line items before submitting for approval.
          </div>
          {err && <ErrBox msg={err} />}
          <ModalFooter onCancel={() => { setShowCreate(false); setErr("") }} onConfirm={() => {
            if (!poForm.supplierId) { setErr("Please select a supplier"); return }
            createMut.mutate({ supplierId: poForm.supplierId, deliverToLocation: poForm.deliverToLocation || null, requiredByDate: poForm.requiredByDate || null, projectRef: poForm.projectRef || null, notes: poForm.notes || null, internalNotes: poForm.internalNotes || null })
          }} label={createMut.isPending ? "Creating…" : "Create PO"} loading={createMut.isPending} accent={ACCENT} />
        </Modal>
      )}
    </div>
  )
}

function ActionBtn({ onClick, color, bg, border, icon: Icon, children }: any) {
  return <button onClick={onClick} style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: bg, color, border: `1px solid ${border}`, borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: "pointer" }}><Icon size={13} />{children}</button>
}
