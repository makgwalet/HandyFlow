// src/pages/clinic/TimeOffTab.tsx
// Leave, training and other blocks when a practitioner cannot be booked. Bookings that fall inside a block are
// refused (with a "book anyway" override) by the booking screen. A block that no longer applies is cancelled,
// and stays in the record as cancelled.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useCan } from "./clinicAccess"

export interface TimeOff { id: string; practitionerId: string; startsAt: string; endsAt: string; reason?: string | null }
interface Practitioner { id: string; fullName: string }

const unwrap = (r: any) => r.data?.data ?? r.data
const fmt = (iso: string) => new Date(iso).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short", timeZone: "Africa/Johannesburg" })
const MAX_DAYS = 90

/** Why the block cannot be added yet, or null. Mirrors the server's checks. */
export function timeOffProblem(from: string, to: string): string | null {
  if (!from || !to) return "Choose when the time off starts and ends"
  const a = new Date(from).getTime(), b = new Date(to).getTime()
  if (Number.isNaN(a) || Number.isNaN(b)) return "Choose a valid start and end"
  if (b <= a) return "The end must be after the start"
  if (b - a > MAX_DAYS * 86400000) return `Time off can be at most ${MAX_DAYS} days at a time`
  return null
}

export default function TimeOffTab() {
  const qc = useQueryClient()
  const canWrite = useCan("editTimeOff")
  const [practitionerId, setPractitionerId] = useState("")
  const [from, setFrom] = useState("")
  const [to, setTo] = useState("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")

  const { data: practitioners = [] } = useQuery<Practitioner[]>({
    queryKey: ["clinic-practitioners-list"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/practitioners/list")),
  })
  const list = useQuery<TimeOff[]>({
    queryKey: ["clinic-time-off", practitionerId],
    enabled: !!practitionerId,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/practitioners/${practitionerId}/time-off`)),
  })
  const add = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/clinic/practitioners/${practitionerId}/time-off`,
      { startsAt: new Date(from).toISOString(), endsAt: new Date(to).toISOString(), reason: reason.trim() || null }),
    onSuccess: () => { setFrom(""); setTo(""); setReason(""); setError(""); qc.invalidateQueries({ queryKey: ["clinic-time-off", practitionerId] }) },
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not add the time off"),
  })
  const cancel = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/clinic/time-off/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["clinic-time-off", practitionerId] }),
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not cancel the time off"),
  })

  const field = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" } as const
  const label = { fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", display: "flex", flexDirection: "column", gap: 4 } as const

  return (
    <div style={{ maxWidth: 760 }}>
      <h2 style={{ margin: "0 0 4px", fontSize: 18, fontWeight: 700, color: "var(--hf-text)" }}>Time off</h2>
      <p style={{ margin: "0 0 16px", fontSize: 13, color: "var(--hf-text-muted)" }}>
        Block out leave, training or any time a practitioner cannot be booked. Booking into a block asks for confirmation first.
      </p>

      <label style={{ ...label, maxWidth: 320, marginBottom: 16 }}>
        Practitioner
        <select aria-label="Practitioner" value={practitionerId} style={field}
          onChange={e => { setPractitionerId(e.target.value); setError("") }}>
          <option value="">Choose a practitioner...</option>
          {practitioners.map(p => <option key={p.id} value={p.id}>{p.fullName}</option>)}
        </select>
      </label>

      {error && <div role="alert" style={{ marginBottom: 12, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}

      {practitionerId && (
        <>
          {canWrite && (
            <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end", marginBottom: 20 }}>
              <label style={label}>From
                <input type="datetime-local" aria-label="From" value={from} onChange={e => setFrom(e.target.value)} style={field} /></label>
              <label style={label}>To
                <input type="datetime-local" aria-label="To" value={to} onChange={e => setTo(e.target.value)} style={field} /></label>
              <label style={{ ...label, flex: 1, minWidth: 160 }}>Reason (optional)
                <input aria-label="Reason" value={reason} maxLength={200} onChange={e => setReason(e.target.value)} style={field} placeholder="Annual leave" /></label>
              <button type="button" disabled={add.isPending} onClick={() => {
                const p = timeOffProblem(from, to)
                if (p) { setError(p); return }
                setError(""); add.mutate()
              }} style={{ padding: "8px 16px", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
                background: "var(--hf-accent)", color: "var(--hf-text-on-solid)" }}>
                {add.isPending ? "Adding..." : "Add time off"}
              </button>
            </div>
          )}

          {list.isError ? (
            <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>Could not load the time off.</div>
          ) : list.isLoading ? (
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading...</div>
          ) : (list.data ?? []).length === 0 ? (
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No current or upcoming time off.</div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {(list.data ?? []).map(b => (
                <div key={b.id} style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 14px", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)" }}>{fmt(b.startsAt)} → {fmt(b.endsAt)}</div>
                    {b.reason && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{b.reason}</div>}
                  </div>
                  {canWrite && (
                    <button type="button" disabled={cancel.isPending} onClick={() => cancel.mutate(b.id)}
                      style={{ padding: "5px 12px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "transparent",
                        color: "var(--hf-danger-text)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>Cancel</button>
                  )}
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}
