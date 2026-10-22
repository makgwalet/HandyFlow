// Improvement plans: the list (mine, my team's, all), one plan with its check-ins, and the forms that move it on.
import { useState } from "react"
import { fmtDate, fmtInstant, todaySast, words } from "./er.logic"
import { useEmployeeOptions } from "./er.hooks"
import { usePerfList, usePerfOne, useSend } from "./perf.hooks"
import { CHECKIN_LABEL, checkinProblem, closePlanProblem, extendProblem, planElapsed, planProblem, reasonProblem, startPlanProblem, type PlanForm } from "./perf.logic"
import { card, errorText, input, plain, primary, td, th, type PlanDetail, type PlanRow } from "./perf.shared"
import { Bar, CheckinChip, Facts, Field, Notice, PersonSelect, PlanStatus, Prose, Section } from "./PerfParts"
import { FormFrame } from "./PerfForms"

export function PlanList({ scope, caseId, open, empty }: { scope: "mine" | "team" | "all"; caseId?: string; open: (id: string) => void; empty: string }) {
  const q = usePerfList<PlanRow>(["plans", scope, caseId ?? ""], `/plans?scope=${scope}${caseId ? `&caseId=${caseId}` : ""}`)
  const rows = q.data ?? []
  return (
    <div>
      {q.isError && <Notice kind="error">{errorText(q.error, "The plans could not be loaded.")}</Notice>}
      {!q.isLoading && rows.length === 0 && <div style={{ ...card, padding: 16, fontSize: 13 }}>{empty}</div>}
      {rows.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr><th style={th}>Plan</th><th style={th}>Employee</th><th style={th}>Status</th><th style={th}>Ends</th><th style={th}>Last check-in</th></tr></thead>
          <tbody>{rows.map(p => (
            <tr key={p.id}>
              <td style={td}><button onClick={() => open(p.id)} style={{ ...plain, border: "none", padding: 0, fontWeight: 600 }}>{p.title}</button></td>
              <td style={td}>{p.employeeName}</td>
              <td style={td}><PlanStatus status={p.status} /></td>
              <td style={td}>{fmtDate(p.endDate)}</td>
              <td style={td}>{p.lastProgress ? <CheckinChip progress={p.lastProgress} /> : "–"}</td>
            </tr>))}
          </tbody>
        </table>
      )}
    </div>
  )
}

export default function PlanScreen({ scope, canCreate }: { scope: "mine" | "team" | "all"; canCreate: boolean }) {
  const [openId, setOpenId] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  if (openId) return <PlanView id={openId} back={() => setOpenId(null)} />
  return (
    <div>
      {canCreate && scope === "all" && (
        <div style={{ display: "flex", marginBottom: 14 }}>
          <div style={{ flex: 1 }} />
          <button onClick={() => setCreating(c => !c)} style={primary}>{creating ? "Close form" : "New improvement plan"}</button>
        </div>
      )}
      {creating && <NewPlan done={() => setCreating(false)} />}
      <PlanList scope={scope} open={setOpenId} empty={scope === "mine" ? "You have no improvement plan." : "No improvement plans."} />
    </div>
  )
}

