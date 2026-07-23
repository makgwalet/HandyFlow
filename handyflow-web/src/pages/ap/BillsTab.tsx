// src/pages/ap/BillsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, X, CheckCircle, XCircle, Search, Download,
  FileText, Upload, AlertTriangle, ChevronRight,
  DollarSign, Clock, Edit3, Paperclip, Loader2, Mail,
} from "lucide-react"

interface Bill {
  id: string; supplierId: string | null; supplierName: string
  billNumber: string; billDate: string; dueDate: string
  category: string; description: string
  amount: number; vatAmount: number; totalAmount: number
  currency: string; status: string; overdue: boolean
  daysUntilDue: number; hasAttachment: boolean; hasPop: boolean
  paymentRef: string | null; batchId: string | null
  notes: string | null; paidAt: string | null; createdAt: string
  firstApprovedBy: string | null; firstApprovedAt: string | null
  possibleDuplicateWarning?: string | null
}

const STATUS: Record<string, { color: string; bg: string; border: string; dot: string; label: string }> = {
  DRAFT:            { color: "#64748B", bg: "#F8FAFC", border: "#E2E8F0", dot: "#CBD5E1", label: "Draft" },
  SECOND_APPROVAL:  { color: "#7C3AED", bg: "#F5F3FF", border: "#DDD6FE", dot: "#A78BFA", label: "Awaiting 2nd approval" },
  APPROVED:  { color: "#166534", bg: "#DCFCE7", border: "#86EFAC", dot: "#22C55E", label: "Approved" },
  PAID:      { color: "#1D4ED8", bg: "#EFF6FF", border: "#BFDBFE", dot: "#3B82F6", label: "Paid" },
  OVERDUE:   { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA", dot: "#EF4444", label: "Overdue" },
  CANCELLED: { color: "#94A3B8", bg: "#F8FAFC", border: "#E2E8F0", dot: "#CBD5E1", label: "Cancelled" },
}

const CATEGORIES = ["RENT","UTILITIES","FUEL","SALARY","PROFESSIONAL_FEES","EQUIPMENT","MAINTENANCE","INSURANCE","SUBSCRIPTIONS","MARKETING","OTHER"]
const CAT_LABELS: Record<string, string> = { RENT: "Rent", UTILITIES: "Utilities", FUEL: "Fuel", SALARY: "Salary", PROFESSIONAL_FEES: "Professional Fees", EQUIPMENT: "Equipment", MAINTENANCE: "Maintenance", INSURANCE: "Insurance", SUBSCRIPTIONS: "Subscriptions", MARKETING: "Marketing", OTHER: "Other" }

const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff", outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 11, fontWeight: 700, color: "#6B7280", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 6 }
const btnP: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const btnS: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "9px 14px", border: "1.5px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 13, cursor: "pointer", color: "#374151", fontWeight: 500 }

const fmtR    = (n: any) => n != null ? `R\u00A0${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtDate = (d: any) => d ? new Date(d + (String(d).includes("T") ? "" : "T00:00:00")).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtDT   = (d: any) => d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—"

// Confirm modal
function ConfirmModal({ title, message, confirmLabel, danger = false, loading = false, onConfirm, onCancel }: any) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 2000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }}>
        <div style={{ display: "flex", gap: 14, marginBottom: 20 }}>
          <div style={{ width: 40, height: 40, borderRadius: "50%", background: danger ? "#FEF2F2" : "#DCFCE7", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
            {danger ? <AlertTriangle size={18} color="#DC2626" /> : <CheckCircle size={18} color="#166534" />}
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 6 }}>{title}</div>
            <div style={{ fontSize: 13, color: "#64748B", lineHeight: 1.6 }}>{message}</div>
          </div>
        </div>
        <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
          <button onClick={onCancel} style={btnS}>Cancel</button>
          <button onClick={onConfirm} disabled={loading} style={{ ...btnP, background: danger ? "#DC2626" : "#166534", opacity: loading ? 0.6 : 1 }}>
            {loading ? <Loader2 size={13} style={{ animation: "spin 1s linear infinite" }} /> : null}
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

export function BillsTab({ onRefreshSummary }: { onRefreshSummary: () => void }) {
  const qc = useQueryClient()
  const [statusFilter, setStatus] = useState("")
  const [search, setSearch]       = useState("")
  const [catFilter, setCat]       = useState("")
  const [selected, setSelected]   = useState<Bill | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [showEdit,   setShowEdit]   = useState(false)
  const [showApprove, setShowApprove] = useState(false)
  const [showCancel,  setShowCancel]  = useState(false)
  const [showPayModal, setShowPayModal] = useState(false)
  const [showPopUpload, setShowPopUpload] = useState(false)
  const [showAttachUpload, setShowAttachUpload] = useState(false)
  const [error, setError]  = useState("")
  const [remittanceNotice, setRemittanceNotice] = useState("")
  const [duplicateWarning, setDuplicateWarning] = useState("")
  const [payRef, setPayRef] = useState("")
  const [popFile, setPopFile] = useState(""); const [popName, setPopName] = useState("")
  const [attFile, setAttFile] = useState(""); const [attName, setAttName] = useState("")

  const today = new Date().toISOString().split("T")[0]
  const initForm = () => ({
    supplierName: "", billNumber: "", billDate: today,
    dueDate: "", category: "OTHER", description: "",
    amount: "", vatAmount: "0", notes: "",
  })
  const [form, setForm] = useState(initForm())
  const f = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["ap-bills"] })
    onRefreshSummary()
  }

  const { data: page, isLoading } = useQuery({
    queryKey: ["ap-bills", statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "200" })
      if (statusFilter) params.set("status", statusFilter)
      const r = await apiClient.get(`/api/v1/ap/bills?${params}`)
      return r.data?.data ?? r.data
    },
  })

  const { data: bankAccounts = [] } = useQuery<any[]>({
    queryKey: ["bank-accounts"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/accounting/bank-accounts?size=50")
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
  })

  const createBill = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/ap/bills", body),
    onSuccess: (r: any) => {
      invalidate(); setShowCreate(false); setForm(initForm()); setError("")
      const created: Bill = r.data?.data ?? r.data
      // A duplicate warning is exactly that — a warning, not a failure.
      // The bill was created either way; this just needs to actually be
      // seen rather than close along with the modal and vanish.
      if (created?.possibleDuplicateWarning) setDuplicateWarning(created.possibleDuplicateWarning)
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create bill"),
  })

  const updateBill = useMutation({
    mutationFn: (body: any) => apiClient.put(`/api/v1/ap/bills/${selected?.id}`, body),
    onSuccess: (r: any) => { invalidate(); setShowEdit(false); setSelected(r.data?.data ?? r.data); setError("") },
    onError: (e: any) => setError(e.response?.data?.message || "Update failed"),
  })

  const approveBill = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/ap/bills/${selected?.id}/approve`),
    onSuccess: (r: any) => { invalidate(); setShowApprove(false); setSelected(r.data?.data ?? r.data) },
    onError: (e: any) => setError(e.response?.data?.message || "Approval failed"),
  })

  const payBill = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/ap/bills/${selected?.id}/pay`, { paymentRef: payRef || null, bankAccountId: bankAccounts[0]?.id ?? null }),
    onSuccess: (r: any) => { invalidate(); setShowPayModal(false); setPayRef(""); setSelected(r.data?.data ?? r.data) },
    onError: (e: any) => setError(e.response?.data?.message || "Payment failed"),
  })

  const cancelBill = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/ap/bills/${selected?.id}/cancel`),
    onSuccess: (r: any) => { invalidate(); setShowCancel(false); setSelected(r.data?.data ?? r.data) },
    onError: (e: any) => setError(e.response?.data?.message || "Cancel failed"),
  })

  const uploadPop = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/ap/bills/${selected?.id}/pop`, { fileBase64: popFile, fileName: popName }),
    onSuccess: (r: any) => { invalidate(); setShowPopUpload(false); setPopFile(""); setPopName(""); setSelected(r.data?.data ?? r.data) },
    onError: (e: any) => setError(e.response?.data?.message || "Upload failed"),
  })

  const sendRemittance = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/ap/bills/${selected?.id}/send-remittance`),
    onSuccess: () => setRemittanceNotice("Remittance email sent."),
    onError: (e: any) => setError(e.response?.data?.message || "Failed to send remittance email"),
  })

  const uploadAttach = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/ap/bills/${selected?.id}/attachment`, { fileBase64: attFile, fileName: attName }),
    onSuccess: (r: any) => { invalidate(); setShowAttachUpload(false); setAttFile(""); setAttName(""); setSelected(r.data?.data ?? r.data) },
    onError: (e: any) => setError(e.response?.data?.message || "Upload failed"),
  })

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>, setB64: (s: string) => void, setN: (s: string) => void) => {
    const file = e.target.files?.[0]; if (!file) return
    const reader = new FileReader()
    reader.onload = () => { setB64((reader.result as string).split(",")[1]); setN(file.name) }
    reader.readAsDataURL(file)
  }

  const exportCSV = () => {
    const bills: Bill[] = page?.content ?? page ?? []
    const headers = ["Bill #","Supplier","Bill Date","Due Date","Category","Description","Amount","VAT","Total","Status","Payment Ref"]
    const rows = bills.filter(b => !catFilter || b.category === catFilter)
      .filter(b => !search || b.supplierName.toLowerCase().includes(search.toLowerCase()) || b.billNumber.toLowerCase().includes(search.toLowerCase()))
      .map(b => [b.billNumber, b.supplierName, fmtDate(b.billDate), fmtDate(b.dueDate), b.category, `"${b.description}"`, Number(b.amount).toFixed(2), Number(b.vatAmount).toFixed(2), Number(b.totalAmount).toFixed(2), b.status, b.paymentRef ?? ""])
    const csv = [headers, ...rows].map(r => r.join(",")).join("\n")
    const a = document.createElement("a"); a.href = "data:text/csv;charset=utf-8," + encodeURIComponent(csv); a.download = `ap-bills-${today}.csv`; a.click()
  }

  const allBills: Bill[] = page?.content ?? page ?? []
  const filtered = allBills.filter(b => {
    if (catFilter && b.category !== catFilter) return false
    if (search && !b.supplierName.toLowerCase().includes(search.toLowerCase()) && !b.billNumber.toLowerCase().includes(search.toLowerCase()) && !b.description.toLowerCase().includes(search.toLowerCase())) return false
    return true
  })

  const vatTotal = (amount: string) => {
    const a = parseFloat(amount) || 0
    const v = parseFloat(form.vatAmount) || 0
    return a + v
  }

  return (
    <div>
      <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>

      {duplicateWarning && (
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12,
          padding: "12px 16px", background: "#FFFBEB", border: "1.5px solid #FDE68A", borderRadius: 10,
          marginBottom: 14 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <AlertTriangle size={16} color="#D97706" />
            <span style={{ fontSize: 13, color: "#92400E" }}>{duplicateWarning}</span>
          </div>
          <button onClick={() => setDuplicateWarning("")}
            style={{ background: "none", border: "none", cursor: "pointer", color: "#D97706", display: "flex" }}>
            <X size={14} />
          </button>
        </div>
      )}

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
          {["", "DRAFT", "APPROVED", "OVERDUE", "PAID", "CANCELLED"].map(s => {
            const cfg = STATUS[s]; const active = statusFilter === s
            return (
              <button key={s} onClick={() => setStatus(s)}
                style={{ padding: "6px 13px", borderRadius: 20, fontSize: 12, cursor: "pointer", fontWeight: active ? 700 : 500, border: `1.5px solid ${active && cfg ? cfg.border : "#E2E8F0"}`, background: active && cfg ? cfg.bg : "#fff", color: active && cfg ? cfg.color : "#64748B", display: "flex", alignItems: "center", gap: 5 }}>
                {s && cfg && <span style={{ width: 6, height: 6, borderRadius: "50%", background: cfg.dot }} />}
                {s ? cfg.label : "All bills"}
              </button>
            )
          })}
          <div style={{ position: "relative" as const }}>
            <Search size={13} style={{ position: "absolute" as const, left: 9, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search bills..."
              style={{ paddingLeft: 28, padding: "7px 10px 7px 28px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", width: 180 }} />
          </div>
          <select value={catFilter} onChange={e => setCat(e.target.value)}
            style={{ padding: "7px 10px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", background: "#fff" }}>
            <option value="">All categories</option>
            {CATEGORIES.map(c => <option key={c} value={c}>{CAT_LABELS[c]}</option>)}
          </select>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={exportCSV} style={btnS}><Download size={13} /> Export</button>
          <button onClick={() => { setShowCreate(true); setError("") }} style={btnP}><Plus size={14} /> Add Bill</button>
        </div>
      </div>

      {/* Bills table */}
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 48, color: "#94A3B8" }}>Loading bills...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px" }}>
          <FileText size={40} style={{ marginBottom: 12, color: "#CBD5E1" }} />
          <div style={{ fontWeight: 700, color: "#475569", fontSize: 15, marginBottom: 6 }}>No bills found</div>
          <button onClick={() => setShowCreate(true)} style={{ ...btnP, marginTop: 8 }}><Plus size={14} /> Add first bill</button>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" as const, fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Supplier / Bill", "Category", "Bill Date", "Due Date", "Amount", "Status", ""].map(h => (
                  <th key={h} style={{ padding: "10px 16px", textAlign: "left" as const, fontSize: 11, fontWeight: 700, color: "#64748B", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((b, i) => {
                const sc = STATUS[b.status] ?? STATUS.DRAFT
                const urgency = b.overdue ? "#FEF2F2" : b.daysUntilDue <= 7 && !["PAID","CANCELLED"].includes(b.status) ? "#FFFBEB" : "#fff"
                return (
                  <tr key={b.id} onClick={() => setSelected(b)}
                    style={{ background: i % 2 === 0 ? urgency : urgency === "#fff" ? "#FAFAFA" : urgency, cursor: "pointer", transition: "background 0.1s" }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = "#F0F9FF"}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = i % 2 === 0 ? urgency : urgency === "#fff" ? "#FAFAFA" : urgency}>
                    <td style={{ padding: "12px 16px" }}>
                      <div style={{ fontWeight: 700, color: "#0F172A" }}>{b.supplierName}</div>
                      <div style={{ fontSize: 11, color: "#94A3B8", display: "flex", alignItems: "center", gap: 6, marginTop: 1 }}>
                        #{b.billNumber}
                        {b.hasAttachment && <Paperclip size={10} color="#94A3B8" />}
                        {b.hasPop && <CheckCircle size={10} color="#0D9488" />}
                      </div>
                    </td>
                    <td style={{ padding: "12px 16px", fontSize: 12, color: "#64748B" }}>{CAT_LABELS[b.category] ?? b.category}</td>
                    <td style={{ padding: "12px 16px", fontSize: 12, color: "#64748B" }}>{fmtDate(b.billDate)}</td>
                    <td style={{ padding: "12px 16px" }}>
                      <div style={{ fontSize: 12, color: b.overdue ? "#DC2626" : b.daysUntilDue <= 7 && !["PAID","CANCELLED"].includes(b.status) ? "#D97706" : "#64748B", fontWeight: b.overdue ? 700 : 400 }}>
                        {fmtDate(b.dueDate)}
                      </div>
                      {!["PAID","CANCELLED"].includes(b.status) && (
                        <div style={{ fontSize: 10, color: b.overdue ? "#DC2626" : "#94A3B8" }}>
                          {b.overdue ? `${Math.abs(b.daysUntilDue)}d overdue` : `${b.daysUntilDue}d left`}
                        </div>
                      )}
                    </td>
                    <td style={{ padding: "12px 16px" }}>
                      <div style={{ fontWeight: 800, color: "#0F172A" }}>{fmtR(b.totalAmount)}</div>
                      {b.vatAmount > 0 && <div style={{ fontSize: 10, color: "#94A3B8" }}>excl. VAT {fmtR(b.amount)}</div>}
                    </td>
                    <td style={{ padding: "12px 16px" }}>
                      <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                        <span style={{ width: 5, height: 5, borderRadius: "50%", background: sc.dot }} />{sc.label}
                      </span>
                    </td>
                    <td style={{ padding: "12px 16px" }}>
                      <div style={{ display: "flex", gap: 4, alignItems: "center" }}>
                        {["DRAFT","SECOND_APPROVAL"].includes(b.status) && (
                          <button onClick={e => { e.stopPropagation(); setSelected(b); setShowApprove(true) }}
                            style={{ padding: "4px 10px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 6, fontSize: 11, fontWeight: 700, cursor: "pointer", display: "flex", alignItems: "center", gap: 3 }}>
                            <CheckCircle size={10} /> {b.status === "SECOND_APPROVAL" ? "Approve (2nd)" : "Approve"}
                          </button>
                        )}
                        {["APPROVED","OVERDUE"].includes(b.status) && !b.batchId && (
                          <button onClick={e => { e.stopPropagation(); setSelected(b); setShowPayModal(true) }}
                            style={{ padding: "4px 10px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 6, fontSize: 11, fontWeight: 700, cursor: "pointer", display: "flex", alignItems: "center", gap: 3 }}>
                            <DollarSign size={10} /> Pay
                          </button>
                        )}
                        {b.batchId && <span style={{ fontSize: 10, color: "#7C3AED", fontWeight: 600 }}>In batch</span>}
                        <ChevronRight size={14} color="#94A3B8" />
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
            {filtered.length > 1 && (
              <tfoot>
                <tr style={{ background: "#F8FAFC", borderTop: "1px solid #E2E8F0" }}>
                  <td colSpan={4} style={{ padding: "10px 16px", fontSize: 12, color: "#64748B", fontWeight: 600 }}>{filtered.length} bills</td>
                  <td style={{ padding: "10px 16px", fontWeight: 800, color: "#0F172A" }}>{fmtR(filtered.reduce((s, b) => s + Number(b.totalAmount), 0))}</td>
                  <td colSpan={2} />
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      )}

      {/* ── Bill detail slide-over ── */}
      {selected && !showApprove && !showCancel && !showPayModal && !showPopUpload && !showAttachUpload && !showEdit && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "flex-end", zIndex: 1000 }}>
          <div style={{ background: "#fff", width: 500, height: "100%", overflowY: "auto", boxShadow: "-8px 0 40px rgba(0,0,0,0.15)", padding: 28 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
              <div>
                <div style={{ fontWeight: 800, fontSize: 17, color: "#0F172A", marginBottom: 5 }}>{selected.supplierName}</div>
                <div style={{ display: "flex", gap: 8 }}>
                  {(() => { const sc = STATUS[selected.status] ?? STATUS.DRAFT; return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: "2px 9px", borderRadius: 20, fontSize: 12, fontWeight: 700 }}><span style={{ width: 5, height: 5, borderRadius: "50%", background: sc.dot }} />{sc.label}</span> })()}
                  <span style={{ fontSize: 12, color: "#94A3B8" }}>#{selected.billNumber}</span>
                </div>
              </div>
              <button onClick={() => { setSelected(null); setRemittanceNotice("") }} style={{ background: "#F1F5F9", border: "none", borderRadius: "50%", width: 30, height: 30, display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer", color: "#64748B" }}><X size={14} /></button>
            </div>

            {/* Amount hero */}
            <div style={{ background: "#F8FAFC", borderRadius: 12, padding: "18px 20px", marginBottom: 20, textAlign: "center" as const, border: "1px solid #E2E8F0" }}>
              <div style={{ fontSize: 32, fontWeight: 900, color: "#0F172A", letterSpacing: "-0.03em" }}>{fmtR(selected.totalAmount)}</div>
              {selected.vatAmount > 0 && <div style={{ fontSize: 12, color: "#64748B", marginTop: 4 }}>{fmtR(selected.amount)} excl. VAT + {fmtR(selected.vatAmount)} VAT</div>}
              <div style={{ fontSize: 13, color: "#64748B", marginTop: 4 }}>{CAT_LABELS[selected.category] ?? selected.category} · Due {fmtDate(selected.dueDate)}</div>
            </div>

            {/* Overdue alert */}
            {selected.overdue && (
              <div style={{ marginBottom: 14, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 9, display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "#DC2626", fontWeight: 600 }}>
                <AlertTriangle size={14} /> {Math.abs(selected.daysUntilDue)} days overdue
              </div>
            )}

            {/* Metadata */}
            <div style={{ display: "flex", flexDirection: "column", gap: 0, marginBottom: 20 }}>
              {[
                ["Supplier",     selected.supplierName],
                ["Bill number",  selected.billNumber],
                ["Bill date",    fmtDate(selected.billDate)],
                ["Due date",     fmtDate(selected.dueDate)],
                ["Category",     CAT_LABELS[selected.category] ?? selected.category],
                ["Description",  selected.description],
                ["Payment ref",  selected.paymentRef ?? "—"],
                ["Paid",         selected.paidAt ? fmtDT(selected.paidAt) : "—"],
              ].map(([k, v]) => (
                <div key={k} style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid #F1F5F9", fontSize: 13 }}>
                  <span style={{ color: "#94A3B8", fontWeight: 600, minWidth: 110 }}>{k}</span>
                  <span style={{ color: "#374151", fontWeight: 500, textAlign: "right" as const }}>{v}</span>
                </div>
              ))}
            </div>

            {selected.notes && (
              <div style={{ marginBottom: 14, padding: "12px 14px", background: "#FFFBEB", borderRadius: 9, border: "1px solid #FDE68A", fontSize: 13, color: "#374151" }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#D97706", marginBottom: 4, textTransform: "uppercase", letterSpacing: "0.06em" }}>Notes</div>
                {selected.notes}
              </div>
            )}

            {/* Evidence */}
            <div style={{ display: "flex", gap: 10, marginBottom: 20 }}>
              <button onClick={() => setShowAttachUpload(true)} style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "9px", border: `1.5px solid ${selected.hasAttachment ? "#86EFAC" : "#E2E8F0"}`, borderRadius: 9, background: selected.hasAttachment ? "#F0FDF4" : "#fff", fontSize: 12, cursor: "pointer", color: selected.hasAttachment ? "#166534" : "#64748B", fontWeight: 600 }}>
                <Paperclip size={13} /> {selected.hasAttachment ? "Invoice uploaded" : "Upload invoice"}
              </button>
              {["PAID"].includes(selected.status) && (
                <button onClick={() => setShowPopUpload(true)} style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "9px", border: `1.5px solid ${selected.hasPop ? "#86EFAC" : "#E2E8F0"}`, borderRadius: 9, background: selected.hasPop ? "#F0FDF4" : "#fff", fontSize: 12, cursor: "pointer", color: selected.hasPop ? "#166534" : "#64748B", fontWeight: 600 }}>
                  <Upload size={13} /> {selected.hasPop ? "POP uploaded" : "Upload POP"}
                </button>
              )}
            </div>

            {["PAID"].includes(selected.status) && (
              <div style={{ marginBottom: 20 }}>
                <button onClick={() => { setError(""); setRemittanceNotice(""); sendRemittance.mutate() }}
                  disabled={sendRemittance.isPending}
                  style={{ width: "100%", display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "9px", border: "1.5px solid #DDD6FE", borderRadius: 9, background: "#F5F3FF", fontSize: 12, cursor: "pointer", color: "#7C3AED", fontWeight: 600 }}>
                  <Mail size={13} /> {sendRemittance.isPending ? "Sending..." : "Email remittance advice to supplier"}
                </button>
                {remittanceNotice && (
                  <div style={{ marginTop: 8, padding: "7px 12px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, color: "#166534" }}>{remittanceNotice}</div>
                )}
              </div>
            )}

            {error && <div style={{ marginBottom: 12, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}

            {/* Actions */}
            <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
              {selected.status === "DRAFT" && <>
                <button onClick={() => { setShowEdit(true); setForm({ supplierName: selected.supplierName, billNumber: selected.billNumber, billDate: selected.billDate, dueDate: selected.dueDate, category: selected.category, description: selected.description, amount: String(selected.amount), vatAmount: String(selected.vatAmount), notes: selected.notes ?? "" }); setError("") }}
                  style={{ ...btnS, fontSize: 12, padding: "8px 12px" }}><Edit3 size={12} /> Edit</button>
                <button onClick={() => setShowApprove(true)}
                  style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "10px", background: "#DCFCE7", color: "#166634", border: "1px solid #86EFAC", borderRadius: 9, fontSize: 13, fontWeight: 700, cursor: "pointer" }}>
                  <CheckCircle size={14} /> Approve bill
                </button>
              </>}
              {selected.status === "SECOND_APPROVAL" && (
                <button onClick={() => setShowApprove(true)}
                  style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "10px", background: "#F5F3FF", color: "#7C3AED", border: "1.5px solid #DDD6FE", borderRadius: 9, fontSize: 13, fontWeight: 700, cursor: "pointer" }}>
                  <CheckCircle size={14} /> Give second approval
                </button>
              )}
              {["APPROVED","OVERDUE"].includes(selected.status) && !selected.batchId && (
                <button onClick={() => setShowPayModal(true)}
                  style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "10px", background: "#EFF6FF", color: "#1D4ED8", border: "1.5px solid #BFDBFE", borderRadius: 9, fontSize: 13, fontWeight: 700, cursor: "pointer" }}>
                  <DollarSign size={14} /> Mark as paid
                </button>
              )}
              {selected.batchId && (
                <div style={{ width: "100%", padding: "10px 14px", background: "#F5F3FF", border: "1px solid #DDD6FE", borderRadius: 9, fontSize: 13, color: "#7C3AED", fontWeight: 600 }}>
                  Included in EFT batch — payment handled via batch
                </div>
              )}
              {!["PAID","CANCELLED"].includes(selected.status) && (
                <button onClick={() => setShowCancel(true)}
                  style={{ padding: "10px 14px", background: "#F8FAFC", color: "#94A3B8", border: "1.5px solid #E2E8F0", borderRadius: 9, fontSize: 13, cursor: "pointer" }}>
                  <XCircle size={14} />
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Create / Edit bill modal */}
      {(showCreate || showEdit) && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1100, padding: 20, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 620, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 25px 80px rgba(0,0,0,0.25)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>{showEdit ? "Edit Bill" : "Add Supplier Bill"}</h3>
              <button onClick={() => { setShowCreate(false); setShowEdit(false); setError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Supplier name *</label>
                <input autoFocus value={form.supplierName} onChange={e => f("supplierName", e.target.value)} placeholder="Eskom, Sasol, Rand Water..." style={inp} />
              </div>
              <div>
                <label style={lbl}>Bill / invoice number *</label>
                <input value={form.billNumber} onChange={e => f("billNumber", e.target.value)} placeholder="INV-2026-0001" style={inp} />
              </div>
              <div>
                <label style={lbl}>Category *</label>
                <select value={form.category} onChange={e => f("category", e.target.value)} style={{ ...inp, background: "#fff" }}>
                  {CATEGORIES.map(c => <option key={c} value={c}>{CAT_LABELS[c]}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Bill date *</label>
                <input type="date" value={form.billDate} onChange={e => f("billDate", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Due date *</label>
                <input type="date" value={form.dueDate} onChange={e => f("dueDate", e.target.value)} style={inp} />
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Description *</label>
                <input value={form.description} onChange={e => f("description", e.target.value)} placeholder="Monthly electricity — Sandton office, floor 3" style={inp} />
              </div>
              <div>
                <label style={lbl}>Amount excl. VAT (R) *</label>
                <input type="number" min="0.01" step="0.01" value={form.amount} onChange={e => f("amount", e.target.value)} placeholder="0.00" style={inp} />
              </div>
              <div>
                <label style={lbl}>VAT amount (R)</label>
                <input type="number" min="0" step="0.01" value={form.vatAmount} onChange={e => f("vatAmount", e.target.value)} placeholder="0.00" style={inp} />
              </div>
              {form.amount && (
                <div style={{ gridColumn: "1/-1", padding: "10px 14px", background: "#F0F9FF", borderRadius: 8, border: "1px solid #BAE6FD", fontSize: 13 }}>
                  <strong>Total incl. VAT: {fmtR(vatTotal(form.amount))}</strong>
                </div>
              )}
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Notes</label>
                <textarea value={form.notes} onChange={e => f("notes", e.target.value)} rows={2} placeholder="Purchase order number, contract reference..." style={{ ...inp, resize: "vertical" as const, fontFamily: "inherit" }} />
              </div>
            </div>
            {error && <div style={{ marginTop: 12, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 22 }}>
              <button onClick={() => { setShowCreate(false); setShowEdit(false); setError("") }} style={btnS}>Cancel</button>
              <button
                disabled={!form.supplierName || !form.billNumber || !form.dueDate || !form.amount || createBill.isPending || updateBill.isPending}
                onClick={() => {
                  const body = { supplierName: form.supplierName, billNumber: form.billNumber, billDate: form.billDate, dueDate: form.dueDate, category: form.category, description: form.description, amount: parseFloat(form.amount), vatAmount: parseFloat(form.vatAmount) || 0, notes: form.notes || null }
                  showEdit ? updateBill.mutate(body) : createBill.mutate(body)
                }}
                style={{ ...btnP, opacity: (!form.supplierName || !form.billNumber || !form.amount) ? 0.5 : 1 }}>
                {(createBill.isPending || updateBill.isPending) ? "Saving..." : showEdit ? "Save changes" : <><FileText size={13} /> Add bill</>}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Pay modal */}
      {showPayModal && selected && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1100, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 18 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Mark as paid</h3>
              <button onClick={() => setShowPayModal(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={18} /></button>
            </div>
            <div style={{ padding: "14px", background: "#F8FAFC", borderRadius: 9, marginBottom: 16, textAlign: "center" as const }}>
              <div style={{ fontSize: 24, fontWeight: 800, color: "#0F172A" }}>{fmtR(selected.totalAmount)}</div>
              <div style={{ fontSize: 12, color: "#64748B" }}>{selected.supplierName} · #{selected.billNumber}</div>
            </div>
            <div>
              <label style={lbl}>Payment reference (optional)</label>
              <input value={payRef} onChange={e => setPayRef(e.target.value)} placeholder="EFT ref, cheque number, transaction ID..." style={inp} autoFocus />
            </div>
            {bankAccounts.length > 0 && (
              <div style={{ marginTop: 12, padding: "8px 12px", background: "#EFF6FF", borderRadius: 8, fontSize: 12, color: "#1D4ED8" }}>
                Payment from: {bankAccounts[0]?.accountName ?? "Primary bank account"}
              </div>
            )}
            {error && <div style={{ marginTop: 10, padding: "8px 12px", background: "#FEF2F2", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowPayModal(false)} style={btnS}>Cancel</button>
              <button onClick={() => payBill.mutate()} disabled={payBill.isPending}
                style={{ ...btnP, background: "#166534", opacity: payBill.isPending ? 0.6 : 1 }}>
                <DollarSign size={13} /> {payBill.isPending ? "Marking..." : "Mark paid"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* File upload modals */}
      {(showAttachUpload || showPopUpload) && selected && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1100, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 18 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>{showAttachUpload ? "Upload invoice document" : "Upload proof of payment"}</h3>
              <button onClick={() => { setShowAttachUpload(false); setShowPopUpload(false) }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={18} /></button>
            </div>
            <label style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 10, padding: "28px", border: `2px dashed ${(showAttachUpload ? attFile : popFile) ? "#0D9488" : "#E2E8F0"}`, borderRadius: 10, cursor: "pointer", background: (showAttachUpload ? attFile : popFile) ? "#F0FDF9" : "#F9FAFB" }}>
              <input type="file" style={{ display: "none" }} onChange={e => showAttachUpload ? handleFile(e, setAttFile, setAttName) : handleFile(e, setPopFile, setPopName)} />
              {(showAttachUpload ? attFile : popFile)
                ? <><CheckCircle size={24} color="#0D9488" /><span style={{ fontSize: 13, color: "#0D9488", fontWeight: 600 }}>{showAttachUpload ? attName : popName}</span></>
                : <><Upload size={24} color="#94A3B8" /><span style={{ fontSize: 13, color: "#64748B" }}>Click to select PDF or image</span></>}
            </label>
            {error && <div style={{ marginTop: 10, padding: "8px 12px", background: "#FEF2F2", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => { setShowAttachUpload(false); setShowPopUpload(false) }} style={btnS}>Cancel</button>
              <button disabled={!(showAttachUpload ? attFile : popFile) || uploadAttach.isPending || uploadPop.isPending}
                onClick={() => showAttachUpload ? uploadAttach.mutate() : uploadPop.mutate()}
                style={{ ...btnP, opacity: !(showAttachUpload ? attFile : popFile) ? 0.5 : 1 }}>
                <Upload size={13} /> Upload
              </button>
            </div>
          </div>
        </div>
      )}

      {showApprove && selected && (
        <ConfirmModal
          title={selected.status === "SECOND_APPROVAL" ? "Give second approval?" : "Approve bill?"}
          message={
            selected.status === "SECOND_APPROVAL"
              ? `This bill (${selected.supplierName}, #${selected.billNumber}, ${fmtR(selected.totalAmount)}) already has one approval and needs a second, different person to confirm it. If you gave the first approval yourself, this will be rejected. Approving now posts the journal entry (DR Expense / CR Accounts Payable).`
              : `Approve ${selected.supplierName} bill #${selected.billNumber} for ${fmtR(selected.totalAmount)}? ${selected.totalAmount > 10000 ? "This exceeds the second-approval threshold — a different person will need to approve it again before the journal posts." : "A journal entry (DR Expense / CR Accounts Payable) will be posted automatically."}`
          }
          confirmLabel={selected.status === "SECOND_APPROVAL" ? "Give second approval" : "Approve bill"}
          loading={approveBill.isPending} onConfirm={() => approveBill.mutate()} onCancel={() => setShowApprove(false)} />
      )}
      {showCancel && selected && (
        <ConfirmModal title="Cancel bill?" message={`Cancel bill #${selected.billNumber} from ${selected.supplierName}? This cannot be undone.`} confirmLabel="Cancel bill" danger loading={cancelBill.isPending} onConfirm={() => cancelBill.mutate()} onCancel={() => setShowCancel(false)} />
      )}
    </div>
  )
}
