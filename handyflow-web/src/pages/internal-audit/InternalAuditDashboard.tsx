import { useEffect, useState } from "react"
import { auditDashboardsApi, type InternalDashboard } from "../../api/auditDashboards.api"
import { Card, Tile, TileRow } from "../../components/DashboardBits"

const MUTED = "var(--hf-text-muted)"

export function InternalAuditDashboard() {
  const [d, setD] = useState<InternalDashboard | null>(null)
  const [error, setError] = useState("")
  useEffect(() => {
    auditDashboardsApi.internal().then(setD).catch((e: any) => setError(e?.response?.data?.message ?? "Failed to load the dashboard"))
  }, [])

  if (error) return <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</div>
  if (!d) return <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>

  const { plan, coverage } = d
  return (
    <div>
      <TileRow>
        <Tile label={plan.year ? `${plan.year} plan completed` : "Annual plan"} value={plan.year ? `${plan.completionPct}%` : "No plan"}
          sub={plan.year ? `${plan.completed} of ${plan.total - plan.deferred} audits${plan.deferred ? ` (${plan.deferred} deferred)` : ""}` : undefined} />
        <Tile label="Universe planned" value={`${coverage.plannedPct}%`} sub={`${coverage.planned} of ${coverage.universeSize} areas`} />
        <Tile label="Universe audited" value={`${coverage.auditedPct}%`} sub={`${coverage.audited} of ${coverage.universeSize} areas`} />
        <Tile label="Open findings" value={d.openFindings} sub={d.findingsPastDue ? `${d.findingsPastDue} past due` : undefined} tone={d.findingsPastDue ? "danger" : undefined} />
        <Tile label="Overdue actions" value={d.overdueActionCount} tone={d.overdueActionCount ? "danger" : undefined} />
        <Tile label="Repeat findings" value={d.repeatFindingCount} tone={d.repeatFindingCount ? "warning" : undefined} sub="reopened or previously closed" />
      </TileRow>

      <Card title="Findings by severity">
        {d.findingsBySeverity.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>No findings yet.</span> : (
          <table style={{ width: "100%", fontSize: 13, borderCollapse: "collapse" }}>
            <thead><tr style={{ textAlign: "left", color: MUTED }}><th>Severity</th><th>Open</th><th>All</th></tr></thead>
            <tbody>{d.findingsBySeverity.map(r => <tr key={r.severity}><td>{r.severity}</td><td>{r.open}</td><td>{r.total}</td></tr>)}</tbody>
          </table>
        )}
      </Card>

      <Card title="Overdue actions">
        {d.overdueActions.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>Nothing overdue.</span> : (
          <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
            {d.overdueActions.map(a => <li key={a.id}>{a.description} — due {a.dueDate} ({a.daysOverdue} day{a.daysOverdue === 1 ? "" : "s"} late)</li>)}
          </ul>
        )}
        {d.overdueActionCount > d.overdueActions.length && <div style={{ fontSize: 12, color: MUTED, marginTop: 6 }}>Showing the {d.overdueActions.length} oldest of {d.overdueActionCount}.</div>}
      </Card>

      <Card title="Repeat findings">
        {d.repeatFindings.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>None.</span> : (
          <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
            {d.repeatFindings.map(f => <li key={f.id}>{f.title} ({f.severity ?? "unrated"}, {f.status.toLowerCase().replace("_", " ")})</li>)}
          </ul>
        )}
      </Card>

      <Card title="Areas not in this year's plan">
        {coverage.universeSize === 0 ? <span style={{ fontSize: 13, color: MUTED }}>The audit universe is empty.</span>
          : coverage.notPlanned.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>Every area is planned.</span> : (
          <div style={{ fontSize: 13 }}>{coverage.notPlanned.join(", ")}{coverage.universeSize - coverage.planned > coverage.notPlanned.length ? " …" : ""}</div>
        )}
      </Card>
    </div>
  )
}
