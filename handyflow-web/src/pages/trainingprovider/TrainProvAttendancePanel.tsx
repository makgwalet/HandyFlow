// Attendance register for one timetable slot, and the per-learner summary for a cohort.
// Attendance is a record of who was there. It is never shown as, or used as, a competence result.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  ATTENDANCE, ATTENDANCE_NOTE, attendanceTone, correctionBody, correctionProblem, label, rateText, registerBody, registerProblem,
  type CohortSummary, type HistoryLine, type Marks, type Register, type RegisterRow, type Slot,
} from "./cohortRules"
import { Badge, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, unwrap } from "./tpStyle"
import { LRN } from "./tpApi"

function RowHistory({ attendanceId }: { attendanceId: string }) {
  const { data = [] } = useQuery<HistoryLine[]>({ queryKey: ["tp-attendance-history", attendanceId], queryFn: async () => unwrap<HistoryLine[]>(await apiClient.get(`${LRN}/attendance/${attendanceId}/history`)) })
  return <div style={{ display: "grid", gap: 2 }}>{data.map(h => <div key={h.id} style={muted}>{h.actedAt.slice(0, 10)} · {h.action === "RECORDED" ? `recorded as ${label(ATTENDANCE, h.toStatus).toLowerCase()}` : `changed from ${label(ATTENDANCE, h.fromStatus).toLowerCase()} to ${label(ATTENDANCE, h.toStatus).toLowerCase()}: ${h.reason}`}</div>)}</div>
}

function RecordedRow({ row, canManage, onChanged }: { row: RegisterRow; canManage: boolean; onChanged: () => void }) {
  const [mode, setMode] = useState<"" | "correct" | "history">("")
  const [f, setF] = useState({ status: row.status ?? "PRESENT", note: row.note ?? "", reason: "" })
  const [error, setError] = useState("")
  const save = async () => {
    const p = correctionProblem({ status: row.status, note: row.note }, f); if (p) { setError(p); return }
    try { await apiClient.post(`${LRN}/attendance/${row.attendanceId}/correct`, correctionBody(f)); setMode(""); onChanged() } catch (e) { setError(errText(e)) }
  }
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div><strong>{row.learnerName}</strong><div style={muted}>{row.enrolmentNo}{row.note ? ` · ${row.note}` : ""}{row.corrections ? ` · corrected ${row.corrections} time${row.corrections === 1 ? "" : "s"}` : ""}</div></div>
        <Badge text={label(ATTENDANCE, row.status)} tone={attendanceTone(row.status)} />
      </div>
      {mode === "correct" && (
        <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
          <select aria-label={`Corrected attendance for ${row.learnerName}`} style={input} value={f.status} onChange={e => setF({ ...f, status: e.target.value })}>{Object.entries(ATTENDANCE).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
          <input aria-label="Note" style={input} placeholder="Note (needed if excused)" value={f.note} onChange={e => setF({ ...f, note: e.target.value })} />
          <input aria-label="Reason for the correction" style={input} placeholder="Why is this being corrected?" value={f.reason} onChange={e => setF({ ...f, reason: e.target.value })} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={save}>Save correction</button><button style={ghost} onClick={() => { setMode(""); setError("") }}>Cancel</button></div>
        </div>
      )}
      {mode === "history" && <div style={{ marginTop: 8 }}><RowHistory attendanceId={row.attendanceId!} /></div>}
      {mode === "" && <div style={{ display: "flex", gap: 8, marginTop: 8 }}>{canManage && <button style={ghost} onClick={() => setMode("correct")}>Correct</button>}<button style={ghost} onClick={() => setMode("history")}>History</button></div>}
    </div>
  )
}

