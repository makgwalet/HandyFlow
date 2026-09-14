// src/pages/internal-audit/InternalAuditPage.tsx
//
// Internal Audit Phase 1, per the design agreed with the product owner
// across several rounds of detailed scoping. See
// InternalAuditController's own class comment (backend) for the fuller
// design context and what's deliberately deferred to later phases
// (workpapers, GL sampling, findings/remediation, report sign-off).

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Database, CalendarRange, ClipboardList, Plus, ChevronDown, ChevronUp,
  ShieldAlert, Users2, UserPlus, CheckCircle2, Ban,
} from "lucide-react"

// ── Types ──────────────────────────────────────────────────────────────────────

interface UniverseEntry {
  id: string; name: string; description: string | null; processArea: string | null
  glAccountGroup: string | null; lastAuditDate: string | null; active: boolean
  createdAt: string; currentRiskLevel: string | null
}
interface RiskAssessment {
  id: string; universeEntryId: string
  inherentRiskScore: number; controlRiskScore: number; historicalFindingsScore: number
  timeSinceLastAuditScore: number; businessRegulatoryImpactScore: number
  systemCalculatedRisk: string; finalAuditRisk: string
  overrideReason: string | null; overrideApprovedBy: string | null
  assessedBy: string | null; assessedAt: string
}
interface PlanEntry {
  id: string; universeEntryId: string; universeEntryName: string
  riskAssessmentId: string | null; riskLevel: string | null
  plannedQuarter: number | null; rationale: string | null; status: string; createdAt: string
}
interface AnnualPlan {
  id: string; planYear: number; status: string
  approvedBy: string | null; approvedAt: string | null; createdBy: string | null; createdAt: string
  entries: PlanEntry[]
}
interface EngagementAssignment {
  id: string; userId: string; userName: string; role: string; assignedBy: string | null; assignedAt: string
}
interface Engagement {
  id: string; planEntryId: string | null; universeEntryId: string; universeEntryName: string
  name: string; status: string; startDate: string | null; endDate: string | null
  createdBy: string | null; createdAt: string; assignments: EngagementAssignment[]
}
interface UserOption { id: string; firstName: string; lastName: string; email: string }

// ── Config ─────────────────────────────────────────────────────────────────────

const RISK_CFG: Record<string, { color: string; bg: string }> = {
  LOW:      { color: "#166534", bg: "#DCFCE7" },
  MEDIUM:   { color: "#B45309", bg: "#FFFBEB" },
  HIGH:     { color: "#C2410C", bg: "#FFEDD5" },
  CRITICAL: { color: "#DC2626", bg: "#FEF2F2" },
}
const ENGAGEMENT_ROLES = ["HEAD_OF_INTERNAL_AUDIT", "AUDIT_MANAGER", "SENIOR_AUDITOR", "AUDITOR", "AUDIT_REVIEWER"]

const TOP_TABS = [
  { id: "universe",    label: "Audit Universe", icon: Database },
  { id: "plans",       label: "Annual Plans",   icon: CalendarRange },
  { id: "engagements", label: "Engagements",    icon: ClipboardList },
] as const

const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff", outline: "none" }
const cancelBtn: React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const submitBtn: React.CSSProperties = { padding: "9px 18px", border: "none", borderRadius: 9, background: "#1B3A6B", color: "#fff", fontSize: 14, fontWeight: 600, cursor: "pointer" }
const modalOverlay: React.CSSProperties = { position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }
const modalBox: React.CSSProperties = { background: "#fff", borderRadius: 14, padding: 26, width: 460, maxHeight: "85vh", overflowY: "auto" as const }

function RiskBadge({ level }: { level: string | null }) {
  if (!level) return <span style={{ fontSize: 11, color: "#94A3B8" }}>Not assessed</span>
  const c = RISK_CFG[level] ?? RISK_CFG.LOW
  return <span style={{ fontSize: 11, fontWeight: 700, padding: "3px 10px", borderRadius: 20, background: c.bg, color: c.color }}>{level}</span>
}

// ── Main page ────────────────────────────────────────────────────────────────

