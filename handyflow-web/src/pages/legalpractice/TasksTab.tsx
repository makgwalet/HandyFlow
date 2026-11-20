// Tasks on matters, and workflow templates that create a set of tasks in one go. A person walled off from a matter does not see its tasks here.
// Nothing is deleted: a task is cancelled with a reason, and every change is written to the task's history.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Field, Input, Pager, Pick, Pill, ReasonDialog, SectionTitle, card, day, errorText, inp, muted, pageOf, td, th } from "./kit"
import { BASE } from "./lookups"
import type { Matter } from "./MattersTab"
import {
  TASK_ASSIGN_TO, TASK_PRIORITIES, TASK_STATUSES, applyProblem, emptyStep, emptyTask, emptyWorkflow, isoDay, moveStep, stepDue, taskActions, taskDueText, taskEventText, taskPriorityLabel,
  taskPriorityTone, taskProblem, taskReasonProblem, taskRequest, taskStatusLabel, taskTone, taskToForm, workflowProblem, workflowRequest, workflowToForm,
  type ApplyResult, type TaskDetail, type TaskForm, type TaskSummary, type WorkflowDetail, type WorkflowForm, type WorkflowSummary,
} from "./legal.logic"

const URL = `${BASE}/tasks`
const unwrap = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
interface Staff { id: string; firstName?: string; lastName?: string; email?: string }
const staffName = (u: Staff) => `${u.firstName ?? ""} ${u.lastName ?? ""}`.trim() || u.email || u.id
const useStaff = () => useQuery({ queryKey: ["lp-task-staff"], queryFn: async () => { const r = await apiClient.get("/api/v1/identity/users"); const d = r.data?.data ?? r.data; return (Array.isArray(d) ? d : d?.content ?? []) as Staff[] } })
const useMatters = (enabled = true) => useQuery({ enabled, queryKey: ["lp-task-matters"], queryFn: async () => pageOf<Matter>(await apiClient.get(`${BASE}/matters`, { params: { size: 500 } })) })
const matterOptions = (ms?: { content: Matter[] }) => (ms?.content ?? []).filter(m => m.status === "OPEN" || m.status === "ON_HOLD").map(m => [m.id, `${m.matterNumber} ${m.matterName}`] as const)

export default function TasksTab() {
  const [view, setView] = useState<"mine" | "all" | "workflows">("mine"), [adding, setAdding] = useState(false), [openId, setOpenId] = useState<string | null>(null)
  const write = usePermissions(["LEGALPRACTICE_TASK_WRITE", "LEGALPRACTICE_ADMIN"])
  return (
    <div>
      <SectionTitle right={write && <Btn kind="primary" onClick={() => setAdding(true)}>Add a task</Btn>}>Tasks</SectionTitle>
      <div style={{ display: "flex", gap: 6, marginBottom: 10 }}>
        <Btn small kind={view === "mine" ? "primary" : undefined} onClick={() => setView("mine")}>My tasks</Btn>
        <Btn small kind={view === "all" ? "primary" : undefined} onClick={() => setView("all")}>All tasks</Btn>
        <Btn small kind={view === "workflows" ? "primary" : undefined} onClick={() => setView("workflows")}>Workflows</Btn>
      </div>
      {view === "workflows" ? <Workflows /> : <TaskList key={view} mine={view === "mine"} onOpen={setOpenId} />}
      {adding && <TaskFormDialog creating onClose={() => setAdding(false)} onDone={id => { setAdding(false); setOpenId(id) }} />}
      {openId && <TaskDialog id={openId} onClose={() => setOpenId(null)} />}
    </div>
  )
}

// ---- the lists -------------------------------------------------------------------------------------------------------------

