// Risk & Control Report for the audit committee: plan delivery, reports, findings, overdue high-risk actions and the control position.

import { useEffect, useState } from "react"
import { auditDashboardsApi, type CommitteeReport as Report } from "../../api/auditDashboards.api"
import { Meter, Panel, Pill, Stat } from "./auditUi"
import { grid, SEVERITY_COLOR } from "./auditUi.styles"

const MUTED = "var(--hf-text-muted)"
const none = (t: string) => <span style={{ fontSize: 13, color: MUTED }}>{t}</span>
const day = (d: string | null) => (d ? d.slice(0, 10) : "—")
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", borderBottom: "1px solid var(--hf-border)" }
const td: React.CSSProperties = { padding: "8px 10px", fontSize: 13, color: "var(--hf-text)", borderBottom: "1px solid var(--hf-border-subtle)" }
const LEVEL_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "LOW"]

export function CommitteeReport() {
  // Loaded directly rather than through the query cache, so the report always shows the position as it is now.
  const [r, setR] = useState<Report | null>(null)
  const [error, setError] = useState("")
  useEffect(() => {
    auditDashboardsApi.committee().then(setR).catch((e: { response?: { data?: { message?: string } } }) => setError(e?.response?.data?.message ?? "Failed to load the report"))
  }, [])

  if (error) return <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</div>
  if (!r) return <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>
  const c = r.controls
  const levelTotal = r.risk.byLevel.reduce((n, l) => n + l.count, 0)
  const levels = [...r.risk.byLevel].sort((a, b) => (LEVEL_ORDER.indexOf(a.level) + 1 || 99) - (LEVEL_ORDER.indexOf(b.level) + 1 || 99))
  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
        <span style={{ fontSize: 12.5, color: MUTED }}>Position as at {r.asOf}</span>
        <button onClick={() => window.print()} style={{ padding: "7px 14px", fontSize: 12.5, fontWeight: 700, cursor: "pointer", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-secondary)" }}>Print</button>
      </div>

      <div style={grid(180)}>
        <Stat label={r.plan.year ? `${r.plan.year} plan delivered` : "Annual plan"} value={r.plan.year ? `${r.plan.completionPct}%` : "No plan"}
          sub={r.plan.year ? `${r.plan.completed} of ${r.plan.committed} audits${r.plan.deferred ? ` (${r.plan.deferred} deferred)` : ""}` : undefined} />
        <Stat label="Reports approved" value={r.reports.approved} sub={r.reports.awaitingSignOff ? `${r.reports.awaitingSignOff} awaiting sign-off` : undefined} />
        <Stat label="Closed without approved report" value={r.reports.closedWithoutApproval} tone={r.reports.closedWithoutApproval ? "warning" : undefined} />
        <Stat label="Open high and critical findings" value={r.findings.openHighOrCritical} sub={r.findings.pastDue ? `${r.findings.pastDue} past due` : undefined} tone={r.findings.pastDue ? "danger" : undefined} />
        <Stat label="Overdue high-risk actions" value={r.overdueHighRiskActionCount} tone={r.overdueHighRiskActionCount ? "danger" : undefined} />
      </div>

      <Panel title="Overdue high-risk actions">
        {r.overdueHighRiskActions.length === 0 ? none("None overdue.") : (
          <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8 }}>
            {r.overdueHighRiskActions.map(a => (
              <li key={a.id} style={{ display: "flex", gap: 10, alignItems: "flex-start", fontSize: 13, paddingBottom: 8, borderBottom: "1px solid var(--hf-border-subtle)" }}>
                <span aria-hidden style={{ marginTop: 5, width: 8, height: 8, borderRadius: 99, flex: "none", background: SEVERITY_COLOR[a.severity] ?? "var(--hf-text-faint)" }} />
                <span>{`${a.description} — ${a.finding} (${a.severity})${a.owner ? `, ${a.owner}` : ""}; due ${a.dueDate}, ${a.daysOverdue} day${a.daysOverdue === 1 ? "" : "s"} late`}</span>
              </li>
            ))}
          </ul>
        )}
        {r.overdueHighRiskActionCount > r.overdueHighRiskActions.length && <div style={{ fontSize: 12, color: MUTED, marginTop: 6 }}>Showing the {r.overdueHighRiskActions.length} oldest of {r.overdueHighRiskActionCount}.</div>}
      </Panel>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 14, alignItems: "start" }}>
        <Panel title="Plan delivery by quarter">
          {r.plan.quarters.length === 0 ? none("No audits are scheduled to a quarter.") : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Quarter</th><th style={th}>Planned</th><th style={th}>Completed</th><th style={{ ...th, width: "35%" }}>Delivered</th></tr></thead>
              <tbody>{r.plan.quarters.map(q => (
                <tr key={q.quarter}>
                  <td style={td}>Q{q.quarter}</td><td style={td}>{q.planned}</td><td style={td}>{q.completed}</td>
                  <td style={td}><Meter pct={q.planned ? Math.round((q.completed / q.planned) * 100) : 0} color="var(--hf-success-text)" label={`Quarter ${q.quarter} delivered`} /></td>
                </tr>
              ))}</tbody>
            </table>
          )}
        </Panel>

        <Panel title="Approved reports">
          {r.reports.recentlyApproved.length === 0 ? none("No reports approved yet.") : (
            <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13, display: "grid", gap: 4 }}>{r.reports.recentlyApproved.map((a, i) => <li key={i}>{a.engagement} — {day(a.approvedAt)}</li>)}</ul>
          )}
        </Panel>
      </div>

      <Panel title="Risk profile of the audit universe">
        {r.risk.byLevel.length === 0 ? none("The audit universe is empty.") : (
          <>
            <div role="img" aria-label="Areas by risk level" style={{ display: "flex", height: 12, borderRadius: 99, overflow: "hidden", background: "var(--hf-surface-sunken)", marginBottom: 10 }}>
              {levels.map(l => <div key={l.level} style={{ width: `${levelTotal ? (l.count / levelTotal) * 100 : 0}%`, background: SEVERITY_COLOR[l.level] ?? "var(--hf-border-strong)" }} />)}
            </div>
            <div style={{ fontSize: 13 }}>{r.risk.byLevel.map(l => `${l.level.toLowerCase()}: ${l.count}`).join(" · ")}</div>
          </>
        )}
        {r.risk.highRiskNotYetAudited.length > 0 && (
          <div style={{ fontSize: 13, marginTop: 8, display: "flex", gap: 8, alignItems: "flex-start" }}>
            <Pill label="Attention" tone="warning" />
            <span>{`High or critical risk, not yet audited this year: ${r.risk.highRiskNotYetAudited.join(", ")}`}</span>
          </div>
        )}
      </Panel>

      <Panel title="Control exceptions">
        <div style={grid(130)}>
          <Stat label="Open" value={c.open} />
          <Stat label="Not yet rated" value={c.unrated} tone={c.unrated ? "warning" : undefined} />
          <Stat label="High or critical" value={c.highOrCritical} tone={c.highOrCritical ? "danger" : undefined} />
          <Stat label="Past target date" value={c.overdue} tone={c.overdue ? "danger" : undefined} />
          <Stat label="Follow-ups due" value={c.followUpsDue} tone={c.followUpsDue ? "warning" : undefined} />
          <Stat label="Ever reopened" value={c.reopenedEver} />
        </div>
      </Panel>
    </div>
  )
}
