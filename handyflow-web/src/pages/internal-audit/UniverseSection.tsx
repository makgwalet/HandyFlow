// Audit universe: the list of auditable areas, their risk assessments and overrides.

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Database, Plus, ChevronDown, ChevronUp, ShieldAlert, Ban,
} from "lucide-react"
import { type UniverseEntry, type RiskAssessment, lbl, inp, cancelBtn, submitBtn, modalOverlay, modalBox } from "./auditShared"
import { RiskBadge } from "./RiskBadge"

export function UniverseSection({ universe, isLoading, qc }: { universe: UniverseEntry[]; isLoading: boolean; qc: ReturnType<typeof useQueryClient> }) {
  const [error, setError] = useState("")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [showForm, setShowForm] = useState<UniverseEntry | "new" | null>(null)
  const [form, setForm] = useState({ name: "", description: "", processArea: "", glAccountGroup: "" })
  const [showAssessForm, setShowAssessForm] = useState<string | null>(null)
  const [assessForm, setAssessForm] = useState({ inherentRiskScore: 3, controlRiskScore: 3, historicalFindingsScore: 3, businessRegulatoryImpactScore: 3 })
  const [showOverride, setShowOverride] = useState<RiskAssessment | null>(null)
  const [overrideForm, setOverrideForm] = useState({ finalAuditRisk: "LOW", reason: "" })

  const riskHistoryQuery = useQuery<RiskAssessment[]>({
    queryKey: ["ia-risk-history", expanded],
    queryFn: async () => (await apiClient.get(`/api/v1/internal-audit/universe/${expanded}/risk-assessments`)).data,
    enabled: !!expanded,
  })

  const saveEntry = useMutation({
    mutationFn: () => showForm === "new"
      ? apiClient.post("/api/v1/internal-audit/universe", form)
      : apiClient.put(`/api/v1/internal-audit/universe/${(showForm as UniverseEntry).id}`, form),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-universe"] }); setShowForm(null); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to save"),
  })
  const deactivate = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/internal-audit/universe/${id}/deactivate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ia-universe"] }),
  })
  const createAssessment = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/internal-audit/universe/${showAssessForm}/risk-assessments`, assessForm),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ia-risk-history", showAssessForm] })
      qc.invalidateQueries({ queryKey: ["ia-universe"] })
      setShowAssessForm(null); setAssessForm({ inherentRiskScore: 3, controlRiskScore: 3, historicalFindingsScore: 3, businessRegulatoryImpactScore: 3 }); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to record assessment"),
  })
  const overrideRisk = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/internal-audit/risk-assessments/${showOverride!.id}/override`, overrideForm),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ia-risk-history", expanded] })
      qc.invalidateQueries({ queryKey: ["ia-universe"] })
      setShowOverride(null); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to override risk"),
  })

  const openNew = () => { setForm({ name: "", description: "", processArea: "", glAccountGroup: "" }); setShowForm("new"); setError("") }
  const openEdit = (e: UniverseEntry) => { setForm({ name: e.name, description: e.description ?? "", processArea: e.processArea ?? "", glAccountGroup: e.glAccountGroup ?? "" }); setShowForm(e); setError("") }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={openNew} style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> New Universe Entry
        </button>
      </div>
      {error && <div style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 14 }}>{error}</div>}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 30, color: "var(--hf-text-faint)" }}>Loading…</div>
      ) : universe.length === 0 ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "var(--hf-text-faint)", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          <Database size={30} style={{ marginBottom: 10, opacity: 0.3 }} />
          No universe entries yet — register the business processes or GL account groups you plan to audit.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {universe.map(u => (
            <div key={u.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10, opacity: u.active ? 1 : 0.6 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 16px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <button onClick={() => setExpanded(expanded === u.id ? null : u.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}>
                    {expanded === u.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                  </button>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{u.name}</div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{u.processArea || "—"}{u.glAccountGroup ? ` · ${u.glAccountGroup}` : ""}</div>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <RiskBadge level={u.currentRiskLevel} />
                  <button onClick={() => openEdit(u)} style={{ padding: "6px 12px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>Edit</button>
                  {u.active && (
                    <button onClick={() => deactivate.mutate(u.id)} style={{ padding: "6px 8px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, cursor: "pointer", color: "var(--hf-danger-text)" }}><Ban size={13} /></button>
                  )}
                </div>
              </div>

              {expanded === u.id && (
                <div style={{ padding: "0 16px 16px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10, paddingTop: 12, borderTop: "1px solid var(--hf-border-subtle)" }}>
                    <span style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const }}>Risk Assessment History</span>
                    <button onClick={() => { setAssessForm({ inherentRiskScore: 3, controlRiskScore: 3, historicalFindingsScore: 3, businessRegulatoryImpactScore: 3 }); setShowAssessForm(u.id); setError("") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", borderRadius: 6, border: "1px solid var(--hf-violet)", background: "var(--hf-violet-soft)", color: "var(--hf-violet-text)", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                      <ShieldAlert size={11} /> New Assessment
                    </button>
                  </div>
                  {riskHistoryQuery.isLoading ? (
                    <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Loading…</p>
                  ) : !riskHistoryQuery.data?.length ? (
                    <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No risk assessments recorded yet.</p>
                  ) : (
                    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                      {riskHistoryQuery.data.map(r => (
                        <div key={r.id} style={{ padding: "10px 12px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }}>
                          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                              <span style={{ color: "var(--hf-text-faint)" }}>System-calculated:</span> <RiskBadge level={r.systemCalculatedRisk} />
                              <span style={{ color: "var(--hf-text-faint)" }}>→ Final:</span> <RiskBadge level={r.finalAuditRisk} />
                            </div>
                            {r.finalAuditRisk === r.systemCalculatedRisk && (
                              <button onClick={() => { setOverrideForm({ finalAuditRisk: r.finalAuditRisk, reason: "" }); setShowOverride(r) }}
                                style={{ padding: "4px 10px", borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                                Override
                              </button>
                            )}
                          </div>
                          {r.overrideReason && <div style={{ color: "var(--hf-warning-text-strong)", marginTop: 6, fontStyle: "italic" }}>"{r.overrideReason}"</div>}
                          <div style={{ color: "var(--hf-text-faint)", marginTop: 6 }}>
                            Inherent {r.inherentRiskScore} · Control {r.controlRiskScore} · Findings {r.historicalFindingsScore} · Time Since Audit {r.timeSinceLastAuditScore} · Impact {r.businessRegulatoryImpactScore}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* New/Edit universe entry modal */}
      {showForm && (
        <div style={modalOverlay}>
          <div role="dialog" aria-modal="true" style={modalBox}>
            <h3 style={{ margin: "0 0 18px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>{showForm === "new" ? "New Universe Entry" : "Edit Universe Entry"}</h3>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-uni-1" style={lbl}>Name *</label>
              <input id="ia-uni-1" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="Payroll" style={inp} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-uni-2" style={lbl}>Process Area</label>
              <input id="ia-uni-2" value={form.processArea} onChange={e => setForm(f => ({ ...f, processArea: e.target.value }))} placeholder="e.g. Payroll, Procurement, Journal Entries" style={inp} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-uni-3" style={lbl}>GL Account Group (optional)</label>
              <input id="ia-uni-3" value={form.glAccountGroup} onChange={e => setForm(f => ({ ...f, glAccountGroup: e.target.value }))} style={inp} />
            </div>
            <div style={{ marginBottom: 18 }}>
              <label htmlFor="ia-uni-4" style={lbl}>Description</label>
              <textarea id="ia-uni-4" value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowForm(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => saveEntry.mutate()} disabled={!form.name.trim() || saveEntry.isPending} style={submitBtn}>
                {saveEntry.isPending ? "Saving…" : showForm === "new" ? "Create" : "Save Changes"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* New risk assessment modal */}
      {showAssessForm && (
        <div style={modalOverlay}>
          <div role="dialog" aria-modal="true" style={modalBox}>
            <h3 style={{ margin: "0 0 6px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>New Risk Assessment</h3>
            <p style={{ fontSize: 12, color: "var(--hf-text-faint)", marginBottom: 16 }}>Score each factor 1 (lowest) to 5 (highest). "Time since last audit" is calculated automatically from this entry's last audit date.</p>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            {(["inherentRiskScore", "controlRiskScore", "historicalFindingsScore", "businessRegulatoryImpactScore"] as const).map(field => (
              <div key={field} style={{ marginBottom: 14 }}>
                <label style={lbl}>
                  {field === "inherentRiskScore" ? "Inherent Risk" : field === "controlRiskScore" ? "Control Risk" :
                   field === "historicalFindingsScore" ? "Historical Findings" : "Business/Regulatory Impact"}
                  {" — "}{assessForm[field]}
                </label>
                <input type="range" min={1} max={5} value={assessForm[field]} onChange={e => setAssessForm(f => ({ ...f, [field]: Number(e.target.value) }))} style={{ width: "100%" }} />
              </div>
            ))}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 6 }}>
              <button onClick={() => setShowAssessForm(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createAssessment.mutate()} disabled={createAssessment.isPending} style={submitBtn}>
                {createAssessment.isPending ? "Saving…" : "Record Assessment"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Override risk modal */}
      {showOverride && (
        <div style={modalOverlay}>
          <div role="dialog" aria-modal="true" style={modalBox}>
            <h3 style={{ margin: "0 0 6px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Override Risk Level</h3>
            <p style={{ fontSize: 12, color: "var(--hf-text-faint)", marginBottom: 16 }}>System-calculated: <strong>{showOverride.systemCalculatedRisk}</strong>. A reason is required if you change this.</p>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-uni-5" style={lbl}>Final Audit Risk</label>
              <select id="ia-uni-5" value={overrideForm.finalAuditRisk} onChange={e => setOverrideForm(f => ({ ...f, finalAuditRisk: e.target.value }))} style={inp}>
                <option value="LOW">Low</option><option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option><option value="CRITICAL">Critical</option>
              </select>
            </div>
            <div style={{ marginBottom: 18 }}>
              <label htmlFor="ia-uni-6" style={lbl}>Reason {overrideForm.finalAuditRisk !== showOverride.systemCalculatedRisk && <span style={{ color: "var(--hf-danger-text)" }}>*</span>}</label>
              <textarea id="ia-uni-6" value={overrideForm.reason} onChange={e => setOverrideForm(f => ({ ...f, reason: e.target.value }))} rows={3}
                placeholder="e.g. Major payroll system migration occurred in Q2." style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowOverride(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => overrideRisk.mutate()}
                disabled={overrideRisk.isPending || (overrideForm.finalAuditRisk !== showOverride.systemCalculatedRisk && !overrideForm.reason.trim())}
                style={submitBtn}>
                {overrideRisk.isPending ? "Saving…" : "Save Override"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Annual Plans Section ─────────────────────────────────────────────────────
