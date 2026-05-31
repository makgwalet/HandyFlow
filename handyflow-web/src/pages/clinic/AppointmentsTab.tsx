// src/pages/clinic/AppointmentsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Calendar, Clock, User, ChevronRight, AlertCircle, CheckCircle, PlayCircle, XCircle } from "lucide-react"

interface Appointment {
  id: string; patientId: string; patientName: string
  practitionerId: string; practitionerName: string
  scheduledAt: string; durationMinutes: number
  appointmentType: string; status: string; reason: string; notes: string
}
interface Patient      { id: string; fullName: string }
interface Practitioner { id: string; fullName: string; specialty: string }

const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string; icon: React.ElementType }> = {
  SCHEDULED:   { color: "#1D4ED8", bg: "#EFF6FF", border: "#BFDBFE", label: "Scheduled",   icon: Calendar },
  CONFIRMED:   { color: "#7C3AED", bg: "#F5F3FF", border: "#DDD6FE", label: "Confirmed",   icon: CheckCircle },
  IN_PROGRESS: { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A", label: "In Progress", icon: PlayCircle },
  COMPLETED:   { color: "#166534", bg: "#DCFCE7", border: "#86EFAC", label: "Completed",   icon: CheckCircle },
  CANCELLED:   { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA", label: "Cancelled",   icon: XCircle },
  NO_SHOW:     { color: "#64748B", bg: "#F8FAFC", border: "#E2E8F0", label: "No Show",     icon: User },
}

const APPT_TYPES  = ["CONSULTATION","FOLLOW_UP","PROCEDURE","EMERGENCY","CHECK_UP"]
const STATUS_FLOW: Record<string, { action: string; label: string; color: string }[]> = {
  SCHEDULED:   [{ action: "confirm",  label: "Confirm",  color: "#7C3AED" }, { action: "cancel", label: "Cancel", color: "#DC2626" }],
  CONFIRMED:   [{ action: "start",    label: "Start",    color: "#D97706" }, { action: "no_show", label: "No Show", color: "#64748B" }, { action: "cancel", label: "Cancel", color: "#DC2626" }],
  IN_PROGRESS: [{ action: "complete", label: "Complete", color: "#166534" }],
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const unwrapList = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : (p?.content ?? []) }

const fmtDT = (iso: string) => new Date(iso).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" })
const fmtT  = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })

const EMPTY_FORM = { patientId: "", practitionerId: "", scheduledAt: "", durationMinutes: "30", appointmentType: "CONSULTATION", reason: "" }

