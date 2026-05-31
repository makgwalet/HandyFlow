import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { CreditCard, CheckCircle, X } from "lucide-react"

interface Lease { id: string; lesseeName: string; unitId: string; monthlyRent: number; status: string }
interface Unit { id: string; unitNumber: string }
interface Property { id: string; name: string; units: Unit[] }

interface Payment {
  id: string
  leaseId: string
  periodYear: number
  periodMonth: number
  amountDue: number
  amountPaid: number
  balance: number
  dueDate: string
  paidDate: string | null
  paymentMethod: string | null
  reference: string | null
  status: "PENDING" | "PAID" | "PARTIAL" | "OVERDUE"
  createdAt: string
}

const PAYMENT_STATUS: Record<string, { color: string; bg: string; label: string }> = {
  PENDING: { color: "#D97706", bg: "#FFFBEB", label: "Pending" },
  PAID:    { color: "#166534", bg: "#DCFCE7", label: "Paid"    },
  PARTIAL: { color: "#1D4ED8", bg: "#EFF6FF", label: "Partial" },
  OVERDUE: { color: "#DC2626", bg: "#FEF2F2", label: "Overdue" },
}

const MONTHS = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
const PAYMENT_METHODS = ["EFT","CASH","CARD","DEBIT_ORDER","OTHER"]

