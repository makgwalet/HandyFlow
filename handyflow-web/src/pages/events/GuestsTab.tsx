import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, QrCode, Users, CheckCircle, XCircle, AlertCircle } from "lucide-react"

interface Guest {
  id: string
  ticketNumber: string
  qrCode: string
  fullName: string
  email: string
  phone: string
  company: string
  dietaryRequirements: string
  tierId: string
  tierName: string
  status: string
  paymentStatus: string
  amountPaid: number
  checkedInAt: string | null
}

interface Tier { id: string; name: string; price: number }

const STATUS_STYLE: Record<string, { color: string; bg: string }> = {
  REGISTERED:  { color: "#1D4ED8", bg: "#EFF6FF" },
  CHECKED_IN:  { color: "#166534", bg: "#DCFCE7" },
  CANCELLED:   { color: "#DC2626", bg: "#FEF2F2" },
  WAITLISTED:  { color: "#D97706", bg: "#FFFBEB" },
}

const CHECK_IN_RESULT_STYLE: Record<string, { color: string; bg: string; icon: typeof CheckCircle }> = {
  SUCCESS:           { color: "#166534", bg: "#DCFCE7", icon: CheckCircle },
  ALREADY_CHECKED_IN:{ color: "#D97706", bg: "#FFFBEB", icon: AlertCircle },
  CANCELLED_TICKET:  { color: "#DC2626", bg: "#FEF2F2", icon: XCircle },
  NOT_FOUND:         { color: "#DC2626", bg: "#FEF2F2", icon: XCircle },
}

