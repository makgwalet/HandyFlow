// src/pages/hr/AttendanceTab.tsx
//
// Attendance: clock in and out, the day-by-day record, the register of people, overtime claims and work schedules. The server
// works out what each day means and who may do what; this screen shows it and offers only what is allowed.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useAuth } from "../../hooks/useAuth"
import { attendanceRights, claimableDays, fmtMinutes, fmtTime, isoDate, lastDays, monthPeriod, periodProblem } from "./attendance.logic"
import { card, errorText, input, label, plain, primary, td, th, unwrap, type Timesheet } from "./attendance.shared"
import { Notice } from "./AttendanceParts"
import OvertimePanel from "./OvertimePanel"
import SchedulesPanel from "./SchedulesPanel"
import TimesheetTable from "./TimesheetTable"

interface Mine { employeeId: string; clockedIn: boolean; since: string | null; scheduleName: string; recent: Timesheet }
interface RegisterRow {
  employeeId: string; employeeNumber: string; employeeName: string; department: string | null
  presentDays: number; lateDays: number; absentDays: number; leaveDays: number; workedMinutes: number; overtimeMinutes: number; flagged: number
}
interface OvertimeResult { created: number; updated: number; withdrawn: number; skipped: { date: string; reason: string }[] }

type Section = "mine" | "register" | "overtime" | "schedules"

export default function AttendanceTab() {
  const { user } = useAuth() as { user?: { userId?: string; permissions?: string[] } }
  const rights = attendanceRights(user?.permissions ?? [])
  const [section, setSection] = useState<Section>("mine")

  const tabs: { id: Section; label: string; show: boolean }[] = [
    { id: "mine", label: "My time", show: true },
    { id: "register", label: "Register", show: rights.register },
    { id: "overtime", label: "Overtime", show: true },
    { id: "schedules", label: "Schedules", show: true },
  ]

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 18, flexWrap: "wrap" }} role="tablist">
        {tabs.filter(t => t.show).map(t => (
          <button key={t.id} role="tab" aria-selected={section === t.id} onClick={() => setSection(t.id)}
            style={{ padding: "6px 14px", borderRadius: 20, fontSize: 13, cursor: "pointer", border: "none", fontWeight: section === t.id ? 600 : 400,
              background: section === t.id ? "var(--hf-primary)" : "var(--hf-surface-sunken)", color: section === t.id ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
            {t.label}
          </button>
        ))}
      </div>
      {section === "mine" && <MyTime />}
      {section === "register" && rights.register && <Register />}
      {section === "overtime" && <OvertimePanel everyone={rights.decide || rights.register} />}
      {section === "schedules" && <SchedulesPanel manage={rights.manage} />}
    </div>
  )
}

