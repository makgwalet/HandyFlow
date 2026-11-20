// src/pages/legalcompliance/ReportSchedulesPanel.tsx
//
// Scheduled report delivery. Endpoints (/api/v1/legalcompliance/report-schedules): GET / · POST / · PUT /{id} · POST /{id}/run-now · DELETE /{id}
// A schedule tells chosen users, weekly / monthly / quarterly, that a report is ready. It sends a notice with a row count and a link, never the report's data.
// The report and who may see it are fixed when the schedule is made; to change either, make a new schedule.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { unwrap, one, input, primaryBtn, ghostBtn, fmtDate, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { Plus, Send, Pause, Play, Trash2, Pencil } from "lucide-react"

const base = "/api/v1/legalcompliance/report-schedules"

type Frequency = "WEEKLY" | "MONTHLY" | "QUARTERLY"
interface Recipient { userId: string; name: string }
interface Schedule {
  id: string; name: string; reportKey: string; reportTitle: string; frequency: Frequency; runDay: number; audience: string; recipients: Recipient[]; active: boolean
  nextRunOn: string; lastRunOn: string | null; lastStatus: "NEVER" | "OK" | "FAILED"; lastRowCount: number | null; lastError: string | null; createdByName: string | null
}
interface ReportOption { key: string; title: string }
interface UserOption { id: string; firstName: string; lastName: string }

const WEEKDAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
const FREQ_LABEL: Record<Frequency, string> = { WEEKLY: "Weekly", MONTHLY: "Monthly", QUARTERLY: "Quarterly" }

function when(s: Pick<Schedule, "frequency" | "runDay">) {
  if (s.frequency === "WEEKLY") return `Every ${WEEKDAYS[s.runDay - 1] ?? "week"}`
  const day = `${s.runDay}${s.runDay % 10 === 1 && s.runDay !== 11 ? "st" : s.runDay % 10 === 2 && s.runDay !== 12 ? "nd" : s.runDay % 10 === 3 && s.runDay !== 13 ? "rd" : "th"}`
  return s.frequency === "MONTHLY" ? `Monthly on the ${day}` : `Quarterly on the ${day} (Jan, Apr, Jul, Oct)`
}

