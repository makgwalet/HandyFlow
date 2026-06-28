// src/pages/bookings/BookingsTab.tsx
//
// FIXES vs original:
// 1. Reschedule action added to nextActions() — new modal with date + time slot picker
// 2. Loading skeletons replace blank-page flash
// 3. CRM client autofill — search by phone/email before typing fresh
// 4. size=100 replaced by real server-side pagination
// 5. cancellationReason shown in detail modal
// 6. Rescheduled badge shown when booking has rescheduled history
import { useState, useEffect } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, X, Calendar, Clock, User, Phone, Mail,
  ChevronRight, ChevronLeft, Briefcase, AlertCircle,
  RotateCcw,
} from "lucide-react"

interface Booking {
  id: string; bookingNumber: string; serviceId: string; serviceName: string
  staffId: string; staffName: string; clientName: string; clientEmail: string
  clientPhone: string; bookingDate: string; startTime: string; endTime: string
  durationMinutes: number; status: string; price: number; notes: string
  invoiceId: string | null; cancellationReason: string | null
  originalBookingDate: string | null; rescheduledAt: string | null
  reminderSent: boolean
}
interface Service { id: string; name: string; durationMinutes: number; price: number; maxAdvanceDays?: number }
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
const EMPTY_FORM   = { serviceId: "", staffId: "", clientName: "", clientEmail: "", clientPhone: "", bookingDate: "", startTime: "", notes: "" }

function Skeleton({ w = "100%", h = 16, mb = 0 }: { w?: string | number; h?: number; mb?: number }) {
  return <div style={{ width: w, height: h, background: "#F1F5F9", borderRadius: 6, marginBottom: mb }} />
}

