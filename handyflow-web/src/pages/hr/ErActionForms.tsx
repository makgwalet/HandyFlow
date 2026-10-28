// The forms behind the buttons on a case. Each one collects what the server needs, checks the obvious mistakes and hands a path and
// a body to `send`. The server checks everything again, and says so plainly when it refuses.
import { DISMISSAL_ENDS, defaultEndType, endBody, endProblem, type EndForm } from "./erEnd.logic"
import type React from "react"
import { useState } from "react"
import {
  ACTION_LABEL, APPEAL_OUTCOMES, GRIEVANCE_OUTCOMES, HEARING_SANCTIONS, INFORMAL_SANCTIONS, LOWER_SANCTIONS, WARNING_SANCTIONS,
  appealDecisionProblem, decisionProblem, noticeProblem, todaySast, words,
} from "./er.logic"
import { useEmployeeOptions, useLoginOptions } from "./er.hooks"
import { input, plain, primary, type Detail } from "./er.shared"
import { Field, Notice, PersonSelect } from "./ErParts"

export interface ActionProps {
  action: string
  c: Detail
  busy: boolean
  error: string | null
  send: (path: string, body?: unknown) => void
  cancel: () => void
}

const orNull = (v: string) => (v.trim() === "" ? null : v.trim())
const months = (c: Detail, sanction: string) => {
  const p = c.policy
  if (!p) return ""
  return String(sanction === "VERBAL_WARNING" ? p.verbalMonths : sanction === "WRITTEN_WARNING" ? p.writtenMonths : sanction === "FINAL_WRITTEN_WARNING" ? p.finalMonths : "")
}

