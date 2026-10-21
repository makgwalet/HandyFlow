// src/pages/customers/TaskQueue.tsx
// Every follow-up in one queue: overdue first, then today, this week and later. Finish, snooze, reassign and re-prioritise
// from the row. Tasks are still created from a customer's own window too; both lists are the same records.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { DEFAULT_FILTER, ESCALATE_AFTER_DAYS, OUTCOME_LABEL, PRIORITIES, PRIORITY_LABEL, SECTION_LABEL, SNOOZE_LABEL, assigneeText, dueText, groupTasks, isEscalated, newTaskBody, newTaskProblem, snoozeDate, taskTotals, filterTasks,
  type NewTask, type Outcome, type Priority, type Scope, type Snooze, type Task, type TaskFilter, type TaskList } from "./tasks.logic"
import { localToday } from "./pipeline.logic"

interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
const inner = (r: { data: unknown }): unknown => { const o = r.data as { data?: unknown } | null; return o && !Array.isArray(o) && typeof o === "object" && "data" in o ? o.data : r.data }
const asList = (r: { data: unknown }): TaskList => { const d = inner(r) as Partial<TaskList> | null; return { tasks: Array.isArray(d?.tasks) ? d.tasks : [], truncated: !!d?.truncated } }
const pageRows = <T,>(r: { data: unknown }): T[] => { const d = inner(r) as { content?: T[] } | T[] | null; return Array.isArray(d) ? d : d?.content ?? [] }
const errorText = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const ghost: React.CSSProperties = { ...field, cursor: "pointer" }
const small: React.CSSProperties = { ...ghost, padding: "3px 8px", fontSize: 12 }
const PRIORITY_COLOUR: Record<Priority, string> = { HIGH: "var(--hf-danger-text)", NORMAL: "var(--hf-text-muted)", LOW: "var(--hf-text-faint)" }

