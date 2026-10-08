// src/pages/clinic/WorkingHoursTab.tsx
// A practitioner's usual week. A practitioner with no hours saved can be booked at any time; once hours are saved,
// the booking screen refuses (with a "book anyway" override) anything outside them. Times are clinic time.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useCan } from "./clinicAccess"

export interface Period { from: string; to: string }
export type Week = Record<number, Period[]>
interface WindowDto { dayOfWeek: number; from: string; to: string }
interface Practitioner { id: string; fullName: string }

const unwrap = (r: any) => r.data?.data ?? r.data
export const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
const MAX_PER_DAY = 4

export const emptyWeek = (): Week => ({ 1: [], 2: [], 3: [], 4: [], 5: [], 6: [], 7: [] })

export function toWeek(windows: WindowDto[]): Week {
  const w = emptyWeek()
  for (const x of windows) (w[x.dayOfWeek] ??= []).push({ from: x.from, to: x.to })
  return w
}

export function toWindows(week: Week): WindowDto[] {
  const out: WindowDto[] = []
  for (let d = 1; d <= 7; d++) for (const p of week[d] ?? []) out.push({ dayOfWeek: d, from: p.from, to: p.to })
  return out
}

/** Why the week cannot be saved yet, or null. Mirrors the server's checks. */
export function weekProblem(week: Week): string | null {
  for (let d = 1; d <= 7; d++) {
    const periods = [...(week[d] ?? [])]
    if (periods.length > MAX_PER_DAY) return `${DAYS[d - 1]}: at most ${MAX_PER_DAY} working periods a day`
    for (const p of periods) {
      if (!p.from || !p.to) return `${DAYS[d - 1]}: enter a start and an end time`
      if (p.to <= p.from) return `${DAYS[d - 1]}: the end time must be after the start time`
    }
    periods.sort((a, b) => a.from.localeCompare(b.from))
    for (let i = 1; i < periods.length; i++) {
      if (periods[i].from < periods[i - 1].to) return `${DAYS[d - 1]}: working periods overlap`
    }
  }
  return null
}

