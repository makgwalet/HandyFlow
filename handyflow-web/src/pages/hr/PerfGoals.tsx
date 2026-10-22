// Goals: a compact line for use inside a review, and the goals screen (mine, my team's, everyone's).
import { useState } from "react"
import { fmtDate, fmtInstant, todaySast, words } from "./er.logic"
import { useEmployeeOptions } from "./er.hooks"
import { usePerfList, useSend } from "./perf.hooks"
import { GOAL_OUTCOMES, GOAL_STATUS, goalCloseProblem, goalProblem, progressProblem, reasonProblem } from "./perf.logic"
import { card, errorText, input, plain, primary, type GoalView } from "./perf.shared"
import { Bar, Field, GoalStatus, Notice, PersonSelect } from "./PerfParts"
import { FormFrame } from "./PerfForms"

export function GoalLine({ goal }: { goal: GoalView }) {
  return (
    <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap", marginBottom: 8, fontSize: 13 }}>
      <strong style={{ flex: "1 1 200px" }}>{goal.title}</strong>
      <GoalStatus status={goal.status} />
      <div style={{ width: 120 }}><Bar percent={goal.progress} label={`Progress on ${goal.title}`} /></div>
      <span style={{ color: goal.overdue ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }}>{goal.progress}% · due {fmtDate(goal.dueDate)}{goal.overdue ? " (late)" : ""}</span>
    </div>
  )
}

export default function GoalsScreen({ scope }: { scope: "mine" | "team" | "all" }) {
  const q = usePerfList<GoalView>(["goals", scope], `/goals?scope=${scope}`)
  const [creating, setCreating] = useState(false)
  const [show, setShow] = useState<"open" | "closed">("open")
  const all = q.data ?? []
  const isOpen = (g: GoalView) => g.status === "PROPOSED" || g.status === "ACTIVE"
  const goals = all.filter(g => (show === "open") === isOpen(g))
  const waiting = all.filter(g => g.status === "PROPOSED" && g.actions.includes("APPROVE")).length
  return (
    <div>
      <div style={{ display: "flex", gap: 10, marginBottom: 14, alignItems: "center", flexWrap: "wrap" }}>
        <select aria-label="Show goals" value={show} onChange={e => setShow(e.target.value as "open" | "closed")} style={{ ...input, width: 190 }}>
          <option value="open">Open goals</option><option value="closed">Finished and cancelled</option>
        </select>
        {waiting > 0 && <span style={{ fontSize: 13, color: "var(--hf-warning-text-strong)" }}>{waiting} waiting for your approval</span>}
        <div style={{ flex: 1 }} />
        <button onClick={() => setCreating(c => !c)} style={primary}>{creating ? "Close form" : scope === "mine" ? "Propose a goal" : "Set a goal"}</button>
      </div>
      {creating && <NewGoal forOthers={scope !== "mine"} done={() => setCreating(false)} />}
      {q.isError && <Notice kind="error">{errorText(q.error, "The goals could not be loaded.")}</Notice>}
      {q.isLoading && <div style={{ color: "var(--hf-text-faint)" }}>Loading…</div>}
      {!q.isLoading && goals.length === 0 && <div style={{ ...card, padding: 16, fontSize: 13 }}>{show === "open" ? "No open goals." : "Nothing finished yet."}</div>}
      {goals.map(g => <GoalCard key={g.id} goal={g} showPerson={scope !== "mine"} />)}
    </div>
  )
}

function NewGoal({ forOthers, done }: { forOthers: boolean; done: () => void }) {
  const people = useEmployeeOptions()
  const [employeeId, setEmployeeId] = useState("")
  const [f, setF] = useState({ title: "", description: "", measure: "", dueDate: "" })
  const s = useSend<object, GoalView>("post", "/goals", "The goal could not be saved.", done)
  const problem = forOthers && !employeeId ? "Choose the employee." : goalProblem(f, true)
  return (
    <FormFrame title={forOthers ? "Set a goal" : "Propose a goal"} label={forOthers ? "Set goal" : "Propose goal"} problem={problem} pending={s.pending} error={s.error}
      submit={() => s.send({ employeeId: forOthers ? employeeId : null, cycleId: null, ...f })} onCancel={done}>
      {forOthers && <Field id="pf-g-emp" text="Employee"><PersonSelect id="pf-g-emp" value={employeeId} onChange={setEmployeeId} options={people.data ?? []} /></Field>}
      <Field id="pf-g-title" text="Goal"><input id="pf-g-title" value={f.title} onChange={e => setF({ ...f, title: e.target.value })} style={input} /></Field>
      <Field id="pf-g-desc" text="Details (optional)"><textarea id="pf-g-desc" rows={3} value={f.description} onChange={e => setF({ ...f, description: e.target.value })} style={input} /></Field>
      <Field id="pf-g-measure" text="How will success be measured? (optional)"><input id="pf-g-measure" value={f.measure} onChange={e => setF({ ...f, measure: e.target.value })} style={input} /></Field>
      <Field id="pf-g-due" text="Due date"><input id="pf-g-due" type="date" min={todaySast()} value={f.dueDate} onChange={e => setF({ ...f, dueDate: e.target.value })} style={input} /></Field>
      {!forOthers && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>Your manager has to approve it before it counts.</p>}
    </FormFrame>
  )
}