function TaskList({ mine, onOpen }: { mine: boolean; onOpen: (id: string) => void }) {
  const [search, setSearch] = useState(""), [status, setStatus] = useState(""), [closed, setClosed] = useState(false), [overdue, setOverdue] = useState(false), [page, setPage] = useState(0)
  const today = isoDay(new Date())
  const list = useQuery({
    queryKey: ["lp-tasks", mine, search, status, closed, overdue, page],
    queryFn: async () => pageOf<TaskSummary>(await apiClient.get(URL, { params: { mine, search: search.trim() || undefined, status: status || undefined, includeClosed: closed || status === "DONE" || status === "CANCELLED", overdueOnly: overdue, page, size: 20 } })),
  })
  const rows = list.data?.content ?? []
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end", marginBottom: 8 }}>
        <div style={{ minWidth: 220, flex: "1 1 220px" }}><Input label="Search by title" value={search} onChange={v => { setSearch(v); setPage(0) }} /></div>
        <div style={{ minWidth: 160 }}><Pick label="Status" value={status} onChange={v => { setStatus(v); setPage(0) }} blank="Open and in progress" options={TASK_STATUSES} /></div>
        <label style={{ fontSize: 13, display: "inline-flex", gap: 6, alignItems: "center", marginBottom: 14 }}><input type="checkbox" checked={overdue} onChange={e => { setOverdue(e.target.checked); setPage(0) }} /> Overdue only</label>
        <label style={{ fontSize: 13, display: "inline-flex", gap: 6, alignItems: "center", marginBottom: 14 }}><input type="checkbox" checked={closed} onChange={e => { setClosed(e.target.checked); setPage(0) }} /> Include done and cancelled</label>
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {list.isError ? <Empty>Could not load the tasks.</Empty> : list.isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>{mine ? "Nothing assigned to you." : "No tasks found."}</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Task</th><th style={th}>Matter</th><th style={th}>Assigned to</th><th style={th}>Due</th><th style={th}>Status</th><th style={th} /></tr></thead>
            <tbody>{rows.map(t => (
              <tr key={t.id}>
                <td style={td}><b>{t.title}</b>{t.priority !== "NORMAL" && <div style={{ marginTop: 3 }}><Pill tone={taskPriorityTone(t.priority)}>{taskPriorityLabel(t.priority)}</Pill></div>}</td>
                <td style={td}>{t.matterLabel}</td>
                <td style={td}>{t.assigneeName ?? "—"}</td>
                <td style={td}>{t.dueDate ? day(t.dueDate) : "—"}<div style={muted}>{taskDueText(t.dueDate, t.status, today)}</div></td>
                <td style={td}><Pill tone={taskTone(t.status, t.overdue)}>{t.overdue ? "Overdue" : taskStatusLabel(t.status)}</Pill></td>
                <td style={{ ...td, textAlign: "right" }}><Btn small onClick={() => onOpen(t.id)}>Open</Btn></td>
              </tr>))}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={list.data?.totalPages ?? 0} onPage={setPage} />
    </div>
  )
}

// ---- add or change a task --------------------------------------------------------------------------------------------------

