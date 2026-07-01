// src/pages/security/ControlRoomTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Siren, Send, CheckCircle, AlertTriangle, XCircle,
  MapPin, Clock, ChevronRight, RefreshCw,
} from "lucide-react"

// ── Types ──────────────────────────────────────────────────────────────────────

interface AlarmEvent {
  id: string
  siteId: string | null
  source: string
  severity: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
  status: "NEW" | "TRIAGED" | "DISPATCHED" | "RESOLVED" | "FALSE_ALARM"
  description: string | null
  triggeredByGuardId: string | null
  triagedAt: string | null
  createdAt: string
}

interface Dispatch {
  id: string
  alarmEventId: string
  dispatchedUnitType: string
  dispatchedGuardId: string | null
  dispatchedGuardName: string | null
  dispatchedAt: string
  arrivedAt: string | null
  resolvedAt: string | null
  responseTimeMinutes: number | null
  resolutionTimeMinutes: number | null
  outcome: string | null
  open: boolean
}

// ── Config ─────────────────────────────────────────────────────────────────────

const SEV_CONFIG = {
  LOW:      { color: "#0369A1", bg: "#E0F2FE", dot: "#0EA5E9" },
  MEDIUM:   { color: "#92400E", bg: "#FEF3C7", dot: "#F59E0B" },
  HIGH:     { color: "#C2410C", bg: "#FFF7ED", dot: "#F97316" },
  CRITICAL: { color: "#991B1B", bg: "#FEF2F2", dot: "#DC2626" },
}

const STATUS_CONFIG = {
  NEW:        { label: "New",         color: "#1D4ED8", bg: "#EFF6FF" },
  TRIAGED:    { label: "Triaged",     color: "#92400E", bg: "#FEF3C7" },
  DISPATCHED: { label: "Dispatched",  color: "#166534", bg: "#DCFCE7" },
  RESOLVED:   { label: "Resolved",    color: "#64748B", bg: "#F1F5F9" },
  FALSE_ALARM:{ label: "False Alarm", color: "#94A3B8", bg: "#F8FAFC" },
}

const fmtTime = (iso: string) =>
  new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })
const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short" })

// ── Sub-components ─────────────────────────────────────────────────────────────

function SevDot({ sev }: { sev: string }) {
  const c = SEV_CONFIG[sev as keyof typeof SEV_CONFIG] ?? SEV_CONFIG.MEDIUM
  return (
    <span style={{
      display: "inline-block", width: 8, height: 8,
      borderRadius: "50%", background: c.dot, marginRight: 6, flexShrink: 0,
    }} />
  )
}

function StatusBadge({ status }: { status: string }) {
  const c = STATUS_CONFIG[status as keyof typeof STATUS_CONFIG] ?? STATUS_CONFIG.NEW
  return (
    <span style={{
      fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 4,
      color: c.color, background: c.bg, letterSpacing: "0.03em",
    }}>
      {c.label}
    </span>
  )
}

// ── Main Component ─────────────────────────────────────────────────────────────

