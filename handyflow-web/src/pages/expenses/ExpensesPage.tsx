import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, X, CheckCircle, XCircle, DollarSign,
  ChevronRight, Search, AlertCircle, Receipt,
} from "lucide-react"

interface ExpenseClaim {
  id: string
  claimNumber: string
  employeeId: string | null
  employeeName: string
  claimDate: string
  category: string
  description: string
  amount: number
  currency: string
  receiptUrl: string | null
  status: string
  rejectionReason: string | null
  journalEntryId: string | null
  notes: string | null
  approvedAt: string | null
  reimbursedAt: string | null
}

interface Employee { id: string; fullName: string }

const STATUS_STYLE: Record<string, { color: string; bg: string }> = {
  PENDING:     { color: "#D97706", bg: "#FFFBEB" },
  APPROVED:    { color: "#166534", bg: "#DCFCE7" },
  REJECTED:    { color: "#DC2626", bg: "#FEF2F2" },
  REIMBURSED:  { color: "#1D4ED8", bg: "#EFF6FF" },
}

const CATEGORIES = [
  "TRAVEL", "ACCOMMODATION", "MEALS", "FUEL", "STATIONERY",
  "EQUIPMENT", "COMMUNICATION", "TRAINING", "MAINTENANCE", "OTHER",
]

