// src/pages/supply-chain/InvoicesTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, CheckCircle, AlertTriangle, AlertCircle, Info } from "lucide-react"
import { apiClient } from "../../api/client"
import { unwrap, fmtR, fmtDate, inp, TH, TD, Badge, Modal, ModalFooter, Field, ErrBox, Spinner, EmptyState, ActionChip, filterPill, type SupplierInvoice, type Supplier } from "./scm.shared"

function matchStyle(ms: string): { Icon: React.ElementType; color: string; label: string } {
  if (ms === "MATCHED")        return { Icon: CheckCircle,   color: "#059669", label: "3-way match" }
  if (ms === "PO_MATCHED")     return { Icon: CheckCircle,   color: "#1D4ED8", label: "PO matched"   }
  if (ms === "PARTIAL_MATCH")  return { Icon: AlertCircle,   color: "#D97706", label: "Partial"      }
  return                              { Icon: AlertTriangle,  color: "#DC2626", label: "No PO"        }
}

export function InvoicesTab() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState("")
  const [showCreate, setShowCreate] = useState(false)
  const [err, setErr] = useState("")
  const blank = () => ({ supplierId: "", supplierInvoiceRef: "", invoiceDate: "", dueDate: "", subtotal: "", vatAmount: "", totalAmount: "", notes: "" })
  const [form, setForm] = useState(blank())
  const sf = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: suppliers = [] } = useQuery<Supplier[]>({ queryKey: ["scm-suppliers-list"], queryFn: async () => { const r = await apiClient.get("/api/v1/supply-chain/suppliers?size=200"); return unwrap<Supplier>(r) }, staleTime: 60_000 })
  const { data: invoices = [], isLoading } = useQuery<SupplierInvoice[]>({
    queryKey: ["scm-invoices", statusFilter],
    queryFn: async () => { const r = await apiClient.get(statusFilter ? `/api/v1/supply-chain/supplier-invoices?status=${statusFilter}&size=50` : "/api/v1/supply-chain/supplier-invoices?size=50"); return unwrap<SupplierInvoice>(r) },
    staleTime: 30_000,
  })

  const createMut  = useMutation({ mutationFn: (body: any) => apiClient.post("/api/v1/supply-chain/supplier-invoices", body), onSuccess: () => { qc.invalidateQueries({ queryKey: ["scm-invoices"] }); qc.invalidateQueries({ queryKey: ["scm-summary"] }); setShowCreate(false); setForm(blank()); setErr("") }, onError: (e: any) => setErr(e.response?.data?.message || "Failed to create invoice") })
  const approveMut = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/supply-chain/supplier-invoices/${id}/approve`), onSuccess: () => qc.invalidateQueries({ queryKey: ["scm-invoices"] }) })
  const payMut     = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/supply-chain/supplier-invoices/${id}/pay`, { paymentReference: "MANUAL" }), onSuccess: () => qc.invalidateQueries({ queryKey: ["scm-invoices"] }) })

  const subtotal = parseFloat(form.subtotal) || 0
  const vat      = parseFloat(form.vatAmount) || subtotal * 0.15
  const STATUSES = ["", "RECEIVED", "UNDER_REVIEW", "APPROVED", "PAID", "DISPUTED"]

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 10, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {STATUSES.map(s => <button key={s} onClick={() => setStatusFilter(s)} style={filterPill(statusFilter === s)}>{s ? s.replace(/_/g, " ") : "All"}</button>)}
        </div>
        <button onClick={() => { setShowCreate(true); setErr("") }} style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 14px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> Record Invoice
        </button>
      </div>

      {isLoading ? <Spinner /> : invoices.length === 0 ? (
        <EmptyState icon={Plus} title="No supplier invoices" sub="Record invoices received from suppliers to process payment" />
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr style={{ background: "#F8FAFC" }}>
              {["Invoice Ref", "Supplier Ref", "Total", "Due Date", "3-Way Match", "Status", ""].map(h => <th key={h} style={TH}>{h}</th>)}
            </tr></thead>
            <tbody>
              {invoices.map((inv, i) => {
                const m = matchStyle(inv.matchStatus ?? "NO_PO")
                return (
                  <tr key={inv.id} style={{ background: i % 2 === 0 ? "#fff" : "#FAFAFA", borderTop: "1px solid #F1F5F9" }}>
                    <td style={TD}><span style={{ fontWeight: 700, color: "#1B3A6B" }}>{inv.invoiceNumber ?? `SINV-${inv.id.slice(0, 6).toUpperCase()}`}</span></td>
                    <td style={{ ...TD, color: "#64748B" }}>{inv.supplierInvoiceRef || "—"}</td>
                    <td style={TD}><strong style={{ color: inv.overdue ? "#DC2626" : "#0F172A" }}>{fmtR(inv.totalAmount)}</strong></td>
                    <td style={TD}>
                      <span style={{ color: inv.overdue ? "#DC2626" : "#475569", fontWeight: inv.overdue ? 700 : 400 }}>
                        {fmtDate(inv.dueDate)}{inv.overdue && " — OVERDUE"}
                      </span>
                    </td>
                    <td style={TD}>
                      <div style={{ display: "flex", alignItems: "center", gap: 5 }}>
                        <m.Icon size={13} color={m.color} />
                        <span style={{ fontSize: 12, color: m.color, fontWeight: 600 }}>{m.label}</span>
                      </div>
                    </td>
                    <td style={TD}><Badge status={inv.status} /></td>
                    <td style={TD}>
                      <div style={{ display: "flex", gap: 5 }}>
                        {inv.status === "RECEIVED" && <ActionChip label="Approve" color="#166534" bg="#DCFCE7" border="#86EFAC" onClick={() => approveMut.mutate(inv.id)} />}
                        {inv.status === "APPROVED" && <ActionChip label="Mark Paid" color="#1D4ED8" bg="#EFF6FF" border="#BFDBFE" onClick={() => payMut.mutate(inv.id)} />}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Record Invoice Modal */}
      {showCreate && (
        <Modal title="Record Supplier Invoice" onClose={() => setShowCreate(false)}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Supplier *" span={2}>
              <select value={form.supplierId} onChange={e => sf("supplierId", e.target.value)} style={inp}>
                <option value="">Select supplier…</option>
                {suppliers.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </Field>
            <Field label="Supplier's Invoice Ref"><input value={form.supplierInvoiceRef} onChange={e => sf("supplierInvoiceRef", e.target.value)} placeholder="INV-2026-001" style={inp} /></Field>
            <Field label="Invoice Date *"><input type="date" value={form.invoiceDate} onChange={e => sf("invoiceDate", e.target.value)} style={inp} /></Field>
            <Field label="Due Date *"><input type="date" value={form.dueDate} onChange={e => sf("dueDate", e.target.value)} style={inp} /></Field>
            <Field label="Subtotal (excl. VAT) *"><input type="number" step="0.01" value={form.subtotal} onChange={e => sf("subtotal", e.target.value)} placeholder="0.00" style={inp} /></Field>
            <Field label="VAT (R)"><input type="number" step="0.01" value={form.vatAmount} onChange={e => sf("vatAmount", e.target.value)} placeholder={subtotal > 0 ? (subtotal * 0.15).toFixed(2) : "0.00"} style={inp} /></Field>
            <Field label="Total (incl. VAT)" span={2}>
              <div style={{ padding: "9px 12px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, fontWeight: 700, color: "#0F172A" }}>
                {fmtR(subtotal + vat)}
              </div>
            </Field>
            <Field label="Notes" span={2}><textarea value={form.notes} onChange={e => sf("notes", e.target.value)} style={{ ...inp, minHeight: 56, resize: "vertical" }} /></Field>
          </div>
          {err && <ErrBox msg={err} />}
          <ModalFooter onCancel={() => setShowCreate(false)} loading={createMut.isPending} label={createMut.isPending ? "Saving…" : "Record Invoice"}
            onConfirm={() => { if (!form.supplierId || !form.invoiceDate || !form.dueDate || !form.subtotal) { setErr("Supplier, dates and subtotal are required"); return } createMut.mutate({ supplierId: form.supplierId, supplierInvoiceRef: form.supplierInvoiceRef || null, invoiceDate: form.invoiceDate, dueDate: form.dueDate, subtotal: parseFloat(form.subtotal), vatAmount: vat, totalAmount: subtotal + vat, notes: form.notes || null }) }} />
        </Modal>
      )}
    </div>
  )
}
