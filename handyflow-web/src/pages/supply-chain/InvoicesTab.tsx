// src/pages/supply-chain/InvoicesTab.tsx
import React, { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, FileText, CheckCircle, CreditCard, AlertTriangle } from "lucide-react"

interface Supplier { id: string; name: string }
interface PO { id: string; orderNumber: string }
interface Invoice {
  id: string; invoiceNumber: string; supplierInvoiceRef: string | null
  supplierId: string; purchaseOrderId: string | null; goodsReceiptId: string | null
  invoiceDate: string; dueDate: string; receivedDate: string
  subtotal: number; vatAmount: number; totalAmount: number; currency: string
  status: string; matchStatus: string; matchNotes: string | null
  approvedByName: string | null; approvedAt: string | null
  paymentReference: string | null; paidAt: string | null; notes: string | null
}

const ACCENT = "#D97706"
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 9, fontSize: 14, boxSizing: "border-box", outline: "none", background: "#fff" }
const fmtR = (n: number) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const fmtD = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"
const isOverdue = (inv: Invoice) => !["PAID","CANCELLED"].includes(inv.status) && new Date(inv.dueDate) < new Date()

const STATUS_BADGE: Record<string, { bg: string; color: string }> = {
  RECEIVED:    { bg: "#F1F5F9", color: "#475569" },
  UNDER_REVIEW:{ bg: "#FEF3C7", color: "#92400E" },
  APPROVED:    { bg: "#DBEAFE", color: "#1D4ED8" },
  DISPUTED:    { bg: "#FEE2E2", color: "#DC2626" },
  PAID:        { bg: "#DCFCE7", color: "#166534" },
  CANCELLED:   { bg: "#F1F5F9", color: "#9CA3AF" },
}
const MATCH_BADGE: Record<string, { bg: string; color: string; label: string }> = {
  PENDING:      { bg: "#F1F5F9", color: "#94A3B8",  label: "Pending" },
  MATCHED:      { bg: "#DCFCE7", color: "#166534",  label: "✓ Matched" },
  PARTIAL_MATCH:{ bg: "#FEF3C7", color: "#92400E",  label: "Partial" },
  DISPUTE:      { bg: "#FEE2E2", color: "#DC2626",  label: "⚠ Dispute" },
  OVERRIDDEN:   { bg: "#EDE9FE", color: "#7C3AED",  label: "Overridden" },
}

const STATUS_FILTERS = ["", "RECEIVED", "UNDER_REVIEW", "APPROVED", "DISPUTED", "PAID"]

