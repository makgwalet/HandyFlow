// src/pages/bookings/BookingsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, X, Calendar, Clock, User, Phone, Mail,
  ChevronRight, Briefcase, AlertCircle, Users,
} from "lucide-react"

interface Booking {
  id: string; bookingNumber: string; serviceId: string; serviceName: string
  staffId: string; staffName: string; clientName: string; clientEmail: string
  clientPhone: string; bookingDate: string; startTime: string; endTime: string
  durationMinutes: number; status: string; price: number; notes: string; invoiceId: string | null
}
interface Service { id: string; name: string; durationMinutes: number; price: number; color: string }
interface Staff   { id: string; name: string }
interface Slot    { startTime: string; endTime: string; displayLabel: string }

const STATUS_STYLE: Record<string, { color: string; bg: string; label: string }> = {
  PENDING:     { color: "#D97706", bg: "#FFFBEB", label: "Pending" },
  CONFIRMED:   { color: "#1D4ED8", bg: "#EFF6FF", label: "Confirmed" },
  IN_PROGRESS: { color: "#7C3AED", bg: "#F5F3FF", label: "In Progress" },
  COMPLETED:   { color: "#166534", bg: "#DCFCE7", label: "Completed" },
  CANCELLED:   { color: "#DC2626", bg: "#FEF2F2", label: "Cancelled" },
  NO_SHOW:     { color: "#64748B", bg: "#F8FAFC", label: "No Show" },
}