function MyTime() {
  const qc = useQueryClient()
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const me = useQuery({ queryKey: ["hr-attendance", "me"], queryFn: async () => unwrap<Mine>(await apiClient.get("/api/v1/hr/attendance/me")), retry: false })

  const clock = useMutation({
    mutationFn: async (dir: "in" | "out") => apiClient.post(`/api/v1/hr/attendance/clock-${dir}`),
    onSuccess: () => { setError(null); qc.invalidateQueries({ queryKey: ["hr-attendance"] }) },
    onError: (e: unknown) => setError(errorText(e, "That did not work.")),
  })

  const claim = useMutation({
    mutationFn: async (m: Mine) => unwrap<OvertimeResult>(await apiClient.post("/api/v1/hr/overtime/request", { employeeId: m.employeeId, from: m.recent.from, to: m.recent.to })),
    onSuccess: r => {
      setError(null)
      const parts = [`${r.created} claim(s) made`, r.updated ? `${r.updated} updated` : "", r.withdrawn ? `${r.withdrawn} taken back` : ""].filter(Boolean)
      setMessage(parts.join(", ") + (r.skipped.length ? `. ${r.skipped.length} day(s) cannot be claimed: ${r.skipped[0].reason}` : "."))
      qc.invalidateQueries({ queryKey: ["hr-attendance"] }); qc.invalidateQueries({ queryKey: ["hr-overtime"] })
    },
    onError: (e: unknown) => setError(errorText(e, "That did not work.")),
  })

  if (me.isLoading) return <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>
  if (me.isError) return <Notice kind="error">{errorText(me.error, "Your attendance could not be loaded.")}</Notice>
  const m = me.data!
  const claimable = claimableDays(m.recent.days, m.recent.aboveThreshold, isoDate(new Date()))

  return (
    <div>
      {error && <Notice kind="error">{error}</Notice>}
      {message && <Notice kind="ok">{message}</Notice>}
      <div style={{ ...card, padding: "16px 18px", display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
        <div>
          <div style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>{m.clockedIn ? `Clocked in since ${fmtTime(m.since)}` : "Not clocked in"}</div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Schedule: {m.scheduleName}. Worked in the last two weeks: {fmtMinutes(m.recent.workedMinutes)}; late {m.recent.lateDays} day(s); absent {m.recent.absentDays}.</div>
        </div>
        {m.clockedIn
          ? <button style={primary} disabled={clock.isPending} onClick={() => clock.mutate("out")}>Clock out</button>
          : <button style={primary} disabled={clock.isPending} onClick={() => clock.mutate("in")}>Clock in</button>}
      </div>
      <div style={card}>
        <div style={{ padding: "12px 18px", display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 8 }}>
          <strong style={{ fontSize: 14 }}>Last two weeks</strong>
          {claimable.length > 0 && <button style={plain} disabled={claim.isPending} onClick={() => claim.mutate(m)}>Claim overtime ({claimable.length} day{claimable.length === 1 ? "" : "s"})</button>}
        </div>
        <div style={{ padding: "0 18px 14px" }}>
          <TimesheetTable ts={{ ...m.recent, canCorrect: false }} employeeId={m.employeeId} onChanged={() => qc.invalidateQueries({ queryKey: ["hr-attendance"] })} />
        </div>
      </div>
    </div>
  )
}

function Register() {
  const qc = useQueryClient()
  const initial = monthPeriod()
  const [from, setFrom] = useState(initial.from)
  const [to, setTo] = useState(initial.to)
  const [openId, setOpenId] = useState<string | null>(null)
  const problem = periodProblem(from, to)

  const rows = useQuery({
    queryKey: ["hr-attendance", "register", from, to],
    queryFn: async () => unwrap<RegisterRow[]>(await apiClient.get("/api/v1/hr/attendance/register", { params: { from, to } })),
    enabled: !problem,
  })
  const sheet = useQuery({
    queryKey: ["hr-attendance", "timesheet", openId, from, to],
    queryFn: async () => unwrap<Timesheet>(await apiClient.get("/api/v1/hr/attendance/timesheet", { params: { employeeId: openId, from, to } })),
    enabled: !!openId && !problem,
  })

  const quick = (n: number) => { const p = lastDays(n); setFrom(p.from); setTo(p.to) }

  return (
    <div>
      <div style={{ display: "flex", gap: 10, alignItems: "flex-end", flexWrap: "wrap", marginBottom: 14 }}>
        <div><label style={label} htmlFor="reg-from">From</label><input id="reg-from" type="date" style={{ ...input, width: 160 }} value={from} onChange={e => setFrom(e.target.value)} /></div>
        <div><label style={label} htmlFor="reg-to">To</label><input id="reg-to" type="date" style={{ ...input, width: 160 }} value={to} onChange={e => setTo(e.target.value)} /></div>
        <button style={plain} onClick={() => quick(7)}>Last 7 days</button>
        <button style={plain} onClick={() => quick(14)}>Last 14 days</button>
        <button style={plain} onClick={() => { const p = monthPeriod(); setFrom(p.from); setTo(p.to) }}>This month</button>
      </div>
      {problem && <Notice kind="error">{problem}</Notice>}
      {rows.isError && <Notice kind="error">{errorText(rows.error, "The register could not be loaded.")}</Notice>}
      {rows.data && (
        <div style={card}>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Employee</th><th style={th}>Present</th><th style={th}>Late</th><th style={th}>Absent</th><th style={th}>On leave</th><th style={th}>Worked</th><th style={th}>Overtime</th><th style={th}>Flags</th></tr></thead>
              <tbody>
                {rows.data.map(r => (
                  <tr key={r.employeeId} onClick={() => setOpenId(openId === r.employeeId ? null : r.employeeId)} style={{ cursor: "pointer", background: openId === r.employeeId ? "var(--hf-surface-sunken)" : undefined }}>
                    <td style={td}><button style={{ background: "none", border: "none", padding: 0, cursor: "pointer", textAlign: "left", font: "inherit", color: "var(--hf-text)" }} aria-expanded={openId === r.employeeId}>
                      {r.employeeName}</button> <span style={{ color: "var(--hf-text-muted)" }}>{r.employeeNumber}</span></td>
                    <td style={td}>{r.presentDays}</td><td style={td}>{r.lateDays}</td>
                    <td style={{ ...td, color: r.absentDays > 0 ? "var(--hf-danger-text)" : undefined }}>{r.absentDays}</td>
                    <td style={td}>{r.leaveDays}</td><td style={td}>{fmtMinutes(r.workedMinutes)}</td><td style={td}>{fmtMinutes(r.overtimeMinutes)}</td><td style={td}>{r.flagged || "–"}</td>
                  </tr>
                ))}
                {rows.data.length === 0 && <tr><td style={td} colSpan={8}>No one to show.</td></tr>}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {openId && sheet.data && (
        <div style={{ ...card, padding: "14px 18px" }}>
          <strong style={{ fontSize: 14 }}>{sheet.data.employeeName}</strong>
          <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}> · {sheet.data.scheduleName}</span>
          <div style={{ marginTop: 10 }}>
            <TimesheetTable ts={sheet.data} employeeId={openId} onChanged={() => qc.invalidateQueries({ queryKey: ["hr-attendance"] })} />
          </div>
        </div>
      )}
      {openId && sheet.isError && <Notice kind="error">{errorText(sheet.error, "That timesheet could not be loaded.")}</Notice>}
    </div>
  )
}
