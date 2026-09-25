// src/pages/security/RotationPatternsTab.tsx
//
// FIX (P1 backlog): confirmed via direct source read before building —
// RotationController had 7 endpoints (pattern CRUD, guard assignment,
// schedule generation) and zero frontend surface. Also added one small,
// safe backend piece as part of this same change: GET /rotations/
// {patternId}/assignments — see RotationService.getAssignmentsForPattern's
// own comment for why (findActiveByPattern already existed at the
// repository level, just never had a service+controller layer, same
// shape of gap as FuelDelivery.dispatch()/.cancel() found earlier this
// session).
//
// cycleDefinition's exact shape per patternType was read directly out of
// RotationService.isOnDuty()/buildShiftStart() rather than guessed:
//   FIXED_DAYS_ON_OFF:     { onDays, offDays, startHour? }
//   WEEKLY_FIXED:          { monday..sunday: "DAY"|"NIGHT"|"OFF", startHour? }
//   ALTERNATING_DAY_NIGHT: { startHour? } — both weeks are on-duty; this
//                          pattern type reads nothing else server-side.
//   CUSTOM:                caller-interpreted — raw JSON here, matching
//                          that it's explicitly not validated server-side.

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { RefreshCw, Plus, Users2, UserMinus, Ban, CalendarRange, AlertTriangle, CheckCircle2 } from "lucide-react"

// ── Types ──────────────────────────────────────────────────────────────────────

interface Site { id: string; name: string }
interface Guard { id: string; fullName: string }

interface RotationPattern {
  id: string; siteId: string; siteName: string; name: string
  patternType: "FIXED_DAYS_ON_OFF" | "ALTERNATING_DAY_NIGHT" | "WEEKLY_FIXED" | "CUSTOM"
  cycleDefinition: Record<string, any>
  shiftLengthHours: number; assignedGuardCount: number; active: boolean; createdAt: string
}

interface RotationAssignment {
  id: string; guardId: string; guardName: string; patternId: string; patternName: string
  startsAt: string; endsAt: string | null; positionInCycle: number; active: boolean
}

interface GenerateScheduleResult {
  patternId: string; patternName: string; fromDate: string; toDate: string
  shiftsCreated: number; shiftsSkipped: number; warnings: string[]
}

const DAYS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"] as const

const SUB_TABS = [
  { id: "patterns", label: "Patterns", icon: RefreshCw },
  { id: "generate", label: "Generate Schedule", icon: CalendarRange },
] as const

const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "var(--hf-surface)", outline: "none" }
const cancelBtn: React.CSSProperties = { padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }
const submitBtn: React.CSSProperties = { padding: "9px 18px", border: "none", borderRadius: 9, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontSize: 14, fontWeight: 600, cursor: "pointer" }
const todayStr = () => new Date().toISOString().slice(0, 10)

function emptyCycleDef(type: string): Record<string, any> {
  if (type === "FIXED_DAYS_ON_OFF") return { onDays: 4, offDays: 2 }
  if (type === "WEEKLY_FIXED") return Object.fromEntries(DAYS.map(d => [d, "OFF"]))
  if (type === "ALTERNATING_DAY_NIGHT") return {}
  return {} // CUSTOM — edited as raw JSON
}

// ── Main component ─────────────────────────────────────────────────────────────

