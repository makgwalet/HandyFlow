// src/pages/hr/LeaveTab.tsx
// KEY FIXES:
// 1. API unwrap — was page?.content, needs r.data?.data?.content
// 2. rejectLeave now opens a modal to capture the reason (was firing immediately with no reason)
// 3. leaveBalances unwrap fixed
// 4. Working days preview added
// 5. Leave type colour badges
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Calendar, ChevronDown, ChevronUp, Clock, CheckCircle, XCircle, AlertCircle } from "lucide-react"

// FIX: correct ApiResponse<Page<T>> unwrap
const unwrap     = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const unwrapList = (r: any): any[] => { const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : (d?.content ?? []) }
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const LEAVE_TYPES = ["ANNUAL","SICK","FAMILY_RESPONSIBILITY","MATERNITY","PATERNITY","STUDY","UNPAID"]
const LEAVE_CFG: Record<string, { color: string; bg: string; label: string }> = {
  ANNUAL:                { color: "var(--hf-info-text)", bg: "var(--hf-info-soft)", label: "Annual"        },
  SICK:                  { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", label: "Sick"          },
  FAMILY_RESPONSIBILITY: { color: "var(--hf-violet-text)", bg: "var(--hf-violet-soft)", label: "Family Resp."  },
  MATERNITY:             { color: "var(--hf-orange-text)", bg: "var(--hf-orange-soft)", label: "Maternity"     },
  PATERNITY:             { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", label: "Paternity"     },
  STUDY:                 { color: "var(--hf-accent-text)", bg: "var(--hf-success-soft)", label: "Study"         },
  UNPAID:                { color: "var(--hf-text-faint)", bg: "var(--hf-surface-muted)", label: "Unpaid"        },
}
const STATUS_CFG: Record<string, { color: string; bg: string; icon: React.ElementType }> = {
  PENDING:  { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", icon: Clock        },
  APPROVED: { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", icon: CheckCircle  },
  REJECTED: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", icon: XCircle      },
}

export default function LeaveTab() {
  const qc = useQueryClient()
  const [statusFilter, setStatus] = useState("ALL")
  const [typeFilter, setType]     = useState("ALL")
  const [showCreate, setShowCreate] = useState(false)
  const [expandedEmp, setExpandedEmp] = useState<string | null>(null)
  const [rejectingId, setRejectingId] = useState<string | null>(null)
  const [rejectReason, setRejectReason] = useState("")
  const [error, setError]           = useState("")
  const [form, setForm]             = useState({ employeeId: "", leaveType: "ANNUAL", startDate: "", endDate: "", reason: "" })

  // FIX: unwrap ApiResponse<Page<LeaveRequest>>
  const { data: requests = [], isLoading } = useQuery<any[]>({
    queryKey: ["leave-requests", statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "200" })
      if (statusFilter !== "ALL") params.set("status", statusFilter)
      return unwrap(await apiClient.get(`/api/v1/hr/leave-requests?${params}`))
    },
  })

  // FIX: unwrap ApiResponse<Page<Employee>>
  const { data: employees = [] } = useQuery<any[]>({
    queryKey: ["hr-employees"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/hr/employees?size=200&status=ACTIVE")),
  })

  // FIX: unwrap ApiResponse<List<LeaveBalance>>
  const { data: leaveBalances = [] } = useQuery<any[]>({
    queryKey: ["leave-balances", expandedEmp],
    queryFn: async () => expandedEmp ? unwrapList(await apiClient.get(`/api/v1/hr/employees/${expandedEmp}/leave-balances`)) : [],
    enabled: !!expandedEmp,
  })

  const submitLeave = useMutation({
    mutationFn: ({ empId, body }: { empId: string; body: any }) =>
      apiClient.post(`/api/v1/hr/employees/${empId}/leave-requests`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["leave-requests"] }); setShowCreate(false); setForm({ employeeId: "", leaveType: "ANNUAL", startDate: "", endDate: "", reason: "" }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to submit leave"),
  })

  const approveLeave = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/hr/leave-requests/${id}/approve`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["leave-requests"] }),
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to approve"),
  })

  // FIX: was firing immediately with no reason — now sends reason via query param
  const rejectLeave = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      apiClient.post(`/api/v1/hr/leave-requests/${id}/reject?reason=${encodeURIComponent(reason)}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["leave-requests"] }); setRejectingId(null); setRejectReason("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to reject"),
  })

  // Working days preview — excludes weekends
  const workingDays = form.startDate && form.endDate ? (() => {
    const start = new Date(form.startDate), end = new Date(form.endDate)
    let count = 0
    const cur = new Date(start)
    while (cur <= end) { if (cur.getDay() > 0 && cur.getDay() < 6) count++; cur.setDate(cur.getDate() + 1) }
    return count
  })() : 0

  const filtered = (requests as any[]).filter(r => typeFilter === "ALL" || r.leaveType === typeFilter)
  const pending  = (requests as any[]).filter(r => r.status === "PENDING")

  const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none" }
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }

  return (
    <div>
      {pending.length > 0 && (
        <div style={{ marginBottom: 18, padding: "12px 16px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <Clock size={16} color="#D97706" style={{ flexShrink: 0 }} />
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-warning-text)" }}>{pending.length} request{pending.length !== 1 ? "s" : ""} awaiting approval</div>
            <div style={{ fontSize: 12, color: "var(--hf-warning-text-deep)" }}>{pending.slice(0, 2).map((r: any) => `${r.employeeName} (${r.leaveType})`).join(" · ")}</div>
          </div>
        </div>
      )}

      {/* Leave balances section */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 10 }}>Leave Balances</div>
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          {(employees as any[]).slice(0, 8).map((emp: any) => (
            <div key={emp.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 8, overflow: "hidden" }}>
              <div onClick={() => setExpandedEmp(expandedEmp === emp.id ? null : emp.id)}
                style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", cursor: "pointer", background: expandedEmp === emp.id ? "var(--hf-surface-muted)" : "var(--hf-surface)" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <div style={{ width: 28, height: 28, borderRadius: "50%", background: "var(--hf-primary)", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <span style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-on-solid)" }}>{emp.firstName[0]}{emp.lastName[0]}</span>
                  </div>
                  <span style={{ fontWeight: 600, fontSize: 13, color: "var(--hf-text)" }}>{emp.fullName}</span>
                  <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{emp.employeeNumber}</span>
                </div>
                {expandedEmp === emp.id ? <ChevronUp size={14} color="#94A3B8" /> : <ChevronDown size={14} color="#94A3B8" />}
              </div>
              {expandedEmp === emp.id && (
                <div style={{ padding: "12px 14px", background: "var(--hf-surface-muted)", borderTop: "1px solid var(--hf-border)" }}>
                  <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                    {(leaveBalances as any[]).map((b: any) => {
                      const cfg = LEAVE_CFG[b.leaveType]
                      const avail = Number(b.availableDays)
                      return (
                        <div key={b.id} style={{ background: "var(--hf-surface)", border: `1px solid ${avail <= 0 ? "#FECACA" : "#E2E8F0"}`, borderRadius: 8, padding: "10px 14px", minWidth: 110 }}>
                          <div style={{ fontSize: 10, fontWeight: 700, color: cfg?.color ?? "var(--hf-text-muted)", marginBottom: 4, textTransform: "uppercase" as const }}>{cfg?.label ?? b.leaveType}</div>
                          <div style={{ fontSize: 18, fontWeight: 700, color: avail <= 0 ? "var(--hf-danger-text)" : "var(--hf-accent-text)" }}>{avail.toFixed(1)}</div>
                          <div style={{ fontSize: 10, color: "var(--hf-text-faint)" }}>{Number(b.takenDays).toFixed(1)} taken · {Number(b.entitledDays).toFixed(1)} total</div>
                          {Number(b.pendingDays) > 0 && <div style={{ fontSize: 10, color: "var(--hf-warning-text)", marginTop: 2 }}>{Number(b.pendingDays).toFixed(1)} pending</div>}
                        </div>
                      )
                    })}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Leave requests */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {["ALL","PENDING","APPROVED","REJECTED"].map(s => (
            <button key={s} onClick={() => setStatus(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: statusFilter === s ? 600 : 400, background: statusFilter === s ? "var(--hf-primary)" : "var(--hf-surface-sunken)", color: statusFilter === s ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
              {s === "ALL" ? "All statuses" : s}
            </button>
          ))}
          <div style={{ width: 1, background: "var(--hf-surface-strong)", margin: "0 4px" }} />
          {["ALL",...LEAVE_TYPES].map(t => (
            <button key={t} onClick={() => setType(t)}
              style={{ padding: "4px 10px", borderRadius: 20, fontSize: 11, cursor: "pointer", border: "none", fontWeight: typeFilter === t ? 600 : 400,
                background: typeFilter === t ? (LEAVE_CFG[t]?.color ?? "var(--hf-primary)") : "var(--hf-surface-sunken)",
                color: typeFilter === t ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
              {t === "ALL" ? "All types" : LEAVE_CFG[t]?.label ?? t}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowCreate(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Submit Request
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading leave requests...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "var(--hf-text-faint)" }}>
          <Calendar size={36} style={{ marginBottom: 10, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No leave requests</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {filtered.map((req: any) => {
            const sCfg  = STATUS_CFG[req.status] ?? STATUS_CFG.PENDING
            const tCfg  = LEAVE_CFG[req.leaveType] ?? LEAVE_CFG.ANNUAL
            const SIcon = sCfg.icon
            return (
              <div key={req.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "14px 18px", border: `1px solid ${req.status === "PENDING" ? "#FDE68A" : "#E2E8F0"}`, borderLeft: `4px solid ${sCfg.color}`, borderRadius: 10, background: "var(--hf-surface)" }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, flexWrap: "wrap" }}>
                    <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{req.employeeName}</span>
                    <span style={{ background: tCfg.bg, color: tCfg.color, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{tCfg.label}</span>
                    <span style={{ display: "flex", alignItems: "center", gap: 3, background: sCfg.bg, color: sCfg.color, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                      <SIcon size={9} />{req.status}
                    </span>
                  </div>
                  <div style={{ fontSize: 13, color: "var(--hf-text-tertiary)" }}>
                    {fmtDate(req.startDate)} → {fmtDate(req.endDate)} · <strong>{Number(req.daysRequested).toFixed(1)} working days</strong>
                    {req.reason && ` · "${req.reason}"`}
                  </div>
                  {req.rejectionReason && <div style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 2 }}>Rejected: {req.rejectionReason}</div>}
                </div>
                {req.status === "PENDING" && (
                  <div style={{ display: "flex", gap: 8, flexShrink: 0, marginLeft: 12 }}>
                    <button onClick={() => approveLeave.mutate(req.id)}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", border: "1px solid var(--hf-success-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                      <CheckCircle size={12} /> Approve
                    </button>
                    {/* FIX: opens reject modal to capture reason instead of firing immediately */}
                    <button onClick={() => { setRejectingId(req.id); setRejectReason(""); setError("") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                      <XCircle size={12} /> Reject
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
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 500, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>Submit Leave Request</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Employee *</label>
                <select value={form.employeeId} onChange={e => setForm(f => ({ ...f, employeeId: e.target.value }))} style={{ ...inp, background: "var(--hf-surface)" }}>
                  <option value="">Select employee...</option>
                  {(employees as any[]).map(e => <option key={e.id} value={e.id}>{e.fullName} ({e.employeeNumber})</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Leave Type *</label>
                <select value={form.leaveType} onChange={e => setForm(f => ({ ...f, leaveType: e.target.value }))} style={{ ...inp, background: "var(--hf-surface)" }}>
                  {LEAVE_TYPES.map(t => <option key={t} value={t}>{LEAVE_CFG[t]?.label ?? t}</option>)}
                </select>
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div><label style={lbl}>Start Date *</label><input type="date" value={form.startDate} onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} style={inp} /></div>
                <div><label style={lbl}>End Date *</label><input type="date" value={form.endDate} min={form.startDate} onChange={e => setForm(f => ({ ...f, endDate: e.target.value }))} style={inp} /></div>
              </div>
              {workingDays > 0 && (
                <div style={{ padding: "9px 12px", background: "var(--hf-info-soft)", border: "1px solid var(--hf-info-border)", borderRadius: 7, fontSize: 13, color: "var(--hf-info-text)", fontWeight: 600 }}>
                  {workingDays} working days (weekends excluded)
                </div>
              )}
              <div>
                <label style={lbl}>Reason <span style={{ fontWeight: 400, color: "var(--hf-text-faint)" }}>(optional)</span></label>
                <textarea value={form.reason} onChange={e => setForm(f => ({ ...f, reason: e.target.value }))} rows={2} style={{ ...inp, resize: "vertical" as const }} />
              </div>
            </div>
            {error && <div style={{ marginTop: 12, padding: "9px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, fontSize: 13, color: "var(--hf-danger-text)", display: "flex", alignItems: "center", gap: 7 }}><AlertCircle size={13} />{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
              <button onClick={() => submitLeave.mutate({ empId: form.employeeId, body: { leaveType: form.leaveType, startDate: form.startDate, endDate: form.endDate, reason: form.reason || null } })}
                disabled={!form.employeeId || !form.startDate || !form.endDate || submitLeave.isPending}
                style={{ padding: "9px 22px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {submitLeave.isPending ? "Submitting..." : "Submit Request"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Reject with reason modal */}
      {rejectingId && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1001, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <h3 style={{ margin: "0 0 16px", fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>Reject Leave Request</h3>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Rejection Reason *</label>
              <textarea value={rejectReason} autoFocus onChange={e => setRejectReason(e.target.value)} rows={3}
                placeholder="Explain why the leave cannot be approved..." style={{ ...inp, resize: "vertical" as const }} />
            </div>
            {error && <div style={{ marginBottom: 12, padding: "9px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => { setRejectingId(null); setRejectReason("") }} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
              <button onClick={() => rejectLeave.mutate({ id: rejectingId, reason: rejectReason })} disabled={!rejectReason || rejectLeave.isPending}
                style={{ padding: "9px 22px", background: !rejectReason ? "var(--hf-text-faint)" : "var(--hf-danger)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {rejectLeave.isPending ? "Rejecting..." : "Reject Request"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