export default function ReportSchedulesPanel({ reports }: { reports: ReportOption[] }) {
  const qc = useQueryClient()
  const [editing, setEditing] = useState<Schedule | "new" | null>(null)
  const [deleting, setDeleting] = useState<Schedule | null>(null)
  const [err, setErr] = useState("")

  const { data: schedules = [], isLoading } = useQuery<Schedule[]>({ queryKey: ["lc-report-schedules"], queryFn: async () => unwrap(await apiClient.get(base)) as Schedule[] })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-report-schedules"] }); setErr("") }
  const fail = (e: unknown) => setErr(errMsg(e, "That did not work."))

  const toggle = useMutation({
    mutationFn: (s: Schedule) => apiClient.put(`${base}/${s.id}`, { name: s.name, frequency: s.frequency, runDay: s.runDay, recipientIds: s.recipients.map(r => r.userId), active: !s.active }),
    onSuccess: refresh, onError: fail,
  })
  const sendNow = useMutation({ mutationFn: (s: Schedule) => apiClient.post(`${base}/${s.id}/run-now`), onSuccess: refresh, onError: fail })
  const remove = useMutation({ mutationFn: (s: Schedule) => apiClient.delete(`${base}/${s.id}`), onSuccess: () => { refresh(); setDeleting(null) }, onError: (e: unknown) => { fail(e); setDeleting(null) } })

  return (
    <div style={{ marginTop: 28, borderTop: "1px solid #E2E8F0", paddingTop: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, marginBottom: 6, flexWrap: "wrap" }}>
        <h3 style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>Scheduled delivery</h3>
        <button style={{ ...primaryBtn, padding: "7px 12px", fontSize: 13, display: "flex", alignItems: "center", gap: 6 }} onClick={() => setEditing("new")}><Plus size={13} />New schedule</button>
      </div>
      <p style={{ fontSize: 12, color: "#64748B", margin: "0 0 12px" }}>
        On the day, the report is run and the people you choose are told it is ready, in the app and by email. The notice shows the row count and a link; the report itself stays behind sign-in.
        People who have left are dropped automatically.
      </p>
      {err && <Err m={err} />}
      {isLoading && <p style={{ color: "#64748B" }}>Loading…</p>}
      {!isLoading && schedules.length === 0 && <p style={{ color: "#64748B" }}>No schedules yet.</p>}

      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {schedules.map(s => (
          <div key={s.id} style={{ border: "1px solid #E2E8F0", borderRadius: 10, padding: 12, opacity: s.active ? 1 : 0.65 }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{s.name}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{s.reportTitle} · {when(s)} · next {s.active ? fmtDate(s.nextRunOn) : "paused"}</div>
                <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>To: {s.recipients.map(r => r.name).join(", ")}</div>
              </div>
              <div style={{ display: "flex", gap: 6, alignItems: "flex-start", flexWrap: "wrap" }}>
                {!s.active && <Badge text="Paused" color="#475569" bg="#E2E8F0" />}
                {s.audience === "ADMIN" && <Badge text="Administrators only" color="#3730A3" bg="#E0E7FF" />}
                {s.lastStatus === "OK" && <Badge text={`Last sent ${fmtDate(s.lastRunOn)} · ${s.lastRowCount ?? 0} rows`} color="#166534" bg="#DCFCE7" />}
                {s.lastStatus === "FAILED" && <Badge text={`Failed ${fmtDate(s.lastRunOn)}`} color="#991B1B" bg="#FEE2E2" />}
              </div>
            </div>
            {s.lastStatus === "FAILED" && s.lastError && <p style={{ fontSize: 12, color: "#991B1B", margin: "6px 0 0" }}>{s.lastError}</p>}
            <div style={{ display: "flex", gap: 8, marginTop: 10, flexWrap: "wrap" }}>
              <button style={{ ...ghostBtn, padding: "6px 10px", display: "flex", alignItems: "center", gap: 5 }} disabled={sendNow.isPending} onClick={() => sendNow.mutate(s)}><Send size={13} />Send now</button>
              <button style={{ ...ghostBtn, padding: "6px 10px", display: "flex", alignItems: "center", gap: 5 }} onClick={() => setEditing(s)}><Pencil size={13} />Edit</button>
              <button style={{ ...ghostBtn, padding: "6px 10px", display: "flex", alignItems: "center", gap: 5 }} disabled={toggle.isPending} onClick={() => toggle.mutate(s)}>
                {s.active ? <><Pause size={13} />Pause</> : <><Play size={13} />Resume</>}
              </button>
              <button style={{ ...ghostBtn, padding: "6px 10px", display: "flex", alignItems: "center", gap: 5 }} onClick={() => setDeleting(s)}><Trash2 size={13} />Delete</button>
            </div>
          </div>
        ))}
      </div>

      {editing && <ScheduleModal existing={editing === "new" ? null : editing} reports={reports} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); refresh() }} />}
      {deleting && (
        <Modal title="Delete schedule" onClose={() => setDeleting(null)}>
          <p style={{ fontSize: 14, color: "#334155" }}>Delete “{deleting.name}”? Nobody will be notified about it again. This is recorded in the audit trail.</p>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <button style={ghostBtn} onClick={() => setDeleting(null)}>Keep it</button>
            <button style={primaryBtn} disabled={remove.isPending} onClick={() => remove.mutate(deleting)}>Delete</button>
          </div>
        </Modal>
      )}
    </div>
  )
}