export default function RotationPatternsTab() {
  const qc = useQueryClient()
  const [subTab, setSubTab] = useState<typeof SUB_TABS[number]["id"]>("patterns")
  const [error, setError] = useState("")

  const { data: sites = [] } = useQuery<Site[]>({
    queryKey: ["sites-list"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/sites?size=100")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as Site[]
    },
  })
  const { data: guards = [] } = useQuery<Guard[]>({
    queryKey: ["guards-list-for-rotation"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/guards?size=200")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as Guard[]
    },
  })

  // ── Patterns ─────────────────────────────────────────────────────────────────

  const { data: patterns = [], isLoading: patternsLoading } = useQuery<RotationPattern[]>({
    queryKey: ["rotation-patterns"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/rotations?size=100")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as RotationPattern[]
    },
  })

  const [showPatternForm, setShowPatternForm] = useState<RotationPattern | "new" | null>(null)
  const [patternForm, setPatternForm] = useState({
    siteId: "", name: "", patternType: "FIXED_DAYS_ON_OFF", shiftLengthHours: 12,
    cycleDefinition: emptyCycleDef("FIXED_DAYS_ON_OFF") as Record<string, any>,
    customJson: "{}",
  })

  const createPattern = useMutation({
    mutationFn: () => apiClient.post("/api/v1/security/rotations", {
      siteId: patternForm.siteId, name: patternForm.name, patternType: patternForm.patternType,
      shiftLengthHours: patternForm.shiftLengthHours,
      cycleDefinition: patternForm.patternType === "CUSTOM" ? JSON.parse(patternForm.customJson || "{}") : patternForm.cycleDefinition,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["rotation-patterns"] }); setShowPatternForm(null); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create pattern"),
  })
  const updatePattern = useMutation({
    mutationFn: (id: string) => apiClient.put(`/api/v1/security/rotations/${id}`, {
      name: patternForm.name, shiftLengthHours: patternForm.shiftLengthHours,
      cycleDefinition: patternForm.patternType === "CUSTOM" ? JSON.parse(patternForm.customJson || "{}") : patternForm.cycleDefinition,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["rotation-patterns"] }); setShowPatternForm(null); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to update pattern"),
  })
  const deactivatePattern = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/security/rotations/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["rotation-patterns"] }),
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to deactivate pattern"),
  })

  const openNew = () => { setPatternForm({ siteId: "", name: "", patternType: "FIXED_DAYS_ON_OFF", shiftLengthHours: 12, cycleDefinition: emptyCycleDef("FIXED_DAYS_ON_OFF"), customJson: "{}" }); setShowPatternForm("new"); setError("") }
  const openEdit = (p: RotationPattern) => {
    setPatternForm({
      siteId: p.siteId, name: p.name, patternType: p.patternType, shiftLengthHours: p.shiftLengthHours,
      cycleDefinition: p.patternType === "CUSTOM" ? {} : p.cycleDefinition,
      customJson: p.patternType === "CUSTOM" ? JSON.stringify(p.cycleDefinition, null, 2) : "{}",
    })
    setShowPatternForm(p); setError("")
  }

  const [expandedPattern, setExpandedPattern] = useState<string | null>(null)
  const assignmentsQuery = useQuery<RotationAssignment[]>({
    queryKey: ["rotation-assignments", expandedPattern],
    queryFn: async () => (await apiClient.get(`/api/v1/security/rotations/${expandedPattern}/assignments`)).data?.data ?? [],
    enabled: !!expandedPattern,
  })

  const [assignFor, setAssignFor] = useState<RotationPattern | null>(null)
  const [assignForm, setAssignForm] = useState({ guardId: "", startsAt: todayStr(), positionInCycle: 0 })
  const assignGuard = useMutation({
    mutationFn: () => apiClient.post("/api/v1/security/rotations/assignments", { ...assignForm, patternId: assignFor!.id }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rotation-patterns"] })
      qc.invalidateQueries({ queryKey: ["rotation-assignments", assignFor?.id] })
      setAssignFor(null); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to assign guard"),
  })
  const endAssignment = useMutation({
    mutationFn: ({ id, endsAt }: { id: string; endsAt: string }) =>
      apiClient.delete(`/api/v1/security/rotations/assignments/${id}?endsAt=${endsAt}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rotation-patterns"] })
      qc.invalidateQueries({ queryKey: ["rotation-assignments", expandedPattern] })
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to end assignment"),
  })

  // ── Generate Schedule ────────────────────────────────────────────────────────

  const [genPatternId, setGenPatternId] = useState("")
  const [genFrom, setGenFrom] = useState(todayStr())
  const [genTo, setGenTo] = useState(() => { const d = new Date(); d.setDate(d.getDate() + 14); return d.toISOString().slice(0, 10) })
  const [genResult, setGenResult] = useState<GenerateScheduleResult | null>(null)
  const generateSchedule = useMutation({
    mutationFn: () => apiClient.post("/api/v1/security/rotations/generate", { patternId: genPatternId, fromDate: genFrom, toDate: genTo }),
    onSuccess: (res) => { setGenResult(res.data?.data ?? res.data); setError("") },
    onError: (e: any) => { setError(e.response?.data?.message ?? "Failed to generate schedule"); setGenResult(null) },
  })

  // ── Render ───────────────────────────────────────────────────────────────────

  return (
    <div>
      <div style={{ marginBottom: 18 }}>
        <h2 style={{ margin: 0, fontSize: 20, fontWeight: 800, color: "var(--hf-text)" }}>Rotation Patterns</h2>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 2 }}>Recurring roster patterns and bulk shift generation</div>
      </div>

      <div style={{ display: "flex", gap: 6, marginBottom: 16, borderBottom: "1px solid var(--hf-border)" }}>
        {SUB_TABS.map(t => {
          const Icon = t.icon; const active = subTab === t.id
          return (
            <button key={t.id} onClick={() => setSubTab(t.id)}
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "10px 14px", background: "none", border: "none", borderBottom: active ? "2px solid var(--hf-primary)" : "2px solid transparent", color: active ? "var(--hf-primary-text)" : "var(--hf-text-muted)", fontWeight: active ? 700 : 500, fontSize: 13, cursor: "pointer" }}>
              <Icon size={14} /> {t.label}
            </button>
          )
        })}
      </div>

      {error && <div style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 14 }}>{error}</div>}

      {/* ── Patterns ── */}
      {subTab === "patterns" && (
        <div>
          <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
            <button onClick={openNew} style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
              <Plus size={14} /> New Pattern
            </button>
          </div>
          {patternsLoading ? (
            <div style={{ textAlign: "center", padding: 30, color: "var(--hf-text-faint)" }}>Loading…</div>
          ) : patterns.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", color: "var(--hf-text-faint)", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
              No rotation patterns configured yet.
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {patterns.map(p => (
                <div key={p.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "14px 16px", opacity: p.active ? 1 : 0.6 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{p.name}</span>
                        <span style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-primary-text)", background: "var(--hf-info-soft)", padding: "2px 8px", borderRadius: 4 }}>{p.patternType.replace(/_/g, " ")}</span>
                      </div>
                      <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{p.siteName} · {p.shiftLengthHours}h shifts · {p.assignedGuardCount} guard{p.assignedGuardCount !== 1 ? "s" : ""} assigned</div>
                    </div>
                    <div style={{ display: "flex", gap: 8 }}>
                      <button onClick={() => setExpandedPattern(expandedPattern === p.id ? null : p.id)}
                        style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        <Users2 size={12} /> Guards
                      </button>
                      <button onClick={() => { setAssignForm({ guardId: "", startsAt: todayStr(), positionInCycle: 0 }); setAssignFor(p) }}
                        style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid var(--hf-primary)", background: "var(--hf-info-soft)", color: "var(--hf-primary-text)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        <Plus size={12} /> Assign
                      </button>
                      <button onClick={() => openEdit(p)}
                        style={{ padding: "6px 10px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        Edit
                      </button>
                      {p.active && (
                        <button onClick={() => deactivatePattern.mutate(p.id)} title="Deactivate"
                          style={{ padding: "6px 8px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, cursor: "pointer", color: "var(--hf-danger-text)" }}>
                          <Ban size={13} />
                        </button>
                      )}
                    </div>
                  </div>

                  {expandedPattern === p.id && (
                    <div style={{ marginTop: 12, paddingTop: 12, borderTop: "1px solid var(--hf-border)" }}>
                      {assignmentsQuery.isLoading ? (
                        <p style={{ color: "var(--hf-text-faint)", fontSize: 12, margin: 0 }}>Loading…</p>
                      ) : !assignmentsQuery.data?.length ? (
                        <p style={{ color: "var(--hf-text-faint)", fontSize: 12, margin: 0 }}>No guards currently assigned to this pattern.</p>
                      ) : (
                        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                          {assignmentsQuery.data.map(a => (
                            <div key={a.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "8px 12px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 7, fontSize: 12 }}>
                              <span><strong style={{ color: "var(--hf-text)" }}>{a.guardName}</strong> <span style={{ color: "var(--hf-text-faint)" }}>since {a.startsAt} · position {a.positionInCycle}</span></span>
                              <button onClick={() => { const d = prompt("End this assignment on (YYYY-MM-DD)?", todayStr()); if (d) endAssignment.mutate({ id: a.id, endsAt: d }) }}
                                style={{ display: "flex", alignItems: "center", gap: 4, padding: "4px 10px", borderRadius: 6, border: "1px solid var(--hf-danger-border)", background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                                <UserMinus size={11} /> End
                              </button>
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
        </div>
      )}

      {/* ── Generate Schedule ── */}
      {subTab === "generate" && (
        <div style={{ maxWidth: 480 }}>
          <div style={{ marginBottom: 14 }}>
            <label style={lbl}>Pattern</label>
            <select value={genPatternId} onChange={e => { setGenPatternId(e.target.value); setGenResult(null) }} style={inp}>
              <option value="">Select a pattern…</option>
              {patterns.filter(p => p.active).map(p => <option key={p.id} value={p.id}>{p.name} ({p.siteName})</option>)}
            </select>
          </div>
          <div style={{ display: "flex", gap: 12, marginBottom: 18 }}>
            <div style={{ flex: 1 }}>
              <label style={lbl}>From</label>
              <input type="date" value={genFrom} onChange={e => setGenFrom(e.target.value)} style={inp} />
            </div>
            <div style={{ flex: 1 }}>
              <label style={lbl}>To</label>
              <input type="date" value={genTo} onChange={e => setGenTo(e.target.value)} style={inp} />
            </div>
          </div>
          <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginBottom: 18 }}>Maximum 90-day window. Idempotent — existing shifts are skipped, safe to re-run.</div>
          <button onClick={() => generateSchedule.mutate()} disabled={!genPatternId || generateSchedule.isPending}
            style={{ display: "flex", alignItems: "center", gap: 6, padding: "10px 20px", background: genPatternId ? "var(--hf-primary)" : "var(--hf-surface-sunken)", color: genPatternId ? "var(--hf-text-on-solid)" : "var(--hf-text-faint)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: genPatternId ? "pointer" : "not-allowed" }}>
            <CalendarRange size={15} /> {generateSchedule.isPending ? "Generating…" : "Generate Schedule"}
          </button>

          {genResult && (
            <div style={{ marginTop: 24, background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 18 }}>
              <div style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)", marginBottom: 12 }}>{genResult.patternName} — {genResult.fromDate} to {genResult.toDate}</div>
              <div style={{ display: "flex", gap: 20, marginBottom: genResult.warnings.length ? 14 : 0 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                  <CheckCircle2 size={15} color="#166534" />
                  <span style={{ fontSize: 13 }}><strong>{genResult.shiftsCreated}</strong> shifts created</span>
                </div>
                {genResult.shiftsSkipped > 0 && (
                  <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                    <AlertTriangle size={15} color="#B45309" />
                    <span style={{ fontSize: 13 }}><strong>{genResult.shiftsSkipped}</strong> skipped</span>
                  </div>
                )}
              </div>
              {genResult.warnings.length > 0 && (
                <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                  {genResult.warnings.map((w, i) => (
                    <div key={i} style={{ fontSize: 12, color: "var(--hf-warning-text-strong)", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 6, padding: "6px 10px" }}>{w}</div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* New/Edit Pattern modal */}
      {showPatternForm && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, padding: 20 }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 14, padding: 26, width: 460, maxHeight: "85vh", overflowY: "auto" as const }}>
            <h3 style={{ margin: "0 0 18px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>{showPatternForm === "new" ? "New Rotation Pattern" : "Edit Pattern"}</h3>
            {showPatternForm === "new" && (
              <div style={{ marginBottom: 14 }}>
                <label style={lbl}>Site *</label>
                <select value={patternForm.siteId} onChange={e => setPatternForm(f => ({ ...f, siteId: e.target.value }))} style={inp}>
                  <option value="">Select a site…</option>
                  {sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>
            )}
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Name *</label>
              <input value={patternForm.name} onChange={e => setPatternForm(f => ({ ...f, name: e.target.value }))} placeholder="4-on 2-off Day Shift" style={inp} />
            </div>
            {showPatternForm === "new" && (
              <div style={{ marginBottom: 14 }}>
                <label style={lbl}>Pattern Type</label>
                <select value={patternForm.patternType} onChange={e => setPatternForm(f => ({ ...f, patternType: e.target.value, cycleDefinition: emptyCycleDef(e.target.value) }))} style={inp}>
                  <option value="FIXED_DAYS_ON_OFF">Fixed Days On/Off</option>
                  <option value="WEEKLY_FIXED">Weekly Fixed</option>
                  <option value="ALTERNATING_DAY_NIGHT">Alternating Day/Night</option>
                  <option value="CUSTOM">Custom</option>
                </select>
              </div>
            )}
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Shift Length (hours)</label>
              <input type="number" min={4} max={24} value={patternForm.shiftLengthHours} onChange={e => setPatternForm(f => ({ ...f, shiftLengthHours: Number(e.target.value) }))} style={inp} />
            </div>

            {patternForm.patternType === "FIXED_DAYS_ON_OFF" && (
              <div style={{ display: "flex", gap: 12, marginBottom: 14 }}>
                <div style={{ flex: 1 }}>
                  <label style={lbl}>Days On</label>
                  <input type="number" min={1} value={patternForm.cycleDefinition.onDays ?? 4} onChange={e => setPatternForm(f => ({ ...f, cycleDefinition: { ...f.cycleDefinition, onDays: Number(e.target.value) } }))} style={inp} />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={lbl}>Days Off</label>
                  <input type="number" min={1} value={patternForm.cycleDefinition.offDays ?? 2} onChange={e => setPatternForm(f => ({ ...f, cycleDefinition: { ...f.cycleDefinition, offDays: Number(e.target.value) } }))} style={inp} />
                </div>
              </div>
            )}

            {patternForm.patternType === "WEEKLY_FIXED" && (
              <div style={{ marginBottom: 14 }}>
                <label style={lbl}>Weekly Schedule</label>
                <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                  {DAYS.map(d => (
                    <div key={d} style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      <span style={{ fontSize: 12, color: "var(--hf-text-muted)", width: 80, textTransform: "capitalize" as const }}>{d}</span>
                      <select value={patternForm.cycleDefinition[d] ?? "OFF"} onChange={e => setPatternForm(f => ({ ...f, cycleDefinition: { ...f.cycleDefinition, [d]: e.target.value } }))} style={{ ...inp, flex: 1 }}>
                        <option value="OFF">Off</option>
                        <option value="DAY">Day</option>
                        <option value="NIGHT">Night</option>
                      </select>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {patternForm.patternType === "CUSTOM" && (
              <div style={{ marginBottom: 14 }}>
                <label style={lbl}>Cycle Definition (raw JSON)</label>
                <textarea value={patternForm.customJson} onChange={e => setPatternForm(f => ({ ...f, customJson: e.target.value }))} rows={5}
                  style={{ ...inp, fontFamily: "monospace", fontSize: 12, resize: "vertical" as const }} />
                <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 4 }}>Not validated server-side for CUSTOM patterns — caller-interpreted.</div>
              </div>
            )}

            {(patternForm.patternType === "FIXED_DAYS_ON_OFF" || patternForm.patternType === "WEEKLY_FIXED" || patternForm.patternType === "ALTERNATING_DAY_NIGHT") && (
              <div style={{ marginBottom: 18 }}>
                <label style={lbl}>Start Hour (optional, default 06:00 SAST)</label>
                <input type="number" min={0} max={23} value={patternForm.cycleDefinition.startHour ?? ""} onChange={e => setPatternForm(f => ({ ...f, cycleDefinition: { ...f.cycleDefinition, startHour: e.target.value ? Number(e.target.value) : undefined } }))} style={inp} />
              </div>
            )}

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowPatternForm(null)} style={cancelBtn}>Cancel</button>
              <button
                onClick={() => showPatternForm === "new" ? createPattern.mutate() : updatePattern.mutate(showPatternForm.id)}
                disabled={!patternForm.name.trim() || (showPatternForm === "new" && !patternForm.siteId) || createPattern.isPending || updatePattern.isPending}
                style={submitBtn}>
                {createPattern.isPending || updatePattern.isPending ? "Saving…" : showPatternForm === "new" ? "Create" : "Save Changes"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Assign guard modal */}
      {assignFor && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 14, padding: 26, width: 420 }}>
            <h3 style={{ margin: "0 0 18px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Assign Guard — {assignFor.name}</h3>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Guard *</label>
              <select value={assignForm.guardId} onChange={e => setAssignForm(f => ({ ...f, guardId: e.target.value }))} style={inp}>
                <option value="">Select guard…</option>
                {guards.map(g => <option key={g.id} value={g.id}>{g.fullName}</option>)}
              </select>
            </div>
            <div style={{ display: "flex", gap: 12, marginBottom: 18 }}>
              <div style={{ flex: 1 }}>
                <label style={lbl}>Starts</label>
                <input type="date" value={assignForm.startsAt} onChange={e => setAssignForm(f => ({ ...f, startsAt: e.target.value }))} style={inp} />
              </div>
              <div style={{ flex: 1 }}>
                <label style={lbl}>Position in Cycle</label>
                <input type="number" min={0} value={assignForm.positionInCycle} onChange={e => setAssignForm(f => ({ ...f, positionInCycle: Number(e.target.value) }))} style={inp} />
              </div>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setAssignFor(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => assignGuard.mutate()} disabled={!assignForm.guardId || assignGuard.isPending} style={submitBtn}>
                {assignGuard.isPending ? "Assigning…" : "Assign"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
