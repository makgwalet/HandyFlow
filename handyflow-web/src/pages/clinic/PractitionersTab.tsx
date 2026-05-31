// src/pages/clinic/PractitionersTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Stethoscope, Mail, Phone, AlertCircle } from "lucide-react"

interface Practitioner {
  id: string; firstName: string; lastName: string; fullName: string
  specialty: string; hpcsaNumber: string; practiceNumber: string
  phone: string; email: string; active: boolean
}

const SPECIALTIES = [
  "General Practitioner","Cardiologist","Dermatologist","Endocrinologist",
  "Gastroenterologist","Gynaecologist","Neurologist","Oncologist",
  "Ophthalmologist","Orthopaedic Surgeon","Paediatrician","Physiotherapist",
  "Psychiatrist","Radiologist","Surgeon","Urologist","Dentist","Nurse",
]

const SPECIALTY_COLORS: Record<string,string> = {
  "General Practitioner": "#0D9488",
  "Cardiologist":         "#DC2626",
  "Paediatrician":        "#D97706",
  "Physiotherapist":      "#7C3AED",
  "Dentist":              "#1D4ED8",
  "Surgeon":              "#166534",
  "Psychiatrist":         "#DB2777",
  "Nurse":                "#0369A1",
}

const getColor = (s: string) => SPECIALTY_COLORS[s] ?? "#64748B"

const EMPTY_FORM = { firstName: "", lastName: "", specialty: "General Practitioner", hpcsaNumber: "", practiceNumber: "", phone: "", email: "" }

export default function PractitionersTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [form, setForm]             = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState<Record<string,string>>({})
  const [apiError, setApiError]     = useState("")

  const f = (k: keyof typeof EMPTY_FORM, v: string) => { setForm(p => ({ ...p, [k]: v })); setFieldErrors(e => { const n = { ...e }; delete n[k]; return n }) }

  const validate = () => {
    const errs: Record<string,string> = {}
    if (!form.firstName.trim()) errs.firstName = "First name is required"
    if (!form.lastName.trim())  errs.lastName  = "Last name is required"
    if (form.phone && !/^(\+|0)[\d\s\-]{7,}$/.test(form.phone)) errs.phone = "Phone must start with + or 0"
    if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) errs.email = "Invalid email address"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const { data: page, isLoading } = useQuery({
    queryKey: ["clinic-practitioners"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/clinic/practitioners?size=50")
      return r.data?.data ?? r.data
    },
  })

  const createPractitioner = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/clinic/practitioners", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["clinic-practitioners"] })
      qc.invalidateQueries({ queryKey: ["clinic-practitioners-list"] })
      setShowCreate(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("")
    },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to register practitioner") },
  })

  const practitioners: Practitioner[] = page?.content ?? []

  const inp = (key: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[key] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[key] ? "#FFF5F5" : "#fff", outline: "none",
  })
  const FErr = ({ k }: { k: string }) => fieldErrors[k] ? (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }}>
      <AlertCircle size={12} />{fieldErrors[k]}
    </div>
  ) : null

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ fontSize: 14, color: "#64748B" }}>{practitioners.length} practitioner{practitioners.length !== 1 ? "s" : ""} registered</div>
        <button onClick={() => { setShowCreate(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }} style={btnPrimary}>
          <Plus size={15} /> Register Practitioner
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
      ) : practitioners.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Stethoscope size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No practitioners registered</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Add practitioners to enable appointment booking.</div>
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(290px, 1fr))", gap: 14 }}>
          {practitioners.map(p => {
            const color = getColor(p.specialty)
            return (
              <div key={p.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden", background: "#fff", transition: "box-shadow 0.15s" }}
                onMouseEnter={e => (e.currentTarget.style.boxShadow = "0 4px 16px rgba(0,0,0,0.08)")}
                onMouseLeave={e => (e.currentTarget.style.boxShadow = "none")}>
                <div style={{ height: 5, background: `linear-gradient(90deg, ${color}, ${color}99)` }} />
                <div style={{ padding: "18px 20px" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 14 }}>
                    <div style={{ width: 46, height: 46, borderRadius: "50%", background: `${color}14`, border: `2px solid ${color}40`, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <span style={{ fontSize: 18, fontWeight: 800, color }}>{p.firstName?.[0]}{p.lastName?.[0]}</span>
                    </div>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>Dr. {p.fullName}</div>
                      <span style={{ background: `${color}14`, color, padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{p.specialty}</span>
                    </div>
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                    {p.email && <div style={{ display: "flex", alignItems: "center", gap: 7, fontSize: 12, color: "#64748B" }}><Mail size={12} color="#94A3B8" />{p.email}</div>}
                    {p.phone && <div style={{ display: "flex", alignItems: "center", gap: 7, fontSize: 12, color: "#64748B" }}><Phone size={12} color="#94A3B8" />{p.phone}</div>}
                    {p.hpcsaNumber && <div style={{ fontSize: 11, color: "#94A3B8", paddingTop: 4 }}>HPCSA: <span style={{ fontWeight: 600, color: "#64748B" }}>{p.hpcsaNumber}</span></div>}
                    {p.practiceNumber && <div style={{ fontSize: 11, color: "#94A3B8" }}>Practice: <span style={{ fontWeight: 600, color: "#64748B" }}>{p.practiceNumber}</span></div>}
                  </div>
                  {!p.active && (
                    <div style={{ marginTop: 10 }}>
                      <span style={{ background: "#F1F5F9", color: "#64748B", padding: "2px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>INACTIVE</span>
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 540, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Register Practitioner</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>First Name *</label>
                <input autoFocus value={form.firstName} onChange={e => f("firstName", e.target.value)} placeholder="John" style={inp("firstName")} />
                <FErr k="firstName" />
              </div>
              <div>
                <label style={lbl}>Last Name *</label>
                <input value={form.lastName} onChange={e => f("lastName", e.target.value)} placeholder="Khumalo" style={inp("lastName")} />
                <FErr k="lastName" />
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Specialty *</label>
                <select value={form.specialty} onChange={e => f("specialty", e.target.value)} style={{ ...inp("specialty"), background: "#fff" }}>
                  {SPECIALTIES.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>HPCSA Number</label>
                <input value={form.hpcsaNumber} onChange={e => f("hpcsaNumber", e.target.value)} placeholder="MP0123456" style={inp("hpcsaNumber")} />
              </div>
              <div>
                <label style={lbl}>Practice Number</label>
                <input value={form.practiceNumber} onChange={e => f("practiceNumber", e.target.value)} placeholder="0123456" style={inp("practiceNumber")} />
              </div>
              <div>
                <label style={lbl}>Phone</label>
                <input value={form.phone} onChange={e => f("phone", e.target.value)} placeholder="+27 11 555 0100" style={inp("phone")} />
                <FErr k="phone" />
              </div>
              <div>
                <label style={lbl}>Email</label>
                <input value={form.email} onChange={e => f("email", e.target.value)} placeholder="dr.khumalo@clinic.co.za" style={inp("email")} />
                <FErr k="email" />
              </div>
            </div>
            {apiError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={14} />{apiError}
              </div>
            )}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => { if (validate()) createPractitioner.mutate(form) }}
                disabled={createPractitioner.isPending} style={btnPrimary}>
                {createPractitioner.isPending ? "Registering..." : "Register Practitioner"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties      = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, padding: "9px 20px", fontSize: 14, fontWeight: 600, cursor: "pointer" }
const btnCancel:  React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