function GoalCard({ goal, showPerson }: { goal: GoalView; showPerson: boolean }) {
  const [form, setForm] = useState<"edit" | "progress" | "close" | "cancel" | null>(null)
  const [open, setOpen] = useState(false)
  const approve = useSend<object, GoalView>("post", `/goals/${goal.id}/approve`, "The goal could not be approved.")
  const can = (a: string) => goal.actions.includes(a)
  const shut = () => setForm(null)
  return (
    <div style={{ ...card, padding: 14 }}>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
        <strong style={{ flex: "1 1 220px" }}>{goal.title}</strong>
        <GoalStatus status={goal.status} />
        {showPerson && <span style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{goal.employeeName}</span>}
      </div>
      <div style={{ display: "flex", gap: 12, alignItems: "center", margin: "8px 0", fontSize: 13, flexWrap: "wrap" }}>
        <div style={{ width: 160 }}><Bar percent={goal.progress} label={`Progress on ${goal.title}`} /></div>
        <span>{goal.progress}%</span>
        <span style={{ color: goal.overdue ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }}>Due {fmtDate(goal.dueDate)}{goal.overdue ? " (late)" : ""}</span>
        {goal.cycleName && <span style={{ color: "var(--hf-text-faint)" }}>{goal.cycleName}</span>}
      </div>
      {goal.description && <div style={{ fontSize: 13, whiteSpace: "pre-wrap", marginBottom: 6 }}>{goal.description}</div>}
      {goal.measure && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Measured by: {goal.measure}</div>}
      {goal.outcomeNote && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>{GOAL_STATUS[goal.status] ?? goal.status}: {goal.outcomeNote}</div>}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 10 }}>
        {can("APPROVE") && <button onClick={() => approve.send({})} disabled={approve.pending} style={primary}>Approve goal</button>}
        {can("PROGRESS") && <button onClick={() => setForm("progress")} style={plain}>Update progress</button>}
        {can("EDIT") && <button onClick={() => setForm("edit")} style={plain}>Change</button>}
        {can("CLOSE") && <button onClick={() => setForm("close")} style={plain}>Close goal</button>}
        {can("CANCEL") && <button onClick={() => setForm("cancel")} style={plain}>Cancel goal</button>}
        <button onClick={() => setOpen(o => !o)} style={plain}>{open ? "Hide history" : "History"}</button>
      </div>
      {approve.error && <Notice kind="error">{approve.error}</Notice>}
      <div style={{ marginTop: 10 }}>
        {form === "progress" && <ProgressForm goal={goal} done={shut} />}
        {form === "edit" && <EditForm goal={goal} done={shut} />}
        {form === "close" && <CloseForm goal={goal} done={shut} />}
        {form === "cancel" && <CancelForm goal={goal} done={shut} />}
      </div>
      {open && <History events={goal.history} />}
    </div>
  )
}

function History({ events }: { events: GoalView["history"] }) {
  return (
    <ol style={{ margin: "8px 0 0", paddingLeft: 18, fontSize: 12 }}>
      {events.map((e, i) => <li key={`${e.action}-${i}`}><strong>{words(e.action)}</strong>{e.note ? ` – ${e.note}` : ""} <span style={{ color: "var(--hf-text-faint)" }}>· {fmtInstant(e.doneAt)}{e.doneBy ? ` · ${e.doneBy}` : ""}</span></li>)}
    </ol>
  )
}

