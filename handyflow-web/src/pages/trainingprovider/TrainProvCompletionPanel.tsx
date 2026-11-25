// Readiness and outcome for one enrolment. Readiness is advice. A person records the outcome, and completion is not a certificate.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  OUTCOMES, READINESS_NOTE, STATES, completionLine, emptyOutcome, label, needsOverride, outcomeBody, outcomeProblem, outcomeTone, readinessSummary, stateTone, withdrawProblem,
  type Completion, type OutcomeForm, type Readiness,
} from "./completionRules"
import { Badge, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { CMP, LRN } from "./tpApi"

export default function TrainProvCompletionPanel({ enrolmentId, startDate }: { enrolmentId: string; startDate: string | null }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [open, setOpen] = useState(false)
  const [recording, setRecording] = useState(false)
  const [withdrawing, setWithdrawing] = useState<string | null>(null)
  const [reason, setReason] = useState("")
  const [f, setF] = useState<OutcomeForm>(emptyOutcome(todayIso()))
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: ready } = useQuery<Readiness>({
    queryKey: ["tp-readiness", enrolmentId], enabled: open,
    queryFn: async () => unwrap<Readiness>(await apiClient.get(`${LRN}/learner-enrolments/${enrolmentId}/readiness`)),
  })
  const { data: outcomes = [] } = useQuery<Completion[]>({
    queryKey: ["tp-completions", enrolmentId], enabled: open,
    queryFn: async () => unwrap<Completion[]>(await apiClient.get(CMP, { params: { enrolmentId } })),
  })
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ["tp-readiness", enrolmentId] }); void qc.invalidateQueries({ queryKey: ["tp-completions"] })
  }
  if (!open) return <div style={{ marginTop: 8 }}><button style={ghost} onClick={() => setOpen(true)}>Readiness and outcome</button></div>
  const live = outcomes.find(o => o.status === "RECORDED")
  const gaps = ready?.openGaps ?? 0
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setRecording(false); setWithdrawing(null); setReason(""); setF(emptyOutcome(todayIso())); refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const record = () => {
    const p = outcomeProblem(f, gaps, todayIso(), startDate); if (p) { setError(p); return }
    void run(() => apiClient.post(CMP, outcomeBody(enrolmentId, f, gaps)))
  }
  const withdraw = (id: string) => {
    const p = withdrawProblem(reason); if (p) { setError(p); return }
    void run(() => apiClient.post(`${CMP}/${id}/withdraw`, { reason: reason.trim() }))
  }
  return (
    <div style={{ ...card, marginTop: 8, display: "grid", gap: 8 }}>
      <div style={muted}>{READINESS_NOTE}</div>
      {!ready ? <div style={muted}>Loading…</div> : (
        <>
          <div style={muted}>{readinessSummary(ready)}</div>
          {ready.rows.map(r => (
            <div key={r.instrumentId} style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
              <span>{r.instrumentNo} · {r.instrumentTitle}{r.attempts > 0 ? <span style={muted}> · {r.attempts} {r.attempts === 1 ? "attempt" : "attempts"}</span> : null}</span>
              <Badge text={label(STATES, r.state)} tone={stateTone(r.state)} />
            </div>
          ))}
        </>
      )}
      <strong>Outcome</strong>
      {outcomes.length === 0 ? <div style={muted}>No outcome recorded.</div> : outcomes.map(o => (
        <div key={o.id} style={{ display: "grid", gap: 4 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{o.completionNo}</strong> <span style={muted}>{completionLine(o)}</span></div>
            <Badge text={o.status === "WITHDRAWN" ? "Withdrawn" : label(OUTCOMES, o.outcome)} tone={outcomeTone(o.outcome, o.status)} />
          </div>
          <div style={muted}>Basis: {o.basis}</div>
          {o.gapsSnapshot && <div style={muted}>Open at the time: {o.gapsSnapshot}</div>}
          {o.withdrawnReason && <div style={muted}>Withdrawn: {o.withdrawnReason}</div>}
          {canManage && o.status === "RECORDED" && withdrawing !== o.id && <div><button style={ghost} onClick={() => { setWithdrawing(o.id); setError("") }}>Withdraw this outcome</button></div>}
          {withdrawing === o.id && (
            <div style={{ display: "grid", gap: 6 }}>
              <input aria-label="Reason for withdrawing" style={input} placeholder="Why is this outcome being withdrawn?" value={reason} onChange={e => setReason(e.target.value)} />
              <Notice text={error} />
              <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => withdraw(o.id)}>Withdraw the outcome</button><button style={ghost} onClick={() => { setWithdrawing(null); setError("") }}>Cancel</button></div>
            </div>
          )}
        </div>
      ))}
      {canManage && !live && !recording && ready && <div><button style={solid} onClick={() => setRecording(true)}>Record an outcome</button></div>}
      {recording && (
        <div style={{ display: "grid", gap: 8 }}>
          <select aria-label="Outcome" style={input} value={f.outcome} onChange={e => setF({ ...f, outcome: e.target.value })}><option value="">Completed or did not complete</option>{Object.entries(OUTCOMES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
          <input aria-label="Day of the outcome" type="date" style={input} value={f.outcomeOn} onChange={e => setF({ ...f, outcomeOn: e.target.value })} />
          <textarea aria-label="Basis for the outcome" style={input} placeholder="What the outcome rests on" value={f.basis} onChange={e => setF({ ...f, basis: e.target.value })} />
          {needsOverride(f, gaps) && (
            <>
              <div style={muted}>Some assessments are still open. Say why this learner is being completed anyway. The reason and what was open are kept on the record.</div>
              <input aria-label="Reason for completing with items open" style={input} placeholder="Why complete with items still open?" value={f.overrideReason} onChange={e => setF({ ...f, overrideReason: e.target.value })} />
            </>
          )}
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={record}>Record the outcome</button><button style={ghost} onClick={() => { setRecording(false); setError("") }}>Cancel</button></div>
        </div>
      )}
      <div><button style={ghost} onClick={() => setOpen(false)}>Hide</button></div>
    </div>
  )
}