export function RegisterPanel({ slot, onClose }: { slot: Slot; onClose: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [marks, setMarks] = useState<Marks>({})
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data, isLoading } = useQuery<Register>({ queryKey: ["tp-register", slot.id], queryFn: async () => unwrap<Register>(await apiClient.get(`${LRN}/timetable/${slot.id}/attendance`)) })
  const refresh = () => { setMarks({}); void qc.invalidateQueries({ queryKey: ["tp-register", slot.id] }); void qc.invalidateQueries({ queryKey: ["tp-attendance-summary", slot.cohortId] }) }
  if (isLoading || !data) return <div style={muted}>Loading…</div>
  const unrecorded = data.rows.filter(r => r.attendanceId === null)
  const recorded = data.rows.filter(r => r.attendanceId !== null)
  const set = (id: string, patch: Partial<{ status: string; note: string }>) => setMarks(m => ({ ...m, [id]: { status: m[id]?.status ?? "PRESENT", note: m[id]?.note ?? "", ...patch } }))
  const save = async () => {
    const p = registerProblem(marks); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${LRN}/timetable/${slot.id}/attendance`, registerBody(marks)); refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", justifyContent: "space-between", flexWrap: "wrap", gap: 8 }}>
        <div><strong>{data.title}</strong><div style={muted}>{data.slotDate} · {data.startTime.slice(0, 5)} to {data.endTime.slice(0, 5)}</div></div>
        <button style={ghost} onClick={onClose}>Back</button>
      </div>
      {!data.canRecord && <div style={muted}>{data.cannotRecordBecause}</div>}
      {data.canRecord && canManage && unrecorded.length > 0 && (
        <div style={{ display: "grid", gap: 8 }}>
          <strong>Mark the register</strong>
          {unrecorded.map(r => {
            const m = marks[r.memberId]
            return (
              <div key={r.memberId} style={{ ...card, display: "grid", gridTemplateColumns: "1fr auto 1fr", gap: 8, alignItems: "center" }}>
                <div><strong>{r.learnerName}</strong><div style={muted}>{r.enrolmentNo}</div></div>
                <select aria-label={`Attendance for ${r.learnerName}`} style={{ ...input, width: "auto" }} value={m?.status ?? ""} onChange={e => e.target.value ? set(r.memberId, { status: e.target.value }) : setMarks(x => { const y = { ...x }; delete y[r.memberId]; return y })}>
                  <option value="">Not marked</option>{Object.entries(ATTENDANCE).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                </select>
                <input aria-label={`Note for ${r.learnerName}`} style={input} placeholder="Note" disabled={!m} value={m?.note ?? ""} onChange={e => set(r.memberId, { note: e.target.value })} />
              </div>
            )
          })}
          <Notice text={error} />
          <div><button style={solid} disabled={busy} onClick={save}>Save the register</button></div>
        </div>
      )}
      {recorded.length > 0 && <strong>Recorded</strong>}
      {recorded.map(r => <RecordedRow key={r.memberId} row={r} canManage={canManage} onChanged={refresh} />)}
      {data.rows.length === 0 && <div style={muted}>No learners are in this cohort yet.</div>}
    </div>
  )
}

export function SummaryPanel({ cohortId }: { cohortId: string }) {
  const { data, isLoading } = useQuery<CohortSummary>({ queryKey: ["tp-attendance-summary", cohortId], queryFn: async () => unwrap<CohortSummary>(await apiClient.get(`${LRN}/cohorts/${cohortId}/attendance-summary`)) })
  if (isLoading || !data) return <div style={muted}>Loading…</div>
  return (
    <div style={{ display: "grid", gap: 8 }}>
      <div style={muted}>{ATTENDANCE_NOTE}</div>
      {data.rows.length === 0 ? <div style={muted}>No learners are in this cohort yet.</div> : data.rows.map(r => (
        <div key={r.memberId} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{r.learnerName}</strong><div style={muted}>{r.enrolmentNo}</div></div>
            <div style={{ textAlign: "right" }}>
              <div>{r.attended} attended · {r.absent} absent · {r.excused} excused · {r.unrecorded} not recorded</div>
              <div style={muted}>{r.expected} sessions held, {rateText(r)}</div>
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}
