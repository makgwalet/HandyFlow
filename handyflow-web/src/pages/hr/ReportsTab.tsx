// src/pages/hr/ReportsTab.tsx
//
// HR reports: workforce, time and leave, payroll cost, and employee relations and compliance. The server decides what each person may see and
// works the figures out; this screen picks the period, lays the figures out, and saves them as a spreadsheet.
import { useState } from "react"
import { useAuth } from "../../hooks/useAuth"
import { fmtDate } from "./er.logic"
import { useReport } from "./reports.hooks"
import { complianceRows, payrollRows, timeRows, workforceRows } from "./reports.csv"
import { defaultPeriod, periodProblem, presets, reportFileName, reportRights, toCsv, type Cell, type Period } from "./reports.logic"
import { errorText, input, plain, primary, type Compliance, type PayrollReport, type TimeReport, type Workforce } from "./reports.shared"
import { Notice } from "./AttendanceParts"
import { ComplianceView, PayrollView, TimeView, WorkforceView } from "./ReportViews"

type Kind = "workforce" | "time" | "payroll" | "compliance"

function download(name: string, rows: Cell[][]) {
  const url = URL.createObjectURL(new Blob(["﻿" + toCsv(rows)], { type: "text/csv;charset=utf-8" }))
  const a = document.createElement("a")
  a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
}

export default function ReportsTab() {
  const { user } = useAuth() as { user?: { permissions?: string[] } }
  const rights = reportRights(user?.permissions ?? [])
  const [kind, setKind] = useState<Kind>("workforce")
  const [draft, setDraft] = useState<Period>(defaultPeriod())
  const [period, setPeriod] = useState<Period>(defaultPeriod())
  const problem = periodProblem(draft)

  const tabs: { id: Kind; label: string; show: boolean }[] = [
    { id: "workforce", label: "Workforce", show: true },
    { id: "time", label: "Time and leave", show: true },
    { id: "payroll", label: "Payroll cost", show: rights.payroll },
    { id: "compliance", label: "Compliance and relations", show: true },
  ]
  const workforce = useReport<Workforce>("workforce", period, rights.read && kind === "workforce")
  const time = useReport<TimeReport>("time", period, rights.read && kind === "time")
  const payroll = useReport<PayrollReport>("payroll", period, rights.read && rights.payroll && kind === "payroll")
  const compliance = useReport<Compliance>("compliance", period, rights.read && kind === "compliance")

  if (!rights.read) return <Notice kind="error">You do not have access to the HR reports.</Notice>
  const current = { workforce, time, payroll, compliance }[kind]
  const rows = (): Cell[][] | null => {
    if (kind === "workforce" && workforce.data) return workforceRows(workforce.data)
    if (kind === "time" && time.data) return timeRows(time.data)
    if (kind === "payroll" && payroll.data) return payrollRows(payroll.data)
    if (kind === "compliance" && compliance.data) return complianceRows(compliance.data)
    return null
  }

  return (
    <div>
      <div role="tablist" style={{ display: "flex", gap: 8, marginBottom: 14, flexWrap: "wrap" }}>
        {tabs.filter(t => t.show).map(t => (
          <button key={t.id} role="tab" aria-selected={kind === t.id} onClick={() => setKind(t.id)}
            style={{ padding: "6px 14px", borderRadius: 20, fontSize: 13, cursor: "pointer", border: "none", fontWeight: kind === t.id ? 600 : 400,
              background: kind === t.id ? "var(--hf-primary)" : "var(--hf-surface-sunken)", color: kind === t.id ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
            {t.label}
          </button>
        ))}
      </div>

      <form aria-label="Period" onSubmit={e => { e.preventDefault(); if (!problem) setPeriod(draft) }}
        style={{ display: "flex", gap: 10, alignItems: "end", flexWrap: "wrap", marginBottom: 14 }}>
        <label style={{ fontSize: 12 }}>From<br /><input aria-label="From" type="date" value={draft.from} onChange={e => setDraft({ ...draft, from: e.target.value })} style={{ ...input, width: 160 }} /></label>
        <label style={{ fontSize: 12 }}>To<br /><input aria-label="To" type="date" value={draft.to} onChange={e => setDraft({ ...draft, to: e.target.value })} style={{ ...input, width: 160 }} /></label>
        <button type="submit" disabled={!!problem} style={primary}>Show</button>
        <select aria-label="Quick periods" value="" onChange={e => { const p = presets().find(x => x.id === e.target.value); if (p) { setDraft(p.period); setPeriod(p.period) } }} style={{ ...input, width: 170 }}>
          <option value="">Quick choices…</option>
          {presets().map(p => <option key={p.id} value={p.id}>{p.label}</option>)}
        </select>
        <div style={{ flex: 1 }} />
        <button type="button" disabled={!rows()} onClick={() => { const r = rows(); if (r) download(reportFileName(kind, period), r) }} style={plain}>Download as spreadsheet</button>
      </form>
      {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: -6 }}>{problem}</p>}
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 14px" }}>Showing {fmtDate(period.from)} to {fmtDate(period.to)}.</p>

      {current.isLoading && <div style={{ color: "var(--hf-text-faint)" }}>Working it out…</div>}
      {current.isError && <Notice kind="error">{errorText(current.error, "The report could not be produced.")}</Notice>}
      {kind === "workforce" && workforce.data && <WorkforceView w={workforce.data} />}
      {kind === "time" && time.data && <TimeView t={time.data} />}
      {kind === "payroll" && payroll.data && <PayrollView p={payroll.data} />}
      {kind === "compliance" && compliance.data && <ComplianceView c={compliance.data} />}
    </div>
  )
}
