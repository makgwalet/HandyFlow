// src/pages/hr/SavedReports.tsx
//
// A person's saved report views: open one to see the report again for its period, keep the one on screen, change it, share it with
// colleagues, and choose a weekly or monthly reminder (in the app, and by email if asked). A reminder and a share carry no figures: each
// person sees the figures their own access allows.
import { useState } from "react"
import { errorText, input, plain, primary } from "./reports.shared"
import { useColleagues, useDeleteSavedReport, useLeaveSharedReport, useSaveReport, useSavedReports, useShareReport, useUpdateSavedReport } from "./savedReports.hooks"
import {
  EMPTY_SAVED_FORM, MAX_SAVED, SAVED_PERIODS, WEEKDAYS, formFromSaved, mayKeep, reminderText, savedProblem, shareProblem, sharingText, visibleSaved,
  type ReportKind, type SavedForm, type SavedReport,
} from "./savedReports.logic"
import { Notice } from "./AttendanceParts"

export default function SavedReports({ kind, payrollRight, onOpen }: { kind: ReportKind; payrollRight: boolean; onOpen: (s: SavedReport) => void }) {
  const list = useSavedReports(true)
  const save = useSaveReport()
  const remove = useDeleteSavedReport()
  const update = useUpdateSavedReport()
  const leave = useLeaveSharedReport()
  const shareMut = useShareReport()
  const [form, setForm] = useState<SavedForm>(EMPTY_SAVED_FORM)
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<SavedReport | null>(null)
  const [sharing, setSharing] = useState<SavedReport | null>(null)
  const [chosen, setChosen] = useState<string[]>([])
  const colleagues = useColleagues(!!sharing)
  const shown = visibleSaved(list.data ?? [], payrollRight)
  const own = shown.filter(s => !s.shared)
  const formOpen = adding || !!editing
  const closeForm = () => { setAdding(false); setEditing(null); setForm(EMPTY_SAVED_FORM) }
  const startEdit = (s: SavedReport) => { setAdding(false); setSharing(null); setEditing(s); setForm(formFromSaved(s)) }
  const startShare = (s: SavedReport) => { closeForm(); setSharing(s); setChosen(s.sharedWith ?? []) }
  const toggle = (id: string) => setChosen(c => c.includes(id) ? c.filter(x => x !== id) : [...c, id])
  const problem = savedProblem(form)
  const set = (k: "name" | "period" | "schedule" | "runDay") => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value, ...(k === "schedule" ? { runDay: "", emailReminder: e.target.value === "NONE" ? false : f.emailReminder } : {}) }))

  return (
    <section aria-label="Saved reports" style={{ marginBottom: 16, padding: 12, border: "1px solid var(--hf-border)", borderRadius: 10 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
        <strong style={{ fontSize: 13 }}>Saved reports</strong>
        <div style={{ flex: 1 }} />
        {mayKeep(kind, payrollRight) && !formOpen && own.length < MAX_SAVED &&
          <button type="button" style={plain} onClick={() => { setSharing(null); setAdding(true) }}>Save this report…</button>}
      </div>

      {list.isError && <Notice kind="error">{errorText(list.error, "Your saved reports could not be loaded.")}</Notice>}
      {!list.isLoading && !list.isError && shown.length === 0 && !formOpen &&
        <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "8px 0 0" }}>Nothing saved yet. Save a report to open it again with one click, or to be reminded when it is due.</p>}

      <ul style={{ listStyle: "none", padding: 0, margin: "8px 0 0", display: "grid", gap: 6 }}>
        {shown.map(s => (
          <li key={s.id} style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", fontSize: 13 }}>
            <button type="button" style={primary} onClick={() => onOpen(s)} aria-label={`Open ${s.name}`}>Open</button>
            <span style={{ fontWeight: 600 }}>{s.name}</span>
            <span style={{ color: "var(--hf-text-muted)" }}>{s.kindLabel} · {s.periodLabel} · {reminderText(s)}{sharingText(s) ? ` · ${sharingText(s)}` : ""}</span>
            <div style={{ flex: 1 }} />
            {s.shared ? (
              <button type="button" style={plain} aria-label={`Remove ${s.name}`} disabled={leave.isPending}
                onClick={() => { if (window.confirm(`Take "${s.name}" off your list? ${s.ownerName ?? "The owner"} keeps it.`)) leave.mutate(s.id) }}>Remove</button>
            ) : <>
              <button type="button" style={plain} aria-label={`Edit ${s.name}`} onClick={() => startEdit(s)}>Edit</button>
              <button type="button" style={plain} aria-label={`Share ${s.name}`} onClick={() => startShare(s)}>Share</button>
              <button type="button" style={plain} aria-label={`Delete ${s.name}`} disabled={remove.isPending}
                onClick={() => { if (window.confirm(`Delete the saved report "${s.name}"?`)) remove.mutate(s.id) }}>Delete</button>
            </>}
          </li>
        ))}
      </ul>
      {remove.isError && <Notice kind="error">{errorText(remove.error, "The saved report could not be deleted.")}</Notice>}
      {leave.isError && <Notice kind="error">{errorText(leave.error, "The report could not be removed from your list.")}</Notice>}

      {sharing && (
        <form aria-label="Share report" style={{ marginTop: 10, padding: 10, border: "1px solid var(--hf-border-subtle)", borderRadius: 8 }}
          onSubmit={e => { e.preventDefault(); if (!shareProblem(chosen)) shareMut.mutate({ id: sharing.id, userIds: chosen }, { onSuccess: () => setSharing(null) }) }}>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 4 }}>Share "{sharing.name}"</div>
          <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>
            Colleagues get this saved view and its reminders, not the figures: each person sees what their own access allows.
            {sharing.kind === "PAYROLL" ? " Only people who may see pay can be chosen." : ""}
          </p>
          {colleagues.isLoading && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Loading colleagues…</div>}
          {colleagues.isError && <Notice kind="error">{errorText(colleagues.error, "Your colleagues could not be loaded.")}</Notice>}
          {colleagues.data && colleagues.data.length === 0 && <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>No one else can open the HR reports.</p>}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(190px, 1fr))", gap: 4 }}>
            {(colleagues.data ?? []).map(c => (
              <label key={c.id} style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}>
                <input type="checkbox" checked={chosen.includes(c.id)} onChange={() => toggle(c.id)} aria-label={c.name} />{c.name}
              </label>
            ))}
          </div>
          {shareProblem(chosen) && <p style={{ fontSize: 12, color: "var(--hf-danger-text)" }}>{shareProblem(chosen)}</p>}
          <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
            <button type="submit" style={primary} disabled={!!shareProblem(chosen) || shareMut.isPending}>Save sharing</button>
            <button type="button" style={plain} onClick={() => setSharing(null)}>Close</button>
          </div>
          {shareMut.isError && <Notice kind="error">{errorText(shareMut.error, "The report could not be shared.")}</Notice>}
        </form>
      )}

      {formOpen && (
        <form aria-label={editing ? "Edit report" : "Save report"} style={{ display: "flex", gap: 10, alignItems: "end", flexWrap: "wrap", marginTop: 10 }}
          onSubmit={e => {
            e.preventDefault()
            if (problem) return
            if (editing) update.mutate({ id: editing.id, form, kind: editing.kind }, { onSuccess: closeForm })
            else save.mutate({ form, kind }, { onSuccess: closeForm })
          }}>
          <label style={{ fontSize: 12 }}>Name<br /><input aria-label="Name" value={form.name} onChange={set("name")} maxLength={80} style={{ ...input, width: 200 }} /></label>
          <label style={{ fontSize: 12 }}>Period<br />
            <select aria-label="Saved period" value={form.period} onChange={set("period")} style={{ ...input, width: 160 }}>
              {SAVED_PERIODS.map(p => <option key={p.value} value={p.value}>{p.label}</option>)}
            </select></label>
          <label style={{ fontSize: 12 }}>Remind me<br />
            <select aria-label="Reminder" value={form.schedule} onChange={set("schedule")} style={{ ...input, width: 130 }}>
              <option value="NONE">Never</option><option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option>
            </select></label>
          {form.schedule === "WEEKLY" && <label style={{ fontSize: 12 }}>On<br />
            <select aria-label="Reminder day" value={form.runDay} onChange={set("runDay")} style={{ ...input, width: 130 }}>
              <option value="">Choose…</option>{WEEKDAYS.map((d, i) => <option key={d} value={i + 1}>{d}</option>)}
            </select></label>}
          {form.schedule === "MONTHLY" && <label style={{ fontSize: 12 }}>Day of month<br />
            <select aria-label="Reminder day" value={form.runDay} onChange={set("runDay")} style={{ ...input, width: 130 }}>
              <option value="">Choose…</option>{Array.from({ length: 28 }, (_, i) => <option key={i + 1} value={i + 1}>{i + 1}</option>)}
            </select></label>}
          {form.schedule !== "NONE" && <label style={{ fontSize: 12, display: "flex", gap: 6, alignItems: "center" }}>
            <input type="checkbox" checked={form.emailReminder} onChange={e => setForm(f => ({ ...f, emailReminder: e.target.checked }))} />
            Also email the reminder (a link, no figures)</label>}
          <button type="submit" style={primary} disabled={!!problem || save.isPending || update.isPending}>{editing ? "Save changes" : "Save"}</button>
          <button type="button" style={plain} onClick={closeForm}>Cancel</button>
          {form.name && problem && <span style={{ fontSize: 12, color: "var(--hf-text-muted)", flexBasis: "100%" }}>{problem}</span>}
          {save.isError && <Notice kind="error">{errorText(save.error, "The report could not be saved.")}</Notice>}
          {update.isError && <Notice kind="error">{errorText(update.error, "The report could not be changed.")}</Notice>}
        </form>
      )}
    </section>
  )
}
