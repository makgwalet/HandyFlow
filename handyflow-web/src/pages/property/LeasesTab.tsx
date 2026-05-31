import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, FileText, X, AlertTriangle } from "lucide-react"

interface Unit { id: string; propertyId: string; unitNumber: string; status: string }
interface Property { id: string; name: string }
interface Lease {
  id: string; unitId: string; lesseeName: string
  lesseeEmail: string | null; lesseePhone: string | null
  startDate: string; endDate: string
  monthlyRent: number; depositAmount: number
  depositPaid: boolean; paymentDay: number
  escalationRate: number; status: string; expiringSoon: boolean; createdAt: string
}

const LEASE_STATUS: Record<string, { color: string; bg: string }> = {
  ACTIVE:    { color: "#166534", bg: "#DCFCE7" },
  EXPIRED:   { color: "#94A3B8", bg: "#F8FAFC" },
  CANCELLED: { color: "#DC2626", bg: "#FEF2F2" },
  PENDING:   { color: "#D97706", bg: "#FFFBEB" },
}

export default function LeasesTab() {
  const qc = useQueryClient()
  const [showAdd, setShowAdd]           = useState(false)
  const [terminateId, setTerminateId]   = useState<string | null>(null)
  const [terminateReason, setTerminateReason] = useState("")
  const [selectedUnit, setSelectedUnit] = useState("")
  const [error, setError]               = useState("")
  const [form, setForm] = useState({
    lesseeName: "", lesseeEmail: "", lesseePhone: "", lesseeIdNumber: "",
    startDate: "", endDate: "", monthlyRent: "", depositAmount: "",
    paymentDay: "1", escalationRate: "8",
  })

  // Properties for name lookup
  const { data: properties = [] } = useQuery<Property[]>({
    queryKey: ["properties"],
    queryFn: async () => (await apiClient.get("/api/v1/property/properties?size=50")).data.content,
  })

  // Fetch units from dedicated endpoint — NOT from properties list (units always empty there)
  const { data: allUnits = [] } = useQuery<Unit[]>({
    queryKey: ["property-units"],
    queryFn: async () => (await apiClient.get("/api/v1/property/units?size=200")).data.content,
  })

  const { data: leases = [], isLoading } = useQuery<Lease[]>({
    queryKey: ["leases"],
    queryFn: async () => (await apiClient.get("/api/v1/property/leases?size=100")).data.content,
  })

  const propertyMap = Object.fromEntries(properties.map(p => [p.id, p.name]))
  const unitMap     = Object.fromEntries(
    allUnits.map(u => [u.id, { ...u, propertyName: propertyMap[u.propertyId] ?? "Unknown property" }])
  )
  const vacantUnits = allUnits.filter(u => u.status === "VACANT")

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["leases"] })
    qc.invalidateQueries({ queryKey: ["property-units"] })
    qc.invalidateQueries({ queryKey: ["properties"] })
  }

  const createLease = useMutation({
    mutationFn: ({ unitId, body }: { unitId: string; body: any }) =>
      apiClient.post(`/api/v1/property/units/${unitId}/leases`, body),
    onSuccess: () => {
      invalidate()
      setShowAdd(false)
      setForm({ lesseeName: "", lesseeEmail: "", lesseePhone: "", lesseeIdNumber: "", startDate: "", endDate: "", monthlyRent: "", depositAmount: "", paymentDay: "1", escalationRate: "8" })
      setSelectedUnit(""); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create lease"),
  })

  const terminateLease = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      apiClient.post(`/api/v1/property/leases/${id}/terminate?reason=${encodeURIComponent(reason)}`),
    onSuccess: () => { invalidate(); setTerminateId(null); setTerminateReason("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to terminate lease"),
  })

  const fmtDate = (d: string) => new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
  const fmtR    = (n: number) => `R ${n.toLocaleString("en-ZA")}`

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ fontSize: 14, color: "#64748B" }}>
          {leases.filter(l => l.status === "ACTIVE").length} active lease{leases.filter(l => l.status === "ACTIVE").length !== 1 ? "s" : ""}
          {vacantUnits.length > 0 && <span style={{ marginLeft: 8, color: "#166534" }}>· {vacantUnits.length} vacant unit{vacantUnits.length !== 1 ? "s" : ""} available</span>}
        </div>
        <button onClick={() => { setShowAdd(true); setError("") }} style={btnPrimary}>
          <Plus size={15} /> Create Lease
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading leases...</div>
      ) : leases.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <FileText size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No leases yet</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Create a lease to assign a tenant to a vacant unit.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {leases.map(lease => {
            const cfg  = LEASE_STATUS[lease.status] || LEASE_STATUS.ACTIVE
            const unit = unitMap[lease.unitId]
            return (
              <div key={lease.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "16px 20px" }}>
                <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
                  <div style={{ display: "flex", gap: 14 }}>
                    <div style={{ width: 42, height: 42, borderRadius: 10, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <FileText size={20} color={cfg.color} />
                    </div>
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{lease.lesseeName}</span>
                        {unit && <span style={{ fontSize: 13, color: "#64748B" }}>→ Unit {unit.unitNumber} · {unit.propertyName}</span>}
                        {lease.expiringSoon && (
                          <span style={{ background: "#FEF3C7", color: "#D97706", padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>EXPIRING SOON</span>
                        )}
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>
                        {fmtDate(lease.startDate)} – {fmtDate(lease.endDate)}
                        {lease.lesseePhone && ` · ${lease.lesseePhone}`}
                        {lease.lesseeEmail && ` · ${lease.lesseeEmail}`}
                      </div>
                      <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>
                        Pay day: {lease.paymentDay} · Escalation: {lease.escalationRate}%
                        {lease.depositPaid && " · ✓ Deposit paid"}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                    <div style={{ textAlign: "right" }}>
                      <div style={{ fontWeight: 700, color: "#0F172A" }}>{fmtR(lease.monthlyRent)}/mo</div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>Deposit: {fmtR(lease.depositAmount)}</div>
                    </div>
                    <span style={{ background: cfg.bg, color: cfg.color, padding: "4px 12px", borderRadius: 20, fontSize: 12, fontWeight: 600 }}>{lease.status}</span>
                    {lease.status === "ACTIVE" && (
                      <button onClick={() => { setTerminateId(lease.id); setTerminateReason(""); setError("") }}
                        style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        <AlertTriangle size={12} /> Terminate
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Create Lease Modal */}
      {showAdd && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 560, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Create Lease Agreement</h3>
              <button onClick={() => { setShowAdd(false); setError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Unit *</label>
                <select value={selectedUnit} onChange={e => setSelectedUnit(e.target.value)} style={sel}>
                  <option value="">Select a vacant unit...</option>
                  {vacantUnits.map(u => (
                    <option key={u.id} value={u.id}>
                      {propertyMap[u.propertyId] ?? "Unknown"} – Unit {u.unitNumber}
                    </option>
                  ))}
                </select>
                {vacantUnits.length === 0 && (
                  <div style={{ fontSize: 12, color: "#D97706", marginTop: 4 }}>
                    No vacant units available. Add units in the Properties tab first.
                  </div>
                )}
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <div style={{ gridColumn: "1 / -1" }}>
                  <label style={lbl}>Tenant Full Name *</label>
                  <input value={form.lesseeName} onChange={e => setForm(f => ({ ...f, lesseeName: e.target.value }))} placeholder="Thandi Mokoena" style={inp} />
                </div>
                <div>
                  <label style={lbl}>SA ID Number</label>
                  <input value={form.lesseeIdNumber} onChange={e => setForm(f => ({ ...f, lesseeIdNumber: e.target.value }))} placeholder="9001015026088" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Phone</label>
                  <input value={form.lesseePhone} onChange={e => setForm(f => ({ ...f, lesseePhone: e.target.value }))} placeholder="+27 72 555 0303" style={inp} />
                </div>
                <div style={{ gridColumn: "1 / -1" }}>
                  <label style={lbl}>Email</label>
                  <input type="email" value={form.lesseeEmail} onChange={e => setForm(f => ({ ...f, lesseeEmail: e.target.value }))} placeholder="thandi@gmail.com" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Lease Start *</label>
                  <input type="date" value={form.startDate} onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} style={inp} />
                </div>
                <div>
                  <label style={lbl}>Lease End *</label>
                  <input type="date" value={form.endDate} onChange={e => setForm(f => ({ ...f, endDate: e.target.value }))} style={inp} />
                </div>
                <div>
                  <label style={lbl}>Monthly Rent (R) *</label>
                  <input type="number" value={form.monthlyRent} onChange={e => setForm(f => ({ ...f, monthlyRent: e.target.value }))} placeholder="8500" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Deposit (R) *</label>
                  <input type="number" value={form.depositAmount} onChange={e => setForm(f => ({ ...f, depositAmount: e.target.value }))} placeholder="17000" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Payment Day (1–28)</label>
                  <input type="number" value={form.paymentDay} onChange={e => setForm(f => ({ ...f, paymentDay: e.target.value }))} placeholder="1" min="1" max="28" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Annual Escalation %</label>
                  <input type="number" value={form.escalationRate} onChange={e => setForm(f => ({ ...f, escalationRate: e.target.value }))} placeholder="8" style={inp} />
                </div>
              </div>
            </div>

            {error && <div style={{ marginTop: 10, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13 }}>{error}</div>}

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => { setShowAdd(false); setError("") }} style={cancelBtn}>Cancel</button>
              <button
                onClick={() => createLease.mutate({ unitId: selectedUnit, body: { lesseeName: form.lesseeName, lesseeIdNumber: form.lesseeIdNumber || null, lesseeEmail: form.lesseeEmail || null, lesseePhone: form.lesseePhone || null, startDate: form.startDate, endDate: form.endDate, monthlyRent: Number(form.monthlyRent), depositAmount: Number(form.depositAmount), paymentDay: Number(form.paymentDay), escalationRate: Number(form.escalationRate) } })}
                disabled={!selectedUnit || !form.lesseeName || !form.startDate || !form.endDate || !form.monthlyRent || createLease.isPending}
                style={{ ...submitBtn, opacity: !selectedUnit || !form.lesseeName || !form.startDate || !form.endDate || !form.monthlyRent ? 0.5 : 1 }}>
                {createLease.isPending ? "Creating..." : "Create Lease"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Terminate Lease Modal */}
      {terminateId && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 440, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Terminate Lease</h3>
              <button onClick={() => setTerminateId(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ padding: "12px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 10, marginBottom: 16, fontSize: 13, color: "#DC2626" }}>
              <AlertTriangle size={14} style={{ display: "inline", marginRight: 6 }} />
              This will end the lease and set the unit back to Vacant. This action cannot be undone.
            </div>
            <div>
              <label style={lbl}>Reason for termination</label>
              <input value={terminateReason} onChange={e => setTerminateReason(e.target.value)}
                placeholder="e.g. Tenant vacated early, mutual agreement..."
                style={{ ...inp, width: "100%", boxSizing: "border-box" as const }} />
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setTerminateId(null)} style={cancelBtn}>Cancel</button>
              <button
                onClick={() => terminateLease.mutate({ id: terminateId, reason: terminateReason || "Terminated by landlord" })}
                disabled={terminateLease.isPending}
                style={{ padding: "9px 20px", background: "#DC2626", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
                {terminateLease.isPending ? "Terminating..." : "Confirm Termination"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const lbl: React.CSSProperties        = { display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }
const sel: React.CSSProperties        = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }
const inp: React.CSSProperties        = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }
const cancelBtn: React.CSSProperties  = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const submitBtn: React.CSSProperties  = { padding: "9px 20px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: "pointer" }