export function ExpensesPage() {
  const qc = useQueryClient()
  const [statusFilter, setStatus] = useState("")
  const [search, setSearch]       = useState("")
  const [showCreate, setShowCreate] = useState(false)
  const [selected, setSelected]     = useState<ExpenseClaim | null>(null)
  const [showReject, setShowReject] = useState(false)
  const [rejectReason, setRejectReason] = useState("")
  const [error, setError] = useState("")

  const today = new Date().toISOString().split("T")[0]
  const initForm = () => ({
    employeeName: "", employeeId: "",
    claimDate: today, category: "TRAVEL",
    description: "", amount: "", receiptUrl: "", notes: "",
  })
  const [form, setForm] = useState(initForm())
  const f = (k: keyof ReturnType<typeof initForm>, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: page, isLoading } = useQuery({
    queryKey: ["expenses", statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "50" })
      if (statusFilter) params.set("status", statusFilter)
      const r = await apiClient.get(`/api/v1/expenses?${params}`)
      return r.data
    },
  })

  const { data: monthlyTotal } = useQuery<number>({
    queryKey: ["expenses-monthly"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/expenses/summary/monthly")
      return r.data || 0
    },
  })

  const { data: employees = [] } = useQuery<Employee[]>({
    queryKey: ["hr-employees-list"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/hr/employees?size=200")
      return r.data?.content || []
    },
  })

  const submitClaim = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/expenses", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["expenses"] })
      qc.invalidateQueries({ queryKey: ["expenses-monthly"] })
      setShowCreate(false); setForm(initForm())
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to submit claim"),
  })

  const approveClaim = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/expenses/${id}/approve`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["expenses"] })
      qc.invalidateQueries({ queryKey: ["expenses-monthly"] })
      setSelected(null)
    },
  })

  const rejectClaim = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      apiClient.post(`/api/v1/expenses/${id}/reject`, { reason }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["expenses"] })
      setShowReject(false); setSelected(null); setRejectReason("")
    },
  })

  const reimburse = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/expenses/${id}/reimburse`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["expenses"] })
      setSelected(null)
    },
  })

  const claims: ExpenseClaim[] = (page?.content || []).filter((c: ExpenseClaim) =>
    !search || c.employeeName.toLowerCase().includes(search.toLowerCase()) ||
    c.description.toLowerCase().includes(search.toLowerCase()) ||
    c.category.toLowerCase().includes(search.toLowerCase())
  )

  const fmtR = (n: number) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
  const fmtDate = (d: string) => d ? new Date(d + "T00:00:00").toLocaleDateString("en-ZA") : "—"

  const pendingTotal = (page?.content || [])
    .filter((c: ExpenseClaim) => c.status === "PENDING")
    .reduce((s: number, c: ExpenseClaim) => s + Number(c.amount), 0)

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: "#0F172A", margin: "0 0 4px" }}>Expenses</h1>
        <p style={{ fontSize: 14, color: "#64748B", margin: 0 }}>Staff expense claims with approval workflow</p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: 24 }}>

        {/* Summary cards */}
        <div style={{ display: "flex", gap: 12, marginBottom: 24, flexWrap: "wrap" }}>
          <div style={{ background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 10, padding: "14px 20px" }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: "#D97706", marginBottom: 2 }}>PENDING APPROVAL</div>
            <div style={{ fontSize: 22, fontWeight: 700, color: "#D97706" }}>{fmtR(pendingTotal)}</div>
          </div>
          <div style={{ background: "#DCFCE7", border: "1px solid #86EFAC", borderRadius: 10, padding: "14px 20px" }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: "#166534", marginBottom: 2 }}>APPROVED THIS MONTH</div>
            <div style={{ fontSize: 22, fontWeight: 700, color: "#166534" }}>{fmtR(monthlyTotal || 0)}</div>
          </div>
          {["PENDING", "APPROVED", "REIMBURSED"].map(s => {
            const style = STATUS_STYLE[s]
            const count = (page?.content || []).filter((c: ExpenseClaim) => c.status === s).length
            return (
              <div key={s} style={{ background: style.bg, borderRadius: 10, padding: "14px 18px" }}>
                <div style={{ fontSize: 11, fontWeight: 600, color: style.color, marginBottom: 2 }}>{s}</div>
                <div style={{ fontSize: 22, fontWeight: 700, color: style.color }}>{count}</div>
              </div>
            )
          })}
        </div>

        {/* Toolbar */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 10, flexWrap: "wrap" }}>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            {["", "PENDING", "APPROVED", "REJECTED", "REIMBURSED"].map(s => (
              <button key={s} onClick={() => setStatus(s)} style={filterBtn(statusFilter === s)}>{s || "All"}</button>
            ))}
            <div style={{ position: "relative" }}>
              <Search size={13} style={{ position: "absolute", left: 9, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
              <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search..."
                style={{ padding: "6px 10px 6px 28px", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 13, width: 180 }} />
            </div>
          </div>
          <button onClick={() => { setShowCreate(true); setError("") }} style={btnPrimary}><Plus size={15} /> Submit Claim</button>
        </div>

        {/* Claims list */}
        {isLoading ? (
          <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading claims...</div>
        ) : claims.length === 0 ? (
          <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
            <Receipt size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
            <div style={{ fontWeight: 600, color: "#475569" }}>No expense claims found</div>
          </div>
        ) : (
          <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr style={{ background: "#F8FAFC" }}>
                  <th style={th}>Claim</th>
                  <th style={th}>Employee</th>
                  <th style={th}>Category</th>
                  <th style={th}>Date</th>
                  <th style={{ ...th, textAlign: "right" }}>Amount</th>
                  <th style={th}>Status</th>
                  <th style={th}></th>
                </tr>
              </thead>
              <tbody>
                {claims.map((c, i) => {
                  const style = STATUS_STYLE[c.status] || { color: "#475569", bg: "#F8FAFC" }
                  return (
                    <tr key={c.id} style={{ background: i % 2 === 0 ? "#fff" : "#FAFAFA", cursor: "pointer" }}
                      onClick={() => setSelected(c)}>
                      <td style={td}>
                        <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{c.description}</div>
                        <div style={{ fontSize: 11, color: "#94A3B8" }}>#{c.claimNumber}</div>
                      </td>
                      <td style={td}><span style={{ fontSize: 13, color: "#475569" }}>{c.employeeName}</span></td>
                      <td style={td}>
                        <span style={{ background: "#F1F5F9", color: "#475569", padding: "2px 8px", borderRadius: 20, fontSize: 11 }}>
                          {c.category}
                        </span>
                      </td>
                      <td style={td}><span style={{ fontSize: 13, color: "#64748B" }}>{fmtDate(c.claimDate)}</span></td>
                      <td style={{ ...td, textAlign: "right" }}>
                        <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{fmtR(c.amount)}</span>
                      </td>
                      <td style={td}>
                        <span style={{ background: style.bg, color: style.color, padding: "2px 10px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>
                          {c.status}
                        </span>
                      </td>
                      <td style={td}><ChevronRight size={16} color="#94A3B8" /></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Claim detail modal */}
      {selected && !showReject && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 500, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
              <div>
                <h3 style={{ margin: "0 0 4px", fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{selected.description}</h3>
                <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                  <span style={{ background: STATUS_STYLE[selected.status]?.bg, color: STATUS_STYLE[selected.status]?.color, padding: "2px 10px", borderRadius: 20, fontSize: 12, fontWeight: 600 }}>
                    {selected.status}
                  </span>
                  <span style={{ fontSize: 12, color: "#94A3B8" }}>#{selected.claimNumber}</span>
                </div>
              </div>
              <button onClick={() => setSelected(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 20 }}>
              {[
                ["Employee",    selected.employeeName],
                ["Category",    selected.category],
                ["Date",        fmtDate(selected.claimDate)],
                ["Amount",      fmtR(selected.amount)],
                ["Approved",    selected.approvedAt ? new Date(selected.approvedAt).toLocaleDateString("en-ZA") : "—"],
                ["Reimbursed",  selected.reimbursedAt ? new Date(selected.reimbursedAt).toLocaleDateString("en-ZA") : "—"],
              ].map(([label, value]) => (
                <div key={label as string}>
                  <div style={{ fontSize: 10, fontWeight: 600, color: "#94A3B8", marginBottom: 2 }}>{(label as string).toUpperCase()}</div>
                  <div style={{ fontSize: 13, color: "#0F172A", fontWeight: label === "Amount" ? 700 : 400 }}>{value as string}</div>
                </div>
              ))}
            </div>

            {selected.notes && (
              <div style={{ marginBottom: 16, padding: "10px 14px", background: "#F8FAFC", borderRadius: 8, fontSize: 13, color: "#475569" }}>
                <div style={{ fontSize: 10, fontWeight: 600, color: "#94A3B8", marginBottom: 3 }}>NOTES</div>
                {selected.notes}
              </div>
            )}

            {selected.rejectionReason && (
              <div style={{ marginBottom: 16, padding: "10px 14px", background: "#FEF2F2", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>
                <div style={{ fontSize: 10, fontWeight: 600, marginBottom: 3 }}>REJECTION REASON</div>
                {selected.rejectionReason}
              </div>
            )}

            {selected.receiptUrl && (
              <a href={selected.receiptUrl} target="_blank" rel="noreferrer"
                style={{ display: "inline-flex", alignItems: "center", gap: 5, marginBottom: 16, fontSize: 13, color: "#1D4ED8" }}>
                <Receipt size={13} /> View Receipt
              </a>
            )}

            {selected.journalEntryId && (
              <div style={{ marginBottom: 16, padding: "8px 12px", background: "#F0FDF4", borderRadius: 7, fontSize: 12, color: "#166534" }}>
                ✓ Journal entry posted to Accounting
              </div>
            )}

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
              {selected.status === "PENDING" && (
                <>
                  <button onClick={() => setShowReject(true)}
                    style={{ padding: "8px 16px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 7, fontSize: 13, cursor: "pointer" }}>
                    Reject
                  </button>
                  <button onClick={() => approveClaim.mutate(selected.id)} disabled={approveClaim.isPending}
                    style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 16px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 13, cursor: "pointer" }}>
                    <CheckCircle size={14} /> {approveClaim.isPending ? "Approving..." : "Approve"}
                  </button>
                </>
              )}
              {selected.status === "APPROVED" && (
                <button onClick={() => reimburse.mutate(selected.id)} disabled={reimburse.isPending}
                  style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 16px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 7, fontSize: 13, cursor: "pointer" }}>
                  <DollarSign size={14} /> {reimburse.isPending ? "Marking..." : "Mark Reimbursed"}
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Reject modal */}
      {showReject && selected && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1001 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#DC2626" }}>Reject Claim</h3>
              <button onClick={() => setShowReject(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <p style={{ margin: "0 0 16px", fontSize: 13, color: "#64748B" }}>
              Rejecting <strong>{selected.description}</strong> — {fmtR(selected.amount)}
            </p>
            <Field label="Reason *">
              <textarea value={rejectReason} onChange={e => setRejectReason(e.target.value)} rows={3}
                placeholder="Reason for rejection..." style={{ ...inputStyle, resize: "vertical" as const }} autoFocus />
            </Field>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowReject(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => rejectClaim.mutate({ id: selected.id, reason: rejectReason })}
                disabled={!rejectReason || rejectClaim.isPending}
                style={{ padding: "9px 18px", background: "#DC2626", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, cursor: "pointer" }}>
                {rejectClaim.isPending ? "Rejecting..." : "Reject Claim"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Submit claim modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 520, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Submit Expense Claim</h3>
              <button onClick={() => { setShowCreate(false); setForm(initForm()) }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              {/* Employee — from HR or manual */}
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Employee *">
                  {employees.length > 0 ? (
                    <select value={form.employeeId} onChange={e => {
                      const emp = employees.find(x => x.id === e.target.value)
                      setForm(p => ({ ...p, employeeId: e.target.value, employeeName: emp?.fullName || "" }))
                    }} style={inputStyle}>
                      <option value="">Select employee...</option>
                      {employees.map(e => <option key={e.id} value={e.id}>{e.fullName}</option>)}
                    </select>
                  ) : (
                    <input value={form.employeeName} onChange={e => f("employeeName", e.target.value)} placeholder="Employee name" style={inputStyle} />
                  )}
                </Field>
              </div>

              <Field label="Claim Date *">
                <input type="date" value={form.claimDate} onChange={e => f("claimDate", e.target.value)} style={inputStyle} />
              </Field>
              <Field label="Category *">
                <select value={form.category} onChange={e => f("category", e.target.value)} style={inputStyle}>
                  {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </Field>

              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Description *">
                  <input value={form.description} onChange={e => f("description", e.target.value)} placeholder="Team lunch, fuel for site visit, etc." style={inputStyle} />
                </Field>
              </div>

              <Field label="Amount (R) *">
                <input type="number" value={form.amount} onChange={e => f("amount", e.target.value)} placeholder="0.00" style={inputStyle} />
              </Field>
              <Field label="Receipt URL">
                <input value={form.receiptUrl} onChange={e => f("receiptUrl", e.target.value)} placeholder="https://..." style={inputStyle} />
              </Field>

              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Notes">
                  <textarea value={form.notes} onChange={e => f("notes", e.target.value)} rows={2}
                    placeholder="Additional context..." style={{ ...inputStyle, resize: "vertical" as const }} />
                </Field>
              </div>
            </div>

            <div style={{ marginTop: 12, padding: "10px 14px", background: "#F0FDF4", borderRadius: 8, fontSize: 12, color: "#166534" }}>
              Once approved, a journal entry will automatically be posted to Accounting.
            </div>

            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => { setShowCreate(false); setForm(initForm()) }} style={btnCancel}>Cancel</button>
              <button
                onClick={() => submitClaim.mutate({
                  employeeId: form.employeeId || null,
                  employeeName: form.employeeName,
                  claimDate: form.claimDate,
                  category: form.category,
                  description: form.description,
                  amount: parseFloat(form.amount),
                  receiptUrl: form.receiptUrl || null,
                  notes: form.notes || null,
                })}
                disabled={!form.employeeName || !form.description || !form.amount || submitClaim.isPending}
                style={btnPrimary}>
                {submitClaim.isPending ? "Submitting..." : "Submit Claim"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }}>{label}</label>{children}</div>
}

const filterBtn = (active: boolean): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 6, fontSize: 12, cursor: "pointer",
  border: active ? "1px solid #0D9488" : "1px solid #E2E8F0",
  background: active ? "#F0FDF4" : "#fff", color: active ? "#0D9488" : "#64748B", fontWeight: active ? 600 : 400,
})
const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const btnCancel: React.CSSProperties  = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff" }
const th: React.CSSProperties = { padding: "10px 16px", textAlign: "left", fontSize: 11, fontWeight: 600, color: "#64748B", letterSpacing: "0.05em", borderBottom: "1px solid #E2E8F0" }
const td: React.CSSProperties = { padding: "12px 16px", fontSize: 13, borderBottom: "1px solid #F1F5F9" }
