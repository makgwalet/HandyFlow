// The days of one employee, with the entries behind them and (for those allowed) the controls to add, correct or void an entry.
import { useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { CLAIM_LABEL, entryFormProblem, fmtMinutes, fmtTime, KIND_LABEL, reasonProblem, sast, toInstant } from "./attendance.logic"
import { errorText, input, label, plain, primary, td, th, type DayView, type EntryView, type Timesheet } from "./attendance.shared"
import { Chip, Notice, StatusChip } from "./AttendanceParts"

type Mode = { type: "add"; date: string } | { type: "correct"; date: string; entry: EntryView } | { type: "void"; date: string; entry: EntryView }

const WEEKDAY = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
const dayName = (iso: string) => {
  const [y, m, d] = iso.split("-").map(Number)
  return `${WEEKDAY[new Date(y, m - 1, d).getDay()]} ${iso.slice(5)}`
}

export default function TimesheetTable({ ts, employeeId, onChanged }: { ts: Timesheet; employeeId: string; onChanged: () => void }) {
  const [mode, setMode] = useState<Mode | null>(null)
  const [timeIn, setTimeIn] = useState("")
  const [timeOut, setTimeOut] = useState("")
  const [date, setDate] = useState("")
  const [reason, setReason] = useState("")
  const [problem, setProblem] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: async (call: () => Promise<unknown>) => call(),
    onSuccess: () => { setMode(null); setReason(""); setProblem(null); setError(null); onChanged() },
    onError: (e: unknown) => setError(errorText(e, "That did not work.")),
  })

  const open = (m: Mode) => {
    setMode(m); setReason(""); setProblem(null); setError(null)
    if (m.type === "add") { setDate(m.date); setTimeIn("08:00"); setTimeOut("17:00") }
    else { setDate(sast(m.entry.clockIn).date); setTimeIn(sast(m.entry.clockIn).time); setTimeOut(m.entry.clockOut ? sast(m.entry.clockOut).time : "") }
  }

  const submit = () => {
    if (!mode) return
    if (mode.type === "void") {
      const p = reasonProblem(reason, "for voiding this entry"); setProblem(p)
      if (!p) save.mutate(() => apiClient.post(`/api/v1/hr/attendance/entries/${mode.entry.id}/void`, { reason: reason.trim() }))
      return
    }
    const p = entryFormProblem({ date, timeIn, timeOut, reason }); setProblem(p)
    if (p) return
    const body = { clockIn: toInstant(date, timeIn), clockOut: toInstant(date, timeOut), reason: reason.trim() }
    if (mode.type === "add") save.mutate(() => apiClient.post("/api/v1/hr/attendance/entries", { ...body, employeeId }))
    else save.mutate(() => apiClient.put(`/api/v1/hr/attendance/entries/${mode.entry.id}`, body))
  }

  return (
    <div>
      {ts.aboveThreshold && (
        <Notice kind="error">This employee earns above the BCEA earnings threshold, so the Act's overtime rates and limits do not apply automatically and no overtime claim is made. Overtime is paid as their contract says.</Notice>
      )}
      {ts.weekFlags.map(f => <div key={f} style={{ fontSize: 12, color: "var(--hf-warning-text-strong)", marginBottom: 6 }}>{f}</div>)}
      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Day</th><th style={th}>Status</th><th style={th}>Times</th><th style={th}>Worked</th><th style={th}>Overtime</th><th style={th}>Notes</th>{ts.canCorrect && <th style={th} />}</tr></thead>
          <tbody>
            {ts.days.map((d: DayView) => (
              <tr key={d.date}>
                <td style={td}>{dayName(d.date)}</td>
                <td style={td}><StatusChip status={d.status} />{d.lateMinutes > 0 && <span style={{ fontSize: 11, color: "var(--hf-text-muted)" }}> {d.lateMinutes}m late</span>}</td>
                <td style={td}>
                  {d.entries.length === 0 ? "–" : d.entries.map(e => (
                    <div key={e.id}>
                      {fmtTime(e.clockIn)}–{e.clockOut ? fmtTime(e.clockOut) : "…"}
                      {e.corrected && <span style={{ fontSize: 11, color: "var(--hf-text-muted)" }} title={e.log.map(l => `${l.action}: ${l.reason}`).join("; ")}> edited</span>}
                      {ts.canCorrect && <>
                        {" "}<button style={{ ...plain, padding: "2px 8px" }} onClick={() => open({ type: "correct", date: d.date, entry: e })}>Correct</button>
                        {" "}<button style={{ ...plain, padding: "2px 8px" }} onClick={() => open({ type: "void", date: d.date, entry: e })}>Void</button>
                      </>}
                    </div>
                  ))}
                </td>
                <td style={td}>{fmtMinutes(d.workedMinutes)}{d.shortMinutes > 0 && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{fmtMinutes(d.shortMinutes)} short</div>}</td>
                <td style={td}>
                  {d.overtimeMinutes > 0 ? <>{fmtMinutes(d.overtimeMinutes)}<div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{d.overtimeKind ? KIND_LABEL[d.overtimeKind] : ""}</div></> : "–"}
                  {d.overtimeClaimStatus && <div><Chip text={CLAIM_LABEL[d.overtimeClaimStatus] ?? d.overtimeClaimStatus} color="var(--hf-text-secondary)" bg="var(--hf-surface-sunken)" /></div>}
                </td>
                <td style={{ ...td, fontSize: 12, color: "var(--hf-text-muted)" }}>{d.flags.map(f => <div key={f}>{f}</div>)}</td>
                {ts.canCorrect && <td style={td}><button style={plain} onClick={() => open({ type: "add", date: d.date })}>Add entry</button></td>}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {mode && (
        <div style={{ marginTop: 14, padding: 14, border: "1px solid var(--hf-border)", borderRadius: 10 }}>
          <strong style={{ fontSize: 13 }}>{mode.type === "add" ? "Add an entry" : mode.type === "correct" ? "Correct the times" : "Void this entry"}</strong>
          {error && <div style={{ marginTop: 8 }}><Notice kind="error">{error}</Notice></div>}
          {mode.type !== "void" && (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: 10, margin: "10px 0" }}>
              <div><label style={label} htmlFor="att-date">Date</label><input id="att-date" type="date" style={input} value={date} onChange={e => setDate(e.target.value)} /></div>
              <div><label style={label} htmlFor="att-in">Time in</label><input id="att-in" type="time" style={input} value={timeIn} onChange={e => setTimeIn(e.target.value)} /></div>
              <div><label style={label} htmlFor="att-out">Time out</label><input id="att-out" type="time" style={input} value={timeOut} onChange={e => setTimeOut(e.target.value)} /></div>
            </div>
          )}
          <label style={label} htmlFor="att-reason">Reason (kept in the log)</label>
          <input id="att-reason" style={input} value={reason} onChange={e => setReason(e.target.value)} />
          {problem && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 4 }}>{problem}</div>}
          <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
            <button style={primary} disabled={save.isPending} onClick={submit}>{mode.type === "void" ? "Void entry" : "Save"}</button>
            <button style={plain} onClick={() => setMode(null)}>Never mind</button>
          </div>
        </div>
      )}
    </div>
  )
}
