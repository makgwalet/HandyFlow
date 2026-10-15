import { useEffect, useState } from "react"
import { auditDashboardsApi, type ExternalDashboard } from "../../api/auditDashboards.api"
import { Card, Tile, TileRow } from "../../components/DashboardBits"

const MUTED = "var(--hf-text-muted)"

export function ExternalAuditDashboard({ engagements }: { engagements: { id: string; name: string }[] }) {
  const [engagementId, setEngagementId] = useState("")
  const [d, setD] = useState<ExternalDashboard | null>(null)
  const [error, setError] = useState("")

  useEffect(() => {
    setError("")
    auditDashboardsApi.external(engagementId || undefined).then(setD)
      .catch((e: any) => setError(e?.response?.data?.message ?? "Failed to load the dashboard"))
  }, [engagementId])

  return (
    <div>
      <label style={{ fontSize: 12, color: MUTED }}>Engagement{" "}
        <select aria-label="Dashboard engagement" value={engagementId} onChange={e => setEngagementId(e.target.value)}>
          <option value="">All engagements</option>
          {engagements.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
        </select>
      </label>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginTop: 8 }}>{error}</div>}
      {!d && !error && <div style={{ color: MUTED, fontSize: 13, marginTop: 8 }}>Loading…</div>}
      {d && (
        <div style={{ marginTop: 12 }}>
          <TileRow>
            <Tile label="Requests waiting on us" value={d.requests.awaitingBusiness} sub={d.requests.dueSoon ? `${d.requests.dueSoon} due within 3 days` : undefined} />
            <Tile label="Overdue responses" value={d.overdueTotal} tone={d.overdueTotal ? "danger" : undefined} sub="requests, queries and notes" />
            <Tile label="Open queries" value={d.queries.awaitingBusiness} sub={d.queries.overdue ? `${d.queries.overdue} overdue` : undefined} />
            <Tile label="Unresolved review notes" value={d.reviewNotes.unresolved} />
            <Tile label="Waiting on the auditor" value={d.requests.awaitingAuditor + d.queries.awaitingAuditor} sub="submitted or answered" />
          </TileRow>
          <Card title="By engagement">
            {d.engagements.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>Nothing yet.</span> : (
              <table style={{ width: "100%", fontSize: 13, borderCollapse: "collapse" }}>
                <thead><tr style={{ textAlign: "left", color: MUTED }}><th>Engagement</th><th>Open requests</th><th>Overdue</th><th>Open queries</th><th>Unresolved notes</th></tr></thead>
                <tbody>{d.engagements.map(r => (
                  <tr key={r.engagementId ?? "none"}><td>{r.name}</td><td>{r.openRequests}</td><td>{r.overdueRequests}</td><td>{r.openQueries}</td><td>{r.unresolvedNotes}</td></tr>
                ))}</tbody>
              </table>
            )}
          </Card>
          <Card title="Most overdue">
            {d.overdueItems.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>Nothing overdue.</span> : (
              <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
                {d.overdueItems.map(i => <li key={`${i.type}-${i.reference}`}>{i.reference} {i.title} — {i.engagement}, {i.daysOverdue} day{i.daysOverdue === 1 ? "" : "s"} late</li>)}
              </ul>
            )}
          </Card>
        </div>
      )}
    </div>
  )
}
