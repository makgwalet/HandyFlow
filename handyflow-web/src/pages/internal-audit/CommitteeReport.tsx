import { useEffect, useState } from "react"
import { auditDashboardsApi, type CommitteeReport as Report } from "../../api/auditDashboards.api"
import { Card, Tile, TileRow } from "../../components/DashboardBits"

const MUTED = "var(--hf-text-muted)"
const none = (t: string) => <span style={{ fontSize: 13, color: MUTED }}>{t}</span>
const day = (d: string | null) => (d ? d.slice(0, 10) : "—")

export function CommitteeReport() {
  const [r, setR] = useState<Report | null>(null)
  const [error, setError] = useState("")
  useEffect(() => {
    auditDashboardsApi.committee().then(setR).catch((e: any) => setError(e?.response?.data?.message ?? "Failed to load the report"))
  }, [])

  if (error) return <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</div>
  if (!r) return <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>
  const c = r.controls
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <span style={{ fontSize: 12.5, color: MUTED }}>Position as at {r.asOf}</span>
        <button onClick={() => window.print()} style={{ padding: "6px 12px", fontSize: 12, fontWeight: 700, cursor: "pointer", borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)" }}>Print</button>
      </div>

      <TileRow>
        <Tile label={r.plan.year ? `${r.plan.year} plan delivered` : "Annual plan"} value={r.plan.year ? `${r.plan.completionPct}%` : "No plan"}
          sub={r.plan.year ? `${r.plan.completed} of ${r.plan.committed} audits${r.plan.deferred ? ` (${r.plan.deferred} deferred)` : ""}` : undefined} />
        <Tile label="Reports approved" value={r.reports.approved} sub={r.reports.awaitingSignOff ? `${r.reports.awaitingSignOff} awaiting sign-off` : undefined} />
        <Tile label="Closed without approved report" value={r.reports.closedWithoutApproval} tone={r.reports.closedWithoutApproval ? "warning" : undefined} />
        <Tile label="Open high and critical findings" value={r.findings.openHighOrCritical} sub={r.findings.pastDue ? `${r.findings.pastDue} past due` : undefined} tone={r.findings.pastDue ? "danger" : undefined} />
        <Tile label="Overdue high-risk actions" value={r.overdueHighRiskActionCount} tone={r.overdueHighRiskActionCount ? "danger" : undefined} />
      </TileRow>

      <Card title="Overdue high-risk actions">
        {r.overdueHighRiskActions.length === 0 ? none("None overdue.") : (
          <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
            {r.overdueHighRiskActions.map(a => (
              <li key={a.id}>{a.description} — {a.finding} ({a.severity}){a.owner ? `, ${a.owner}` : ""}; due {a.dueDate}, {a.daysOverdue} day{a.daysOverdue === 1 ? "" : "s"} late</li>
            ))}
          </ul>
        )}
        {r.overdueHighRiskActionCount > r.overdueHighRiskActions.length && <div style={{ fontSize: 12, color: MUTED, marginTop: 6 }}>Showing the {r.overdueHighRiskActions.length} oldest of {r.overdueHighRiskActionCount}.</div>}
      </Card>

      <Card title="Plan delivery by quarter">
        {r.plan.quarters.length === 0 ? none("No audits are scheduled to a quarter.") : (
          <table style={{ width: "100%", fontSize: 13, borderCollapse: "collapse" }}>
            <thead><tr style={{ textAlign: "left", color: MUTED }}><th>Quarter</th><th>Planned</th><th>Completed</th></tr></thead>
            <tbody>{r.plan.quarters.map(q => <tr key={q.quarter}><td>Q{q.quarter}</td><td>{q.planned}</td><td>{q.completed}</td></tr>)}</tbody>
          </table>
        )}
      </Card>

      <Card title="Approved reports">
        {r.reports.recentlyApproved.length === 0 ? none("No reports approved yet.") : (
          <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>{r.reports.recentlyApproved.map((a, i) => <li key={i}>{a.engagement} — {day(a.approvedAt)}</li>)}</ul>
        )}
      </Card>

      <Card title="Risk profile of the audit universe">
        {r.risk.byLevel.length === 0 ? none("The audit universe is empty.") : (
          <div style={{ fontSize: 13 }}>{r.risk.byLevel.map(l => `${l.level.toLowerCase()}: ${l.count}`).join(" · ")}</div>
        )}
        {r.risk.highRiskNotYetAudited.length > 0 && (
          <div style={{ fontSize: 13, marginTop: 8 }}>High or critical risk, not yet audited this year: {r.risk.highRiskNotYetAudited.join(", ")}</div>
        )}
      </Card>

      <Card title="Control exceptions">
        <TileRow>
          <Tile label="Open" value={c.open} />
          <Tile label="Not yet rated" value={c.unrated} tone={c.unrated ? "warning" : undefined} />
          <Tile label="High or critical" value={c.highOrCritical} tone={c.highOrCritical ? "danger" : undefined} />
          <Tile label="Past target date" value={c.overdue} tone={c.overdue ? "danger" : undefined} />
          <Tile label="Follow-ups due" value={c.followUpsDue} tone={c.followUpsDue ? "warning" : undefined} />
          <Tile label="Ever reopened" value={c.reopenedEver} />
        </TileRow>
      </Card>
    </div>
  )
}
