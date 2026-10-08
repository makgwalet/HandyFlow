// src/pages/clinic/WaitingRoomTab.tsx
// Today's appointments as a board by where each patient is: expected, waiting (checked in), triaged, with the
// clinician. Read-only: check-in, triage and start still happen on the Schedule. It refreshes itself every 30 seconds.
// The time shown is the scheduled time and how far past it is now; no wait-time target is applied.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export interface BoardAppt {
  id: string; patientName: string; practitionerName?: string; scheduledAt: string; status: string; reason?: string | null
}
export const COLUMNS: { key: string; title: string; statuses: string[] }[] = [
  { key: "expected", title: "Expected", statuses: ["SCHEDULED", "CONFIRMED"] },
  { key: "waiting", title: "Waiting", statuses: ["CHECKED_IN"] },
  { key: "triaged", title: "Triaged", statuses: ["TRIAGED"] },
  { key: "with", title: "With the clinician", statuses: ["IN_PROGRESS"] },
]

const sameLocalDay = (iso: string, now: Date) => {
  const d = new Date(iso)
  return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate()
}

/** Today's appointments grouped into the board's columns, earliest first. Finished, cancelled and no-show ones are left out. */
export function buildBoard(all: BoardAppt[], now: Date): Record<string, BoardAppt[]> {
  const out: Record<string, BoardAppt[]> = Object.fromEntries(COLUMNS.map(c => [c.key, []]))
  for (const a of all) {
    if (!a.scheduledAt || !sameLocalDay(a.scheduledAt, now)) continue
    const col = COLUMNS.find(c => c.statuses.includes(a.status))
    if (col) out[col.key].push(a)
  }
  for (const k of Object.keys(out)) out[k].sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt))
  return out
}

/** The query for today's appointments: [local midnight, next local midnight) as instants. */
export function todayRangeUrl(now: Date): string {
  const from = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const to = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1)
  return `/api/v1/clinic/appointments/range?${new URLSearchParams({ from: from.toISOString(), to: to.toISOString() })}`
}

/** "10:30 · 12 min past" / "10:30 · in 20 min", rounded to whole minutes. */
export function timeNote(iso: string, now: Date): string {
  const t = new Date(iso)
  const hhmm = t.toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })
  const mins = Math.round((now.getTime() - t.getTime()) / 60000)
  if (mins === 0) return `${hhmm} · now`
  return mins > 0 ? `${hhmm} · ${mins} min past` : `${hhmm} · in ${-mins} min`
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : (p?.content ?? []) }

export default function WaitingRoomTab() {
  const { data, isLoading, isError, dataUpdatedAt } = useQuery<BoardAppt[]>({
    queryKey: ["clinic-waiting-room"], refetchInterval: 30000,
    // Only today, from the range endpoint: the plain list returns the newest 200 overall, which can miss today.
    queryFn: async () => unwrap(await apiClient.get(todayRangeUrl(new Date()))),
  })
  const now = new Date(dataUpdatedAt || Date.now())
  const board = buildBoard(data ?? [], now)
  const total = Object.values(board).reduce((n, l) => n + l.length, 0)
  return (
    <div>
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading…</div>}
      {isError && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The board could not be loaded. It will try again shortly.</div>}
      {!isLoading && !isError && total === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Nobody is expected, waiting or being seen right now.</div>}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(210px, 1fr))", gap: 12, marginTop: 8 }}>
        {COLUMNS.map(c => (
          <section key={c.key} aria-label={c.title} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 10, background: "var(--hf-surface-muted)" }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", marginBottom: 8 }}>{c.title.toUpperCase()} · {board[c.key].length}</div>
            {board[c.key].map(a => (
              <div key={a.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: "6px 10px", marginBottom: 6 }}>
                <div style={{ fontSize: 13, fontWeight: 600 }}>{a.patientName}</div>
                <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{timeNote(a.scheduledAt, now)}</div>
                {a.practitionerName && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Dr {a.practitionerName}</div>}
              </div>
            ))}
          </section>
        ))}
      </div>
      <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 10 }}>Check-in, triage and starting a session are done on the Schedule.</div>
    </div>
  )
}
