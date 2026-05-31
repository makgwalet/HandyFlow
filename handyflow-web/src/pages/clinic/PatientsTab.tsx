// src/pages/clinic/PatientsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, X, Search, User, ChevronRight, Heart, AlertCircle,
  Phone, Mail, Calendar, FileText, Pill,
} from "lucide-react"

// ── Types ──────────────────────────────────────────────────────────────────────

interface Patient {
  id: string; firstName: string; lastName: string; fullName: string
  idNumber: string; dateOfBirth: string; gender: string
  phone: string; email: string; bloodType: string
  allergies: string[]; chronicConditions: string[]
  emergencyContactName: string; emergencyContactPhone: string
  notes: string; active: boolean; createdAt: string
}

// ── SA ID Validator ────────────────────────────────────────────────────────────

function validateSaId(id: string): { valid: boolean; dob?: string; age?: number; gender?: string; error?: string } {
  const clean = id.replace(/\s/g, "")
  if (!clean) return { valid: true }
  if (!/^\d{13}$/.test(clean)) return { valid: false, error: "Must be exactly 13 digits" }
  let sum = 0
  for (let i = 0; i < 12; i++) {
    let d = parseInt(clean[i])
    if (i % 2 === 1) { d *= 2; if (d > 9) d -= 9 }
    sum += d
  }
  if ((10 - (sum % 10)) % 10 !== parseInt(clean[12]))
    return { valid: false, error: "Invalid ID number (checksum failed)" }
  const yy = parseInt(clean.slice(0, 2)), mm = parseInt(clean.slice(2, 4)), dd = parseInt(clean.slice(4, 6))
  const yr  = yy <= (new Date().getFullYear() % 100) ? 2000 + yy : 1900 + yy
  if (mm < 1 || mm > 12 || dd < 1 || dd > 31) return { valid: false, error: "Invalid date in ID number" }
  const months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
  const dobDate = new Date(yr, mm - 1, dd)
  const age     = Math.floor((Date.now() - dobDate.getTime()) / (365.25 * 24 * 3600 * 1000))
  return { valid: true, dob: `${String(dd).padStart(2,"0")} ${months[mm-1]} ${yr}`, age, gender: parseInt(clean[6]) >= 5 ? "Male" : "Female" }
}

const BLOOD_TYPES = ["A+","A-","B+","B-","AB+","AB-","O+","O-"]
const GENDERS     = ["MALE","FEMALE","NON_BINARY","PREFER_NOT_TO_SAY"]

const EMPTY_FORM = {
  firstName: "", lastName: "", idNumber: "", dateOfBirth: "",
  gender: "", phone: "", email: "",
  emergencyContactName: "", emergencyContactPhone: "",
}

const unwrapPage = (r: any): any[] => {
  const payload = r.data?.data ?? r.data
  return payload?.content ?? payload ?? []
}

// ── Main ───────────────────────────────────────────────────────────────────────