export default function InternalAuditPage() {
  const [tab, setTab] = useState<typeof TOP_TABS[number]["id"]>("universe")
  const qc = useQueryClient()

  const { data: universe = [], isLoading: universeLoading } = useQuery<UniverseEntry[]>({
    queryKey: ["ia-universe"],
    queryFn: async () => (await apiClient.get("/api/v1/internal-audit/universe")).data,
  })
  const { data: plans = [], isLoading: plansLoading } = useQuery<AnnualPlan[]>({
    queryKey: ["ia-plans"],
    queryFn: async () => (await apiClient.get("/api/v1/internal-audit/plans")).data,
  })
  const { data: engagements = [], isLoading: engagementsLoading } = useQuery<Engagement[]>({
    queryKey: ["ia-engagements"],
    queryFn: async () => (await apiClient.get("/api/v1/internal-audit/engagements")).data,
  })
  const { data: users = [] } = useQuery<UserOption[]>({
    queryKey: ["ia-users"],
    queryFn: async () => (await apiClient.get("/api/v1/identity/users?size=200")).data?.content ?? (await apiClient.get("/api/v1/identity/users?size=200")).data,
  })

  return (
    <div style={{ padding: "24px 28px" }}>
      <div style={{ marginBottom: 18 }}>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 800, color: "#0F172A" }}>Internal Audit</h1>
        <div style={{ fontSize: 13, color: "#64748B", marginTop: 2 }}>Risk-based audit planning — Phase 1: universe, risk scoring, annual plan, engagements</div>
      </div>

      <div style={{ display: "flex", gap: 6, marginBottom: 20, borderBottom: "1px solid #E2E8F0" }}>
        {TOP_TABS.map(t => {
          const Icon = t.icon; const active = tab === t.id
          return (
            <button key={t.id} onClick={() => setTab(t.id)}
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "10px 16px", background: "none", border: "none", borderBottom: active ? "2px solid #1B3A6B" : "2px solid transparent", color: active ? "#1B3A6B" : "#64748B", fontWeight: active ? 700 : 500, fontSize: 13, cursor: "pointer" }}>
              <Icon size={15} /> {t.label}
            </button>
          )
        })}
      </div>

      {tab === "universe" && <UniverseSection universe={universe} isLoading={universeLoading} qc={qc} />}
      {tab === "plans" && <PlansSection plans={plans} universe={universe} isLoading={plansLoading} qc={qc} />}
      {tab === "engagements" && <EngagementsSection engagements={engagements} universe={universe} plans={plans} users={users} isLoading={engagementsLoading} qc={qc} />}
    </div>
  )
}

// ── Universe Section ─────────────────────────────────────────────────────────