export default function AppointmentsTab() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState("")
  const [showCreate, setShowCreate]     = useState(false)
  const [selected, setSelected]         = useState<Appointment | null>(null)
  const [form, setForm]                 = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors]   = useState<Record<string,string>>({})
  const [apiError, setApiError]         = useState("")

  const { data: appointments = [], isLoading } = useQuery<Appointment[]>({
    queryKey: ["clinic-appointments", statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "100" })
      if (statusFilter) params.set("status", statusFilter)
      return unwrap(await apiClient.get(`/api/v1/clinic/appointments?${params}`))
    },
  })

  const { data: patients = [] } = useQuery<Patient[]>({
    queryKey: ["clinic-patients-list"],
    queryFn: async () => unwrapList(await apiClient.get("/api/v1/clinic/patients?size=200")),
  })

  const { data: practitioners = [] } = useQuery<Practitioner[]>({
    queryKey: ["clinic-practitioners-list"],
    queryFn: async () => unwrapList(await apiClient.get("/api/v1/clinic/practitioners/list")),
  })

  const createAppt = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/clinic/appointments", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["clinic-appointments"] }); qc.invalidateQueries({ queryKey: ["clinic-appts-dashboard"] }); setShowCreate(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to book appointment") },
  })

  const doAction = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) => apiClient.post(`/api/v1/clinic/appointments/${id}/${action}`),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ["clinic-appointments"] })
      qc.invalidateQueries({ queryKey: ["clinic-appts-dashboard"] })
      const updated = res.data?.data ?? res.data
      setSelected(updated)
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to update appointment"),
  })

  const validate = () => {
    const errs: Record<string,string> = {}
    if (!form.patientId) errs.patientId = "Please select a patient"
    if (!form.scheduledAt) errs.scheduledAt = "Please select a date and time"
    const mins = parseInt(form.durationMinutes)
    if (isNaN(mins) || mins < 5 || mins > 480) errs.durationMinutes = "Duration must be 5–480 minutes"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const inp = (key: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[key] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[key] ? "#FFF5F5" : "#fff", outline: "none",
  })

  const grouped = appointments.reduce((acc: Record<string,Appointment[]>, a) => {
    const day = new Date(a.scheduledAt).toLocaleDateString("en-ZA", { weekday: "long", day: "numeric", month: "long", year: "numeric" })
    if (!acc[day]) acc[day] = []
    acc[day].push(a)
    return acc
  }, {})

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {[{ v: "", l: "All" }, ...Object.entries(STATUS_CFG).map(([v, c]) => ({ v, l: c.label }))].map(({ v, l }) => (
            <button key={v} onClick={() => setStatusFilter(v)}
              style={{ padding: "6px 12px", borderRadius: 20, border: "none", fontSize: 12, cursor: "pointer", fontWeight: statusFilter === v ? 600 : 400,
                background: statusFilter === v ? (v ? STATUS_CFG[v].color : "#1B3A6B") : "#F1F5F9",
                color: statusFilter === v ? "#fff" : "#64748B" }}>
              {l}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowCreate(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }} style={btnPrimary}>
          <Plus size={15} /> Book Appointment
        </button>
      </div>

      {/* Stats row */}
      <div style={{ display: "flex", gap: 12, marginBottom: 20 }}>
        {[
          { label: "Total",       value: appointments.length, color: "#1B3A6B" },
          { label: "Pending",     value: appointments.filter(a => ["SCHEDULED","CONFIRMED"].includes(a.status)).length, color: "#D97706" },
          { label: "Completed",   value: appointments.filter(a => a.status === "COMPLETED").length, color: "#166534" },
          { label: "No-shows",    value: appointments.filter(a => a.status === "NO_SHOW").length,  color: "#64748B" },
        ].map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "10px 16px" }}>
            <div style={{ fontSize: 20, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "#64748B", marginTop: 1 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading appointments...</div>
      ) : appointments.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Calendar size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No appointments found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
          {Object.entries(grouped).map(([day, dayAppts]) => (
            <div key={day}>
              <div style={{ fontSize: 12, fontWeight: 700, color: "#64748B", marginBottom: 10, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>{day}</div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {dayAppts.sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt)).map(appt => {
                  const s = STATUS_CFG[appt.status] ?? STATUS_CFG.SCHEDULED
                  const Icon = s.icon
                  return (
                    <div key={appt.id} onClick={() => { setSelected(appt); setApiError("") }}
                      style={{ display: "flex", alignItems: "center", gap: 14, padding: "14px 18px", border: `1px solid ${s.border}`, borderLeft: `4px solid ${s.color}`, borderRadius: 10, cursor: "pointer", background: "#fff" }}
                      onMouseEnter={e => (e.currentTarget.style.background = s.bg)}
                      onMouseLeave={e => (e.currentTarget.style.background = "#fff")}>
                      <div style={{ textAlign: "center", minWidth: 48 }}>
                        <div style={{ fontSize: 16, fontWeight: 800, color: "#0F172A" }}>{fmtT(appt.scheduledAt)}</div>
                        <div style={{ fontSize: 10, color: "#94A3B8" }}>{appt.durationMinutes}m</div>
                      </div>
                      <div style={{ flex: 1 }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                          <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{appt.patientName}</span>
                          <span style={{ background: s.bg, color: s.color, padding: "1px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700, border: `1px solid ${s.border}` }}>{s.label}</span>
                          <span style={{ fontSize: 11, color: "#94A3B8" }}>{appt.appointmentType?.replace("_"," ")}</span>
                        </div>
                        <div style={{ fontSize: 12, color: "#64748B" }}>
                          {appt.practitionerName ? `Dr. ${appt.practitionerName}` : "No practitioner assigned"}
                          {appt.reason && ` · ${appt.reason}`}
                        </div>
                      </div>
                      <ChevronRight size={16} color="#CBD5E1" />
                    </div>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Appointment detail modal */}
      {selected && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 500, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            {(() => {
              const s = STATUS_CFG[selected.status] ?? STATUS_CFG.SCHEDULED
              const Icon = s.icon
              return (
                <>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
                    <div>
                      <h3 style={{ margin: "0 0 6px", fontSize: 18, fontWeight: 700, color: "#0F172A" }}>{selected.patientName}</h3>
                      <span style={{ display: "inline-flex", alignItems: "center", gap: 5, background: s.bg, color: s.color, padding: "3px 10px", borderRadius: 20, fontSize: 12, fontWeight: 700, border: `1px solid ${s.border}` }}>
                        <Icon size={11} />{s.label}
                      </span>
                    </div>
                    <button onClick={() => setSelected(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
                  </div>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 20 }}>
                    {[
                      ["Scheduled", fmtDT(selected.scheduledAt)],
                      ["Duration",  `${selected.durationMinutes} minutes`],
                      ["Type",      selected.appointmentType?.replace("_"," ")],
                      ["Practitioner", selected.practitionerName ? `Dr. ${selected.practitionerName}` : "—"],
                      ["Reason",    selected.reason || "—"],
                      ["Notes",     selected.notes  || "—"],
                    ].map(([label, value]) => (
                      <div key={label as string} style={{ padding: "8px 12px", background: "#F8FAFC", borderRadius: 8 }}>
                        <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 2 }}>{label as string}</div>
                        <div style={{ fontSize: 13, color: "#0F172A" }}>{value as string}</div>
                      </div>
                    ))}
                  </div>
                  {apiError && <div style={{ marginBottom: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{apiError}</div>}
                  {(STATUS_FLOW[selected.status] ?? []).length > 0 && (
                    <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
                      {(STATUS_FLOW[selected.status] ?? []).map(btn => (
                        <button key={btn.action} onClick={() => doAction.mutate({ id: selected.id, action: btn.action })}
                          disabled={doAction.isPending}
                          style={{ padding: "8px 18px", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer", background: `${btn.color}18`, color: btn.color }}>
                          {btn.label}
                        </button>
                      ))}
                    </div>
                  )}
                </>
              )
            })()}
          </div>
        </div>
      )}

      {/* Book appointment modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 540, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Book Appointment</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Patient *</label>
                <select value={form.patientId} onChange={e => { setForm(f => ({ ...f, patientId: e.target.value })); setFieldErrors(f => omit(f,"patientId")) }} style={{ ...inp("patientId"), background: "#fff" }}>
                  <option value="">Select patient...</option>
                  {(patients as Patient[]).map(p => <option key={p.id} value={p.id}>{p.fullName}</option>)}
                </select>
                {fieldErrors.patientId && <div style={{ fontSize: 12, color: "#DC2626", marginTop: 4, display: "flex", alignItems: "center", gap: 4 }}><AlertCircle size={12} />{fieldErrors.patientId}</div>}
              </div>
              <div>
                <label style={lbl}>Practitioner</label>
                <select value={form.practitionerId} onChange={e => setForm(f => ({ ...f, practitionerId: e.target.value }))} style={{ ...inp("practitionerId"), background: "#fff" }}>
                  <option value="">Any / unassigned</option>
                  {(practitioners as Practitioner[]).map(p => <option key={p.id} value={p.id}>{p.fullName} — {p.specialty}</option>)}
                </select>
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Date & Time *</label>
                  <input type="datetime-local" value={form.scheduledAt} onChange={e => { setForm(f => ({ ...f, scheduledAt: e.target.value })); setFieldErrors(f => omit(f,"scheduledAt")) }} style={inp("scheduledAt")} />
                  {fieldErrors.scheduledAt && <div style={{ fontSize: 12, color: "#DC2626", marginTop: 4 }}>{fieldErrors.scheduledAt}</div>}
                </div>
                <div>
                  <label style={lbl}>Duration (minutes)</label>
                  <input type="number" min="5" max="480" value={form.durationMinutes} onChange={e => { setForm(f => ({ ...f, durationMinutes: e.target.value })); setFieldErrors(f => omit(f,"durationMinutes")) }} style={inp("durationMinutes")} />
                  {fieldErrors.durationMinutes && <div style={{ fontSize: 12, color: "#DC2626", marginTop: 4 }}>{fieldErrors.durationMinutes}</div>}
                </div>
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Type</label>
                  <select value={form.appointmentType} onChange={e => setForm(f => ({ ...f, appointmentType: e.target.value }))} style={{ ...inp("appointmentType"), background: "#fff" }}>
                    {APPT_TYPES.map(t => <option key={t} value={t}>{t.replace("_"," ")}</option>)}
                  </select>
                </div>
                <div>
                  <label style={lbl}>Reason</label>
                  <input value={form.reason} onChange={e => setForm(f => ({ ...f, reason: e.target.value }))} placeholder="Optional" style={inp("reason")} />
                </div>
              </div>
              {/* Duration preview */}
              {parseInt(form.durationMinutes) > 0 && form.scheduledAt && (
                <div style={{ padding: "8px 12px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 8, fontSize: 12, color: "#166534" }}>
                  ✓ Appointment from {new Date(form.scheduledAt).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })} — {new Date(new Date(form.scheduledAt).getTime() + parseInt(form.durationMinutes) * 60000).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })}
                </div>
              )}
            </div>
            {apiError && <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{apiError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => { if (validate()) createAppt.mutate({ patientId: form.patientId, practitionerId: form.practitionerId || null, scheduledAt: new Date(form.scheduledAt).toISOString(), durationMinutes: parseInt(form.durationMinutes) || 30, appointmentType: form.appointmentType, reason: form.reason || null }) }}
                disabled={createAppt.isPending} style={btnPrimary}>
                {createAppt.isPending ? "Booking..." : "Book Appointment"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const omit = (obj: Record<string,string>, key: string) => { const n = { ...obj }; delete n[key]; return n }
const inp  = (key: string): React.CSSProperties => ({ width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, outline: "none" })
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, padding: "9px 20px", fontSize: 14, fontWeight: 600, cursor: "pointer" }
const btnCancel:  React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
