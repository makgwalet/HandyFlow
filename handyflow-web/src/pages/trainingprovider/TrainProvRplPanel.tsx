// RPL (recognition of prior learning) for one enrolment. Loads only when opened.
// Evidence is reviewed piece by piece. A decision needs a written basis and the assessor's name, and a second person confirms it
// (or the decider confirms alone with a written reason). The result is not a completion and not a certificate.
import { useState } from "react"
import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  KINDS, OUTCOMES, REVIEWS, RPL_NOTE, STATUSES, applicationBody, applicationProblem, confirmProblem, decisionBody, decisionProblem, emptyApplication, emptyDecision, emptyEvidence,
  evidenceBody, evidenceProblem, label, reasonProblem, reviewProblem, statusTone, type Application, type ApplicationDetail, type DecisionForm, type Evidence, type EvidenceForm,
} from "./rplRules"
import { enrolmentOpen as workplaceOpen } from "./workplaceRules"
import type { ProgrammeDetail } from "./catalogueRules"
import { Badge, Notice } from "./tpUi"
import { card, errText, fmtDate, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { CAT, PRC, RPL } from "./tpApi"
import type { Eligible } from "./practitionerRules"

type Mode = "" | "evidence" | "decide" | "confirm" | "withdraw" | { review: string } | { dropEvidence: string }

function ApplicationCard({ a, programmeId, onChanged }: { a: Application; programmeId: string | null; onChanged: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<Mode>("")
  const [ev, setEv] = useState<EvidenceForm>(emptyEvidence(todayIso()))
  const [dec, setDec] = useState<DecisionForm>(emptyDecision(todayIso()))
  const [verdict, setVerdict] = useState("")
  const [text, setText] = useState("")
  const [day, setDay] = useState(todayIso())
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: detail } = useQuery<ApplicationDetail>({
    queryKey: ["tp-rpl", a.id], enabled: open,
    queryFn: async () => unwrap<ApplicationDetail>(await apiClient.get(`${RPL}/rpl-applications/${a.id}`)),
  })
  const cur = detail?.application ?? a
  const { data: assessors = [] } = useQuery<Eligible[]>({
    queryKey: ["tp-eligible-rpl-assessors", programmeId, dec.decidedOn], enabled: mode === "decide" && !!dec.decidedOn && !!programmeId, placeholderData: keepPreviousData,
    queryFn: async () => unwrap<Eligible[]>(await apiClient.get(`${PRC}/eligible`, { params: { programmeId, role: "RPL_ASSESSOR", on: dec.decidedOn } })),
  })
  const chosenAssessor = assessors.find(x => x.practitionerId === dec.assessorId)
  const reset = () => { setMode(""); setText(""); setVerdict(""); setError(""); setDay(todayIso()); setEv(emptyEvidence(todayIso())); setDec(emptyDecision(todayIso())) }
  const refresh = () => { void qc.invalidateQueries({ queryKey: ["tp-rpl", a.id] }); onChanged() }
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); reset(); refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const addEvidence = () => {
    const p = evidenceProblem(ev, cur.appliedOn, todayIso()); if (p) { setError(p); return }
    void run(() => apiClient.post(`${RPL}/rpl-applications/${a.id}/evidence`, evidenceBody(ev)))
  }
  const review = (e: Evidence) => {
    const p = reviewProblem(verdict, text, day, e.receivedOn, todayIso()); if (p) { setError(p); return }
    void run(() => apiClient.post(`${RPL}/rpl-evidence/${e.id}/review`, { review: verdict, note: text.trim(), reviewedOn: day }))
  }
  const dropEvidence = (e: Evidence) => {
    const p = reasonProblem(text); if (p) { setError(p); return }
    void run(() => apiClient.post(`${RPL}/rpl-evidence/${e.id}/withdraw`, { reason: text.trim() }))
  }
  const decide = () => {
    const p = decisionProblem(dec, cur, todayIso(), chosenAssessor ? chosenAssessor.hasLogin !== false : true); if (p) { setError(p); return }
    void run(() => apiClient.post(`${RPL}/rpl-applications/${a.id}/decide`, decisionBody(dec)))
  }
  const confirm = () => {
    const p = confirmProblem(day, cur.decidedOn, todayIso()); if (p) { setError(p); return }
    void run(() => apiClient.post(`${RPL}/rpl-applications/${a.id}/confirm`, { confirmedOn: day }))
  }
  const withdraw = () => {
    const p = reasonProblem(text); if (p) { setError(p); return }
    void run(() => apiClient.post(`${RPL}/rpl-applications/${a.id}/withdraw`, { reason: text.trim() }))
  }
  const target = (kind: "review" | "dropEvidence", id: string) => typeof mode === "object" && kind in mode && (mode as Record<string, string>)[kind] === id
  const pick = (set: (m: Mode) => void, m: Mode) => { setError(""); set(m) }
  return (
    <div style={{ ...card, display: "grid", gap: 6 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div>
          <strong>{a.rplNo}</strong> · {a.moduleTitle ?? "Whole programme"}
          <div style={muted}>{a.claim}</div>
          <div style={muted}>Applied {fmtDate(a.appliedOn)}</div>
        </div>
        <Badge text={label(STATUSES, a.status)} tone={statusTone(a.status)} />
      </div>
      <div style={muted}>{cur.stateLabel}</div>
      {cur.decisionBasis && <div style={muted}>Basis: {cur.decisionBasis} (assessor {cur.assessorName}{cur.legacyAssessorText ? ", typed name, not checked" : ""}, {fmtDate(cur.decidedOn)})</div>}
      {cur.singleReason && <div style={muted}>Confirmed without a second person because: {cur.singleReason}</div>}
      {cur.withdrawReason && <div style={muted}>Withdrawn: {cur.withdrawReason}</div>}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <button style={ghost} onClick={() => setOpen(!open)}>{open ? "Hide evidence" : "Evidence"}</button>
        {canManage && mode === "" && cur.status === "OPEN" && <button style={ghost} onClick={() => pick(setMode, "evidence")}>Add evidence</button>}
        {canManage && mode === "" && cur.status === "OPEN" && <button style={ghost} onClick={() => pick(setMode, "decide")}>Record decision</button>}
        {canManage && mode === "" && cur.status === "DECIDED" && <button style={ghost} onClick={() => pick(setMode, "confirm")}>Confirm decision</button>}
        {canManage && mode === "" && cur.status !== "WITHDRAWN" && <button style={ghost} onClick={() => pick(setMode, "withdraw")}>Withdraw application</button>}
      </div>
      {mode === "evidence" && (
        <div style={{ display: "grid", gap: 6 }}>
          <select aria-label="Kind of evidence" style={input} value={ev.kind} onChange={e => setEv({ ...ev, kind: e.target.value })}><option value="">Kind of evidence…</option>{Object.entries(KINDS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
          <textarea aria-label="Describe the evidence" style={input} placeholder="Describe the evidence" value={ev.description} onChange={e => setEv({ ...ev, description: e.target.value })} />
          <input aria-label="Where it is kept" style={input} placeholder="Where it is kept or reference (optional)" value={ev.sourceRef} onChange={e => setEv({ ...ev, sourceRef: e.target.value })} />
          <input aria-label="Day received" type="date" style={input} value={ev.receivedOn} onChange={e => setEv({ ...ev, receivedOn: e.target.value })} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={addEvidence}>Save evidence</button><button style={ghost} onClick={reset}>Cancel</button></div>
        </div>
      )}
      {mode === "decide" && (
        <div style={{ display: "grid", gap: 6 }}>
          <div style={muted}>{cur.liveEvidence} pieces of evidence, {cur.unreviewedEvidence} not yet reviewed, {cur.acceptedEvidence} accepted.</div>
          <select aria-label="Outcome" style={input} value={dec.outcome} onChange={e => setDec({ ...dec, outcome: e.target.value })}><option value="">Outcome…</option>{Object.entries(OUTCOMES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
          <textarea aria-label="Basis for the decision" style={input} placeholder="Basis for the decision" value={dec.basis} onChange={e => setDec({ ...dec, basis: e.target.value })} />
          <select aria-label="RPL assessor" style={input} value={dec.assessorId} onChange={e => setDec({ ...dec, assessorId: e.target.value })}>
            <option value="">RPL assessor…</option>{assessors.map(x => <option key={x.practitionerId} value={x.practitionerId}>{x.name} · {x.practitionerNo}</option>)}
          </select>
          {assessors.length === 0 && <div style={muted}>No one has a checked, in-force RPL assessor appointment for this programme on that day. An ordinary assessor appointment does not cover RPL.</div>}
          {chosenAssessor && chosenAssessor.hasLogin === false && <textarea aria-label="How the assessor's decision reached you" style={input} placeholder="This assessor has no login. How did their decision reach you (who gave it, and how)?" value={dec.recordedForAssessor} onChange={e => setDec({ ...dec, recordedForAssessor: e.target.value })} />}
          <input aria-label="Day of the decision" type="date" style={input} value={dec.decidedOn} onChange={e => setDec({ ...dec, decidedOn: e.target.value })} />
          <label style={muted}><input type="checkbox" checked={dec.confirmAlone} onChange={e => setDec({ ...dec, confirmAlone: e.target.checked })} /> Confirm without a second person</label>
          {dec.confirmAlone && <input aria-label="Reason for confirming alone" style={input} placeholder="Why can no second person confirm it?" value={dec.singleReason} onChange={e => setDec({ ...dec, singleReason: e.target.value })} />}
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={decide}>Save the decision</button><button style={ghost} onClick={reset}>Cancel</button></div>
        </div>
      )}
      {mode === "confirm" && (
        <div style={{ display: "grid", gap: 6 }}>
          <div style={muted}>A second person confirms. The person who recorded the decision cannot.</div>
          <input aria-label="Day confirmed" type="date" style={input} value={day} onChange={e => setDay(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={confirm}>Confirm the decision</button><button style={ghost} onClick={reset}>Cancel</button></div>
        </div>
      )}
      {mode === "withdraw" && (
        <div style={{ display: "grid", gap: 6 }}>
          <input aria-label="Reason for withdrawing the application" style={input} placeholder="Why is this application being withdrawn?" value={text} onChange={e => setText(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={withdraw}>Withdraw the application</button><button style={ghost} onClick={reset}>Back</button></div>
        </div>
      )}
      {open && (
        <div style={{ display: "grid", gap: 6 }}>
          {!detail ? <div style={muted}>Loading…</div> : detail.evidence.length === 0 ? <div style={muted}>No evidence yet.</div> : detail.evidence.map(e => (
            <div key={e.id} style={{ display: "grid", gap: 4 }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
                <span>{label(KINDS, e.kind)} · {fmtDate(e.receivedOn)}{e.sourceRef ? ` · ${e.sourceRef}` : ""}</span>
                <span style={muted}>{e.status === "WITHDRAWN" ? `Withdrawn: ${e.withdrawReason}` : e.review ? `${label(REVIEWS, e.review)} on ${fmtDate(e.reviewedOn)}` : "Not yet reviewed"}</span>
              </div>
              <div style={muted}>{e.description}</div>
              {e.reviewNote && <div style={muted}>Review: {e.reviewNote}</div>}
              {canManage && cur.status === "OPEN" && e.status === "RECEIVED" && mode === "" && (
                <div style={{ display: "flex", gap: 8 }}>
                  {!e.review && <button style={ghost} onClick={() => { setError(""); setDay(todayIso()); setText(""); setVerdict(""); setMode({ review: e.id }) }}>Review evidence</button>}
                  <button style={ghost} onClick={() => { setError(""); setText(""); setMode({ dropEvidence: e.id }) }}>Withdraw evidence</button>
                </div>
              )}
              {target("review", e.id) && (
                <div style={{ display: "grid", gap: 6 }}>
                  <select aria-label="Review of the evidence" style={input} value={verdict} onChange={x => setVerdict(x.target.value)}><option value="">Accepted or queried…</option>{Object.entries(REVIEWS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
                  <input aria-label="Review note" style={input} placeholder="Why?" value={text} onChange={x => setText(x.target.value)} />
                  <input aria-label="Day of the review" type="date" style={input} value={day} onChange={x => setDay(x.target.value)} />
                  <Notice text={error} />
                  <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => review(e)}>Save the review</button><button style={ghost} onClick={reset}>Cancel</button></div>
                </div>
              )}
              {target("dropEvidence", e.id) && (
                <div style={{ display: "grid", gap: 6 }}>
                  <input aria-label="Reason for withdrawing the evidence" style={input} placeholder="Why is this evidence being withdrawn?" value={text} onChange={x => setText(x.target.value)} />
                  <Notice text={error} />
                  <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => dropEvidence(e)}>Withdraw the evidence</button><button style={ghost} onClick={reset}>Cancel</button></div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function NewApplication({ enrolmentId, programmeId, versionId, startDate, onDone, onCancel }: { enrolmentId: string; programmeId: string | null; versionId: string | null; startDate: string | null; onDone: () => void; onCancel: () => void }) {
  const [f, setF] = useState(emptyApplication(todayIso()))
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: detail } = useQuery<ProgrammeDetail>({
    queryKey: ["tp-programme-detail", programmeId], enabled: !!programmeId,
    queryFn: async () => unwrap<ProgrammeDetail>(await apiClient.get(`${CAT}/programmes/${programmeId}`)),
  })
  const modules = detail?.versions.find(v => v.id === versionId)?.modules ?? []
  const save = async () => {
    const p = applicationProblem(f, startDate, todayIso()); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${RPL}/rpl-applications`, applicationBody(enrolmentId, f)); onDone() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      <select aria-label="Module claimed" style={input} value={f.moduleId} onChange={e => setF({ ...f, moduleId: e.target.value })}>
        <option value="">Whole programme version</option>{modules.map(m => <option key={m.id} value={m.id}>{m.seq}. {m.title}</option>)}
      </select>
      <textarea aria-label="Prior learning claimed" style={input} placeholder="What prior learning is being claimed?" value={f.claim} onChange={e => setF({ ...f, claim: e.target.value })} />
      <input aria-label="Day of the application" type="date" style={input} value={f.appliedOn} onChange={e => setF({ ...f, appliedOn: e.target.value })} />
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>Add application</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

export default function TrainProvRplPanel({ enrolmentId, enrolmentStatus, startDate, programmeId, versionId }: { enrolmentId: string; enrolmentStatus: string; startDate: string | null; programmeId: string | null; versionId: string | null }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [open, setOpen] = useState(false)
  const [adding, setAdding] = useState(false)
  const { data: apps = [] } = useQuery<Application[]>({
    queryKey: ["tp-rpls", enrolmentId], enabled: open,
    queryFn: async () => unwrap<Application[]>(await apiClient.get(`${RPL}/rpl-applications`, { params: { enrolmentId } })),
  })
  if (!open) return <div style={{ marginTop: 8 }}><button style={ghost} onClick={() => setOpen(true)}>Recognition of prior learning</button></div>
  const changed = () => { void qc.invalidateQueries({ queryKey: ["tp-rpls", enrolmentId] }) }
  return (
    <div style={{ ...card, marginTop: 8, display: "grid", gap: 8 }}>
      <div style={muted}>{RPL_NOTE}</div>
      {apps.length === 0 && <div style={muted}>No RPL applications yet.</div>}
      {apps.map(a => <ApplicationCard key={a.id} a={a} programmeId={programmeId} onChanged={changed} />)}
      {adding && <NewApplication enrolmentId={enrolmentId} programmeId={programmeId} versionId={versionId} startDate={startDate} onCancel={() => setAdding(false)} onDone={() => { setAdding(false); changed() }} />}
      {canManage && !adding && (workplaceOpen(enrolmentStatus)
        ? <div><button style={solid} onClick={() => setAdding(true)}>Add an RPL application</button></div>
        : <div style={muted}>An RPL application can be added while the enrolment is enrolled or in progress.</div>)}
      <div><button style={ghost} onClick={() => setOpen(false)}>Hide</button></div>
    </div>
  )
}