export function InvoicesTab() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState("")
  const [selected, setSelected] = useState<Invoice | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [showMarkPaid, setShowMarkPaid] = useState(false)
  const [paymentRef, setPaymentRef] = useState("")
  const [err, setErr] = useState("")

  const initF = () => ({
    supplierId: "", purchaseOrderId: "", goodsReceiptId: "", supplierInvoiceRef: "",
    invoiceDate: "", dueDate: "", subtotal: "", vatAmount: "", totalAmount: "", notes: ""
  })
  const [form, setForm] = useState(initF())
  const sf = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: invoices = [], isLoading } = useQuery<Invoice[]>({
    queryKey: ["scm-invoices", statusFilter],
    queryFn: async () => {
      const url = statusFilter ? `/api/v1/supply-chain/supplier-invoices?status=${statusFilter}&size=50` : "/api/v1/supply-chain/supplier-invoices?size=50"
      const r = await apiClient.get(url); const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : d?.content ?? []
    }, staleTime: 30_000,
  })

  const { data: suppliers = [] } = useQuery<Supplier[]>({
    queryKey: ["scm-suppliers-list"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/supply-chain/suppliers?size=200"); const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : d?.content ?? [] },
    staleTime: 120_000,
  })

  const { data: openPOs = [] } = useQuery<PO[]>({
    queryKey: ["scm-pos-open"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/supply-chain/purchase-orders?status=APPROVED&size=100")
      const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : d?.content ?? []
    }, staleTime: 60_000,
  })

  const invalidate = () => { qc.invalidateQueries({ queryKey: ["scm-invoices"] }); qc.invalidateQueries({ queryKey: ["scm-summary"] }) }

  const createMut = useMutation({
    mutationFn: (b: any) => apiClient.post("/api/v1/supply-chain/supplier-invoices", b),
    onSuccess: () => { invalidate(); setShowCreate(false); setForm(initF()); setErr("") },
    onError: (e: any) => setErr(e.response?.data?.message || "Failed to record invoice"),
  })
  const approveMut = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/supply-chain/supplier-invoices/${id}/approve`),
    onSuccess: (r) => { invalidate(); const inv = r.data?.data ?? r.data; if (inv?.id) setSelected(inv) },
    onError: (e: any) => setErr(e.response?.data?.message || "Approval failed"),
  })
  const paidMut = useMutation({
    mutationFn: ({ id, ref }: { id: string; ref: string }) => apiClient.post(`/api/v1/supply-chain/supplier-invoices/${id}/pay`, { paymentReference: ref }),
    onSuccess: (r) => { invalidate(); const inv = r.data?.data ?? r.data; if (inv?.id) setSelected(inv); setShowMarkPaid(false); setPaymentRef("") },
    onError: (e: any) => setErr(e.response?.data?.message || "Failed to mark paid"),
  })

  const overdue = invoices.filter(isOverdue).length
  const pending = invoices.filter(i => i.status === "RECEIVED").length
  const disputes = invoices.filter(i => i.matchStatus === "DISPUTE" || i.status === "DISPUTED").length

  return (
    <div>
      {/* Summary pills */}
      {(overdue > 0 || disputes > 0) && (
        <div style={{ display: "flex", gap: 8, marginBottom: 14 }}>
          {overdue > 0 && <div style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 12, fontWeight: 700, color: "#DC2626" }}><AlertTriangle size={12} /> {overdue} overdue</div>}
          {disputes > 0 && <div style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 8, fontSize: 12, fontWeight: 700, color: "#92400E" }}><AlertTriangle size={12} /> {disputes} dispute{disputes !== 1 ? "s" : ""}</div>}
          {pending > 0 && <div style={{ padding: "6px 12px", background: "#F1F5F9", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 12, fontWeight: 700, color: "#475569" }}>{pending} pending review</div>}
        </div>
      )}

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 10, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {STATUS_FILTERS.map(s => (
            <button key={s} onClick={() => setStatusFilter(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", fontWeight: statusFilter === s ? 700 : 400, border: statusFilter === s ? `1.5px solid ${ACCENT}` : "1px solid #E2E8F0", background: statusFilter === s ? "#FEF3C7" : "#fff", color: statusFilter === s ? ACCENT : "#64748B" }}>
              {s || "All"}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowCreate(true); setErr("") }}
          style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 14px", background: ACCENT, color: "#fff", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> Record Invoice
        </button>
      </div>

      {/* Invoice table */}
      {isLoading
        ? <div style={{ padding: 40, textAlign: "center", color: "#94A3B8" }}>Loading…</div>
        : invoices.length === 0
          ? <div style={{ textAlign: "center", padding: "50px 0", color: "#94A3B8" }}><FileText size={36} style={{ opacity: .3, marginBottom: 10 }} /><div style={{ fontWeight: 600, color: "#475569" }}>No invoices found</div></div>
          : <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr style={{ background: "#F8FAFC" }}>
                  {["Invoice #", "Supplier Ref", "Invoice Date", "Due Date", "Amount", "Status", "Match", ""].map(h => (
                    <th key={h} style={{ padding: "10px 14px", textAlign: "left", fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.05em" }}>{h}</th>
                  ))}
                </tr></thead>
                <tbody>
                  {invoices.map((inv, i) => {
                    const sb  = STATUS_BADGE[inv.status] ?? STATUS_BADGE.RECEIVED
                    const mb  = MATCH_BADGE[inv.matchStatus] ?? MATCH_BADGE.PENDING
                    const odd = isOverdue(inv)
                    return (
                      <tr key={inv.id} onClick={() => { setSelected(inv); setErr("") }}
                        style={{ borderTop: "1px solid #F1F5F9", background: odd ? "#FEF2F2" : i % 2 === 0 ? "#fff" : "#FAFAFA", cursor: "pointer" }}
                        onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = "#F0F7FF"}
                        onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = odd ? "#FEF2F2" : i % 2 === 0 ? "#fff" : "#FAFAFA"}
                      >
                        <td style={{ padding: "11px 14px", fontSize: 12, fontWeight: 700, color: ACCENT }}>{inv.invoiceNumber}</td>
                        <td style={{ padding: "11px 14px", fontSize: 12, color: "#64748B" }}>{inv.supplierInvoiceRef ?? "—"}</td>
                        <td style={{ padding: "11px 14px", fontSize: 12, color: "#64748B" }}>{fmtD(inv.invoiceDate)}</td>
                        <td style={{ padding: "11px 14px", fontSize: 12, color: odd ? "#DC2626" : "#64748B", fontWeight: odd ? 700 : 400 }}>{fmtD(inv.dueDate)}{odd && " ⚠"}</td>
                        <td style={{ padding: "11px 14px", fontSize: 13, fontWeight: 700 }}>{fmtR(inv.totalAmount)}</td>
                        <td style={{ padding: "11px 14px" }}><span style={{ background: sb.bg, color: sb.color, fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20 }}>{inv.status}</span></td>
                        <td style={{ padding: "11px 14px" }}><span style={{ background: mb.bg, color: mb.color, fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20 }}>{mb.label}</span></td>
                        <td style={{ padding: "11px 14px", fontSize: 12, color: "#1D4ED8", fontWeight: 600 }}>View →</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
      }

      {/* Invoice detail panel */}
      {selected && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 560, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 16 }}>
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, color: ACCENT, marginBottom: 3 }}>{selected.invoiceNumber}</div>
                <div style={{ fontSize: 16, fontWeight: 700, color: "#0F172A" }}>Supplier Invoice</div>
              </div>
              <button onClick={() => { setSelected(null); setErr("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", fontSize: 20, lineHeight: 1 }}>×</button>
            </div>

            {/* Match status banner */}
            {selected.matchStatus !== "PENDING" && (
              <div style={{ marginBottom: 14, padding: "10px 14px", background: MATCH_BADGE[selected.matchStatus]?.bg ?? "#F1F5F9", borderRadius: 9, fontSize: 13, fontWeight: 600, color: MATCH_BADGE[selected.matchStatus]?.color ?? "#475569" }}>
                3-Way Match: {MATCH_BADGE[selected.matchStatus]?.label}
                {selected.matchNotes && <div style={{ fontSize: 12, fontWeight: 400, marginTop: 3, opacity: .8 }}>{selected.matchNotes}</div>}
              </div>
            )}

            {/* Detail grid */}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 16 }}>
              {[
                ["Supplier Ref",    selected.supplierInvoiceRef ?? "—"],
                ["Invoice Date",    fmtD(selected.invoiceDate)],
                ["Due Date",        fmtD(selected.dueDate)],
                ["Received",        fmtD(selected.receivedDate)],
                ["PO Linked",       selected.purchaseOrderId ? "Yes" : "No"],
                ["GR Linked",       selected.goodsReceiptId ? "Yes" : "No"],
              ].map(([k, v]) => (
                <div key={k}><div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.04em", marginBottom: 3 }}>{k}</div><div style={{ fontSize: 13, fontWeight: 600 }}>{v}</div></div>
              ))}
            </div>

            {/* Amounts */}
            <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "14px 16px", marginBottom: 16 }}>
              {[["Subtotal", fmtR(selected.subtotal)], ["VAT", fmtR(selected.vatAmount)]].map(([k, v]) => (
                <div key={k} style={{ display: "flex", justifyContent: "space-between", fontSize: 13, color: "#64748B", marginBottom: 8 }}><span>{k}</span><span>{v}</span></div>
              ))}
              <div style={{ borderTop: "1px solid #E2E8F0", paddingTop: 8, display: "flex", justifyContent: "space-between", fontSize: 15, fontWeight: 800, color: "#0F172A" }}>
                <span>Total</span><span style={{ color: ACCENT }}>{fmtR(selected.totalAmount)}</span>
              </div>
            </div>

            {/* Payment info */}
            {selected.status === "PAID" && (
              <div style={{ marginBottom: 16, padding: "10px 14px", background: "#DCFCE7", border: "1px solid #86EFAC", borderRadius: 9 }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: "#166534" }}>✓ Paid {fmtD(selected.paidAt)}</div>
                {selected.paymentReference && <div style={{ fontSize: 12, color: "#166534" }}>Ref: {selected.paymentReference}</div>}
              </div>
            )}
            {selected.approvedByName && <div style={{ fontSize: 12, color: "#64748B", marginBottom: 12 }}>Approved by {selected.approvedByName} on {fmtD(selected.approvedAt)}</div>}
            {selected.notes && <div style={{ fontSize: 12, color: "#64748B", marginBottom: 12 }}>Notes: {selected.notes}</div>}

            {err && <div style={{ marginBottom: 12, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13 }}>{err}</div>}

            {/* Actions */}
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
              <button onClick={() => { setSelected(null); setErr("") }} style={{ padding: "9px 16px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 13, cursor: "pointer", color: "#64748B" }}>Close</button>
              {(selected.status === "RECEIVED" || selected.status === "UNDER_REVIEW") && (
                <button onClick={() => approveMut.mutate(selected.id)} disabled={approveMut.isPending}
                  style={{ display: "flex", alignItems: "center", gap: 5, padding: "9px 16px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                  <CheckCircle size={13} /> {approveMut.isPending ? "Approving…" : "Approve"}
                </button>
              )}
              {selected.status === "APPROVED" && (
                <button onClick={() => setShowMarkPaid(true)}
                  style={{ display: "flex", alignItems: "center", gap: 5, padding: "9px 16px", background: "#DBEAFE", color: "#1D4ED8", border: "1px solid #93C5FD", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                  <CreditCard size={13} /> Mark as Paid
                </button>
              )}
            </div>

            {/* Mark paid sub-panel */}
            {showMarkPaid && (
              <div style={{ marginTop: 14, padding: "14px 16px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10 }}>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 6 }}>Payment Reference *</label>
                <input value={paymentRef} onChange={e => setPaymentRef(e.target.value)} placeholder="e.g. EFT-20260703-001 or cheque number" style={inp} autoFocus />
                <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 10 }}>
                  <button onClick={() => { setShowMarkPaid(false); setPaymentRef("") }} style={{ padding: "7px 14px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 12, cursor: "pointer" }}>Cancel</button>
                  <button onClick={() => { if (!paymentRef.trim()) return; paidMut.mutate({ id: selected.id, ref: paymentRef.trim() }) }}
                    disabled={!paymentRef.trim() || paidMut.isPending}
                    style={{ padding: "7px 14px", background: ACCENT, color: "#fff", border: "none", borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: "pointer", opacity: !paymentRef.trim() ? .5 : 1 }}>
                    {paidMut.isPending ? "Saving…" : "Confirm Payment"}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Create Invoice Modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 560, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>Record Supplier Invoice</h3>
              <button onClick={() => { setShowCreate(false); setErr("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", fontSize: 20, lineHeight: 1 }}>×</button>
            </div>
            <div style={{ marginBottom: 14, padding: "10px 12px", background: "#FFFBEB", border: "1px solid #FCD34D", borderRadius: 8, fontSize: 12, color: "#92400E" }}>
              Linking a PO and GR enables 3-way matching — HandyFlow will automatically compare amounts and flag discrepancies.
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div style={{ gridColumn: "span 2" }}>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Supplier *</label>
                <select value={form.supplierId} onChange={e => sf("supplierId", e.target.value)} style={inp}>
                  <option value="">Select supplier…</option>
                  {suppliers.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>
              <div>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Link to PO (optional)</label>
                <select value={form.purchaseOrderId} onChange={e => sf("purchaseOrderId", e.target.value)} style={inp}>
                  <option value="">No PO linked</option>
                  {openPOs.map(p => <option key={p.id} value={p.id}>{p.orderNumber}</option>)}
                </select>
              </div>
              <div>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Supplier's Invoice Ref</label>
                <input value={form.supplierInvoiceRef} onChange={e => sf("supplierInvoiceRef", e.target.value)} placeholder="INV-2026-1234" style={inp} />
              </div>
              <div>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Invoice Date *</label>
                <input type="date" value={form.invoiceDate} onChange={e => sf("invoiceDate", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Due Date *</label>
                <input type="date" value={form.dueDate} onChange={e => sf("dueDate", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Subtotal (R) *</label>
                <input type="number" value={form.subtotal} onChange={e => sf("subtotal", e.target.value)} placeholder="0.00" style={inp} />
              </div>
              <div>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>VAT Amount (R)</label>
                <input type="number" value={form.vatAmount} onChange={e => sf("vatAmount", e.target.value)} placeholder="0.00" style={inp} />
              </div>
              <div>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Total (incl. VAT) *</label>
                <input type="number" value={form.totalAmount} onChange={e => sf("totalAmount", e.target.value)} placeholder="0.00" style={inp} />
              </div>
              <div style={{ gridColumn: "span 2" }}>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Notes</label>
                <textarea value={form.notes} onChange={e => sf("notes", e.target.value)} style={{ ...inp, minHeight: 50, resize: "vertical" }} />
              </div>
            </div>
            {err && <div style={{ marginTop: 10, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13 }}>{err}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => { setShowCreate(false); setErr("") }} style={{ padding: "9px 16px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 13, cursor: "pointer", color: "#64748B" }}>Cancel</button>
              <button onClick={() => {
                if (!form.supplierId || !form.invoiceDate || !form.dueDate || !form.subtotal || !form.totalAmount) { setErr("Supplier, dates, subtotal and total are required"); return }
                createMut.mutate({ supplierId: form.supplierId, purchaseOrderId: form.purchaseOrderId || null, goodsReceiptId: form.goodsReceiptId || null, supplierInvoiceRef: form.supplierInvoiceRef || null, invoiceDate: form.invoiceDate, dueDate: form.dueDate, currency: "ZAR", subtotal: parseFloat(form.subtotal), vatAmount: parseFloat(form.vatAmount) || 0, totalAmount: parseFloat(form.totalAmount), notes: form.notes || null })
              }} disabled={createMut.isPending} style={{ padding: "9px 18px", background: ACCENT, color: "#fff", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer", opacity: createMut.isPending ? .6 : 1 }}>
                {createMut.isPending ? "Recording…" : "Record Invoice"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