function TaskFormDialog({ creating, current, onClose, onDone }: { creating: boolean; current?: TaskDetail; onClose: () => void; onDone: (id: string) => void }) {
  const qc = useQueryClient(), me = useAuthStore(s => s.user?.userId)
  const [f, setF] = useState<TaskForm>(current ? taskToForm(current) : { ...emptyTask(), assigneeId: me ?? "" }), [err, setErr] = useState("")
  const set = (k: keyof TaskForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const matters = useMatters(creating), staff = useStaff()
  const people = staff.data ?? [], problem = taskProblem(f, creating)
  const save = useMutation({
    mutationFn: async () => unwrap<TaskDetail>(creating
      ? await apiClient.post(URL, taskRequest(f, true, staffName(people.find(p => p.id === f.assigneeId) ?? { id: f.assigneeId })))
      : await apiClient.put(`${URL}/${current!.task.id}`, taskRequest(f, false, null))),
    onSuccess: d => { toast.success(creating ? "Task added" : "Saved"); qc.invalidateQueries({ queryKey: ["lp-tasks"] }); qc.setQueryData(["lp-task", d.task.id], d); onDone(d.task.id) },
    onError: async e => setErr(await errorText(e, "Could not save the task")),
  })
  return (
    <Dialog title={creating ? "Add a task" : "Change the task"} width={560} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : creating ? "Add task" : "Save"}</Btn></>}>
      {creating && <Pick label="Matter" value={f.matterId} onChange={set("matterId")} blank={matters.isLoading ? "Loading…" : "Choose a matter"} options={matterOptions(matters.data)} />}
      <Input label="What needs to be done" value={f.title} onChange={set("title")} />
      <Field label="Details"><textarea value={f.description} onChange={e => set("description")(e.target.value)} rows={3} style={inp} /></Field>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: "0 12px" }}>
        <Pick label="Priority" value={f.priority} onChange={set("priority")} options={TASK_PRIORITIES} />
        <Input label="Due date" type="date" value={f.dueDate} onChange={set("dueDate")} />
        {creating && <Pick label="Assigned to" value={f.assigneeId} onChange={set("assigneeId")} blank="Nobody yet" options={people.map(p => [p.id, staffName(p)] as const)} />}
      </div>
      {problem && (f.title || f.matterId) && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

// ---- one task --------------------------------------------------------------------------------------------------------------

type Step = null | "edit" | "assign" | "reopen" | "cancel"

function TaskDialog({ id, onClose }: { id: string; onClose: () => void }) {
  const qc = useQueryClient(), today = isoDay(new Date())
  const write = usePermissions(["LEGALPRACTICE_TASK_WRITE", "LEGALPRACTICE_ADMIN"])
  const [step, setStep] = useState<Step>(null), [err, setErr] = useState("")
  const q = useQuery({ queryKey: ["lp-task", id], queryFn: async () => unwrap<TaskDetail>(await apiClient.get(`${URL}/${id}`)) })
  const d = q.data, a = d ? taskActions(d.task.status, write) : null
  const changed = (r: TaskDetail) => { qc.setQueryData(["lp-task", id], r); qc.invalidateQueries({ queryKey: ["lp-tasks"] }); setStep(null) }
  const act = useMutation({
    mutationFn: async (path: string) => unwrap<TaskDetail>(await apiClient.post(`${URL}/${id}/${path}`)),
    onSuccess: changed, onError: async e => setErr(await errorText(e, "Could not do that")),
  })
  const withReason = (path: "reopen" | "cancel") => async (reason: string) => {
    const problem = taskReasonProblem(reason, path === "cancel" ? "why it is being cancelled" : "why it is being reopened")
    if (problem) throw { response: { data: { message: problem } } } // shaped like a server error so the dialog shows it
    changed(unwrap<TaskDetail>(await apiClient.post(`${URL}/${id}/${path}`, { reason: reason.trim() }))) }
  return (
    <Dialog title={d ? d.task.title : "Task"} width={680} onClose={onClose}>
      {q.isError ? <Empty>Could not load this task.</Empty> : !d || !a ? <Empty>Loading…</Empty> : (
        <div>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 6 }}>
            <Pill tone={taskTone(d.task.status, d.task.overdue)}>{d.task.overdue ? "Overdue" : taskStatusLabel(d.task.status)}</Pill>
            <Pill tone={taskPriorityTone(d.task.priority)}>{taskPriorityLabel(d.task.priority)} priority</Pill>
          </div>
          <div style={muted}>{d.task.matterLabel}</div>
          <div style={muted}>Assigned to {d.task.assigneeName ?? "nobody"}{d.task.dueDate ? ` · Due ${day(d.task.dueDate)}` : ""}{taskDueText(d.task.dueDate, d.task.status, today) ? ` (${taskDueText(d.task.dueDate, d.task.status, today)})` : ""}</div>
          {d.description && <div style={{ marginTop: 8, whiteSpace: "pre-wrap" }}>{d.description}</div>}
          {d.task.status === "DONE" && <div style={{ ...muted, marginTop: 6 }}>Completed{d.completedByName ? ` by ${d.completedByName}` : ""}{d.completedAt ? ` on ${day(d.completedAt.slice(0, 10))}` : ""}</div>}
          {d.task.status === "CANCELLED" && <div style={{ ...muted, marginTop: 6 }}>Cancelled: {d.cancelReason}</div>}
          {err && <ErrorLine text={err} />}
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", margin: "12px 0" }}>
            {a.canStart && <Btn small kind="primary" disabled={act.isPending} onClick={() => { setErr(""); act.mutate("start") }}>Start</Btn>}
            {a.canStop && <Btn small disabled={act.isPending} onClick={() => { setErr(""); act.mutate("stop") }}>Put back to open</Btn>}
            {a.canComplete && <Btn small kind="primary" disabled={act.isPending} onClick={() => { setErr(""); act.mutate("complete") }}>Mark done</Btn>}
            {a.canAssign && <Btn small onClick={() => setStep("assign")}>Reassign</Btn>}
            {a.canEdit && <Btn small onClick={() => setStep("edit")}>Change</Btn>}
            {a.canReopen && <Btn small onClick={() => setStep("reopen")}>Reopen</Btn>}
            {a.canCancel && <Btn small kind="danger" onClick={() => setStep("cancel")}>Cancel the task</Btn>}
          </div>
          <div style={{ fontWeight: 700, margin: "6px 0" }}>History</div>
          <div style={{ ...card, padding: 8 }}>
            {d.events.length === 0 ? <div style={muted}>Nothing yet.</div> : d.events.map((e, i) => (
              <div key={i} style={{ padding: "4px 0", fontSize: 13 }}>{taskEventText(e)}<span style={muted}> · {day(e.occurredAt.slice(0, 10))}</span></div>))}
          </div>
          {step === "edit" && <TaskFormDialog creating={false} current={d} onClose={() => setStep(null)} onDone={() => { qc.invalidateQueries({ queryKey: ["lp-task", id] }); setStep(null) }} />}
          {step === "assign" && <AssignDialog id={id} current={d.task.assigneeId} onClose={() => setStep(null)} onDone={changed} />}
          {step === "reopen" && <ReasonDialog title="Reopen the task" label="Why it is being reopened" confirm="Reopen" onSubmit={withReason("reopen")} onClose={() => setStep(null)} />}
          {step === "cancel" && <ReasonDialog title="Cancel the task" label="Why it is being cancelled" confirm="Cancel the task" danger onSubmit={withReason("cancel")} onClose={() => setStep(null)} />}
        </div>)}
    </Dialog>
  )
}