export default function BookingsTab() {
  const qc = useQueryClient()

  const [statusFilter, setStatusFilter] = useState("")
  const [dateFilter, setDateFilter]     = useState("")
  const [searchInput, setSearchInput]   = useState("")
  const [search, setSearch]             = useState("")
  const [page, setPage]                 = useState(0)
  const PAGE_SIZE = 20

  const [showCreate, setShowCreate]   = useState(false)
  const [selected, setSelected]       = useState<Booking | null>(null)
  const [showCancel, setShowCancel]   = useState(false)
  const [showReschedule, setShowReschedule] = useState(false)
  const [cancelReason, setCancelReason]     = useState("")
  const [rescheduleForm, setRescheduleForm] = useState({ newDate: "", newStartTime: "" })
  const [form, setForm]               = useState(EMPTY_FORM)
  const [formErrors, setFormErrors]   = useState<Record<string, string>>({})
  const [createError, setCreateError] = useState("")
  const [actionError, setActionError] = useState("")

  // Reset page when filters change
  useEffect(() => { setPage(0) }, [statusFilter, dateFilter, search])

  // ── Validation ─────────────────────────────────────────────────────────────
  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.serviceId)        errs.serviceId   = "Please select a service"
    if (!form.clientName.trim()) errs.clientName  = "Client name is required"
    if (!form.bookingDate)      errs.bookingDate  = "Please select a date"
    if (!form.startTime)        errs.startTime    = "Please select a time slot"
    if (form.clientEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.clientEmail))
      errs.clientEmail = "Invalid email address"
    if (form.clientPhone && !/^(\+|0)[\d\s\-]{7,}$/.test(form.clientPhone))
      errs.clientPhone = "Phone must start with + or 0"
    setFormErrors(errs)
    return Object.keys(errs).length === 0
  }

  // ── Queries ─────────────────────────────────────────────────────────────────
  const { data: bookingsPage, isLoading, isError } = useQuery({
    queryKey: ["bookings", statusFilter, dateFilter, search, page],
    queryFn: async () => {
      const params = new URLSearchParams({ size: String(PAGE_SIZE), page: String(page) })
      if (statusFilter) params.set("status", statusFilter)
      if (dateFilter)   params.set("date", dateFilter)
      if (search)       params.set("search", search)
      const res = await apiClient.get(`/api/v1/bookings?${params}`)
      return res.data?.data ?? res.data
    },
    keepPreviousData: true,
  })

  const { data: services = [] } = useQuery<Service[]>({
    queryKey: ["booking-services"],
    queryFn: async () => (await apiClient.get("/api/v1/bookings/services")).data?.data ?? [],
  })

  const { data: staff = [] } = useQuery<Staff[]>({
    queryKey: ["booking-staff"],
    queryFn: async () => (await apiClient.get("/api/v1/bookings/staff")).data?.data ?? [],
  })

  const { data: slots = [] } = useQuery<Slot[]>({
    queryKey: ["booking-slots", form.serviceId, form.bookingDate, form.staffId],
    queryFn: async () => {
      if (!form.serviceId || !form.bookingDate) return []
      const p = new URLSearchParams({ serviceId: form.serviceId, date: form.bookingDate })
      if (form.staffId) p.set("staffId", form.staffId)
      return (await apiClient.get(`/api/v1/bookings/available-slots?${p}`)).data?.data ?? []
    },
    enabled: !!form.serviceId && !!form.bookingDate,
  })

  // Eligible staff for selected service — filters by skill assignments
  // If service has no skill assignments, all active staff are shown (backwards compatible)
  const { data: eligibleStaff = staff } = useQuery<Staff[]>({
    queryKey: ["eligible-staff", form.serviceId],
    queryFn: async () => {
      if (!form.serviceId) return staff
      const res = await apiClient.get(`/api/v1/bookings/services/${form.serviceId}/staff`)
      const assigned: string[] = res.data?.data ?? []
      // If service has no assignments yet, return all staff
      if (assigned.length === 0) return staff
      return staff.filter((s: Staff) => assigned.includes(s.id))
    },
    enabled: !!form.serviceId && staff.length > 0,
    placeholderData: staff,
  })

  const { data: rescheduleSlots = [] } = useQuery<Slot[]>({
    queryKey: ["reschedule-slots", selected?.serviceId, rescheduleForm.newDate, selected?.staffId],
    queryFn: async () => {
      if (!selected?.serviceId || !rescheduleForm.newDate) return []
      const p = new URLSearchParams({ serviceId: selected.serviceId, date: rescheduleForm.newDate })
      if (selected.staffId) p.set("staffId", selected.staffId)
      return (await apiClient.get(`/api/v1/bookings/available-slots?${p}`)).data?.data ?? []
    },
    enabled: !!selected?.serviceId && !!rescheduleForm.newDate,
  })

  const bookings: Booking[]  = bookingsPage?.content ?? []
  const totalPages: number   = bookingsPage?.totalPages ?? 0
  const totalElements: number = bookingsPage?.totalElements ?? 0

  // ── Mutations ───────────────────────────────────────────────────────────────
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["bookings"] })
    qc.invalidateQueries({ queryKey: ["bookings-pending-count"] })
    qc.invalidateQueries({ queryKey: ["bookings-today"] })
  }

  const createBooking = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/bookings", body),
    onSuccess: () => { invalidate(); setShowCreate(false); setForm(EMPTY_FORM); setFormErrors({}); setCreateError("") },
    onError: (e: any) => setCreateError(e.response?.data?.message ?? "Failed to create booking."),
  })

  const statusAction = useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: any }) =>
      apiClient.post(`/api/v1/bookings/${id}/${action}`, body ?? {}),
    onSuccess: (_, vars) => {
      invalidate()
      setActionError("")
      if (vars.action === "cancel")          { setShowCancel(false);    setSelected(null) }
      else if (vars.action === "reschedule") { setShowReschedule(false); setSelected(null) }
      else                                   { setSelected(null) }
    },
    onError: (e: any) => setActionError(e.response?.data?.message ?? "Action failed. Please try again."),
  })

  // ── CRM client autofill ─────────────────────────────────────────────────────
  // WHY? Typing a client's name fresh every time is error-prone and slow.
  // A phone/email search against the CRM fills name + contact fields automatically,
  // matching how Acuity, Calendly, and Fresha work.
  const [clientSearch, setClientSearch] = useState("")
  const [showClientSuggestions, setShowClientSuggestions] = useState(false)

  const { data: clientSuggestions = [] } = useQuery({
    queryKey: ["crm-client-search", clientSearch],
    queryFn: async () => {
      if (clientSearch.length < 2) return []
      const res = await apiClient.get(`/api/v1/crm/customers?search=${encodeURIComponent(clientSearch)}&size=5`)
      return (res.data?.data?.content ?? res.data?.data ?? []) as any[]
    },
    enabled: clientSearch.length >= 2,
  })

  // ── Helpers ─────────────────────────────────────────────────────────────────
  const fmtTime = (t: string) => t?.substring(0, 5) ?? "—"
  const fmtR    = (n: number) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

  // WHY: action buttons returned here instead of inline so they're easy to extend
  const nextActions = (status: string) => {
    if (status === "PENDING")     return [
      { label: "Confirm",   action: "confirm",    color: "#1D4ED8", bg: "#EFF6FF" },
      { label: "Reschedule", action: "reschedule", color: "#7C3AED", bg: "#F5F3FF", icon: RotateCcw },
      { label: "Cancel",    action: "cancel",     color: "#DC2626", bg: "#FEF2F2" },
    ]
    if (status === "CONFIRMED")   return [
      { label: "Start",     action: "start",      color: "#7C3AED", bg: "#F5F3FF" },
      { label: "Reschedule", action: "reschedule", color: "#0D9488", bg: "#F0FDF4", icon: RotateCcw },
      { label: "No Show",   action: "no-show",    color: "#64748B", bg: "#F8FAFC" },
      { label: "Cancel",    action: "cancel",     color: "#DC2626", bg: "#FEF2F2" },
    ]
    if (status === "IN_PROGRESS") return [
      { label: "Complete",  action: "complete",   color: "#166534", bg: "#DCFCE7" },
    ]
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
      {/* ── Filters ──────────────────────────────────────────────────────── */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
          <button onClick={() => setStatusFilter("")} style={filterBtn(statusFilter === "")}>All</button>
          {STATUS_ORDER.map(s => (
            <button key={s} onClick={() => setStatusFilter(s)} style={filterBtn(statusFilter === s)}>
              {STATUS_STYLE[s]?.label}
            </button>
          ))}
          <input
            type="date" value={dateFilter}
            onChange={e => setDateFilter(e.target.value)}
            style={{ padding: "6px 10px", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 13, color: "#374151" }}
          />
          {dateFilter && (
            <button onClick={() => setDateFilter("")}
              style={{ fontSize: 12, color: "#DC2626", background: "none", border: "none", cursor: "pointer", padding: "6px 4px" }}>
              Clear
            </button>
          )}
          {/* Search input with debounce-on-Enter */}
          <input
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            onKeyDown={e => { if (e.key === "Enter") setSearch(searchInput) }}
            placeholder="Search client, phone…"
            style={{ padding: "6px 10px", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 13, color: "#374151", width: 180 }}
          />
          {search && (
            <button onClick={() => { setSearch(""); setSearchInput("") }}
              style={{ fontSize: 12, color: "#DC2626", background: "none", border: "none", cursor: "pointer", padding: "6px 4px" }}>
              Clear search
            </button>
          )}
        </div>
        <button onClick={() => { setShowCreate(true); setForm(EMPTY_FORM); setFormErrors({}); setCreateError(""); setClientSearch("") }}
          style={btnPrimary}>
          <Plus size={15} /> New Booking
        </button>
      </div>

      {/* ── Status summary chips ──────────────────────────────────────────── */}
      <div style={{ display: "flex", gap: 10, marginBottom: 20, flexWrap: "wrap" }}>
        {["PENDING", "CONFIRMED", "COMPLETED"].map(s => {
          const ss    = STATUS_STYLE[s]
          // We use total from the page when the filter matches, else show "—"
          const count = statusFilter === s ? (totalElements ?? "—") : "—"
          return (
            <div key={s} onClick={() => setStatusFilter(s)}
              style={{ background: ss.bg, borderRadius: 9, padding: "10px 18px", cursor: "pointer" }}>
              <div style={{ fontSize: 22, fontWeight: 800, color: ss.color }}>
                {statusFilter === s ? totalElements : "·"}
              </div>
              <div style={{ fontSize: 11, color: ss.color, opacity: 0.8, fontWeight: 600 }}>{ss.label}</div>
            </div>
          )
        })}
      </div>

      {/* ── Booking list ──────────────────────────────────────────────────── */}
      {isLoading ? (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {[1, 2, 3, 4].map(i => (
            <div key={i} style={{ padding: "14px 18px", border: "1px solid #E2E8F0", borderRadius: 10 }}>
              <div style={{ display: "flex", gap: 14, alignItems: "center" }}>
                <Skeleton w={52} h={44} />
                <div style={{ flex: 1 }}>
                  <Skeleton h={14} w="40%" mb={8} />
                  <Skeleton h={11} w="60%" />
                </div>
                <Skeleton w={60} h={16} />
              </div>
            </div>
          ))}
        </div>
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
        </div>
      ) : (
        <>
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {bookings.map((b: Booking) => {
              const ss = STATUS_STYLE[b.status] ?? STATUS_STYLE.PENDING
              return (
                <div
                  key={b.id}
                  onClick={() => { setSelected(b); setActionError("") }}
                  style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "14px 18px", border: "1px solid #E2E8F0", borderRadius: 10, cursor: "pointer", background: "#fff" }}
                  onMouseEnter={e => (e.currentTarget.style.borderColor = "#0D9488")}
                  onMouseLeave={e => (e.currentTarget.style.borderColor = "#E2E8F0")}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                    <div style={{ textAlign: "center", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, padding: "6px 12px", minWidth: 52 }}>
                      <div style={{ fontSize: 18, fontWeight: 800, color: "#0F172A", lineHeight: 1 }}>{b.bookingDate?.split("-")[2]}</div>
                      <div style={{ fontSize: 9, color: "#64748B", fontWeight: 600, textTransform: "uppercase" }}>
                        {new Date(b.bookingDate + "T00:00:00").toLocaleString("en-ZA", { month: "short" })}
                      </div>
                    </div>
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                        <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{b.clientName}</span>
                        <span style={{ background: ss.bg, color: ss.color, padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{ss.label}</span>
                        <span style={{ fontSize: 11, color: "#94A3B8" }}>#{b.bookingNumber}</span>
                        {b.originalBookingDate && (
                          <span title="This booking was rescheduled" style={{ fontSize: 10, color: "#0D9488", background: "#F0FDF4", border: "1px solid #BBF7D0", padding: "1px 6px", borderRadius: 10 }}>
                            rescheduled
                          </span>
                        )}
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

          {/* Pagination */}
          {totalPages > 1 && (
            <div style={{ display: "flex", justifyContent: "center", alignItems: "center", gap: 12, marginTop: 20 }}>
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                style={{ ...navBtn, opacity: page === 0 ? 0.4 : 1 }}>
                <ChevronLeft size={15} /> Previous
              </button>
              <span style={{ fontSize: 13, color: "#64748B" }}>
                Page {page + 1} of {totalPages}
              </span>
              <button
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                style={{ ...navBtn, opacity: page >= totalPages - 1 ? 0.4 : 1 }}>
                Next <ChevronRight size={15} />
              </button>
            </div>
          )}
        </>
      )}

      {/* ── Booking detail modal ─────────────────────────────────────────── */}
      {selected && !showCancel && !showReschedule && (
        <div style={overlay}>
          <div style={{ ...modal, width: 540 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
              <div>
                <div style={{ fontSize: 11, color: "#94A3B8", marginBottom: 4 }}>#{selected.bookingNumber}</div>
                <h3 style={{ margin: "0 0 6px", fontSize: 18, fontWeight: 800, color: "#0F172A" }}>{selected.clientName}</h3>
                <span style={{ background: STATUS_STYLE[selected.status]?.bg, color: STATUS_STYLE[selected.status]?.color, padding: "3px 12px", borderRadius: 20, fontSize: 12, fontWeight: 700 }}>
                  {STATUS_STYLE[selected.status]?.label ?? selected.status}
                </span>
                {selected.originalBookingDate && (
                  <span style={{ marginLeft: 8, fontSize: 11, color: "#0D9488", background: "#F0FDF4", border: "1px solid #BBF7D0", padding: "2px 8px", borderRadius: 10 }}>
                    Rescheduled from {selected.originalBookingDate}
                  </span>
                )}
              </div>
              <button onClick={() => setSelected(null)} style={iconBtn}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 20 }}>
              {[
                { icon: <Calendar size={14} />, label: "Date",    value: selected.bookingDate },
                { icon: <Clock size={14} />,    label: "Time",    value: `${fmtTime(selected.startTime)} – ${fmtTime(selected.endTime)} (${selected.durationMinutes} min)` },
                { icon: <Briefcase size={14} />, label: "Service", value: selected.serviceName },
                { icon: <User size={14} />,     label: "Staff",   value: selected.staffName ?? "—" },
                ...(selected.clientEmail ? [{ icon: <Mail size={14} />,  label: "Email", value: selected.clientEmail }] : []),
                ...(selected.clientPhone ? [{ icon: <Phone size={14} />, label: "Phone", value: selected.clientPhone }] : []),
              ].map(({ icon, label, value }) => (
                <div key={label} style={{ display: "flex", alignItems: "flex-start", gap: 10 }}>
                  <span style={{ color: "#94A3B8", marginTop: 2 }}>{icon}</span>
                  <div>
                    <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", marginBottom: 1 }}>{label}</div>
                    <div style={{ fontSize: 13, color: "#0F172A" }}>{value}</div>
                  </div>
                </div>
              ))}
            </div>

            {selected.notes && (
              <div style={{ marginBottom: 16, padding: "10px 14px", background: "#FFFBEB", border: "1px solid #FEF3C7", borderRadius: 8 }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#92400E", marginBottom: 4 }}>NOTES</div>
                <div style={{ fontSize: 13, color: "#78350F" }}>{selected.notes}</div>
              </div>
            )}

            {selected.cancellationReason && (
              <div style={{ marginBottom: 16, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8 }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#991B1B", marginBottom: 4 }}>CANCELLATION REASON</div>
                <div style={{ fontSize: 13, color: "#7F1D1D" }}>{selected.cancellationReason}</div>
              </div>
            )}

            {/* Reminder status */}
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 14 }}>
              <span style={{
                fontSize: 11, fontWeight: 600, padding: "2px 10px", borderRadius: 20,
                background: selected.reminderSent ? "#F0FDF4" : "#F8FAFC",
                color: selected.reminderSent ? "#166534" : "#94A3B8",
                border: `1px solid ${selected.reminderSent ? "#BBF7D0" : "#E2E8F0"}`,
              }}>
                {selected.reminderSent ? "✓ Reminder sent" : "Reminder pending"}
              </span>
              {selected.originalBookingDate && (
                <span style={{ fontSize: 11, color: "#0D9488", background: "#F0FDF4", border: "1px solid #BBF7D0", padding: "2px 10px", borderRadius: 20 }}>
                  Rescheduled from {selected.originalBookingDate}
                </span>
              )}
            </div>

            {actionError && (
              <div style={{ marginBottom: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", gap: 8 }}>
                <AlertCircle size={14} style={{ flexShrink: 0, marginTop: 1 }} />{actionError}
              </div>
            )}

            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div style={{ fontSize: 20, fontWeight: 800, color: "#0F172A" }}>{fmtR(selected.price)}</div>
              <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                <button
                  onClick={async () => {
                    try {
                      const res = await apiClient.get(`/api/v1/bookings/${selected.id}/confirmation.pdf`, { responseType: "blob" })
                      const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
                      const a = document.createElement("a"); a.href = url
                      a.download = `booking-${selected.bookingNumber}.pdf`
                      document.body.appendChild(a); a.click()
                      document.body.removeChild(a); URL.revokeObjectURL(url)
                    } catch { /* silent */ }
                  }}
                  title="Download confirmation PDF"
                  style={{ padding: "7px 12px", borderRadius: 8, fontSize: 12, cursor: "pointer", border: "1px solid #E2E8F0", background: "#F8FAFC", color: "#374151" }}>
                  ↓ PDF
                </button>
                {nextActions(selected.status).map(({ label, action, color, bg }) => (
                  <button
                    key={action}
                    onClick={() => {
                      if (action === "cancel")     { setShowCancel(true); setActionError("") }
                      else if (action === "reschedule") { setShowReschedule(true); setRescheduleForm({ newDate: "", newStartTime: "" }); setActionError("") }
                      else statusAction.mutate({ id: selected.id, action })
                    }}
                    disabled={statusAction.isPending}
                    style={{ padding: "8px 16px", borderRadius: 8, fontSize: 13, cursor: "pointer", border: "none", fontWeight: 600, background: bg, color }}>
                    {label}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── Reschedule modal ─────────────────────────────────────────────── */}
      {/* WHY a modal and not inline edit?
          Reschedule involves fetching new slots (async), which needs its own
          loading state separate from the detail modal.  A dedicated modal also
          makes the user's intent explicit — they're not just editing a field,
          they're performing a business action that sends an email. */}
      {showReschedule && selected && (
        <div style={overlay}>
          <div style={{ ...modal, width: 480 }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Reschedule Booking</h3>
              <button onClick={() => { setShowReschedule(false); setActionError("") }} style={iconBtn}><X size={20} /></button>
            </div>

            <div style={{ marginBottom: 16, padding: "10px 14px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 8, fontSize: 13, color: "#1E40AF" }}>
              Moving <strong>{selected.clientName}</strong> — {selected.serviceName}<br />
              <span style={{ fontSize: 12, color: "#3B82F6" }}>
                Currently: {selected.bookingDate} at {fmtTime(selected.startTime)}
              </span>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>New date *</label>
                <input
                  type="date"
                  value={rescheduleForm.newDate}
                  min={new Date().toISOString().split("T")[0]}
                  onChange={e => setRescheduleForm(f => ({ ...f, newDate: e.target.value, newStartTime: "" }))}
                  style={inputStyle}
                />
              </div>

              {rescheduleForm.newDate && (
                <div>
                  <label style={lbl}>New time slot *</label>
                  {rescheduleSlots.length === 0 ? (
                    <div style={{ padding: "12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", gap: 8 }}>
                      <AlertCircle size={14} />No available slots. Try another date.
                    </div>
                  ) : (
                    <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
                      {rescheduleSlots.map((slot: Slot) => (
                        <button
                          key={slot.startTime}
                          onClick={() => setRescheduleForm(f => ({ ...f, newStartTime: slot.startTime }))}
                          style={{
                            padding: "7px 14px", borderRadius: 8, fontSize: 13, cursor: "pointer",
                            border: rescheduleForm.newStartTime === slot.startTime ? "2px solid #0D9488" : "1.5px solid #E2E8F0",
                            background: rescheduleForm.newStartTime === slot.startTime ? "#F0FDF4" : "#fff",
                            color: rescheduleForm.newStartTime === slot.startTime ? "#0D9488" : "#374151",
                            fontWeight: rescheduleForm.newStartTime === slot.startTime ? 700 : 400,
                          }}>
                          {slot.displayLabel || `${slot.startTime?.substring(0, 5)} – ${slot.endTime?.substring(0, 5)}`}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>

            {actionError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", gap: 8 }}>
                <AlertCircle size={14} />{actionError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => { setShowReschedule(false); setActionError("") }}
                style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>
                Cancel
              </button>
              <button
                onClick={() => {
                  if (!rescheduleForm.newDate || !rescheduleForm.newStartTime) {
                    setActionError("Please select a new date and time slot.")
                    return
                  }
                  statusAction.mutate({
                    id: selected.id, action: "reschedule",
                    body: { newDate: rescheduleForm.newDate, newStartTime: rescheduleForm.newStartTime },
                  })
                }}
                disabled={statusAction.isPending}
                style={{ padding: "9px 22px", background: "#0D9488", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {statusAction.isPending ? "Rescheduling…" : "Confirm reschedule"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Cancel confirmation modal ─────────────────────────────────────── */}
      {showCancel && selected && (
        <div style={{ ...overlay, zIndex: 1001 }}>
          <div style={{ ...modal, width: 420, textAlign: "center" }}>
            <div style={{ width: 52, height: 52, borderRadius: "50%", background: "#FEF2F2", border: "2px solid #FECACA", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px" }}>
              <X size={22} color="#DC2626" />
            </div>
            <h3 style={{ margin: "0 0 6px", fontSize: 18, fontWeight: 700, color: "#0F172A" }}>Cancel Booking?</h3>
            <p style={{ fontSize: 13, color: "#64748B", margin: "0 0 16px", lineHeight: 1.6 }}>
              {selected.clientName} — {selected.serviceName} on {selected.bookingDate}
            </p>
            <div style={{ marginBottom: 16, textAlign: "left" }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Reason (optional)</label>
              <input
                value={cancelReason}
                onChange={e => setCancelReason(e.target.value)}
                placeholder="e.g. Client requested cancellation"
                style={{ ...inputStyle, width: "100%", boxSizing: "border-box" }}
              />
            </div>
            <div style={{ display: "flex", gap: 10 }}>
              <button onClick={() => { setShowCancel(false); setCancelReason(""); setActionError("") }}
                style={{ flex: 1, padding: "10px", border: "1.5px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, fontWeight: 600, cursor: "pointer", color: "#374151" }}>
                Keep booking
              </button>
              <button
                onClick={() => statusAction.mutate({ id: selected.id, action: "cancel", body: { reason: cancelReason || "Cancelled" } })}
                disabled={statusAction.isPending}
                style={{ flex: 1, padding: "10px", border: "none", borderRadius: 9, background: "#DC2626", color: "#fff", fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {statusAction.isPending ? "Cancelling…" : "Yes, cancel"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Create booking modal ─────────────────────────────────────────── */}
      {showCreate && (
        <div style={overlay}>
          <div style={{ ...modal, width: 580, maxHeight: "88vh", overflowY: "auto" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>New Booking</h3>
              <button onClick={() => { setShowCreate(false); setForm(EMPTY_FORM); setFormErrors({}); setCreateError("") }} style={iconBtn}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              {/* Service */}
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Service *</label>
                <select value={form.serviceId}
                  onChange={e => { setForm(f => ({ ...f, serviceId: e.target.value, startTime: "" })); setFormErrors(f => { const n = { ...f }; delete n.serviceId; return n }) }}
                  style={inpStyle("serviceId")}>
                  <option value="">Select service…</option>
                  {services.map(s => <option key={s.id} value={s.id}>{s.name} — {s.durationMinutes} min</option>)}
                </select>
                <FieldErr name="serviceId" />
              </div>

              {/* Staff */}
              <div>
                <label style={lbl}>Staff <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <select value={form.staffId} onChange={e => setForm(f => ({ ...f, staffId: e.target.value, startTime: "" }))} style={inputStyle}>
                  <option value="">Any available staff</option>
                  {eligibleStaff.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>

              {/* Date */}
              <div>
                <label style={lbl}>Date *</label>
                {(() => {
                  const selectedSvc = services.find(s => s.id === form.serviceId) as any
                  const maxDays = selectedSvc?.maxAdvanceDays ?? 365
                  const maxDate = new Date()
                  maxDate.setDate(maxDate.getDate() + maxDays)
                  return (
                    <input type="date" value={form.bookingDate}
                      min={new Date().toISOString().split("T")[0]}
                      max={maxDate.toISOString().split("T")[0]}
                      onChange={e => { setForm(f => ({ ...f, bookingDate: e.target.value, startTime: "" })); setFormErrors(f => { const n = { ...f }; delete n.bookingDate; return n }) }}
                      style={inpStyle("bookingDate")} />
                  )
                })()}
                <FieldErr name="bookingDate" />
              </div>

              {/* Slots */}
              {form.serviceId && form.bookingDate && (
                <div style={{ gridColumn: "1 / -1" }}>
                  <label style={lbl}>Available slots *</label>
                  {slots.length === 0 ? (
                    <div style={{ padding: "12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", gap: 8 }}>
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

              {/* CRM Client search + autofill */}
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Client name *</label>
                <div style={{ position: "relative" }}>
                  <input
                    value={form.clientName}
                    onChange={e => {
                      setForm(f => ({ ...f, clientName: e.target.value }))
                      setClientSearch(e.target.value)
                      setShowClientSuggestions(true)
                      setFormErrors(f => { const n = { ...f }; delete n.clientName; return n })
                    }}
                    onBlur={() => setTimeout(() => setShowClientSuggestions(false), 150)}
                    placeholder="Start typing name, phone, or email to search CRM…"
                    style={inpStyle("clientName")}
                  />
                  {/* CRM suggestions dropdown */}
                  {showClientSuggestions && clientSuggestions.length > 0 && (
                    <div style={{
                      position: "absolute", top: "100%", left: 0, right: 0, zIndex: 10,
                      background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8,
                      boxShadow: "0 4px 16px rgba(0,0,0,0.1)", marginTop: 2, overflow: "hidden",
                    }}>
                      {clientSuggestions.map((c: any) => (
                        <button
                          key={c.id}
                          onMouseDown={() => {
                            // onMouseDown fires before onBlur, so we can autofill before the dropdown closes
                            setForm(f => ({
                              ...f,
                              clientName:  c.name ?? f.clientName,
                              clientEmail: c.email ?? f.clientEmail,
                              clientPhone: c.phone ?? f.clientPhone,
                            }))
                            setClientSearch("")
                            setShowClientSuggestions(false)
                          }}
                          style={{ display: "flex", flexDirection: "column", alignItems: "flex-start", width: "100%", padding: "10px 14px", background: "none", border: "none", cursor: "pointer", textAlign: "left" }}
                          onMouseEnter={e => (e.currentTarget.style.background = "#F8FAFC")}
                          onMouseLeave={e => (e.currentTarget.style.background = "none")}>
                          <span style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{c.name}</span>
                          <span style={{ fontSize: 11, color: "#94A3B8" }}>{[c.email, c.phone].filter(Boolean).join(" · ")}</span>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                <FieldErr name="clientName" />
              </div>

              <div>
                <label style={lbl}>Email <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <input type="email" value={form.clientEmail}
                  onChange={e => { setForm(f => ({ ...f, clientEmail: e.target.value })); setFormErrors(f => { const n = { ...f }; delete n.clientEmail; return n }) }}
                  placeholder="client@example.com" style={inpStyle("clientEmail")} />
                <FieldErr name="clientEmail" />
              </div>

              <div>
                <label style={lbl}>Phone <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <input value={form.clientPhone}
                  onChange={e => { setForm(f => ({ ...f, clientPhone: e.target.value.replace(/[^\d\s\-+]/g, "") })); setFormErrors(f => { const n = { ...f }; delete n.clientPhone; return n }) }}
                  placeholder="+27 82 123 4567" style={inpStyle("clientPhone")} />
                <FieldErr name="clientPhone" />
              </div>

              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Notes <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
                  rows={2} placeholder="Any special requirements…"
                  style={{ ...inputStyle, resize: "vertical" }} />
              </div>
            </div>

            {createError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", gap: 8 }}>
                <AlertCircle size={15} style={{ flexShrink: 0 }} />{createError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => { setShowCreate(false); setForm(EMPTY_FORM); setFormErrors({}); setCreateError("") }}
                style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>
                Cancel
              </button>
              <button
                onClick={() => { if (validate()) createBooking.mutate({ ...form, staffId: form.staffId || null }) }}
                disabled={createBooking.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {createBooking.isPending ? "Creating…" : "Create booking"}
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
const btnPrimary: React.CSSProperties  = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }
const navBtn: React.CSSProperties      = { display: "flex", alignItems: "center", gap: 5, padding: "7px 14px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 13, cursor: "pointer", color: "#374151" }
const lbl: React.CSSProperties         = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
const inputStyle: React.CSSProperties  = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box", background: "#fff", outline: "none" }
const overlay: React.CSSProperties     = { position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }
const modal: React.CSSProperties       = { background: "#fff", borderRadius: 16, padding: 28, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }
const iconBtn: React.CSSProperties     = { background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }
