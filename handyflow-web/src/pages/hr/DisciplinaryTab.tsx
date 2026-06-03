// src/pages/hr/DisciplinaryTab.tsx
// Endpoints:
//   GET  /api/v1/hr/employees?size=200          — employee list
//   GET  /api/v1/hr/employees/{id}/disciplinary — records for employee (ApiResponse<List<DisciplinaryResponse>>)
//   POST /api/v1/hr/employees/{id}/disciplinary — add record (AddDisciplinaryRequest)
// API unwrap: r.data?.data ?? r.data — handles ApiResponse wrapper
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, AlertOctagon, X, AlertCircle, ShieldAlert, ChevronRight } from "lucide-react"

const unwrap     = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const unwrapList = (r: any): any[] => { const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : (d?.content ?? []) }
const fmtDate    = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const INCIDENT_TYPES = [
  "VERBAL_WARNING", "WRITTEN_WARNING", "FINAL_WRITTEN_WARNING",
  "NOTICE_TO_ATTEND", "SUSPENSION", "DISMISSAL", "OTHER",
]

// Severity drives colour and escalation order — matches CCMA progressive discipline ladder
const TYPE_CFG: Record<string, { color: string; bg: string; border: string; label: string; severity: number }> = {
  VERBAL_WARNING:        { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A", label: "Verbal Warning",        severity: 1 },
  WRITTEN_WARNING:       { color: "#EA580C", bg: "#FFF7ED", border: "#FDBA74", label: "Written Warning",       severity: 2 },
  FINAL_WRITTEN_WARNING: { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA", label: "Final Written Warning", severity: 3 },
  NOTICE_TO_ATTEND:      { color: "#7C3AED", bg: "#F5F3FF", border: "#DDD6FE", label: "Notice to Attend",      severity: 3 },
  SUSPENSION:            { color: "#1D4ED8", bg: "#EFF6FF", border: "#BFDBFE", label: "Suspension",            severity: 4 },
  DISMISSAL:             { color: "#881337", bg: "#FFF1F2", border: "#FECDD3", label: "Dismissal",             severity: 5 },
  OTHER:                 { color: "#64748B", bg: "#F8FAFC", border: "#E2E8F0", label: "Other",                 severity: 0 },
}

// How many warnings before escalation is expected (CCMA guideline, not enforced)
const ESCALATION_STEPS = [
  { type: "VERBAL_WARNING",        step: 1, desc: "Verbal warning issued and acknowledged" },
  { type: "WRITTEN_WARNING",       step: 2, desc: "Written warning with 3-month validity" },
  { type: "FINAL_WRITTEN_WARNING", step: 3, desc: "Final written warning — 6-month validity" },
  { type: "NOTICE_TO_ATTEND",      step: 4, desc: "Formal hearing scheduled" },
  { type: "DISMISSAL",             step: 5, desc: "Dismissal — after fair hearing" },
]

const EMPTY_FORM = {
  employeeId: "", incidentDate: "", incidentType: "VERBAL_WARNING", description: "", hearingDate: "",
}

export default function DisciplinaryTab() {
  const qc = useQueryClient()
  const [selectedEmp, setSelectedEmp] = useState("")
  const [showAdd, setShowAdd]         = useState(false)
  const [form, setForm]               = useState(EMPTY_FORM)
  const [apiError, setApiError]       = useState("")

  // Employee list — same query key as other tabs; hits cache
  const { data: employees = [] } = useQuery<any[]>({
    queryKey: ["hr-employees"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/hr/employees?size=200")),
  })

  // Disciplinary records for the selected employee
  const { data: records = [], isLoading } = useQuery<any[]>({
    queryKey: ["disciplinary", selectedEmp],
    queryFn: async () => selectedEmp
      ? unwrapList(await apiClient.get(`/api/v1/hr/employees/${selectedEmp}/disciplinary`))
      : [],
    enabled: !!selectedEmp,
  })

  const addRecord = useMutation({
    mutationFn: (body: any) =>
      apiClient.post(`/api/v1/hr/employees/${form.employeeId}/disciplinary`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["disciplinary", form.employeeId] })
      qc.invalidateQueries({ queryKey: ["hr-employees"] })   // status may change (e.g. SUSPENDED)
      setShowAdd(false); setForm(EMPTY_FORM); setApiError("")
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to add record"),
  })

  const selectedEmployee = (employees as any[]).find(e => e.id === selectedEmp)

  // Sort chronologically (most recent first for the list, earliest first for the escalation ladder)
  const sorted = [...(records as any[])].sort((a, b) =>
    new Date(b.incidentDate).getTime() - new Date(a.incidentDate).getTime())

  const byType = (t: string) => (records as any[]).filter(r => r.incidentType === t).length

  const inp: React.CSSProperties = {
    width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0",
    borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none", background: "#fff",
  }
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

  return (
    <div>
      {/* CCMA compliance notice */}
      <div style={{ marginBottom: 18, padding: "10px 16px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 12, color: "#B91C1C", display: "flex", gap: 9, alignItems: "flex-start" }}>
        <ShieldAlert size={14} style={{ flexShrink: 0, marginTop: 1 }} />
        <div>
          <strong>CCMA progressive discipline:</strong> Verbal warning → Written warning → Final written warning → Notice to Attend (hearing) → Outcome.
          Dismissal without fair process is automatically unfair under LRA section 188. All records are time-stamped and immutable for audit purposes.
        </div>
      </div>

      {/* Employee selector + add button */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <label style={{ fontSize: 14, fontWeight: 600, color: "#374151", whiteSpace: "nowrap" as const }}>Employee:</label>
          <select value={selectedEmp} onChange={e => setSelectedEmp(e.target.value)}
            style={{ padding: "9px 14px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", minWidth: 280, outline: "none", color: "#0F172A" }}>
            <option value="">Select employee...</option>
            {(employees as any[]).map(e => (
              <option key={e.id} value={e.id}>{e.fullName} ({e.employeeNumber})</option>
            ))}
          </select>
        </div>
        {selectedEmp && (
          <button
            onClick={() => { setShowAdd(true); setForm({ ...EMPTY_FORM, employeeId: selectedEmp, incidentDate: new Date().toISOString().split("T")[0] }); setApiError("") }}
            style={{ display: "flex", alignItems: "center", gap: 7, background: "#DC2626", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={15} /> Add Record
          </button>
        )}
      </div>

      {!selectedEmp ? (
        <div style={{ textAlign: "center", padding: "70px 20px", color: "#94A3B8" }}>
          <AlertOctagon size={44} style={{ marginBottom: 14, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, fontSize: 15, color: "#475569" }}>Select an employee to view their disciplinary history</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Records are employee-specific and visible to HR administrators only.</div>
        </div>
      ) : isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading disciplinary records...</div>
      ) : (
        <div>
          {/* Escalation summary bar */}
          <div style={{ marginBottom: 22, padding: "14px 18px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 12 }}>
              Sanction escalation — {selectedEmployee?.fullName} · {records.length} record{records.length !== 1 ? "s" : ""}
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 4, flexWrap: "wrap" }}>
              {ESCALATION_STEPS.map((step, i) => {
                const count = byType(step.type)
                const cfg   = TYPE_CFG[step.type]
                const active = count > 0
                return (
                  <div key={step.type} style={{ display: "flex", alignItems: "center", gap: 4 }}>
                    {i > 0 && <ChevronRight size={12} color={active ? "#94A3B8" : "#CBD5E1"} />}
                    <span style={{
                      padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700,
                      background: active ? cfg.bg : "#F1F5F9",
                      color: active ? cfg.color : "#94A3B8",
                      border: `1px solid ${active ? cfg.border : "#E2E8F0"}`,
                    }}>
                      {cfg.label}{count > 1 ? ` ×${count}` : ""}
                    </span>
                  </div>
                )
              })}
            </div>

            {/* Active warnings summary */}
            {records.length > 0 && (
              <div style={{ marginTop: 10, display: "flex", gap: 8, flexWrap: "wrap" }}>
                {(records as any[]).map(r => {
                  const cfg = TYPE_CFG[r.incidentType] ?? TYPE_CFG.OTHER
                  return (
                    <span key={r.id} style={{ background: cfg.bg, color: cfg.color, padding: "2px 9px", borderRadius: 20, fontSize: 10, fontWeight: 700, border: `1px solid ${cfg.border}` }}>
                      {cfg.label} · {fmtDate(r.incidentDate)}
                    </span>
                  )
                })}
              </div>
            )}
          </div>

          {/* Record list */}
          {sorted.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", border: "1px dashed #E2E8F0", borderRadius: 12 }}>
              No disciplinary records for {selectedEmployee?.fullName}.
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {sorted.map(r => {
                const cfg = TYPE_CFG[r.incidentType] ?? TYPE_CFG.OTHER
                return (
                  <div key={r.id} style={{ border: `1px solid ${cfg.border}`, borderLeft: `4px solid ${cfg.color}`, borderRadius: 10, padding: "14px 18px", background: "#fff" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8, flexWrap: "wrap" }}>
                      <span style={{ background: cfg.bg, color: cfg.color, padding: "2px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{cfg.label}</span>
                      <span style={{ fontSize: 12, color: "#64748B" }}>Incident: {fmtDate(r.incidentDate)}</span>
                      {r.hearingDate && <span style={{ fontSize: 12, color: "#64748B" }}>· Hearing: {fmtDate(r.hearingDate)}</span>}
                      {r.acknowledged && (
                        <span style={{ fontSize: 11, fontWeight: 700, background: "#DCFCE7", color: "#166534", padding: "1px 7px", borderRadius: 20, border: "1px solid #86EFAC" }}>
                          ✓ Acknowledged
                        </span>
                      )}
                    </div>
                    <div style={{ fontSize: 14, color: "#374151", lineHeight: 1.6 }}>{r.description}</div>
                    {r.outcome && (
                      <div style={{ marginTop: 10, padding: "9px 12px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 13, color: "#475569" }}>
                        <strong>Outcome:</strong> {r.outcome}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}

      {/* Add record modal */}
      {showAdd && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 580, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Add Disciplinary Record</h3>
                <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 2 }}>{selectedEmployee?.fullName} · {selectedEmployee?.employeeNumber}</div>
              </div>
              <button onClick={() => setShowAdd(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Incident Type *</label>
                <select value={form.incidentType} onChange={e => setForm(f => ({ ...f, incidentType: e.target.value }))} style={inp}>
                  {INCIDENT_TYPES.map(t => <option key={t} value={t}>{TYPE_CFG[t]?.label ?? t}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Incident Date *</label>
                <input type="date" value={form.incidentDate} onChange={e => setForm(f => ({ ...f, incidentDate: e.target.value }))} style={inp} />
              </div>
              <div>
                <label style={lbl}>Hearing Date <span style={{ fontWeight: 400, color: "#94A3B8" }}>(if applicable)</span></label>
                <input type="date" value={form.hearingDate} onChange={e => setForm(f => ({ ...f, hearingDate: e.target.value }))} style={inp} />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2 }}>Required for NTA, suspension, and dismissal</div>
              </div>
              <div />
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Description of Incident *</label>
                <textarea autoFocus value={form.description}
                  onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                  rows={5} placeholder="Describe the incident precisely: what occurred, which policy/rule was contravened, any witnesses. Factual language only — this record is permanent."
                  style={{ ...inp, resize: "vertical" as const }} />
              </div>
            </div>

            {/* Context-sensitive CCMA guidance */}
            {form.incidentType === "DISMISSAL" && (
              <div style={{ marginTop: 12, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 12, color: "#B91C1C" }}>
                <strong>Before dismissal:</strong> A written Notice to Attend, a formal hearing, and an outcome letter must be on record.
                Dismissal without a fair procedure is automatically unfair under LRA s.188. Ensure prior warnings and the NTA are already captured.
              </div>
            )}
            {form.incidentType === "SUSPENSION" && (
              <div style={{ marginTop: 12, padding: "10px 14px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 8, fontSize: 12, color: "#1D4ED8" }}>
                <strong>Suspension:</strong> Preventive suspension (on full pay) is permissible pending a disciplinary hearing. Suspension as a sanction after conviction is punitive and must form part of the outcome, not precede the hearing.
              </div>
            )}
            {(form.incidentType === "VERBAL_WARNING" || form.incidentType === "WRITTEN_WARNING") && (
              <div style={{ marginTop: 12, padding: "10px 14px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 8, fontSize: 12, color: "#92400E" }}>
                <strong>Validity:</strong> Verbal warnings: 3 months. Written warnings: 3–6 months. Final written warnings: 6 months.
                Expired warnings cannot be used to justify dismissal but remain on record.
              </div>
            )}

            {apiError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={14} />{apiError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowAdd(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button
                onClick={() => addRecord.mutate({
                  incidentDate: form.incidentDate,
                  incidentType: form.incidentType,
                  description:  form.description,
                  hearingDate:  form.hearingDate || null,
                })}
                disabled={!form.incidentDate || !form.description.trim() || addRecord.isPending}
                style={{ padding: "9px 22px", background: !form.incidentDate || !form.description.trim() ? "#94A3B8" : "#DC2626", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {addRecord.isPending ? "Adding..." : "Add Record"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
