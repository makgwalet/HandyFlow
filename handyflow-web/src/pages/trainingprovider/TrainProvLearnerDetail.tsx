// One learner: profile, applications and enrolments. Entry checks are recorded by a person; nothing here decides eligibility.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  APPLICATION_STATUS, ENROLMENT_STATUS, ID_TYPES, applicationBody, applicationFormProblem, applicationTone, appTransitionBody, appTransitionProblem, coverNote,
  emptyAppTransition, emptyApplication, emptyEnrol, enrolBody, enrolFormProblem, enrolmentTone, enrolmentTransitionProblem, emptyLearner, label, learnerBody,
  learnerForm, learnerProblem, reasonProblem, ENROL_NEEDS_REASON, type AppTransitionForm, type ApplicationForm, type EnrolForm, type Learner, type LearnerDetail,
  type LearnerForm, type Application, type Enrolment,
} from "./learnerRules"
import { Badge, Field, Notice } from "./tpUi"
import { errText, ghost, input, muted, solid, card, todayIso, unwrap, fmtDate } from "./tpStyle"
import { LRN, useActiveProgrammes, useClients, useSites } from "./tpApi"
import TrainProvLearnerCohortsSection from "./TrainProvLearnerCohorts"
import TrainProvLearnerAttempts from "./TrainProvLearnerAttempts"
import TrainProvCompletionPanel from "./TrainProvCompletionPanel"

