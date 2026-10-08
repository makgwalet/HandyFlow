// src/pages/clinic/ClosuresTab.tsx
// Whole days when the clinic takes no bookings (public holidays, a staff day, maintenance). Booking onto a closed day
// is refused (with a "book anyway" override) by the booking screen. Nothing is pre-filled: enter your own closures.
// A closure that no longer applies is cancelled, and stays in the record as cancelled.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useCan } from "./clinicAccess"

export interface Closure { id: string; firstDay: string; lastDay: string; reason?: string | null }

const unwrap = (r: any) => r.data?.data ?? r.data
const MAX_DAYS = 60
const day = (d: string) => new Date(d + "T00:00:00").toLocaleDateString("en-ZA", { weekday: "short", day: "numeric", month: "short", year: "numeric" })

/** Why the closure cannot be added yet, or null. Mirrors the server's checks. Days are yyyy-mm-dd. */
export function closureProblem(first: string, last: string): string | null {
  if (!first || !last) return "Choose the first and last day"
  const a = Date.parse(first + "T00:00:00Z"), b = Date.parse(last + "T00:00:00Z")
  if (Number.isNaN(a) || Number.isNaN(b)) return "Choose valid days"
  if (b < a) return "The last day cannot be before the first day"
  if ((b - a) / 86400000 + 1 > MAX_DAYS) return `A closure can be at most ${MAX_DAYS} days`
  return null
}

export default function ClosuresTab() {
  const qc = useQueryClient()
  const canAdmin = useCan("manageClosures")
  const [first, setFirst] = useState("")
  const [last, setLast] = useState("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")

  const list = useQuery<Closure[]>({
    queryKey: ["clinic-closures"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/closures")),
  })
  const add = useMutation({
    mutationFn: () => apiClient.post("/api/v1/clinic/closures", { firstDay: first, lastDay: last || first, reason: reason.trim() || null }),
    onSuccess: () => { setFirst(""); setLast(""); setReason(""); setError(""); qc.invalidateQueries({ queryKey: ["clinic-closures"] }) },
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not add the closure"),
  })
  const cancel = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/clinic/closures/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["clinic-closures"] }),
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not cancel the closure"),
  })

  const field = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" } as const
  const label = { fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", display: "flex", flexDirection: "column", gap: 4 } as const

  return (
    <div style={{ maxWidth: 760 }}>
      <h2 style={{ margin: "0 0 4px", fontSize: 18, fontWeight: 700, color: "var(--hf-text)" }}>Closures</h2>
      <p style={{ margin: "0 0 16px", fontSize: 13, color: "var(--hf-text-muted)" }}>
        Days when the whole clinic is closed, such as public holidays. Booking onto a closed day asks for confirmation first.
        Enter your own dates; none are pre-filled.
      </p>

      {error && <div role="alert" style={{ marginBottom: 12, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}

      {canAdmin && (
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end", marginBottom: 20 }}>
          <label style={label}>First day
            <input type="date" aria-label="First day" value={first} onChange={e => setFirst(e.target.value)} style={field} /></label>
          <label style={label}>Last day (blank for one day)
            <input type="date" aria-label="Last day" value={last} onChange={e => setLast(e.target.value)} style={field} /></label>
          <label style={{ ...label, flex: 1, minWidth: 160 }}>Reason (optional)
            <input aria-label="Reason" value={reason} maxLength={200} onChange={e => setReason(e.target.value)} style={field} placeholder="Public holiday" /></label>
          <button type="button" disabled={add.isPending} onClick={() => {
            const p = closureProblem(first, last || first)
            if (p) { setError(p); return }
            setError(""); add.mutate()
          }} style={{ padding: "8px 16px", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
            background: "var(--hf-accent)", color: "var(--hf-text-on-solid)" }}>
            {add.isPending ? "Adding..." : "Add closure"}
          </button>
        </div>
      )}

      {list.isError ? (
        <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>Could not load the closures.</div>
      ) : list.isLoading ? (
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading...</div>
      ) : (list.data ?? []).length === 0 ? (
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No current or upcoming closures.</div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {(list.data ?? []).map(c => (
            <div key={c.id} style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 14px", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)" }}>
                  {c.firstDay === c.lastDay ? day(c.firstDay) : `${day(c.firstDay)} → ${day(c.lastDay)}`}
                </div>
                {c.reason && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{c.reason}</div>}
              </div>
              {canAdmin && (
                <button type="button" disabled={cancel.isPending} onClick={() => cancel.mutate(c.id)}
                  style={{ padding: "5px 12px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "transparent",
                    color: "var(--hf-danger-text)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>Cancel</button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
