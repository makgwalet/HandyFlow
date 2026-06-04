// src/pages/events/GuestsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, X, QrCode, UserCheck, UserX, Ticket, Users,
  Search, Download, ChevronLeft, AlertTriangle, Tag,
} from "lucide-react"

const fmtDT = (d: any) => d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—"
const fmtR  = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "Free"
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

const GUEST_STATUS: Record<string, { color: string; bg: string }> = {
  REGISTERED:  { color: "#0284C7", bg: "#E0F2FE" },
  CONFIRMED:   { color: "#166534", bg: "#DCFCE7" },
  CHECKED_IN:  { color: "#166534", bg: "#DCFCE7" },
  CANCELLED:   { color: "#94A3B8", bg: "#F1F5F9" },
  NO_SHOW:     { color: "#DC2626", bg: "#FEF2F2" },
}
const PAY_STATUS: Record<string, { color: string; bg: string }> = {
  FREE:    { color: "#166534", bg: "#DCFCE7" },
  PAID:    { color: "#166534", bg: "#DCFCE7" },
  PENDING: { color: "#D97706", bg: "#FFFBEB" },
  REFUNDED:{ color: "#64748B", bg: "#F1F5F9" },
}

interface Props {
  eventId: string | null
  eventTitle: string
  onChangeEvent: () => void
}