export function LearnerFormPanel({ initial, onSaved, onCancel }: { initial?: Learner; onSaved: (l: Learner) => void; onCancel: () => void }) {
  const { data: clients = [] } = useClients()
  const [f, setF] = useState<LearnerForm>(initial ? learnerForm(initial) : emptyLearner)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const set = (k: keyof LearnerForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value })
  const save = async () => {
    const p = learnerProblem(f, todayIso()); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      const body = learnerBody(f)
      const l = unwrap<Learner>(await (initial ? apiClient.put(`${LRN}/learners/${initial.id}`, body) : apiClient.post(`${LRN}/learners`, body)))
      onSaved(l)
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        <input aria-label="First names" style={input} placeholder="First names" value={f.firstNames} onChange={set("firstNames")} />
        <input aria-label="Surname" style={input} placeholder="Surname" value={f.surname} onChange={set("surname")} />
        <select aria-label="Identity document" style={input} value={f.idType} onChange={set("idType")}>{Object.entries(ID_TYPES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
        <input aria-label="Identity number" style={input} placeholder="Identity number" disabled={f.idType === "NONE"} value={f.idNumber} onChange={set("idNumber")} />
        <Field label="Date of birth"><input aria-label="Date of birth" type="date" style={input} value={f.dateOfBirth} onChange={set("dateOfBirth")} /></Field>
        <select aria-label="Client" style={{ ...input, alignSelf: "end" }} value={f.clientId} onChange={set("clientId")}><option value="">No employer or sponsor</option>{clients.map(c => <option key={c.id} value={c.id}>{c.tradingName}</option>)}</select>
        <input aria-label="Email" style={input} placeholder="Email" value={f.email} onChange={set("email")} />
        <input aria-label="Phone" style={input} placeholder="Phone" value={f.phone} onChange={set("phone")} />
        <input aria-label="Province" style={input} placeholder="Province" value={f.province} onChange={set("province")} />
        <input aria-label="Address" style={input} placeholder="Address" value={f.address} onChange={set("address")} />
      </div>
      <label style={{ fontSize: 13 }}><input type="checkbox" checked={f.consentGiven} onChange={e => setF({ ...f, consentGiven: e.target.checked, consentOn: e.target.checked ? f.consentOn || todayIso() : "" })} /> The learner has consented to the provider holding their details</label>
      {f.consentGiven && <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: 8 }}>
        <input aria-label="Consent date" type="date" style={input} value={f.consentOn} onChange={set("consentOn")} />
        <input aria-label="Consent note" style={input} placeholder="How consent was given, for example the signed form" value={f.consentNote} onChange={set("consentNote")} />
      </div>}
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>{initial ? "Save changes" : "Add learner"}</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function ApplicationCard({ a, canManage, isAdmin, onChanged, onEnrol }: { a: Application; canManage: boolean; isAdmin: boolean; onChanged: () => void; onEnrol: (a: Application) => void }) {
  const [open, setOpen] = useState(false)
  const allowed = a.nextStatuses
  const [f, setF] = useState<AppTransitionForm>(emptyAppTransition(allowed[0] ?? ""))
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const submit = async () => {
    const p = appTransitionProblem(f, allowed); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${LRN}/applications/${a.id}/transition`, appTransitionBody(f)); setOpen(false); onChanged() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const set = <K extends keyof AppTransitionForm>(k: K, v: AppTransitionForm[K]) => setF(x => ({ ...x, [k]: v }))
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div><strong>{a.programmeTitle}</strong><div style={muted}>{a.siteName ? `at ${a.siteName} · ` : ""}applied {a.createdAt.slice(0, 10)}{a.preferredStart ? ` · wants to start ${a.preferredStart}` : ""}</div></div>
        <Badge text={label(APPLICATION_STATUS, a.status)} tone={applicationTone(a.status)} />
      </div>
      {a.statusReason && <div style={muted}>Reason: {a.statusReason}</div>}
      {a.entryRequirementsMet !== null && <div style={muted}>Entry check by a person: {a.entryRequirementsMet ? "requirements met" : "requirements not met"} · {a.entryCheckNote}{a.entryOverrideReason ? ` · accepted anyway because: ${a.entryOverrideReason}` : ""}</div>}
      {canManage && (allowed.length > 0 || a.status === "ACCEPTED") && !open && (
        <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
          {allowed.length > 0 && <button style={ghost} onClick={() => { setOpen(true); setF(emptyAppTransition(allowed[0])); setError("") }}>Change status</button>}
          {a.status === "ACCEPTED" && <button style={solid} onClick={() => onEnrol(a)}>Enrol this learner</button>}
        </div>
      )}
      {open && (
        <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
          <select aria-label="New application status" style={input} value={f.toStatus} onChange={e => setF(emptyAppTransition(e.target.value))}>{allowed.map(s => <option key={s} value={s}>{label(APPLICATION_STATUS, s)}</option>)}</select>
          {f.toStatus === "ACCEPTED" && (<>
            <select aria-label="Entry requirements met" style={input} value={f.entryMet} onChange={e => set("entryMet", e.target.value as AppTransitionForm["entryMet"])}>
              <option value="">Does the learner meet the entry requirements? (checked by a person)</option><option value="yes">Yes, requirements met</option><option value="no">No, requirements not met</option>
            </select>
            <input aria-label="Entry check note" style={input} placeholder="What was checked, for example the certificate sighted" value={f.entryNote} onChange={e => set("entryNote", e.target.value)} />
            {f.entryMet === "no" && (isAdmin
              ? <input aria-label="Entry override reason" style={input} placeholder="Why the learner is being accepted anyway" value={f.overrideReason} onChange={e => set("overrideReason", e.target.value)} />
              : <div style={muted}>Only an administrator can accept a learner who does not meet the entry requirements.</div>)}
          </>)}
          <input aria-label="Application reason" style={input} placeholder="Reason or note" value={f.reason} onChange={e => set("reason", e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={submit}>Record</button><button style={ghost} onClick={() => setOpen(false)}>Cancel</button></div>
        </div>
      )}
    </div>
  )
}

function EnrolmentCard({ e, canManage, onChanged }: { e: Enrolment; canManage: boolean; onChanged: () => void }) {
  const [to, setTo] = useState("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const submit = async () => {
    const p = enrolmentTransitionProblem(to, reason, e.nextStatuses); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${LRN}/learner-enrolments/${e.id}/transition`, { toStatus: to, reason: reason.trim() || null }); setTo(""); setReason(""); onChanged() } catch (x) { setError(errText(x)) } finally { setBusy(false) }
  }
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div><strong>{e.programmeTitle}</strong> <span style={muted}>{e.enrolmentNo} · version {e.programmeVersionNo ?? "—"} · {e.siteName}</span>
          <div style={muted}>Starts {fmtDate(e.startDate)}{e.expectedEndDate ? ` · expected end ${e.expectedEndDate}` : ""}</div></div>
        <Badge text={label(ENROLMENT_STATUS, e.status)} tone={enrolmentTone(e.status)} />
      </div>
      <div style={muted}>{coverNote(e)}</div>
      {e.statusReason && <div style={muted}>Reason: {e.statusReason}</div>}
      {canManage && e.nextStatuses.length > 0 && (
        <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
          <select aria-label="New enrolment status" style={input} value={to} onChange={ev => setTo(ev.target.value)}><option value="">Change status…</option>{e.nextStatuses.map(s => <option key={s} value={s}>{label(ENROLMENT_STATUS, s)}</option>)}</select>
          {to && ENROL_NEEDS_REASON.includes(to) && <input aria-label="Enrolment reason" style={input} placeholder="Reason" value={reason} onChange={ev => setReason(ev.target.value)} />}
          {to && <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={submit}>Record</button><button style={ghost} onClick={() => { setTo(""); setError("") }}>Cancel</button></div>}
          <Notice text={error} />
        </div>
      )}
      <TrainProvCompletionPanel enrolmentId={e.id} startDate={e.startDate} />
    </div>
  )
}

