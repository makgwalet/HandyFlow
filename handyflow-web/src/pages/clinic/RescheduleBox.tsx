import { useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { clashMessage, WALK_IN_GRACE_MS } from "./bookingRules"

export interface Movable { id: string; scheduledAt: string; durationMinutes: number }

/** Local "yyyy-mm-ddThh:mm" for a datetime-local input. */
export const toLocalInput = (iso: string) => {
  const d = new Date(iso)
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}

/** Why the new time cannot be sent, or null. */
export function moveProblem(local: string, now: number): string | null {
  if (!local) return "Choose the new date and time"
  const t = new Date(local).getTime()
  if (Number.isNaN(t)) return "Choose a valid date and time"
  if (t < now - WALK_IN_GRACE_MS) return "Cannot move an appointment into the past"
  return null
}

const btn = { padding: "7px 14px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" } as const

/**
 * "Reschedule" for an appointment that has not started: pick a new time, and if the practitioner is busy
 * then, the server's warning is shown with a "Move anyway" button.
 */
export default function RescheduleBox({ appointment, onMoved }: { appointment: Movable; onMoved: (updated: any) => void }) {
  const [open, setOpen] = useState(false)
  const [when, setWhen] = useState(toLocalInput(appointment.scheduledAt))
  const [error, setError] = useState("")
  const [clash, setClash] = useState("")

  const move = useMutation({
    mutationFn: (allowOverlap: boolean) => apiClient.post(`/api/v1/clinic/appointments/${appointment.id}/reschedule`,
      { scheduledAt: new Date(when).toISOString() }, allowOverlap ? { params: { allowOverlap: true } } : undefined),
    onSuccess: (res: any) => { setOpen(false); setClash(""); setError(""); onMoved(res.data?.data ?? res.data) },
    onError: (e: any) => {
      const c = clashMessage(e)
      if (c) { setClash(c); setError(""); return }
      setClash(""); setError(e?.response?.data?.message ?? "Could not move the appointment")
    },
  })

  if (!open) {
    return <button type="button" onClick={() => setOpen(true)}
      style={{ ...btn, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-accent-text)" }}>Reschedule</button>
  }
  return (
    <div style={{ width: "100%", display: "flex", flexDirection: "column", gap: 8, padding: 12, border: "1px solid var(--hf-border)", borderRadius: 8 }}>
      <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)" }}>
        New date and time
        <input type="datetime-local" aria-label="New date and time" value={when}
          onChange={e => { setWhen(e.target.value); setClash(""); setError("") }}
          style={{ display: "block", marginTop: 4, padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, width: "100%" }} />
      </label>
      {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
      {clash && (
        <div role="alert" style={{ padding: "8px 10px", background: "var(--hf-warning-soft)", borderRadius: 8, fontSize: 13, color: "var(--hf-warning-text)" }}>
          <div style={{ fontWeight: 600, marginBottom: 6 }}>{clash}</div>
          <button type="button" disabled={move.isPending} onClick={() => move.mutate(true)}
            style={{ ...btn, padding: "4px 10px", fontSize: 12, border: "1px solid var(--hf-warning-text)", background: "transparent", color: "var(--hf-warning-text)" }}>Move anyway</button>
        </div>
      )}
      <div style={{ display: "flex", gap: 8 }}>
        <button type="button" disabled={move.isPending} onClick={() => {
          const p = moveProblem(when, Date.now())
          if (p) { setError(p); return }
          setError(""); move.mutate(false)
        }} style={{ ...btn, border: "none", background: "var(--hf-accent)", color: "var(--hf-text-on-solid)" }}>
          {move.isPending ? "Moving..." : "Move appointment"}
        </button>
        <button type="button" onClick={() => { setOpen(false); setClash(""); setError("") }}
          style={{ ...btn, border: "1px solid var(--hf-border)", background: "transparent", color: "var(--hf-text-muted)" }}>Cancel</button>
      </div>
    </div>
  )
}