export default function TaskQueue() {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE"), me = useAuthStore(s => s.user?.userId ?? null), today = localToday()
  const [tab, setTab] = useState<"open" | "done">("open"), [filter, setFilter] = useState<TaskFilter>(DEFAULT_FILTER), [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false), [confirmDelete, setConfirmDelete] = useState<string | null>(null), [pickDate, setPickDate] = useState<string | null>(null)
  const { data, isLoading, isError } = useQuery<TaskList>({ queryKey: ["crm", "tasks", tab], queryFn: async () => asList(await apiClient.get("/api/v1/crm/tasks", { params: { done: tab === "done" } })) })
  const { data: users = [] } = useQuery<UserRow[]>({ queryKey: ["crm", "task-users"], retry: false, queryFn: async () => pageRows<UserRow>(await apiClient.get("/api/v1/identity/users", { params: { size: 200 } })) })
  const names = Object.fromEntries(users.map(u => [u.id, [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email || "Colleague"]))
  const refresh = () => { setError(null); qc.invalidateQueries({ queryKey: ["crm", "tasks"] }) }
  const fail = (e: unknown) => setError(errorText(e))
  const finish = useMutation({ mutationFn: (v: { t: Task; outcome: Outcome }) => apiClient.post(`/api/v1/crm/customers/${v.t.customerId}/followups/${v.t.id}/complete`, { outcome: v.outcome }), onSuccess: refresh, onError: fail })
  const reopen = useMutation({ mutationFn: (t: Task) => apiClient.post(`/api/v1/crm/customers/${t.customerId}/followups/${t.id}/reopen`), onSuccess: refresh, onError: fail })
  const remove = useMutation({ mutationFn: (t: Task) => apiClient.delete(`/api/v1/crm/customers/${t.customerId}/followups/${t.id}`), onSuccess: () => { setConfirmDelete(null); refresh() }, onError: fail })
  const change = useMutation({ mutationFn: (v: { id: string; body: { assignedTo?: string; dueDate?: string; priority?: Priority } }) => apiClient.patch(`/api/v1/crm/tasks/${v.id}`, v.body), onSuccess: () => { setPickDate(null); refresh() }, onError: fail })
  const busy = finish.isPending || reopen.isPending || remove.isPending || change.isPending

  const all = data?.tasks ?? [], shown = filterTasks(all, filter, me), totals = taskTotals(all, today)
  const groups = tab === "open" ? groupTasks(shown, today) : []

  return (
    <div>
      <div role="tablist" aria-label="Task lists" style={{ display: "flex", gap: 6, marginBottom: 10 }}>
        {(["open", "done"] as const).map(t => <button key={t} type="button" role="tab" aria-selected={tab === t} style={{ ...small, fontWeight: tab === t ? 700 : 500 }} onClick={() => setTab(t)}>{t === "open" ? "Open" : "Done (last 30 days)"}</button>)}
      </div>
      {tab === "open" && (
        <div aria-label="Task totals" style={{ display: "flex", gap: 18, flexWrap: "wrap", fontSize: 13, marginBottom: 10 }}>
          <span><strong>{totals.open}</strong> open</span>
          <span style={{ color: totals.overdue ? "var(--hf-danger-text)" : undefined }}><strong>{totals.overdue}</strong> overdue</span>
          <span><strong>{totals.today}</strong> due today</span>
          <span><strong>{totals.week}</strong> in the next 7 days</span>
          {totals.escalated > 0 && <span style={{ color: "var(--hf-danger-text)" }}><strong>{totals.escalated}</strong> late by {ESCALATE_AFTER_DAYS}+ days</span>}
        </div>)}
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 10 }}>
        <select aria-label="Whose tasks" style={field} value={filter.scope} onChange={e => setFilter({ ...filter, scope: e.target.value as Scope })}><option value="mine">My tasks</option><option value="all">Everyone's tasks</option></select>
        <select aria-label="Priority filter" style={field} value={filter.priority} onChange={e => setFilter({ ...filter, priority: e.target.value as Priority | "" })}><option value="">Any priority</option>{PRIORITIES.map(p => <option key={p} value={p}>{PRIORITY_LABEL[p]}</option>)}</select>
        <input aria-label="Search tasks" placeholder="Search customer or note" style={{ ...field, minWidth: 220 }} value={filter.search} onChange={e => setFilter({ ...filter, search: e.target.value })} />
        {canEdit && tab === "open" && <button type="button" style={{ ...ghost, fontWeight: 700, marginLeft: "auto" }} onClick={() => setAdding(!adding)}>{adding ? "Close" : "New task"}</button>}
      </div>
      {adding && canEdit && <NewTaskForm me={me} users={users} today={today} onDone={() => { setAdding(false); refresh() }} />}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginBottom: 8 }}>{error}</div>}
      {data?.truncated && <div role="status" style={{ fontSize: 12.5, color: "var(--hf-warning-text)", marginBottom: 8 }}>There are more tasks than this list holds. Finish or reassign some to see the rest.</div>}
      {isLoading && <p>Loading…</p>}
      {isError && <div role="alert">Couldn't load the tasks.</div>}
      {!isLoading && !isError && all.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{tab === "open" ? "No open tasks. Schedule a follow-up from a customer, or add a task here." : "Nothing finished in the last 30 days."}</p>}
      {all.length > 0 && shown.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No tasks match these filters.{filter.scope === "mine" && " Switch to everyone's tasks to see the rest."}</p>}

      {groups.map(g => (
        <section key={g.section} aria-label={`${SECTION_LABEL[g.section]} tasks`} style={{ marginBottom: 14 }}>
          <h4 style={{ margin: "0 0 6px", fontSize: 13, color: g.section === "overdue" ? "var(--hf-danger-text)" : undefined }}>{SECTION_LABEL[g.section]} ({g.tasks.length})</h4>
          {g.tasks.map(t => (
            <article key={t.id} aria-label={`${t.customerName}: ${t.note}`} style={{ border: "1px solid var(--hf-border)", borderRadius: 8, padding: 9, marginBottom: 6, fontSize: 13, background: "var(--hf-surface)" }}>
              <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "baseline" }}>
                <strong>{t.customerName}</strong>
                <span style={{ color: PRIORITY_COLOUR[t.priority], fontSize: 12 }}>{PRIORITY_LABEL[t.priority]} priority</span>
                <span style={{ color: g.section === "overdue" ? "var(--hf-danger-text)" : "var(--hf-text-muted)", fontSize: 12.5 }}>{dueText(t, today)}</span>
                {isEscalated(t, today) && <span style={{ color: "var(--hf-danger-text)", fontSize: 11.5, fontWeight: 700 }}>ESCALATED</span>}
                <span style={{ color: "var(--hf-text-muted)", fontSize: 12.5, marginLeft: "auto" }}>{assigneeText(t.assignedTo, me, names)}</span>
              </div>
              <div style={{ margin: "3px 0 6px" }}>{t.note}</div>
              {canEdit && (
                <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
                  <button type="button" style={{ ...small, fontWeight: 700 }} disabled={busy} aria-label={`Done: ${t.customerName}`} onClick={() => finish.mutate({ t, outcome: "COMPLETED" })}>Done</button>
                  <button type="button" style={small} disabled={busy} aria-label={`No response: ${t.customerName}`} onClick={() => finish.mutate({ t, outcome: "NO_RESPONSE" })}>No response</button>
                  <select aria-label={`Snooze ${t.customerName}`} style={small} value="" disabled={busy} onChange={e => { const v = e.target.value; if (v === "pick") setPickDate(t.id); else if (v) change.mutate({ id: t.id, body: { dueDate: snoozeDate(v as Snooze, today) } }) }}>
                    <option value="">Snooze…</option>{(Object.keys(SNOOZE_LABEL) as Snooze[]).map(k => <option key={k} value={k}>{SNOOZE_LABEL[k]}</option>)}<option value="pick">Pick a date…</option></select>
                  <select aria-label={`Assign ${t.customerName}`} style={small} value="" disabled={busy} onChange={e => e.target.value && change.mutate({ id: t.id, body: { assignedTo: e.target.value } })}>
                    <option value="">Assign to…</option>{me && <option value={me}>Me</option>}{users.filter(u => u.id !== me).map(u => <option key={u.id} value={u.id}>{names[u.id]}</option>)}</select>
                  <select aria-label={`Priority ${t.customerName}`} style={small} value={t.priority} disabled={busy} onChange={e => change.mutate({ id: t.id, body: { priority: e.target.value as Priority } })}>
                    {PRIORITIES.map(p => <option key={p} value={p}>{PRIORITY_LABEL[p]}</option>)}</select>
                  {confirmDelete === t.id
                    ? <><button type="button" style={{ ...small, color: "var(--hf-danger-text)", fontWeight: 700 }} disabled={busy} aria-label={`Confirm delete: ${t.customerName}`} onClick={() => remove.mutate(t)}>Confirm delete</button><button type="button" style={small} onClick={() => setConfirmDelete(null)}>Cancel</button></>
                    : <button type="button" style={small} aria-label={`Delete: ${t.customerName}`} onClick={() => setConfirmDelete(t.id)}>Delete</button>}
                </div>)}
              {pickDate === t.id && <PickDate today={today} saving={change.isPending} onCancel={() => setPickDate(null)} onSave={d => change.mutate({ id: t.id, body: { dueDate: d } })} />}
            </article>))}
        </section>))}

      {tab === "done" && shown.map(t => (
        <article key={t.id} aria-label={`${t.customerName}: ${t.note}`} style={{ border: "1px solid var(--hf-border-subtle)", borderRadius: 8, padding: 8, marginBottom: 6, fontSize: 13, opacity: 0.85 }}>
          <strong>{t.customerName}</strong> <span style={{ color: "var(--hf-text-muted)" }}>· {t.outcome ? OUTCOME_LABEL[t.outcome] : "Done"}{t.completedAt ? ` · ${t.completedAt.slice(0, 10)}` : ""}</span>
          <div>{t.note}</div>
          {canEdit && <button type="button" style={{ ...small, marginTop: 4 }} disabled={busy} aria-label={`Reopen: ${t.customerName}`} onClick={() => reopen.mutate(t)}>Reopen</button>}
        </article>))}
    </div>
  )
}

