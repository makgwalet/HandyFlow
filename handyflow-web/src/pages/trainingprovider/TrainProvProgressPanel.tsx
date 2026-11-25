// Learning progress for one enrolment. Loads only when opened. Progress only: not attendance, not an assessment outcome.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { KINDS, PROGRESS_NOTE, finishProblem, label, reasonProblem, type Progress } from "./lessonRules"
import { Notice } from "./tpUi"
import { card, errText, fmtDate, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { LMS, LSN } from "./tpApi"
import type { LmsLink } from "./lmsRules"

export default function TrainProvProgressPanel({ enrolmentId, startDate, versionId = null }: { enrolmentId: string; startDate: string | null; versionId?: string | null }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<{ kind: "finish" | "reverse"; id: string } | null>(null)
  const [day, setDay] = useState(todayIso())
  const [note, setNote] = useState("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: p } = useQuery<Progress>({
    queryKey: ["tp-lesson-progress", enrolmentId], enabled: open,
    queryFn: async () => unwrap<Progress>(await apiClient.get(`${LSN}/learner-enrolments/${enrolmentId}/lesson-progress`)),
  })
  const { data: links = [] } = useQuery<LmsLink[]>({
    queryKey: ["tp-lms-link", versionId], enabled: open && !!versionId,
    queryFn: async () => unwrap<LmsLink[]>(await apiClient.get(LMS, { params: { programmeVersionId: versionId, status: "ACTIVE" } })),
  })
  const link = links[0]
  if (!open) return <div style={{ marginTop: 8 }}><button style={ghost} onClick={() => setOpen(true)}>Learning progress</button></div>
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setMode(null); setReason(""); setNote(""); void qc.invalidateQueries({ queryKey: ["tp-lesson-progress", enrolmentId] }) } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const finish = (lessonId: string) => {
    const pr = finishProblem({ finishedOn: day, note }, startDate, todayIso()); if (pr) { setError(pr); return }
    void run(() => apiClient.post(`${LSN}/lesson-progress`, { enrolmentId, lessonId, finishedOn: day, note: note.trim() || null }))
  }
  const reverse = (progressId: string) => {
    const pr = reasonProblem(reason); if (pr) { setError(pr); return }
    void run(() => apiClient.post(`${LSN}/lesson-progress/${progressId}/reverse`, { reason: reason.trim() }))
  }
  return (
    <div style={{ ...card, marginTop: 8, display: "grid", gap: 8 }}>
      <div style={muted}>{PROGRESS_NOTE}</div>
      {link && <div><a href={link.launchUrl} target="_blank" rel="noreferrer noopener">Open {link.systemName}</a><span style={muted}> · learners sign in on that system. Progress there is not synced.</span></div>}
      {!p ? <div style={muted}>Loading…</div> : (
        <>
          <div style={muted}>{p.summary}</div>
          {p.rows.map(r => (
            <div key={r.lessonId} style={{ display: "grid", gap: 4 }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
                <span>{r.position}. {r.title} <span style={muted}>· {label(KINDS, r.kind)}</span></span>
                <span style={muted}>{r.finishedOn ? `Finished ${fmtDate(r.finishedOn)}` : "Not finished"}</span>
              </div>
              {r.note && <div style={muted}>{r.note}</div>}
              {canManage && !(mode?.id === r.lessonId || mode?.id === r.progressId) && (
                <div>
                  {r.progressId
                    ? <button style={ghost} onClick={() => { setMode({ kind: "reverse", id: r.progressId ?? "" }); setError("") }}>Reverse this record</button>
                    : <button style={ghost} onClick={() => { setMode({ kind: "finish", id: r.lessonId }); setError("") }}>Mark as finished</button>}
                </div>
              )}
              {mode?.kind === "finish" && mode.id === r.lessonId && (
                <div style={{ display: "grid", gap: 6 }}>
                  <input aria-label="Day finished" type="date" style={input} value={day} onChange={e => setDay(e.target.value)} />
                  <input aria-label="Note" style={input} placeholder="Note (optional)" value={note} onChange={e => setNote(e.target.value)} />
                  <Notice text={error} />
                  <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => finish(r.lessonId)}>Save as finished</button><button style={ghost} onClick={() => setMode(null)}>Cancel</button></div>
                </div>
              )}
              {mode?.kind === "reverse" && mode.id === r.progressId && (
                <div style={{ display: "grid", gap: 6 }}>
                  <input aria-label="Reason for reversing" style={input} placeholder="Why is this record being reversed?" value={reason} onChange={e => setReason(e.target.value)} />
                  <Notice text={error} />
                  <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => reverse(r.progressId ?? "")}>Reverse the record</button><button style={ghost} onClick={() => setMode(null)}>Cancel</button></div>
                </div>
              )}
            </div>
          ))}
        </>
      )}
      <div><button style={ghost} onClick={() => setOpen(false)}>Hide</button></div>
    </div>
  )
}