function Shell({ title, problem, submitText, busy, error, cancel, onSubmit, children, tone }:
  { title: string; problem: string | null; submitText: string; busy: boolean; error: string | null; cancel: () => void; onSubmit: () => void; children: React.ReactNode; tone?: "danger" }) {
  const [tried, setTried] = useState(false)
  return (
    <form onSubmit={e => { e.preventDefault(); setTried(true); if (!problem) onSubmit() }}
      style={{ border: "1.5px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16, background: "var(--hf-surface)" }} aria-label={title}>
      <h3 style={{ margin: "0 0 12px", fontSize: 15 }}>{title}</h3>
      {children}
      {tried && problem && <Notice kind="error">{problem}</Notice>}
      {error && <Notice kind="error">{error}</Notice>}
      <div style={{ display: "flex", gap: 8 }}>
        <button type="submit" disabled={busy} style={{ ...primary, ...(tone === "danger" ? { background: "var(--hf-danger-text)" } : {}) }}>{busy ? "Saving…" : submitText}</button>
        <button type="button" onClick={cancel} style={plain}>Cancel</button>
      </div>
    </form>
  )
}

export default function ErActionForm(p: ActionProps) {
  switch (p.action) {
    case "ASSIGN": return <Assign {...p} />
    case "PARTIES": return <Parties {...p} />
    case "CONFIDENTIAL": return <Confidential {...p} />
    case "INVESTIGATE": return <Plain {...p} path="investigate" submitText="Start investigation" text="The case moves to investigating. The employee is not told yet." />
    case "WITHDRAW": return <Reason {...p} path="withdraw" submitText="Withdraw the case" tone="danger" />
    case "CLOSE_NO_CASE": return <Reason {...p} path="close" submitText="Close the case" />
    case "CLOSE": return <Reason {...p} path="close" submitText="Close the case" />
    case "RETURN_TO_INVESTIGATION": return <Reason {...p} path="return-to-investigation" submitText="Return to investigation" />
    case "INFORMAL": return <Informal {...p} />
    case "NOTICE": return <NoticeForm {...p} />
    case "HEARING": return <HearingForm {...p} />
    case "DECIDE": return p.c.type === "DISCIPLINARY" ? <DisciplineDecision {...p} /> : <OtherDecision {...p} />
    case "COMMUNICATE": return <Communicate {...p} />
    case "APPEAL": return <Appeal {...p} />
    case "DECIDE_APPEAL": return <AppealDecision {...p} />
    case "END_EMPLOYMENT": return <EndEmployment {...p} />
    case "SUSPEND": return <Suspend {...p} />
    case "END_SUSPENSION": return <EndSuspension {...p} />
    case "STEP": return <StepForm {...p} />
    case "REVIEW": return <ReviewForm {...p} />
    default: return null
  }
}

function Plain({ path, text, submitText, ...p }: ActionProps & { path: string; text: string; submitText: string }) {
  return <Shell title={ACTION_LABEL[p.action]} problem={null} submitText={submitText} busy={p.busy} error={p.error} cancel={p.cancel} onSubmit={() => p.send(path, {})}><p style={{ fontSize: 13, marginTop: 0 }}>{text}</p></Shell>
}

function Reason({ path, submitText, tone, ...p }: ActionProps & { path: string; submitText: string; tone?: "danger" }) {
  const [reason, setReason] = useState("")
  return (
    <Shell title={ACTION_LABEL[p.action]} problem={reason.trim() ? null : "Give the reason."} submitText={submitText} busy={p.busy} error={p.error} cancel={p.cancel} tone={tone}
      onSubmit={() => p.send(path, { reason: reason.trim() })}>
      <Field id="er-reason" text="Reason"><textarea id="er-reason" rows={3} value={reason} onChange={e => setReason(e.target.value)} style={input} /></Field>
    </Shell>
  )
}

function Assign(p: ActionProps) {
  const logins = useLoginOptions()
  const [owner, setOwner] = useState("")
  return (
    <Shell title={ACTION_LABEL.ASSIGN} problem={owner ? null : "Choose who will handle the case."} submitText="Assign" busy={p.busy} error={p.error} cancel={p.cancel} onSubmit={() => p.send("assign", { ownerUserId: owner })}>
      <Field id="er-owner" text="Case officer"><PersonSelect id="er-owner" value={owner} onChange={setOwner} options={logins.data ?? []} /></Field>
      {logins.isError && <Notice kind="error">The list of users could not be loaded.</Notice>}
    </Shell>
  )
}

function Parties(p: ActionProps) {
  const emps = useEmployeeOptions()
  const [initiator, setInitiator] = useState(p.c.initiator?.id ?? "")
  const [chair, setChair] = useState(p.c.chair?.id ?? "")
  const [respondent, setRespondent] = useState(p.c.respondent?.id ?? "")
  const grievance = p.c.type === "GRIEVANCE"
  return (
    <Shell title={ACTION_LABEL.PARTIES} problem={null} submitText="Save" busy={p.busy} error={p.error} cancel={p.cancel}
      onSubmit={() => p.send("parties", { initiatorId: orNull(initiator), chairId: orNull(chair), respondentId: orNull(respondent) })}>
      {!grievance && <Field id="er-initiator" text="Initiator (presents the case)"><PersonSelect id="er-initiator" value={initiator} onChange={setInitiator} options={emps.data ?? []} empty="Not set" /></Field>}
      {!grievance && <Field id="er-chair" text="Chair"><PersonSelect id="er-chair" value={chair} onChange={setChair} options={emps.data ?? []} empty="Not set" /></Field>}
      {grievance && <Field id="er-respondent" text="Who the grievance is about"><PersonSelect id="er-respondent" value={respondent} onChange={setRespondent} options={emps.data ?? []} empty="Nobody in particular" /></Field>}
    </Shell>
  )
}

function Confidential(p: ActionProps) {
  const [on, setOn] = useState(p.c.confidential)
  return (
    <Shell title={ACTION_LABEL.CONFIDENTIAL} problem={null} submitText="Save" busy={p.busy} error={p.error} cancel={p.cancel} onSubmit={() => p.send("confidential", { confidential: on })}>
      <label style={{ fontSize: 13, display: "flex", gap: 8, alignItems: "center" }}>
        <input type="checkbox" checked={on} onChange={e => setOn(e.target.checked)} /> Keep this case to the case officer and people who may see sensitive HR records
      </label>
    </Shell>
  )
}

function Informal(p: ActionProps) {
  const [sanction, setSanction] = useState("")
  const [response, setResponse] = useState("")
  const [reasons, setReasons] = useState("")
  const [m, setM] = useState("")
  const warn = WARNING_SANCTIONS.includes(sanction)
  const problem = !sanction ? "Choose the outcome." : !response.trim() ? "Record the employee's response." : !reasons.trim() ? "Give the reasons."
    : warn && !(Number(m) >= 1 && Number(m) <= 60) ? "Say how many months the warning stays valid (1 to 60)." : null
  return (
    <Shell title={ACTION_LABEL.INFORMAL} problem={problem} submitText="Record outcome" busy={p.busy} error={p.error} cancel={p.cancel}
      onSubmit={() => p.send("informal", { sanction, employeeResponse: response.trim(), reasons: reasons.trim(), warningMonths: warn ? Number(m) : null })}>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>Counselling, a verbal warning or a written warning, given after the employee has had the chance to respond. A final warning or a dismissal needs notice and a hearing.</p>
      <Field id="er-san" text="Outcome">
        <select id="er-san" value={sanction} onChange={e => { setSanction(e.target.value); setM(months(p.c, e.target.value)) }} style={input}>
          <option value="">Choose…</option>{INFORMAL_SANCTIONS.map(s => <option key={s} value={s}>{words(s)}</option>)}
        </select>
      </Field>
      {warn && <Field id="er-months" text="Warning valid for (months)"><input id="er-months" type="number" min={1} max={60} value={m} onChange={e => setM(e.target.value)} style={input} /></Field>}
      <Field id="er-resp" text="What the employee said"><textarea id="er-resp" rows={3} value={response} onChange={e => setResponse(e.target.value)} style={input} /></Field>
      <Field id="er-why" text="Reasons"><textarea id="er-why" rows={3} value={reasons} onChange={e => setReasons(e.target.value)} style={input} /></Field>
    </Shell>
  )
}

function NoticeForm(p: ActionProps) {
  const emps = useEmployeeOptions()
  const [f, setF] = useState({ hearingDate: "", hearingTime: "09:00", venue: "", chairId: p.c.chair?.id ?? "", initiatorId: p.c.initiator?.id ?? "", charges: [""] })
  const disc = p.c.type === "DISCIPLINARY"
  const set = (k: keyof typeof f, v: string) => setF(x => ({ ...x, [k]: v }))
  const setCharge = (i: number, v: string) => setF(x => ({ ...x, charges: x.charges.map((c, j) => (j === i ? v : c)) }))
  const hours = p.c.policy?.noticeHours
  return (
    <Shell title={disc ? "Issue notice of hearing" : "Invite to a meeting"} problem={noticeProblem(f, p.c.type)} submitText="Issue notice" busy={p.busy} error={p.error} cancel={p.cancel}
      onSubmit={() => p.send("notice", { hearingDate: f.hearingDate, hearingTime: f.hearingTime, venue: f.venue.trim(), chairId: f.chairId, initiatorId: orNull(f.initiatorId), charges: disc ? f.charges.map(c => c.trim()).filter(Boolean) : [] })}>
      {hours ? <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>Company policy gives the employee at least {hours} hours' notice.</p> : null}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 12 }}>
        <Field id="er-hdate" text="Date"><input id="er-hdate" type="date" value={f.hearingDate} onChange={e => set("hearingDate", e.target.value)} style={input} /></Field>
        <Field id="er-htime" text="Time"><input id="er-htime" type="time" value={f.hearingTime} onChange={e => set("hearingTime", e.target.value)} style={input} /></Field>
        <Field id="er-venue" text="Venue"><input id="er-venue" value={f.venue} onChange={e => set("venue", e.target.value)} style={input} /></Field>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: 12 }}>
        <Field id="er-chair2" text="Chair"><PersonSelect id="er-chair2" value={f.chairId} onChange={v => set("chairId", v)} options={emps.data ?? []} /></Field>
        <Field id="er-init2" text="Initiator"><PersonSelect id="er-init2" value={f.initiatorId} onChange={v => set("initiatorId", v)} options={emps.data ?? []} empty="Not set" /></Field>
      </div>
      {disc && (
        <fieldset style={{ border: "none", padding: 0, margin: "0 0 12px" }}>
          <legend style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>Charges, in plain words</legend>
          {f.charges.map((ch, i) => (
            <input key={i} aria-label={`Charge ${i + 1}`} value={ch} onChange={e => setCharge(i, e.target.value)} style={{ ...input, marginBottom: 6 }} />
          ))}
          <button type="button" style={plain} onClick={() => setF(x => ({ ...x, charges: [...x.charges, ""] }))}>Add a charge</button>
        </fieldset>
      )}
    </Shell>
  )
}

function HearingForm(p: ActionProps) {
  const [heldOn, setHeldOn] = useState(todaySast())
  const [attended, setAttended] = useState("yes")
  const [rep, setRep] = useState("")
  const [notes, setNotes] = useState("")
  return (
    <Shell title={ACTION_LABEL.HEARING} problem={heldOn ? null : "Give the date it was held."} submitText="Save" busy={p.busy} error={p.error} cancel={p.cancel}
      onSubmit={() => p.send("hearing", { heldOn, employeeAttended: attended === "yes", representative: orNull(rep), notes: orNull(notes) })}>
      <Field id="er-held" text="Held on"><input id="er-held" type="date" value={heldOn} max={todaySast()} onChange={e => setHeldOn(e.target.value)} style={input} /></Field>
      <Field id="er-att" text="Did the employee attend?">
        <select id="er-att" value={attended} onChange={e => setAttended(e.target.value)} style={input}><option value="yes">Yes</option><option value="no">No</option></select>
      </Field>
      <Field id="er-rep" text="Representative (union or co-worker)"><input id="er-rep" value={rep} onChange={e => setRep(e.target.value)} style={input} /></Field>
      <Field id="er-notes" text="Notes"><textarea id="er-notes" rows={3} value={notes} onChange={e => setNotes(e.target.value)} style={input} /></Field>
    </Shell>
  )
}

function DisciplineDecision(p: ActionProps) {
  const [findings, setFindings] = useState<Record<string, string>>({})
  const [notes, setNotes] = useState<Record<string, string>>({})
  const [sanction, setSanction] = useState("")
  const [m, setM] = useState("")
  const [reasons, setReasons] = useState("")
  const ids = p.c.charges.map(x => x.id)
  const warn = WARNING_SANCTIONS.includes(sanction)
  return (
    <Shell title={ACTION_LABEL.DECIDE} problem={decisionProblem({ findings, sanction, warningMonths: m, reasons }, ids)} submitText="Record decision" busy={p.busy} error={p.error} cancel={p.cancel}
      onSubmit={() => p.send("decision", {
        findings: p.c.charges.map(x => ({ chargeId: x.id, finding: findings[x.id], note: orNull(notes[x.id] ?? "") })),
        sanction, warningMonths: warn ? Number(m) : null, reasons: reasons.trim(),
      })}>
      {p.c.charges.map(x => (
        <div key={x.id} style={{ marginBottom: 10 }}>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 4 }}>{x.seq}. {x.charge}</div>
          <div style={{ display: "flex", gap: 8 }}>
            <select aria-label={`Finding on charge ${x.seq}`} value={findings[x.id] ?? ""} onChange={e => setFindings(f => ({ ...f, [x.id]: e.target.value }))} style={{ ...input, width: 180 }}>
              <option value="">Finding…</option><option value="GUILTY">Guilty</option><option value="NOT_GUILTY">Not guilty</option>
            </select>
            <input aria-label={`Note on charge ${x.seq}`} placeholder="Note (optional)" value={notes[x.id] ?? ""} onChange={e => setNotes(n => ({ ...n, [x.id]: e.target.value }))} style={input} />
          </div>
        </div>
      ))}
      <Field id="er-sanction" text="Outcome">
        <select id="er-sanction" value={sanction} onChange={e => { setSanction(e.target.value); setM(months(p.c, e.target.value)) }} style={input}>
          <option value="">Choose…</option>{HEARING_SANCTIONS.map(s => <option key={s} value={s}>{words(s)}</option>)}
        </select>
      </Field>
      {warn && <Field id="er-months2" text="Warning valid for (months)"><input id="er-months2" type="number" min={1} max={60} value={m} onChange={e => setM(e.target.value)} style={input} /></Field>}
      <Field id="er-reasons" text="Reasons for the decision"><textarea id="er-reasons" rows={4} value={reasons} onChange={e => setReasons(e.target.value)} style={input} /></Field>
    </Shell>
  )
}

