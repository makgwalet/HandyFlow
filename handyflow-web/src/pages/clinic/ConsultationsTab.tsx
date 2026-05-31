// src/pages/clinic/ConsultationsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, FileText, ChevronDown, ChevronUp, Pill, AlertCircle, Activity } from "lucide-react"

interface Consultation {
  id: string; patientId: string; patientName: string
  practitionerId: string; practitionerName: string
  consultedAt: string; weightKg: number; heightCm: number
  bloodPressure: string; pulseBpm: number; temperatureC: number; oxygenSatPct: number
  chiefComplaint: string; history: string; examination: string
  diagnosis: string; icd10Codes: string[]; treatmentPlan: string
  followUpDays: number | null; billed: boolean; billingAmount: number
}
interface Prescription {
  id: string; medicationName: string; dosage: string; frequency: string
  duration: string; quantity: number; repeats: number; instructions: string; dispensed: boolean
}
interface Patient      { id: string; fullName: string }
interface Practitioner { id: string; fullName: string; specialty: string }

const unwrapList = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : (p?.content ?? []) }

const EMPTY_FORM = {
  patientId: "", practitionerId: "", chiefComplaint: "",
  weightKg: "", heightCm: "", bloodPressure: "", pulseBpm: "", temperatureC: "", oxygenSatPct: "",
  history: "", examination: "", diagnosis: "", icd10Codes: "", treatmentPlan: "", followUpDays: "",
}

