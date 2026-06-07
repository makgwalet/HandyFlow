// src/pages/ap/BatchesTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, X, CheckCircle, XCircle, AlertTriangle,
  Download, Upload, FileText, ChevronDown, ChevronUp,
  Loader2, CreditCard, Send,
} from "lucide-react"

interface Bill {
  id: string; supplierName: string; billNumber: string
  dueDate: string; totalAmount: number; status: string
  description: string; category: string; hasPop: boolean
}
interface Batch {
  id: string; batchNumber: string; bankAccountId: string | null
  bankAccountName: string | null; description: string | null
  totalAmount: number; billCount: number; status: string
  paymentDate: string | null; paymentRef: string | null
  hasPop: boolean; bills: Bill[] | null
  submittedAt: string | null; paidAt: string | null; createdAt: string
}

const STATUS: Record<string, { color: string; bg: string; border: string; dot: string; label: string }> = {
  DRAFT:     { color: "#64748B", bg: "#F8FAFC", border: "#E2E8F0", dot: "#CBD5E1", label: "Draft" },
  SUBMITTED: { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A", dot: "#F59E0B", label: "Submitted" },
  PAID:      { color: "#1D4ED8", bg: "#EFF6FF", border: "#BFDBFE", dot: "#3B82F6", label: "Paid" },
  CANCELLED: { color: "#94A3B8", bg: "#F8FAFC", border: "#E2E8F0", dot: "#CBD5E1", label: "Cancelled" },
}

const fmtR    = (n: any) => n != null ? `R\u00A0${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtDate = (d: any) => d ? new Date(d + (String(d).includes("T") ? "" : "T00:00:00")).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtDT   = (d: any) => d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—"

const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff", outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 11, fontWeight: 700, color: "#6B7280", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 6 }
const btnP: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const btnS: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "9px 14px", border: "1.5px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 13, cursor: "pointer", color: "#374151", fontWeight: 500 }

function ConfirmModal({ title, message, confirmLabel, danger = false, loading = false, onConfirm, onCancel, children }: any) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 2000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 440, boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }}>
        <div style={{ display: "flex", gap: 14, marginBottom: 20 }}>
          <div style={{ width: 40, height: 40, borderRadius: "50%", background: danger ? "#FEF2F2" : "#DCFCE7", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
            {danger ? <AlertTriangle size={18} color="#DC2626" /> : <CheckCircle size={18} color="#166534" />}
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 6 }}>{title}</div>
            <div style={{ fontSize: 13, color: "#64748B", lineHeight: 1.6 }}>{message}</div>
          </div>
        </div>
        {children}
        <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 16 }}>
          <button onClick={onCancel} style={btnS}>Cancel</button>
          <button onClick={onConfirm} disabled={loading} style={{ ...btnP, background: danger ? "#DC2626" : "#166534", opacity: loading ? 0.6 : 1 }}>
            {loading && <Loader2 size={13} style={{ animation: "spin 1s linear infinite" }} />}
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

export function BatchesTab({ onRefreshSummary }: { onRefreshSummary: () => void }) {
  const qc = useQueryClient()
  const [selectedBatch, setSelectedBatch] = useState<Batch | null>(null)
  const [expandedBatch, setExpandedBatch] = useState<string | null>(null)
  // Cache loaded batch details keyed by batch id so expand doesn't re-fetch
  const [batchDetails, setBatchDetails]   = useState<Record<string, Batch>>({})
  const [showCreate,  setShowCreate]  = useState(false)
  const [showSubmit,  setShowSubmit]  = useState(false)
  const [showConfirmPaid, setShowConfirmPaid] = useState(false)
  const [showCancel,  setShowCancel]  = useState(false)
  const [showPopUpload, setShowPopUpload] = useState(false)
  const [payRef,   setPayRef]   = useState("")
  const [popFile,  setPopFile]  = useState(""); const [popName, setPopName] = useState("")
  const [error,    setError]    = useState("")
  const today = new Date().toISOString().split("T")[0]
  const [createForm, setCreateForm] = useState({ description: "", paymentDate: today, bankAccountId: "", billIds: [] as string[] })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["ap-batches"] })
    qc.invalidateQueries({ queryKey: ["ap-bills"] })
    onRefreshSummary()
  }

  const { data: batchPage, isLoading } = useQuery({
    queryKey: ["ap-batches"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/ap/batches?size=50")
      return r.data?.data ?? r.data
    },
  })

  const { data: approvableBills = [] } = useQuery<Bill[]>({
    queryKey: ["ap-bills-approved"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/ap/bills?status=APPROVED&size=200")
      const p = r.data?.data ?? r.data
      const all = p?.content ?? p ?? []
      return all.filter((b: Bill) => !b.status || b.status === "APPROVED")
    },
    enabled: showCreate,
  })

  const { data: bankAccounts = [] } = useQuery<any[]>({
    queryKey: ["bank-accounts"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/accounting/bank-accounts?size=50")
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
    enabled: showCreate,
  })

  const loadBatchDetail = async (batch: Batch) => {
    const r = await apiClient.get(`/api/v1/ap/batches/${batch.id}`)
    const detail = r.data?.data ?? r.data
    setBatchDetails(prev => ({ ...prev, [batch.id]: detail }))
    setSelectedBatch(detail)
    return detail
  }

  const handleExpand = async (batch: Batch) => {
    const isExpanded = expandedBatch === batch.id
    setExpandedBatch(isExpanded ? null : batch.id)
    // Load detail if not yet cached
    if (!isExpanded && !batchDetails[batch.id]) {
      await loadBatchDetail(batch)
    }
  }

  const createBatch = useMutation({
    mutationFn: () => apiClient.post("/api/v1/ap/batches", {
      bankAccountId: createForm.bankAccountId || (bankAccounts[0]?.id ?? null),
      description: createForm.description || null,
      paymentDate: createForm.paymentDate,
      billIds: createForm.billIds,
    }),
    onSuccess: () => { invalidate(); setShowCreate(false); setCreateForm({ description: "", paymentDate: today, bankAccountId: "", billIds: [] }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create batch"),
  })

  const submitBatch = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/ap/batches/${selectedBatch?.id}/submit`),
    onSuccess: async () => { await loadBatchDetail(selectedBatch!); invalidate(); setShowSubmit(false) },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to submit"),
  })

  const confirmPaid = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/ap/batches/${selectedBatch?.id}/confirm-paid`, { paymentRef: payRef || null }),
    onSuccess: async () => { await loadBatchDetail(selectedBatch!); invalidate(); setShowConfirmPaid(false); setPayRef("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to confirm"),
  })

  const cancelBatch = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/ap/batches/${selectedBatch?.id}/cancel`),
    onSuccess: async () => { await loadBatchDetail(selectedBatch!); invalidate(); setShowCancel(false) },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to cancel"),
  })

  const uploadPop = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/ap/batches/${selectedBatch?.id}/pop`, { fileBase64: popFile, fileName: popName }),
    onSuccess: async () => { await loadBatchDetail(selectedBatch!); setShowPopUpload(false); setPopFile(""); setPopName("") },
  })

  const exportBatch = async (batchId: string, batchNumber: string) => {
    const r = await apiClient.get(`/api/v1/ap/batches/${batchId}/export`, { responseType: "blob" })
    const a = document.createElement("a")
    a.href = URL.createObjectURL(r.data)
    a.download = `eft-${batchNumber}.csv`; a.click()
  }

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]; if (!file) return
    const reader = new FileReader()
    reader.onload = () => { setPopFile((reader.result as string).split(",")[1]); setPopName(file.name) }
    reader.readAsDataURL(file)
  }

  const toggleBillSelection = (billId: string) => {
    setCreateForm(p => ({
      ...p, billIds: p.billIds.includes(billId) ? p.billIds.filter(id => id !== billId) : [...p.billIds, billId]
    }))
  }

  const selectedBillsTotal = (approvableBills as Bill[]).filter(b => createForm.billIds.includes(b.id)).reduce((s, b) => s + Number(b.totalAmount), 0)

  const batches: Batch[] = batchPage?.content ?? batchPage ?? []

  return (
    <div>
      <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>

      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 16 }}>
        <button onClick={() => setShowCreate(true)} style={btnP}><Plus size={14} /> Create EFT Batch</button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 48, color: "#94A3B8" }}>Loading batches...</div>
      ) : batches.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px" }}>
          <CreditCard size={40} style={{ marginBottom: 12, color: "#CBD5E1" }} />
          <div style={{ fontWeight: 700, color: "#475569", fontSize: 15, marginBottom: 6 }}>No EFT batches yet</div>
          <div style={{ fontSize: 13, color: "#94A3B8", marginBottom: 18 }}>Group approved bills into batches for bulk bank payment.</div>
          <button onClick={() => setShowCreate(true)} style={btnP}><Plus size={14} /> Create first batch</button>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {batches.map(batch => {
            const sc = STATUS[batch.status] ?? STATUS.DRAFT
            const expanded = expandedBatch === batch.id
            return (
              <div key={batch.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "16px 20px", display: "flex", justifyContent: "space-between", alignItems: "center", cursor: "pointer", background: "#fff" }}
                  onClick={() => handleExpand(batch)}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                    <div style={{ width: 40, height: 40, borderRadius: 10, background: sc.bg, display: "flex", alignItems: "center", justifyContent: "center" }}>
                      <CreditCard size={18} color={sc.color} />
                    </div>
                    <div>
                      <div style={{ fontWeight: 800, fontSize: 14, color: "#0F172A", display: "flex", alignItems: "center", gap: 8 }}>
                        {batch.batchNumber}
                        <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                          <span style={{ width: 4, height: 4, borderRadius: "50%", background: sc.dot }} />{sc.label}
                        </span>
                        {batch.hasPop && <CheckCircle size={12} color="#0D9488" />}
                      </div>
                      <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>
                        {batch.billCount} bill{batch.billCount !== 1 ? "s" : ""} · Payment {fmtDate(batch.paymentDate)}
                        {batch.bankAccountName && ` · ${batch.bankAccountName}`}
                        {batch.description && ` · ${batch.description}`}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                    <div style={{ textAlign: "right" as const }}>
                      <div style={{ fontWeight: 800, fontSize: 16, color: "#0F172A" }}>{fmtR(batch.totalAmount)}</div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>{fmtDate(batch.createdAt)}</div>
                    </div>
                    <div style={{ display: "flex", gap: 6 }}>
                      {batch.status === "DRAFT" && (
                        <button onClick={e => { e.stopPropagation(); loadBatchDetail(batch).then(() => setShowSubmit(true)) }}
                          style={{ display: "flex", alignItems: "center", gap: 4, padding: "5px 11px", background: "#FFFBEB", color: "#D97706", border: "1px solid #FDE68A", borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: "pointer" }}>
                          <Send size={10} /> Submit
                        </button>
                      )}
                      {batch.status === "SUBMITTED" && (
                        <button onClick={e => { e.stopPropagation(); loadBatchDetail(batch).then(() => setShowConfirmPaid(true)) }}
                          style={{ display: "flex", alignItems: "center", gap: 4, padding: "5px 11px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: "pointer" }}>
                          <CheckCircle size={10} /> Confirm paid
                        </button>
                      )}
                      <button onClick={e => { e.stopPropagation(); exportBatch(batch.id, batch.batchNumber) }}
                        style={{ display: "flex", alignItems: "center", gap: 4, padding: "5px 10px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 7, fontSize: 11, cursor: "pointer" }}>
                        <Download size={10} />
                      </button>
                    </div>
                    {expanded ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                  </div>
                </div>

                {/* Expanded bill list */}
                {expanded && (
                  <div style={{ borderTop: "1px solid #F1F5F9", background: "#F8FAFC" }}>
                    <div style={{ padding: "10px 20px 6px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <span style={{ fontSize: 11, fontWeight: 700, color: "#64748B", textTransform: "uppercase", letterSpacing: "0.06em" }}>Bills in this batch</span>
                      <div style={{ display: "flex", gap: 8 }}>
                        {!["PAID","CANCELLED"].includes(batch.status) && (
                          <>
                            <button onClick={() => { loadBatchDetail(batch).then(() => setShowPopUpload(true)) }}
                              style={{ display: "flex", alignItems: "center", gap: 4, padding: "5px 10px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 11, cursor: "pointer", color: "#64748B" }}>
                              <Upload size={10} /> Upload POP
                            </button>
                            <button onClick={() => loadBatchDetail(batch).then(() => setShowCancel(true))}
                              style={{ display: "flex", alignItems: "center", gap: 4, padding: "5px 10px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 7, fontSize: 11, cursor: "pointer", color: "#DC2626" }}>
                              <XCircle size={10} /> Cancel
                            </button>
                          </>
                        )}
                      </div>
                    </div>
                    {(() => {
                      const detail = batchDetails[batch.id]
                      if (!detail) return (
                        <div style={{ padding: "16px 20px", display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "#94A3B8" }}>
                          <Loader2 size={14} style={{ animation: "spin 1s linear infinite" }} /> Loading bills...
                        </div>
                      )
                      const bills = detail.bills ?? []
                      if (bills.length === 0) return (
                        <div style={{ padding: "16px 20px", fontSize: 13, color: "#94A3B8" }}>No bills in this batch.</div>
                      )
                      return (
                      <table style={{ width: "100%", borderCollapse: "collapse" as const, fontSize: 12 }}>
                        <thead>
                          <tr style={{ borderBottom: "1px solid #E2E8F0" }}>
                            {["Supplier", "Bill #", "Due date", "Amount", "POP"].map(h => (
                              <th key={h} style={{ padding: "8px 16px", textAlign: "left" as const, fontSize: 10, fontWeight: 700, color: "#64748B", letterSpacing: "0.05em" }}>{h}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {bills.map((bill: Bill) => (
                            <tr key={bill.id} style={{ borderBottom: "1px solid #F1F5F9" }}>
                              <td style={{ padding: "9px 16px", fontWeight: 600, color: "#0F172A" }}>{bill.supplierName}</td>
                              <td style={{ padding: "9px 16px", color: "#64748B" }}>#{bill.billNumber}</td>
                              <td style={{ padding: "9px 16px", color: "#64748B" }}>{fmtDate(bill.dueDate)}</td>
                              <td style={{ padding: "9px 16px", fontWeight: 700, color: "#0F172A" }}>{fmtR(bill.totalAmount)}</td>
                              <td style={{ padding: "9px 16px" }}>
                                {bill.hasPop ? <CheckCircle size={13} color="#0D9488" /> : <span style={{ fontSize: 10, color: "#94A3B8" }}>—</span>}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                        <tfoot>
                          <tr style={{ background: "#F0F9FF", borderTop: "1px solid #BFDBFE" }}>
                            <td colSpan={3} style={{ padding: "9px 16px", fontSize: 11, fontWeight: 700, color: "#1D4ED8" }}>Total</td>
                            <td style={{ padding: "9px 16px", fontWeight: 800, color: "#1D4ED8" }}>{fmtR(batch.totalAmount)}</td>
                            <td />
                          </tr>
                        </tfoot>
                      </table>
                      )
                    })()}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Create batch modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1100, padding: 20, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 640, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 25px 80px rgba(0,0,0,0.25)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 22 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>Create EFT Batch</h3>
                <p style={{ margin: "3px 0 0", fontSize: 13, color: "#64748B" }}>Group approved bills for bulk payment via bank EFT</p>
              </div>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 18 }}>
              <div>
                <label style={lbl}>Payment date *</label>
                <input type="date" value={createForm.paymentDate} onChange={e => setCreateForm(p => ({ ...p, paymentDate: e.target.value }))} style={inp} />
              </div>
              <div>
                <label style={lbl}>Bank account</label>
                <select value={createForm.bankAccountId} onChange={e => setCreateForm(p => ({ ...p, bankAccountId: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                  {bankAccounts.map((a: any) => <option key={a.id} value={a.id}>{a.accountName}</option>)}
                </select>
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Description (optional)</label>
                <input value={createForm.description} onChange={e => setCreateForm(p => ({ ...p, description: e.target.value }))} placeholder="June supplier payments" style={inp} />
              </div>
            </div>

            <div style={{ marginBottom: 14 }}>
              <label style={{ ...lbl, marginBottom: 10 }}>Select approved bills to include ({createForm.billIds.length} selected)</label>
              {(approvableBills as Bill[]).length === 0 ? (
                <div style={{ padding: "20px", textAlign: "center", color: "#94A3B8", border: "1.5px dashed #E2E8F0", borderRadius: 10, fontSize: 13 }}>
                  No approved bills available. Approve bills in the Bills tab first.
                </div>
              ) : (
                <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden", maxHeight: 320, overflowY: "auto" }}>
                  {(approvableBills as Bill[]).map((bill, i) => {
                    const checked = createForm.billIds.includes(bill.id)
                    return (
                      <div key={bill.id} onClick={() => toggleBillSelection(bill.id)}
                        style={{ display: "flex", alignItems: "center", gap: 12, padding: "11px 16px", cursor: "pointer", background: checked ? "#EFF6FF" : i % 2 === 0 ? "#fff" : "#FAFAFA", borderBottom: "1px solid #F1F5F9", transition: "background 0.1s" }}>
                        <div style={{ width: 18, height: 18, borderRadius: 4, border: `2px solid ${checked ? "#1B3A6B" : "#D1D5DB"}`, background: checked ? "#1B3A6B" : "#fff", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                          {checked && <CheckCircle size={11} color="#fff" />}
                        </div>
                        <div style={{ flex: 1 }}>
                          <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{bill.supplierName}</div>
                          <div style={{ fontSize: 11, color: "#94A3B8" }}>#{bill.billNumber} · Due {fmtDate(bill.dueDate)}</div>
                        </div>
                        <div style={{ fontWeight: 800, fontSize: 13, color: "#0F172A" }}>{fmtR(bill.totalAmount)}</div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>

            {createForm.billIds.length > 0 && (
              <div style={{ padding: "12px 16px", background: "#EFF6FF", borderRadius: 9, border: "1px solid #BFDBFE", marginBottom: 14, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <span style={{ fontSize: 13, color: "#1D4ED8", fontWeight: 600 }}>{createForm.billIds.length} bills selected</span>
                <span style={{ fontSize: 16, fontWeight: 800, color: "#1B3A6B" }}>{fmtR(selectedBillsTotal)}</span>
              </div>
            )}

            {error && <div style={{ marginBottom: 12, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => setShowCreate(false)} style={btnS}>Cancel</button>
              <button disabled={createForm.billIds.length === 0 || !createForm.paymentDate || createBatch.isPending}
                onClick={() => createBatch.mutate()}
                style={{ ...btnP, opacity: createForm.billIds.length === 0 ? 0.5 : 1 }}>
                {createBatch.isPending ? "Creating..." : <><CreditCard size={13} /> Create batch ({fmtR(selectedBillsTotal)})</>}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* POP upload */}
      {showPopUpload && selectedBatch && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1100, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 18 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Upload remittance / POP</h3>
              <button onClick={() => setShowPopUpload(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={18} /></button>
            </div>
            <label style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 10, padding: "28px", border: `2px dashed ${popFile ? "#0D9488" : "#E2E8F0"}`, borderRadius: 10, cursor: "pointer", background: popFile ? "#F0FDF9" : "#F9FAFB" }}>
              <input type="file" style={{ display: "none" }} onChange={handleFile} />
              {popFile ? <><CheckCircle size={24} color="#0D9488" /><span style={{ fontSize: 13, color: "#0D9488", fontWeight: 600 }}>{popName}</span></> : <><Upload size={24} color="#94A3B8" /><span style={{ fontSize: 13, color: "#64748B" }}>Bank confirmation, remittance advice</span></>}
            </label>
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowPopUpload(false)} style={btnS}>Cancel</button>
              <button disabled={!popFile || uploadPop.isPending} onClick={() => uploadPop.mutate()} style={{ ...btnP, opacity: !popFile ? 0.5 : 1 }}>
                <Upload size={13} /> Upload
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Submit confirm */}
      {showSubmit && selectedBatch && (
        <ConfirmModal title="Submit batch to bank?" message={`Submit batch ${selectedBatch.batchNumber} with ${selectedBatch.billCount} bills (${fmtR(selectedBatch.totalAmount)}) to the bank? Download the EFT export first to upload to your banking portal.`} confirmLabel="Mark as submitted" loading={submitBatch.isPending} onConfirm={() => submitBatch.mutate()} onCancel={() => setShowSubmit(false)} />
      )}

      {/* Confirm paid */}
      {showConfirmPaid && selectedBatch && (
        <ConfirmModal title="Confirm batch payment?" message={`Confirm that batch ${selectedBatch.batchNumber} (${fmtR(selectedBatch.totalAmount)}) has been paid. This will mark all ${selectedBatch.billCount} bills as PAID and post payment journal entries.`} confirmLabel="Confirm payment" loading={confirmPaid.isPending} onConfirm={() => confirmPaid.mutate()} onCancel={() => setShowConfirmPaid(false)}>
          <div style={{ marginBottom: 12 }}>
            <label style={lbl}>Payment reference</label>
            <input value={payRef} onChange={e => setPayRef(e.target.value)} placeholder="Bank transaction ID, EFT reference..." style={inp} autoFocus />
          </div>
        </ConfirmModal>
      )}

      {/* Cancel batch */}
      {showCancel && selectedBatch && (
        <ConfirmModal title="Cancel batch?" message={`Cancel batch ${selectedBatch.batchNumber}? All ${selectedBatch.billCount} bills will be released back to APPROVED status.`} confirmLabel="Cancel batch" danger loading={cancelBatch.isPending} onConfirm={() => cancelBatch.mutate()} onCancel={() => setShowCancel(false)} />
      )}
    </div>
  )
}
