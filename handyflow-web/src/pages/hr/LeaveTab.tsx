import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Calendar, ChevronDown, ChevronUp } from "lucide-react"

interface LeaveRequest {
  id: string
  employeeId: string
  employeeName: string
  leaveType: string
  startDate: string
  endDate: string
  daysRequested: number
  reason: string
  status: string
  rejectionReason: string | null
  createdAt: string
}

interface Employee { id: string; fullName: string }

const STATUS_STYLE: Record<string, { color: string; bg: string }> = {
  PENDING:  { color: "#D97706", bg: "#FFFBEB" },
  APPROVED: { color: "#166534", bg: "#DCFCE7" },
  REJECTED: { color: "#DC2626", bg: "#FEF2F2" },
}

const LEAVE_TYPES = ["ANNUAL", "SICK", "FAMILY_RESPONSIBILITY", "MATERNITY", "PATERNITY", "UNPAID", "STUDY"]

export default function LeaveTab() {
  const qc = useQueryClient()
  const [statusFilter, setStatus] = useState("")
  const [showCreate, setShowCreate] = useState(false)
  const [expandedEmp, setExpandedEmp] = useState<string | null>(null)
  const [error, setError] = useState("")

  const [form, setForm] = useState({
    employeeId: "", leaveType: "ANNUAL",
    startDate: "", endDate: "", reason: "",
  })

  const { data: page, isLoading } = useQuery({
    queryKey: ["leave-requests", statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "50" })
      if (statusFilter) params.set("status", statusFilter)
      const r = await apiClient.get(`/api/v1/hr/leave-requests?${params}`)
      return r.data
    },
  })

  const { data: employees = [] } = useQuery<Employee[]>({
    queryKey: ["hr-employees-list"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/hr/employees?size=200")
      return r.data?.content || []
    },
  })

  const { data: leaveBalances } = useQuery({
    queryKey: ["leave-balances", expandedEmp],
    queryFn: async () => {
      if (!expandedEmp) return null
      const r = await apiClient.get(`/api/v1/hr/employees/${expandedEmp}/leave-balances`)
      return r.data
    },
    enabled: !!expandedEmp,
  })

  const submitLeave = useMutation({
    mutationFn: ({ empId, body }: { empId: string; body: any }) =>
      apiClient.post(`/api/v1/hr/employees/${empId}/leave-requests`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["leave-requests"] }); setShowCreate(false); resetForm() },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to submit leave"),
  })

  const approveLeave = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/hr/leave-requests/${id}/approve`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["leave-requests"] }),
  })

  const rejectLeave = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/hr/leave-requests/${id}/reject`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["leave-requests"] }),
  })

  const resetForm = () => { setForm({ employeeId: "", leaveType: "ANNUAL", startDate: "", endDate: "", reason: "" }); setError("") }
  const requests: LeaveRequest[] = page?.content || []

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20, gap: 10 }}>
        <div style={{ display: "flex", gap: 8 }}>
          {["", "PENDING", "APPROVED", "REJECTED"].map(s => (
            <button key={s} onClick={() => setStatus(s)} style={filterBtn(statusFilter === s)}>
              {s || "All"}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowCreate(true); setError("") }} style={btnPrimary}><Plus size={15} /> Submit Leave</button>
      </div>

      {/* Leave balance by employee */}
      {employees.length > 0 && (
        <div style={{ marginBottom: 24 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 10 }}>Leave Balances by Employee</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {employees.slice(0, 10).map(emp => (
              <div key={emp.id} style={{ border: "1px solid #E2E8F0", borderRadius: 8, overflow: "hidden" }}>
                <div
                  onClick={() => setExpandedEmp(expandedEmp === emp.id ? null : emp.id)}
                  style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 16px", cursor: "pointer", background: expandedEmp === emp.id ? "#F8FAFC" : "#fff" }}
                >
                  <span style={{ fontWeight: 500, fontSize: 13, color: "#0F172A" }}>{emp.fullName}</span>
                  {expandedEmp === emp.id ? <ChevronUp size={15} color="#94A3B8" /> : <ChevronDown size={15} color="#94A3B8" />}
                </div>
                {expandedEmp === emp.id && leaveBalances && (
                  <div style={{ padding: "12px 16px", background: "#FAFAFA", borderTop: "1px solid #E2E8F0" }}>
                    <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                      {(leaveBalances as any[]).map((b: any) => (
                        <div key={b.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, padding: "10px 14px", minWidth: 120 }}>
                          <div style={{ fontSize: 10, fontWeight: 600, color: "#94A3B8", marginBottom: 4 }}>{b.leaveType}</div>
                          <div style={{ fontSize: 18, fontWeight: 700, color: "#0D9488" }}>{Number(b.availableDays).toFixed(1)}</div>
                          <div style={{ fontSize: 10, color: "#94A3B8" }}>{Number(b.takenDays).toFixed(1)} taken / {Number(b.entitledDays).toFixed(1)} total</div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Leave requests list */}
      <div style={{ fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 10 }}>Leave Requests</div>
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
      ) : requests.length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8" }}>
          <Calendar size={36} style={{ marginBottom: 10, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No leave requests</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {requests.map(req => {
            const style = STATUS_STYLE[req.status] || { color: "#475569", bg: "#F8FAFC" }
            return (
              <div key={req.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "14px 18px", border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff" }}>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                    <span style={{ fontWeight: 600, fontSize: 14, color: "#0F172A" }}>{req.employeeName}</span>
                    <span style={{ background: style.bg, color: style.color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{req.status}</span>
                    <span style={{ background: "#F0FDF4", color: "#166534", padding: "2px 8px", borderRadius: 20, fontSize: 11 }}>{req.leaveType}</span>
                  </div>
                  <div style={{ fontSize: 12, color: "#64748B" }}>
                    {req.startDate} → {req.endDate} · {Number(req.daysRequested).toFixed(1)} days
                    {req.reason && ` · "${req.reason}"`}
                  </div>
                  {req.rejectionReason && (
                    <div style={{ fontSize: 12, color: "#DC2626", marginTop: 2 }}>Reason: {req.rejectionReason}</div>
                  )}
                </div>
                {req.status === "PENDING" && (
                  <div style={{ display: "flex", gap: 8 }}>
                    <button onClick={() => approveLeave.mutate(req.id)}
                      style={{ padding: "6px 14px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 6, fontSize: 12, cursor: "pointer" }}>
                      Approve
                    </button>
                    <button onClick={() => rejectLeave.mutate(req.id)}
                      style={{ padding: "6px 14px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 6, fontSize: 12, cursor: "pointer" }}>
                      Reject
                    </button>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Submit leave modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 480, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Submit Leave Request</h3>
              <button onClick={() => { setShowCreate(false); resetForm() }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Employee *">
                  <select value={form.employeeId} onChange={e => setForm(f => ({ ...f, employeeId: e.target.value }))} style={inputStyle}>
                    <option value="">Select employee...</option>
                    {employees.map(e => <option key={e.id} value={e.id}>{e.fullName}</option>)}
                  </select>
                </Field>
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Leave Type *">
                  <select value={form.leaveType} onChange={e => setForm(f => ({ ...f, leaveType: e.target.value }))} style={inputStyle}>
                    {LEAVE_TYPES.map(t => <option key={t} value={t}>{t.replace("_", " ")}</option>)}
                  </select>
                </Field>
              </div>
              <Field label="Start Date *"><input type="date" value={form.startDate} onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} style={inputStyle} /></Field>
              <Field label="End Date *"><input type="date" value={form.endDate} onChange={e => setForm(f => ({ ...f, endDate: e.target.value }))} style={inputStyle} /></Field>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Reason"><textarea value={form.reason} onChange={e => setForm(f => ({ ...f, reason: e.target.value }))} rows={2} placeholder="Optional reason..." style={{ ...inputStyle, resize: "vertical" as const }} /></Field>
              </div>
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => { setShowCreate(false); resetForm() }} style={btnCancel}>Cancel</button>
              <button
                onClick={() => submitLeave.mutate({ empId: form.employeeId, body: { leaveType: form.leaveType, startDate: form.startDate, endDate: form.endDate, reason: form.reason || null } })}
                disabled={!form.employeeId || !form.startDate || !form.endDate || submitLeave.isPending}
                style={btnPrimary}
              >
                {submitLeave.isPending ? "Submitting..." : "Submit Request"}
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