export default function ControlRoomTab() {
  const qc = useQueryClient()
  const [selected, setSelected]       = useState<AlarmEvent | null>(null)
  const [dispatchForm, setDispatchForm] = useState({ unitType: "ARMED_RESPONSE", guardId: "" })
  const [resolveForm, setResolveForm]   = useState({ outcome: "RESOLVED", notes: "" })
  const [showDispatch, setShowDispatch] = useState(false)
  const [showResolve,  setShowResolve]  = useState<string | null>(null)
  const [apiError, setApiError]         = useState("")

  // Queries
  const { data: events = [], isLoading, refetch } = useQuery<AlarmEvent[]>({
    queryKey: ["alarm-events"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/alarm-events?size=50")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as AlarmEvent[]
    },
    refetchInterval: 15000, // auto-refresh every 15s
  })

  const { data: openDispatches = [] } = useQuery<Dispatch[]>({
    queryKey: ["dispatches-open"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/dispatches/open")
      return (r.data?.data ?? r.data) as Dispatch[]
    },
    refetchInterval: 15000,
  })

  const { data: guards = [] } = useQuery<{ id: string; fullName: string }[]>({
    queryKey: ["guards-list"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/guards?size=100")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as any[]
    },
  })

  // Mutations
  const triage = useMutation({
    mutationFn: (id: string) =>
      apiClient.post(`/api/v1/security/alarm-events/${id}/triage`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["alarm-events"] }),
  })

  const markFalse = useMutation({
    mutationFn: (id: string) =>
      apiClient.post(`/api/v1/security/alarm-events/${id}/false-alarm`, {}),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["alarm-events"] })
      setSelected(null)
    },
  })

  const dispatch = useMutation({
    mutationFn: ({ eventId, body }: any) =>
      apiClient.post(`/api/v1/security/alarm-events/${eventId}/dispatch`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["alarm-events"] })
      qc.invalidateQueries({ queryKey: ["dispatches-open"] })
      setShowDispatch(false)
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Dispatch failed"),
  })

  const arrive = useMutation({
    mutationFn: (id: string) =>
      apiClient.post(`/api/v1/security/dispatches/${id}/arrive`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["dispatches-open"] }),
  })

  const resolve = useMutation({
    mutationFn: ({ id, body }: any) =>
      apiClient.patch(`/api/v1/security/dispatches/${id}/resolve`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["alarm-events"] })
      qc.invalidateQueries({ queryKey: ["dispatches-open"] })
      setShowResolve(null)
    },
  })

  const openEvents = events.filter(e => ["NEW","TRIAGED","DISPATCHED"].includes(e.status))

  return (
    <div>
      {/* Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "#0F172A" }}>Control Room</h2>
          <p style={{ margin: "2px 0 0", fontSize: 12, color: "#64748B" }}>
            {openEvents.length} open event{openEvents.length !== 1 ? "s" : ""} · {openDispatches.length} active dispatch{openDispatches.length !== 1 ? "es" : ""}
          </p>
        </div>
        <button onClick={() => refetch()} style={{ display: "flex", alignItems: "center", gap: 6, padding: "7px 14px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", color: "#64748B", fontSize: 12, cursor: "pointer" }}>
          <RefreshCw size={13} /> Refresh
        </button>
      </div>

      {/* Active dispatches strip */}
      {openDispatches.length > 0 && (
        <div style={{ background: "#FEF3C7", border: "1px solid #FDE68A", borderRadius: 10, padding: "12px 16px", marginBottom: 20 }}>
          <p style={{ margin: "0 0 8px", fontSize: 11, fontWeight: 700, color: "#92400E", letterSpacing: "0.06em", textTransform: "uppercase" }}>
            Active Dispatches
          </p>
          {openDispatches.map(d => (
            <div key={d.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "6px 0", borderTop: "1px solid #FDE68A" }}>
              <div style={{ fontSize: 12, color: "#78350F" }}>
                <strong>{d.dispatchedUnitType.replace("_", " ")}</strong>
                {d.dispatchedGuardName && ` — ${d.dispatchedGuardName}`}
                {" · "}<Clock size={10} style={{ verticalAlign: "middle" }} /> {fmtTime(d.dispatchedAt)}
                {d.arrivedAt && <span style={{ color: "#166534" }}> · On scene {fmtTime(d.arrivedAt)}</span>}
              </div>
              <div style={{ display: "flex", gap: 6 }}>
                {!d.arrivedAt && (
                  <button onClick={() => arrive.mutate(d.id)} style={{ fontSize: 11, padding: "4px 10px", borderRadius: 6, border: "1px solid #D97706", background: "#FFFBEB", color: "#92400E", cursor: "pointer" }}>
                    On Scene
                  </button>
                )}
                <button onClick={() => setShowResolve(d.id)} style={{ fontSize: 11, padding: "4px 10px", borderRadius: 6, border: "1px solid #16A34A", background: "#F0FDF4", color: "#166534", cursor: "pointer" }}>
                  Resolve
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Two-column layout: event list + detail panel */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 380px", gap: 20 }}>
        {/* Event list */}
        <div>
          {isLoading ? (
            <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading events…</p>
          ) : events.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 0", color: "#CBD5E1" }}>
              <Siren size={32} strokeWidth={1.5} style={{ margin: "0 auto 8px", display: "block" }} />
              <p style={{ margin: 0, fontWeight: 500 }}>No alarm events yet</p>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {events.map(e => (
                <button
                  key={e.id}
                  onClick={() => setSelected(e)}
                  style={{
                    display: "flex", alignItems: "center", gap: 12,
                    padding: "12px 16px", border: `1px solid ${selected?.id === e.id ? "#0D9488" : "#E2E8F0"}`,
                    borderRadius: 10, background: selected?.id === e.id ? "#F0FDFA" : "#fff",
                    cursor: "pointer", textAlign: "left", width: "100%",
                    transition: "border-color 0.15s, background 0.15s",
                  }}
                >
                  <SevDot sev={e.severity} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 2 }}>
                      <span style={{ fontSize: 12, fontWeight: 600, color: "#0F172A" }}>
                        {e.source.replace(/_/g, " ")}
                      </span>
                      <StatusBadge status={e.status} />
                    </div>
                    <p style={{ margin: 0, fontSize: 11, color: "#64748B", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {e.description ?? "No description"} · {fmtDate(e.createdAt)} {fmtTime(e.createdAt)}
                    </p>
                  </div>
                  <ChevronRight size={14} color="#CBD5E1" />
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Detail panel */}
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, padding: 20, alignSelf: "start", background: "#FAFAFA" }}>
          {!selected ? (
            <div style={{ textAlign: "center", padding: "40px 0", color: "#CBD5E1" }}>
              <AlertTriangle size={28} strokeWidth={1.5} style={{ display: "block", margin: "0 auto 8px" }} />
              <p style={{ margin: 0, fontSize: 12 }}>Select an event to action</p>
            </div>
          ) : (
            <div>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 16 }}>
                <span style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>
                  {selected.source.replace(/_/g, " ")}
                </span>
                <StatusBadge status={selected.status} />
              </div>

              <div style={{ fontSize: 11, color: "#64748B", marginBottom: 14, display: "flex", flexDirection: "column", gap: 4 }}>
                <span><strong>Severity:</strong> {selected.severity}</span>
                <span><Clock size={10} style={{ verticalAlign: "middle" }} /> {fmtDate(selected.createdAt)} {fmtTime(selected.createdAt)}</span>
                {selected.description && <span>{selected.description}</span>}
              </div>

              {/* Actions */}
              {selected.status === "NEW" && (
                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                  <button onClick={() => triage.mutate(selected.id)} style={btnStyle("#0D9488")}>
                    <CheckCircle size={13} /> Triage Event
                  </button>
                  <button onClick={() => markFalse.mutate(selected.id)} style={btnStyle("#94A3B8")}>
                    <XCircle size={13} /> Mark False Alarm
                  </button>
                </div>
              )}
              {(selected.status === "TRIAGED" || selected.status === "NEW") && !showDispatch && (
                <button onClick={() => setShowDispatch(true)} style={{ ...btnStyle("#1D4ED8"), marginTop: 8 }}>
                  <Send size={13} /> Dispatch Response
                </button>
              )}
              {showDispatch && (
                <div style={{ marginTop: 12, display: "flex", flexDirection: "column", gap: 8 }}>
                  {apiError && <p style={{ margin: 0, fontSize: 11, color: "#DC2626" }}>{apiError}</p>}
                  <select value={dispatchForm.unitType} onChange={e => setDispatchForm(f => ({ ...f, unitType: e.target.value }))}
                    style={inputStyle}>
                    <option value="ARMED_RESPONSE">Armed Response</option>
                    <option value="GUARD">Guard</option>
                    <option value="POLICE">Police</option>
                    <option value="OTHER">Other</option>
                  </select>
                  <select value={dispatchForm.guardId} onChange={e => setDispatchForm(f => ({ ...f, guardId: e.target.value }))}
                    style={inputStyle}>
                    <option value="">No specific guard</option>
                    {guards.map((g: any) => <option key={g.id} value={g.id}>{g.fullName}</option>)}
                  </select>
                  <div style={{ display: "flex", gap: 8 }}>
                    <button onClick={() => dispatch.mutate({ eventId: selected.id, body: { dispatchedUnitType: dispatchForm.unitType, dispatchedGuardId: dispatchForm.guardId || null } })}
                      style={btnStyle("#1D4ED8")}>
                      <Send size={12} /> Send
                    </button>
                    <button onClick={() => setShowDispatch(false)} style={btnStyle("#94A3B8")}>
                      Cancel
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Resolve modal */}
      {showResolve && (
        <div style={modalOverlay}>
          <div style={modalBox}>
            <h3 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 700 }}>Resolve Dispatch</h3>
            <select value={resolveForm.outcome} onChange={e => setResolveForm(f => ({ ...f, outcome: e.target.value }))}
              style={{ ...inputStyle, marginBottom: 10 }}>
              <option value="RESOLVED">Resolved</option>
              <option value="FALSE_ALARM">False Alarm</option>
              <option value="ESCALATED">Escalated</option>
              <option value="NO_ACTION_NEEDED">No Action Needed</option>
            </select>
            <textarea value={resolveForm.notes} onChange={e => setResolveForm(f => ({ ...f, notes: e.target.value }))}
              placeholder="Resolution notes…"
              style={{ ...inputStyle, height: 80, resize: "vertical" as const, marginBottom: 12 }} />
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
              <button onClick={() => setShowResolve(null)} style={btnStyle("#94A3B8")}>Cancel</button>
              <button onClick={() => resolve.mutate({ id: showResolve, body: resolveForm })}
                style={btnStyle("#0D9488")}>
                <CheckCircle size={13} /> Confirm
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const btnStyle = (color: string) => ({
  display: "flex", alignItems: "center", gap: 6, justifyContent: "center" as const,
  width: "100%", padding: "9px 14px", borderRadius: 8,
  border: `1px solid ${color}`, background: color, color: "#fff",
  fontSize: 12, fontWeight: 600, cursor: "pointer",
} as const)

const inputStyle = {
  width: "100%", padding: "8px 10px", border: "1px solid #E2E8F0",
  borderRadius: 8, fontSize: 12, background: "#fff", boxSizing: "border-box" as const,
} as const

const modalOverlay = {
  position: "fixed" as const, inset: 0, background: "rgba(0,0,0,0.4)",
  display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000,
} as const

const modalBox = {
  background: "#fff", borderRadius: 14, padding: 24, width: 420,
  boxShadow: "0 20px 60px rgba(0,0,0,0.2)",
} as const
