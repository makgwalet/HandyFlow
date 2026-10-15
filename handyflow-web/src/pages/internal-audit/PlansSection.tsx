// Annual audit plans: entries by quarter, approval.

import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  CalendarRange, Plus, ChevronDown, ChevronUp, CheckCircle2,
} from "lucide-react"
import { type UniverseEntry, type AnnualPlan, lbl, inp, cancelBtn, submitBtn, modalOverlay, modalBox } from "./auditShared"
import { RiskBadge } from "./RiskBadge"

export function PlansSection({ plans, universe, isLoading, qc }: { plans: AnnualPlan[]; universe: UniverseEntry[]; isLoading: boolean; qc: ReturnType<typeof useQueryClient> }) {
  const [error, setError] = useState("")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [showNewPlan, setShowNewPlan] = useState(false)
  const [planYear, setPlanYear] = useState(new Date().getFullYear() + 1)
  const [showAddEntry, setShowAddEntry] = useState<string | null>(null)
  const [entryForm, setEntryForm] = useState({ universeEntryId: "", plannedQuarter: 1, rationale: "" })

  const createPlan = useMutation({
    mutationFn: () => apiClient.post("/api/v1/internal-audit/plans", { planYear }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-plans"] }); setShowNewPlan(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create plan"),
  })
  const approvePlan = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/internal-audit/plans/${id}/approve`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ia-plans"] }),
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to approve plan"),
  })
  const addEntry = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/internal-audit/plans/${showAddEntry}/entries`, entryForm),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-plans"] }); setShowAddEntry(null); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to add entry"),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={() => { setPlanYear(new Date().getFullYear() + 1); setShowNewPlan(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> New Annual Plan
        </button>
      </div>
      {error && <div style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 14 }}>{error}</div>}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 30, color: "var(--hf-text-faint)" }}>Loading…</div>
      ) : plans.length === 0 ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "var(--hf-text-faint)", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          <CalendarRange size={30} style={{ marginBottom: 10, opacity: 0.3 }} />
          No annual plans yet.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {plans.map(p => (
            <div key={p.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 16px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <button onClick={() => setExpanded(expanded === p.id ? null : p.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}>
                    {expanded === p.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                  </button>
                  <div style={{ fontWeight: 700, fontSize: 15, color: "var(--hf-text)" }}>{p.planYear} Audit Plan</div>
                  <span style={{ fontSize: 11, fontWeight: 700, padding: "3px 10px", borderRadius: 20, background: p.status === "APPROVED" || p.status === "ACTIVE" ? "var(--hf-success-soft-strong)" : "var(--hf-surface-sunken)", color: p.status === "APPROVED" || p.status === "ACTIVE" ? "var(--hf-success-text-strong)" : "var(--hf-text-muted)" }}>{p.status}</span>
                </div>
                {p.status === "DRAFT" && (
                  <button onClick={() => approvePlan.mutate(p.id)} disabled={approvePlan.isPending}
                    style={{ display: "flex", alignItems: "center", gap: 6, padding: "7px 14px", background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", border: "1px solid var(--hf-success-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                    <CheckCircle2 size={13} /> Approve
                  </button>
                )}
              </div>

              {expanded === p.id && (
                <div style={{ padding: "0 16px 16px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10, paddingTop: 12, borderTop: "1px solid var(--hf-border-subtle)" }}>
                    <span style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const }}>Plan Entries — {p.entries.length}</span>
                    <button onClick={() => { setEntryForm({ universeEntryId: "", plannedQuarter: 1, rationale: "" }); setShowAddEntry(p.id); setError("") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", borderRadius: 6, border: "1px solid var(--hf-primary)", background: "var(--hf-info-soft)", color: "var(--hf-primary-text)", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                      <Plus size={11} /> Add Entry
                    </button>
                  </div>
                  {p.entries.length === 0 ? (
                    <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No universe entries selected into this plan yet.</p>
                  ) : (
                    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                      {p.entries.map(e => (
                        <div key={e.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "9px 12px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }}>
                          <div>
                            <strong style={{ color: "var(--hf-text)" }}>{e.universeEntryName}</strong>
                            {e.plannedQuarter && <span style={{ color: "var(--hf-text-faint)", marginLeft: 8 }}>Q{e.plannedQuarter}</span>}
                            {e.rationale && <div style={{ color: "var(--hf-text-faint)", marginTop: 2 }}>{e.rationale}</div>}
                          </div>
                          <RiskBadge level={e.riskLevel} />
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

      {showNewPlan && (
        <div style={modalOverlay}>
          <div style={{ ...modalBox, width: 360 }}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>New Annual Plan</h3>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <label htmlFor="ia-pla-1" style={lbl}>Plan Year</label>
            <input id="ia-pla-1" type="number" value={planYear} onChange={e => setPlanYear(Number(e.target.value))} style={inp} />
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowNewPlan(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createPlan.mutate()} disabled={createPlan.isPending} style={submitBtn}>{createPlan.isPending ? "Creating…" : "Create"}</button>
            </div>
          </div>
        </div>
      )}

      {showAddEntry && (
        <div style={modalOverlay}>
          <div role="dialog" aria-modal="true" style={modalBox}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Add Plan Entry</h3>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-pla-2" style={lbl}>Universe Entry *</label>
              <select id="ia-pla-2" value={entryForm.universeEntryId} onChange={e => setEntryForm(f => ({ ...f, universeEntryId: e.target.value }))} style={inp}>
                <option value="">Select…</option>
                {universe.map(u => <option key={u.id} value={u.id}>{u.name}{u.currentRiskLevel ? ` (${u.currentRiskLevel})` : ""}</option>)}
              </select>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-pla-3" style={lbl}>Planned Quarter</label>
              <select id="ia-pla-3" value={entryForm.plannedQuarter} onChange={e => setEntryForm(f => ({ ...f, plannedQuarter: Number(e.target.value) }))} style={inp}>
                <option value={1}>Q1</option><option value={2}>Q2</option><option value={3}>Q3</option><option value={4}>Q4</option>
              </select>
            </div>
            <div style={{ marginBottom: 18 }}>
              <label htmlFor="ia-pla-4" style={lbl}>Rationale</label>
              <textarea id="ia-pla-4" value={entryForm.rationale} onChange={e => setEntryForm(f => ({ ...f, rationale: e.target.value }))} rows={2}
                placeholder="Why this entry was selected — risk score, regulatory mandate, board request, etc." style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowAddEntry(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => addEntry.mutate()} disabled={!entryForm.universeEntryId || addEntry.isPending} style={submitBtn}>
                {addEntry.isPending ? "Adding…" : "Add"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Engagements Section ──────────────────────────────────────────────────────