function UniverseSection({ universe, isLoading, qc }: { universe: UniverseEntry[]; isLoading: boolean; qc: ReturnType<typeof useQueryClient> }) {
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
        <button onClick={openNew} style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> New Universe Entry
        </button>
      </div>
      {error && <div style={{ padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13, marginBottom: 14 }}>{error}</div>}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 30, color: "#94A3B8" }}>Loading…</div>
      ) : universe.length === 0 ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "#94A3B8", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12 }}>
          <Database size={30} style={{ marginBottom: 10, opacity: 0.3 }} />
          No universe entries yet — register the business processes or GL account groups you plan to audit.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {universe.map(u => (
            <div key={u.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10, opacity: u.active ? 1 : 0.6 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 16px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <button onClick={() => setExpanded(expanded === u.id ? null : u.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                    {expanded === u.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                  </button>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{u.name}</div>
                    <div style={{ fontSize: 12, color: "#94A3B8" }}>{u.processArea || "—"}{u.glAccountGroup ? ` · ${u.glAccountGroup}` : ""}</div>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <RiskBadge level={u.currentRiskLevel} />
                  <button onClick={() => openEdit(u)} style={{ padding: "6px 12px", borderRadius: 7, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>Edit</button>
                  {u.active && (
                    <button onClick={() => deactivate.mutate(u.id)} style={{ padding: "6px 8px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 7, cursor: "pointer", color: "#DC2626" }}><Ban size={13} /></button>
                  )}
                </div>
              </div>

              {expanded === u.id && (
                <div style={{ padding: "0 16px 16px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10, paddingTop: 12, borderTop: "1px solid #F1F5F9" }}>
                    <span style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const }}>Risk Assessment History</span>
                    <button onClick={() => { setAssessForm({ inherentRiskScore: 3, controlRiskScore: 3, historicalFindingsScore: 3, businessRegulatoryImpactScore: 3 }); setShowAssessForm(u.id); setError("") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", borderRadius: 6, border: "1px solid #7C3AED", background: "#F5F3FF", color: "#7C3AED", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                      <ShieldAlert size={11} /> New Assessment
                    </button>
                  </div>
                  {riskHistoryQuery.isLoading ? (
                    <p style={{ fontSize: 12, color: "#94A3B8" }}>Loading…</p>
                  ) : !riskHistoryQuery.data?.length ? (
                    <p style={{ fontSize: 12, color: "#94A3B8" }}>No risk assessments recorded yet.</p>
                  ) : (
                    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                      {riskHistoryQuery.data.map(r => (
                        <div key={r.id} style={{ padding: "10px 12px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 12 }}>
                          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                              <span style={{ color: "#94A3B8" }}>System-calculated:</span> <RiskBadge level={r.systemCalculatedRisk} />
                              <span style={{ color: "#94A3B8" }}>→ Final:</span> <RiskBadge level={r.finalAuditRisk} />
                            </div>
                            {r.finalAuditRisk === r.systemCalculatedRisk && (
                              <button onClick={() => { setOverrideForm({ finalAuditRisk: r.finalAuditRisk, reason: "" }); setShowOverride(r) }}
                                style={{ padding: "4px 10px", borderRadius: 6, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                                Override
                              </button>
                            )}
                          </div>
                          {r.overrideReason && <div style={{ color: "#B45309", marginTop: 6, fontStyle: "italic" }}>"{r.overrideReason}"</div>}
                          <div style={{ color: "#94A3B8", marginTop: 6 }}>
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
          <div style={modalBox}>
            <h3 style={{ margin: "0 0 18px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>{showForm === "new" ? "New Universe Entry" : "Edit Universe Entry"}</h3>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Name *</label>
              <input value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="Payroll" style={inp} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Process Area</label>
              <input value={form.processArea} onChange={e => setForm(f => ({ ...f, processArea: e.target.value }))} placeholder="e.g. Payroll, Procurement, Journal Entries" style={inp} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>GL Account Group (optional)</label>
              <input value={form.glAccountGroup} onChange={e => setForm(f => ({ ...f, glAccountGroup: e.target.value }))} style={inp} />
            </div>
            <div style={{ marginBottom: 18 }}>
              <label style={lbl}>Description</label>
              <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
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
          <div style={modalBox}>
            <h3 style={{ margin: "0 0 6px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>New Risk Assessment</h3>
            <p style={{ fontSize: 12, color: "#94A3B8", marginBottom: 16 }}>Score each factor 1 (lowest) to 5 (highest). "Time since last audit" is calculated automatically from this entry's last audit date.</p>
            {error && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>{error}</p>}
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
          <div style={modalBox}>
            <h3 style={{ margin: "0 0 6px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>Override Risk Level</h3>
            <p style={{ fontSize: 12, color: "#94A3B8", marginBottom: 16 }}>System-calculated: <strong>{showOverride.systemCalculatedRisk}</strong>. A reason is required if you change this.</p>
            {error && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Final Audit Risk</label>
              <select value={overrideForm.finalAuditRisk} onChange={e => setOverrideForm(f => ({ ...f, finalAuditRisk: e.target.value }))} style={inp}>
                <option value="LOW">Low</option><option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option><option value="CRITICAL">Critical</option>
              </select>
            </div>
            <div style={{ marginBottom: 18 }}>
              <label style={lbl}>Reason {overrideForm.finalAuditRisk !== showOverride.systemCalculatedRisk && <span style={{ color: "#DC2626" }}>*</span>}</label>
              <textarea value={overrideForm.reason} onChange={e => setOverrideForm(f => ({ ...f, reason: e.target.value }))} rows={3}
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

function PlansSection({ plans, universe, isLoading, qc }: { plans: AnnualPlan[]; universe: UniverseEntry[]; isLoading: boolean; qc: ReturnType<typeof useQueryClient> }) {
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
          style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> New Annual Plan
        </button>
      </div>
      {error && <div style={{ padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13, marginBottom: 14 }}>{error}</div>}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 30, color: "#94A3B8" }}>Loading…</div>
      ) : plans.length === 0 ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "#94A3B8", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12 }}>
          <CalendarRange size={30} style={{ marginBottom: 10, opacity: 0.3 }} />
          No annual plans yet.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {plans.map(p => (
            <div key={p.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 16px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <button onClick={() => setExpanded(expanded === p.id ? null : p.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                    {expanded === p.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                  </button>
                  <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{p.planYear} Audit Plan</div>
                  <span style={{ fontSize: 11, fontWeight: 700, padding: "3px 10px", borderRadius: 20, background: p.status === "APPROVED" || p.status === "ACTIVE" ? "#DCFCE7" : "#F1F5F9", color: p.status === "APPROVED" || p.status === "ACTIVE" ? "#166534" : "#64748B" }}>{p.status}</span>
                </div>
                {p.status === "DRAFT" && (
                  <button onClick={() => approvePlan.mutate(p.id)} disabled={approvePlan.isPending}
                    style={{ display: "flex", alignItems: "center", gap: 6, padding: "7px 14px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                    <CheckCircle2 size={13} /> Approve
                  </button>
                )}
              </div>

              {expanded === p.id && (
                <div style={{ padding: "0 16px 16px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10, paddingTop: 12, borderTop: "1px solid #F1F5F9" }}>
                    <span style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const }}>Plan Entries — {p.entries.length}</span>
                    <button onClick={() => { setEntryForm({ universeEntryId: "", plannedQuarter: 1, rationale: "" }); setShowAddEntry(p.id); setError("") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", borderRadius: 6, border: "1px solid #1B3A6B", background: "#EFF6FF", color: "#1B3A6B", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                      <Plus size={11} /> Add Entry
                    </button>
                  </div>
                  {p.entries.length === 0 ? (
                    <p style={{ fontSize: 12, color: "#94A3B8" }}>No universe entries selected into this plan yet.</p>
                  ) : (
                    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                      {p.entries.map(e => (
                        <div key={e.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "9px 12px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 12 }}>
                          <div>
                            <strong style={{ color: "#0F172A" }}>{e.universeEntryName}</strong>
                            {e.plannedQuarter && <span style={{ color: "#94A3B8", marginLeft: 8 }}>Q{e.plannedQuarter}</span>}
                            {e.rationale && <div style={{ color: "#94A3B8", marginTop: 2 }}>{e.rationale}</div>}
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
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>New Annual Plan</h3>
            {error && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <label style={lbl}>Plan Year</label>
            <input type="number" value={planYear} onChange={e => setPlanYear(Number(e.target.value))} style={inp} />
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowNewPlan(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createPlan.mutate()} disabled={createPlan.isPending} style={submitBtn}>{createPlan.isPending ? "Creating…" : "Create"}</button>
            </div>
          </div>
        </div>
      )}

      {showAddEntry && (
        <div style={modalOverlay}>
          <div style={modalBox}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>Add Plan Entry</h3>
            {error && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Universe Entry *</label>
              <select value={entryForm.universeEntryId} onChange={e => setEntryForm(f => ({ ...f, universeEntryId: e.target.value }))} style={inp}>
                <option value="">Select…</option>
                {universe.map(u => <option key={u.id} value={u.id}>{u.name}{u.currentRiskLevel ? ` (${u.currentRiskLevel})` : ""}</option>)}
              </select>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Planned Quarter</label>
              <select value={entryForm.plannedQuarter} onChange={e => setEntryForm(f => ({ ...f, plannedQuarter: Number(e.target.value) }))} style={inp}>
                <option value={1}>Q1</option><option value={2}>Q2</option><option value={3}>Q3</option><option value={4}>Q4</option>
              </select>
            </div>
            <div style={{ marginBottom: 18 }}>
              <label style={lbl}>Rationale</label>
              <textarea value={entryForm.rationale} onChange={e => setEntryForm(f => ({ ...f, rationale: e.target.value }))} rows={2}
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

function EngagementsSection({ engagements, universe, plans, users, isLoading, qc }: {
  engagements: Engagement[]; universe: UniverseEntry[]; plans: AnnualPlan[]; users: UserOption[]
  isLoading: boolean; qc: ReturnType<typeof useQueryClient>
}) {
  const [error, setError] = useState("")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [showNew, setShowNew] = useState(false)
  const [form, setForm] = useState({ universeEntryId: "", planEntryId: "", name: "", startDate: "", endDate: "" })
  const [showAssign, setShowAssign] = useState<string | null>(null)
  const [assignForm, setAssignForm] = useState({ userId: "", role: "AUDITOR" })

  const allPlanEntries = plans.flatMap(p => p.entries.map(e => ({ ...e, planYear: p.planYear })))

  const createEngagement = useMutation({
    mutationFn: () => apiClient.post("/api/v1/internal-audit/engagements", {
      universeEntryId: form.universeEntryId, planEntryId: form.planEntryId || null,
      name: form.name, startDate: form.startDate || null, endDate: form.endDate || null,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-engagements"] }); setShowNew(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create engagement"),
  })
  const assignRole = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/internal-audit/engagements/${showAssign}/assignments`, assignForm),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-engagements"] }); setShowAssign(null); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to assign role"),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={() => { setForm({ universeEntryId: "", planEntryId: "", name: "", startDate: "", endDate: "" }); setShowNew(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> New Engagement
        </button>
      </div>
      {error && <div style={{ padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13, marginBottom: 14 }}>{error}</div>}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 30, color: "#94A3B8" }}>Loading…</div>
      ) : engagements.length === 0 ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "#94A3B8", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12 }}>
          <ClipboardList size={30} style={{ marginBottom: 10, opacity: 0.3 }} />
          No engagements yet.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {engagements.map(e => (
            <div key={e.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 16px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <button onClick={() => setExpanded(expanded === e.id ? null : e.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                    {expanded === e.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                  </button>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{e.name}</div>
                    <div style={{ fontSize: 12, color: "#94A3B8" }}>{e.universeEntryName}{!e.planEntryId && " · Ad-hoc"}</div>
                  </div>
                </div>
                <span style={{ fontSize: 11, fontWeight: 700, padding: "3px 10px", borderRadius: 20, background: "#F1F5F9", color: "#64748B" }}>{e.status}</span>
              </div>

              {expanded === e.id && (
                <div style={{ padding: "0 16px 16px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10, paddingTop: 12, borderTop: "1px solid #F1F5F9" }}>
                    <span style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const }}>Team — {e.assignments.length}</span>
                    <button onClick={() => { setAssignForm({ userId: "", role: "AUDITOR" }); setShowAssign(e.id); setError("") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", borderRadius: 6, border: "1px solid #1B3A6B", background: "#EFF6FF", color: "#1B3A6B", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                      <UserPlus size={11} /> Assign
                    </button>
                  </div>
                  {e.assignments.length === 0 ? (
                    <p style={{ fontSize: 12, color: "#94A3B8" }}>No team members assigned yet.</p>
                  ) : (
                    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                      {e.assignments.map(a => (
                        <div key={a.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 12px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 12 }}>
                          <Users2 size={13} color="#7C3AED" />
                          <span style={{ fontWeight: 600, color: "#0F172A" }}>{a.userName}</span>
                          <span style={{ color: "#7C3AED", fontWeight: 600 }}>{a.role.replace(/_/g, " ")}</span>
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

      {showNew && (
        <div style={modalOverlay}>
          <div style={modalBox}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>New Engagement</h3>
            {error && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Name *</label>
              <input value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="2026 Payroll Audit" style={inp} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Universe Entry *</label>
              <select value={form.universeEntryId} onChange={e => setForm(f => ({ ...f, universeEntryId: e.target.value }))} style={inp}>
                <option value="">Select…</option>
                {universe.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
              </select>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Plan Entry (optional — leave blank for an ad-hoc engagement)</label>
              <select value={form.planEntryId} onChange={e => setForm(f => ({ ...f, planEntryId: e.target.value }))} style={inp}>
                <option value="">None (ad-hoc)</option>
                {allPlanEntries.map(pe => <option key={pe.id} value={pe.id}>{pe.planYear} — {pe.universeEntryName}</option>)}
              </select>
            </div>
            <div style={{ display: "flex", gap: 12, marginBottom: 18 }}>
              <div style={{ flex: 1 }}>
                <label style={lbl}>Start Date</label>
                <input type="date" value={form.startDate} onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} style={inp} />
              </div>
              <div style={{ flex: 1 }}>
                <label style={lbl}>End Date</label>
                <input type="date" value={form.endDate} onChange={e => setForm(f => ({ ...f, endDate: e.target.value }))} style={inp} />
              </div>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowNew(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createEngagement.mutate()} disabled={!form.name.trim() || !form.universeEntryId || createEngagement.isPending} style={submitBtn}>
                {createEngagement.isPending ? "Creating…" : "Create"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showAssign && (
        <div style={modalOverlay}>
          <div style={{ ...modalBox, width: 380 }}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>Assign Engagement Role</h3>
            {error && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>User *</label>
              <select value={assignForm.userId} onChange={e => setAssignForm(f => ({ ...f, userId: e.target.value }))} style={inp}>
                <option value="">Select…</option>
                {users.map(u => <option key={u.id} value={u.id}>{u.firstName} {u.lastName}</option>)}
              </select>
            </div>
            <div style={{ marginBottom: 18 }}>
              <label style={lbl}>Role</label>
              <select value={assignForm.role} onChange={e => setAssignForm(f => ({ ...f, role: e.target.value }))} style={inp}>
                {ENGAGEMENT_ROLES.map(r => <option key={r} value={r}>{r.replace(/_/g, " ")}</option>)}
              </select>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowAssign(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => assignRole.mutate()} disabled={!assignForm.userId || assignRole.isPending} style={submitBtn}>
                {assignRole.isPending ? "Assigning…" : "Assign"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