export function NewPlan({ caseId, employeeId: fixedEmployee, done }: { caseId?: string; employeeId?: string; done: () => void }) {
  const people = useEmployeeOptions()
  const [employeeId, setEmployeeId] = useState(fixedEmployee ?? "")
  const [ownerId, setOwnerId] = useState("")
  const [f, setF] = useState<PlanForm>({ title: "", shortfall: "", standard: "", support: "", startDate: todaySast(), endDate: "" })
  const s = useSend<object, PlanDetail>("post", "/plans", "The plan could not be saved.", done)
  const problem = !employeeId ? "Choose the employee." : planProblem(f)
  const text = (key: "title" | "shortfall" | "standard" | "support", label: string, rows: number, hint?: string) => (
    <Field id={`pf-p-${key}`} text={label} hint={hint}>
      {rows === 1 ? <input id={`pf-p-${key}`} value={f[key]} onChange={e => setF({ ...f, [key]: e.target.value })} style={input} />
        : <textarea id={`pf-p-${key}`} rows={rows} value={f[key]} onChange={e => setF({ ...f, [key]: e.target.value })} style={input} />}
    </Field>
  )
  return (
    <FormFrame title="New improvement plan" label="Save plan" problem={problem} pending={s.pending} error={s.error}
      submit={() => s.send({ employeeId, ownerId: ownerId || null, caseId: caseId ?? null, ...f })} onCancel={done}>
      {!fixedEmployee && <Field id="pf-p-emp" text="Employee"><PersonSelect id="pf-p-emp" value={employeeId} onChange={setEmployeeId} options={people.data ?? []} /></Field>}
      <Field id="pf-p-owner" text="Who supports and checks in (optional)" hint="Usually the manager. Left empty, HR does the check-ins."><PersonSelect id="pf-p-owner" value={ownerId} onChange={setOwnerId} options={people.data ?? []} /></Field>
      {text("title", "Title", 1)}
      {text("shortfall", "Where performance falls short", 4, "Be specific, with examples and dates.")}
      {text("standard", "The standard expected", 4, "What good looks like, and how it will be measured.")}
      {text("support", "Support the company will give", 4, "Training, coaching, tools, time.")}
      <Field id="pf-p-start" text="Starts"><input id="pf-p-start" type="date" value={f.startDate} onChange={e => setF({ ...f, startDate: e.target.value })} style={input} /></Field>
      <Field id="pf-p-end" text="Ends"><input id="pf-p-end" type="date" value={f.endDate} onChange={e => setF({ ...f, endDate: e.target.value })} style={input} /></Field>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>The employee cannot see the plan until you start it and record that they were told.</p>
    </FormFrame>
  )
}

type PlanForms = "start" | "checkin" | "extend" | "close" | "cancel" | "acknowledge" | "link"

export function PlanView({ id, back }: { id: string; back: () => void }) {
  const q = usePerfOne<PlanDetail>(["plan", id], `/plans/${id}`)
  const [form, setForm] = useState<PlanForms | null>(null)
  if (q.isError || !q.data) return <div><button onClick={back} style={plain}>Back</button>{q.isError && <Notice kind="error">{errorText(q.error, "This plan could not be opened. It may not exist, or you may not be allowed to see it.")}</Notice>}</div>
  const d = q.data, p = d.row
  const can = (a: string) => d.actions.includes(a)
  const shut = () => setForm(null)
  const pct = planElapsed(p.startDate, p.endDate)
  const buttons: [string, PlanForms, string][] = [["START", "start", "Start the plan"], ["CHECKIN", "checkin", "Record a check-in"], ["EXTEND", "extend", "Extend"], ["CLOSE", "close", "Close the plan"], ["LINK", "link", "Link to a case"], ["ACKNOWLEDGE", "acknowledge", "Add my comment"], ["CANCEL", "cancel", "Cancel plan"]]
  return (
    <div>
      <button onClick={back} style={plain}>Back</button>
      <div style={{ display: "flex", gap: 10, alignItems: "center", margin: "12px 0", flexWrap: "wrap" }}>
        <h3 style={{ margin: 0 }}>{p.title}</h3><PlanStatus status={p.status} />
      </div>
      {p.shorterThanMinimum && <Notice kind="error">This plan is shorter than the {d.minReviewDays} days the company's policy sets for a review period. Lengthen it or note why before relying on it.</Notice>}
      <Facts rows={[
        ["Employee", p.employeeName], ["Support person", p.ownerName ?? "HR"], ["Runs", `${fmtDate(p.startDate)} – ${fmtDate(p.endDate)}`],
        ["Employee told on", p.communicatedOn ? fmtDate(p.communicatedOn) : "Not yet"], ["Case", p.caseNumber ?? "Not linked"],
        ...(d.closedOn ? [["Closed", `${fmtDate(d.closedOn)}${d.outcomeNote ? ` – ${d.outcomeNote}` : ""}`] as [string, string]] : []),
      ]} />
      {p.status === "ACTIVE" && <div style={{ width: 260, margin: "8px 0 14px" }}><Bar percent={pct} label="Time elapsed" /><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{pct}% of the time has passed</div></div>}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "12px 0" }}>
        {buttons.filter(([a]) => can(a)).map(([a, f, label]) => <button key={a} onClick={() => setForm(f)} style={a === "START" || a === "CHECKIN" ? primary : plain}>{label}</button>)}
      </div>
      {form === "start" && <StartForm id={id} done={shut} />}
      {form === "checkin" && <CheckinForm id={id} start={p.startDate} done={shut} />}
      {form === "extend" && <ExtendForm id={id} end={p.endDate} done={shut} />}
      {form === "close" && <CloseForm id={id} end={p.endDate} checkins={d.checkins.length} done={shut} />}
      {form === "cancel" && <CancelForm id={id} done={shut} />}
      {form === "acknowledge" && <AckForm id={id} done={shut} />}
      {form === "link" && <LinkForm id={id} done={shut} />}
      <Section title="Where performance falls short"><Prose text={d.shortfall} /></Section>
      <Section title="The standard expected"><Prose text={d.standard} /></Section>
      <Section title="Support"><Prose text={d.support} /></Section>
      {d.employeeComment && <Section title="Employee's comment"><Prose text={d.employeeComment} /></Section>}
      <Section title="Check-ins">
        {d.checkins.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>None yet.</div> : d.checkins.map(c => (
          <div key={c.id} style={{ marginBottom: 10, fontSize: 13 }}>
            <strong>{fmtDate(c.onDate)}</strong> <CheckinChip progress={c.progress} /> <span style={{ color: "var(--hf-text-faint)" }}>{CHECKIN_LABEL[c.progress] ? "" : words(c.progress)}{c.doneBy ? ` · ${c.doneBy}` : ""}</span>
            <Prose text={c.note} />
          </div>))}
      </Section>
      <Section title="History">
        <ol style={{ margin: 0, paddingLeft: 18, fontSize: 12 }}>
          {d.events.map((e, i) => <li key={`${e.action}-${i}`}><strong>{words(e.action)}</strong>{e.note ? ` – ${e.note}` : ""} <span style={{ color: "var(--hf-text-faint)" }}>· {fmtInstant(e.doneAt)}{e.doneBy ? ` · ${e.doneBy}` : ""}</span></li>)}
        </ol>
      </Section>
    </div>
  )
}

