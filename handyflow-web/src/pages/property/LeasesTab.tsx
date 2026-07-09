// src/pages/property/LeasesTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, ChevronDown, ChevronUp, FileText, User, AlertTriangle, RefreshCw, TrendingUp } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtR   = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtD   = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const STATUS_CFG: Record<string, { color: string; bg: string; border: string }> = {
  ACTIVE:     { color: "#166534", bg: "#DCFCE7", border: "#86EFAC" },
  PENDING:    { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A" },
  EXPIRED:    { color: "#64748B", bg: "#F1F5F9", border: "#E2E8F0" },
  TERMINATED: { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA" },
}

const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none", background: "#fff" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

export default function LeasesTab({ initialFilter }: { initialFilter?: string }) {
  const qc = useQueryClient()
  // FIX: previously always started at "" (All) regardless of how this tab
  // was reached — clicking "Leases expiring soon" on the Dashboard just
  // switched to this tab with no filter applied, same generic list as
  // navigating here directly. initialFilter lets the Dashboard hand off
  // which view to land on.
  const [statusFilter, setStatus] = useState(initialFilter ?? "")
  const [expanded, setExpanded]   = useState<string | null>(null)
  const [showCreate, setCreate]   = useState(false)
  const [showRenew, setRenew]     = useState<any | null>(null)
  const [showEscalate, setEscalate] = useState<any | null>(null)
  const [showTerminate, setTerminate] = useState<any | null>(null)
  const [error, setError] = useState("")

  const INIT = () => ({
    unitId: "", lesseeName: "", lesseeIdNumber: "", lesseeEmail: "", lesseePhone: "",
    startDate: "", endDate: "", monthlyRent: "", depositAmount: "0",
    paymentDay: "1", escalationRate: "0", notes: "",
  })
  const [form, setForm] = useState(INIT())
  const [renewForm, setRenewForm]   = useState({ newEndDate: "", newMonthlyRent: "", newEscalationRate: "" })
  const [escalateForm, setEscForm]  = useState({ escalationPercent: "", newMonthlyRent: "" })
  const [terminateReason, setTermReason] = useState("")

  const { data: leases = [], isLoading } = useQuery<any[]>({
    queryKey: ["leases", statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "100" })
      // NEW: "Expiring soon" isn't a real lease status the backend knows
      // about (chk_lease_status only allows ACTIVE/PENDING/EXPIRED/
      // TERMINATED) — it's LeaseResponse's own already-computed
      // expiringSoon boolean, applied as a client-side filter over active
      // leases below, not sent as a query param.
      const backendStatus = statusFilter === "EXPIRING_SOON" ? "ACTIVE" : statusFilter
      if (backendStatus) params.set("status", backendStatus)
      return unwrap(await apiClient.get(`/api/v1/property/leases?${params}`))
    },
  })

  const visibleLeases = statusFilter === "EXPIRING_SOON"
    ? (leases as any[]).filter(l => l.expiringSoon)
    : (leases as any[])

  const { data: units = [] } = useQuery<any[]>({
    queryKey: ["units-all"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/property/units?size=200")),
  })

  const createLease = useMutation({
    mutationFn: ({ unitId, body }: { unitId: string; body: any }) =>
      apiClient.post(`/api/v1/property/units/${unitId}/leases`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["leases"] }); setCreate(false); setForm(INIT()); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create lease"),
  })

  const terminateLease = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      apiClient.post(`/api/v1/property/leases/${id}/terminate?reason=${encodeURIComponent(reason)}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["leases"] }); setTerminate(null); setTermReason(""); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to terminate lease"),
  })

  const renewLease = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/property/leases/${id}/renew`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["leases"] }); setRenew(null); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to renew lease"),
  })

  const escalateLease = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/property/leases/${id}/escalate`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["leases"] }); setEscalate(null); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to escalate rent"),
  })

  const vacantUnits = (units as any[]).filter(u => u.status === "VACANT")

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {[{ k: "", l: "All" },{ k: "ACTIVE", l: "Active" },{ k: "PENDING", l: "Pending" },{ k: "EXPIRED", l: "Expired" },{ k: "TERMINATED", l: "Terminated" },{ k: "EXPIRING_SOON", l: "Expiring soon" }].map(s => (
            <button key={s.k} onClick={() => setStatus(s.k)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none",
                background: statusFilter === s.k ? "#1B3A6B" : "#F1F5F9",
                color: statusFilter === s.k ? "#fff" : "#64748B", fontWeight: statusFilter === s.k ? 600 : 400 }}>
              {s.l}
            </button>
          ))}
        </div>
        <button onClick={() => { setCreate(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> New Lease
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
      ) : (visibleLeases).length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <FileText size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No leases found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {(visibleLeases).map(l => {
            const cfg    = STATUS_CFG[l.status] ?? STATUS_CFG.ACTIVE
            const isOpen = expanded === l.id
            return (
              <div key={l.id} style={{ border: `1px solid ${cfg.border}`, borderLeft: `3px solid ${cfg.color}`, borderRadius: 10, overflow: "hidden" }}>
                <div onClick={() => setExpanded(isOpen ? null : l.id)}
                  style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "14px 20px", cursor: "pointer", background: isOpen ? "#F8FAFC" : "#fff" }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, flexWrap: "wrap" }}>
                      <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{l.lesseeName}</span>
                      <span style={{ background: cfg.bg, color: cfg.color, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{l.status}</span>
                      {l.monthToMonth && <span style={{ background: "#F0FDF4", color: "#166534", padding: "1px 8px", borderRadius: 20, fontSize: 10, fontWeight: 600, border: "1px solid #86EFAC" }}>Month-to-month</span>}
                      {l.expiringSoon && <span style={{ background: "#FFFBEB", color: "#D97706", padding: "1px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700, border: "1px solid #FDE68A" }}>Expiring soon</span>}
                    </div>
                    <div style={{ fontSize: 12, color: "#64748B", display: "flex", gap: 14, flexWrap: "wrap" }}>
                      <span>{fmtD(l.startDate)} → {l.endDate ? fmtD(l.endDate) : "Ongoing"}</span>
                      <span style={{ fontWeight: 700, color: "#1B3A6B" }}>{fmtR(l.monthlyRent)}/mo</span>
                      {l.paymentDay && <span>Due {l.paymentDay}{["st","nd","rd"][l.paymentDay-1]||"th"}</span>}
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, flexShrink: 0 }}>
                    {isOpen ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                  </div>
                </div>

                {isOpen && (
                  <div style={{ borderTop: `1px solid ${cfg.border}`, padding: "16px 20px", background: "#FAFAFA" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 10, marginBottom: 16 }}>
                      {[
                        { l: "Monthly rent",    v: fmtR(l.monthlyRent)    },
                        { l: "Deposit",         v: fmtR(l.depositAmount)  },
                        { l: "Escalation",      v: l.escalationRate ? `${l.escalationRate}% pa` : "—" },
                        { l: "Payment day",     v: `${l.paymentDay}${["st","nd","rd"][l.paymentDay-1]||"th"} of month` },
                        { l: "Lessee email",    v: l.lesseeEmail ?? "—"   },
                        { l: "Lessee phone",    v: l.lesseePhone ?? "—"   },
                        { l: "Deposit paid",    v: l.depositPaid ? "Yes" : "No" },
                      ].map(item => (
                        <div key={item.l} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, padding: "8px 12px" }}>
                          <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, marginBottom: 2 }}>{item.l}</div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{item.v}</div>
                        </div>
                      ))}
                    </div>

                    {l.status === "ACTIVE" && (
                      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                        <button onClick={() => { setRenew(l); setRenewForm({ newEndDate: "", newMonthlyRent: "", newEscalationRate: "" }); setError("") }}
                          style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 13px", background: "#F0FDF4", color: "#166534", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                          <RefreshCw size={12} /> Renew
                        </button>
                        <button onClick={() => { setEscalate(l); setEscForm({ escalationPercent: "", newMonthlyRent: "" }); setError("") }}
                          style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 13px", background: "#FFFBEB", color: "#D97706", border: "1px solid #FDE68A", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                          <TrendingUp size={12} /> Escalate rent
                        </button>
                        <button onClick={() => { setTerminate(l); setTermReason(""); setError("") }}
                          style={{ padding: "7px 13px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                          Terminate
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Create lease modal */}
      {showCreate && (
        <ModalShell title="New Lease" onClose={() => setCreate(false)} width={640}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <div style={{ gridColumn: "1/-1" }}>
              <label style={lbl}>Unit *</label>
              <select value={form.unitId} onChange={e => setForm(f => ({ ...f, unitId: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                <option value="">Select a vacant unit</option>
                {vacantUnits.map((u: any) => <option key={u.id} value={u.id}>Unit {u.unitNumber} — {fmtR(u.baseRent)}/mo</option>)}
              </select>
              {vacantUnits.length === 0 && <div style={{ fontSize: 12, color: "#DC2626", marginTop: 4 }}>No vacant units available. Add units to a property first.</div>}
            </div>
            <div style={{ gridColumn: "1/-1" }}>
              <label style={lbl}>Lessee full name *</label>
              <input autoFocus value={form.lesseeName} onChange={e => setForm(f => ({ ...f, lesseeName: e.target.value }))} placeholder="Jane Smith" style={inp} />
            </div>
            <div>
              <label style={lbl}>SA ID / Passport</label>
              <input value={form.lesseeIdNumber} onChange={e => setForm(f => ({ ...f, lesseeIdNumber: e.target.value }))} placeholder="9001015026083" style={inp} />
            </div>
            <div>
              <label style={lbl}>Email</label>
              <input type="email" value={form.lesseeEmail} onChange={e => setForm(f => ({ ...f, lesseeEmail: e.target.value }))} placeholder="jane@example.com" style={inp} />
            </div>
            <div>
              <label style={lbl}>Phone</label>
              <input value={form.lesseePhone} onChange={e => setForm(f => ({ ...f, lesseePhone: e.target.value }))} placeholder="+27 82 123 4567" style={inp} />
            </div>
            <div>
              <label style={lbl}>Monthly rent (R) *</label>
              <input type="number" value={form.monthlyRent} onChange={e => setForm(f => ({ ...f, monthlyRent: e.target.value }))} placeholder="8500.00" style={inp} />
            </div>
            <div>
              <label style={lbl}>Deposit (R) *</label>
              <input type="number" value={form.depositAmount} onChange={e => setForm(f => ({ ...f, depositAmount: e.target.value }))} placeholder="17000.00" style={inp} />
            </div>
            <div>
              <label style={lbl}>Start date *</label>
              <input type="date" value={form.startDate} onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} style={inp} />
            </div>
            <div>
              <label style={lbl}>End date <span style={{ fontWeight: 400, color: "#94A3B8" }}>(leave blank for month-to-month)</span></label>
              <input type="date" value={form.endDate} min={form.startDate} onChange={e => setForm(f => ({ ...f, endDate: e.target.value }))} style={inp} />
            </div>
            <div>
              <label style={lbl}>Payment day</label>
              <input type="number" min={1} max={31} value={form.paymentDay} onChange={e => setForm(f => ({ ...f, paymentDay: e.target.value }))} style={inp} />
            </div>
            <div>
              <label style={lbl}>Annual escalation (%)</label>
              <input type="number" value={form.escalationRate} onChange={e => setForm(f => ({ ...f, escalationRate: e.target.value }))} placeholder="8.5" style={inp} />
            </div>
            <div style={{ gridColumn: "1/-1" }}>
              <label style={lbl}>Notes</label>
              <textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} rows={2} style={{ ...inp, resize: "vertical" as const }} />
            </div>
          </div>
          {error && <ErrBox msg={error} />}
          <div style={{ marginTop: 12, padding: "10px 14px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 8, fontSize: 12, color: "#1E40AF" }}>
            Under the Rental Housing Act 50 of 1999, rental deposits must be held in an interest-bearing account. A confirmation email will be sent to the lessee if an email address is provided.
          </div>
          <ModalFoot onCancel={() => setCreate(false)} loading={createLease.isPending}
            disabled={!form.unitId || !form.lesseeName || !form.monthlyRent || !form.startDate} label="Create Lease"
            onSubmit={() => createLease.mutate({ unitId: form.unitId, body: {
              lesseeName: form.lesseeName, lesseeIdNumber: form.lesseeIdNumber || null,
              lesseeEmail: form.lesseeEmail || null, lesseePhone: form.lesseePhone || null,
              startDate: form.startDate, endDate: form.endDate || null,
              monthlyRent: parseFloat(form.monthlyRent),
              depositAmount: parseFloat(form.depositAmount) || 0,
              paymentDay: parseInt(form.paymentDay) || 1,
              escalationRate: parseFloat(form.escalationRate) || 0,
              notes: form.notes || null,
            }})} />
        </ModalShell>
      )}

      {/* Renew modal */}
      {showRenew && (
        <ModalShell title={`Renew Lease — ${showRenew.lesseeName}`} onClose={() => setRenew(null)}>
          <div style={{ marginBottom: 16, padding: "10px 14px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 8, fontSize: 13, color: "#166534" }}>
            Current: <strong>{fmtR(showRenew.monthlyRent)}/mo</strong> · Expires {fmtD(showRenew.endDate)}{showRenew.escalationRate > 0 && ` · ${showRenew.escalationRate}% escalation stored`}
          </div>
          <div style={{ display: "flex", flexDirection: "column" as const, gap: 14 }}>
            <div><label style={lbl}>New end date *</label><input type="date" value={renewForm.newEndDate} onChange={e => setRenewForm(f => ({ ...f, newEndDate: e.target.value }))} style={inp} /></div>
            <div>
              <label style={lbl}>New monthly rent <span style={{ fontWeight: 400, color: "#94A3B8" }}>(leave blank to auto-apply stored escalation)</span></label>
              <input type="number" value={renewForm.newMonthlyRent} onChange={e => setRenewForm(f => ({ ...f, newMonthlyRent: e.target.value }))} placeholder={fmtR(showRenew.monthlyRent)} style={inp} />
            </div>
            <div><label style={lbl}>New escalation rate (%)</label><input type="number" value={renewForm.newEscalationRate} onChange={e => setRenewForm(f => ({ ...f, newEscalationRate: e.target.value }))} placeholder={showRenew.escalationRate?.toString()} style={inp} /></div>
          </div>
          {error && <ErrBox msg={error} />}
          <ModalFoot onCancel={() => setRenew(null)} loading={renewLease.isPending}
            disabled={!renewForm.newEndDate} label="Renew Lease"
            onSubmit={() => renewLease.mutate({ id: showRenew.id, body: {
              newEndDate: renewForm.newEndDate,
              newMonthlyRent: renewForm.newMonthlyRent ? parseFloat(renewForm.newMonthlyRent) : null,
              newEscalationRate: renewForm.newEscalationRate ? parseFloat(renewForm.newEscalationRate) : null,
            }})} />
        </ModalShell>
      )}

      {/* Escalate modal */}
      {showEscalate && (
        <ModalShell title={`Escalate Rent — ${showEscalate.lesseeName}`} onClose={() => setEscalate(null)}>
          <div style={{ marginBottom: 16, padding: "10px 14px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 8, fontSize: 13, color: "#92400E" }}>
            Current rent: <strong>{fmtR(showEscalate.monthlyRent)}/mo</strong>
          </div>
          <div style={{ display: "flex", flexDirection: "column" as const, gap: 14 }}>
            <div>
              <label style={lbl}>Escalation percentage (%)</label>
              <input type="number" value={escalateForm.escalationPercent} onChange={e => setEscForm(f => ({ ...f, escalationPercent: e.target.value }))} placeholder="8.5" style={inp} />
              {escalateForm.escalationPercent && (
                <div style={{ fontSize: 12, color: "#64748B", marginTop: 4 }}>
                  New rent: <strong>{fmtR(Number(showEscalate.monthlyRent) * (1 + parseFloat(escalateForm.escalationPercent)/100))}</strong>
                </div>
              )}
            </div>
            <div style={{ textAlign: "center" as const, color: "#94A3B8", fontSize: 12 }}>— or set exact amount —</div>
            <div><label style={lbl}>New monthly rent (R)</label><input type="number" value={escalateForm.newMonthlyRent} onChange={e => setEscForm(f => ({ ...f, newMonthlyRent: e.target.value }))} placeholder="0.00" style={inp} /></div>
          </div>
          {error && <ErrBox msg={error} />}
          <ModalFoot onCancel={() => setEscalate(null)} loading={escalateLease.isPending}
            disabled={!escalateForm.escalationPercent && !escalateForm.newMonthlyRent} label="Apply Escalation"
            onSubmit={() => escalateLease.mutate({ id: showEscalate.id, body: {
              escalationPercent: escalateForm.escalationPercent ? parseFloat(escalateForm.escalationPercent) : null,
              newMonthlyRent: escalateForm.newMonthlyRent ? parseFloat(escalateForm.newMonthlyRent) : null,
            }})} />
        </ModalShell>
      )}

      {/* Terminate modal */}
      {showTerminate && (
        <ModalShell title="Terminate Lease" onClose={() => setTerminate(null)}>
          <div style={{ marginBottom: 16, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>
            Terminating the lease for <strong>{showTerminate.lesseeName}</strong>. The unit will be set to VACANT.
          </div>
          <div><label style={lbl}>Reason</label><textarea value={terminateReason} autoFocus onChange={e => setTermReason(e.target.value)} rows={3} placeholder="Reason for termination..." style={{ ...inp, resize: "vertical" as const }} /></div>
          {error && <ErrBox msg={error} />}
          <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
            <button onClick={() => setTerminate(null)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
            <button onClick={() => terminateLease.mutate({ id: showTerminate.id, reason: terminateReason || "Terminated by landlord" })} disabled={terminateLease.isPending}
              style={{ padding: "9px 22px", background: "#DC2626", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
              {terminateLease.isPending ? "Terminating..." : "Terminate Lease"}
            </button>
          </div>
        </ModalShell>
      )}
    </div>
  )
}

function ModalShell({ title, onClose, children, width = 520 }: any) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
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
