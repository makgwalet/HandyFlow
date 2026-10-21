// src/pages/customers/ReportsPanel.tsx
// Scheduled reports: pick a report, how often and who gets it. The server writes and sends them; this sends the choices and shows a preview.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { errorMessage, unwrapList } from "./crmApi"
import { EMPTY_REPORT, FREQUENCIES, MAX_SCHEDULES, REPORT_TYPES, lastSentText, recipientText, reportBody, reportProblem, sentToText, toReportForm, toggledId, type Frequency, type ReportForm, type ReportSchedule, type ReportType } from "./reports.logic"

const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const ghost: React.CSSProperties = { ...field, cursor: "pointer" }
const small: React.CSSProperties = { ...ghost, padding: "3px 8px", fontSize: 12 }
interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
const userName = (u: UserRow) => [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email || "User"
interface Preview { title: string; body: string }
const unwrap = <T,>(r: unknown): T => ((r as { data?: { data?: T } })?.data?.data ?? (r as { data?: T })?.data) as T

export default function ReportsPanel() {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE")
  const [error, setError] = useState<string | null>(null), [note, setNote] = useState<string | null>(null)
  const [edit, setEdit] = useState<string | "new" | null>(null), [form, setForm] = useState<ReportForm>(EMPTY_REPORT)
  const [preview, setPreview] = useState<Preview | null>(null)
  const list = useQuery({ queryKey: ["crm", "report-schedules"], queryFn: async () => unwrapList<ReportSchedule>(await apiClient.get("/api/v1/crm/report-schedules")) })
  const users = useQuery({ queryKey: ["crm", "task-users"], retry: false, queryFn: async () => unwrapList<UserRow>(await apiClient.get("/api/v1/identity/users", { params: { size: 200 } })) })
  const fail = (e: unknown) => { setNote(null); setError(errorMessage(e, "Something went wrong. Try again.")) }
  const done = () => { setError(null); setEdit(null); qc.invalidateQueries({ queryKey: ["crm", "report-schedules"] }) }
  const save = useMutation({ mutationFn: () => edit === "new" ? apiClient.post("/api/v1/crm/report-schedules", reportBody(form)) : apiClient.put(`/api/v1/crm/report-schedules/${edit}`, reportBody(form)), onSuccess: done, onError: fail })
  const toggle = useMutation({ mutationFn: (s: ReportSchedule) => apiClient.put(`/api/v1/crm/report-schedules/${s.id}`, reportBody({ ...toReportForm(s), active: !s.active })), onSuccess: done, onError: fail })
  const del = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/report-schedules/${id}`), onSuccess: done, onError: fail })
  const send = useMutation({ mutationFn: async (id: string) => unwrap<{ recipients: number }>(await apiClient.post(`/api/v1/crm/report-schedules/${id}/send`)), onSuccess: r => { setError(null); setNote(sentToText(r.recipients)) }, onError: fail })
  const look = useMutation({ mutationFn: async (type: ReportType) => unwrap<Preview>(await apiClient.get("/api/v1/crm/report-schedules/preview", { params: { type } })), onSuccess: p => { setError(null); setPreview(p) }, onError: fail })

  const rows = list.data ?? [], problem = edit ? reportProblem(form) : null, busy = save.isPending || toggle.isPending || del.isPending || send.isPending
  const people = users.data ?? [], names = new Map(people.map(u => [u.id, userName(u)]))

  return (
    <div style={{ display: "grid", gap: 16, fontSize: 13 }}>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)" }}>{error}</div>}
      {note && <div role="status" style={{ color: "var(--hf-success-text)" }}>{note}</div>}
      <section aria-label="Scheduled reports">
        <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>Scheduled reports</h3>
        <p style={{ margin: "0 0 8px", color: "var(--hf-text-muted)" }}>Reports arrive as a notification and an email. Weekly reports go out on Mondays and monthly ones on the 1st, early in the morning. If nobody is chosen, the account's admins get it.</p>
        {list.isLoading && <p>Loading…</p>}{list.isError && <div role="alert">Couldn't load the scheduled reports.</div>}
        {list.data && rows.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No reports are scheduled.</p>}
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8 }}>
          {rows.map(s => (
            <li key={s.id} aria-label={s.name} style={{ display: "grid", gap: 2, opacity: s.active ? 1 : 0.55 }}>
              <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
                <strong style={{ minWidth: 150 }}>{s.name}</strong><span>{s.description}</span>{!s.active && <span>(off)</span>}
                {canEdit && <span style={{ display: "flex", gap: 6 }}>
                  <button type="button" style={small} aria-label={`Send ${s.name} now`} disabled={busy} onClick={() => { setNote(null); send.mutate(s.id) }}>Send now</button>
                  <button type="button" style={small} aria-label={`Edit ${s.name}`} onClick={() => { setForm(toReportForm(s)); setEdit(s.id); setError(null) }}>Edit</button>
                  <button type="button" style={small} aria-label={`${s.active ? "Turn off" : "Turn on"} ${s.name}`} disabled={busy} onClick={() => toggle.mutate(s)}>{s.active ? "Turn off" : "Turn on"}</button>
                  <button type="button" style={small} aria-label={`Delete ${s.name}`} disabled={busy} onClick={() => del.mutate(s.id)}>Delete</button></span>}
              </div>
              <span style={{ color: "var(--hf-text-muted)" }}>To {recipientText(s.recipientIds, names)}. {lastSentText(s.lastSentAt)}.</span>
            </li>))}
        </ul>
        {canEdit && !edit && <button type="button" style={{ ...ghost, marginTop: 8 }} disabled={rows.length >= MAX_SCHEDULES} onClick={() => { setForm(EMPTY_REPORT); setEdit("new"); setError(null) }}>Schedule a report</button>}
        {rows.length >= MAX_SCHEDULES && <p style={{ color: "var(--hf-text-muted)" }}>You have reached the limit of {MAX_SCHEDULES} scheduled reports.</p>}
        {canEdit && edit && (
          <form aria-label="Scheduled report" onSubmit={e => { e.preventDefault(); if (!problem) save.mutate() }} style={{ display: "grid", gap: 8, maxWidth: 560, marginTop: 8, padding: 10, border: "1px solid var(--hf-border)", borderRadius: 10 }}>
            <label>Name <input aria-label="Report name" style={{ ...field, width: "100%" }} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
            <label>Report <select aria-label="Report" style={field} value={form.reportType} onChange={e => setForm({ ...form, reportType: e.target.value as ReportType | "" })}>
              <option value="">Choose a report…</option>{REPORT_TYPES.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}</select></label>
            <label>How often <select aria-label="How often" style={field} value={form.frequency} onChange={e => setForm({ ...form, frequency: e.target.value as Frequency | "" })}>
              {FREQUENCIES.map(f => <option key={f.id} value={f.id}>{f.label}</option>)}</select></label>
            <fieldset style={{ border: "1px solid var(--hf-border)", borderRadius: 8, padding: "6px 10px" }}>
              <legend>Send to (nobody ticked = the admins)</legend>
              {users.isError && <span>Couldn't load the people.</span>}
              <div style={{ display: "flex", flexWrap: "wrap", gap: 12 }}>
                {people.map(u => <label key={u.id}><input type="checkbox" checked={form.recipientIds.includes(u.id)} onChange={() => setForm({ ...form, recipientIds: toggledId(form.recipientIds, u.id) })} /> {userName(u)}</label>)}
              </div>
            </fieldset>
            <div><button type="button" style={small} disabled={!form.reportType || look.isPending} onClick={() => look.mutate(form.reportType as ReportType)}>{look.isPending ? "Loading…" : "Preview with today's numbers"}</button></div>
            {preview && <pre aria-label="Report preview" style={{ margin: 0, padding: 10, background: "var(--hf-surface-muted, var(--hf-surface))", borderRadius: 8, whiteSpace: "pre-wrap", fontFamily: "inherit" }}><strong>{preview.title}</strong>{"\n"}{preview.body}</pre>}
            {form.name.trim() && problem && <span style={{ color: "var(--hf-danger-text)" }}>{problem}</span>}
            <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!problem || busy}>{save.isPending ? "Saving…" : "Save report"}</button><button type="button" style={ghost} onClick={() => { setEdit(null); setError(null); setPreview(null) }}>Cancel</button></div>
          </form>)}
      </section>
    </div>
  )
}
