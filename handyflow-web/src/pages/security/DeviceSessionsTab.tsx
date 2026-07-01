// src/pages/security/DeviceSessionsTab.tsx
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Tablet, Clock, LogOut, AlertTriangle } from "lucide-react"
import { useState } from "react"

interface DeviceSession {
  sessionId: string
  deviceId: string
  deviceName: string | null
  guardId: string
  guardName: string
  shiftId: string | null
  shiftSummary: string | null
  startedAt: string
  endedAt: string | null
  open: boolean
  durationMinutes: number | null
  handoverNotes: string | null
  forcedCloseReason: string | null
}

const fmtTime  = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })
const fmtDate  = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short" })
const fmtDur   = (min: number | null) => min == null ? "ongoing" : min < 60 ? `${min}m` : `${Math.floor(min/60)}h ${min%60}m`

export default function DeviceSessionsTab() {
  const qc = useQueryClient()
  const [forceClose, setForceClose] = useState<string | null>(null)
  const [reason, setReason]         = useState("")
  const [apiError, setApiError]     = useState("")

  const { data: allSessions = [], isLoading } = useQuery<DeviceSession[]>({
    queryKey: ["device-sessions"],
    queryFn: async () => {
      // Use the resolve-guard endpoint to get active sessions — or a general list
      // We'll query the sessions endpoint if it exists, otherwise fall back gracefully
      try {
        const r = await apiClient.get("/api/v1/security/sessions?size=100")
        const p = r.data?.data ?? r.data
        return (p?.content ?? p ?? []) as DeviceSession[]
      } catch {
        return []
      }
    },
    refetchInterval: 30000,
  })

  const forceCloseMut = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      apiClient.post(`/api/v1/security/sessions/${id}/force-close?reason=${encodeURIComponent(reason)}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["device-sessions"] })
      setForceClose(null)
      setReason("")
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Force close failed"),
  })

  const open   = allSessions.filter(s => s.open)
  const closed = allSessions.filter(s => !s.open).slice(0, 20) // last 20

  return (
    <div>
      <div style={{ marginBottom: 20 }}>
        <h2 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "#0F172A" }}>Device Sessions</h2>
        <p style={{ margin: "2px 0 0", fontSize: 12, color: "#64748B" }}>
          {open.length} active session{open.length !== 1 ? "s" : ""} — guards currently clocked in
        </p>
      </div>

      {/* Active sessions */}
      <div style={{ marginBottom: 28 }}>
        <p style={{ fontSize: 11, fontWeight: 700, color: "#374151", letterSpacing: "0.06em", textTransform: "uppercase" as const, marginBottom: 10 }}>
          Active
        </p>
        {isLoading ? (
          <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
        ) : open.length === 0 ? (
          <div style={{ padding: "24px 0", textAlign: "center", color: "#CBD5E1" }}>
            <Tablet size={28} strokeWidth={1.5} style={{ display: "block", margin: "0 auto 8px" }} />
            <p style={{ margin: 0, fontSize: 12 }}>No guards currently clocked in</p>
          </div>
        ) : (
          <div style={{ display: "grid", gap: 8 }}>
            {open.map(s => (
              <div key={s.sessionId} style={{ display: "flex", alignItems: "center", gap: 14, padding: "14px 16px", border: "1px solid #BFDBFE", borderRadius: 10, background: "#EFF6FF" }}>
                <div style={{ width: 36, height: 36, borderRadius: 8, background: "#2563EB", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                  <Tablet size={16} color="#fff" />
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A", marginBottom: 2 }}>
                    {s.guardName}
                  </div>
                  <div style={{ fontSize: 11, color: "#64748B" }}>
                    {s.deviceName ?? "Unknown device"} · Started {fmtDate(s.startedAt)} {fmtTime(s.startedAt)} · {fmtDur(s.durationMinutes)}
                    {s.shiftSummary && ` · ${s.shiftSummary}`}
                  </div>
                </div>
                <button
                  onClick={() => { setForceClose(s.sessionId); setReason(""); setApiError("") }}
                  style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid #FECACA", background: "#FEF2F2", color: "#991B1B", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                  <LogOut size={12} /> Force Close
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Recent closed */}
      {closed.length > 0 && (
        <div>
          <p style={{ fontSize: 11, fontWeight: 700, color: "#374151", letterSpacing: "0.06em", textTransform: "uppercase" as const, marginBottom: 10 }}>
            Recent (last 20)
          </p>
          <div style={{ display: "grid", gap: 6 }}>
            {closed.map(s => (
              <div key={s.sessionId} style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 16px", border: "1px solid #F1F5F9", borderRadius: 8, background: "#FAFAFA" }}>
                <Clock size={14} color="#CBD5E1" />
                <div style={{ flex: 1, fontSize: 12, color: "#374151" }}>
                  <strong>{s.guardName}</strong> · {fmtDate(s.startedAt)} {fmtTime(s.startedAt)} → {s.endedAt ? fmtTime(s.endedAt) : "?"} ({fmtDur(s.durationMinutes)})
                  {s.forcedCloseReason && (
                    <span style={{ marginLeft: 8, fontSize: 10, color: "#DC2626" }}>
                      <AlertTriangle size={9} style={{ verticalAlign: "middle" }} /> Force closed: {s.forcedCloseReason}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Force close modal */}
      {forceClose && (
        <div style={{ position: "fixed" as const, inset: 0, background: "rgba(0,0,0,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 24, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <h3 style={{ margin: "0 0 8px", fontSize: 15, fontWeight: 700 }}>Force Close Session</h3>
            <p style={{ margin: "0 0 16px", fontSize: 12, color: "#64748B" }}>The guard's session will be ended immediately. This is logged in the audit trail.</p>
            {apiError && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 10 }}>{apiError}</p>}
            <input value={reason} onChange={e => setReason(e.target.value)} placeholder="Reason (required)" style={{ width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box" as const, marginBottom: 16 }} />
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => setForceClose(null)} style={{ padding: "9px 16px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 13, cursor: "pointer" }}>Cancel</button>
              <button onClick={() => forceCloseMut.mutate({ id: forceClose, reason })} disabled={!reason.trim()}
                style={{ padding: "9px 16px", border: "none", borderRadius: 8, background: reason.trim() ? "#DC2626" : "#F1F5F9", color: reason.trim() ? "#fff" : "#94A3B8", fontSize: 13, fontWeight: 600, cursor: reason.trim() ? "pointer" : "not-allowed" }}>
                Force Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