function ProgressForm({ goal, done }: { goal: GoalView; done: () => void }) {
  const [progress, setProgress] = useState(String(goal.progress))
  const [note, setNote] = useState("")
  const s = useSend<{ progress: number; note: string }, GoalView>("post", `/goals/${goal.id}/progress`, "Progress could not be saved.", done)
  return (
    <FormFrame title="Update progress" label="Save progress" problem={progressProblem(progress)} pending={s.pending} error={s.error} submit={() => s.send({ progress: Number(progress), note })} onCancel={done}>
      <Field id={`pf-gp-${goal.id}`} text="Progress (%)"><input id={`pf-gp-${goal.id}`} type="number" min={0} max={100} value={progress} onChange={e => setProgress(e.target.value)} style={input} /></Field>
      <Field id={`pf-gpn-${goal.id}`} text="Note (optional)"><input id={`pf-gpn-${goal.id}`} value={note} onChange={e => setNote(e.target.value)} style={input} /></Field>
    </FormFrame>
  )
}

function EditForm({ goal, done }: { goal: GoalView; done: () => void }) {
  const [f, setF] = useState({ title: goal.title, description: goal.description ?? "", measure: goal.measure ?? "", dueDate: goal.dueDate })
  const s = useSend<typeof f, GoalView>("put", `/goals/${goal.id}`, "The goal could not be changed.", done)
  return (
    <FormFrame title="Change the goal" label="Save changes" problem={goalProblem(f, false)} pending={s.pending} error={s.error} submit={() => s.send(f)} onCancel={done}>
      <Field id={`pf-ge-t-${goal.id}`} text="Goal"><input id={`pf-ge-t-${goal.id}`} value={f.title} onChange={e => setF({ ...f, title: e.target.value })} style={input} /></Field>
      <Field id={`pf-ge-d-${goal.id}`} text="Details"><textarea id={`pf-ge-d-${goal.id}`} rows={3} value={f.description} onChange={e => setF({ ...f, description: e.target.value })} style={input} /></Field>
      <Field id={`pf-ge-m-${goal.id}`} text="How success is measured"><input id={`pf-ge-m-${goal.id}`} value={f.measure} onChange={e => setF({ ...f, measure: e.target.value })} style={input} /></Field>
      <Field id={`pf-ge-due-${goal.id}`} text="Due date"><input id={`pf-ge-due-${goal.id}`} type="date" value={f.dueDate} onChange={e => setF({ ...f, dueDate: e.target.value })} style={input} /></Field>
    </FormFrame>
  )
}

function CloseForm({ goal, done }: { goal: GoalView; done: () => void }) {
  const [outcome, setOutcome] = useState("")
  const [note, setNote] = useState("")
  const s = useSend<{ outcome: string; note: string }, GoalView>("post", `/goals/${goal.id}/close`, "The goal could not be closed.", done)
  return (
    <FormFrame title="Close the goal" label="Close goal" problem={goalCloseProblem(outcome, note)} pending={s.pending} error={s.error} submit={() => s.send({ outcome, note })} onCancel={done}>
      <Field id={`pf-gc-o-${goal.id}`} text="How did it end?">
        <select id={`pf-gc-o-${goal.id}`} value={outcome} onChange={e => setOutcome(e.target.value)} style={input}>
          <option value="">Choose…</option>{GOAL_OUTCOMES.map(o => <option key={o} value={o}>{GOAL_STATUS[o]}</option>)}
        </select>
      </Field>
      <Field id={`pf-gc-n-${goal.id}`} text="What happened?" hint="Needed unless the goal was achieved."><textarea id={`pf-gc-n-${goal.id}`} rows={3} value={note} onChange={e => setNote(e.target.value)} style={input} /></Field>
    </FormFrame>
  )
}

function CancelForm({ goal, done }: { goal: GoalView; done: () => void }) {
  const [reason, setReason] = useState("")
  const s = useSend<{ reason: string }, GoalView>("post", `/goals/${goal.id}/cancel`, "The goal could not be cancelled.", done)
  return (
    <FormFrame title="Cancel the goal" label="Cancel goal" problem={reasonProblem("The reason for cancelling the goal", reason)} pending={s.pending} error={s.error} submit={() => s.send({ reason })} onCancel={done}>
      <Field id={`pf-gx-${goal.id}`} text="Why?"><input id={`pf-gx-${goal.id}`} value={reason} onChange={e => setReason(e.target.value)} style={input} /></Field>
    </FormFrame>
  )
}
