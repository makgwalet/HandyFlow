// src/pages/security/PatrolRoutesTab.tsx
//
// FIX (P1 backlog): confirmed via direct source read before building
// anything — PatrolRouteController exists with exactly 4 endpoints
// (list/create routes, add checkpoint, list rounds for a shift) and had
// zero frontend surface at all. The controller's own class doc comment
// notes its permission gaps were "finally addressed" in a prior fix —
// confirmed genuinely true (SECURITY_READ/SECURITY_MANAGE correctly
// applied to all 4), so no backend permission work needed here.
//
// Scope note: PatrolRound has acknowledgedBy/acknowledgementNote columns
// for an off-schedule-round acknowledgement workflow, but confirmed via
// direct read of PatrolRoundService that no method anywhere ever sets
// them — there is no backend endpoint to acknowledge a round. Displaying
// these fields read-only where already present, not building an
// "Acknowledge" button that would call an endpoint that doesn't exist.
// Also: routes have create + add-checkpoint only, no update/deactivate
// endpoint — this UI doesn't offer editing a route for the same reason.

import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Route, Plus, MapPin, Radio, AlertTriangle, CheckCircle2, Clock3 } from "lucide-react"
import { useMutation, useQueryClient } from "@tanstack/react-query"

// ── Types ──────────────────────────────────────────────────────────────────────

interface Site { id: string; name: string }
interface Checkpoint { id: string; name: string; description: string | null; qrCode: string; sortOrder: number }
interface SiteDetail extends Site { checkpoints: Checkpoint[] }

interface RouteCheckpoint { id: string; routeId: string; checkpointId: string; sequence: number; expectedMinutesAfterRouteStart: number | null }
interface PatrolRoute {
  id: string; siteId: string; name: string
  intervalMinutes: number; toleranceMinutes: number; active: boolean
  checkpoints: RouteCheckpoint[]
  createdAt: string
}

interface PatrolRound {
  id: string; siteId: string; shiftId: string; routeId: string | null
  roundNumber: number
  expectedStartAt: string | null; expectedEndAt: string | null
  startedAt: string | null; completedAt: string | null
  status: "EXPECTED" | "IN_PROGRESS" | "COMPLETE" | "PARTIAL" | "MISSED"
  scansExpected: number; scansCompleted: number
  offSchedule: boolean; offScheduleReason: string | null
  acknowledgedBy: string | null; acknowledgementNote: string | null
  createdAt: string
}

interface Shift { id: string; siteId: string; guardId: string; startAt: string; endAt: string; status: string }
interface Guard { id: string; fullName: string }

// ── Config ─────────────────────────────────────────────────────────────────────

const ROUND_STATUS_CFG: Record<string, { label: string; color: string; bg: string; border: string; icon: any }> = {
  EXPECTED:    { label: "Expected",    color: "#64748B", bg: "#F8FAFC", border: "#E2E8F0", icon: Clock3 },
  IN_PROGRESS: { label: "In Progress", color: "#B45309", bg: "#FFFBEB", border: "#FDE68A", icon: Radio },
  COMPLETE:    { label: "Complete",    color: "#166534", bg: "#DCFCE7", border: "#86EFAC", icon: CheckCircle2 },
  PARTIAL:     { label: "Partial",     color: "#C2410C", bg: "#FFEDD5", border: "#FDBA74", icon: AlertTriangle },
  MISSED:      { label: "Missed",      color: "#DC2626", bg: "#FEF2F2", border: "#FECACA", icon: AlertTriangle },
}

const SUB_TABS = [
  { id: "routes",     label: "Routes",          icon: Route },
  { id: "monitoring", label: "Live Monitoring", icon: Radio },
] as const

const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff", outline: "none" }
const cancelBtn: React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const submitBtn: React.CSSProperties = { padding: "9px 18px", border: "none", borderRadius: 9, background: "#1B3A6B", color: "#fff", fontSize: 14, fontWeight: 600, cursor: "pointer" }