export default function TrainProvLearnerDetail({ id, canManage, isAdmin, onBack }: { id: string; canManage: boolean; isAdmin: boolean; onBack: () => void }) {
  const qc = useQueryClient()
  const { data: programmes = [] } = useActiveProgrammes()
  const { data: sites = [] } = useSites()
  const { data: clients = [] } = useClients()
  const { data, isLoading } = useQuery<LearnerDetail>({ queryKey: ["tp-learner", id], queryFn: async () => unwrap<LearnerDetail>(await apiClient.get(`${LRN}/learners/${id}`)) })
  const [panel, setPanel] = useState<"" | "edit" | "apply" | "enrol" | "archive">("")
  const [af, setAf] = useState<ApplicationForm>(emptyApplication)
  const [ef, setEf] = useState<EnrolForm>(emptyEnrol)
  const [fromApp, setFromApp] = useState<Application | null>(null)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const refresh = () => { qc.invalidateQueries({ queryKey: ["tp-learner", id] }); qc.invalidateQueries({ queryKey: ["tp-learners"] }); qc.invalidateQueries({ queryKey: ["tp-applications"] }); qc.invalidateQueries({ queryKey: ["tp-learner-enrolments"] }) }
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setPanel(""); setReason(""); setFromApp(null); refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  if (isLoading || !data) return <div style={muted}>Loading…</div>
  const l = data.learner
  const archived = l.status === "ARCHIVED"
  const chosen = programmes.find(p => p.id === ef.programmeId)
  const startEnrol = (a: Application | null) => {
    setFromApp(a); setPanel("enrol"); setError("")
    setEf({ ...emptyEnrol, programmeId: a?.programmeId ?? "", siteId: a?.siteId ?? "", startDate: a?.preferredStart ?? "", clientId: l.clientId ?? "" })
  }
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div><button style={ghost} onClick={onBack}>← All learners</button></div>
      {panel === "edit" ? <LearnerFormPanel initial={l} onCancel={() => setPanel("")} onSaved={() => { setPanel(""); refresh() }} /> : (
        <div style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong style={{ fontSize: 16 }}>{l.firstNames} {l.surname}</strong><div style={muted}>{l.learnerNo} · {label(ID_TYPES, l.idType)}{l.idNumber ? ` ${l.idNumber}` : ""}{l.dateOfBirth ? ` · born ${l.dateOfBirth}` : ""}</div></div>
            <Badge text={archived ? "Archived" : "Active"} tone={archived ? "neutral" : "good"} />
          </div>
          <div style={muted}>{[l.email, l.phone, l.province, l.clientName ? `Employer or sponsor: ${l.clientName}` : null].filter(Boolean).join(" · ") || "No contact details recorded"}</div>
          <div style={muted}>{l.consentGiven ? `Consent given on ${l.consentOn}${l.consentNote ? ` · ${l.consentNote}` : ""}` : "No consent recorded"}</div>
          {l.archivedReason && <div style={muted}>Archived: {l.archivedReason}</div>}
          {canManage && (
            <div style={{ display: "flex", gap: 8, marginTop: 10, flexWrap: "wrap" }}>
              {!archived && <button style={ghost} onClick={() => { setPanel("edit"); setError("") }}>Edit</button>}
              {!archived && <button style={ghost} onClick={() => { setPanel("apply"); setAf(emptyApplication); setError("") }}>New application</button>}
              {!archived && <button style={solid} onClick={() => startEnrol(null)}>Enrol directly</button>}
              {isAdmin && !archived && <button style={ghost} onClick={() => { setPanel("archive"); setError("") }}>Archive</button>}
              {isAdmin && archived && <button style={ghost} disabled={busy} onClick={() => run(() => apiClient.post(`${LRN}/learners/${id}/restore`))}>Restore</button>}
            </div>
          )}
          <Notice text={panel === "" ? error : ""} />
        </div>
      )}
      {panel === "archive" && (
        <div style={{ ...card, display: "grid", gap: 6, background: "var(--hf-surface-muted)" }}>
          <input aria-label="Archive reason" style={input} placeholder="Why is this learner being archived? Nothing is deleted" value={reason} onChange={e => setReason(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => { const p = reasonProblem(reason); if (p) setError(p); else run(() => apiClient.post(`${LRN}/learners/${id}/archive`, { reason: reason.trim() })) }}>Archive</button><button style={ghost} onClick={() => setPanel("")}>Cancel</button></div>
        </div>
      )}
      {panel === "apply" && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <select aria-label="Programme" style={input} value={af.programmeId} onChange={e => setAf({ ...af, programmeId: e.target.value })}><option value="">Choose a programme</option>{programmes.map(p => <option key={p.id} value={p.id}>{p.title}</option>)}</select>
          <select aria-label="Preferred site" style={input} value={af.siteId} onChange={e => setAf({ ...af, siteId: e.target.value })}><option value="">No preferred site</option>{sites.filter(s => s.status === "ACTIVE").map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
          <input aria-label="Preferred start" type="date" style={input} value={af.preferredStart} onChange={e => setAf({ ...af, preferredStart: e.target.value })} />
          <input aria-label="Source note" style={input} placeholder="Where the application came from (optional)" value={af.sourceNote} onChange={e => setAf({ ...af, sourceNote: e.target.value })} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => { const p = applicationFormProblem(af); if (p) setError(p); else run(() => apiClient.post(`${LRN}/applications`, applicationBody(id, af))) }}>Record application</button><button style={ghost} onClick={() => setPanel("")}>Cancel</button></div>
        </div>
      )}
      {panel === "enrol" && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <select aria-label="Enrol programme" style={input} disabled={!!fromApp} value={ef.programmeId} onChange={e => setEf({ ...ef, programmeId: e.target.value })}><option value="">Choose a programme</option>{programmes.map(p => <option key={p.id} value={p.id}>{p.title}</option>)}</select>
          <select aria-label="Enrol site" style={input} value={ef.siteId} onChange={e => setEf({ ...ef, siteId: e.target.value })}><option value="">Choose a site</option>{sites.filter(s => s.status === "ACTIVE").map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            <Field label="Start date"><input aria-label="Start date" type="date" style={input} value={ef.startDate} onChange={e => setEf({ ...ef, startDate: e.target.value })} /></Field>
            <Field label="Expected end date"><input aria-label="Expected end date" type="date" style={input} value={ef.expectedEndDate} onChange={e => setEf({ ...ef, expectedEndDate: e.target.value })} /></Field>
          </div>
          <select aria-label="Paying client" style={input} value={ef.clientId} onChange={e => setEf({ ...ef, clientId: e.target.value })}><option value="">No employer or sponsor</option>{clients.map(c => <option key={c.id} value={c.id}>{c.tradingName}</option>)}</select>
          {chosen?.trainingActivity === "ACCREDITED" && (<>
            <div style={muted}>This is an accredited programme. The server checks that an approval covers it at this site on the start date and records the answer. If it does not, only an administrator can enrol, and must say why.</div>
            {isAdmin && <input aria-label="Cover override reason" style={input} placeholder="Only if there is no cover: why this enrolment goes ahead" value={ef.overrideReason} onChange={e => setEf({ ...ef, overrideReason: e.target.value })} />}
          </>)}
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => { const p = enrolFormProblem(ef); if (p) setError(p); else run(() => apiClient.post(`${LRN}/learner-enrolments`, enrolBody(id, ef, fromApp?.id ?? null))) }}>Enrol</button><button style={ghost} onClick={() => { setPanel(""); setFromApp(null) }}>Cancel</button></div>
        </div>
      )}
      <strong>Enrolments</strong>
      {data.enrolments.length === 0 ? <div style={muted}>Not enrolled on anything yet.</div> : data.enrolments.map(e => <EnrolmentCard key={e.id} e={e} canManage={canManage} onChanged={refresh} />)}
      <strong>Cohorts and attendance</strong>
      <TrainProvLearnerCohortsSection learnerId={id} />
      <strong>Assessment attempts</strong>
      <TrainProvLearnerAttempts learnerId={id} />
      <strong>Applications</strong>
      {data.applications.length === 0 ? <div style={muted}>No applications.</div> : data.applications.map(a => <ApplicationCard key={a.id} a={a} canManage={canManage} isAdmin={isAdmin} onChanged={refresh} onEnrol={startEnrol} />)}
    </div>
  )
}