export default function PatientsTab() {
  const qc = useQueryClient()
  const [search, setSearch]         = useState("")
  const [showCreate, setShowCreate] = useState(false)
  const [selected, setSelected]     = useState<Patient | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError]     = useState("")
  const [form, setForm]             = useState(EMPTY_FORM)
  const [showTimeline, setShowTimeline] = useState(false)

  const f = (k: keyof typeof EMPTY_FORM, v: string) => {
    setForm(p => ({ ...p, [k]: v }))
    if (k === "idNumber" && v.replace(/\D/g,"").length === 13) {
      const r = validateSaId(v)
      if (r.valid && r.dob) {
        // Auto-fill DOB and gender from ID
        const clean = v.replace(/\D/g,"")
        const yy = parseInt(clean.slice(0,2)), mm = parseInt(clean.slice(2,4)), dd = parseInt(clean.slice(4,6))
        const yr  = yy <= (new Date().getFullYear() % 100) ? 2000 + yy : 1900 + yy
        const dobStr = `${yr}-${String(mm).padStart(2,"0")}-${String(dd).padStart(2,"0")}`
        setForm(p => ({ ...p, idNumber: v, dateOfBirth: dobStr, gender: r.gender === "Male" ? "MALE" : "FEMALE" }))
      }
    }
    setFieldErrors(e => { const n = { ...e }; delete n[k]; return n })
  }

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.firstName.trim()) errs.firstName = "First name is required"
    if (!form.lastName.trim())  errs.lastName  = "Last name is required"
    if (form.idNumber) { const r = validateSaId(form.idNumber); if (!r.valid) errs.idNumber = r.error! }
    if (form.phone && !/^(\+|0)[\d\s\-]{7,}$/.test(form.phone)) errs.phone = "Phone must start with + or 0"
    if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) errs.email = "Invalid email address"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const { data, isLoading } = useQuery({
    queryKey: ["clinic-patients", search],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "50" })
      if (search) params.set("search", search)
      const r = await apiClient.get(`/api/v1/clinic/patients?${params}`)
      return r.data?.data ?? r.data
    },
  })

  const { data: consultations = [] } = useQuery({
    queryKey: ["patient-consultations", selected?.id],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/clinic/patients/${selected!.id}/consultations`)
      const p = r.data?.data ?? r.data
      return (p?.content ?? p ?? []) as any[]
    },
    enabled: !!selected && showTimeline,
  })

  const { data: appointments = [] } = useQuery({
    queryKey: ["patient-appointments", selected?.id],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/clinic/patients/${selected!.id}/appointments`)
      const p = r.data?.data ?? r.data
      return (p?.content ?? p ?? []) as any[]
    },
    enabled: !!selected && showTimeline,
  })

  const createPatient = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/clinic/patients", body),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ["clinic-patients"] })
      qc.invalidateQueries({ queryKey: ["clinic-patients-dashboard"] })
      setShowCreate(false)
      setForm(EMPTY_FORM)
      setFieldErrors({})
      setApiError("")
      // Open the new patient's profile
      const p = res.data?.data ?? res.data
      if (p?.id) setSelected(p)
    },
    onError: (e: any) => {
      const d = e.response?.data
      if (d?.errors) setFieldErrors(d.errors)
      else setApiError(d?.message ?? "Failed to register patient")
    },
  })

  const patients: Patient[] = data?.content ?? []
  const idInfo = validateSaId(form.idNumber)

  const inp = (key: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[key] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14,
    background: fieldErrors[key] ? "#FFF5F5" : "#fff", outline: "none",
  })

  const FErr = ({ k }: { k: string }) => fieldErrors[k] ? (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }}>
      <AlertCircle size={12} />{fieldErrors[k]}
    </div>
  ) : null

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, gap: 10 }}>
        <div style={{ position: "relative" }}>
          <Search size={14} style={{ position: "absolute", left: 10, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search by name or ID number..."
            style={{ padding: "8px 12px 8px 32px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, width: 300, outline: "none" }} />
        </div>
        <button onClick={() => { setShowCreate(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
          style={btnPrimary}><Plus size={15} /> Register Patient</button>
      </div>

      {/* Stats */}
      <div style={{ display: "flex", gap: 12, marginBottom: 18 }}>
        {[
          { label: "Total patients", value: data?.totalElements ?? patients.length, color: "#1B3A6B" },
          { label: "Active",         value: patients.filter(p => p.active).length,  color: "#166534" },
        ].map(s => (
          <div key={s.label} style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "10px 20px" }}>
            <div style={{ fontSize: 20, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "#64748B", marginTop: 1 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading patients...</div>
      ) : patients.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <User size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No patients found</div>
          {!search && <div style={{ fontSize: 13, marginTop: 4 }}>Register your first patient to get started.</div>}
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Patient","DOB / Age","Contact","Blood Type","Conditions",""].map(h => (
                  <th key={h} style={{ padding: "11px 16px", textAlign: "left", fontSize: 11, fontWeight: 700, color: "#64748B", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {patients.map((p, i) => {
                const idR = p.idNumber ? validateSaId(p.idNumber) : null
                return (
                  <tr key={p.id} onClick={() => { setSelected(p); setShowTimeline(false) }}
                    style={{ background: "#fff", cursor: "pointer", borderBottom: i < patients.length-1 ? "1px solid #F1F5F9" : "none" }}
                    onMouseEnter={e => (e.currentTarget.style.background = "#F8FAFC")}
                    onMouseLeave={e => (e.currentTarget.style.background = "#fff")}>
                    <td style={td}>
                      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                        <div style={{ width: 34, height: 34, borderRadius: "50%", background: "#F0FDF4", border: "2px solid #86EFAC", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                          <span style={{ fontSize: 12, fontWeight: 700, color: "#0D9488" }}>{p.firstName?.[0]}{p.lastName?.[0]}</span>
                        </div>
                        <div>
                          <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{p.fullName}</div>
                          {p.idNumber && <div style={{ fontSize: 11, color: "#94A3B8" }}>{p.idNumber}</div>}
                        </div>
                      </div>
                    </td>
                    <td style={td}>
                      <div style={{ fontSize: 13, color: "#475569" }}>
                        {idR?.valid && idR.dob ? idR.dob : (p.dateOfBirth ?? "—")}
                      </div>
                      {idR?.valid && idR.age !== undefined && (
                        <div style={{ fontSize: 11, color: "#94A3B8" }}>{idR.age} years · {idR.gender}</div>
                      )}
                    </td>
                    <td style={td}>
                      {p.phone && <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#64748B" }}><Phone size={11} color="#94A3B8" />{p.phone}</div>}
                      {p.email && <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 11, color: "#94A3B8" }}><Mail size={11} color="#CBD5E1" />{p.email}</div>}
                    </td>
                    <td style={td}>
                      {p.bloodType ? (
                        <span style={{ background: "#FEF2F2", color: "#DC2626", padding: "2px 8px", borderRadius: 20, fontSize: 12, fontWeight: 700 }}>{p.bloodType}</span>
                      ) : <span style={{ color: "#CBD5E1" }}>—</span>}
                    </td>
                    <td style={td}>
                      {p.chronicConditions?.length > 0 ? (
                        <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
                          {p.chronicConditions.slice(0, 2).map(c => (
                            <span key={c} style={{ background: "#FFF7ED", color: "#D97706", padding: "1px 6px", borderRadius: 4, fontSize: 11 }}>{c}</span>
                          ))}
                          {p.chronicConditions.length > 2 && <span style={{ fontSize: 11, color: "#94A3B8" }}>+{p.chronicConditions.length - 2}</span>}
                        </div>
                      ) : <span style={{ color: "#CBD5E1" }}>—</span>}
                    </td>
                    <td style={td}><ChevronRight size={16} color="#CBD5E1" /></td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Patient detail modal */}
      {selected && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, width: 640, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            {/* Header band */}
            <div style={{ background: "linear-gradient(135deg, #0D9488 0%, #0F766E 100%)", padding: "24px 28px", borderRadius: "16px 16px 0 0" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                  <div style={{ width: 56, height: 56, borderRadius: "50%", background: "rgba(255,255,255,0.2)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 22, fontWeight: 800, color: "#fff" }}>
                    {selected.firstName?.[0]}{selected.lastName?.[0]}
                  </div>
                  <div>
                    <h3 style={{ margin: "0 0 4px", fontSize: 20, fontWeight: 800, color: "#fff" }}>{selected.fullName}</h3>
                    <div style={{ fontSize: 13, color: "rgba(255,255,255,0.8)" }}>
                      {selected.gender?.replace("_"," ")}
                      {(() => { const r = validateSaId(selected.idNumber); return r.valid && r.age ? ` · ${r.age} years` : "" })()}
                      {selected.bloodType && <span style={{ background: "rgba(255,255,255,0.2)", borderRadius: 20, padding: "1px 8px", marginLeft: 8, fontSize: 12, fontWeight: 700 }}>{selected.bloodType}</span>}
                    </div>
                  </div>
                </div>
                <button onClick={() => setSelected(null)} style={{ background: "rgba(255,255,255,0.15)", border: "none", borderRadius: 8, cursor: "pointer", color: "#fff", padding: 6, display: "flex" }}><X size={18} /></button>
              </div>
              {/* Tabs inside modal */}
              <div style={{ display: "flex", gap: 4, marginTop: 16 }}>
                {[{ label: "Profile", icon: User }, { label: "Timeline", icon: Calendar }].map(t => (
                  <button key={t.label} onClick={() => setShowTimeline(t.label === "Timeline")}
                    style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 14px", borderRadius: 8, border: "none", fontSize: 12, fontWeight: 600, cursor: "pointer",
                      background: (t.label === "Timeline") === showTimeline ? "rgba(255,255,255,0.25)" : "transparent",
                      color: "#fff" }}>
                    <t.icon size={13} />{t.label}
                  </button>
                ))}
              </div>
            </div>

            <div style={{ padding: "24px 28px" }}>
              {!showTimeline ? (
                <>
                  {/* Details grid */}
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 18 }}>
                    {[
                      { label: "SA ID Number", value: selected.idNumber || "—" },
                      { label: "Date of Birth", value: (() => { const r = validateSaId(selected.idNumber); return r.valid && r.dob ? `${r.dob} (${r.age} yrs)` : selected.dateOfBirth || "—" })() },
                      { label: "Phone",         value: selected.phone || "—" },
                      { label: "Email",         value: selected.email || "—" },
                      { label: "Emergency Contact", value: selected.emergencyContactName || "—" },
                      { label: "Emergency Phone",   value: selected.emergencyContactPhone || "—" },
                    ].map(item => (
                      <div key={item.label} style={{ padding: "10px 14px", background: "#F8FAFC", borderRadius: 8 }}>
                        <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.label}</div>
                        <div style={{ fontSize: 14, color: "#0F172A", fontWeight: 500 }}>{item.value}</div>
                      </div>
                    ))}
                  </div>

                  {selected.allergies?.length > 0 && (
                    <div style={{ marginBottom: 14, padding: "12px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 10 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 8 }}>
                        <AlertCircle size={13} color="#DC2626" />
                        <span style={{ fontSize: 11, fontWeight: 700, color: "#DC2626", textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>⚠ Allergies</span>
                      </div>
                      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                        {selected.allergies.map(a => (
                          <span key={a} style={{ background: "#fff", color: "#DC2626", padding: "3px 10px", borderRadius: 6, fontSize: 13, fontWeight: 600, border: "1px solid #FECACA" }}>{a}</span>
                        ))}
                      </div>
                    </div>
                  )}

                  {selected.chronicConditions?.length > 0 && (
                    <div style={{ marginBottom: 14, padding: "12px 14px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 10 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 8 }}>
                        <Heart size={13} color="#D97706" />
                        <span style={{ fontSize: 11, fontWeight: 700, color: "#D97706", textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>Chronic Conditions</span>
                      </div>
                      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                        {selected.chronicConditions.map(c => (
                          <span key={c} style={{ background: "#fff", color: "#D97706", padding: "3px 10px", borderRadius: 6, fontSize: 13, fontWeight: 600, border: "1px solid #FDE68A" }}>{c}</span>
                        ))}
                      </div>
                    </div>
                  )}

                  {selected.notes && (
                    <div style={{ padding: "10px 14px", background: "#F8FAFC", borderRadius: 8, fontSize: 13, color: "#475569" }}>
                      <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", marginBottom: 4, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>Notes</div>
                      {selected.notes}
                    </div>
                  )}
                </>
              ) : (
                /* Timeline view */
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 16 }}>Patient History</div>
                  {appointments.length === 0 && consultations.length === 0 ? (
                    <div style={{ textAlign: "center", padding: "30px 20px", color: "#94A3B8" }}>No history recorded yet</div>
                  ) : (
                    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                      {[...appointments.map((a: any) => ({ ...a, _type: "appt" })),
                        ...consultations.map((c: any) => ({ ...c, _type: "consult" }))]
                        .sort((a, b) => (b.scheduledAt ?? b.consultedAt ?? "").localeCompare(a.scheduledAt ?? a.consultedAt ?? ""))
                        .map((item: any) => (
                          <div key={item.id} style={{ display: "flex", gap: 12, alignItems: "flex-start" }}>
                            <div style={{ width: 36, height: 36, borderRadius: "50%", background: item._type === "appt" ? "#EFF6FF" : "#F0FDF4", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                              {item._type === "appt" ? <Calendar size={14} color="#1D4ED8" /> : <FileText size={14} color="#0D9488" />}
                            </div>
                            <div style={{ flex: 1, padding: "10px 14px", border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff" }}>
                              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 3 }}>
                                <span style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>
                                  {item._type === "appt" ? (item.appointmentType?.replace("_"," ") || "Appointment") : (item.chiefComplaint || "Consultation")}
                                </span>
                                <span style={{ fontSize: 11, color: "#94A3B8" }}>
                                  {new Date(item.scheduledAt ?? item.consultedAt).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })}
                                </span>
                              </div>
                              {item._type === "appt" && (
                                <div style={{ fontSize: 12, color: "#64748B" }}>{item.status} {item.practitionerName ? `· ${item.practitionerName}` : ""}</div>
                              )}
                              {item._type === "consult" && item.diagnosis && (
                                <div style={{ fontSize: 12, color: "#64748B" }}>Dx: {item.diagnosis}</div>
                              )}
                            </div>
                          </div>
                        ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Register patient modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 600, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
              <div>
                <h3 style={{ margin: "0 0 3px", fontSize: 18, fontWeight: 700, color: "#0F172A" }}>Register Patient</h3>
                <p style={{ margin: 0, fontSize: 12, color: "#94A3B8" }}>Enter ID number to auto-fill date of birth and gender</p>
              </div>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <Section title="Personal Information">
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>First Name *</label>
                  <input autoFocus value={form.firstName} onChange={e => f("firstName", e.target.value)} placeholder="Jane" style={inp("firstName")} />
                  <FErr k="firstName" />
                </div>
                <div>
                  <label style={lbl}>Last Name *</label>
                  <input value={form.lastName} onChange={e => f("lastName", e.target.value)} placeholder="Smith" style={inp("lastName")} />
                  <FErr k="lastName" />
                </div>
                <div style={{ gridColumn: "1 / -1" }}>
                  <label style={lbl}>SA ID Number</label>
                  <input value={form.idNumber} onChange={e => f("idNumber", e.target.value.replace(/\D/g,"").slice(0,13))} placeholder="8501015026083" inputMode="numeric" style={inp("idNumber")} />
                  <FErr k="idNumber" />
                  {form.idNumber.length === 13 && (
                    idInfo.valid ? (
                      <div style={{ marginTop: 6, padding: "8px 12px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, color: "#166534", display: "flex", gap: 16 }}>
                        <span>✓ Valid ID</span>
                        {idInfo.dob && <span>DOB: {idInfo.dob}</span>}
                        {idInfo.age !== undefined && <span>Age: {idInfo.age}</span>}
                        {idInfo.gender && <span>{idInfo.gender}</span>}
                      </div>
                    ) : (
                      <div style={{ marginTop: 6, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 7, fontSize: 12, color: "#DC2626" }}>
                        ✗ {idInfo.error}
                      </div>
                    )
                  )}
                </div>
                <div>
                  <label style={lbl}>Date of Birth</label>
                  <input type="date" value={form.dateOfBirth} onChange={e => f("dateOfBirth", e.target.value)} style={inp("dateOfBirth")} />
                </div>
                <div>
                  <label style={lbl}>Gender</label>
                  <select value={form.gender} onChange={e => f("gender", e.target.value)} style={{ ...inp("gender"), background: "#fff" }}>
                    <option value="">Select...</option>
                    {GENDERS.map(g => <option key={g} value={g}>{g.replace("_"," ")}</option>)}
                  </select>
                </div>
              </div>
            </Section>

            <Section title="Contact Details">
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Phone</label>
                  <input value={form.phone} onChange={e => f("phone", e.target.value)} placeholder="+27 82 123 4567" style={inp("phone")} />
                  <FErr k="phone" />
                </div>
                <div>
                  <label style={lbl}>Email</label>
                  <input value={form.email} onChange={e => f("email", e.target.value)} placeholder="jane@example.com" style={inp("email")} />
                  <FErr k="email" />
                </div>
              </div>
            </Section>

            <Section title="Emergency Contact">
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Name</label>
                  <input value={form.emergencyContactName} onChange={e => f("emergencyContactName", e.target.value)} placeholder="John Smith" style={inp("emergencyContactName")} />
                </div>
                <div>
                  <label style={lbl}>Phone</label>
                  <input value={form.emergencyContactPhone} onChange={e => f("emergencyContactPhone", e.target.value)} placeholder="+27 82 987 6543" style={inp("emergencyContactPhone")} />
                </div>
              </div>
            </Section>

            {apiError && (
              <div style={{ marginBottom: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={14} />{apiError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => setShowCreate(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => { if (validate()) createPatient.mutate({ ...form, dateOfBirth: form.dateOfBirth || null }) }}
                disabled={createPatient.isPending} style={btnPrimary}>
                {createPatient.isPending ? "Registering..." : "Register Patient"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 20 }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", letterSpacing: "0.07em", textTransform: "uppercase" as const, marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid #F1F5F9" }}>{title}</div>
      {children}
    </div>
  )
}

const inp = (key: string, errors: Record<string,string> = {}): React.CSSProperties => ({
  width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
  border: `1.5px solid ${errors[key] ? "#DC2626" : "#E2E8F0"}`,
  borderRadius: 8, fontSize: 14, background: errors[key] ? "#FFF5F5" : "#fff", outline: "none",
})
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
const td:  React.CSSProperties = { padding: "12px 16px", fontSize: 13 }
const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, padding: "9px 20px", fontSize: 14, fontWeight: 600, cursor: "pointer" }
const btnCancel:  React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
