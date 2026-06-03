// src/pages/property/PaymentsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, AlertTriangle, CheckCircle, Clock, CreditCard } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtR   = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtD   = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const MONTH_NAMES = ["","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]

const STATUS_CFG: Record<string, { color: string; bg: string; border: string; icon: React.ElementType }> = {
  PAID:     { color: "#166534", bg: "#DCFCE7", border: "#86EFAC", icon: CheckCircle  },
  PARTIAL:  { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A", icon: Clock        },
  PENDING:  { color: "#1D4ED8", bg: "#EFF6FF", border: "#BFDBFE", icon: Clock        },
  OVERDUE:  { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA", icon: AlertTriangle },
  WAIVED:   { color: "#64748B", bg: "#F1F5F9", border: "#E2E8F0", icon: CheckCircle  },
}

const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none", background: "#fff" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

export default function PaymentsTab() {
  const qc = useQueryClient()
  const [selectedLease, setLease] = useState<any | null>(null)
  const [showCreate, setCreate]   = useState(false)
  const [showRecord, setRecord]   = useState<any | null>(null)
  const [error, setError]         = useState("")

  const now = new Date()
  const [createForm, setCreateForm] = useState({
    periodYear: String(now.getFullYear()), periodMonth: String(now.getMonth() + 1),
    amountDue: "", dueDate: "",
  })
  const [recordForm, setRecordForm] = useState({
    amountPaid: "", paidDate: new Date().toISOString().split("T")[0],
    paymentMethod: "EFT", reference: "",
  })

  const { data: leases = [] } = useQuery<any[]>({
    queryKey: ["leases", "ACTIVE"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/property/leases?status=ACTIVE&size=200")),
  })

  const { data: outstanding = [] } = useQuery<any[]>({
    queryKey: ["outstanding"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/property/payments/outstanding")),
  })

  const { data: payments = [], isLoading } = useQuery<any[]>({
    queryKey: ["payments", selectedLease?.id],
    enabled: !!selectedLease,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/property/leases/${selectedLease.id}/payments?size=50`)),
  })

  const createPayment = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/property/leases/${selectedLease?.id}/payments`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["payments"] }); qc.invalidateQueries({ queryKey: ["outstanding"] }); setCreate(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create payment record"),
  })

  const recordPayment = useMutation({
    mutationFn: ({ paymentId, body }: { paymentId: string; body: any }) =>
      apiClient.post(`/api/v1/property/leases/${selectedLease?.id}/payments/${paymentId}/record`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["payments"] }); qc.invalidateQueries({ queryKey: ["outstanding"] }); setRecord(null); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to record payment"),
  })

  const os = outstanding as any[]
  const totalArrears = os.reduce((s, p) => s + Math.max(0, Number(p.amountDue) - Number(p.amountPaid)), 0)

  return (
    <div>
      {/* Arrears banner */}
      {os.length > 0 && (
        <div style={{ marginBottom: 20, padding: "14px 18px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 10, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <AlertTriangle size={16} color="#DC2626" />
            <span style={{ fontWeight: 700, fontSize: 14, color: "#DC2626" }}>{os.length} outstanding payment{os.length !== 1 ? "s" : ""}</span>
            <span style={{ fontSize: 13, color: "#B91C1C" }}>· Total arrears: <strong>{fmtR(totalArrears)}</strong></span>
          </div>
        </div>
      )}

      <div style={{ display: "flex", gap: 16 }}>
        {/* Lease selector */}
        <div style={{ width: 280, flexShrink: 0 }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 10 }}>Active leases</div>
          <div style={{ display: "flex", flexDirection: "column" as const, gap: 6 }}>
            {(leases as any[]).map(l => (
              <button key={l.id} onClick={() => setLease(l)}
                style={{ width: "100%", textAlign: "left" as const, padding: "10px 14px", border: `1px solid ${selectedLease?.id === l.id ? "#1B3A6B" : "#E2E8F0"}`, background: selectedLease?.id === l.id ? "#EEF2FF" : "#fff", borderRadius: 9, cursor: "pointer" }}>
                <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{l.lesseeName}</div>
                <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{fmtR(l.monthlyRent)}/mo · Day {l.paymentDay}</div>
              </button>
            ))}
            {(leases as any[]).length === 0 && <div style={{ fontSize: 13, color: "#94A3B8", padding: "12px 0" }}>No active leases</div>}
          </div>

          {/* Outstanding summary */}
          {os.length > 0 && (
            <div style={{ marginTop: 20 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: "#DC2626", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 10 }}>Overdue</div>
              {os.slice(0, 8).map((p: any) => (
                <div key={p.id} style={{ padding: "8px 12px", marginBottom: 6, background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 7 }}>
                  <div style={{ fontWeight: 600, fontSize: 12, color: "#DC2626" }}>{MONTH_NAMES[p.periodMonth]} {p.periodYear}</div>
                  <div style={{ fontSize: 11, color: "#94A3B8" }}>{fmtR(Number(p.amountDue) - Number(p.amountPaid))} outstanding</div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Payment ledger */}
        <div style={{ flex: 1 }}>
          {!selectedLease ? (
            <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
              <CreditCard size={36} style={{ marginBottom: 12, opacity: 0.3 }} />
              <div>Select a lease to view the payment ledger</div>
            </div>
          ) : (
            <>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
                <div>
                  <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{selectedLease.lesseeName}</div>
                  <div style={{ fontSize: 12, color: "#64748B" }}>{fmtR(selectedLease.monthlyRent)}/mo · Due on {selectedLease.paymentDay}{["st","nd","rd"][selectedLease.paymentDay-1]||"th"}</div>
                </div>
                <button onClick={() => { setCreate(true); setCreateForm({ periodYear: String(now.getFullYear()), periodMonth: String(now.getMonth()+1), amountDue: selectedLease.monthlyRent, dueDate: "" }); setError("") }}
                  style={{ display: "flex", alignItems: "center", gap: 6, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "8px 14px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                  <Plus size={13} /> Add period
                </button>
              </div>

              {isLoading ? (
                <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
              ) : (payments as any[]).length === 0 ? (
                <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", border: "1px dashed #E2E8F0", borderRadius: 10, fontSize: 13 }}>
                  No payment records yet. Add a period to start tracking.
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column" as const, gap: 6 }}>
                  {(payments as any[]).map((p: any) => {
                    const cfg = STATUS_CFG[p.status] ?? STATUS_CFG.PENDING
                    const Icon = cfg.icon
                    const balance = Number(p.amountDue) - Number(p.amountPaid)
                    return (
                      <div key={p.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", border: `1px solid ${cfg.border}`, borderLeft: `3px solid ${cfg.color}`, borderRadius: 8, background: "#fff" }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                          <Icon size={16} color={cfg.color} />
                          <div>
                            <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{MONTH_NAMES[p.periodMonth]} {p.periodYear}</div>
                            <div style={{ fontSize: 11, color: "#64748B" }}>Due {fmtD(p.dueDate)}{p.paidDate && ` · Paid ${fmtD(p.paidDate)}`}</div>
                          </div>
                        </div>
                        <div style={{ display: "flex", alignItems: "center", gap: 14, flexShrink: 0 }}>
                          <div style={{ textAlign: "right" as const }}>
                            <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{fmtR(p.amountPaid)} / {fmtR(p.amountDue)}</div>
                            {balance > 0 && <div style={{ fontSize: 11, color: cfg.color, fontWeight: 600 }}>{fmtR(balance)} outstanding</div>}
                          </div>
                          <span style={{ background: cfg.bg, color: cfg.color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{p.status}</span>
                          {(p.status === "PENDING" || p.status === "PARTIAL" || p.status === "OVERDUE") && (
                            <button onClick={() => { setRecord(p); setRecordForm({ amountPaid: String(Number(p.amountDue) - Number(p.amountPaid)), paidDate: new Date().toISOString().split("T")[0], paymentMethod: "EFT", reference: "" }); setError("") }}
                              style={{ padding: "5px 10px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 6, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                              Record
                            </button>
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* Create payment record modal */}
      {showCreate && selectedLease && (
        <ModalShell title="Add Payment Period" onClose={() => setCreate(false)}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <div>
              <label style={lbl}>Year *</label>
              <input type="number" value={createForm.periodYear} onChange={e => setCreateForm(f => ({ ...f, periodYear: e.target.value }))} style={inp} />
            </div>
            <div>
              <label style={lbl}>Month *</label>
              <select value={createForm.periodMonth} onChange={e => setCreateForm(f => ({ ...f, periodMonth: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                {MONTH_NAMES.slice(1).map((m, i) => <option key={i+1} value={String(i+1)}>{m}</option>)}
              </select>
            </div>
            <div>
              <label style={lbl}>Amount due (R) *</label>
              <input type="number" value={createForm.amountDue} onChange={e => setCreateForm(f => ({ ...f, amountDue: e.target.value }))} style={inp} />
            </div>
            <div>
              <label style={lbl}>Due date *</label>
              <input type="date" value={createForm.dueDate} onChange={e => setCreateForm(f => ({ ...f, dueDate: e.target.value }))} style={inp} />
            </div>
          </div>
          {error && <ErrBox msg={error} />}
          <ModalFoot onCancel={() => setCreate(false)} loading={createPayment.isPending}
            disabled={!createForm.amountDue || !createForm.dueDate} label="Add Period"
            onSubmit={() => createPayment.mutate({
              periodYear: parseInt(createForm.periodYear), periodMonth: parseInt(createForm.periodMonth),
              amountDue: parseFloat(createForm.amountDue), dueDate: createForm.dueDate,
            })} />
        </ModalShell>
      )}

      {/* Record payment modal */}
      {showRecord && selectedLease && (
        <ModalShell title={`Record Payment — ${MONTH_NAMES[showRecord.periodMonth]} ${showRecord.periodYear}`} onClose={() => setRecord(null)}>
          <div style={{ marginBottom: 16, padding: "10px 14px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 8, fontSize: 13, color: "#1D4ED8" }}>
            Amount due: <strong>{fmtR(showRecord.amountDue)}</strong> · Already paid: <strong>{fmtR(showRecord.amountPaid)}</strong>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <div>
              <label style={lbl}>Amount paid (R) *</label>
              <input autoFocus type="number" value={recordForm.amountPaid} onChange={e => setRecordForm(f => ({ ...f, amountPaid: e.target.value }))} style={inp} />
            </div>
            <div>
              <label style={lbl}>Payment date *</label>
              <input type="date" value={recordForm.paidDate} onChange={e => setRecordForm(f => ({ ...f, paidDate: e.target.value }))} style={inp} />
            </div>
            <div>
              <label style={lbl}>Payment method</label>
              <select value={recordForm.paymentMethod} onChange={e => setRecordForm(f => ({ ...f, paymentMethod: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                {["EFT","CASH","CARD","DEBIT_ORDER","OTHER"].map(m => <option key={m} value={m}>{m.replace("_"," ")}</option>)}
              </select>
            </div>
            <div>
              <label style={lbl}>Reference</label>
              <input value={recordForm.reference} onChange={e => setRecordForm(f => ({ ...f, reference: e.target.value }))} placeholder="Bank ref / transaction ID" style={inp} />
            </div>
          </div>
          {error && <ErrBox msg={error} />}
          <ModalFoot onCancel={() => setRecord(null)} loading={recordPayment.isPending}
            disabled={!recordForm.amountPaid || !recordForm.paidDate} label="Record Payment"
            onSubmit={() => recordPayment.mutate({ paymentId: showRecord.id, body: {
              amountPaid: parseFloat(recordForm.amountPaid), paidDate: recordForm.paidDate,
              paymentMethod: recordForm.paymentMethod, reference: recordForm.reference || null,
            }})} />
        </ModalShell>
      )}
    </div>
  )
}

function ModalShell({ title, onClose, children }: any) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 520, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}
function ModalFoot({ onCancel, onSubmit, loading, disabled, label }: any) {
  return (
    <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
      <button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
      <button onClick={onSubmit} disabled={disabled || loading}
        style={{ padding: "9px 22px", background: disabled ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
        {loading ? "Saving..." : label}
      </button>
    </div>
  )
}
function ErrBox({ msg }: { msg: string }) {
  return <div style={{ marginTop: 12, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{msg}</div>
}
