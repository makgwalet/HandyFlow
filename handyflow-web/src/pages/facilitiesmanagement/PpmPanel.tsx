// An asset's planned maintenance: the tasks that come round on a schedule and raise a work order when due.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, ErrorLine, Input, Pick, Pill, card, day, errorText, fail, muted, td, th } from "./kit"
import { BASE } from "./lookups"
import { FREQUENCIES, emptyPpm, everyText, ppmBody, ppmDueNote, ppmProblem, type PpmForm } from "./fm.logic"

interface Schedule { id: string; assetId: string; taskName: string; description: string | null; frequencyDays: number; nextDueDate: string | null; lastCompletedDate: string | null; active: boolean }

export default function PpmPanel({ assetId, canWrite }: { assetId: string; canWrite: boolean }) {
  const qc = useQueryClient(), key = ["fm-ppm", assetId]
  const [editing, setEditing] = useState<Schedule | "new" | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: key, queryFn: async () => (await apiClient.get<Schedule[]>(`${BASE}/assets/${assetId}/ppm-schedules`)).data })
  const rows = Array.isArray(data) ? data : []
  const toggle = useMutation({
    mutationFn: (s: Schedule) => apiClient.post(`${BASE}/ppm-schedules/${s.id}/${s.active ? "deactivate" : "reactivate"}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: key }), onError: e => { void fail(e, "That did not work") },
  })
  const now = new Date()
  return (
    <div style={{ marginTop: 14 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", margin: "0 0 6px" }}>
        <div style={{ fontSize: 13, fontWeight: 700 }}>Planned maintenance</div>
        {canWrite && <Btn small onClick={() => setEditing("new")}>Add a task</Btn>}
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <div style={{ ...muted, padding: 14 }}>Could not load the schedules.</div> : isLoading ? <div style={{ ...muted, padding: 14 }}>Loading…</div> : rows.length === 0 ? <div style={{ ...muted, padding: 14 }}>No planned maintenance yet.</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Task</th><th style={th}>Every</th><th style={th}>Next due</th><th style={th}>Last done</th><th /></tr></thead>
            <tbody>{rows.map(s => {
              const n = ppmDueNote(s.active, s.nextDueDate, now)
              return (
                <tr key={s.id} style={{ opacity: s.active ? 1 : 0.6 }}>
                  <td style={td}><b>{s.taskName}</b>{!s.active && <> <Pill>Stopped</Pill></>}{s.description && <div style={muted}>{s.description}</div>}</td>
                  <td style={td}>{everyText(s.frequencyDays)}</td>
                  <td style={td}>{s.active ? day(s.nextDueDate) : "—"}{n && <div><Pill tone={n.tone}>{n.text}</Pill></div>}</td>
                  <td style={td}>{day(s.lastCompletedDate)}</td>
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canWrite && <><Btn small onClick={() => setEditing(s)}>Edit</Btn>{" "}<Btn small disabled={toggle.isPending} onClick={() => toggle.mutate(s)}>{s.active ? "Stop" : "Restart"}</Btn></>}</td>
                </tr>)})}</tbody>
          </table>)}
      </div>
      {editing && <PpmDialog assetId={assetId} schedule={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={() => qc.invalidateQueries({ queryKey: key })} />}
    </div>
  )
}

function PpmDialog({ assetId, schedule, onClose, onSaved }: { assetId: string; schedule: Schedule | null; onClose: () => void; onSaved: () => void }) {
  const creating = !schedule
  const [f, setF] = useState<PpmForm>(() => schedule ? { taskName: schedule.taskName, description: schedule.description ?? "", frequencyDays: String(schedule.frequencyDays), startDate: "" } : emptyPpm())
  const [err, setErr] = useState("")
  const set = (k: keyof PpmForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = ppmProblem(f)
  const save = useMutation({
    mutationFn: () => schedule ? apiClient.put(`${BASE}/ppm-schedules/${schedule.id}`, ppmBody(f, false)) : apiClient.post(`${BASE}/ppm-schedules`, ppmBody(f, true, assetId)),
    onSuccess: () => { toast.success(schedule ? "Task saved" : "Task added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the task")),
  })
  const preset = FREQUENCIES.some(([d]) => d === f.frequencyDays) ? f.frequencyDays : "custom"
  return (
    <Dialog title={schedule ? `Edit ${schedule.taskName}` : "Add a planned task"} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <Input label="Task" value={f.taskName} onChange={set("taskName")} autoFocus maxLength={255} />
      <Input label="Details" value={f.description} onChange={set("description")} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Pick label="How often" value={preset} onChange={v => v !== "custom" && set("frequencyDays")(v)} options={[...FREQUENCIES, ["custom", "Other (set days)"] as const]} />
        <Input label="Days between visits" type="number" min="1" step="1" value={f.frequencyDays} onChange={set("frequencyDays")} />
        {creating && <Input label="First visit" type="date" value={f.startDate} onChange={set("startDate")} hint="Leave empty to start one interval from today" />}
      </div>
      {problem && f.taskName.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
