// src/pages/hr/SavedReports.tsx
//
// A person's saved report views: open one to see the report again for its period, keep the one on screen, and choose a weekly or
// monthly reminder. A reminder is a notification with no figures in it.
import { useState } from "react"
import { errorText, input, plain, primary } from "./reports.shared"
import { useDeleteSavedReport, useSaveReport, useSavedReports } from "./savedReports.hooks"
import {
  EMPTY_SAVED_FORM, MAX_SAVED, SAVED_PERIODS, WEEKDAYS, mayKeep, reminderText, savedProblem, visibleSaved,
  type ReportKind, type SavedForm, type SavedReport,
} from "./savedReports.logic"
import { Notice } from "./AttendanceParts"

export default function SavedReports({ kind, payrollRight, onOpen }: { kind: ReportKind; payrollRight: boolean; onOpen: (s: SavedReport) => void }) {
  const list = useSavedReports(true)
  const save = useSaveReport()
  const remove = useDeleteSavedReport()
  const [form, setForm] = useState<SavedForm>(EMPTY_SAVED_FORM)
  const [adding, setAdding] = useState(false)
  const shown = visibleSaved(list.data ?? [], payrollRight)
  const problem = savedProblem(form)
  const set = (k: keyof SavedForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value, ...(k === "schedule" ? { runDay: "" } : {}) }))

  return (
    <section aria-label="Saved reports" style={{ marginBottom: 16, padding: 12, border: "1px solid var(--hf-border)", borderRadius: 10 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
        <strong style={{ fontSize: 13 }}>Saved reports</strong>
        <div style={{ flex: 1 }} />
        {mayKeep(kind, payrollRight) && !adding && shown.length < MAX_SAVED &&
          <button type="button" style={plain} onClick={() => setAdding(true)}>Save this report…</button>}
      </div>

      {list.isError && <Notice kind="error">{errorText(list.error, "Your saved reports could not be loaded.")}</Notice>}
      {!list.isLoading && !list.isError && shown.length === 0 && !adding &&
        <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "8px 0 0" }}>Nothing saved yet. Save a report to open it again with one click, or to be reminded when it is due.</p>}

      <ul style={{ listStyle: "none", padding: 0, margin: "8px 0 0", display: "grid", gap: 6 }}>
        {shown.map(s => (
          <li key={s.id} style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", fontSize: 13 }}>
            <button type="button" style={primary} onClick={() => onOpen(s)} aria-label={`Open ${s.name}`}>Open</button>
            <span style={{ fontWeight: 600 }}>{s.name}</span>
            <span style={{ color: "var(--hf-text-muted)" }}>{s.kindLabel} · {s.periodLabel} · {reminderText(s)}</span>
            <div style={{ flex: 1 }} />
            <button type="button" style={plain} aria-label={`Delete ${s.name}`} disabled={remove.isPending}
              onClick={() => { if (window.confirm(`Delete the saved report "${s.name}"?`)) remove.mutate(s.id) }}>Delete</button>
          </li>
        ))}
      </ul>
      {remove.isError && <Notice kind="error">{errorText(remove.error, "The saved report could not be deleted.")}</Notice>}

      {adding && (
        <form aria-label="Save report" style={{ display: "flex", gap: 10, alignItems: "end", flexWrap: "wrap", marginTop: 10 }}
          onSubmit={e => { e.preventDefault(); if (!problem) save.mutate({ form, kind }, { onSuccess: () => { setForm(EMPTY_SAVED_FORM); setAdding(false) } }) }}>
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
          <button type="submit" style={primary} disabled={!!problem || save.isPending}>Save</button>
          <button type="button" style={plain} onClick={() => { setAdding(false); setForm(EMPTY_SAVED_FORM) }}>Cancel</button>
          {form.name && problem && <span style={{ fontSize: 12, color: "var(--hf-text-muted)", flexBasis: "100%" }}>{problem}</span>}
          {save.isError && <Notice kind="error">{errorText(save.error, "The report could not be saved.")}</Notice>}
        </form>
      )}
    </section>
  )
}