function PickDate({ today, saving, onSave, onCancel }: { today: string; saving: boolean; onSave: (d: string) => void; onCancel: () => void }) {
  const [d, setD] = useState(today)
  const bad = !d || d < today
  return (
    <div style={{ display: "flex", gap: 6, alignItems: "center", marginTop: 6 }}>
      <input aria-label="New due date" type="date" min={today} style={field} value={d} onChange={e => setD(e.target.value)} />
      <button type="button" style={{ ...small, fontWeight: 700 }} disabled={bad || saving} onClick={() => onSave(d)}>Move</button><button type="button" style={small} onClick={onCancel}>Cancel</button>
      {d && d < today && <span style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>Not in the past.</span>}
    </div>
  )
}

function NewTaskForm({ me, users, today, onDone }: { me: string | null; users: UserRow[]; today: string; onDone: () => void }) {
  const [term, setTerm] = useState(""), [results, setResults] = useState<{ id: string; name: string }[] | null>(null), [searchError, setSearchError] = useState<string | null>(null), [chosen, setChosen] = useState<{ id: string; name: string } | null>(null)
  const [task, setTask] = useState<NewTask>({ customerId: "", note: "", dueDate: today, priority: "NORMAL", assignedTo: me }), [error, setError] = useState<string | null>(null)
  const problem = newTaskProblem(task, today)
  const search = async () => {
    if (term.trim().length < 2) { setSearchError("Type at least 2 letters of the customer's name."); return }
    setSearchError(null)
    try { setResults(pageRows<{ id: string; name: string }>(await apiClient.get("/api/v1/crm/customers", { params: { search: term.trim(), size: 8 } }))) } catch { setSearchError("Customer search failed. Try again.") }
  }
  const create = useMutation({ mutationFn: () => apiClient.post(`/api/v1/crm/customers/${task.customerId}/followups`, newTaskBody(task)), onSuccess: onDone, onError: e => setError(errorText(e)) })
  return (
    <form aria-label="New task" onSubmit={e => { e.preventDefault(); if (!problem) create.mutate() }} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 10, marginBottom: 10, display: "grid", gap: 8, maxWidth: 560 }}>
      <div>
        {chosen ? <div>Customer: <strong>{chosen.name}</strong> <button type="button" style={small} onClick={() => { setChosen(null); setTask({ ...task, customerId: "" }) }}>Change</button></div>
          : <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
            <input aria-label="Find customer" placeholder="Find the customer" style={{ ...field, flex: 1, minWidth: 200 }} value={term} onChange={e => setTerm(e.target.value)} onKeyDown={e => { if (e.key === "Enter") { e.preventDefault(); void search() } }} />
            <button type="button" style={ghost} onClick={() => void search()}>Search</button></div>}
        {searchError && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5 }}>{searchError}</div>}
        {!chosen && results && (results.length === 0 ? <div style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>No customer found.</div>
          : <ul aria-label="Customer results" style={{ listStyle: "none", margin: "4px 0 0", padding: 0, border: "1px solid var(--hf-border)", borderRadius: 8 }}>{results.map(r => <li key={r.id}><button type="button" style={{ ...ghost, border: "none", width: "100%", textAlign: "left" }} onClick={() => { setChosen(r); setTask({ ...task, customerId: r.id }); setResults(null) }}>{r.name}</button></li>)}</ul>)}
      </div>
      <input aria-label="What needs doing" placeholder="What needs doing?" maxLength={500} style={field} value={task.note} onChange={e => setTask({ ...task, note: e.target.value })} />
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <label style={{ fontSize: 12.5 }}>Due <input aria-label="Due date" type="date" min={today} style={field} value={task.dueDate} onChange={e => setTask({ ...task, dueDate: e.target.value })} /></label>
        <select aria-label="New task priority" style={field} value={task.priority} onChange={e => setTask({ ...task, priority: e.target.value as Priority })}>{PRIORITIES.map(p => <option key={p} value={p}>{PRIORITY_LABEL[p]} priority</option>)}</select>
        <select aria-label="Assigned to" style={field} value={task.assignedTo ?? ""} onChange={e => setTask({ ...task, assignedTo: e.target.value || null })}>
          {me && <option value={me}>Me</option>}{users.filter(u => u.id !== me).map(u => <option key={u.id} value={u.id}>{[u.firstName, u.lastName].filter(Boolean).join(" ") || u.email}</option>)}</select>
      </div>
      {problem && (task.customerId || task.note) && <span style={{ color: "var(--hf-text-muted)", fontSize: 12.5 }}>{problem}</span>}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5 }}>{error}</div>}
      <div><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!problem || create.isPending}>{create.isPending ? "Saving…" : "Add task"}</button></div>
    </form>
  )
}
