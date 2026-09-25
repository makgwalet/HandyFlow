// src/pages/supply-chain/InvoicesTab.tsx
import React, { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, FileText, CheckCircle, CreditCard, AlertTriangle, Paperclip, Trash2, Download } from "lucide-react"
import { Modal, ErrBox, ModalFooter, Field } from "./scm.shared"

interface Supplier { id: string; name: string }
interface PO { id: string; orderNumber: string }
// NEW (Tier 1 gap analysis): backs the GR-linking picker.
interface GoodsReceipt { id: string; receiptNumber: string; status: string }
// NEW: backs supplier invoice attachments.
interface Attachment { id: string; fileName: string; contentType: string; fileSizeBytes: number; uploadedByName: string | null; createdAt: string }
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
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 9, fontSize: 14, boxSizing: "border-box", outline: "none", background: "var(--hf-surface)" }
const fmtR = (n: number) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const fmtD = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"
const isOverdue = (inv: Invoice) => !["PAID","CANCELLED"].includes(inv.status) && new Date(inv.dueDate) < new Date()

const STATUS_BADGE: Record<string, { bg: string; color: string }> = {
  RECEIVED:    { bg: "var(--hf-surface-sunken)", color: "var(--hf-text-tertiary)" },
  UNDER_REVIEW:{ bg: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text-deep)" },
  APPROVED:    { bg: "var(--hf-info-soft-strong)", color: "var(--hf-info-text)" },
  DISPUTED:    { bg: "var(--hf-danger-soft-strong)", color: "var(--hf-danger-text)" },
  PAID:        { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)" },
  CANCELLED:   { bg: "var(--hf-surface-sunken)", color: "var(--hf-text-faint)" },
}
const MATCH_BADGE: Record<string, { bg: string; color: string; label: string }> = {
  PENDING:      { bg: "var(--hf-surface-sunken)", color: "var(--hf-text-faint)",  label: "Pending" },
  MATCHED:      { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)",  label: "✓ Matched" },
  PARTIAL_MATCH:{ bg: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text-deep)",  label: "Partial" },
  DISPUTE:      { bg: "var(--hf-danger-soft-strong)", color: "var(--hf-danger-text)",  label: "⚠ Dispute" },
  OVERRIDDEN:   { bg: "var(--hf-violet-soft-strong)", color: "var(--hf-violet-text)",  label: "Overridden" },
}

const STATUS_FILTERS = ["", "RECEIVED", "UNDER_REVIEW", "APPROVED", "DISPUTED", "PAID"]

// NEW: backs the Record Invoice form's field-specific validation —
// names exactly which required fields are still empty, and which keys
// to red-border, instead of one bundled message naming every possible
// field regardless of which ones actually are missing.
const REQUIRED_INVOICE_FIELDS: { key: string; label: string }[] = [
  { key: "supplierId",  label: "Supplier" },
  { key: "invoiceDate", label: "Invoice Date" },
  { key: "dueDate",     label: "Due Date" },
  { key: "subtotal",    label: "Subtotal" },
  { key: "totalAmount", label: "Total (incl. VAT)" },
]

function joinWithAnd(items: string[]): string {
  if (items.length === 1) return items[0]
  if (items.length === 2) return `${items[0]} and ${items[1]}`
  return `${items.slice(0, -1).join(", ")} and ${items[items.length - 1]}`
}