export default function WorkingHoursTab() {
  const qc = useQueryClient()
  const canWrite = useCan("editWorkingHours")
  const [practitionerId, setPractitionerId] = useState("")
  const [week, setWeek] = useState<Week>(emptyWeek())
  const [error, setError] = useState("")
  const [saved, setSaved] = useState(false)

  const { data: practitioners = [] } = useQuery<Practitioner[]>({
    queryKey: ["clinic-practitioners-list"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/practitioners/list")),
  })
  const hours = useQuery<WindowDto[]>({
    queryKey: ["clinic-working-hours", practitionerId],
    enabled: !!practitionerId,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/practitioners/${practitionerId}/working-hours`)),
  })
  useEffect(() => { if (hours.data) { setWeek(toWeek(hours.data)); setSaved(false) } }, [hours.data])

  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/clinic/practitioners/${practitionerId}/working-hours`, { windows: toWindows(week) }),
    onSuccess: () => { setError(""); setSaved(true); qc.invalidateQueries({ queryKey: ["clinic-working-hours", practitionerId] }) },
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not save the working hours"),
  })

  const edit = (d: number, fn: (p: Period[]) => Period[]) => { setWeek(w => ({ ...w, [d]: fn(w[d] ?? []) })); setSaved(false) }
  const field = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" } as const
  const small = { padding: "5px 10px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer" } as const
  const unrestricted = toWindows(week).length === 0

  return (
    <div style={{ maxWidth: 760 }}>
      <h2 style={{ margin: "0 0 4px", fontSize: 18, fontWeight: 700, color: "var(--hf-text)" }}>Working hours</h2>
      <p style={{ margin: "0 0 16px", fontSize: 13, color: "var(--hf-text-muted)" }}>
        Set a practitioner's usual week. Booking outside these hours asks for confirmation first. A day with no periods is a day off.
        A practitioner with no hours saved can be booked at any time.
      </p>

      <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", display: "flex", flexDirection: "column", gap: 4, maxWidth: 320, marginBottom: 16 }}>
        Practitioner
        <select aria-label="Practitioner" value={practitionerId} style={field}
          onChange={e => { setPractitionerId(e.target.value); setWeek(emptyWeek()); setError(""); setSaved(false) }}>
          <option value="">Choose a practitioner...</option>
          {practitioners.map(p => <option key={p.id} value={p.id}>{p.fullName}</option>)}
        </select>
      </label>

      {error && <div role="alert" style={{ marginBottom: 12, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}

      {practitionerId && (hours.isError ? (
        <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>Could not load the working hours.</div>
      ) : hours.isLoading ? (
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading...</div>
      ) : (
        <>
          {unrestricted && (
            <div style={{ marginBottom: 12, fontSize: 13, color: "var(--hf-text-muted)" }}>No hours set. This practitioner can be booked at any time.</div>
          )}
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {DAYS.map((name, i) => {
              const d = i + 1
              const periods = week[d] ?? []
              return (
                <div key={d} style={{ display: "flex", alignItems: "flex-start", gap: 12, padding: "10px 14px", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
                  <div style={{ width: 96, fontSize: 14, fontWeight: 600, color: "var(--hf-text)", paddingTop: 6 }}>{name}</div>
                  <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 6 }}>
                    {periods.length === 0 && !unrestricted && <div style={{ fontSize: 13, color: "var(--hf-text-muted)", paddingTop: 6 }}>Day off</div>}
                    {periods.length === 0 && unrestricted && <div style={{ fontSize: 13, color: "var(--hf-text-faint)", paddingTop: 6 }}>-</div>}
                    {periods.map((p, k) => (
                      <div key={k} style={{ display: "flex", gap: 8, alignItems: "center" }}>
                        <input type="time" aria-label={`${name} from ${k + 1}`} value={p.from} disabled={!canWrite} style={field}
                          onChange={e => edit(d, ps => ps.map((x, j) => j === k ? { ...x, from: e.target.value } : x))} />
                        <span style={{ color: "var(--hf-text-muted)" }}>to</span>
                        <input type="time" aria-label={`${name} to ${k + 1}`} value={p.to} disabled={!canWrite} style={field}
                          onChange={e => edit(d, ps => ps.map((x, j) => j === k ? { ...x, to: e.target.value } : x))} />
                        {canWrite && <button type="button" aria-label={`Remove ${name} period ${k + 1}`} style={small}
                          onClick={() => edit(d, ps => ps.filter((_, j) => j !== k))}>Remove</button>}
                      </div>
                    ))}
                  </div>
                  {canWrite && periods.length < MAX_PER_DAY && (
                    <button type="button" aria-label={`Add ${name} period`} style={small}
                      onClick={() => edit(d, ps => [...ps, ps.length ? { from: ps[ps.length - 1].to, to: "" } : { from: "08:00", to: "17:00" }])}>Add period</button>
                  )}
                </div>
              )
            })}
          </div>

          {canWrite && (
            <div style={{ display: "flex", gap: 10, marginTop: 16, alignItems: "center" }}>
              <button type="button" disabled={save.isPending} onClick={() => {
                const p = weekProblem(week)
                if (p) { setError(p); return }
                setError(""); save.mutate()
              }} style={{ padding: "8px 16px", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
                background: "var(--hf-accent)", color: "var(--hf-text-on-solid)" }}>
                {save.isPending ? "Saving..." : "Save working hours"}
              </button>
              <button type="button" style={small} onClick={() => { setWeek(emptyWeek()); setSaved(false) }}>Clear all (no restriction)</button>
              {saved && <span role="status" style={{ fontSize: 13, color: "var(--hf-success-text)" }}>Saved</span>}
            </div>
          )}
        </>
      ))}
    </div>
  )
}
