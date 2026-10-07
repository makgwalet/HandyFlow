// src/pages/security/SchedulerTab.tsx
//
// Scheduler: a week at a glance, one row per guard and one column per day, South African time. Each shift shows its
// hours and site; a red or amber mark means a conflict (overlap, short rest, expired PSiRA, guard not active), and a
// guard row shows the week's hours, flagged over 45. "+" on a cell adds a shift: the server still enforces the hard rules
// and its message is shown as it comes back. Real shifts only; an empty cell is simply nothing scheduled.
import { useMemo, useState } from "react"
import { Link } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertTriangle, ChevronLeft, ChevronRight, Plus, Users, CalendarClock, Clock } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import Chip from "../../components/ui/Chip"
import StatTile from "../../components/ui/StatTile"
import {
  addDays, buildCells, clock, detectConflicts, fetchWindow, gridGuards, guardName, mondayOf, overHours, toInstants, weekDays, weekLabel, weeklyHours,
  CONFLICT_TONE, WEEKLY_HOURS_FLAG, type GridGuard, type GridShift,
} from "./schedule.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }
const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)", width: "100%", boxSizing: "border-box" }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "7px 11px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const TONE_COLOR = { bad: "var(--hf-danger-text)", warn: "var(--hf-warning-text)" } as Record<string, string>
const STATUS_BG: Record<string, string> = { SCHEDULED: "var(--hf-info-soft)", ACTIVE: "var(--hf-success-soft-strong)", COMPLETED: "var(--hf-success-soft)", MISSED: "var(--hf-danger-soft)", PULLED: "var(--hf-surface-sunken)" }
const dayHead = (k: string) => new Date(`${k}T00:00:00Z`).toLocaleDateString("en-ZA", { weekday: "short", day: "numeric", timeZone: "UTC" })
const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : p?.content ?? [] }
const errText = (e: any) => e?.response?.data?.message ?? "That did not work. Please try again."

interface Site { id: string; name: string }

function AddShift({ guard, day, sites, onClose, onSaved }: { guard: GridGuard; day: string; sites: Site[]; onClose: () => void; onSaved: () => void }) {
  const [siteId, setSiteId] = useState(sites[0]?.id ?? "")
  const [start, setStart] = useState("06:00")
  const [end, setEnd] = useState("18:00")
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: (b: any) => apiClient.post("/api/v1/security/shifts", b),
    onSuccess: onSaved, onError: e => setErr(errText(e)),
  })
  const submit = () => {
    const t = toInstants(day, start, end)
    if (!siteId) return setErr("Choose a site.")
    if (!t) return setErr("Enter a start and an end time that differ.")
    setErr(""); save.mutate({ siteId, guardId: guard.id, ...t })
  }
  return (
    <div role="dialog" aria-label="Add shift" style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.35)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ ...card, padding: 18, width: 360, display: "grid", gap: 10 }}>
        <div style={{ fontWeight: 700 }}>Add shift for {guardName(guard)}</div>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{dayHead(day)}. An end time before the start means the shift ends the next day.</div>
        <label style={{ fontSize: 12 }}>Site<select value={siteId} onChange={e => setSiteId(e.target.value)} style={input}>{sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
        <div style={{ display: "flex", gap: 10 }}>
          <label style={{ fontSize: 12, flex: 1 }}>Start<input type="time" value={start} onChange={e => setStart(e.target.value)} style={input} /></label>
          <label style={{ fontSize: 12, flex: 1 }}>End<input type="time" value={end} onChange={e => setEnd(e.target.value)} style={input} /></label>
        </div>
        {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}
        <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
          <button style={btn} onClick={onClose}>Cancel</button>
          <button style={{ ...btn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }} disabled={save.isPending} onClick={submit}>{save.isPending ? "Saving…" : "Add shift"}</button>
        </div>
      </div>
    </div>
  )
}