function fmtTime(s: string | null) { return s ? new Date(s).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" }) : "—" }

// ── Main component ─────────────────────────────────────────────────────────────

export default function PatrolRoutesTab() {
  const qc = useQueryClient()
  const [subTab, setSubTab] = useState<typeof SUB_TABS[number]["id"]>("routes")
  const [siteId, setSiteId] = useState("")
  const [error, setError] = useState("")

  const { data: sites = [] } = useQuery<Site[]>({
    queryKey: ["sites-list"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/sites?size=100")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as Site[]
    },
  })

  const { data: siteDetail } = useQuery<SiteDetail>({
    queryKey: ["site-detail", siteId],
    queryFn: async () => (await apiClient.get(`/api/v1/security/sites/${siteId}`)).data?.data,
    enabled: !!siteId,
  })
  const checkpointName = (id: string) => siteDetail?.checkpoints.find(c => c.id === id)?.name ?? id.slice(0, 8)

  // ── Routes ───────────────────────────────────────────────────────────────────

  const { data: routes = [], isLoading: routesLoading } = useQuery<PatrolRoute[]>({
    queryKey: ["patrol-routes", siteId],
    queryFn: async () => (await apiClient.get(`/api/v1/security/patrol-routes?siteId=${siteId}`)).data?.data ?? [],
    enabled: !!siteId && subTab === "routes",
  })

  const [showRouteForm, setShowRouteForm] = useState(false)
  const [routeForm, setRouteForm] = useState({ name: "", intervalMinutes: 120, toleranceMinutes: 20 })
  const createRoute = useMutation({
    mutationFn: () => apiClient.post("/api/v1/security/patrol-routes", { siteId, ...routeForm }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["patrol-routes", siteId] }); setShowRouteForm(false); setRouteForm({ name: "", intervalMinutes: 120, toleranceMinutes: 20 }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create route"),
  })

  const [addCheckpointFor, setAddCheckpointFor] = useState<PatrolRoute | null>(null)
  const [checkpointForm, setCheckpointForm] = useState({ checkpointId: "", sequence: 0 })
  const addCheckpoint = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/security/patrol-routes/${addCheckpointFor!.id}/checkpoints`, checkpointForm),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["patrol-routes", siteId] }); setAddCheckpointFor(null); setCheckpointForm({ checkpointId: "", sequence: 0 }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to add checkpoint"),
  })

  // ── Live Monitoring ──────────────────────────────────────────────────────────

  // FIX: getShifts has no site/status filter params at all (confirmed via
  // direct read of ShiftController) — same client-side filtering
  // ShiftsTab itself already does, not something invented for this tab.
  const { data: allShifts = [] } = useQuery<Shift[]>({
    queryKey: ["shifts-list-for-patrol"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/shifts?size=100")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as Shift[]
    },
    enabled: subTab === "monitoring",
  })
  const { data: guards = [] } = useQuery<Guard[]>({
    queryKey: ["guards-list-for-patrol"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/guards?size=200")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as Guard[]
    },
    enabled: subTab === "monitoring",
  })
  const guardName = (id: string) => guards.find(g => g.id === id)?.fullName ?? id.slice(0, 8)
  const activeShiftsAtSite = allShifts.filter(s => s.siteId === siteId && s.status === "ACTIVE")

  const [shiftId, setShiftId] = useState("")
  const { data: rounds = [], isLoading: roundsLoading } = useQuery<PatrolRound[]>({
    queryKey: ["patrol-rounds", shiftId],
    queryFn: async () => (await apiClient.get(`/api/v1/security/patrol-routes/rounds?shiftId=${shiftId}`)).data?.data ?? [],
    enabled: !!shiftId,
    refetchInterval: 30000, // matches GateAccessTab's on-site polling convention
  })

  // ── Render ───────────────────────────────────────────────────────────────────

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 18, flexWrap: "wrap", gap: 12 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 20, fontWeight: 800, color: "#0F172A" }}>Patrol Routes</h2>
          <div style={{ fontSize: 13, color: "#64748B", marginTop: 2 }}>Route configuration and live round-by-round monitoring</div>
        </div>
        <div style={{ minWidth: 220 }}>
          <select value={siteId} onChange={e => { setSiteId(e.target.value); setShiftId("") }} style={inp}>
            <option value="">Select a site…</option>
            {sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
      </div>

      {!siteId ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12 }}>
          <Route size={32} style={{ marginBottom: 10, opacity: 0.3 }} />
          <div>Select a site to configure patrol routes or monitor live rounds.</div>
        </div>
      ) : (
        <>
          <div style={{ display: "flex", gap: 6, marginBottom: 16, borderBottom: "1px solid #E2E8F0" }}>
            {SUB_TABS.map(t => {
              const Icon = t.icon; const active = subTab === t.id
              return (
                <button key={t.id} onClick={() => setSubTab(t.id)}
                  style={{ display: "flex", alignItems: "center", gap: 6, padding: "10px 14px", background: "none", border: "none", borderBottom: active ? "2px solid #1B3A6B" : "2px solid transparent", color: active ? "#1B3A6B" : "#64748B", fontWeight: active ? 700 : 500, fontSize: 13, cursor: "pointer" }}>
                  <Icon size={14} /> {t.label}
                </button>
              )
            })}
          </div>

          {error && <div style={{ padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13, marginBottom: 14 }}>{error}</div>}

          {/* ── Routes ── */}
          {subTab === "routes" && (
            <div>
              <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
                <button onClick={() => { setShowRouteForm(true); setError("") }} style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                  <Plus size={14} /> New Route
                </button>
              </div>
              {routesLoading ? (
                <div style={{ textAlign: "center", padding: 30, color: "#94A3B8" }}>Loading…</div>
              ) : routes.length === 0 ? (
                <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12 }}>
                  No patrol routes configured for this site yet.
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                  {routes.map(r => (
                    <div key={r.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10, padding: "14px 16px" }}>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
                        <div>
                          <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{r.name}</div>
                          <div style={{ fontSize: 12, color: "#94A3B8" }}>Every {r.intervalMinutes} min, ±{r.toleranceMinutes} min tolerance</div>
                        </div>
                        <button onClick={() => { setAddCheckpointFor(r); setCheckpointForm({ checkpointId: "", sequence: r.checkpoints.length }); setError("") }}
                          style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid #1B3A6B", background: "#EFF6FF", color: "#1B3A6B", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                          <Plus size={12} /> Add Checkpoint
                        </button>
                      </div>
                      {r.checkpoints.length === 0 ? (
                        <div style={{ fontSize: 12, color: "#CBD5E1" }}>No checkpoints added yet — this route has nothing to patrol.</div>
                      ) : (
                        <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                          {[...r.checkpoints].sort((a, b) => a.sequence - b.sequence).map(cp => (
                            <div key={cp.id} style={{ display: "flex", alignItems: "center", gap: 5, padding: "4px 10px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 20, fontSize: 12 }}>
                              <span style={{ fontWeight: 700, color: "#1B3A6B" }}>{cp.sequence + 1}.</span>
                              <MapPin size={11} style={{ color: "#94A3B8" }} />
                              {checkpointName(cp.checkpointId)}
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* ── Live Monitoring ── */}
          {subTab === "monitoring" && (
            <div>
              <div style={{ marginBottom: 16, maxWidth: 360 }}>
                <label style={lbl}>Active Shift</label>
                <select value={shiftId} onChange={e => setShiftId(e.target.value)} style={inp}>
                  <option value="">Select an active shift…</option>
                  {activeShiftsAtSite.map(s => (
                    <option key={s.id} value={s.id}>{guardName(s.guardId)} — started {fmtTime(s.startAt)}</option>
                  ))}
                </select>
                {activeShiftsAtSite.length === 0 && (
                  <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 6 }}>No active shifts at this site right now.</div>
                )}
              </div>

              {!shiftId ? (
                <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12 }}>
                  Select an active shift to see its round-by-round patrol status.
                </div>
              ) : roundsLoading ? (
                <div style={{ textAlign: "center", padding: 30, color: "#94A3B8" }}>Loading…</div>
              ) : rounds.length === 0 ? (
                <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12 }}>
                  No rounds generated for this shift yet.
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                  {rounds.map(r => {
                    const cfg = ROUND_STATUS_CFG[r.status] ?? ROUND_STATUS_CFG.EXPECTED
                    const Icon = cfg.icon
                    return (
                      <div key={r.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", background: "#fff", border: `1px solid ${r.offSchedule ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, padding: "12px 16px" }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                          <div style={{ width: 36, height: 36, borderRadius: 9, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                            <Icon size={16} color={cfg.color} />
                          </div>
                          <div>
                            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                              <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>Round {r.roundNumber}</span>
                              {r.offSchedule && (
                                <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: "#FEF2F2", color: "#DC2626" }}>OFF SCHEDULE</span>
                              )}
                            </div>
                            <div style={{ fontSize: 12, color: "#94A3B8" }}>
                              Expected {fmtTime(r.expectedStartAt)}–{fmtTime(r.expectedEndAt)}
                              {r.startedAt && <> · Started {fmtTime(r.startedAt)}</>}
                              {r.completedAt && <> · Completed {fmtTime(r.completedAt)}</>}
                            </div>
                            {r.offScheduleReason && <div style={{ fontSize: 11, color: "#C2410C", marginTop: 2 }}>{r.offScheduleReason}</div>}
                            {r.acknowledgementNote && <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>Acknowledged: {r.acknowledgementNote}</div>}
                          </div>
                        </div>
                        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                          <span style={{ fontSize: 12, color: "#64748B" }}>{r.scansCompleted}/{r.scansExpected} scanned</span>
                          <span style={{ fontSize: 11, fontWeight: 600, padding: "3px 10px", borderRadius: 20, background: cfg.bg, color: cfg.color, border: `1px solid ${cfg.border}` }}>{cfg.label}</span>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          )}
        </>
      )}

      {/* New Route modal */}
      {showRouteForm && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 26, width: 420 }}>
            <h3 style={{ margin: "0 0 18px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>New Patrol Route</h3>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Name *</label>
              <input value={routeForm.name} onChange={e => setRouteForm(f => ({ ...f, name: e.target.value }))} placeholder="Perimeter Round" style={inp} />
            </div>
            <div style={{ display: "flex", gap: 12, marginBottom: 18 }}>
              <div style={{ flex: 1 }}>
                <label style={lbl}>Interval (min)</label>
                <input type="number" min={15} max={480} value={routeForm.intervalMinutes} onChange={e => setRouteForm(f => ({ ...f, intervalMinutes: Number(e.target.value) }))} style={inp} />
              </div>
              <div style={{ flex: 1 }}>
                <label style={lbl}>Tolerance (min)</label>
                <input type="number" min={5} max={60} value={routeForm.toleranceMinutes} onChange={e => setRouteForm(f => ({ ...f, toleranceMinutes: Number(e.target.value) }))} style={inp} />
              </div>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowRouteForm(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createRoute.mutate()} disabled={!routeForm.name.trim() || createRoute.isPending} style={submitBtn}>
                {createRoute.isPending ? "Creating…" : "Create"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Checkpoint modal */}
      {addCheckpointFor && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 26, width: 420 }}>
            <h3 style={{ margin: "0 0 18px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>Add Checkpoint — {addCheckpointFor.name}</h3>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Checkpoint *</label>
              <select value={checkpointForm.checkpointId} onChange={e => setCheckpointForm(f => ({ ...f, checkpointId: e.target.value }))} style={inp}>
                <option value="">Select a checkpoint…</option>
                {(siteDetail?.checkpoints ?? []).map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
              {(siteDetail?.checkpoints ?? []).length === 0 && (
                <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 6 }}>This site has no checkpoints registered yet — add one from the Sites tab first.</div>
              )}
            </div>
            <div style={{ marginBottom: 18 }}>
              <label style={lbl}>Sequence</label>
              <input type="number" min={0} value={checkpointForm.sequence} onChange={e => setCheckpointForm(f => ({ ...f, sequence: Number(e.target.value) }))} style={inp} />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setAddCheckpointFor(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => addCheckpoint.mutate()} disabled={!checkpointForm.checkpointId || addCheckpoint.isPending} style={submitBtn}>
                {addCheckpoint.isPending ? "Adding…" : "Add"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