const STATUS_ORDER = ["PENDING", "CONFIRMED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "NO_SHOW"]

const EMPTY_FORM = { serviceId: "", staffId: "", clientName: "", clientEmail: "", clientPhone: "", bookingDate: "", startTime: "", notes: "" }

export default function BookingsTab() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState("")
  const [dateFilter, setDateFilter]     = useState("")
  const [showCreate, setShowCreate]     = useState(false)
  const [selected, setSelected]         = useState<Booking | null>(null)
  const [cancelReason, setCancelReason] = useState("")
  const [showCancel, setShowCancel]     = useState(false)
  const [form, setForm]                 = useState(EMPTY_FORM)
  const [formErrors, setFormErrors]     = useState<Record<string, string>>({})
  const [createError, setCreateError]   = useState("")

  // ── Validation ──────────────────────────────────────────────────────────────
  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.serviceId)   errs.serviceId   = "Please select a service"
    if (!form.clientName.trim()) errs.clientName = "Client name is required"
    if (!form.bookingDate) errs.bookingDate = "Please select a date"
    if (!form.startTime)   errs.startTime   = "Please select a time slot"
    if (form.clientEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.clientEmail))
      errs.clientEmail = "Invalid email address"
    if (form.clientPhone && !/^(\+|0)[\d\s\-]{7,}$/.test(form.clientPhone))
      errs.clientPhone = "Phone must start with + or 0"
    setFormErrors(errs)
    return Object.keys(errs).length === 0
  }

  // ── Queries ─────────────────────────────────────────────────────────────────
  const { data: bookingsPage, isLoading, isError } = useQuery({
    queryKey: ["bookings", statusFilter, dateFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "100" })
      if (statusFilter) params.set("status", statusFilter)
      if (dateFilter)   params.set("date", dateFilter)
      const res = await apiClient.get(`/api/v1/bookings?${params}`)
      return res.data?.data ?? res.data
    },
  })

  const { data: services = [] } = useQuery<Service[]>({
    queryKey: ["booking-services"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/bookings/services")
      return (res.data?.data ?? res.data) as Service[]
    },
  })

  const { data: staff = [] } = useQuery<Staff[]>({
    queryKey: ["booking-staff"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/bookings/staff")
      return (res.data?.data ?? res.data) as Staff[]
    },
  })

  const { data: slots = [] } = useQuery<Slot[]>({
    queryKey: ["booking-slots", form.serviceId, form.bookingDate, form.staffId],
    queryFn: async () => {
      if (!form.serviceId || !form.bookingDate) return []
      const params = new URLSearchParams({ serviceId: form.serviceId, date: form.bookingDate })
      if (form.staffId) params.set("staffId", form.staffId)
      const res = await apiClient.get(`/api/v1/bookings/available-slots?${params}`)
      return (res.data?.data ?? res.data) as Slot[]
    },
    enabled: !!form.serviceId && !!form.bookingDate,
  })

  const bookings: Booking[] = bookingsPage?.content ?? bookingsPage ?? []

  // ── Mutations ────────────────────────────────────────────────────────────────
  const createBooking = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/bookings", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["bookings"] })
      setShowCreate(false); setForm(EMPTY_FORM); setFormErrors({}); setCreateError("")
    },
    onError: (e: any) => {
      setCreateError(e.response?.data?.message ?? "Failed to create booking. Please try again.")
    },
  })

  const statusAction = useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: any }) =>
      apiClient.post(`/api/v1/bookings/${id}/${action}`, body ?? {}),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ["bookings"] })
      if (vars.action === "cancel") { setShowCancel(false); setSelected(null) }
      else {
        // Refresh the selected booking
        qc.invalidateQueries({ queryKey: ["booking", selected?.id] })
        setSelected(null)
      }
    },
  })

  // ── Helpers ─────────────────────────────────────────────────────────────────
  const fmtTime = (t: string) => t?.substring(0, 5) ?? "—"
  const fmtR    = (n: number) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

  const nextActions = (status: string) => {
    if (status === "PENDING")     return [{ label: "Confirm",  action: "confirm",  color: "#1D4ED8", bg: "#EFF6FF" }, { label: "Cancel", action: "cancel", color: "#DC2626", bg: "#FEF2F2" }]
    if (status === "CONFIRMED")   return [{ label: "Start",    action: "start",    color: "#7C3AED", bg: "#F5F3FF" }, { label: "No Show", action: "no-show", color: "#64748B", bg: "#F8FAFC" }, { label: "Cancel", action: "cancel", color: "#DC2626", bg: "#FEF2F2" }]
    if (status === "IN_PROGRESS") return [{ label: "Complete", action: "complete", color: "#166534", bg: "#DCFCE7" }]
    return []
  }

  const inpStyle = (key: string): React.CSSProperties => ({
    ...inputStyle,
    ...(formErrors[key] ? { borderColor: "#DC2626", background: "#FFF5F5" } : {}),
  })

  const FieldErr = ({ name }: { name: string }) =>
    formErrors[name] ? (
      <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }}>
        <AlertCircle size={12} />{formErrors[name]}
      </div>
    ) : null

  return (
    <div>
      {/* Filters */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
          <button onClick={() => setStatusFilter("")} style={filterBtn(statusFilter === "")}>All</button>
          {STATUS_ORDER.map(s => (
            <button key={s} onClick={() => setStatusFilter(s)} style={filterBtn(statusFilter === s)}>
              {STATUS_STYLE[s]?.label}
            </button>
          ))}
          <input type="date" value={dateFilter} onChange={e => setDateFilter(e.target.value)}
            style={{ padding: "6px 10px", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 13, color: "#374151" }} />
          {dateFilter && (
            <button onClick={() => setDateFilter("")}
              style={{ padding: "6px 10px", fontSize: 12, color: "#DC2626", background: "none", border: "none", cursor: "pointer" }}>
              Clear
            </button>
          )}
        </div>
        <button onClick={() => { setShowCreate(true); setForm(EMPTY_FORM); setFormErrors({}); setCreateError("") }} style={btnPrimary}>
          <Plus size={15} /> New Booking
        </button>
      </div>

      {/* Stats */}
      <div style={{ display: "flex", gap: 10, marginBottom: 20, flexWrap: "wrap" }}>
        {["PENDING", "CONFIRMED", "COMPLETED"].map(s => {
          const count = bookings.filter(b => b.status === s).length
          const ss    = STATUS_STYLE[s]
          return (
            <div key={s} style={{ background: ss.bg, borderRadius: 9, padding: "10px 18px", cursor: "pointer" }} onClick={() => setStatusFilter(s)}>
              <div style={{ fontSize: 22, fontWeight: 800, color: ss.color }}>{count}</div>
              <div style={{ fontSize: 11, color: ss.color, opacity: 0.8, fontWeight: 600 }}>{ss.label}</div>
            </div>
          )
        })}
      </div>

      {/* List */}
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 60, color: "#94A3B8" }}>Loading bookings...</div>
      ) : isError ? (
        <div style={{ textAlign: "center", padding: 60 }}>
          <AlertCircle size={32} color="#DC2626" style={{ marginBottom: 10 }} />
          <div style={{ fontWeight: 600, color: "#DC2626" }}>Failed to load bookings</div>
        </div>
      ) : bookings.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Calendar size={36} color="#CBD5E1" style={{ marginBottom: 12 }} />
          <div style={{ fontWeight: 600, color: "#475569", marginBottom: 4 }}>
            {statusFilter || dateFilter ? "No bookings match your filters" : "No bookings yet"}
          </div>
          <div style={{ fontSize: 13 }}>Create your first booking to get started.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {bookings.map((b: Booking) => {
            const ss = STATUS_STYLE[b.status] ?? STATUS_STYLE.PENDING
            return (
              <div key={b.id} onClick={() => setSelected(b)}
                style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "14px 18px", border: "1px solid #E2E8F0", borderRadius: 10, cursor: "pointer", background: "#fff" }}
                onMouseEnter={e => (e.currentTarget.style.borderColor = "#0D9488")}
                onMouseLeave={e => (e.currentTarget.style.borderColor = "#E2E8F0")}>
                <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                  {/* Date block */}
                  <div style={{ textAlign: "center", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, padding: "6px 12px", minWidth: 52, flexShrink: 0 }}>
                    <div style={{ fontSize: 18, fontWeight: 800, color: "#0F172A", lineHeight: 1 }}>{b.bookingDate?.split("-")[2]}</div>
                    <div style={{ fontSize: 9, color: "#64748B", fontWeight: 600, textTransform: "uppercase" as const }}>
                      {new Date(b.bookingDate + "T00:00:00").toLocaleString("en-ZA", { month: "short" })}
                    </div>
                  </div>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                      <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{b.clientName}</span>
                      <span style={{ background: ss.bg, color: ss.color, padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{ss.label}</span>
                      <span style={{ fontSize: 11, color: "#94A3B8" }}>#{b.bookingNumber}</span>
                    </div>
                    <div style={{ display: "flex", gap: 14, fontSize: 12, color: "#64748B" }}>
                      <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Briefcase size={11} />{b.serviceName}</span>
                      <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Clock size={11} />{fmtTime(b.startTime)} – {fmtTime(b.endTime)}</span>
                      {b.staffName && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><User size={11} />{b.staffName}</span>}
                    </div>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 12, flexShrink: 0 }}>
                  <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{fmtR(b.price)}</span>
                  <ChevronRight size={15} color="#94A3B8" />
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* ── Booking detail modal ─────────────────────────────────────────── */}
      {selected && !showCancel && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 520, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
              <div>
                <div style={{ fontSize: 11, color: "#94A3B8", marginBottom: 4 }}>#{selected.bookingNumber}</div>
                <h3 style={{ margin: "0 0 6px", fontSize: 18, fontWeight: 800, color: "#0F172A" }}>{selected.clientName}</h3>
                <span style={{ background: STATUS_STYLE[selected.status]?.bg, color: STATUS_STYLE[selected.status]?.color, padding: "3px 12px", borderRadius: 20, fontSize: 12, fontWeight: 700 }}>
                  {STATUS_STYLE[selected.status]?.label ?? selected.status}
                </span>
              </div>
              <button onClick={() => setSelected(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 20 }}>
              {[
                { icon: <Calendar size={14} />, label: "Date",    value: selected.bookingDate },
                { icon: <Clock size={14} />,    label: "Time",    value: `${fmtTime(selected.startTime)} – ${fmtTime(selected.endTime)} (${selected.durationMinutes} min)` },
                { icon: <Briefcase size={14} />,label: "Service", value: selected.serviceName },
                { icon: <User size={14} />,     label: "Staff",   value: selected.staffName ?? "—" },
                ...(selected.clientEmail ? [{ icon: <Mail size={14} />,  label: "Email", value: selected.clientEmail }] : []),
                ...(selected.clientPhone ? [{ icon: <Phone size={14} />, label: "Phone", value: selected.clientPhone }] : []),
              ].map(({ icon, label, value }) => (
                <div key={label} style={{ display: "flex", alignItems: "flex-start", gap: 10 }}>
                  <span style={{ color: "#94A3B8", marginTop: 2, flexShrink: 0 }}>{icon}</span>
                  <div>
                    <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, marginBottom: 1 }}>{label}</div>
                    <div style={{ fontSize: 13, color: "#0F172A" }}>{value}</div>
                  </div>
                </div>
              ))}
            </div>

            {selected.notes && (
              <div style={{ marginBottom: 20, padding: "10px 14px", background: "#FFFBEB", border: "1px solid #FEF3C7", borderRadius: 8 }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#92400E", marginBottom: 4 }}>NOTES</div>
                <div style={{ fontSize: 13, color: "#78350F" }}>{selected.notes}</div>
              </div>
            )}

            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div style={{ fontSize: 20, fontWeight: 800, color: "#0F172A" }}>{fmtR(selected.price)}</div>
              <div style={{ display: "flex", gap: 8 }}>
                {nextActions(selected.status).map(({ label, action, color, bg }) => (
                  <button key={action}
                    onClick={() => {
                      if (action === "cancel") { setShowCancel(true) }
                      else statusAction.mutate({ id: selected.id, action })
                    }}
                    style={{ padding: "8px 16px", borderRadius: 8, fontSize: 13, cursor: "pointer", border: "none", fontWeight: 600, background: bg, color }}>
                    {label}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── Cancel confirmation modal ────────────────────────────────────── */}
      {showCancel && selected && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1001, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)", textAlign: "center" }}>
            <div style={{ width: 52, height: 52, borderRadius: "50%", background: "#FEF2F2", border: "2px solid #FECACA", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px" }}>
              <X size={22} color="#DC2626" />
            </div>
            <h3 style={{ margin: "0 0 6px", fontSize: 18, fontWeight: 700, color: "#0F172A" }}>Cancel Booking?</h3>
            <div style={{ display: "flex", alignItems: "center", gap: 8, background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 40, padding: "6px 14px", margin: "10px auto", width: "fit-content" }}>
              <Users size={13} color="#DC2626" />
              <span style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{selected.clientName} — {selected.serviceName}</span>
            </div>
            <p style={{ fontSize: 13, color: "#64748B", margin: "0 0 16px", lineHeight: 1.6 }}>
              Provide a reason for cancellation so it's recorded for reporting.
            </p>
            <div style={{ marginBottom: 16, textAlign: "left" }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Reason (optional)</label>
              <input value={cancelReason} onChange={e => setCancelReason(e.target.value)}
                placeholder="e.g. Client requested cancellation"
                style={{ ...inputStyle, width: "100%" }} />
            </div>
            <div style={{ display: "flex", gap: 10 }}>
              <button onClick={() => { setShowCancel(false); setCancelReason("") }}
                style={{ flex: 1, padding: "10px", border: "1.5px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, fontWeight: 600, cursor: "pointer", color: "#374151" }}>
                Keep booking
              </button>
              <button
                onClick={() => statusAction.mutate({ id: selected.id, action: "cancel", body: { reason: cancelReason || "Cancelled" } })}
                disabled={statusAction.isPending}
                style={{ flex: 1, padding: "10px", border: "none", borderRadius: 9, background: "#DC2626", color: "#fff", fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {statusAction.isPending ? "Cancelling..." : "Yes, cancel"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Create booking modal ─────────────────────────────────────────── */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "88vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>New Booking</h3>
              <button onClick={() => { setShowCreate(false); setForm(EMPTY_FORM); setFormErrors({}); setCreateError("") }}
                style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>

              {/* Service */}
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Service *</label>
                <select value={form.serviceId}
                  onChange={e => { setForm(f => ({ ...f, serviceId: e.target.value, startTime: "" })); setFormErrors(f => { const n = { ...f }; delete n.serviceId; return n }) }}
                  style={inpStyle("serviceId")}>
                  <option value="">Select service...</option>
                  {services.map(s => <option key={s.id} value={s.id}>{s.name} — {s.durationMinutes} min</option>)}
                </select>
                <FieldErr name="serviceId" />
              </div>

              {/* Staff */}
              <div>
                <label style={lbl}>Staff <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <select value={form.staffId} onChange={e => setForm(f => ({ ...f, staffId: e.target.value, startTime: "" }))} style={inputStyle}>
                  <option value="">Any available staff</option>
                  {staff.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>

              {/* Date */}
              <div>
                <label style={lbl}>Date *</label>
                <input type="date" value={form.bookingDate}
                  min={new Date().toISOString().split("T")[0]}
                  onChange={e => { setForm(f => ({ ...f, bookingDate: e.target.value, startTime: "" })); setFormErrors(f => { const n = { ...f }; delete n.bookingDate; return n }) }}
                  style={inpStyle("bookingDate")} />
                <FieldErr name="bookingDate" />
              </div>

              {/* Slots */}
              {form.serviceId && form.bookingDate && (
                <div style={{ gridColumn: "1 / -1" }}>
                  <label style={lbl}>Available slots *</label>
                  {slots.length === 0 ? (
                    <div style={{ padding: "12px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                      <AlertCircle size={14} />No available slots. Try another date or staff member.
                    </div>
                  ) : (
                    <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
                      {slots.map(slot => (
                        <button key={slot.startTime} type="button"
                          onClick={() => { setForm(f => ({ ...f, startTime: slot.startTime })); setFormErrors(f => { const n = { ...f }; delete n.startTime; return n }) }}
                          style={{
                            padding: "7px 14px", borderRadius: 8, fontSize: 13, cursor: "pointer",
                            border: form.startTime === slot.startTime ? "2px solid #0D9488" : "1.5px solid #E2E8F0",
                            background: form.startTime === slot.startTime ? "#F0FDF4" : "#fff",
                            color: form.startTime === slot.startTime ? "#0D9488" : "#374151",
                            fontWeight: form.startTime === slot.startTime ? 700 : 400,
                          }}>
                          {slot.displayLabel || `${slot.startTime?.substring(0, 5)} – ${slot.endTime?.substring(0, 5)}`}
                        </button>
                      ))}
                    </div>
                  )}
                  <FieldErr name="startTime" />
                </div>
              )}

              {/* Client name */}
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Client name *</label>
                <input value={form.clientName}
                  onChange={e => { setForm(f => ({ ...f, clientName: e.target.value })); setFormErrors(f => { const n = { ...f }; delete n.clientName; return n }) }}
                  placeholder="John Smith" style={inpStyle("clientName")} />
                <FieldErr name="clientName" />
              </div>

              {/* Email */}
              <div>
                <label style={lbl}>Email <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <input type="email" value={form.clientEmail}
                  onChange={e => { setForm(f => ({ ...f, clientEmail: e.target.value })); setFormErrors(f => { const n = { ...f }; delete n.clientEmail; return n }) }}
                  placeholder="john@example.com" style={inpStyle("clientEmail")} />
                <FieldErr name="clientEmail" />
              </div>

              {/* Phone */}
              <div>
                <label style={lbl}>Phone <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <input value={form.clientPhone}
                  onChange={e => { setForm(f => ({ ...f, clientPhone: e.target.value.replace(/[^\d\s\-+]/g, "") })); setFormErrors(f => { const n = { ...f }; delete n.clientPhone; return n }) }}
                  placeholder="+27 82 123 4567" style={inpStyle("clientPhone")} />
                <FieldErr name="clientPhone" />
              </div>

              {/* Notes */}
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Notes <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
                  rows={2} placeholder="Any special requirements..."
                  style={{ ...inputStyle, resize: "vertical" as const }} />
              </div>
            </div>

            {createError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={15} color="#DC2626" style={{ flexShrink: 0 }} />{createError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => { setShowCreate(false); setForm(EMPTY_FORM); setFormErrors({}); setCreateError("") }}
                style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>
                Cancel
              </button>
              <button onClick={() => { if (validate()) createBooking.mutate({ ...form, staffId: form.staffId || null }) }}
                disabled={createBooking.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {createBooking.isPending ? "Creating..." : "Create booking"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const filterBtn = (active: boolean): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 6, fontSize: 12, cursor: "pointer",
  border: active ? "1px solid #0D9488" : "1px solid #E2E8F0",
  background: active ? "#F0FDF4" : "#fff",
  color: active ? "#0D9488" : "#64748B", fontWeight: active ? 600 : 400,
})
const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }
const lbl: React.CSSProperties       = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff", outline: "none" }