function StartForm({ id, done }: { id: string; done: () => void }) {
  const [on, setOn] = useState(todaySast())
  const s = useSend<{ communicatedOn: string }, PlanDetail>("post", `/plans/${id}/start`, "The plan could not be started.", done)
  return (
    <FormFrame title="Start the plan" label="Start plan" problem={startPlanProblem(on)} pending={s.pending} error={s.error} submit={() => s.send({ communicatedOn: on })} onCancel={done}>
      <Field id="pf-st-on" text="Date the employee was told" hint="After this the shortfall, standard and support can no longer be changed."><input id="pf-st-on" type="date" value={on} onChange={e => setOn(e.target.value)} style={input} /></Field>
    </FormFrame>
  )
}

function CheckinForm({ id, start, done }: { id: string; start: string; done: () => void }) {
  const [on, setOn] = useState(todaySast())
  const [progress, setProgress] = useState("")
  const [note, setNote] = useState("")
  const s = useSend<{ onDate: string; progress: string; note: string }, PlanDetail>("post", `/plans/${id}/checkins`, "The check-in could not be saved.", done)
  return (
    <FormFrame title="Record a check-in" label="Save check-in" problem={checkinProblem(on, start, progress, note)} pending={s.pending} error={s.error} submit={() => s.send({ onDate: on, progress, note })} onCancel={done}>
      <Field id="pf-ck-on" text="Date of the meeting"><input id="pf-ck-on" type="date" value={on} onChange={e => setOn(e.target.value)} style={input} /></Field>
      <Field id="pf-ck-pr" text="How is it going?">
        <select id="pf-ck-pr" value={progress} onChange={e => setProgress(e.target.value)} style={input}>
          <option value="">Choose…</option>{Object.entries(CHECKIN_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </Field>
      <Field id="pf-ck-n" text="What was discussed?" hint="A check-in cannot be changed afterwards."><textarea id="pf-ck-n" rows={4} value={note} onChange={e => setNote(e.target.value)} style={input} /></Field>
    </FormFrame>
  )
}

function ExtendForm({ id, end, done }: { id: string; end: string; done: () => void }) {
  const [to, setTo] = useState("")
  const [reason, setReason] = useState("")
  const s = useSend<{ endDate: string; reason: string }, PlanDetail>("post", `/plans/${id}/extend`, "The plan could not be extended.", done)
  return (
    <FormFrame title="Extend the plan" label="Extend plan" problem={extendProblem(to, end, reason)} pending={s.pending} error={s.error} submit={() => s.send({ endDate: to, reason })} onCancel={done}>
      <Field id="pf-ex-to" text="New end date" hint={`It now ends on ${fmtDate(end)}. It can be made longer, not shorter.`}><input id="pf-ex-to" type="date" value={to} onChange={e => setTo(e.target.value)} style={input} /></Field>
      <Field id="pf-ex-r" text="Why?"><input id="pf-ex-r" value={reason} onChange={e => setReason(e.target.value)} style={input} /></Field>
    </FormFrame>
  )
}

function CloseForm({ id, end, checkins, done }: { id: string; end: string; checkins: number; done: () => void }) {
  const [outcome, setOutcome] = useState("")
  const [note, setNote] = useState("")
  const s = useSend<{ outcome: string; note: string }, PlanDetail>("post", `/plans/${id}/close`, "The plan could not be closed.", done)
  return (
    <FormFrame title="Close the plan" label="Close plan" problem={closePlanProblem(outcome, note, end, checkins)} pending={s.pending} error={s.error} submit={() => s.send({ outcome, note })} onCancel={done}>
      <Field id="pf-cl-o" text="Outcome">
        <select id="pf-cl-o" value={outcome} onChange={e => setOutcome(e.target.value)} style={input}>
          <option value="">Choose…</option><option value="MET">Met – performance is at the standard</option><option value="NOT_MET">Not met</option>
        </select>
      </Field>
      <Field id="pf-cl-n" text="Reasons"><textarea id="pf-cl-n" rows={4} value={note} onChange={e => setNote(e.target.value)} style={input} /></Field>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>“Not met” is only possible once the end date has passed and at least one check-in is recorded.</p>
    </FormFrame>
  )
}

function CancelForm({ id, done }: { id: string; done: () => void }) {
  const [reason, setReason] = useState("")
  const s = useSend<{ reason: string }, PlanDetail>("post", `/plans/${id}/cancel`, "The plan could not be cancelled.", done)
  return (
    <FormFrame title="Cancel the plan" label="Cancel plan" problem={reasonProblem("The reason for cancelling", reason)} pending={s.pending} error={s.error} submit={() => s.send({ reason })} onCancel={done}>
      <Field id="pf-cx-r" text="Why?"><input id="pf-cx-r" value={reason} onChange={e => setReason(e.target.value)} style={input} /></Field>
    </FormFrame>
  )
}

function AckForm({ id, done }: { id: string; done: () => void }) {
  const [comment, setComment] = useState("")
  const s = useSend<{ comment: string }, PlanDetail>("post", `/plans/${id}/acknowledge`, "Your comment could not be saved.", done)
  return (
    <FormFrame title="Your comment on the plan" label="Save" problem={null} pending={s.pending} error={s.error} submit={() => s.send({ comment })} onCancel={done}>
      <Field id="pf-ak-c" text="Comment (optional)" hint="Saying you have read the plan does not mean you agree with it."><textarea id="pf-ak-c" rows={4} value={comment} onChange={e => setComment(e.target.value)} style={input} /></Field>
    </FormFrame>
  )
}

function LinkForm({ id, done }: { id: string; done: () => void }) {
  const [caseId, setCaseId] = useState("")
  const s = useSend<{ caseId: string }, PlanDetail>("post", `/plans/${id}/case`, "The plan could not be linked.", done)
  return (
    <FormFrame title="Link to a case" label="Link" problem={caseId.trim() ? null : "Enter the case's id."} pending={s.pending} error={s.error} submit={() => s.send({ caseId: caseId.trim() })} onCancel={done}>
      <Field id="pf-lk-c" text="Case id" hint="Open the poor performance case and use “Make a plan” there; this is for a case that already exists."><input id="pf-lk-c" value={caseId} onChange={e => setCaseId(e.target.value)} style={input} /></Field>
    </FormFrame>
  )
}