export default function GuestsTab({ eventId }: { eventId: string | null }) {
  const qc = useQueryClient()
  const [showRegister, setShowRegister] = useState(false)
  const [showCheckIn, setShowCheckIn]   = useState(false)
  const [statusFilter, setStatus]       = useState("")
  const [checkInCode, setCheckInCode]   = useState("")
  const [checkInResult, setCheckInResult] = useState<any>(null)
  const [error, setError] = useState("")

  const [form, setForm] = useState({
    fullName: "", email: "", phone: "", company: "",
    dietaryRequirements: "", tierId: "", amountPaid: "", notes: "",
  })

  const { data: page, isLoading } = useQuery({
    queryKey: ["event-guests", eventId, statusFilter],
    queryFn: async () => {
      if (!eventId) return null
      const params = new URLSearchParams({ size: "100" })
      if (statusFilter) params.set("status", statusFilter)
      const r = await apiClient.get(`/api/v1/events/${eventId}/guests?${params}`)
      return r.data
    },
    enabled: !!eventId,
  })

  const { data: tiers = [] } = useQuery<Tier[]>({
    queryKey: ["event-tiers", eventId],
    queryFn: async () => {
      if (!eventId) return []
      const r = await apiClient.get(`/api/v1/events/${eventId}/tiers`)
      return r.data || []
    },
    enabled: !!eventId,
  })

  const registerGuest = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/events/${eventId}/guests`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["event-guests"] }); setShowRegister(false); resetForm() },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to register guest"),
  })

  const checkIn = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/events/${eventId}/check-in`, body),
    onSuccess: (res: any) => { setCheckInResult(res.data); qc.invalidateQueries({ queryKey: ["event-guests"] }) },
    onError: (e: any) => setError(e.response?.data?.message || "Check-in failed"),
  })

  const cancelGuest = useMutation({
    mutationFn: (guestId: string) => apiClient.post(`/api/v1/events/${eventId}/guests/${guestId}/cancel`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["event-guests"] }),
  })

  const resetForm = () => setForm({ fullName: "", email: "", phone: "", company: "", dietaryRequirements: "", tierId: "", amountPaid: "", notes: "" })

  const guests: Guest[] = page?.content || []
  const fmtR = (n: number) => n ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

  if (!eventId) return (
    <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
      <Users size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
      <div style={{ fontWeight: 600, color: "#475569" }}>Select an event first</div>
      <div style={{ fontSize: 14, marginTop: 4 }}>Click "Guests" on an event to manage its guest list.</div>
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 10, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {["", "REGISTERED", "CHECKED_IN", "CANCELLED", "WAITLISTED"].map(s => (
            <button key={s} onClick={() => setStatus(s)} style={filterBtn(statusFilter === s)}>{s || "All"}</button>
          ))}
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={() => setShowCheckIn(true)}
            style={{ display: "flex", alignItems: "center", gap: 7, background: "#F0FDF4", color: "#166534", border: "1px solid #86EFAC", borderRadius: 8, padding: "9px 16px", fontSize: 14, cursor: "pointer" }}>
            <QrCode size={15} /> Check In
          </button>
          <button onClick={() => { setShowRegister(true); setError("") }} style={btnPrimary}><Plus size={15} /> Register Guest</button>
        </div>
      </div>

      {/* Summary */}
      {guests.length > 0 && (
        <div style={{ display: "flex", gap: 10, marginBottom: 16, flexWrap: "wrap" }}>
          {["REGISTERED", "CHECKED_IN", "CANCELLED"].map(s => {
            const style = STATUS_STYLE[s] || { color: "#475569", bg: "#F8FAFC" }
            const count = guests.filter(g => g.status === s).length
            return count > 0 ? (
              <div key={s} style={{ background: style.bg, borderRadius: 8, padding: "8px 14px" }}>
                <span style={{ fontSize: 16, fontWeight: 700, color: style.color }}>{count}</span>
                <span style={{ fontSize: 11, color: style.color, marginLeft: 5 }}>{s.replace("_", " ")}</span>
              </div>
            ) : null
          })}
        </div>
      )}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading guests...</div>
      ) : guests.length === 0 ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "#94A3B8" }}>
          <Users size={36} style={{ marginBottom: 10, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No guests registered yet</div>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "#F8FAFC" }}>
                <th style={th}>Guest</th>
                <th style={th}>Ticket</th>
                <th style={th}>Tier</th>
                <th style={th}>Contact</th>
                <th style={th}>Status</th>
                <th style={th}>Amount</th>
                <th style={th}></th>
              </tr>
            </thead>
            <tbody>
              {guests.map((g, i) => {
                const style = STATUS_STYLE[g.status] || { color: "#475569", bg: "#F8FAFC" }
                return (
                  <tr key={g.id} style={{ background: i % 2 === 0 ? "#fff" : "#FAFAFA" }}>
                    <td style={td}>
                      <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{g.fullName}</div>
                      {g.company && <div style={{ fontSize: 11, color: "#94A3B8" }}>{g.company}</div>}
                    </td>
                    <td style={td}><span style={{ fontFamily: "monospace", fontSize: 12, color: "#475569" }}>{g.ticketNumber}</span></td>
                    <td style={td}><span style={{ fontSize: 12, color: "#64748B" }}>{g.tierName || "—"}</span></td>
                    <td style={td}>
                      <div style={{ fontSize: 12, color: "#64748B" }}>{g.email || "—"}</div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>{g.phone || ""}</div>
                    </td>
                    <td style={td}>
                      <span style={{ background: style.bg, color: style.color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{g.status}</span>
                    </td>
                    <td style={td}><span style={{ fontSize: 13, fontWeight: 500 }}>{fmtR(g.amountPaid)}</span></td>
                    <td style={td}>
                      {g.status === "REGISTERED" && (
                        <button onClick={() => cancelGuest.mutate(g.id)}
                          style={{ padding: "4px 10px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 5, fontSize: 11, cursor: "pointer" }}>
                          Cancel
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

      {/* QR Check-in modal */}
      {showCheckIn && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>QR Check-In</h3>
              <button onClick={() => { setShowCheckIn(false); setCheckInResult(null); setCheckInCode("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            {checkInResult ? (
              <div>
                {(() => {
                  const style = CHECK_IN_RESULT_STYLE[checkInResult.result] || { color: "#475569", bg: "#F8FAFC", icon: AlertCircle }
                  const Icon = style.icon
                  return (
                    <div style={{ textAlign: "center", padding: "20px 0" }}>
                      <Icon size={48} color={style.color} style={{ marginBottom: 12 }} />
                      <div style={{ fontSize: 20, fontWeight: 700, color: style.color, marginBottom: 8 }}>
                        {checkInResult.result.replace(/_/g, " ")}
                      </div>
                      {checkInResult.guestName && <div style={{ fontSize: 16, fontWeight: 600, color: "#0F172A" }}>{checkInResult.guestName}</div>}
                      {checkInResult.tierName && <div style={{ fontSize: 13, color: "#64748B" }}>{checkInResult.tierName} · {checkInResult.ticketNumber}</div>}
                      {checkInResult.totalCheckedIn != null && (
                        <div style={{ marginTop: 16, padding: "10px 14px", background: "#F8FAFC", borderRadius: 8, fontSize: 13, color: "#475569" }}>
                          Total checked in: <strong>{checkInResult.totalCheckedIn}</strong>
                        </div>
                      )}
                    </div>
                  )
                })()}
                <button onClick={() => { setCheckInResult(null); setCheckInCode("") }} style={{ ...btnPrimary, width: "100%", justifyContent: "center", marginTop: 16 }}>
                  Scan Next
                </button>
              </div>
            ) : (
              <div>
                <div style={{ background: "#F8FAFC", border: "2px dashed #E2E8F0", borderRadius: 10, padding: "24px", textAlign: "center", marginBottom: 20 }}>
                  <QrCode size={48} color="#94A3B8" style={{ marginBottom: 8 }} />
                  <div style={{ fontSize: 13, color: "#64748B" }}>Enter ticket number or QR code</div>
                </div>
                <Field label="QR Code / Ticket Number">
                  <input value={checkInCode} onChange={e => setCheckInCode(e.target.value)}
                    onKeyDown={e => { if (e.key === "Enter" && checkInCode) checkIn.mutate({ qrCode: checkInCode, location: "Main Entrance", scanDevice: "Web" }) }}
                    placeholder="Scan or type ticket number..." autoFocus style={inputStyle} />
                </Field>
                {error && <div style={{ marginTop: 8, color: "#DC2626", fontSize: 13 }}>{error}</div>}
                <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 16 }}>
                  <button onClick={() => setShowCheckIn(false)} style={btnCancel}>Close</button>
                  <button onClick={() => checkIn.mutate({ qrCode: checkInCode, location: "Main Entrance", scanDevice: "Web" })}
                    disabled={!checkInCode || checkIn.isPending} style={btnPrimary}>
                    {checkIn.isPending ? "Checking in..." : "Check In"}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Register guest modal */}
      {showRegister && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 520, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Register Guest</h3>
              <button onClick={() => { setShowRegister(false); resetForm() }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Full Name *"><input value={form.fullName} onChange={e => setForm(f => ({ ...f, fullName: e.target.value }))} placeholder="John Smith" style={inputStyle} /></Field>
              </div>
              {tiers.length > 0 && (
                <div style={{ gridColumn: "1 / -1" }}>
                  <Field label="Ticket Tier">
                    <select value={form.tierId} onChange={e => setForm(f => ({ ...f, tierId: e.target.value }))} style={inputStyle}>
                      <option value="">No tier</option>
                      {tiers.map(t => <option key={t.id} value={t.id}>{t.name} {t.price > 0 ? `— R${t.price}` : "— Free"}</option>)}
                    </select>
                  </Field>
                </div>
              )}
              <Field label="Email"><input value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} placeholder="john@example.com" style={inputStyle} /></Field>
              <Field label="Phone"><input value={form.phone} onChange={e => setForm(f => ({ ...f, phone: e.target.value }))} placeholder="+27 82 123 4567" style={inputStyle} /></Field>
              <Field label="Company"><input value={form.company} onChange={e => setForm(f => ({ ...f, company: e.target.value }))} placeholder="Acme Corp" style={inputStyle} /></Field>
              <Field label="Amount Paid (R)"><input type="number" value={form.amountPaid} onChange={e => setForm(f => ({ ...f, amountPaid: e.target.value }))} placeholder="0.00" style={inputStyle} /></Field>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Dietary Requirements"><input value={form.dietaryRequirements} onChange={e => setForm(f => ({ ...f, dietaryRequirements: e.target.value }))} placeholder="Vegetarian, Halaal, etc." style={inputStyle} /></Field>
              </div>
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => { setShowRegister(false); resetForm() }} style={btnCancel}>Cancel</button>
              <button onClick={() => registerGuest.mutate({ ...form, tierId: form.tierId || null, amountPaid: parseFloat(form.amountPaid) || null })}
                disabled={!form.fullName || registerGuest.isPending} style={btnPrimary}>
                {registerGuest.isPending ? "Registering..." : "Register Guest"}
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
const td: React.CSSProperties = { padding: "11px 16px", fontSize: 13, borderBottom: "1px solid #F1F5F9" }