function ScheduleModal({ existing, reports, onClose, onSaved }: { existing: Schedule | null; reports: ReportOption[]; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(existing?.name ?? "")
  const [reportKey, setReportKey] = useState(existing?.reportKey ?? "")
  const [frequency, setFrequency] = useState<Frequency>(existing?.frequency ?? "MONTHLY")
  const [runDay, setRunDay] = useState(existing?.runDay ?? 1)
  const [picked, setPicked] = useState<string[]>(existing?.recipients.map(r => r.userId) ?? [])
  const [err, setErr] = useState("")

  const { data: usersRaw, isError: usersFailed } = useQuery<UserOption[] | { content: UserOption[] }>({
    queryKey: ["lc-report-schedules", "users"], queryFn: async () => (await apiClient.get("/api/v1/identity/users?size=200")).data,
  })
  const users: UserOption[] = Array.isArray(usersRaw) ? usersRaw : usersRaw?.content ?? []

  const save = useMutation({
    mutationFn: async () => {
      const body = { name: name.trim(), frequency, runDay, recipientIds: picked }
      return existing ? one(await apiClient.put(`${base}/${existing.id}`, { ...body, active: existing.active })) : one(await apiClient.post(base, { ...body, reportKey }))
    },
    onSuccess: onSaved, onError: (e: unknown) => setErr(errMsg(e, "Could not save the schedule.")),
  })

  const changeFrequency = (f: Frequency) => { setFrequency(f); setRunDay(f === "WEEKLY" ? 1 : Math.min(runDay, 28) || 1) }
  const toggleUser = (id: string) => setPicked(p => p.includes(id) ? p.filter(x => x !== id) : [...p, id])
  const ready = name.trim() !== "" && (existing !== null || reportKey !== "") && picked.length > 0

  return (
    <Modal title={existing ? "Edit schedule" : "New schedule"} onClose={onClose}>
      {err && <Err m={err} />}
      <Field l="Name"><input style={input} value={name} maxLength={255} onChange={e => setName(e.target.value)} placeholder="e.g. Monthly risk register" /></Field>
      <Field l="Report">
        {existing ? <div style={{ fontSize: 14, color: "#334155" }}>{existing.reportTitle} <span style={{ fontSize: 12, color: "#64748B" }}>(cannot be changed — make a new schedule)</span></div> : (
          <select style={input} value={reportKey} onChange={e => setReportKey(e.target.value)}>
            <option value="">Choose a report…</option>
            {reports.map(r => <option key={r.key} value={r.key}>{r.title}</option>)}
          </select>
        )}
      </Field>
      <div style={{ display: "flex", gap: 10 }}>
        <div style={{ flex: 1 }}>
          <Field l="How often">
            <select style={input} value={frequency} onChange={e => changeFrequency(e.target.value as Frequency)}>
              {(Object.keys(FREQ_LABEL) as Frequency[]).map(f => <option key={f} value={f}>{FREQ_LABEL[f]}</option>)}
            </select>
          </Field>
        </div>
        <div style={{ flex: 1 }}>
          <Field l={frequency === "WEEKLY" ? "On" : "Day of the month"}>
            {frequency === "WEEKLY" ? (
              <select style={input} value={runDay} onChange={e => setRunDay(Number(e.target.value))}>{WEEKDAYS.map((d, i) => <option key={d} value={i + 1}>{d}</option>)}</select>
            ) : (
              <select style={input} value={runDay} onChange={e => setRunDay(Number(e.target.value))}>{Array.from({ length: 28 }, (_, i) => i + 1).map(d => <option key={d} value={d}>{d}</option>)}</select>
            )}
          </Field>
        </div>
      </div>
      {frequency === "QUARTERLY" && <p style={{ fontSize: 12, color: "#64748B", margin: "0 0 8px" }}>Runs in January, April, July and October.</p>}
      <Field l={`Tell these people (${picked.length} chosen, up to 25)`}>
        {usersFailed && <p style={{ fontSize: 12, color: "#991B1B" }}>Could not load the list of users.</p>}
        <div style={{ maxHeight: 180, overflowY: "auto", border: "1px solid #E2E8F0", borderRadius: 8, padding: 6 }}>
          {users.map(u => (
            <label key={u.id} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, padding: "3px 4px", cursor: "pointer" }}>
              <input type="checkbox" checked={picked.includes(u.id)} disabled={!picked.includes(u.id) && picked.length >= 25} onChange={() => toggleUser(u.id)} />
              {u.firstName} {u.lastName}
            </label>
          ))}
          {users.length === 0 && !usersFailed && <span style={{ fontSize: 12, color: "#64748B" }}>No users found.</span>}
        </div>
      </Field>
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 12 }}>
        <button style={ghostBtn} onClick={onClose}>Cancel</button>
        <button style={primaryBtn} disabled={!ready || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{existing ? "Save" : "Create schedule"}</button>
      </div>
    </Modal>
  )
}