export function InvoicesTab() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState("")
  const [selected, setSelected] = useState<Invoice | null>(null)
  // NEW: gap-analysis item — supplier invoice attachments.
  const [attachError, setAttachError] = useState("")
  const [uploadingAttachment, setUploadingAttachment] = useState(false)
  const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024 // matches ScmService's own cap — checked client-side too, so a huge file is rejected before wasting time base64-encoding it
  const [showCreate, setShowCreate] = useState(false)
  const [showMarkPaid, setShowMarkPaid] = useState(false)
  const [paymentRef, setPaymentRef] = useState("")
  // NEW (Tier 1 gap analysis): drives a shared reason-entry panel for
  // both dispute-resolution actions, rather than two near-duplicate
  // sub-panels — same visual pattern as showMarkPaid/paymentRef above.
  const [reasonAction, setReasonAction] = useState<"override" | "cancel" | null>(null)
  const [reasonText, setReasonText] = useState("")
  const [err, setErr] = useState("")

  const initF = () => ({
    supplierId: "", purchaseOrderId: "", goodsReceiptId: "", supplierInvoiceRef: "",
    invoiceDate: "", dueDate: "", subtotal: "", vatAmount: "", totalAmount: "", notes: ""
  })
  const [form, setForm] = useState(initF())
  // NEW: drives per-field red-border highlighting on the Record Invoice
  // form. Previously "Supplier, dates, subtotal and total are required"
  // named every possibly-missing field regardless of which ones actually
  // were — confirmed via a real screenshot where Supplier/Invoice Date/
  // Subtotal/Total were all filled in and only Due Date was empty, but
  // the message gave no indication of that.
  const [invalidFields, setInvalidFields] = useState<Set<string>>(new Set())
  const sf = (k: string, v: string) => {
    setForm(p => ({ ...p, [k]: v }))
    setInvalidFields(prev => {
      if (!prev.has(k)) return prev
      const next = new Set(prev)
      next.delete(k)
      return next
    })
  }

  // NEW: replaces the old single bundled boolean check. Returns false
  // and sets both a specific message and the exact set of field keys to
  // red-border when something's missing, rather than naming every
  // possibly-required field regardless of which ones actually are.
  const validateInvoiceForm = (): boolean => {
    const missing = REQUIRED_INVOICE_FIELDS.filter(f => !String((form as any)[f.key] ?? "").trim())
    if (missing.length === 0) {
      setInvalidFields(new Set())
      return true
    }
    setInvalidFields(new Set(missing.map(f => f.key)))
    setErr(`${joinWithAnd(missing.map(f => f.label))} ${missing.length === 1 ? "is" : "are"} required`)
    return false
  }
  const fieldStyle = (key: string) => invalidFields.has(key) ? { ...inp, border: "1.5px solid var(--hf-danger)" } : inp

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

  // NEW (Tier 1 gap analysis): backs the GR-linking field below —
  // previously form.goodsReceiptId existed in state but had no input to
  // set it from anywhere, so the 3-way match's GR-posted check could
  // never actually be exercised from this UI. Scoped to the currently
  // selected PO — only fetches once one is chosen.
  const { data: poGoodsReceipts = [] } = useQuery<GoodsReceipt[]>({
    queryKey: ["scm-po-goods-receipts", form.purchaseOrderId],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/supply-chain/purchase-orders/${form.purchaseOrderId}/goods-receipts`)
      const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : []
    },
    enabled: !!form.purchaseOrderId,
    staleTime: 30_000,
  })

  const invalidate = () => { qc.invalidateQueries({ queryKey: ["scm-invoices"] }); qc.invalidateQueries({ queryKey: ["scm-summary"] }) }

  // NEW: gap-analysis item — supplier invoice attachments. Metadata-only
  // list, enabled only once an invoice is selected.
  const { data: attachments = [], refetch: refetchAttachments } = useQuery<Attachment[]>({
    queryKey: ["scm-invoice-attachments", selected?.id],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/supply-chain/supplier-invoices/${selected!.id}/attachments`)
      const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : []
    },
    enabled: !!selected,
  })

  // Client-side size check first — rejects an obviously-too-large file
  // before spending time base64-encoding it, but the real enforcement is
  // server-side (ScmService.uploadInvoiceAttachment) since a client check
  // alone is trivially bypassable.
  const uploadAttachment = async (file: File) => {
    setAttachError("")
    if (file.size > MAX_ATTACHMENT_BYTES) {
      setAttachError(`File is too large — maximum attachment size is ${MAX_ATTACHMENT_BYTES / (1024 * 1024)}MB`)
      return
    }
    setUploadingAttachment(true)
    try {
      const base64 = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(((reader.result as string) || "").split(",")[1] ?? "")
        reader.onerror = () => reject(reader.error)
        reader.readAsDataURL(file)
      })
      await apiClient.post(`/api/v1/supply-chain/supplier-invoices/${selected!.id}/attachments`, {
        fileName: file.name,
        contentType: file.type || "application/octet-stream",
        fileSizeBytes: file.size,
        fileContentBase64: base64,
      })
      refetchAttachments()
    } catch (e: any) {
      setAttachError(e.response?.data?.message || "Failed to upload attachment")
    } finally {
      setUploadingAttachment(false)
    }
  }

  // Blob download — same pattern as the PO PDF download button, since this
  // endpoint requires the Bearer auth header apiClient attaches, which a
  // plain anchor link can't carry.
  const downloadAttachment = async (att: Attachment) => {
    try {
      const res = await apiClient.get(`/api/v1/supply-chain/supplier-invoices/${selected!.id}/attachments/${att.id}`, { responseType: "blob" })
      const blob = new Blob([res.data], { type: att.contentType })
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url; a.download = att.fileName
      document.body.appendChild(a); a.click(); a.remove()
      URL.revokeObjectURL(url)
    } catch (e: any) {
      setAttachError(e.response?.data?.message || "Failed to download attachment")
    }
  }

  // NEW: gap-analysis item — "remittance advice to the supplier when an
  // invoice is marked paid" was missing entirely. Same blob-download
  // pattern as the PO PDF / attachment downloads.
  const [downloadingRemittance, setDownloadingRemittance] = useState(false)
  const downloadRemittanceAdvice = async (invoiceId: string) => {
    setDownloadingRemittance(true)
    try {
      const res = await apiClient.get(`/api/v1/supply-chain/supplier-invoices/${invoiceId}/remittance-advice`, { responseType: "blob" })
      const blob = new Blob([res.data], { type: "application/pdf" })
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url; a.download = `remittance-advice-${selected?.invoiceNumber ?? invoiceId}.pdf`
      document.body.appendChild(a); a.click(); a.remove()
      URL.revokeObjectURL(url)
    } catch (e: any) {
      setErr(e.response?.data?.message || "Failed to download remittance advice")
    } finally {
      setDownloadingRemittance(false)
    }
  }

  const deleteAttachMut = useMutation({
    mutationFn: (attachmentId: string) => apiClient.delete(`/api/v1/supply-chain/supplier-invoices/${selected!.id}/attachments/${attachmentId}`),
    onSuccess: () => refetchAttachments(),
    onError: (e: any) => setAttachError(e.response?.data?.message || "Failed to delete attachment"),
  })

  const createMut = useMutation({
    mutationFn: (b: any) => apiClient.post("/api/v1/supply-chain/supplier-invoices", b),
    onSuccess: () => { invalidate(); setShowCreate(false); setForm(initF()); setErr(""); setInvalidFields(new Set()) },
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
  // NEW (Tier 1 gap analysis): resolves a DISPUTED invoice one of two
  // ways — override the mismatch and approve it anyway, or cancel it.
  // Previously neither was possible; a disputed invoice was a dead end.
  const overrideMut = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => apiClient.post(`/api/v1/supply-chain/supplier-invoices/${id}/override-dispute`, { reason }),
    onSuccess: (r) => { invalidate(); const inv = r.data?.data ?? r.data; if (inv?.id) setSelected(inv); setReasonAction(null); setReasonText("") },
    onError: (e: any) => setErr(e.response?.data?.message || "Failed to override dispute"),
  })
  const cancelInvoiceMut = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => apiClient.post(`/api/v1/supply-chain/supplier-invoices/${id}/cancel`, { reason }),
    onSuccess: (r) => { invalidate(); const inv = r.data?.data ?? r.data; if (inv?.id) setSelected(inv); setReasonAction(null); setReasonText("") },
    onError: (e: any) => setErr(e.response?.data?.message || "Failed to cancel invoice"),
  })

  const overdue = invoices.filter(isOverdue).length
  const pending = invoices.filter(i => i.status === "RECEIVED").length
  const disputes = invoices.filter(i => i.matchStatus === "DISPUTE" || i.status === "DISPUTED").length

  return (
    <div>
      {/* Summary pills */}
      {(overdue > 0 || disputes > 0) && (
        <div style={{ display: "flex", gap: 8, marginBottom: 14 }}>
          {overdue > 0 && <div style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 12, fontWeight: 700, color: "var(--hf-danger-text)" }}><AlertTriangle size={12} /> {overdue} overdue</div>}
          {disputes > 0 && <div style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-warning-soft-strong)", border: "1px solid var(--hf-warning-border-strong)", borderRadius: 8, fontSize: 12, fontWeight: 700, color: "var(--hf-warning-text-deep)" }}><AlertTriangle size={12} /> {disputes} dispute{disputes !== 1 ? "s" : ""}</div>}
          {pending > 0 && <div style={{ padding: "6px 12px", background: "var(--hf-surface-sunken)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12, fontWeight: 700, color: "var(--hf-text-tertiary)" }}>{pending} pending review</div>}
        </div>
      )}

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 10, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {STATUS_FILTERS.map(s => (
            <button key={s} onClick={() => setStatusFilter(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", fontWeight: statusFilter === s ? 700 : 400, border: statusFilter === s ? `1.5px solid ${ACCENT}` : "1px solid var(--hf-border)", background: statusFilter === s ? "var(--hf-warning-soft-strong)" : "var(--hf-surface)", color: statusFilter === s ? ACCENT : "var(--hf-text-muted)" }}>
              {s || "All"}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowCreate(true); setErr(""); setInvalidFields(new Set()) }}
          style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 14px", background: ACCENT, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> Record Invoice
        </button>
      </div>

      {/* Invoice table */}
      {isLoading
        ? <div style={{ padding: 40, textAlign: "center", color: "var(--hf-text-faint)" }}>Loading…</div>
        : invoices.length === 0
          ? <div style={{ textAlign: "center", padding: "50px 0", color: "var(--hf-text-faint)" }}><FileText size={36} style={{ opacity: .3, marginBottom: 10 }} /><div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No invoices found</div></div>
          : <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr style={{ background: "var(--hf-surface-muted)" }}>
                  {["Invoice #", "Supplier Ref", "Invoice Date", "Due Date", "Amount", "Status", "Match", ""].map(h => (
                    <th key={h} style={{ padding: "10px 14px", textAlign: "left", fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.05em" }}>{h}</th>
                  ))}
                </tr></thead>
                <tbody>
                  {invoices.map((inv, i) => {
                    const sb  = STATUS_BADGE[inv.status] ?? STATUS_BADGE.RECEIVED
                    const mb  = MATCH_BADGE[inv.matchStatus] ?? MATCH_BADGE.PENDING
                    const odd = isOverdue(inv)
                    return (
                      <tr key={inv.id} onClick={() => { setSelected(inv); setErr(""); setAttachError("") }}
                        style={{ borderTop: "1px solid var(--hf-border-subtle)", background: odd ? "var(--hf-danger-soft)" : i % 2 === 0 ? "var(--hf-surface)" : "var(--hf-surface-muted)", cursor: "pointer" }}
                        onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = "#F0F7FF"}
                        onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = odd ? "var(--hf-danger-soft)" : i % 2 === 0 ? "var(--hf-surface)" : "var(--hf-surface-muted)"}
                      >
                        <td style={{ padding: "11px 14px", fontSize: 12, fontWeight: 700, color: ACCENT }}>{inv.invoiceNumber}</td>
                        <td style={{ padding: "11px 14px", fontSize: 12, color: "var(--hf-text-muted)" }}>{inv.supplierInvoiceRef ?? "—"}</td>
                        <td style={{ padding: "11px 14px", fontSize: 12, color: "var(--hf-text-muted)" }}>{fmtD(inv.invoiceDate)}</td>
                        <td style={{ padding: "11px 14px", fontSize: 12, color: odd ? "var(--hf-danger-text)" : "var(--hf-text-muted)", fontWeight: odd ? 700 : 400 }}>{fmtD(inv.dueDate)}{odd && " ⚠"}</td>
                        <td style={{ padding: "11px 14px", fontSize: 13, fontWeight: 700 }}>{fmtR(inv.totalAmount)}</td>
                        <td style={{ padding: "11px 14px" }}><span style={{ background: sb.bg, color: sb.color, fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20 }}>{inv.status}</span></td>
                        <td style={{ padding: "11px 14px" }}><span style={{ background: mb.bg, color: mb.color, fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20 }}>{mb.label}</span></td>
                        <td style={{ padding: "11px 14px", fontSize: 12, color: "var(--hf-info-text)", fontWeight: 600 }}>View →</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
      }

      {/* Invoice detail panel */}
      {selected && (
        <Modal
          title={<><div style={{ fontSize: 11, fontWeight: 700, color: ACCENT, marginBottom: 3 }}>{selected.invoiceNumber}</div>Supplier Invoice</>}
          onClose={() => { setSelected(null); setErr(""); setAttachError("") }}>

            {/* Match status banner */}
            {selected.matchStatus !== "PENDING" && (
              <div style={{ marginBottom: 14, padding: "10px 14px", background: MATCH_BADGE[selected.matchStatus]?.bg ?? "var(--hf-surface-sunken)", borderRadius: 9, fontSize: 13, fontWeight: 600, color: MATCH_BADGE[selected.matchStatus]?.color ?? "var(--hf-text-tertiary)" }}>
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
                <div key={k}><div style={{ fontSize: 10, color: "var(--hf-text-faint)", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.04em", marginBottom: 3 }}>{k}</div><div style={{ fontSize: 13, fontWeight: 600 }}>{v}</div></div>
              ))}
            </div>

            {/* Amounts */}
            <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "14px 16px", marginBottom: 16 }}>
              {[["Subtotal", fmtR(selected.subtotal)], ["VAT", fmtR(selected.vatAmount)]].map(([k, v]) => (
                <div key={k} style={{ display: "flex", justifyContent: "space-between", fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 8 }}><span>{k}</span><span>{v}</span></div>
              ))}
              <div style={{ borderTop: "1px solid var(--hf-border)", paddingTop: 8, display: "flex", justifyContent: "space-between", fontSize: 15, fontWeight: 800, color: "var(--hf-text)" }}>
                <span>Total</span><span style={{ color: ACCENT }}>{fmtR(selected.totalAmount)}</span>
              </div>
            </div>

            {/* Payment info */}
            {selected.status === "PAID" && (
              <div style={{ marginBottom: 16, padding: "10px 14px", background: "var(--hf-success-soft-strong)", border: "1px solid var(--hf-success-border)", borderRadius: 9 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div>
                    <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-success-text-strong)" }}>✓ Paid {fmtD(selected.paidAt)}</div>
                    {selected.paymentReference && <div style={{ fontSize: 12, color: "var(--hf-success-text-strong)" }}>Ref: {selected.paymentReference}</div>}
                  </div>
                  {/* NEW: gap-analysis item — remittance advice PDF,
                      only downloadable once actually paid, matching the
                      backend's own guard. */}
                  <button onClick={() => downloadRemittanceAdvice(selected.id)} disabled={downloadingRemittance}
                    style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-surface)", border: "1px solid var(--hf-success-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, color: "var(--hf-success-text-strong)", cursor: "pointer" }}>
                    <Download size={12} /> {downloadingRemittance ? "Downloading…" : "Remittance Advice"}
                  </button>
                </div>
              </div>
            )}
            {selected.approvedByName && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 12 }}>Approved by {selected.approvedByName} on {fmtD(selected.approvedAt)}</div>}
            {selected.notes && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 12 }}>Notes: {selected.notes}</div>}

            {/* NEW: gap-analysis item — supplier invoice attachments.
                Base64-in-DB, following Creative's own proven pattern
                since there's no S3 available in dev — see
                ScSupplierInvoiceAttachment.java's class Javadoc. */}
            <div style={{ marginBottom: 16 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.04em" }}>
                  Attachments{attachments.length > 0 ? ` (${attachments.length})` : ""}
                </div>
                <label style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 7, fontSize: 12, cursor: uploadingAttachment ? "default" : "pointer", color: "var(--hf-text-secondary)", opacity: uploadingAttachment ? .6 : 1 }}>
                  <Paperclip size={12} />
                  {uploadingAttachment ? "Uploading…" : "Add File"}
                  <input type="file" style={{ display: "none" }} disabled={uploadingAttachment}
                    onChange={e => { const f = e.target.files?.[0]; if (f) uploadAttachment(f); e.target.value = "" }} />
                </label>
              </div>
              {attachError && <ErrBox msg={attachError} />}
              {attachments.length === 0
                ? <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No attachments yet.</div>
                : (
                  <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                    {attachments.map(a => (
                      <div key={a.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "7px 10px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 7 }}>
                        <button onClick={() => downloadAttachment(a)} style={{ background: "none", border: "none", cursor: "pointer", fontSize: 12, color: ACCENT, fontWeight: 600, textAlign: "left", padding: 0 }}>
                          {a.fileName}
                        </button>
                        <div style={{ display: "flex", alignItems: "center", gap: 8, flexShrink: 0 }}>
                          <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{(a.fileSizeBytes / 1024).toFixed(0)} KB</span>
                          <button onClick={() => deleteAttachMut.mutate(a.id)} disabled={deleteAttachMut.isPending}
                            style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-danger-text)", display: "flex" }}>
                            <Trash2 size={13} />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )
              }
            </div>

            {err && <ErrBox msg={err} />}

            {/* Actions — custom multi-button row, doesn't fit ModalFooter's
                generic two-button shape (up to 4 buttons depending on
                status), so this stays as its own thing rather than being
                forced into it. */}
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 12 }}>
              <button onClick={() => { setSelected(null); setErr(""); setAttachError("") }} style={{ padding: "9px 16px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 13, cursor: "pointer", color: "var(--hf-text-muted)" }}>Close</button>
              {(selected.status === "RECEIVED" || selected.status === "UNDER_REVIEW") && (
                <button onClick={() => approveMut.mutate(selected.id)} disabled={approveMut.isPending}
                  style={{ display: "flex", alignItems: "center", gap: 5, padding: "9px 16px", background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", border: "1px solid var(--hf-success-border)", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                  <CheckCircle size={13} /> {approveMut.isPending ? "Approving…" : "Approve"}
                </button>
              )}
              {selected.status === "APPROVED" && (
                <button onClick={() => setShowMarkPaid(true)}
                  style={{ display: "flex", alignItems: "center", gap: 5, padding: "9px 16px", background: "var(--hf-info-soft-strong)", color: "var(--hf-info-text)", border: "1px solid var(--hf-info-border)", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                  <CreditCard size={13} /> Mark as Paid
                </button>
              )}
              {/* NEW (Tier 1 gap analysis): previously a DISPUTED invoice
                  had no action available here at all — just "Close". */}
              {selected.status === "DISPUTED" && (
                <>
                  <button onClick={() => { setReasonAction("override"); setReasonText(""); setErr("") }}
                    style={{ display: "flex", alignItems: "center", gap: 5, padding: "9px 16px", background: "var(--hf-violet-soft-strong)", color: "var(--hf-violet-text)", border: "1px solid var(--hf-violet-border)", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                    <CheckCircle size={13} /> Override &amp; Approve
                  </button>
                  <button onClick={() => { setReasonAction("cancel"); setReasonText(""); setErr("") }}
                    style={{ display: "flex", alignItems: "center", gap: 5, padding: "9px 16px", background: "var(--hf-danger-soft-strong)", color: "var(--hf-danger-text)", border: "1px solid var(--hf-danger-border)", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                    <AlertTriangle size={13} /> Cancel Invoice
                  </button>
                </>
              )}
            </div>

            {/* Mark paid sub-panel */}
            {showMarkPaid && (
              <div style={{ marginTop: 14, padding: "14px 16px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 6 }}>Payment Reference *</label>
                <input value={paymentRef} onChange={e => setPaymentRef(e.target.value)} placeholder="e.g. EFT-20260703-001 or cheque number" style={inp} autoFocus />
                <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 10 }}>
                  <button onClick={() => { setShowMarkPaid(false); setPaymentRef("") }} style={{ padding: "7px 14px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", fontSize: 12, cursor: "pointer" }}>Cancel</button>
                  <button onClick={() => { if (!paymentRef.trim()) return; paidMut.mutate({ id: selected.id, ref: paymentRef.trim() }) }}
                    disabled={!paymentRef.trim() || paidMut.isPending}
                    style={{ padding: "7px 14px", background: ACCENT, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: "pointer", opacity: !paymentRef.trim() ? .5 : 1 }}>
                    {paidMut.isPending ? "Saving…" : "Confirm Payment"}
                  </button>
                </div>
              </div>
            )}

            {/* NEW (Tier 1 gap analysis): shared reason-entry panel for
                both dispute-resolution actions — same visual pattern as
                the mark-paid panel above. */}
            {reasonAction && (
              <div style={{ marginTop: 14, padding: "14px 16px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 6 }}>
                  {reasonAction === "override" ? "Reason for overriding this dispute *" : "Reason for cancelling this invoice *"}
                </label>
                <input value={reasonText} onChange={e => setReasonText(e.target.value)}
                  placeholder={reasonAction === "override" ? "e.g. Variance confirmed acceptable with supplier" : "e.g. Duplicate invoice — requesting corrected copy"}
                  style={inp} autoFocus />
                <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 10 }}>
                  <button onClick={() => { setReasonAction(null); setReasonText("") }} style={{ padding: "7px 14px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", fontSize: 12, cursor: "pointer" }}>Cancel</button>
                  <button onClick={() => {
                      if (!reasonText.trim()) return
                      if (reasonAction === "override") overrideMut.mutate({ id: selected.id, reason: reasonText.trim() })
                      else cancelInvoiceMut.mutate({ id: selected.id, reason: reasonText.trim() })
                    }}
                    disabled={!reasonText.trim() || overrideMut.isPending || cancelInvoiceMut.isPending}
                    style={{ padding: "7px 14px", background: reasonAction === "override" ? "var(--hf-violet)" : "var(--hf-danger)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: "pointer", opacity: !reasonText.trim() ? .5 : 1 }}>
                    {(overrideMut.isPending || cancelInvoiceMut.isPending) ? "Saving…" : reasonAction === "override" ? "Confirm Override" : "Confirm Cancellation"}
                  </button>
                </div>
              </div>
            )}
        </Modal>
      )}

      {/* Create Invoice Modal */}
      {showCreate && (
        <Modal title="Record Supplier Invoice" onClose={() => { setShowCreate(false); setErr(""); setInvalidFields(new Set()) }}>
            <div style={{ marginBottom: 14, padding: "10px 12px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border-strong)", borderRadius: 8, fontSize: 12, color: "var(--hf-warning-text-deep)" }}>
              Linking a PO and GR enables 3-way matching — HandyFlow will automatically compare amounts and flag discrepancies.
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <Field label="Supplier *" span={2}>
                <select value={form.supplierId} onChange={e => sf("supplierId", e.target.value)} style={fieldStyle("supplierId")}>
                  <option value="">Select supplier…</option>
                  {suppliers.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </Field>
              <Field label="Link to PO (optional)">
                <select value={form.purchaseOrderId}
                  onChange={e => setForm(p => ({ ...p, purchaseOrderId: e.target.value, goodsReceiptId: "" }))}
                  style={inp}>
                  <option value="">No PO linked</option>
                  {openPOs.map(p => <option key={p.id} value={p.id}>{p.orderNumber}</option>)}
                </select>
              </Field>
              {/* NEW (Tier 1 gap analysis): only shown once a PO is
                  selected, since a GR only makes sense in relation to a
                  specific PO. This is what closes the gap where the 3-way
                  match's GR-posted check was correctly implemented on the
                  backend but structurally unreachable from this form. */}
              {form.purchaseOrderId && (
                <Field label="Link to Goods Receipt (optional)">
                  <select value={form.goodsReceiptId} onChange={e => sf("goodsReceiptId", e.target.value)} style={inp}>
                    <option value="">No GR linked</option>
                    {poGoodsReceipts.map(g => (
                      <option key={g.id} value={g.id}>{g.receiptNumber} — {g.status === "POSTED" ? "Posted" : "Draft"}</option>
                    ))}
                  </select>
                  {poGoodsReceipts.length === 0 && (
                    <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 4 }}>No goods receipts recorded against this PO yet.</div>
                  )}
                </Field>
              )}
              <Field label="Supplier's Invoice Ref">
                <input value={form.supplierInvoiceRef} onChange={e => sf("supplierInvoiceRef", e.target.value)} placeholder="INV-2026-1234" style={inp} />
              </Field>
              <Field label="Invoice Date *">
                <input type="date" value={form.invoiceDate} onChange={e => sf("invoiceDate", e.target.value)} style={fieldStyle("invoiceDate")} />
              </Field>
              <Field label="Due Date *">
                <input type="date" value={form.dueDate} onChange={e => sf("dueDate", e.target.value)} style={fieldStyle("dueDate")} />
              </Field>
              <Field label="Subtotal (R) *">
                <input type="number" value={form.subtotal} onChange={e => sf("subtotal", e.target.value)} placeholder="0.00" style={fieldStyle("subtotal")} />
              </Field>
              <Field label="VAT Amount (R)">
                <input type="number" value={form.vatAmount} onChange={e => sf("vatAmount", e.target.value)} placeholder="0.00" style={inp} />
              </Field>
              <Field label="Total (incl. VAT) *">
                <input type="number" value={form.totalAmount} onChange={e => sf("totalAmount", e.target.value)} placeholder="0.00" style={fieldStyle("totalAmount")} />
              </Field>
              <Field label="Notes" span={2}>
                <textarea value={form.notes} onChange={e => sf("notes", e.target.value)} style={{ ...inp, minHeight: 50, resize: "vertical" }} />
              </Field>
            </div>
            {err && <ErrBox msg={err} />}
            <ModalFooter
              onCancel={() => { setShowCreate(false); setErr(""); setInvalidFields(new Set()) }}
              onConfirm={() => {
                if (!validateInvoiceForm()) return
                createMut.mutate({ supplierId: form.supplierId, purchaseOrderId: form.purchaseOrderId || null, goodsReceiptId: form.goodsReceiptId || null, supplierInvoiceRef: form.supplierInvoiceRef || null, invoiceDate: form.invoiceDate, dueDate: form.dueDate, currency: "ZAR", subtotal: parseFloat(form.subtotal), vatAmount: parseFloat(form.vatAmount) || 0, totalAmount: parseFloat(form.totalAmount), notes: form.notes || null })
              }}
              label={createMut.isPending ? "Recording…" : "Record Invoice"}
              loading={createMut.isPending}
              accent={ACCENT}
            />
        </Modal>
      )}
    </div>
  )
}