export default function PaymentsTab() {
  const qc = useQueryClient()
  const [selectedLease, setSelectedLease] = useState("")
  const [showRecord, setShowRecord] = useState<Payment | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [error, setError] = useState("")

  const [createForm, setCreateForm] = useState({ periodYear: new Date().getFullYear().toString(), periodMonth: (new Date().getMonth() + 1).toString(), amountDue: "", dueDate: "" })
  const [recordForm, setRecordForm] = useState({ amountPaid: "", paidDate: new Date().toISOString().split("T")[0], paymentMethod: "EFT", reference: "" })

  const { data: properties = [] } = useQuery({
    queryKey: ["properties"],
    queryFn: async () => { const res = await apiClient.get("/api/v1/property/properties?size=50"); return res.data.content as Property[] },
  })

  const { data: leases = [] } = useQuery({
    queryKey: ["leases"],
    queryFn: async () => { const res = await apiClient.get("/api/v1/property/leases?size=50"); return res.data.content as Lease[] },
  })

  const { data: payments = [], isLoading } = useQuery({
    queryKey: ["payments", selectedLease],
    queryFn: async () => {
      if (!selectedLease) return []
      const res = await apiClient.get(`/api/v1/property/leases/${selectedLease}/payments?size=50`)
      return res.data.content as Payment[]
    },
    enabled: !!selectedLease,
  })

  const createPayment = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/property/leases/${selectedLease}/payments`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["payments", selectedLease] }); setShowCreate(false); setCreateForm({ periodYear: new Date().getFullYear().toString(), periodMonth: (new Date().getMonth() + 1).toString(), amountDue: "", dueDate: "" }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create payment record"),
  })

  const recordPayment = useMutation({
    mutationFn: ({ leaseId, paymentId, body }: { leaseId: string; paymentId: string; body: any }) =>
      apiClient.post(`/api/v1/property/leases/${leaseId}/payments/${paymentId}/record`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["payments", selectedLease] }); setShowRecord(null); setRecordForm({ amountPaid: "", paidDate: new Date().toISOString().split("T")[0], paymentMethod: "EFT", reference: "" }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to record payment"),
  })

  const allUnits = properties.flatMap(p => p.units.map(u => ({ ...u, propertyName: p.name })))
  const unitMap = Object.fromEntries(allUnits.map(u => [u.id, u]))
  const selectedLeaseObj = leases.find(l => l.id === selectedLease)

  const fmtDate = (d: string) => new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
  const fmtR = (n: number) => `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <label style={{ fontSize: 14, fontWeight: 500, color: "#374151" }}>Select Lease:</label>
          <select value={selectedLease} onChange={e => setSelectedLease(e.target.value)} style={{ padding: "9px 14px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", minWidth: 300 }}>
            <option value="">Choose a lease...</option>
            {leases.filter(l => l.status === "ACTIVE").map(l => {
              const unit = unitMap[l.unitId]
              return <option key={l.id} value={l.id}>{l.lesseeName} — {unit ? `Unit ${unit.unitNumber}` : ""} · R {l.monthlyRent.toLocaleString()}/mo</option>
            })}
          </select>
        </div>
        {selectedLease && (
          <button onClick={() => setShowCreate(true)} style={btnPrimary}>
            + Create Payment Record
          </button>
        )}
      </div>

      {/* Lease summary */}
      {selectedLeaseObj && (
        <div style={{ display: "flex", gap: 16, marginBottom: 20, padding: "14px 18px", background: "#F0FDF4", border: "1px solid #BBF7D0", borderRadius: 10 }}>
          <div><div style={{ fontSize: 11, color: "#64748B", marginBottom: 2 }}>TENANT</div><div style={{ fontWeight: 700, color: "#0F172A" }}>{selectedLeaseObj.lesseeName}</div></div>
          <div style={{ width: 1, background: "#BBF7D0" }} />
          <div><div style={{ fontSize: 11, color: "#64748B", marginBottom: 2 }}>MONTHLY RENT</div><div style={{ fontWeight: 700, color: "#0D9488" }}>R {selectedLeaseObj.monthlyRent.toLocaleString()}</div></div>
          <div style={{ width: 1, background: "#BBF7D0" }} />
          <div><div style={{ fontSize: 11, color: "#64748B", marginBottom: 2 }}>RECORDS</div><div style={{ fontWeight: 700, color: "#1B3A6B" }}>{payments.length}</div></div>
          <div style={{ width: 1, background: "#BBF7D0" }} />
          <div><div style={{ fontSize: 11, color: "#64748B", marginBottom: 2 }}>OUTSTANDING</div><div style={{ fontWeight: 700, color: "#DC2626" }}>R {payments.filter(p => p.status !== "PAID").reduce((s, p) => s + p.balance, 0).toLocaleString()}</div></div>
        </div>
      )}

      {!selectedLease ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <CreditCard size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>Select a lease to view payments</div>
        </div>
      ) : isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading payments...</div>
      ) : payments.length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8" }}>No payment records yet. Create the first one.</div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Period","Amount Due","Amount Paid","Balance","Due Date","Paid Date","Status",""].map(h => (
                  <th key={h} style={{ padding: "11px 16px", textAlign: "left", fontWeight: 600, fontSize: 12, color: "#64748B", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {payments.map((p, i) => {
                const cfg = PAYMENT_STATUS[p.status] || PAYMENT_STATUS.PENDING
                return (
                  <tr key={p.id} style={{ borderBottom: i < payments.length - 1 ? "1px solid #F1F5F9" : "none" }}>
                    <td style={{ padding: "13px 16px", fontWeight: 600, color: "#0F172A" }}>
                      {MONTHS[p.periodMonth - 1]} {p.periodYear}
                    </td>
                    <td style={{ padding: "13px 16px", color: "#475569" }}>{fmtR(p.amountDue)}</td>
                    <td style={{ padding: "13px 16px", color: p.amountPaid > 0 ? "#166534" : "#94A3B8", fontWeight: p.amountPaid > 0 ? 600 : 400 }}>{fmtR(p.amountPaid)}</td>
                    <td style={{ padding: "13px 16px", color: p.balance > 0 ? "#DC2626" : "#166534", fontWeight: 600 }}>{fmtR(p.balance)}</td>
                    <td style={{ padding: "13px 16px", color: "#475569" }}>{fmtDate(p.dueDate)}</td>
                    <td style={{ padding: "13px 16px", color: "#475569" }}>{p.paidDate ? fmtDate(p.paidDate) : "—"}</td>
                    <td style={{ padding: "13px 16px" }}>
                      <span style={{ background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 12, fontWeight: 600 }}>{cfg.label}</span>
                    </td>
                    <td style={{ padding: "13px 16px" }}>
                      {p.status !== "PAID" && (
                        <button
                          onClick={() => { setShowRecord(p); setRecordForm(f => ({ ...f, amountPaid: p.amountDue.toString() })); setError("") }}
                          style={{ display: "flex", alignItems: "center", gap: 5, background: "#166534", color: "#fff", border: "none", borderRadius: 6, padding: "6px 12px", fontSize: 12, cursor: "pointer" }}
                        >
                          <CheckCircle size={12} /> Record
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Create Payment Record Modal */}
      {showCreate && selectedLeaseObj && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 460, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Create Payment Record</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={labelStyle}>Year *</label>
                <input type="number" value={createForm.periodYear} onChange={e => setCreateForm(f => ({ ...f, periodYear: e.target.value }))} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Month *</label>
                <select value={createForm.periodMonth} onChange={e => setCreateForm(f => ({ ...f, periodMonth: e.target.value }))} style={selectStyle}>
                  {MONTHS.map((m, i) => <option key={i + 1} value={i + 1}>{m}</option>)}
                </select>
              </div>
              <div>
                <label style={labelStyle}>Amount Due (R) *</label>
                <input type="number" value={createForm.amountDue} onChange={e => setCreateForm(f => ({ ...f, amountDue: e.target.value }))} placeholder={selectedLeaseObj.monthlyRent.toString()} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Due Date *</label>
                <input type="date" value={createForm.dueDate} onChange={e => setCreateForm(f => ({ ...f, dueDate: e.target.value }))} style={inputStyle} />
              </div>
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createPayment.mutate({ periodYear: Number(createForm.periodYear), periodMonth: Number(createForm.periodMonth), amountDue: Number(createForm.amountDue), dueDate: createForm.dueDate })} disabled={!createForm.amountDue || !createForm.dueDate || createPayment.isPending} style={submitBtn}>
                {createPayment.isPending ? "Creating..." : "Create Record"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Record Payment Modal */}
      {showRecord && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 460, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Record Payment — {MONTHS[showRecord.periodMonth - 1]} {showRecord.periodYear}</h3>
              <button onClick={() => setShowRecord(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ marginBottom: 14, padding: "10px 14px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, color: "#475569" }}>
              Outstanding: <strong style={{ color: "#DC2626" }}>{fmtR(showRecord.balance)}</strong>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={labelStyle}>Amount Paid (R) *</label>
                <input type="number" value={recordForm.amountPaid} onChange={e => setRecordForm(f => ({ ...f, amountPaid: e.target.value }))} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Date Paid *</label>
                <input type="date" value={recordForm.paidDate} onChange={e => setRecordForm(f => ({ ...f, paidDate: e.target.value }))} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Payment Method</label>
                <select value={recordForm.paymentMethod} onChange={e => setRecordForm(f => ({ ...f, paymentMethod: e.target.value }))} style={selectStyle}>
                  {PAYMENT_METHODS.map(m => <option key={m} value={m}>{m.replace("_", " ")}</option>)}
                </select>
              </div>
              <div>
                <label style={labelStyle}>Reference</label>
                <input value={recordForm.reference} onChange={e => setRecordForm(f => ({ ...f, reference: e.target.value }))} placeholder="TM-JUNE-2026" style={inputStyle} />
              </div>
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowRecord(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => recordPayment.mutate({ leaseId: selectedLease, paymentId: showRecord.id, body: { amountPaid: Number(recordForm.amountPaid), paidDate: recordForm.paidDate, paymentMethod: recordForm.paymentMethod, reference: recordForm.reference || null } })} disabled={!recordForm.amountPaid || !recordForm.paidDate || recordPayment.isPending} style={submitBtn}>
                {recordPayment.isPending ? "Recording..." : "Record Payment"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const labelStyle: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }
const selectStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }
const cancelBtn: React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const submitBtn: React.CSSProperties = { padding: "9px 20px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 500, cursor: "pointer" }
