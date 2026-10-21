// src/pages/customers/WorkflowsPanel.tsx
// Automation: rules that act when a lead is added, changes stage or an opportunity is won, and a log of what they did. The server runs them; this sends and shows.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { errorMessage, unwrapList } from "./crmApi"
import { ConditionEditor } from "./ConditionEditor"
import { ACTIONS, EMPTY_WORKFLOW, MAX_WORKFLOWS, TRIGGERS, WORKFLOW_STAGES, canPickUser, needsDays, textLabel, toWorkflowForm, workflowBody, workflowProblem, runSummary, type Action, type Trigger, type Workflow, type WorkflowForm, type WorkflowRun } from "./workflows.logic"
import { Loading, ErrorNote } from "./CrmKit"

const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const ghost: React.CSSProperties = { ...field, cursor: "pointer" }
const small: React.CSSProperties = { ...ghost, padding: "3px 8px", fontSize: 12 }
interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
const userName = (u: UserRow) => [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email || "User"
const when = (iso: string) => { const d = new Date(iso); return Number.isNaN(d.getTime()) ? iso : d.toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" }) }

export default function WorkflowsPanel() {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE")
  const [error, setError] = useState<string | null>(null)
  const [edit, setEdit] = useState<string | "new" | null>(null), [form, setForm] = useState<WorkflowForm>(EMPTY_WORKFLOW)
  const flows = useQuery({ queryKey: ["crm", "workflows"], queryFn: async () => unwrapList<Workflow>(await apiClient.get("/api/v1/crm/workflows")) })
  const runs = useQuery({ queryKey: ["crm", "workflow-runs"], retry: false, queryFn: async () => unwrapList<WorkflowRun>(await apiClient.get("/api/v1/crm/workflows/runs", { params: { limit: 50 } })) })
  const users = useQuery({ queryKey: ["crm", "task-users"], retry: false, queryFn: async () => unwrapList<UserRow>(await apiClient.get("/api/v1/identity/users", { params: { size: 200 } })) })
  const fail = (e: unknown) => setError(errorMessage(e, "Something went wrong. Try again."))
  const done = () => { setError(null); setEdit(null); qc.invalidateQueries({ queryKey: ["crm", "workflows"] }) }
  const save = useMutation({ mutationFn: () => edit === "new" ? apiClient.post("/api/v1/crm/workflows", workflowBody(form)) : apiClient.put(`/api/v1/crm/workflows/${edit}`, workflowBody(form)), onSuccess: done, onError: fail })
  const toggle = useMutation({ mutationFn: (w: Workflow) => apiClient.put(`/api/v1/crm/workflows/${w.id}`, workflowBody({ ...toWorkflowForm(w), active: !w.active })), onSuccess: done, onError: fail })
  const del = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/workflows/${id}`), onSuccess: () => { done(); qc.invalidateQueries({ queryKey: ["crm", "workflow-runs"] }) }, onError: fail })

  const list = flows.data ?? [], problem = edit ? workflowProblem(form) : null, busy = save.isPending || toggle.isPending || del.isPending
  const names = new Map((users.data ?? []).map(u => [u.id, userName(u)]))
  const sentence = (w: Workflow) => w.action === "ASSIGN_OWNER" && w.actionUser && names.get(w.actionUser) ? w.description.replace(/make .* the owner$/, `make ${names.get(w.actionUser)} the owner`) : w.description
  const sum = runSummary(runs.data ?? [])

  return (
    <div style={{ display: "grid", gap: 22, fontSize: 13 }}>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)" }}>{error}</div>}
      <section aria-label="Workflows">
        <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>Workflow automation</h3>
        <p style={{ margin: "0 0 8px", color: "var(--hf-text-muted)" }}>A workflow does one thing when something happens to a lead. What it does never starts another workflow. If one fails, the change that started it still stands and the failure shows in the log below.</p>
        {flows.isLoading && <Loading />}{flows.isError && <ErrorNote>Couldn't load the workflows.</ErrorNote>}
        {flows.data && list.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No workflows yet.</p>}
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 6 }}>
          {list.map(w => (
            <li key={w.id} aria-label={w.name} style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", opacity: w.active ? 1 : 0.55 }}>
              <strong style={{ minWidth: 150 }}>{w.name}</strong><span style={{ color: "var(--hf-text-muted)" }}>{sentence(w)}</span>{!w.active && <span>(off)</span>}
              {canEdit && <span style={{ display: "flex", gap: 6 }}>
                <button type="button" style={small} aria-label={`Edit ${w.name}`} onClick={() => { setForm(toWorkflowForm(w)); setEdit(w.id); setError(null) }}>Edit</button>
                <button type="button" style={small} aria-label={`${w.active ? "Turn off" : "Turn on"} ${w.name}`} disabled={busy} onClick={() => toggle.mutate(w)}>{w.active ? "Turn off" : "Turn on"}</button>
                <button type="button" style={small} aria-label={`Delete ${w.name}`} disabled={busy} onClick={() => del.mutate(w.id)}>Delete</button></span>}
            </li>))}
        </ul>
        {canEdit && !edit && <button type="button" style={{ ...ghost, marginTop: 8 }} disabled={list.length >= MAX_WORKFLOWS} onClick={() => { setForm(EMPTY_WORKFLOW); setEdit("new"); setError(null) }}>Add workflow</button>}
        {list.length >= MAX_WORKFLOWS && <p style={{ color: "var(--hf-text-muted)" }}>You have reached the limit of {MAX_WORKFLOWS} workflows.</p>}
        {canEdit && edit && (
          <form aria-label="Workflow" onSubmit={e => { e.preventDefault(); if (!problem) save.mutate() }} style={{ display: "grid", gap: 8, maxWidth: 560, marginTop: 8, padding: 10, border: "1px solid var(--hf-border)", borderRadius: 10 }}>
            <label>Name <input aria-label="Workflow name" style={{ ...field, width: "100%" }} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
            <label>When <select aria-label="Trigger" style={field} value={form.trigger} onChange={e => setForm({ ...form, trigger: e.target.value as Trigger | "", triggerStage: "" })}>
              <option value="">Choose what starts it…</option>{TRIGGERS.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}</select></label>
            {form.trigger === "STAGE_CHANGED" && <label>Moves to <select aria-label="Trigger stage" style={field} value={form.triggerStage} onChange={e => setForm({ ...form, triggerStage: e.target.value })}>
              <option value="">Any stage</option>{WORKFLOW_STAGES.map(s => <option key={s.id} value={s.id}>{s.label}</option>)}</select></label>}
            <div>Only if <ConditionEditor optional label="Workflow condition" value={form.condition} onChange={c => setForm({ ...form, condition: c })} /></div>
            <label>Then <select aria-label="Action" style={field} value={form.action} onChange={e => setForm({ ...form, action: e.target.value as Action | "" })}>
              <option value="">Choose what it does…</option>{ACTIONS.map(a => <option key={a.id} value={a.id}>{a.label}</option>)}</select></label>
            {textLabel(form.action) && <label>{textLabel(form.action)} <input aria-label={textLabel(form.action) ?? ""} style={{ ...field, width: "100%" }} value={form.text} onChange={e => setForm({ ...form, text: e.target.value })} /></label>}
            {needsDays(form.action) && <label>Due in (days) <input aria-label="Due in days" inputMode="numeric" style={{ ...field, width: 80 }} value={form.days} onChange={e => setForm({ ...form, days: e.target.value })} /></label>}
            {canPickUser(form.action) && <label>{form.action === "ASSIGN_OWNER" ? "New owner" : "Give the task to"} <select aria-label="Person" style={field} value={form.user} onChange={e => setForm({ ...form, user: e.target.value })}>
              <option value="">{form.action === "ASSIGN_OWNER" ? "Choose…" : "The lead's owner"}</option>{(users.data ?? []).map(u => <option key={u.id} value={u.id}>{userName(u)}</option>)}</select></label>}
            {form.name.trim() && problem && <span style={{ color: "var(--hf-danger-text)" }}>{problem}</span>}
            <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!problem || busy}>{save.isPending ? "Saving…" : "Save workflow"}</button><button type="button" style={ghost} onClick={() => { setEdit(null); setError(null) }}>Cancel</button></div>
          </form>)}
      </section>

      <section aria-label="Workflow log">
        <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>What ran recently</h3>
        {runs.isLoading && <Loading />}{runs.isError && <ErrorNote>Couldn't load the log.</ErrorNote>}
        {runs.data && runs.data.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>Nothing has run yet.</p>}
        {runs.data && runs.data.length > 0 && <p style={{ margin: "0 0 6px", color: "var(--hf-text-muted)" }}>{sum.done} done{sum.failed ? `, ${sum.failed} failed` : ""} in the last {runs.data.length}.</p>}
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 4 }}>
          {(runs.data ?? []).map(r => (
            <li key={r.id} aria-label={`Run of ${r.workflowName} for ${r.customerName}`} style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
              <span style={{ color: "var(--hf-text-muted)", minWidth: 140 }}>{when(r.ranAt)}</span><strong>{r.workflowName}</strong><span>{r.customerName}</span>
              <span style={{ color: r.outcome === "DONE" ? "var(--hf-success-text)" : "var(--hf-danger-text)" }}>{r.outcome === "DONE" ? "Done" : "Failed"}{r.detail ? `: ${r.detail}` : ""}</span>
            </li>))}
        </ul>
      </section>
    </div>
  )
}