function OtherDecision(p: ActionProps) {
  const emps = useEmployeeOptions()
  const [decider, setDecider] = useState("")
  const [outcome, setOutcome] = useState("")
  const [note, setNote] = useState("")
  const grievance = p.c.type === "GRIEVANCE"
  const options = grievance ? GRIEVANCE_OUTCOMES : p.c.outcomes
  const problem = !decider ? "Choose who decides." : !outcome ? "Choose the outcome." : !note.trim() ? "Give the reasons." : null
  return (
    <Shell title={ACTION_LABEL.DECIDE} problem={problem} submitText="Record decision" busy={p.busy} error={p.error} cancel={p.cancel}
      onSubmit={() => p.send(grievance ? "grievance-decision" : "incapacity-decision", { deciderId: decider, outcome, note: note.trim() })}>
      {p.c.missingSteps.length > 0 && (
        <Notice kind="error">These steps are not recorded yet, and a fair process expects them: {p.c.missingSteps.join("; ")}.</Notice>
      )}
      <Field id="er-decider" text="Decided by"><PersonSelect id="er-decider" value={decider} onChange={setDecider} options={emps.data ?? []} /></Field>
      <Field id="er-outcome" text="Outcome">
        <select id="er-outcome" value={outcome} onChange={e => setOutcome(e.target.value)} style={input}><option value="">Choose…</option>{options.map(o => <option key={o} value={o}>{words(o)}</option>)}</select>
      </Field>
      <Field id="er-note" text="Reasons"><textarea id="er-note" rows={4} value={note} onChange={e => setNote(e.target.value)} style={input} /></Field>
    </Shell>
  )
}