function AssignDialog({ id, current, onClose, onDone }: { id: string; current: string | null; onClose: () => void; onDone: (d: TaskDetail) => void }) {
  const staff = useStaff(), [who, setWho] = useState(current ?? ""), [err, setErr] = useState("")
  const people = staff.data ?? [], same = who === (current ?? "")
  const save = useMutation({
    mutationFn: async () => unwrap<TaskDetail>(await apiClient.post(`${URL}/${id}/assign`, { assigneeId: who || null, assigneeName: who ? staffName(people.find(p => p.id === who) ?? { id: who }) : null })),
    onSuccess: d => { toast.success("Reassigned"); onDone(d) }, onError: async e => setErr(await errorText(e, "Could not reassign the task")),
  })
  return (
    <Dialog title="Reassign the task" width={420} onClose={onClose} footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={same || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Reassign"}</Btn></>}>
      <Pick label="Assigned to" value={who} onChange={setWho} blank={staff.isLoading ? "Loading…" : "Nobody"} options={people.map(p => [p.id, staffName(p)] as const)} />
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

// ---- workflows -------------------------------------------------------------------------------------------------------------

function Workflows() {
  const qc = useQueryClient()
  const manage = usePermissions(["LEGALPRACTICE_WORKFLOW_MANAGE", "LEGALPRACTICE_ADMIN"]), apply = usePermissions(["LEGALPRACTICE_WORKFLOW_MANAGE", "LEGALPRACTICE_TASK_WRITE", "LEGALPRACTICE_ADMIN"])
  const [off, setOff] = useState(false), [editing, setEditing] = useState<"new" | string | null>(null), [applying, setApplying] = useState<string | null>(null), [err, setErr] = useState("")
  const list = useQuery({ queryKey: ["lp-workflows", off], queryFn: async () => unwrap<WorkflowSummary[]>(await apiClient.get(`${URL}/workflows`, { params: { includeOff: off } })) })
  const toggle = useMutation({
    mutationFn: async (w: WorkflowSummary) => apiClient.post(`${URL}/workflows/${w.id}/${w.active ? "turn-off" : "turn-on"}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["lp-workflows"] }), onError: async e => setErr(await errorText(e, "Could not change the workflow")),
  })
  const rows = list.data ?? []
  return (
    <div>
      <div style={{ display: "flex", gap: 10, alignItems: "center", justifyContent: "space-between", marginBottom: 8, flexWrap: "wrap" }}>
        <div style={muted}>A workflow is a list of steps. Applying it to a matter adds one task per step, each due a set number of days after the start date.</div>
        <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
          <label style={{ fontSize: 13, display: "inline-flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={off} onChange={e => setOff(e.target.checked)} /> Show turned off</label>
          {manage && <Btn kind="primary" onClick={() => setEditing("new")}>New workflow</Btn>}
        </div>
      </div>
      {err && <ErrorLine text={err} />}
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {list.isError ? <Empty>Could not load the workflows.</Empty> : list.isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No workflows yet.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Workflow</th><th style={th}>For</th><th style={th}>Steps</th><th style={th} /></tr></thead>
            <tbody>{rows.map(w => (
              <tr key={w.id}>
                <td style={td}><b>{w.name}</b>{!w.active && <> <Pill>Off</Pill></>}{w.description && <div style={muted}>{w.description}</div>}</td>
                <td style={td}>{w.matterType ?? "Any matter"}</td>
                <td style={td}>{w.steps}</td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                  {apply && w.active && <Btn small kind="primary" onClick={() => setApplying(w.id)}>Apply to a matter</Btn>}{" "}
                  {manage && <Btn small onClick={() => setEditing(w.id)}>Change</Btn>}{" "}
                  {manage && <Btn small disabled={toggle.isPending} onClick={() => { setErr(""); toggle.mutate(w) }}>{w.active ? "Turn off" : "Turn on"}</Btn>}
                </td>
              </tr>))}</tbody>
          </table>)}
      </div>
      {editing && <WorkflowDialog id={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
      {applying && <ApplyDialog id={applying} onClose={() => setApplying(null)} />}
    </div>
  )
}

function WorkflowDialog({ id, onClose }: { id: string | null; onClose: () => void }) {
  const q = useQuery({ enabled: !!id, queryKey: ["lp-workflow", id], queryFn: async () => unwrap<WorkflowDetail>(await apiClient.get(`${URL}/workflows/${id}`)) })
  if (id && q.isError) return <Dialog title="Workflow" onClose={onClose}><Empty>Could not load this workflow.</Empty></Dialog>
  if (id && !q.data) return <Dialog title="Workflow" onClose={onClose}><Empty>Loading…</Empty></Dialog>
  return <WorkflowForm id={id} initial={q.data ? workflowToForm(q.data) : emptyWorkflow()} onClose={onClose} />
}

function WorkflowForm({ id, initial, onClose }: { id: string | null; initial: WorkflowForm; onClose: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState<WorkflowForm>(initial), [err, setErr] = useState("")
  const problem = workflowProblem(f)
  const setStep = (i: number, k: keyof WorkflowForm["steps"][number]) => (v: string) => setF(s => ({ ...s, steps: s.steps.map((x, j) => (j === i ? { ...x, [k]: v } : x)) }))
  const save = useMutation({
    mutationFn: async () => (id ? apiClient.put(`${URL}/workflows/${id}`, workflowRequest(f)) : apiClient.post(`${URL}/workflows`, workflowRequest(f))),
    onSuccess: () => { toast.success(id ? "Saved" : "Workflow added"); qc.invalidateQueries({ queryKey: ["lp-workflows"] }); qc.invalidateQueries({ queryKey: ["lp-workflow", id] }); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the workflow")),
  })
  return (
    <Dialog title={id ? "Change the workflow" : "New workflow"} width={760} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "0 12px" }}>
        <Input label="Name" value={f.name} onChange={v => setF(s => ({ ...s, name: v }))} />
        <Input label="Kind of matter it is for (optional)" value={f.matterType} onChange={v => setF(s => ({ ...s, matterType: v }))} hint="You are warned if it is applied to a different kind" />
      </div>
      <Input label="Description" value={f.description} onChange={v => setF(s => ({ ...s, description: v }))} />
      <div style={{ fontWeight: 700, margin: "6px 0" }}>Steps</div>
      {f.steps.map((s, i) => (
        <div key={i} style={{ ...card, padding: 10, marginBottom: 8 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: "0 12px" }}>
            <div style={{ gridColumn: "span 2" }}><Input label={`Step ${i + 1}`} value={s.title} onChange={setStep(i, "title")} /></div>
            <Pick label="Priority" value={s.priority} onChange={setStep(i, "priority")} options={TASK_PRIORITIES} />
            <Input label="Days after start" type="number" min={0} max={730} value={s.offsetDays} onChange={setStep(i, "offsetDays")} />
            <Pick label="Assigned to" value={s.assignTo} onChange={setStep(i, "assignTo")} options={TASK_ASSIGN_TO} />
          </div>
          <div style={{ display: "flex", gap: 6 }}>
            <Btn small disabled={i === 0} onClick={() => setF(x => ({ ...x, steps: moveStep(x.steps, i, i - 1) }))}>Move up</Btn>
            <Btn small disabled={i === f.steps.length - 1} onClick={() => setF(x => ({ ...x, steps: moveStep(x.steps, i, i + 1) }))}>Move down</Btn>
            <Btn small disabled={f.steps.length === 1} onClick={() => setF(x => ({ ...x, steps: x.steps.filter((_, j) => j !== i) }))}>Remove</Btn>
          </div>
        </div>))}
      <Btn small disabled={f.steps.length >= 30} onClick={() => setF(s => ({ ...s, steps: [...s.steps, emptyStep()] }))}>Add a step</Btn>
      {problem && <div style={{ ...muted, marginTop: 8 }}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function ApplyDialog({ id, onClose }: { id: string; onClose: () => void }) {
  const qc = useQueryClient(), today = isoDay(new Date())
  const [matterId, setMatterId] = useState(""), [start, setStart] = useState(today), [err, setErr] = useState(""), [result, setResult] = useState<ApplyResult | null>(null)
  const matters = useMatters(), q = useQuery({ queryKey: ["lp-workflow", id], queryFn: async () => unwrap<WorkflowDetail>(await apiClient.get(`${URL}/workflows/${id}`)) })
  const problem = applyProblem(matterId, start, today)
  const run = useMutation({
    mutationFn: async () => unwrap<ApplyResult>(await apiClient.post(`${URL}/workflows/${id}/apply`, { matterId, startDate: start })),
    onSuccess: r => { toast.success(`${r.tasks.length} tasks added`); qc.invalidateQueries({ queryKey: ["lp-tasks"] }); setResult(r) },
    onError: async e => setErr(await errorText(e, "Could not apply the workflow")),
  })
  const w = q.data
  return (
    <Dialog title={w ? `Apply “${w.template.name}”` : "Apply workflow"} width={620} onClose={onClose}
      footer={result ? <Btn kind="primary" onClick={onClose}>Done</Btn> : <><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || run.isPending || !w} onClick={() => { setErr(""); run.mutate() }}>{run.isPending ? "Adding…" : "Add the tasks"}</Btn></>}>
      {result ? (
        <div>
          <div>{result.tasks.length} tasks were added to the matter.</div>
          {result.warnings.map(x => <div key={x} style={{ ...muted, marginTop: 6, color: "var(--hf-warning-text, inherit)" }}>⚠ {x}</div>)}
          <div style={{ ...card, padding: 0, marginTop: 10 }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}><tbody>{result.tasks.map(t => <tr key={t.id}><td style={td}>{t.title}</td><td style={td}>{t.dueDate ? day(t.dueDate) : "—"}</td><td style={td}>{t.assigneeName ?? "—"}</td></tr>)}</tbody></table>
          </div>
        </div>
      ) : (
        <div>
          <Pick label="Matter" value={matterId} onChange={setMatterId} blank={matters.isLoading ? "Loading…" : "Choose a matter"} options={matterOptions(matters.data)} />
          <Input label="Start date" type="date" value={start} onChange={setStart} />
          {w && start && (
            <div style={{ ...card, padding: 0, marginBottom: 8 }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>Step</th><th style={th}>Due</th></tr></thead>
                <tbody>{w.steps.map(s => <tr key={s.stepNo}><td style={td}>{s.title}</td><td style={td}>{day(stepDue(start, s.offsetDays))}</td></tr>)}</tbody>
              </table>
            </div>)}
          {problem && matterId !== "" && <div style={muted}>{problem}</div>}
          {err && <ErrorLine text={err} />}
        </div>)}
    </Dialog>
  )
}
