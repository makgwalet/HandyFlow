// Persistent video dock (W-2): the telehealth call lives above the routes, so it stays open while the clinician moves
// between the schedule, the patient file and the consultation. Leaving the call is always a click on "Leave call".
// Hiding the frame would drop the call, so "minimise" only makes it small.
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react"
import { useNavigate } from "react-router-dom"
import { apiClient } from "../../api/client"
import { workspacePath } from "./workspace"
import { DOCK_DIMENSIONS, isSafeRoomUrl, roomUrlFrom, sameCall, type DockSize } from "./videoDock"

interface Call { appointmentId: string; patientName: string; url: string }
interface VideoDock {
  call: Call | null
  busy: boolean
  error: string
  /** Open (or return to) the call for an appointment. Resolves true when the call is showing. */
  join: (appointmentId: string, patientName?: string) => Promise<boolean>
  leave: () => void
}

// Outside a provider (a screen rendered on its own, in a test) the dock does nothing and join reports failure.
const NONE: VideoDock = { call: null, busy: false, error: "", join: async () => false, leave: () => {} }
const Ctx = createContext<VideoDock>(NONE)
export const useVideoDock = () => useContext(Ctx)

export default function VideoDockProvider({ children }: { children: ReactNode }) {
  const [call, setCall] = useState<Call | null>(null)
  const [size, setSize] = useState<DockSize>("full")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")

  const join = useCallback(async (appointmentId: string, patientName = "Patient") => {
    setError("")
    if (sameCall(call, appointmentId)) { setSize("full"); return true }
    setBusy(true)
    try {
      const url = roomUrlFrom(await apiClient.post(`/api/v1/clinic/appointments/${appointmentId}/video-room`))
      if (!isSafeRoomUrl(url)) { setError("The video room address was not valid, so the call was not opened."); return false }
      setCall({ appointmentId, patientName, url })
      setSize("full")
      return true
    } catch (e: any) {
      setError(e?.response?.data?.message ?? "Failed to start video call")
      return false
    } finally { setBusy(false) }
  }, [call])

  const leave = useCallback(() => { setCall(null); setError("") }, [])
  const value = useMemo<VideoDock>(() => ({ call, busy, error, join, leave }), [call, busy, error, join, leave])

  return (
    <Ctx.Provider value={value}>
      {children}
      {error && !call && (
        <div role="alert" style={{ position: "fixed", right: 16, bottom: 84, zIndex: 60, maxWidth: 360, padding: "10px 14px", borderRadius: 10, fontSize: 13,
          background: "var(--hf-danger-soft)", color: "var(--hf-danger-text, var(--hf-danger))", boxShadow: "0 6px 24px rgba(0,0,0,0.18)", display: "flex", gap: 10, alignItems: "center" }}>
          <span style={{ flex: 1 }}>{error}</span>
          <button onClick={() => setError("")} aria-label="Dismiss" style={{ border: "none", background: "transparent", color: "inherit", fontWeight: 800, cursor: "pointer" }}>×</button>
        </div>)}
      {call && <DockFrame call={call} size={size} onSize={setSize} onLeave={leave} />}
    </Ctx.Provider>
  )
}

function DockFrame({ call, size, onSize, onLeave }: { call: Call; size: DockSize; onSize: (s: DockSize) => void; onLeave: () => void }) {
  const navigate = useNavigate()
  const { width, height } = DOCK_DIMENSIONS[size]
  const btn = { padding: "3px 9px", borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 12, fontWeight: 600, cursor: "pointer" } as const
  return (
    <div role="region" aria-label="Video call" style={{ position: "fixed", right: 16, bottom: 84, zIndex: 60, width, background: "var(--hf-surface)",
      border: "1px solid var(--hf-border)", borderTop: "3px solid var(--hf-success-text)", borderRadius: 12, boxShadow: "0 8px 30px rgba(0,0,0,0.25)", overflow: "hidden" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 6, padding: "6px 8px", flexWrap: "wrap" }}>
        <div style={{ flex: 1, minWidth: 0, fontSize: 12, fontWeight: 800, color: "var(--hf-text)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
          Video call · {call.patientName}
        </div>
        <button style={btn} onClick={() => onSize(size === "full" ? "mini" : "full")} aria-label={size === "full" ? "Minimise video call" : "Expand video call"}>
          {size === "full" ? "Minimise" : "Expand"}</button>
        {size === "full" && <button style={btn} onClick={() => navigate(workspacePath(call.appointmentId))}>Open consultation</button>}
        <button style={{ ...btn, color: "var(--hf-danger-text, var(--hf-danger))", borderColor: "var(--hf-danger)" }} onClick={onLeave}>Leave call</button>
      </div>
      <iframe title={`Video call with ${call.patientName}`} src={call.url} allow="camera; microphone; fullscreen; display-capture; autoplay"
        style={{ display: "block", width: "100%", height, border: "none", background: "#000" }} />
    </div>
  )
}