function Communicate(p: ActionProps) {
  const [on, setOn] = useState(todaySast())
  const [rights, setRights] = useState(false)
  const dismissal = p.c.decision?.outcome === "DISMISSAL"
  return (
    <Shell title={ACTION_LABEL.COMMUNICATE} problem={on ? null : "Give the date."} submitText="Save" busy={p.busy} error={p.error} cancel={p.cancel}
      onSubmit={() => p.send("communicate", { on, rightsExplained: dismissal ? rights : null })}>
      <Field id="er-told" text="Employee told on"><input id="er-told" type="date" value={on} max={todaySast()} onChange={e => setOn(e.target.value)} style={input} /></Field>
      {dismissal && (
        <label style={{ fontSize: 13, display: "flex", gap: 8, alignItems: "center" }}>
          <input type="checkbox" checked={rights} onChange={e => setRights(e.target.checked)} /> The employee was told of the right to refer a dispute to the CCMA within 30 days
        </label>
      )}
    </Shell>
  )
}

function Appeal(p: ActionProps) {
  const [on, setOn] = useState(todaySast())
  const [grounds, setGrounds] = useState("")
  const [late, setLate] = useState(false)
  return (
    <Shell title={p.c.type === "GRIEVANCE" ? "Take to the next level" : "Record an appeal"} problem={!on ? "Give the date." : !grounds.trim() ? "State the grounds." : null} submitText="Save" busy={p.busy} error={p.error} cancel={p.cancel}
      onSubmit={() => p.send("appeal", { lodgedOn: on, grounds: grounds.trim(), acceptLate: late })}>
      <Field id="er-apdate" text="Lodged on"><input id="er-apdate" type="date" value={on} max={todaySast()} onChange={e => setOn(e.target.value)} style={input} /></Field>
      <Field id="er-grounds" text="Grounds"><textarea id="er-grounds" rows={3} value={grounds} onChange={e => setGrounds(e.target.value)} style={input} /></Field>
      <label style={{ fontSize: 13, display: "flex", gap: 8, alignItems: "center", marginBottom: 12 }}>
        <input type="checkbox" checked={late} onChange={e => setLate(e.target.checked)} /> Accept this even if it is after the deadline
      </label>
    </Shell>
  )
}

