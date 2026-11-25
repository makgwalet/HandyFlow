// Assessment attempts for one cohort. Recording an attempt stores the mark; the decision is recorded separately on the attempt.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { attemptBody, attemptProblem, attemptStateText, attemptTone, emptyAttempt, markText, SEPARATE_NOTE, type Attempt, type AttemptForm } from "./attemptRules"
import type { Instrument } from "./assessmentRules"
import type { Cohort, Member } from "./cohortRules"
import type { Eligible } from "./practitionerRules"
import { Badge, Field, Notice } from "./tpUi"
import { card, errText, fmtDate, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { ASM, ATT, PRC } from "./tpApi"
import TrainProvAttemptDetail from "./TrainProvAttemptDetail"

function RecordForm({ cohort, members, onSaved, onCancel }: { cohort: Cohort; members: Member[]; onSaved: (a: Attempt) => void; onCancel: () => void }) {
  const [f, setF] = useState<AttemptForm>(emptyAttempt(todayIso()))
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: instruments = [] } = useQuery<Instrument[]>({
    queryKey: ["tp-instruments-cohort", cohort.id],
    queryFn: async () => unwrap<Instrument[]>(await apiClient.get(ASM, { params: { status: "APPROVED" } })),
  })
  const { data: assessors = [] } = useQuery<Eligible[]>({
    queryKey: ["tp-eligible-assessors", cohort.programmeId, f.assessedOn], enabled: !!f.assessedOn,
    queryFn: async () => unwrap<Eligible[]>(await apiClient.get(`${PRC}/eligible`, { params: { programmeId: cohort.programmeId, role: "ASSESSOR", on: f.assessedOn } })),
  })
  const usable = instruments.filter(i => i.programmeId === cohort.programmeId && i.versionNo === cohort.programmeVersionNo)
  const chosen = usable.find(i => i.id === f.instrumentId)
  const save = async () => {
    const p = attemptProblem(f, chosen ? chosen.maxMark : null, todayIso()); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { onSaved(unwrap<Attempt>(await apiClient.post(ATT, attemptBody(f)))) } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        <select aria-label="Learner" style={input} value={f.memberId} onChange={e => setF({ ...f, memberId: e.target.value })}>
          <option value="">Choose the learner</option>{members.filter(m => m.status === "ACTIVE").map(m => <option key={m.id} value={m.id}>{m.learnerName} · {m.enrolmentNo}</option>)}
        </select>
        <select aria-label="Instrument" style={input} value={f.instrumentId} onChange={e => setF({ ...f, instrumentId: e.target.value })}>
          <option value="">Choose the instrument</option>{usable.map(i => <option key={i.id} value={i.id}>{i.instrumentNo} · {i.title}{i.maxMark === null ? "" : ` (out of ${i.maxMark})`}</option>)}
        </select>
        <Field label="Day of the assessment"><input aria-label="Assessment day" type="date" style={input} value={f.assessedOn} onChange={e => setF({ ...f, assessedOn: e.target.value, assessorId: "" })} /></Field>
        <Field label="Assessor"><select aria-label="Assessor" style={input} value={f.assessorId} onChange={e => setF({ ...f, assessorId: e.target.value })}>
          <option value="">Choose the assessor</option>{assessors.map(a => <option key={a.practitionerId} value={a.practitionerId}>{a.name} · {a.practitionerNo}</option>)}
        </select></Field>
      </div>
      {chosen && chosen.maxMark !== null && <input aria-label="Mark" style={input} inputMode="decimal" placeholder={`Mark out of ${chosen.maxMark}`} value={f.mark} onChange={e => setF({ ...f, mark: e.target.value })} />}
      <input aria-label="Notes" style={input} placeholder="Notes (optional)" value={f.notes} onChange={e => setF({ ...f, notes: e.target.value })} />
      <div style={muted}>Only assessors whose appointment was checked and in force on that day are listed. Recording an attempt does not decide competence.</div>
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>Record the attempt</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

export default function TrainProvAttemptsPanel({ cohort, members }: { cohort: Cohort; members: Member[] }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [adding, setAdding] = useState(false)
  const [openId, setOpenId] = useState<string | null>(null)
  const { data = [], isLoading } = useQuery<Attempt[]>({
    queryKey: ["tp-attempts", cohort.id], queryFn: async () => unwrap<Attempt[]>(await apiClient.get(ATT, { params: { cohortId: cohort.id } })),
  })
  if (openId) return <TrainProvAttemptDetail attemptId={openId} onBack={() => setOpenId(null)} />
  const canRecord = canManage && (cohort.status === "RUNNING" || cohort.status === "COMPLETED")
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>{SEPARATE_NOTE}</div>
      {canRecord && !adding && <div><button style={solid} onClick={() => setAdding(true)}>Record an attempt</button></div>}
      {!canRecord && canManage && <div style={muted}>Attempts can be recorded once the cohort is running.</div>}
      {adding && <RecordForm cohort={cohort} members={members} onCancel={() => setAdding(false)} onSaved={a => { setAdding(false); void qc.invalidateQueries({ queryKey: ["tp-attempts", cohort.id] }); setOpenId(a.id) }} />}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No attempts recorded for this cohort.</div> : data.map(a => (
        <button key={a.id} onClick={() => setOpenId(a.id)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{a.learnerName ?? "Learner"} · {a.instrumentTitle}</strong><div style={muted}>{a.attemptNo} · attempt {a.attemptSeq} · {fmtDate(a.assessedOn)} · {markText(a)}</div></div>
            <Badge text={attemptStateText(a)} tone={attemptTone(a)} />
          </div>
        </button>
      ))}
    </div>
  )
}
