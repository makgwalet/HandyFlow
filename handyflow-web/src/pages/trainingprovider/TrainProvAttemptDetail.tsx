// One assessment attempt: the mark, the competency decision (separate), moderation and history.
// The decision is written once and never edited. A wrong mark is corrected with a reason before a decision; after one, the attempt is voided.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  ACTIONS, DECISIONS, OUTCOMES, SEPARATE_NOTE, attemptStateText, attemptTone, correctBody, correctProblem, decisionBody, decisionProblem, decisionTone, emptyDecision, emptyModeration,
  label, markText, moderationBody, moderationProblem, moderationText, reasonProblem, resolveProblem, type Attempt, type CorrectForm, type DecisionForm, type Moderation, type ModerationForm,
} from "./attemptRules"
import type { Instrument } from "./assessmentRules"
import type { Eligible } from "./practitionerRules"
import { Badge, Field, Notice } from "./tpUi"
import { card, errText, fmtDate, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { ASM, ATT, MOD, PRC } from "./tpApi"

type Mode = "" | "correct" | "decide" | "void" | "moderate" | "resolve"

export default function TrainProvAttemptDetail({ attemptId, onBack }: { attemptId: string; onBack: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [mode, setMode] = useState<Mode>("")
  const [cf, setCf] = useState<CorrectForm>({ mark: "", reason: "" })
  const [df, setDf] = useState<DecisionForm>(emptyDecision)
  const [mf, setMf] = useState<ModerationForm>(emptyModeration(todayIso()))
  const [text, setText] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: a } = useQuery<Attempt>({ queryKey: ["tp-attempt", attemptId], queryFn: async () => unwrap<Attempt>(await apiClient.get(`${ATT}/${attemptId}`)) })
  const { data: moderation } = useQuery<Moderation | null>({
    queryKey: ["tp-attempt-moderation", attemptId],
    queryFn: async () => {
      // An attempt with no moderation comes back as a response whose data is null, which unwrap would hand back whole. Only a record with an id counts.
      const d = unwrap<Moderation | null>(await apiClient.get(`${MOD}/assessment-attempts/${attemptId}/moderation`))
      return d && typeof d === "object" && "id" in d ? d : null
    },
  })
  const { data: instrument } = useQuery<Instrument>({
    queryKey: ["tp-instrument", a?.instrumentId], enabled: !!a?.instrumentId,
    queryFn: async () => unwrap<Instrument>(await apiClient.get(`${ASM}/${a?.instrumentId}`)),
  })
  const { data: moderators = [] } = useQuery<Eligible[]>({
    queryKey: ["tp-eligible-moderators", instrument?.programmeId, mf.moderatedOn], enabled: mode === "moderate" && !!instrument?.programmeId && !!mf.moderatedOn,
    queryFn: async () => unwrap<Eligible[]>(await apiClient.get(`${PRC}/eligible`, { params: { programmeId: instrument?.programmeId, role: "MODERATOR", on: mf.moderatedOn } })),
  })
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ["tp-attempt", attemptId] }); void qc.invalidateQueries({ queryKey: ["tp-attempt-moderation", attemptId] })
    void qc.invalidateQueries({ queryKey: ["tp-attempts"] }); void qc.invalidateQueries({ queryKey: ["tp-moderation-pending"] }); void qc.invalidateQueries({ queryKey: ["tp-moderation-queries"] })
  }
  if (!a) return <div style={muted}>Loading…</div>
  const live = a.status === "RECORDED"
  const undecided = live && !a.decision
  const awaitingModeration = live && !!a.decision && a.requiresModeration && !moderation
  const close = () => { setMode(""); setText(""); setError("") }
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); close(); refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const correct = () => {
    const p = correctProblem(cf, a.maxMark, a.mark); if (p) { setError(p); return }
    void run(() => apiClient.post(`${ATT}/${attemptId}/correct-mark`, correctBody(cf)))
  }
  const decide = () => {
    const p = decisionProblem(df); if (p) { setError(p); return }
    void run(() => apiClient.post(`${ATT}/${attemptId}/decision`, decisionBody(df)))
  }
  const voidIt = () => {
    const p = reasonProblem(text); if (p) { setError(p); return }
    void run(() => apiClient.post(`${ATT}/${attemptId}/void`, { reason: text.trim() }))
  }
  const moderate = () => {
    const p = moderationProblem(mf, todayIso(), a.decision?.decidedOn ?? null); if (p) { setError(p); return }
    void run(() => apiClient.post(`${MOD}/assessment-attempts/${attemptId}/moderation`, moderationBody(mf)))
  }
  const resolve = () => {
    const p = resolveProblem(text); if (p) { setError(p); return }
    void run(() => apiClient.post(`${MOD}/assessment-moderations/${moderation?.id}/resolve`, { note: text.trim() }))
  }
  const form = (children: React.ReactNode, submit: () => void, cta: string) => (
    <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
      {children}<Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={submit}>{cta}</button><button style={ghost} onClick={close}>Cancel</button></div>
    </div>
  )
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div><button style={ghost} onClick={onBack}>Back</button></div>
      <div style={card}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
          <div>
            <strong>{a.learnerName ?? "Learner"} · {a.instrumentTitle}</strong>
            <div style={muted}>{a.attemptNo} · attempt {a.attemptSeq} · {a.cohortName ?? "Cohort"} · assessed {fmtDate(a.assessedOn)} by {a.assessorName ?? "assessor"}</div>
          </div>
          <Badge text={attemptStateText(a)} tone={attemptTone(a)} />
        </div>
        <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
          <div><span style={muted}>Mark: </span><strong>{markText(a)}</strong></div>
          <div>
            <span style={muted}>Decision: </span>
            {a.decision ? <><Badge text={label(DECISIONS, a.decision.decision)} tone={decisionTone(a.decision.decision)} /> <span style={muted}>on {fmtDate(a.decision.decidedOn)}. Basis: {a.decision.basis}</span></> : <span>{live ? "Not recorded yet" : "None"}</span>}
          </div>
          {a.notes && <div style={muted}>Notes: {a.notes}</div>}
          {a.voidReason && <div style={muted}>Voided: {a.voidReason}</div>}
          <div style={muted}>{SEPARATE_NOTE}</div>
        </div>
        {canManage && mode === "" && live && (
          <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
            {undecided && <button style={ghost} onClick={() => { setCf({ mark: a.mark === null ? "" : String(a.mark), reason: "" }); setMode("correct") }}>Correct the mark</button>}
            {undecided && <button style={ghost} onClick={() => setMode("decide")}>Record the decision</button>}
            <button style={ghost} onClick={() => setMode("void")}>Void this attempt</button>
          </div>
        )}
        {mode === "correct" && form(<>
          <input aria-label="Corrected mark" style={input} inputMode="decimal" placeholder="Corrected mark" value={cf.mark} onChange={e => setCf({ ...cf, mark: e.target.value })} />
          <input aria-label="Reason for correcting" style={input} placeholder="Why is the mark being corrected?" value={cf.reason} onChange={e => setCf({ ...cf, reason: e.target.value })} />
        </>, correct, "Correct the mark")}
        {mode === "decide" && form(<>
          <div style={muted}>This is written once and cannot be edited. It should rest on the evidence, not on the mark alone or on attendance.</div>
          <select aria-label="Decision" style={input} value={df.decision} onChange={e => setDf({ ...df, decision: e.target.value })}><option value="">Choose the decision</option>{Object.entries(DECISIONS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
          <textarea aria-label="Basis for the decision" style={input} placeholder="What the decision rests on" value={df.basis} onChange={e => setDf({ ...df, basis: e.target.value })} />
        </>, decide, "Record the decision")}
        {mode === "void" && form(<>
          <div style={muted}>A void attempt stays on record and cannot be changed. Record it again if it was wrong.</div>
          <input aria-label="Reason for voiding" style={input} placeholder="Why is this attempt being voided?" value={text} onChange={e => setText(e.target.value)} />
        </>, voidIt, "Void the attempt")}
      </div>
      <strong>Moderation</strong>
      {moderation ? (
        <div style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{moderation.moderationNo}</strong><div style={muted}>{moderation.moderatorName ?? "Moderator"} · {fmtDate(moderation.moderatedOn)}</div></div>
            <Badge text={label(OUTCOMES, moderation.outcome)} tone={moderation.outcome === "CONFIRMED" ? "good" : moderation.resolvedAt ? "neutral" : "warn"} />
          </div>
          <div style={muted}>{moderationText(moderation)}. Basis: {moderation.basis}</div>
          {moderation.resolutionNote && <div style={muted}>Answer: {moderation.resolutionNote}</div>}
          {canManage && moderation.outcome === "QUERIED" && !moderation.resolvedAt && mode === "" && <div style={{ marginTop: 8 }}><button style={ghost} onClick={() => setMode("resolve")}>Answer the query</button></div>}
          {mode === "resolve" && form(<input aria-label="Answer to the query" style={input} placeholder="How was the query answered?" value={text} onChange={e => setText(e.target.value)} />, resolve, "Record the answer")}
        </div>
      ) : (
        <div style={card}>
          <div style={muted}>{!a.requiresModeration ? "Results from this instrument are not moderated." : awaitingModeration ? "Decided, waiting for a moderator." : live ? "Nothing to moderate until a decision is recorded." : "A void attempt is not moderated."}</div>
          {canManage && awaitingModeration && mode === "" && <div style={{ marginTop: 8 }}><button style={ghost} onClick={() => setMode("moderate")}>Record moderation</button></div>}
          {mode === "moderate" && form(<>
            <div style={muted}>A moderator reviews the decision. They cannot be the assessor, and the person who recorded the decision cannot enter this.</div>
            <Field label="Day of the moderation"><input aria-label="Moderation day" type="date" style={input} value={mf.moderatedOn} onChange={e => setMf({ ...mf, moderatedOn: e.target.value, moderatorId: "" })} /></Field>
            <select aria-label="Moderator" style={input} value={mf.moderatorId} onChange={e => setMf({ ...mf, moderatorId: e.target.value })}><option value="">Choose the moderator</option>{moderators.filter(m => m.practitionerId !== a.assessorId).map(m => <option key={m.practitionerId} value={m.practitionerId}>{m.name} · {m.practitionerNo}</option>)}</select>
            <select aria-label="Moderation outcome" style={input} value={mf.outcome} onChange={e => setMf({ ...mf, outcome: e.target.value })}><option value="">Confirmed or queried</option>{Object.entries(OUTCOMES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
            <textarea aria-label="Basis for the moderation" style={input} placeholder="What you reviewed and why" value={mf.basis} onChange={e => setMf({ ...mf, basis: e.target.value })} />
          </>, moderate, "Record moderation")}
        </div>
      )}
      <strong>History</strong>
      {a.history.length === 0 ? <div style={muted}>No history yet.</div> : a.history.map(h => <div key={h.id} style={muted}>{fmtDate(h.actedAt.slice(0, 10))} · {label(ACTIONS, h.action)}{h.note ? ` · ${h.note}` : ""}</div>)}
    </div>
  )
}