function AppealDecision(p: ActionProps) {
  const emps = useEmployeeOptions()
  const grievance = p.c.type === "GRIEVANCE"
  const [decider, setDecider] = useState("")
  const [outcome, setOutcome] = useState("")
  const [replacement, setReplacement] = useState("")
  const [m, setM] = useState("")
  const [note, setNote] = useState("")
  const replacements = p.c.type === "INCAPACITY" ? p.c.outcomes.filter(o => o !== "DISMISSAL") : LOWER_SANCTIONS
  const problem = grievance
    ? (!decider ? "Choose who decides." : !outcome ? "Choose the outcome." : !note.trim() ? "Give the reasons." : null)
    : appealDecisionProblem(outcome, replacement, m, note, decider)
  return (
    <Shell title={ACTION_LABEL.DECIDE_APPEAL} problem={problem} submitText="Record decision" busy={p.busy} error={p.error} cancel={p.cancel}
      onSubmit={() => grievance
        ? p.send("grievance-escalation-decision", { deciderId: decider, outcome, note: note.trim() })
        : p.send("appeal-decision", { deciderId: decider, outcome, newOutcome: outcome === "VARIED" ? replacement : null, warningMonths: outcome === "VARIED" && WARNING_SANCTIONS.includes(replacement) ? Number(m) : null, note: note.trim() })}>
      {!grievance && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>An appeal can confirm the decision, replace it with a lighter one, or overturn it. It cannot make it harsher.</p>}
      <Field id="er-adec" text="Decided by"><PersonSelect id="er-adec" value={decider} onChange={setDecider} options={emps.data ?? []} /></Field>
      <Field id="er-aout" text="Outcome">
        <select id="er-aout" value={outcome} onChange={e => setOutcome(e.target.value)} style={input}>
          <option value="">Choose…</option>{(grievance ? GRIEVANCE_OUTCOMES : APPEAL_OUTCOMES).map(o => <option key={o} value={o}>{words(o)}</option>)}
        </select>
      </Field>
      {!grievance && outcome === "VARIED" && (
        <Field id="er-arep" text="Replaced by">
          <select id="er-arep" value={replacement} onChange={e => { setReplacement(e.target.value); setM(months(p.c, e.target.value)) }} style={input}>
            <option value="">Choose…</option>{replacements.map(o => <option key={o} value={o}>{words(o)}</option>)}
          </select>
        </Field>
      )}
      {!grievance && outcome === "VARIED" && WARNING_SANCTIONS.includes(replacement) && (
        <Field id="er-amonths" text="Warning valid for (months)"><input id="er-amonths" type="number" min={1} max={60} value={m} onChange={e => setM(e.target.value)} style={input} /></Field>
      )}
      <Field id="er-anote" text="Reasons"><textarea id="er-anote" rows={4} value={note} onChange={e => setNote(e.target.value)} style={input} /></Field>
    </Shell>
  )
}