export default function SchedulerTab() {
  const qc = useQueryClient()
  const canManage = usePermission("SECURITY_MANAGE")
  const [monday, setMonday] = useState(() => mondayOf(new Date()))
  const [siteId, setSiteId] = useState("")
  const [search, setSearch] = useState("")
  const [adding, setAdding] = useState<{ guard: GridGuard; day: string } | null>(null)
  const days = useMemo(() => weekDays(monday), [monday])

  const { data: shifts = [], isLoading, error } = useQuery<GridShift[]>({
    queryKey: ["scheduler-shifts", monday],
    queryFn: async () => { const w = fetchWindow(monday); return unwrap(await apiClient.get(`/api/v1/security/shifts/range?from=${encodeURIComponent(w.from.toISOString())}&to=${encodeURIComponent(w.to.toISOString())}`)) },
  })
  const { data: guards = [] } = useQuery<GridGuard[]>({ queryKey: ["scheduler-guards"], queryFn: async () => unwrap(await apiClient.get("/api/v1/security/guards?size=200")) })
  const { data: sites = [] } = useQuery<Site[]>({ queryKey: ["scheduler-sites"], queryFn: async () => unwrap(await apiClient.get("/api/v1/security/sites?size=200")) })

  const siteName = useMemo(() => new Map(sites.map(s => [s.id, s.name])), [sites])
  const cells = useMemo(() => buildCells(shifts, days), [shifts, days])
  const conflicts = useMemo(() => detectConflicts(shifts, guards), [shifts, guards])
  const hours = useMemo(() => weeklyHours(shifts, days), [shifts, days])
  const rows = useMemo(() => gridGuards(guards, cells, { search, siteId }), [guards, cells, search, siteId])

  const inWeek = [...cells.values()].flatMap(r => [...r.values()].flat())
  const conflicted = inWeek.filter(s => conflicts.has(s.id)).length
  const over = [...hours.entries()].filter(([, h]) => overHours(h)).length

  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
        <button style={btn} aria-label="Previous week" onClick={() => setMonday(addDays(monday, -7))}><ChevronLeft size={14} /></button>
        <div style={{ fontWeight: 700, minWidth: 150, textAlign: "center" }}>{weekLabel(monday)}</div>
        <button style={btn} aria-label="Next week" onClick={() => setMonday(addDays(monday, 7))}><ChevronRight size={14} /></button>
        <button style={btn} onClick={() => setMonday(mondayOf(new Date()))}>This week</button>
        <select aria-label="Site" value={siteId} onChange={e => setSiteId(e.target.value)} style={{ ...input, width: "auto" }}>
          <option value="">All sites</option>{sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
        <input aria-label="Search guards" placeholder="Search guards" value={search} onChange={e => setSearch(e.target.value)} style={{ ...input, width: 180 }} />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 12, marginBottom: 14 }}>
        <StatTile label="Shifts this week" value={inWeek.length} icon={<CalendarClock size={18} />} />
        <StatTile label="Guards scheduled" value={cells.size} icon={<Users size={18} />} />
        <StatTile label="Shifts with a conflict" value={conflicted} tone={conflicted ? "bad" : "neutral"} icon={<AlertTriangle size={18} />} />
        <StatTile label={`Guards over ${WEEKLY_HOURS_FLAG} h`} value={over} tone={over ? "warn" : "neutral"} icon={<Clock size={18} />} />
      </div>

      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", marginBottom: 10 }}>The shifts for this week could not be loaded.</div>}

      <div style={{ ...card, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 1000, fontSize: 12, tableLayout: "fixed" }}>
          <thead>
            <tr>
              <th style={{ textAlign: "left", padding: 10, width: 190, color: "var(--hf-text-muted)", fontWeight: 700 }}>Guard</th>
              {days.map(d => <th key={d} style={{ textAlign: "left", padding: 10, color: "var(--hf-text-muted)", fontWeight: 700 }}>{dayHead(d)}</th>)}
            </tr>
          </thead>
          <tbody>
            {isLoading && <tr><td colSpan={8} style={{ padding: 14, color: "var(--hf-text-muted)" }}>Loading…</td></tr>}
            {!isLoading && rows.length === 0 && <tr><td colSpan={8} style={{ padding: 14, color: "var(--hf-text-muted)" }}>No guards to show for these filters.</td></tr>}
            {rows.map(g => {
              const h = hours.get(g.id) ?? 0
              return (
                <tr key={g.id} style={{ borderTop: "1px solid var(--hf-border)", verticalAlign: "top" }}>
                  <td style={{ padding: 10 }}>
                    <Link to={`/security/guards/${g.id}`} style={{ color: "var(--hf-primary-text)", fontWeight: 600, textDecoration: "none" }}>{guardName(g)}</Link>
                    <div style={{ marginTop: 4, display: "flex", gap: 6, flexWrap: "wrap" }}>
                      <Chip tone={overHours(h) ? "warn" : "neutral"}>{h} h</Chip>
                      {g.status !== "ACTIVE" && <Chip tone="warn">{g.status.toLowerCase().replace(/_/g, " ")}</Chip>}
                    </div>
                  </td>
                  {days.map(d => {
                    const list = (cells.get(g.id)?.get(d) ?? []).filter(s => !siteId || s.siteId === siteId)
                    return (
                      <td key={d} style={{ padding: 6 }}>
                        {list.map(s => {
                          const cs = conflicts.get(s.id) ?? []
                          const worst = cs.find(c => CONFLICT_TONE[c.kind] === "bad") ?? cs[0]
                          return (
                            <div key={s.id} title={cs.map(c => c.text).join("\n") || undefined} data-conflict={cs.length ? "yes" : undefined}
                              style={{ background: STATUS_BG[s.status] ?? "var(--hf-surface-sunken)", borderRadius: 8, padding: "5px 7px", marginBottom: 4, borderLeft: worst ? `3px solid ${TONE_COLOR[CONFLICT_TONE[worst.kind]]}` : "3px solid transparent" }}>
                              <div style={{ fontWeight: 700 }}>{clock(s.startAt)} to {clock(s.endAt)}</div>
                              <div style={{ color: "var(--hf-text-muted)" }}>{siteName.get(s.siteId) ?? "Site"}</div>
                              {cs.map(c => <div key={c.kind} style={{ color: TONE_COLOR[CONFLICT_TONE[c.kind]], fontWeight: 600 }}>{c.text}</div>)}
                            </div>
                          )
                        })}
                        {canManage && g.status === "ACTIVE" && (
                          <button aria-label={`Add shift for ${guardName(g)} on ${dayHead(d)}`} style={{ ...btn, padding: "2px 6px", opacity: 0.7 }} onClick={() => setAdding({ guard: g, day: d })}><Plus size={12} /></button>
                        )}
                      </td>
                    )
                  })}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>
        Times are South African time. Hours count shifts that start this week (scheduled, active and completed). Flags describe the schedule only; nothing is changed or blocked from here beyond what the server already enforces when a shift is added.
      </div>

      {adding && <AddShift guard={adding.guard} day={adding.day} sites={sites} onClose={() => setAdding(null)}
        onSaved={() => { setAdding(null); qc.invalidateQueries({ queryKey: ["scheduler-shifts"] }); qc.invalidateQueries({ queryKey: ["shifts"] }) }} />}
    </div>
  )
}