export default function ConsultationsTab() {
  const qc = useQueryClient()
  const [selectedPatientId, setSelectedPatientId] = useState("")
  const [expanded, setExpanded]     = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [showRx, setShowRx]         = useState<string | null>(null)
  const [form, setForm]             = useState(EMPTY_FORM)
  const [apiError, setApiError]     = useState("")
  const [fieldErrors, setFieldErrors] = useState<Record<string,string>>({})
  const f = (k: keyof typeof EMPTY_FORM, v: string) => { setForm(p => ({ ...p, [k]: v })); setFieldErrors(e => { const n = { ...e }; delete n[k]; return n }) }

  const [rxForm, setRxForm] = useState({ medicationName: "", dosage: "", frequency: "", duration: "", quantity: "30", repeats: "0", instructions: "" })

  const { data: patients = [] } = useQuery<Patient[]>({
    queryKey: ["clinic-patients-list"],
    queryFn: async () => unwrapList(await apiClient.get("/api/v1/clinic/patients?size=200")),
  })
  const { data: practitioners = [] } = useQuery<Practitioner[]>({
    queryKey: ["clinic-practitioners-list"],
    queryFn: async () => unwrapList(await apiClient.get("/api/v1/clinic/practitioners/list")),
  })
  const { data: consultations = [], isLoading } = useQuery<Consultation[]>({
    queryKey: ["patient-consultations", selectedPatientId],
    queryFn: async () => {
      if (!selectedPatientId) return []
      return unwrapList(await apiClient.get(`/api/v1/clinic/patients/${selectedPatientId}/consultations`))
    },
    enabled: !!selectedPatientId,
  })
  const { data: prescriptions = [] } = useQuery<Prescription[]>({
    queryKey: ["prescriptions", showRx],
    queryFn: async () => showRx ? unwrapList(await apiClient.get(`/api/v1/clinic/consultations/${showRx}/prescriptions`)) : [],
    enabled: !!showRx,
  })

  const createConsultation = useMutation({
    mutationFn: ({ patientId, body }: { patientId: string; body: any }) =>
      apiClient.post(`/api/v1/clinic/patients/${patientId}/consultations`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["patient-consultations"] }); setShowCreate(false); setForm(EMPTY_FORM); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to record consultation") },
  })

  const addPrescription = useMutation({
    mutationFn: ({ consultationId, body }: { consultationId: string; body: any }) =>
      apiClient.post(`/api/v1/clinic/consultations/${consultationId}/prescriptions`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["prescriptions"] }); setRxForm({ medicationName: "", dosage: "", frequency: "", duration: "", quantity: "30", repeats: "0", instructions: "" }) },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to add prescription"),
  })

  const validate = () => {
    const errs: Record<string,string> = {}
    if (!form.patientId) errs.patientId = "Patient is required"
    if (!form.chiefComplaint.trim()) errs.chiefComplaint = "Chief complaint is required"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const handleCreate = () => {
    if (!validate()) return
    createConsultation.mutate({
      patientId: form.patientId,
      body: {
        practitionerId:   form.practitionerId || null,
        chiefComplaint:   form.chiefComplaint,
        weightKg:         parseFloat(form.weightKg) || null,
        heightCm:         parseFloat(form.heightCm) || null,
        bloodPressure:    form.bloodPressure || null,
        pulseBpm:         parseInt(form.pulseBpm) || null,
        temperatureC:     parseFloat(form.temperatureC) || null,
        oxygenSatPct:     parseFloat(form.oxygenSatPct) || null,
        history:          form.history || null,
        examination:      form.examination || null,
        diagnosis:        form.diagnosis || null,
        icd10Codes:       form.icd10Codes ? form.icd10Codes.split(",").map(s => s.trim()).filter(Boolean) : [],
        treatmentPlan:    form.treatmentPlan || null,
        followUpDays:     parseInt(form.followUpDays) || null,
      }
    })
  }

  const inp = (key: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[key] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[key] ? "#FFF5F5" : "#fff", outline: "none",
  })

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, gap: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span style={{ fontSize: 13, color: "#64748B", fontWeight: 500 }}>Patient:</span>
          <select value={selectedPatientId} onChange={e => setSelectedPatientId(e.target.value)}
            style={{ padding: "8px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, minWidth: 220, outline: "none" }}>
            <option value="">Select patient to view history...</option>
            {(patients as Patient[]).map(p => <option key={p.id} value={p.id}>{p.fullName}</option>)}
          </select>
        </div>
        <button onClick={() => { setShowCreate(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }} style={btnPrimary}>
          <Plus size={15} /> Record Consultation
        </button>
      </div>

      {!selectedPatientId ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8", border: "1px dashed #E2E8F0", borderRadius: 12 }}>
          <FileText size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>Select a patient to view their consultation history</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Or record a new consultation for any patient.</div>
        </div>
      ) : isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
      ) : (consultations as Consultation[]).length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", border: "1px dashed #E2E8F0", borderRadius: 12 }}>
          <div style={{ fontWeight: 600, color: "#475569" }}>No consultations recorded for this patient</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {(consultations as Consultation[]).map(c => {
            const isOpen = expanded === c.id
            return (
              <div key={c.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
                {/* Header */}
                <div onClick={() => setExpanded(isOpen ? null : c.id)}
                  style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 18px", cursor: "pointer", background: isOpen ? "#F8FAFC" : "#fff" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                    <div style={{ width: 36, height: 36, borderRadius: 8, background: "#F0FDF4", border: "1px solid #86EFAC", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <Activity size={16} color="#0D9488" />
                    </div>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A", marginBottom: 2 }}>{c.chiefComplaint}</div>
                      <div style={{ fontSize: 12, color: "#64748B" }}>
                        {new Date(c.consultedAt).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })}
                        {c.practitionerName && ` · Dr. ${c.practitionerName}`}
                        {c.diagnosis && ` · ${c.diagnosis}`}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    <button onClick={e => { e.stopPropagation(); setShowRx(c.id); setApiError("") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", background: "#F0FDF4", color: "#166534", border: "1px solid #86EFAC", borderRadius: 6, fontSize: 12, cursor: "pointer", fontWeight: 600 }}>
                      <Pill size={11} /> Rx
                    </button>
                    {c.followUpDays && (
                      <span style={{ fontSize: 11, color: "#D97706", background: "#FFFBEB", padding: "2px 8px", borderRadius: 20, border: "1px solid #FDE68A" }}>F/U {c.followUpDays}d</span>
                    )}
                    {isOpen ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                  </div>
                </div>

                {isOpen && (
                  <div style={{ borderTop: "1px solid #E2E8F0", padding: "18px 20px", background: "#FAFAFA" }}>
                    {/* Vitals */}
                    {(c.weightKg || c.bloodPressure || c.pulseBpm || c.temperatureC || c.oxygenSatPct) && (
                      <div style={{ marginBottom: 16 }}>
                        <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", letterSpacing: "0.06em", marginBottom: 8 }}>VITALS</div>
                        <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                          {[
                            { l: "Weight",  v: c.weightKg   ? `${c.weightKg} kg`    : null },
                            { l: "Height",  v: c.heightCm   ? `${c.heightCm} cm`    : null },
                            { l: "BP",      v: c.bloodPressure },
                            { l: "Pulse",   v: c.pulseBpm   ? `${c.pulseBpm} bpm`   : null },
                            { l: "Temp",    v: c.temperatureC ? `${c.temperatureC}°C` : null },
                            { l: "SpO₂",   v: c.oxygenSatPct ? `${c.oxygenSatPct}%` : null },
                          ].filter(x => x.v).map(({ l, v }) => (
                            <div key={l} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, padding: "8px 14px", minWidth: 80, textAlign: "center" as const }}>
                              <div style={{ fontSize: 10, color: "#94A3B8", marginBottom: 2 }}>{l}</div>
                              <div style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>{v}</div>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                    {/* BMI if weight + height */}
                    {c.weightKg && c.heightCm && (
                      <div style={{ marginBottom: 12, fontSize: 12, color: "#64748B" }}>
                        BMI: {(c.weightKg / Math.pow(c.heightCm / 100, 2)).toFixed(1)}
                      </div>
                    )}
                    {/* Clinical notes */}
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                      {[
                        { l: "History",       v: c.history },
                        { l: "Examination",   v: c.examination },
                        { l: "Diagnosis",     v: c.diagnosis },
                        { l: "Treatment Plan",v: c.treatmentPlan },
                      ].filter(x => x.v).map(({ l, v }) => (
                        <div key={l}>
                          <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{l}</div>
                          <div style={{ fontSize: 13, color: "#0F172A", lineHeight: 1.5 }}>{v}</div>
                        </div>
                      ))}
                    </div>
                    {c.icd10Codes?.length > 0 && (
                      <div style={{ marginTop: 12 }}>
                        <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 5 }}>ICD-10 Codes</div>
                        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                          {c.icd10Codes.map(code => (
                            <span key={code} style={{ background: "#EFF6FF", color: "#1D4ED8", padding: "2px 8px", borderRadius: 4, fontSize: 12, fontWeight: 600, border: "1px solid #BFDBFE" }}>{code}</span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Prescriptions modal */}
      {showRx && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 580, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ width: 36, height: 36, borderRadius: 8, background: "#F0FDF4", display: "flex", alignItems: "center", justifyContent: "center" }}><Pill size={16} color="#0D9488" /></div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Prescriptions</h3>
              </div>
              <button onClick={() => { setShowRx(null); setApiError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            {(prescriptions as Prescription[]).length === 0 ? (
              <div style={{ textAlign: "center", padding: "20px 0", color: "#94A3B8", fontSize: 13 }}>No prescriptions issued for this consultation.</div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 10, marginBottom: 20 }}>
                {(prescriptions as Prescription[]).map(rx => (
                  <div key={rx.id} style={{ border: "1px solid #E2E8F0", borderRadius: 10, padding: "14px 16px", background: rx.dispensed ? "#F0FDF4" : "#fff" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                      <div>
                        <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A", marginBottom: 4 }}>{rx.medicationName}</div>
                        <div style={{ fontSize: 12, color: "#64748B" }}>
                          {[rx.dosage, rx.frequency, rx.duration].filter(Boolean).join(" · ")}
                          {rx.quantity ? ` · Qty: ${rx.quantity}` : ""}
                          {rx.repeats > 0 ? ` · Repeats: ${rx.repeats}` : ""}
                        </div>
                        {rx.instructions && <div style={{ fontSize: 12, color: "#475569", marginTop: 6, fontStyle: "italic" }}>{rx.instructions}</div>}
                      </div>
                      {rx.dispensed && (
                        <span style={{ background: "#DCFCE7", color: "#166534", padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700, flexShrink: 0 }}>DISPENSED</span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}

            <div style={{ borderTop: "1px solid #E2E8F0", paddingTop: 18 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 14 }}>Add Prescription</div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <div style={{ gridColumn: "1 / -1" }}>
                  <label style={lbl}>Medication *</label>
                  <input value={rxForm.medicationName} onChange={e => setRxForm(f => ({ ...f, medicationName: e.target.value }))} placeholder="Amoxicillin 500mg" style={sinp} />
                </div>
                <div>
                  <label style={lbl}>Dosage</label>
                  <input value={rxForm.dosage} onChange={e => setRxForm(f => ({ ...f, dosage: e.target.value }))} placeholder="500mg" style={sinp} />
                </div>
                <div>
                  <label style={lbl}>Frequency</label>
                  <input value={rxForm.frequency} onChange={e => setRxForm(f => ({ ...f, frequency: e.target.value }))} placeholder="3× daily" style={sinp} />
                </div>
                <div>
                  <label style={lbl}>Duration</label>
                  <input value={rxForm.duration} onChange={e => setRxForm(f => ({ ...f, duration: e.target.value }))} placeholder="7 days" style={sinp} />
                </div>
                <div>
                  <label style={lbl}>Quantity</label>
                  <input type="number" value={rxForm.quantity} onChange={e => setRxForm(f => ({ ...f, quantity: e.target.value }))} style={sinp} />
                </div>
                <div style={{ gridColumn: "1 / -1" }}>
                  <label style={lbl}>Instructions</label>
                  <input value={rxForm.instructions} onChange={e => setRxForm(f => ({ ...f, instructions: e.target.value }))} placeholder="Take with food and water" style={sinp} />
                </div>
              </div>
              {apiError && <div style={{ marginTop: 10, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 7, fontSize: 13, color: "#DC2626" }}>{apiError}</div>}
              <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 14 }}>
                <button onClick={() => addPrescription.mutate({ consultationId: showRx!, body: { ...rxForm, quantity: parseInt(rxForm.quantity), repeats: parseInt(rxForm.repeats) } })}
                  disabled={!rxForm.medicationName || addPrescription.isPending} style={btnPrimary}>
                  {addPrescription.isPending ? "Adding..." : "Add Prescription"}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Record consultation modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 720, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
              <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700, color: "#0F172A" }}>Record Consultation</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <FormSection title="Patient & Practitioner">
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Patient *</label>
                  <select value={form.patientId} onChange={e => f("patientId", e.target.value)} style={{ ...inp("patientId"), background: "#fff" }}>
                    <option value="">Select patient...</option>
                    {(patients as Patient[]).map(p => <option key={p.id} value={p.id}>{p.fullName}</option>)}
                  </select>
                  {fieldErrors.patientId && <div style={{ fontSize: 12, color: "#DC2626", marginTop: 4, display: "flex", alignItems: "center", gap: 4 }}><AlertCircle size={12} />{fieldErrors.patientId}</div>}
                </div>
                <div>
                  <label style={lbl}>Practitioner</label>
                  <select value={form.practitionerId} onChange={e => f("practitionerId", e.target.value)} style={{ ...inp("practitionerId"), background: "#fff" }}>
                    <option value="">Select...</option>
                    {(practitioners as Practitioner[]).map(p => <option key={p.id} value={p.id}>{p.fullName}</option>)}
                  </select>
                </div>
                <div style={{ gridColumn: "1 / -1" }}>
                  <label style={lbl}>Chief Complaint *</label>
                  <input value={form.chiefComplaint} onChange={e => f("chiefComplaint", e.target.value)} placeholder="Main reason for visit..." style={inp("chiefComplaint")} autoFocus />
                  {fieldErrors.chiefComplaint && <div style={{ fontSize: 12, color: "#DC2626", marginTop: 4 }}>{fieldErrors.chiefComplaint}</div>}
                </div>
              </div>
            </FormSection>

            <FormSection title="Vitals">
              <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 14 }}>
                <div><label style={lbl}>Weight (kg)</label><input type="number" step="0.1" value={form.weightKg} onChange={e => f("weightKg", e.target.value)} placeholder="70.5" style={sinp} /></div>
                <div><label style={lbl}>Height (cm)</label><input type="number" value={form.heightCm} onChange={e => f("heightCm", e.target.value)} placeholder="175" style={sinp} /></div>
                <div><label style={lbl}>Blood Pressure</label><input value={form.bloodPressure} onChange={e => f("bloodPressure", e.target.value)} placeholder="120/80" style={sinp} /></div>
                <div><label style={lbl}>Pulse (bpm)</label><input type="number" value={form.pulseBpm} onChange={e => f("pulseBpm", e.target.value)} placeholder="72" style={sinp} /></div>
                <div><label style={lbl}>Temperature (°C)</label><input type="number" step="0.1" value={form.temperatureC} onChange={e => f("temperatureC", e.target.value)} placeholder="36.6" style={sinp} /></div>
                <div><label style={lbl}>SpO₂ (%)</label><input type="number" value={form.oxygenSatPct} onChange={e => f("oxygenSatPct", e.target.value)} placeholder="98" style={sinp} /></div>
              </div>
              {form.weightKg && form.heightCm && (
                <div style={{ marginTop: 10, fontSize: 12, color: "#64748B", background: "#F8FAFC", padding: "6px 12px", borderRadius: 7, display: "inline-block" }}>
                  BMI: {(parseFloat(form.weightKg) / Math.pow(parseFloat(form.heightCm)/100, 2)).toFixed(1)}
                </div>
              )}
            </FormSection>

            <FormSection title="Clinical Notes">
              <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                <div><label style={lbl}>History</label><textarea value={form.history} onChange={e => f("history", e.target.value)} rows={2} placeholder="Patient history and presenting complaint..." style={{ ...sinp, resize: "vertical" as const }} /></div>
                <div><label style={lbl}>Examination</label><textarea value={form.examination} onChange={e => f("examination", e.target.value)} rows={2} placeholder="Physical examination findings..." style={{ ...sinp, resize: "vertical" as const }} /></div>
                <div><label style={lbl}>Diagnosis</label><textarea value={form.diagnosis} onChange={e => f("diagnosis", e.target.value)} rows={2} placeholder="Working diagnosis..." style={{ ...sinp, resize: "vertical" as const }} /></div>
                <div><label style={lbl}>ICD-10 Codes <span style={{ fontWeight: 400, color: "#94A3B8" }}>(comma separated)</span></label><input value={form.icd10Codes} onChange={e => f("icd10Codes", e.target.value)} placeholder="J06.9, Z00.0" style={sinp} /></div>
                <div><label style={lbl}>Treatment Plan</label><textarea value={form.treatmentPlan} onChange={e => f("treatmentPlan", e.target.value)} rows={2} placeholder="Management and treatment plan..." style={{ ...sinp, resize: "vertical" as const }} /></div>
                <div><label style={lbl}>Follow-up in (days)</label><input type="number" value={form.followUpDays} onChange={e => f("followUpDays", e.target.value)} placeholder="7" style={{ ...sinp, width: 120 }} /></div>
              </div>
            </FormSection>

            {apiError && <div style={{ marginBottom: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{apiError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => setShowCreate(false)} style={btnCancel}>Cancel</button>
              <button onClick={handleCreate} disabled={createConsultation.isPending} style={btnPrimary}>
                {createConsultation.isPending ? "Recording..." : "Record Consultation"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function FormSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 22 }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", letterSpacing: "0.07em", textTransform: "uppercase" as const, marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid #F1F5F9" }}>{title}</div>
      {children}
    </div>
  )
}

const inp  = (key: string): React.CSSProperties => ({ width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, outline: "none" })
const sinp: React.CSSProperties = { width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, outline: "none", background: "#fff" }
const lbl: React.CSSProperties  = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, padding: "9px 20px", fontSize: 14, fontWeight: 600, cursor: "pointer" }
const btnCancel:  React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