function EndEmployment(p: ActionProps) {
  const told = p.c.decision?.communicatedOn
  const [f, setF] = useState<EndForm>({ lastDay: todaySast(), endType: defaultEndType(p.c.type), noticeGivenOn: "" })
  const problem = endProblem(f, told, todaySast())
  return (
    <Shell title={ACTION_LABEL.END_EMPLOYMENT} problem={problem} submitText="End employment" tone="danger" busy={p.busy} error={p.error} cancel={p.cancel}
      onSubmit={() => p.send("end-employment", endBody(f))}>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>
        This marks {p.c.employee.name} as terminated. The next pay run pays them up to this day, with any annual leave owing. Leave after that day is cancelled and portal access is closed.
        Pay in place of notice is worked out for HR to see, but not paid for you.
      </p>
      <Field id="er-endtype" text="How does the dismissal end employment?">
        <select id="er-endtype" value={f.endType} onChange={e => setF(x => ({ ...x, endType: e.target.value }))} style={input}>
          <option value="">Choose…</option>{DISMISSAL_ENDS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      </Field>
      <Field id="er-lastday" text="Last day of employment"><input id="er-lastday" type="date" value={f.lastDay} max={todaySast()} onChange={e => setF(x => ({ ...x, lastDay: e.target.value }))} style={input} /></Field>
      {f.endType === "EMPLOYER_NOTICE" && (
        <Field id="er-noticeon" text="Notice given on (blank: the day the employee was told)">
          <input id="er-noticeon" type="date" value={f.noticeGivenOn} min={told ?? undefined} max={f.lastDay || undefined} onChange={e => setF(x => ({ ...x, noticeGivenOn: e.target.value }))} style={input} />
        </Field>
      )}
    </Shell>
  )
}

function Suspend(p: ActionProps) {
  const [from, setFrom] = useState(todaySast())
  const [to, setTo] = useState("")
  const [reason, setReason] = useState("")
  const problem = !from ? "Give the start date." : to && to < from ? "The end cannot be before the start." : !reason.trim() ? "Give the reason." : null
  return (
    <Shell title={ACTION_LABEL.SUSPEND} problem={problem} submitText="Record suspension" busy={p.busy} error={p.error} cancel={p.cancel}
      onSubmit={() => p.send("suspend", { from, to: orNull(to), reason: reason.trim() })}>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>A precautionary suspension is on full pay and is not a sanction. It does not change the employee's status, so payroll carries on.</p>
      <Field id="er-sfrom" text="From"><input id="er-sfrom" type="date" value={from} onChange={e => setFrom(e.target.value)} style={input} /></Field>
      <Field id="er-sto" text="Until (leave empty if not yet known)"><input id="er-sto" type="date" value={to} onChange={e => setTo(e.target.value)} style={input} /></Field>
      <Field id="er-sreason" text="Reason"><textarea id="er-sreason" rows={3} value={reason} onChange={e => setReason(e.target.value)} style={input} /></Field>
    </Shell>
  )
}

function EndSuspension(p: ActionProps) {
  const [on, setOn] = useState(todaySast())
  return (
    <Shell title={ACTION_LABEL.END_SUSPENSION} problem={on ? null : "Give the date."} submitText="End suspension" busy={p.busy} error={p.error} cancel={p.cancel} onSubmit={() => p.send("suspension/end", { on })}>
      <Field id="er-send" text="Ended on"><input id="er-send" type="date" value={on} onChange={e => setOn(e.target.value)} style={input} /></Field>
    </Shell>
  )
}

function StepForm(p: ActionProps) {
  const [code, setCode] = useState("")
  const [on, setOn] = useState(todaySast())
  const [note, setNote] = useState("")
  const done = p.c.steps.find(s => s.code === code)?.doneOn
  return (
    <Shell title={ACTION_LABEL.STEP} problem={!code ? "Choose the step." : !on ? "Give the date." : null} submitText={done ? "Update step" : "Record step"} busy={p.busy} error={p.error} cancel={p.cancel}
      onSubmit={() => p.send("steps", { code, doneOn: on, note: orNull(note), undo: false })}>
      <Field id="er-step" text="Step">
        <select id="er-step" value={code} onChange={e => setCode(e.target.value)} style={input}>
          <option value="">Choose…</option>{p.c.steps.map(s => <option key={s.code} value={s.code}>{s.label}</option>)}
        </select>
      </Field>
      <Field id="er-stepon" text="Done on"><input id="er-stepon" type="date" value={on} max={todaySast()} onChange={e => setOn(e.target.value)} style={input} /></Field>
      <Field id="er-stepnote" text="Note"><textarea id="er-stepnote" rows={2} value={note} onChange={e => setNote(e.target.value)} style={input} /></Field>
      {done && (
        <button type="button" style={{ ...plain, marginBottom: 12 }} onClick={() => p.send("steps", { code, doneOn: null, note: null, undo: true })}>Mark as not done</button>
      )}
    </Shell>
  )
}

function ReviewForm(p: ActionProps) {
  const [from, setFrom] = useState(p.c.review?.from ?? todaySast())
  const [to, setTo] = useState(p.c.review?.to ?? "")
  const minimum = p.c.policy?.minReviewDays
  return (
    <Shell title={ACTION_LABEL.REVIEW} problem={!from || !to ? "Give both dates." : to <= from ? "The review must end after it starts." : null} submitText="Save" busy={p.busy} error={p.error} cancel={p.cancel}
      onSubmit={() => p.send("review", { from, to })}>
      {minimum ? <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>Company policy sets a minimum of {minimum} days. A shorter period can be saved, but it is flagged.</p> : null}
      <Field id="er-rfrom" text="From"><input id="er-rfrom" type="date" value={from} onChange={e => setFrom(e.target.value)} style={input} /></Field>
      <Field id="er-rto" text="To"><input id="er-rto" type="date" value={to} onChange={e => setTo(e.target.value)} style={input} /></Field>
    </Shell>
  )
}