export default function GuestsTab({ eventId, eventTitle, onChangeEvent }: Props) {
  const qc = useQueryClient()
  const [filterStatus, setFilterStatus] = useState("ALL")
  const [search,       setSearch]       = useState("")
  const [showRegister, setShowRegister] = useState(false)
  const [showTiers,    setShowTiers]    = useState(false)
  const [showCheckIn,  setShowCheckIn]  = useState(false)
  const [qrInput,      setQrInput]      = useState("")
  const [checkInResult, setCheckInResult] = useState<any>(null)
  const [error,        setError]        = useState("")
  const [confirmTarget, setConfirmTarget] = useState<{ id: string; name: string } | null>(null)

  const INIT_GUEST = () => ({ tierId: "", fullName: "", email: "", phone: "", company: "", dietaryRequirements: "", amountPaid: "", notes: "" })
  const INIT_TIER  = () => ({ name: "", description: "", price: "", quantity: "", saleStart: "", saleEnd: "" })
  const [guestForm, setGuestForm] = useState(INIT_GUEST())
  const [tierForm,  setTierForm]  = useState(INIT_TIER())
  const gf = (k: string, v: any) => setGuestForm(p => ({ ...p, [k]: v }))
  const tf = (k: string, v: any) => setTierForm(p => ({ ...p, [k]: v }))

  const { data: guests = [], isLoading: guestsLoading } = useQuery<any[]>({
    queryKey: ["event-guests", eventId, filterStatus],
    queryFn: async () => {
      if (!eventId) return []
      const params = new URLSearchParams({ size: "500" })
      if (filterStatus !== "ALL") params.set("status", filterStatus)
      const r = await apiClient.get(`/api/v1/events/${eventId}/guests?${params}`)
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
    enabled: !!eventId,
  })

  const { data: tiers = [] } = useQuery<any[]>({
    queryKey: ["event-tiers", eventId],
    queryFn: async () => {
      if (!eventId) return []
      const r = await apiClient.get(`/api/v1/events/${eventId}/tiers`)
      return r.data?.data ?? r.data ?? []
    },
    enabled: !!eventId,
  })

  const { data: stats } = useQuery<any>({
    queryKey: ["event-stats", eventId],
    queryFn: async () => {
      if (!eventId) return null
      const r = await apiClient.get(`/api/v1/events/${eventId}/stats`)
      return r.data?.data ?? r.data
    },
    enabled: !!eventId,
    refetchInterval: 10000, // live update every 10s
  })

  const registerGuest = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/events/${eventId}/guests`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["event-guests", eventId] })
      qc.invalidateQueries({ queryKey: ["event-stats", eventId] })
      setShowRegister(false); setGuestForm(INIT_GUEST()); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Registration failed"),
  })

  const cancelGuest = useMutation({
    mutationFn: (guestId: string) => apiClient.post(`/api/v1/events/${eventId}/guests/${guestId}/cancel`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["event-guests", eventId] }); qc.invalidateQueries({ queryKey: ["event-stats", eventId] }) },
  })

  const createTier = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/events/${eventId}/tiers`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["event-tiers", eventId] })
      setTierForm(INIT_TIER()); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create tier"),
  })

  const doCheckIn = useMutation({
    mutationFn: (qrCode: string) => apiClient.post(`/api/v1/events/${eventId}/check-in`, { qrCode }),
    onSuccess: (r: any) => {
      const data = r.data?.data ?? r.data
      setCheckInResult(data)
      setQrInput("")
      qc.invalidateQueries({ queryKey: ["event-guests", eventId] })
      qc.invalidateQueries({ queryKey: ["event-stats", eventId] })
    },
    onError: (e: any) => setCheckInResult({ result: "ERROR", message: e.response?.data?.message ?? "Check-in failed" }),
  })

  // Export guest list as CSV
  const exportCSV = () => {
    const rows = (guests as any[])
    const headers = ["Ticket","Name","Email","Phone","Company","Tier","Status","Payment","Amount","Dietary","Checked In"]
    const csv = [headers, ...rows.map((g: any) => [
      g.ticketNumber, g.fullName, g.email ?? "", g.phone ?? "", g.company ?? "",
      g.tierName ?? "", g.status, g.paymentStatus, g.amountPaid ?? 0,
      g.dietaryRequirements ?? "", g.checkedInAt ? new Date(g.checkedInAt).toLocaleString("en-ZA") : "",
    ])].map(r => r.join(",")).join("\n")
    const a = document.createElement("a")
    a.href = "data:text/csv;charset=utf-8," + encodeURIComponent(csv)
    a.download = `guests-${eventId?.slice(0, 8)}.csv`
    a.click()
  }

  const filtered = (guests as any[]).filter(g =>
    !search || g.fullName?.toLowerCase().includes(search.toLowerCase()) ||
    g.email?.toLowerCase().includes(search.toLowerCase()) ||
    g.ticketNumber?.includes(search))

  const checkedIn  = (guests as any[]).filter(g => g.status === "CHECKED_IN").length
  const registered = (guests as any[]).filter(g => !["CANCELLED","NO_SHOW"].includes(g.status)).length
  const revenue    = (guests as any[]).reduce((s: number, g: any) => s + parseFloat(g.amountPaid ?? 0), 0)

  if (!eventId) {
    return (
      <div style={{ textAlign: "center", padding: "60px 20px" }}>
        <Ticket size={40} style={{ marginBottom: 12, color: "#CBD5E1" }} />
        <div style={{ fontWeight: 600, color: "#475569", marginBottom: 8 }}>No event selected</div>
        <button onClick={onChangeEvent}
          style={{ display: "flex", alignItems: "center", gap: 6, margin: "0 auto", padding: "8px 16px", background: "#0284C7", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <ChevronLeft size={14} /> Select an event
        </button>
      </div>
    )
  }

  return (
    <div>
      {/* Event context bar */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 18, padding: "10px 14px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <button onClick={onChangeEvent} style={{ display: "flex", alignItems: "center", gap: 4, background: "none", border: "none", cursor: "pointer", color: "#64748B", fontSize: 12, fontWeight: 600 }}>
            <ChevronLeft size={13} /> Events
          </button>
          <span style={{ color: "#CBD5E1" }}>/</span>
          <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{eventTitle}</span>
        </div>
        <div style={{ display: "flex", gap: 12, fontSize: 13 }}>
          <span style={{ color: "#64748B" }}>Registered: <strong style={{ color: "#0F172A" }}>{registered}</strong></span>
          <span style={{ color: "#64748B" }}>Checked in: <strong style={{ color: "#166534" }}>{checkedIn}</strong></span>
          {revenue > 0 && <span style={{ color: "#64748B" }}>Revenue: <strong style={{ color: "#0D9488" }}>{fmtR(revenue)}</strong></span>}
        </div>
      </div>

      {/* Live check-in stats bar */}
      {stats && (
        <div style={{ display: "flex", gap: 10, marginBottom: 16 }}>
          {[
            { l: "Registered",   v: stats.totalRegistered,  color: "#0284C7", bg: "#E0F2FE" },
            { l: "Checked in",   v: stats.totalCheckedIn,   color: "#166534", bg: "#DCFCE7" },
            { l: "Vendors",      v: stats.totalVendors,     color: "#D97706", bg: "#FFFBEB" },
            { l: "Attendance %", v: stats.totalRegistered > 0 ? `${Math.round(stats.totalCheckedIn / stats.totalRegistered * 100)}%` : "—", color: "#7C3AED", bg: "#F5F3FF" },
          ].map(s => (
            <div key={s.l} style={{ background: s.bg, borderRadius: 8, padding: "8px 14px" }}>
              <div style={{ fontSize: 18, fontWeight: 800, color: s.color }}>{s.v}</div>
              <div style={{ fontSize: 10, color: s.color, opacity: 0.8 }}>{s.l}</div>
            </div>
          ))}
        </div>
      )}

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <div style={{ position: "relative" as const }}>
            <Search size={13} style={{ position: "absolute" as const, left: 9, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search guests..."
              style={{ paddingLeft: 28, padding: "7px 10px 7px 28px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", width: 200 }} />
          </div>
          <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)}
            style={{ padding: "7px 10px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", background: "#fff" }}>
            <option value="ALL">All guests</option>
            {Object.keys(GUEST_STATUS).map(s => <option key={s} value={s}>{s.replace("_"," ")}</option>)}
          </select>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={() => setShowTiers(true)}
            style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 14px", background: "#F1F5F9", color: "#374151", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            <Tag size={13} /> Manage Tiers ({(tiers as any[]).length})
          </button>
          <button onClick={() => setShowCheckIn(true)}
            style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 14px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            <QrCode size={13} /> Check-in
          </button>
          <button onClick={exportCSV}
            style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 14px", background: "#F1F5F9", color: "#374151", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, cursor: "pointer" }}>
            <Download size={13} /> Export CSV
          </button>
          <button onClick={() => { setShowRegister(true); setError("") }}
            style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 16px", background: "#0284C7", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={13} /> Register Guest
          </button>
        </div>
      </div>

      {/* Tier summary chips */}
      {(tiers as any[]).length > 0 && (
        <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap" }}>
          {(tiers as any[]).map((t: any) => (
            <div key={t.id} style={{ padding: "5px 12px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 20, fontSize: 12 }}>
              <span style={{ fontWeight: 700, color: "#0F172A" }}>{t.name}</span>
              <span style={{ color: "#94A3B8", marginLeft: 6 }}>{t.quantitySold}/{t.quantity} sold</span>
              {t.price > 0 && <span style={{ color: "#0D9488", marginLeft: 6, fontWeight: 600 }}>{fmtR(t.price)}</span>}
              {t.available === 0 && <span style={{ background: "#FEF2F2", color: "#DC2626", padding: "0 5px", borderRadius: 10, fontSize: 10, marginLeft: 6, fontWeight: 700 }}>SOLD OUT</span>}
            </div>
          ))}
        </div>
      )}

      {/* Guest list */}
      {guestsLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading guests...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "#94A3B8" }}>
          <Users size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No guests yet</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Register the first guest or share the event link.</div>
        </div>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Ticket #","Guest","Tier","Status","Payment","Checked in",""].map(h => (
                  <th key={h} style={{ padding: "10px 14px", textAlign: "left" as const, fontWeight: 600, color: "#64748B", fontSize: 12, whiteSpace: "nowrap" as const }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((g: any, i: number) => {
                const gs = GUEST_STATUS[g.status]  ?? { color: "#64748B", bg: "#F1F5F9" }
                const ps = PAY_STATUS[g.paymentStatus] ?? { color: "#64748B", bg: "#F1F5F9" }
                return (
                  <tr key={g.id} style={{ borderBottom: "1px solid #F1F5F9", background: i % 2 === 0 ? "#fff" : "#FAFAFA" }}>
                    <td style={{ padding: "10px 14px", fontFamily: "monospace", fontSize: 11, color: "#64748B" }}>{g.ticketNumber}</td>
                    <td style={{ padding: "10px 14px" }}>
                      <div style={{ fontWeight: 600, color: "#0F172A" }}>{g.fullName}</div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>{g.email}{g.phone ? ` · ${g.phone}` : ""}</div>
                      {g.dietaryRequirements && <div style={{ fontSize: 10, color: "#D97706", marginTop: 1 }}>{g.dietaryRequirements}</div>}
                    </td>
                    <td style={{ padding: "10px 14px", color: "#64748B" }}>{g.tierName ?? "—"}</td>
                    <td style={{ padding: "10px 14px" }}>
                      <span style={{ background: gs.bg, color: gs.color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{g.status.replace("_"," ")}</span>
                    </td>
                    <td style={{ padding: "10px 14px" }}>
                      <div><span style={{ background: ps.bg, color: ps.color, padding: "2px 7px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{g.paymentStatus}</span></div>
                      {g.amountPaid > 0 && <div style={{ fontSize: 11, color: "#0D9488", marginTop: 2, fontWeight: 700 }}>{fmtR(g.amountPaid)}</div>}
                    </td>
                    <td style={{ padding: "10px 14px", fontSize: 11, color: "#64748B" }}>
                      {g.checkedInAt ? <span style={{ display: "flex", alignItems: "center", gap: 4, color: "#166534" }}><UserCheck size={12} />{fmtDT(g.checkedInAt)}</span> : "—"}
                    </td>
                    <td style={{ padding: "10px 14px" }}>
                      {!["CANCELLED","CHECKED_IN"].includes(g.status) && (
                        <button onClick={() => setConfirmTarget({ id: g.id, name: g.fullName })}
                          style={{ padding: "4px 10px", background: "none", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 11, color: "#94A3B8", cursor: "pointer" }}>
                          <UserX size={11} />
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
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.65)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(3px)" }}>
          <div style={{ background: "#fff", borderRadius: 18, width: 820, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 24px 70px rgba(0,0,0,0.28)", display: "flex", flexDirection: "column" }}>

            {/* Header */}
            <div style={{ padding: "22px 28px 18px", borderBottom: "1px solid #E2E8F0", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 18, fontWeight: 800, display: "flex", alignItems: "center", gap: 8 }}>
                  <QrCode size={20} color="#0284C7" /> QR Check-in
                </h3>
                <p style={{ margin: "3px 0 0", fontSize: 12, color: "#64748B" }}>
                  Scan a QR code, type a ticket number (e.g. EVT-2026-00001-0001), or click a guest row to check in
                </p>
              </div>
              <button onClick={() => { setShowCheckIn(false); setCheckInResult(null); setQrInput("") }}
                style={{ background: "#F1F5F9", border: "none", cursor: "pointer", color: "#64748B", width: 32, height: 32, borderRadius: "50%", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <X size={16} />
              </button>
            </div>

            <div style={{ display: "flex", flex: 1 }}>
              {/* Left — scanner panel */}
              <div style={{ width: 300, padding: "22px 24px", borderRight: "1px solid #E2E8F0", flexShrink: 0 }}>
                {/* Live counter */}
                <div style={{ textAlign: "center", marginBottom: 20, padding: "16px", background: "linear-gradient(135deg, #F0FDF4, #DCFCE7)", border: "1px solid #86EFAC", borderRadius: 12 }}>
                  <div style={{ fontSize: 42, fontWeight: 900, color: "#166534", lineHeight: 1 }}>{stats?.totalCheckedIn ?? 0}</div>
                  <div style={{ fontSize: 12, color: "#166534", marginTop: 4, fontWeight: 600 }}>checked in</div>
                  <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>of {stats?.totalRegistered ?? 0} registered</div>
                  {stats?.totalRegistered > 0 && (
                    <div style={{ marginTop: 10, height: 5, background: "#BBF7D0", borderRadius: 10, overflow: "hidden" }}>
                      <div style={{ height: "100%", width: `${Math.round((stats.totalCheckedIn / stats.totalRegistered) * 100)}%`, background: "#22C55E", borderRadius: 10, transition: "width 0.4s" }} />
                    </div>
                  )}
                </div>

                {/* Input */}
                <input
                  autoFocus
                  value={qrInput}
                  onChange={e => setQrInput(e.target.value)}
                  onKeyDown={e => {
                    if (e.key === "Enter" && qrInput.trim()) {
                      doCheckIn.mutate(qrInput.trim())
                    }
                  }}
                  placeholder="Ticket # or QR code, then Enter"
                  style={{ ...inp, fontSize: 14, textAlign: "center" as const, letterSpacing: "0.03em", marginBottom: 10 }}
                />
                <button
                  disabled={!qrInput.trim() || doCheckIn.isPending}
                  onClick={() => doCheckIn.mutate(qrInput.trim())}
                  style={{ width: "100%", padding: "11px", background: !qrInput.trim() ? "#94A3B8" : "#0284C7", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: !qrInput.trim() ? "default" : "pointer", marginBottom: 18 }}>
                  {doCheckIn.isPending ? "Checking..." : "Check In"}
                </button>

                {/* Result display */}
                {checkInResult && (() => {
                  const cfg: Record<string, { bg: string; color: string; border: string; icon: any; label: string }> = {
                    SUCCESS:            { bg: "#DCFCE7", color: "#166534", border: "#86EFAC", icon: UserCheck,    label: "Welcome!" },
                    ALREADY_CHECKED_IN: { bg: "#FFFBEB", color: "#D97706", border: "#FDE68A", icon: AlertTriangle, label: "Already checked in" },
                    CANCELLED_TICKET:   { bg: "#FEF2F2", color: "#DC2626", border: "#FECACA", icon: UserX,         label: "Cancelled ticket" },
                    NOT_FOUND:          { bg: "#FEF2F2", color: "#DC2626", border: "#FECACA", icon: UserX,         label: "Not found" },
                    ERROR:              { bg: "#FEF2F2", color: "#DC2626", border: "#FECACA", icon: AlertTriangle, label: "Error" },
                  }
                  const c   = cfg[checkInResult.result] ?? cfg.NOT_FOUND
                  const Icon = c.icon
                  return (
                    <div style={{ padding: "16px", background: c.bg, border: `1px solid ${c.border}`, borderRadius: 12, textAlign: "center" as const }}>
                      <Icon size={32} color={c.color} style={{ marginBottom: 8 }} />
                      <div style={{ fontWeight: 800, fontSize: 16, color: c.color, marginBottom: 6 }}>{c.label}</div>
                      {checkInResult.guestName && checkInResult.guestName !== "Unknown" && (
                        <div style={{ fontWeight: 700, fontSize: 17, color: "#0F172A", marginBottom: 2 }}>{checkInResult.guestName}</div>
                      )}
                      {checkInResult.tierName && checkInResult.tierName !== "—" && (
                        <div style={{ fontSize: 13, color: c.color, marginBottom: 4 }}>{checkInResult.tierName}</div>
                      )}
                      {checkInResult.ticketNumber && checkInResult.ticketNumber !== "—" && (
                        <div style={{ fontFamily: "monospace", fontSize: 11, color: c.color }}>{checkInResult.ticketNumber}</div>
                      )}
                      {checkInResult.result === "SUCCESS" && (
                        <div style={{ marginTop: 10, fontSize: 11, color: "#166534", fontWeight: 600 }}>
                          Total checked in: {checkInResult.totalCheckedIn}
                        </div>
                      )}
                    </div>
                  )
                })()}
              </div>

              {/* Right — guest list for manual tap check-in */}
              <div style={{ flex: 1, padding: "18px 24px", overflowY: "auto" }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 12 }}>
                  Click any guest to check them in
                </div>
                {(guests as any[])
                  .filter(g => !["CANCELLED","NO_SHOW"].includes(g.status))
                  .sort((a: any, b: any) => a.fullName.localeCompare(b.fullName))
                  .map((g: any) => {
                    const isCheckedIn = g.status === "CHECKED_IN"
                    return (
                      <div
                        key={g.id}
                        onClick={() => {
                          if (!isCheckedIn) {
                            // Use the actual qrCode from the guest record
                            doCheckIn.mutate(g.qrCode)
                          }
                        }}
                        style={{
                          display: "flex", alignItems: "center", justifyContent: "space-between",
                          padding: "10px 14px", marginBottom: 6, borderRadius: 9,
                          border: `1px solid ${isCheckedIn ? "#86EFAC" : "#E2E8F0"}`,
                          background: isCheckedIn ? "#F0FDF4" : "#fff",
                          cursor: isCheckedIn ? "default" : "pointer",
                          transition: "all 0.15s",
                          opacity: isCheckedIn ? 0.7 : 1,
                        }}
                        onMouseEnter={e => { if (!isCheckedIn) (e.currentTarget as HTMLElement).style.background = "#F0F9FF" }}
                        onMouseLeave={e => { if (!isCheckedIn) (e.currentTarget as HTMLElement).style.background = "#fff" }}
                      >
                        <div>
                          <div style={{ fontWeight: 600, fontSize: 14, color: "#0F172A", display: "flex", alignItems: "center", gap: 7 }}>
                            {g.fullName}
                            {isCheckedIn && <UserCheck size={13} color="#166534" />}
                          </div>
                          <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 1 }}>
                            {g.ticketNumber}
                            {g.tierName && g.tierName !== "—" && ` · ${g.tierName}`}
                          </div>
                        </div>
                        {isCheckedIn ? (
                          <span style={{ background: "#DCFCE7", color: "#166534", padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>In</span>
                        ) : (
                          <span style={{ background: "#EFF6FF", color: "#0284C7", padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>Check in</span>
                        )}
                      </div>
                    )
                  })}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Register guest modal */}
      {showRegister && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Register Guest</h3>
              <button onClick={() => setShowRegister(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              {(tiers as any[]).length > 0 && (
                <div style={{ gridColumn: "1/-1" }}>
                  <label style={lbl}>Ticket tier</label>
                  <select value={guestForm.tierId} onChange={e => gf("tierId", e.target.value)} style={{ ...inp, background: "#fff" }}>
                    <option value="">No tier / general admission</option>
                    {(tiers as any[]).filter((t: any) => t.available > 0).map((t: any) => (
                      <option key={t.id} value={t.id}>{t.name} — {t.price > 0 ? fmtR(t.price) : "Free"} ({t.available} left)</option>
                    ))}
                  </select>
                </div>
              )}
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Full name *</label>
                <input autoFocus value={guestForm.fullName} onChange={e => gf("fullName", e.target.value)} placeholder="Thabo Modise" style={inp} />
              </div>
              <div>
                <label style={lbl}>Email</label>
                <input type="email" value={guestForm.email} onChange={e => gf("email", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Phone</label>
                <input value={guestForm.phone} onChange={e => gf("phone", e.target.value)} placeholder="+27 82 ..." style={inp} />
              </div>
              <div>
                <label style={lbl}>Company / organisation</label>
                <input value={guestForm.company} onChange={e => gf("company", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Dietary requirements</label>
                <input value={guestForm.dietaryRequirements} onChange={e => gf("dietaryRequirements", e.target.value)} placeholder="Halaal, vegetarian, etc." style={inp} />
              </div>
              <div>
                <label style={lbl}>Amount paid (R)</label>
                <input type="number" value={guestForm.amountPaid} onChange={e => gf("amountPaid", e.target.value)} placeholder="0.00" style={inp} />
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Notes</label>
                <input value={guestForm.notes} onChange={e => gf("notes", e.target.value)} style={inp} />
              </div>
            </div>
            {error && <div style={{ marginTop: 12, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowRegister(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer" }}>Cancel</button>
              <button disabled={!guestForm.fullName || registerGuest.isPending}
                onClick={() => registerGuest.mutate({
                  tierId: guestForm.tierId || null,
                  fullName: guestForm.fullName,
                  email: guestForm.email || null,
                  phone: guestForm.phone || null,
                  company: guestForm.company || null,
                  dietaryRequirements: guestForm.dietaryRequirements || null,
                  amountPaid: guestForm.amountPaid ? parseFloat(guestForm.amountPaid) : 0,
                  notes: guestForm.notes || null,
                })}
                style={{ padding: "9px 22px", background: "#0284C7", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {registerGuest.isPending ? "Registering..." : "Register Guest"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Manage tiers modal */}
      {showTiers && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 580, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Ticket Tiers</h3>
              <button onClick={() => setShowTiers(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            {/* Existing tiers */}
            {(tiers as any[]).length > 0 && (
              <div style={{ marginBottom: 20 }}>
                {(tiers as any[]).map((t: any) => (
                  <div key={t.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "12px 16px", border: "1px solid #E2E8F0", borderRadius: 9, marginBottom: 8, background: t.available === 0 ? "#FFF8F8" : "#fff" }}>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{t.name}</div>
                      <div style={{ fontSize: 12, color: "#64748B" }}>
                        {t.price > 0 ? fmtR(t.price) : "Free"} · {t.quantitySold}/{t.quantity} sold · {t.quantityCheckedIn} checked in
                      </div>
                    </div>
                    <div style={{ textAlign: "right" as const }}>
                      {t.available === 0 ? (
                        <span style={{ background: "#FEF2F2", color: "#DC2626", padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>SOLD OUT</span>
                      ) : (
                        <span style={{ background: "#DCFCE7", color: "#166534", padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{t.available} available</span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Add tier form */}
            <div style={{ borderTop: "1px solid #E2E8F0", paddingTop: 18 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: "#374151", marginBottom: 14 }}>Add ticket tier</div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <div><label style={lbl}>Tier name *</label><input value={tierForm.name} onChange={e => tf("name", e.target.value)} placeholder="General Admission" style={inp} /></div>
                <div><label style={lbl}>Price (R, 0 = free)</label><input type="number" value={tierForm.price} onChange={e => tf("price", e.target.value)} placeholder="0" style={inp} /></div>
                <div><label style={lbl}>Quantity *</label><input type="number" value={tierForm.quantity} onChange={e => tf("quantity", e.target.value)} placeholder="100" style={inp} /></div>
                <div><label style={lbl}>Description</label><input value={tierForm.description} onChange={e => tf("description", e.target.value)} style={inp} /></div>
                <div><label style={lbl}>Sale start</label><input type="datetime-local" value={tierForm.saleStart} onChange={e => tf("saleStart", e.target.value)} style={inp} /></div>
                <div><label style={lbl}>Sale end</label><input type="datetime-local" value={tierForm.saleEnd} onChange={e => tf("saleEnd", e.target.value)} style={inp} /></div>
              </div>
              {error && <div style={{ marginTop: 10, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
              <button disabled={!tierForm.name || !tierForm.quantity || createTier.isPending}
                onClick={() => createTier.mutate({
                  name: tierForm.name, description: tierForm.description || null,
                  price: tierForm.price ? parseFloat(tierForm.price) : 0,
                  quantity: parseInt(tierForm.quantity),
                  saleStart: tierForm.saleStart || null, saleEnd: tierForm.saleEnd || null,
                })}
                style={{ marginTop: 14, padding: "9px 22px", background: "#0284C7", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {createTier.isPending ? "Adding..." : "Add Tier"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Custom cancel-registration confirmation modal */}
      {confirmTarget && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 2000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 380, boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 16 }}>
              <div style={{ width: 40, height: 40, borderRadius: "50%", background: "#FEF2F2", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                <UserX size={20} color="#DC2626" />
              </div>
              <div>
                <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>Cancel registration?</div>
                <div style={{ fontSize: 13, color: "#64748B", marginTop: 2 }}>
                  {confirmTarget.name}'s ticket will be marked as cancelled. This cannot be undone.
                </div>
              </div>
            </div>
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button
                onClick={() => setConfirmTarget(null)}
                style={{ padding: "8px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151", fontWeight: 500 }}>
                Keep registration
              </button>
              <button
                onClick={() => { cancelGuest.mutate(confirmTarget.id); setConfirmTarget(null) }}
                style={{ padding: "8px 18px", background: "#DC2626", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                Cancel registration
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
