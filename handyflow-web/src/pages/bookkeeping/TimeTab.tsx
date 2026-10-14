// Staff time on a client: log it, see what is still to be billed, write it off.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { dateText, errorOf, money, rowsOf } from "./bookkeeping.logic"
import { ACTIVITIES, activityLabel, timeProblem, timeValue, unbilledTotal, type TimeEntry } from "./practice.logic"
import ClientScope from "./ClientScope"
import { btn, card, danger, field, muted, primary, right, td, th } from "./ui"

export default function TimeTab() { return <ClientScope>{id => <Time clientId={id} />}</ClientScope> }

function Time({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("BOOKKEEPING_MANAGE") || usePermission("BOOKKEEPING_ADMIN")
  const base = "/api/v1/bookkeeping"
  const today = new Date().toISOString().slice(0, 10)
  const [f, setF] = useState({ date: today, activity: "BOOKKEEPING", description: "", hours: "", rate: "", billable: true, who: "" })
  const [error, setError] = useState<string | null>(null)
  const { data: entries = [] } = useQuery<TimeEntry[]>({ queryKey: ["bk-time", clientId], queryFn: async () => rowsOf<TimeEntry>(await apiClient.get(`${base}/clients/${clientId}/time-entries?size=100&sort=entryDate,desc`)) })
  const { data: unbilled = [] } = useQuery<TimeEntry[]>({ queryKey: ["bk-unbilled", clientId], queryFn: async () => rowsOf<TimeEntry>(await apiClient.get(`${base}/clients/${clientId}/time-entries/unbilled`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["bk-time"] }); qc.invalidateQueries({ queryKey: ["bk-unbilled"] }) }
  const fail = (e: any) => setError(errorOf(e))
  const log = useMutation({
    mutationFn: () => apiClient.post(`${base}/time-entries`, { clientId, practitionerId: null, practitionerName: f.who.trim() || null, entryDate: f.date, activityType: f.activity, description: f.description.trim() || null, hours: Number(f.hours), hourlyRate: Number(f.rate), billable: f.billable }),
    onSuccess: () => { setError(null); setF({ ...f, description: "", hours: "" }); refresh() }, onError: fail,
  })
  const writeOff = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/time-entries/${id}/write-off`), onSuccess: refresh, onError: fail })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`${base}/time-entries/${id}`), onSuccess: refresh, onError: fail })
  const problem = timeProblem(f, today)
  const statusText = (t: TimeEntry) => t.invoiceId ? "Billed" : t.status === "WRITTEN_OFF" ? "Written off" : t.status === "NON_BILLABLE" || !t.billable ? "Not billable" : "To bill"
  return (
    <div>
      <div style={{ ...card, fontSize: 13 }}>Still to bill: <b>{money(unbilledTotal(unbilled))}</b> across {unbilled.length} {unbilled.length === 1 ? "entry" : "entries"}.</div>
      {canManage && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>Log time</h3>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            <input aria-label="Date" type="date" max={today} value={f.date} onChange={e => setF({ ...f, date: e.target.value })} style={field} />
            <select aria-label="Activity" value={f.activity} onChange={e => setF({ ...f, activity: e.target.value })} style={field}>{ACTIVITIES.map(a => <option key={a.value} value={a.value}>{a.label}</option>)}</select>
            <input aria-label="What was done" placeholder="What was done" value={f.description} onChange={e => setF({ ...f, description: e.target.value })} style={{ ...field, width: 240 }} />
            <input aria-label="Hours" placeholder="Hours" value={f.hours} onChange={e => setF({ ...f, hours: e.target.value })} style={{ ...field, width: 70 }} />
            <input aria-label="Hourly rate" placeholder="Rate per hour" value={f.rate} onChange={e => setF({ ...f, rate: e.target.value })} style={{ ...field, width: 110 }} />
            <input aria-label="Done by" placeholder="Done by" value={f.who} onChange={e => setF({ ...f, who: e.target.value })} style={{ ...field, width: 120 }} />
            <label style={muted}><input type="checkbox" aria-label="Billable" checked={f.billable} onChange={e => setF({ ...f, billable: e.target.checked })} /> Billable</label>
            {timeValue(f) > 0 && <span style={muted}>{money(timeValue(f))}</span>}
            <button style={primary} disabled={!!problem || log.isPending} onClick={() => log.mutate()}>Log</button>
          </div>
          {problem && f.hours && <div style={{ ...muted, marginTop: 6 }}>{problem}</div>}
        </div>
      )}
      {error && <div role="alert" style={{ ...danger, marginBottom: 8 }}>{error}</div>}
      <div style={card}>
        {entries.length === 0 && <p style={muted}>No time logged yet.</p>}
        {entries.length > 0 && (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={th}>Date</th><th style={th}>Who</th><th style={th}>Work</th><th style={{ ...th, ...right }}>Hours</th><th style={{ ...th, ...right }}>Value</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{entries.map(t => (
              <tr key={t.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={td}>{dateText(t.entryDate)}</td><td style={td}>{t.practitionerName || "—"}</td><td style={td}>{activityLabel(t.activityType)}{t.description ? ` — ${t.description}` : ""}</td>
                <td style={{ ...td, ...right }}>{t.hours}</td><td style={{ ...td, ...right }}>{money(t.lineTotal)}</td><td style={td}>{statusText(t)}</td>
                <td style={{ ...td, ...right }}>{canManage && !t.invoiceId && t.status !== "WRITTEN_OFF" && <>
                  <button style={btn} onClick={() => writeOff.mutate(t.id)}>Write off</button>{" "}
                  <button style={btn} onClick={() => { if (window.confirm("Delete this time entry?")) remove.mutate(t.id) }}>Delete</button></>}</td>
              </tr>))}</tbody>
          </table>
        )}
      </div>
    </div>
  )
}
