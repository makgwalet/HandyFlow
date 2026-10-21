// Work schedules: the ordinary hours that lateness, absence and overtime are measured against.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { fmtMinutes, scheduleFormProblem, WEEKDAYS, WEEKDAY_LABEL, type ScheduleForm } from "./attendance.logic"
import { card, errorText, input, label, plain, primary, td, th, unwrap } from "./attendance.shared"
import { Chip, Notice } from "./AttendanceParts"

interface Schedule {
  id: string; name: string; days: string[]; startTime: string; endTime: string; breakMinutes: number
  isDefault: boolean; active: boolean; dailyMinutes: number; weeklyMinutes: number; warnings: string[]
}

const blank: ScheduleForm = { name: "", days: ["MON", "TUE", "WED", "THU", "FRI"], startTime: "08:00", endTime: "17:00", breakMinutes: "60" }

export default function SchedulesPanel({ manage }: { manage: boolean }) {
  const qc = useQueryClient()
  const [editing, setEditing] = useState<{ id: string | null; form: ScheduleForm } | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const list = useQuery({ queryKey: ["hr-attendance", "schedules"], queryFn: async () => unwrap<Schedule[]>(await apiClient.get("/api/v1/hr/attendance/schedules")) })

  const act = useMutation({
    mutationFn: async (call: () => Promise<unknown>) => call(),
    onSuccess: () => { setError(null); setEditing(null); setProblem(null); qc.invalidateQueries({ queryKey: ["hr-attendance"] }) },
    onError: (e: unknown) => setError(errorText(e, "That did not work.")),
  })

  const save = () => {
    if (!editing) return
    const p = scheduleFormProblem(editing.form); setProblem(p)
    if (p) return
    const f = editing.form
    const body = { name: f.name.trim(), days: f.days, startTime: f.startTime, endTime: f.endTime, breakMinutes: Number(f.breakMinutes) }
    act.mutate(() => editing.id ? apiClient.put(`/api/v1/hr/attendance/schedules/${editing.id}`, body) : apiClient.post("/api/v1/hr/attendance/schedules", body))
  }

  const setForm = (patch: Partial<ScheduleForm>) => setEditing(e => e && { ...e, form: { ...e.form, ...patch } })

  return (
    <div>
      {error && <Notice kind="error">{error}</Notice>}
      {list.isLoading && <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>}
      <div style={card}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Schedule</th><th style={th}>Days</th><th style={th}>Hours</th><th style={th}>Ordinary week</th>{manage && <th style={th} />}</tr></thead>
          <tbody>
            {(list.data ?? []).map(s => (
              <tr key={s.id}>
                <td style={td}>{s.name} {s.isDefault && <Chip text="Default" color="var(--hf-success-text-strong)" bg="var(--hf-success-soft-strong)" />} {!s.active && <Chip text="Off" color="var(--hf-text-muted)" bg="var(--hf-surface-sunken)" />}
                  {s.warnings.map(w => <div key={w} style={{ fontSize: 11, color: "var(--hf-warning-text-strong)" }}>{w}</div>)}</td>
                <td style={td}>{s.days.map(d => WEEKDAY_LABEL[d]).join(", ")}</td>
                <td style={td}>{s.startTime.slice(0, 5)}–{s.endTime.slice(0, 5)}, {s.breakMinutes}m break</td>
                <td style={td}>{fmtMinutes(s.weeklyMinutes)}</td>
                {manage && <td style={td}>
                  <button style={plain} onClick={() => { setEditing({ id: s.id, form: { name: s.name, days: s.days, startTime: s.startTime.slice(0, 5), endTime: s.endTime.slice(0, 5), breakMinutes: String(s.breakMinutes) } }); setProblem(null) }}>Edit</button>
                  {!s.isDefault && s.active && <>{" "}<button style={plain} onClick={() => act.mutate(() => apiClient.put(`/api/v1/hr/attendance/schedules/${s.id}/default`))}>Make default</button></>}
                  {!s.isDefault && <>{" "}<button style={plain} onClick={() => act.mutate(() => apiClient.put(`/api/v1/hr/attendance/schedules/${s.id}/active`, { active: !s.active }))}>{s.active ? "Switch off" : "Switch on"}</button></>}
                </td>}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>People work the default schedule unless they are given another. Shifts across midnight are not supported yet.</p>

      {manage && !editing && <button style={primary} onClick={() => { setEditing({ id: null, form: blank }); setProblem(null) }}>Add a schedule</button>}
      {editing && (
        <div style={{ padding: 14, border: "1px solid var(--hf-border)", borderRadius: 10 }}>
          <strong style={{ fontSize: 13 }}>{editing.id ? "Edit schedule" : "New schedule"}</strong>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 10, margin: "10px 0" }}>
            <div><label style={label} htmlFor="sch-name">Name</label><input id="sch-name" style={input} value={editing.form.name} onChange={e => setForm({ name: e.target.value })} /></div>
            <div><label style={label} htmlFor="sch-start">Start</label><input id="sch-start" type="time" style={input} value={editing.form.startTime} onChange={e => setForm({ startTime: e.target.value })} /></div>
            <div><label style={label} htmlFor="sch-end">End</label><input id="sch-end" type="time" style={input} value={editing.form.endTime} onChange={e => setForm({ endTime: e.target.value })} /></div>
            <div><label style={label} htmlFor="sch-break">Unpaid break (minutes)</label><input id="sch-break" style={input} value={editing.form.breakMinutes} onChange={e => setForm({ breakMinutes: e.target.value })} /></div>
          </div>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 8 }}>
            {WEEKDAYS.map(d => (
              <label key={d} style={{ fontSize: 13, display: "flex", gap: 4, alignItems: "center" }}>
                <input type="checkbox" checked={editing.form.days.includes(d)} onChange={e => setForm({ days: e.target.checked ? [...editing.form.days, d] : editing.form.days.filter(x => x !== d) })} />
                {WEEKDAY_LABEL[d]}
              </label>
            ))}
          </div>
          {problem && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 6 }}>{problem}</div>}
          <div style={{ display: "flex", gap: 8 }}>
            <button style={primary} disabled={act.isPending} onClick={save}>Save schedule</button>
            <button style={plain} onClick={() => setEditing(null)}>Never mind</button>
          </div>
        </div>
      )}
    </div>
  )
}
