// Workplace placements and logbooks for one enrolment. Loads only when opened.
// Hours are a record of time spent. They are not a competency decision. The mentor's confirmation is recorded by staff.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  STATUSES, WORKPLACE_NOTE, confirmProblem, dayProblem, emptyLog, emptyPlacement, enrolmentOpen, label, logBody, logLine, logProblem, placementBody, placementForm,
  placementProblem, reasonProblem, statusTone, type LogForm, type LogEntry, type Placement, type PlacementDetail, type PlacementForm,
} from "./workplaceRules"
import { Badge, Notice } from "./tpUi"
import { card, errText, fmtDate, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { WPL } from "./tpApi"

function PlacementFormPanel({ enrolmentId, enrolmentStart, initial, onSaved, onCancel }: { enrolmentId: string; enrolmentStart: string | null; initial?: Placement; onSaved: () => void; onCancel: () => void }) {
  const [f, setF] = useState<PlacementForm>(initial ? placementForm(initial) : emptyPlacement)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const set = (k: keyof PlacementForm) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value })
  const save = async () => {
    const p = placementProblem(f, enrolmentStart); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      await (initial ? apiClient.put(`${WPL}/workplace-placements/${initial.id}`, placementBody(f)) : apiClient.post(`${WPL}/workplace-placements`, { enrolmentId, ...placementBody(f) }))
      onSaved()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        <input aria-label="Host workplace" style={input} placeholder="Host workplace" value={f.hostName} onChange={set("hostName")} />
        <input aria-label="Host contact" style={input} placeholder="Host contact (optional)" value={f.hostContact} onChange={set("hostContact")} />
        <input aria-label="Mentor name" style={input} placeholder="Workplace mentor" value={f.mentorName} onChange={set("mentorName")} />
        <input aria-label="Mentor contact" style={input} placeholder="Mentor contact (optional)" value={f.mentorContact} onChange={set("mentorContact")} />
        <input aria-label="Planned start" type="date" style={input} value={f.startDate} onChange={set("startDate")} />
        <input aria-label="Planned end" type="date" style={input} value={f.endDate} onChange={set("endDate")} />
      </div>
      <input aria-label="Agreement reference" style={input} placeholder="Reference of the agreement with the host (optional)" value={f.agreementRef} onChange={set("agreementRef")} />
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>{initial ? "Save changes" : "Add placement"}</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function PlacementCard({ p, enrolmentStart, onChanged }: { p: Placement; enrolmentStart: string | null; onChanged: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<"" | "edit" | "start" | "complete" | "cancel" | "log" | { confirm: string } | { void: string }>("")
  const [day, setDay] = useState(todayIso())
  const [text, setText] = useState("")
  const [log, setLog] = useState<LogForm>(emptyLog(todayIso()))
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: detail } = useQuery<PlacementDetail>({
    queryKey: ["tp-placement", p.id], enabled: open,
    queryFn: async () => unwrap<PlacementDetail>(await apiClient.get(`${WPL}/workplace-placements/${p.id}`)),
  })
  const reset = () => { setMode(""); setText(""); setError(""); setDay(todayIso()); setLog(emptyLog(todayIso())) }
  const refresh = () => { void qc.invalidateQueries({ queryKey: ["tp-placement", p.id] }); onChanged() }
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); reset(); refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const dayAction = (kind: "start" | "complete") => {
    const earliest = kind === "complete" ? p.startedOn : null
    const pr = dayProblem(day, earliest, todayIso(), kind === "start" ? "it started" : "it ended"); if (pr) { setError(pr); return }
    void run(() => apiClient.post(`${WPL}/workplace-placements/${p.id}/${kind}`, { on: day }))
  }
  const cancel = () => {
    const pr = reasonProblem(text); if (pr) { setError(pr); return }
    void run(() => apiClient.post(`${WPL}/workplace-placements/${p.id}/cancel`, { reason: text.trim() }))
  }
  const addLog = () => {
    const pr = logProblem(log, p.startedOn, p.endDate, todayIso()); if (pr) { setError(pr); return }
    void run(() => apiClient.post(`${WPL}/workplace-placements/${p.id}/logs`, logBody(log)))
  }
  const confirm = (l: LogEntry) => {
    const pr = confirmProblem(day, l.logDate, todayIso(), text); if (pr) { setError(pr); return }
    void run(() => apiClient.post(`${WPL}/workplace-logs/${l.id}/confirm`, { confirmedOn: day, mentorName: text.trim() }))
  }
  const voidLog = (l: LogEntry) => {
    const pr = reasonProblem(text); if (pr) { setError(pr); return }
    void run(() => apiClient.post(`${WPL}/workplace-logs/${l.id}/void`, { reason: text.trim() }))
  }
  if (mode === "edit") return <PlacementFormPanel enrolmentId={p.enrolmentId} enrolmentStart={enrolmentStart} initial={p} onCancel={reset} onSaved={() => { reset(); refresh() }} />
  const live = p.status === "PLANNED" || p.status === "ACTIVE"
  const target = (m: typeof mode, kind: "confirm" | "void", id: string) => typeof m === "object" && kind in m && (m as Record<string, string>)[kind] === id
  return (
    <div style={{ ...card, display: "grid", gap: 6 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div>
          <strong>{p.hostName}</strong>
          <div style={muted}>{p.placementNo} · mentor {p.mentorName} · planned {fmtDate(p.startDate)}{p.endDate ? ` to ${fmtDate(p.endDate)}` : ""}</div>
          {p.agreementRef && <div style={muted}>Agreement: {p.agreementRef}</div>}
        </div>
        <Badge text={label(STATUSES, p.status)} tone={statusTone(p.status)} />
      </div>
      <div style={muted}>{p.summary}</div>
      {p.cancelReason && <div style={muted}>Cancelled: {p.cancelReason}</div>}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <button style={ghost} onClick={() => setOpen(!open)}>{open ? "Hide logbook" : "Logbook"}</button>
        {canManage && mode === "" && live && <button style={ghost} onClick={() => setMode("edit")}>Edit</button>}
        {canManage && mode === "" && p.status === "PLANNED" && <button style={ghost} onClick={() => setMode("start")}>Start placement</button>}
        {canManage && mode === "" && p.status === "ACTIVE" && <button style={ghost} onClick={() => setMode("complete")}>Complete placement</button>}
        {canManage && mode === "" && live && <button style={ghost} onClick={() => setMode("cancel")}>Cancel placement</button>}
        {canManage && mode === "" && p.status === "ACTIVE" && open && <button style={ghost} onClick={() => setMode("log")}>Add an entry</button>}
      </div>
      {(mode === "start" || mode === "complete") && (
        <div style={{ display: "grid", gap: 6 }}>
          <input aria-label={mode === "start" ? "Day started" : "Day ended"} type="date" style={input} value={day} onChange={e => setDay(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => dayAction(mode)}>{mode === "start" ? "Save as started" : "Save as completed"}</button><button style={ghost} onClick={reset}>Cancel</button></div>
        </div>
      )}
      {mode === "cancel" && (
        <div style={{ display: "grid", gap: 6 }}>
          <input aria-label="Reason for cancelling the placement" style={input} placeholder="Why is this placement being cancelled?" value={text} onChange={e => setText(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={cancel}>Cancel the placement</button><button style={ghost} onClick={reset}>Back</button></div>
        </div>
      )}
      {mode === "log" && (
        <div style={{ display: "grid", gap: 6 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            <input aria-label="Entry day" type="date" style={input} value={log.logDate} onChange={e => setLog({ ...log, logDate: e.target.value })} />
            <input aria-label="Hours" style={input} inputMode="decimal" placeholder="Hours" value={log.hours} onChange={e => setLog({ ...log, hours: e.target.value })} />
          </div>
          <textarea aria-label="What was done" style={input} placeholder="What was done" value={log.activity} onChange={e => setLog({ ...log, activity: e.target.value })} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={addLog}>Save the entry</button><button style={ghost} onClick={reset}>Cancel</button></div>
        </div>
      )}
      {open && (
        <div style={{ display: "grid", gap: 6 }}>
          <div style={muted}>{WORKPLACE_NOTE}</div>
          {!detail ? <div style={muted}>Loading…</div> : detail.logs.length === 0 ? <div style={muted}>No entries yet.</div> : detail.logs.map(l => (
            <div key={l.id} style={{ display: "grid", gap: 4 }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
                <span>{fmtDate(l.logDate)} · {l.hours} {Number(l.hours) === 1 ? "hour" : "hours"}</span>
                <span style={muted}>{logLine(l)}</span>
              </div>
              <div style={muted}>{l.activity}</div>
              {canManage && l.status === "RECORDED" && mode === "" && (
                <div style={{ display: "flex", gap: 8 }}>
                  {!l.confirmedOn && <button style={ghost} onClick={() => { setMode({ confirm: l.id }); setDay(todayIso()); setText("") }}>Record mentor confirmation</button>}
                  <button style={ghost} onClick={() => { setMode({ void: l.id }); setText("") }}>Void entry</button>
                </div>
              )}
              {target(mode, "confirm", l.id) && (
                <div style={{ display: "grid", gap: 6 }}>
                  <input aria-label="Mentor name for confirmation" style={input} placeholder="Name of the mentor who confirmed it" value={text} onChange={e => setText(e.target.value)} />
                  <input aria-label="Day confirmed" type="date" style={input} value={day} onChange={e => setDay(e.target.value)} />
                  <Notice text={error} />
                  <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => confirm(l)}>Save confirmation</button><button style={ghost} onClick={reset}>Cancel</button></div>
                </div>
              )}
              {target(mode, "void", l.id) && (
                <div style={{ display: "grid", gap: 6 }}>
                  <input aria-label="Reason for voiding the entry" style={input} placeholder="Why is this entry being voided?" value={text} onChange={e => setText(e.target.value)} />
                  <Notice text={error} />
                  <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => voidLog(l)}>Void the entry</button><button style={ghost} onClick={reset}>Cancel</button></div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default function TrainProvWorkplacePanel({ enrolmentId, enrolmentStatus, startDate }: { enrolmentId: string; enrolmentStatus: string; startDate: string | null }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [open, setOpen] = useState(false)
  const [adding, setAdding] = useState(false)
  const { data: placements = [] } = useQuery<Placement[]>({
    queryKey: ["tp-placements", enrolmentId], enabled: open,
    queryFn: async () => unwrap<Placement[]>(await apiClient.get(`${WPL}/workplace-placements`, { params: { enrolmentId } })),
  })
  if (!open) return <div style={{ marginTop: 8 }}><button style={ghost} onClick={() => setOpen(true)}>Workplace</button></div>
  const changed = () => { void qc.invalidateQueries({ queryKey: ["tp-placements", enrolmentId] }) }
  return (
    <div style={{ ...card, marginTop: 8, display: "grid", gap: 8 }}>
      <div style={muted}>{WORKPLACE_NOTE}</div>
      {placements.length === 0 && <div style={muted}>No placements yet.</div>}
      {placements.map(p => <PlacementCard key={p.id} p={p} enrolmentStart={startDate} onChanged={changed} />)}
      {adding && <PlacementFormPanel enrolmentId={enrolmentId} enrolmentStart={startDate} onCancel={() => setAdding(false)} onSaved={() => { setAdding(false); changed() }} />}
      {canManage && !adding && (enrolmentOpen(enrolmentStatus)
        ? <div><button style={solid} onClick={() => setAdding(true)}>Add a placement</button></div>
        : <div style={muted}>Placements can be added while the enrolment is enrolled or in progress.</div>)}
      <div><button style={ghost} onClick={() => setOpen(false)}>Hide</button></div>
    </div>
  )
}
