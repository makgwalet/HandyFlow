import { useEffect, useState } from "react"
import { auditDashboardsApi, type InternalDashboard } from "../../api/auditDashboards.api"
import type { Engagement } from "./auditShared"
import { Legend, Meter, Panel, Pill, Ring, Stat } from "./auditUi"
import { GROUP_COLOR, SEVERITY_COLOR, grid } from "./auditUi.styles"
import { severityRows, shortDate, upcomingDates } from "./auditUi.logic"

const MUTED = "var(--hf-text-muted)"
const QUICK: { id: string; label: string; hint: string }[] = [
  { id: "engagements", label: "Engagements", hint: "Start or open an engagement" },
  { id: "universe", label: "Audit universe", hint: "Add or reassess an area" },
  { id: "follow-ups", label: "Follow-ups", hint: "Controls and actions to chase" },
  { id: "committee", label: "Risk & control report", hint: "For the audit committee" },
]

/**
 * The audit landing page: how much of the plan is done, what is open and late, and what is coming up.
 * `engagements` and `onGo` are optional: without them the key dates and quick links are left out.
 */
export function InternalAuditDashboard({ engagements, onGo, today }: { engagements?: Engagement[]; onGo?: (section: string) => void; today?: string }) {
  const [d, setD] = useState<InternalDashboard | null>(null)
  const [error, setError] = useState("")
  useEffect(() => {
    auditDashboardsApi.internal().then(setD).catch((e: { response?: { data?: { message?: string } } }) => setError(e?.response?.data?.message ?? "Failed to load the dashboard"))
  }, [])

  if (error) return <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</div>
  if (!d) return <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>

  const { plan, coverage } = d
  const sev = severityRows(d.findingsBySeverity)
  const live = plan.total - plan.deferred
  const dates = engagements ? upcomingDates(engagements, today ?? new Date().toISOString().slice(0, 10)) : []

  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div style={grid(170)}>
        <Stat label={plan.year ? `Audits in the ${plan.year} plan` : "Annual plan"} value={plan.year ? live : "No plan"}
          sub={plan.year ? `${plan.inProgress} in progress, ${plan.planned} not started` : undefined} />
        <Stat label="Open findings" value={d.openFindings} sub={d.findingsPastDue ? `${d.findingsPastDue} past due` : undefined} tone={d.findingsPastDue ? "danger" : undefined} />
        <Stat label="Overdue actions" value={d.overdueActionCount} tone={d.overdueActionCount ? "danger" : undefined}
          onClick={onGo && d.overdueActionCount ? () => onGo("follow-ups") : undefined} />
        <Stat label="Repeat findings" value={d.repeatFindingCount} tone={d.repeatFindingCount ? "warning" : undefined} sub="reopened or previously closed" />
      </div>

      <div style={grid(300)}>
        <Panel title="Audit progress">
          {plan.year ? (
            <div style={{ display: "flex", gap: 18, alignItems: "center", flexWrap: "wrap" }}>
              <Ring centre={`${plan.completionPct}%`} caption="Completed"
                parts={[{ key: "completed", value: plan.completed }, { key: "inProgress", value: plan.inProgress }, { key: "planned", value: plan.planned }]} />
              <div style={{ flex: 1, minWidth: 140 }}>
                <Legend items={[
                  { label: "Completed", value: plan.completed, color: GROUP_COLOR.completed },
                  { label: "In progress", value: plan.inProgress, color: GROUP_COLOR.inProgress },
                  { label: "Planned", value: plan.planned, color: GROUP_COLOR.planned },
                ]} />
                <div style={{ fontSize: 12, color: MUTED, marginTop: 8 }}>{`${plan.completed} of ${live} audits${plan.deferred ? ` (${plan.deferred} deferred)` : ""}`}</div>
              </div>
            </div>
          ) : <span style={{ fontSize: 13, color: MUTED }}>There is no annual plan yet. Create one under Annual Plans.</span>}
        </Panel>

        <Panel title="Findings by severity">
          {d.findingsBySeverity.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>No findings yet.</span> : (
            <div style={{ display: "grid", gap: 10 }}>
              {sev.map(r => (
                <div key={r.severity} style={{ display: "grid", gridTemplateColumns: "70px 1fr 56px", gap: 10, alignItems: "center", fontSize: 13 }}>
                  <span style={{ color: "var(--hf-text-secondary)" }}>{r.label}</span>
                  <Meter pct={r.widthPct} color={SEVERITY_COLOR[r.severity] ?? "var(--hf-primary)"} label={`${r.label} open findings`} />
                  <span style={{ textAlign: "right", color: "var(--hf-text)" }}><strong>{r.open}</strong><span style={{ color: MUTED }}> / {r.total}</span></span>
                </div>
              ))}
              <div style={{ fontSize: 11.5, color: MUTED }}>Open of all findings raised</div>
            </div>
          )}
        </Panel>

        <Panel title="Coverage of the audit universe">
          <div style={{ display: "grid", gap: 12, fontSize: 13 }}>
            <div>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}><span>Planned</span><strong>{coverage.plannedPct}%</strong></div>
              <Meter pct={coverage.plannedPct} label="Universe planned" />
              <div style={{ fontSize: 11.5, color: MUTED, marginTop: 3 }}>{coverage.planned} of {coverage.universeSize} areas</div>
            </div>
            <div>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}><span>Audited</span><strong>{coverage.auditedPct}%</strong></div>
              <Meter pct={coverage.auditedPct} color="var(--hf-success-text)" label="Universe audited" />
              <div style={{ fontSize: 11.5, color: MUTED, marginTop: 3 }}>{coverage.audited} of {coverage.universeSize} areas</div>
            </div>
          </div>
        </Panel>
      </div>

      <div style={grid(320)}>
        <Panel title="Overdue actions" action={onGo && d.overdueActionCount > 0 ? <LinkButton onClick={() => onGo("follow-ups")}>View all</LinkButton> : undefined}>
          {d.overdueActions.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>Nothing overdue.</span> : (
            <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13, display: "grid", gap: 4 }}>
              {d.overdueActions.map(a => <li key={a.id}>{a.description} — due {a.dueDate} ({a.daysOverdue} day{a.daysOverdue === 1 ? "" : "s"} late)</li>)}
            </ul>
          )}
          {d.overdueActionCount > d.overdueActions.length && <div style={{ fontSize: 12, color: MUTED, marginTop: 6 }}>Showing the {d.overdueActions.length} oldest of {d.overdueActionCount}.</div>}
        </Panel>

        <Panel title="Repeat findings">
          {d.repeatFindings.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>None.</span> : (
            <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13, display: "grid", gap: 4 }}>
              {d.repeatFindings.map(f => <li key={f.id}>{f.title} ({f.severity ?? "unrated"}, {f.status.toLowerCase().replace("_", " ")})</li>)}
            </ul>
          )}
        </Panel>

        <Panel title="Areas not in this year's plan">
          {coverage.universeSize === 0 ? <span style={{ fontSize: 13, color: MUTED }}>The audit universe is empty.</span>
            : coverage.notPlanned.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>Every area is planned.</span> : (
            <div style={{ fontSize: 13 }}>{coverage.notPlanned.join(", ")}{coverage.universeSize - coverage.planned > coverage.notPlanned.length ? " …" : ""}</div>
          )}
        </Panel>
      </div>

      {(engagements || onGo) && (
        <div style={grid(320)}>
          {engagements && (
            <Panel title="Upcoming key dates">
              {dates.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>No engagement starts or ends in the next 90 days.</span> : (
                <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 10 }}>
                  {dates.map(k => (
                    <li key={`${k.engagementId}-${k.kind}`} style={{ display: "grid", gridTemplateColumns: "92px 1fr auto", gap: 10, alignItems: "center", fontSize: 13 }}>
                      <span style={{ color: MUTED }}>{shortDate(k.date)}</span>
                      <span><strong style={{ color: "var(--hf-text)" }}>{k.title}</strong><span style={{ display: "block", fontSize: 12, color: MUTED }}>{k.detail}</span></span>
                      <Pill label={k.kind === "start" ? "Starts" : k.kind === "end" ? "Ends" : "Late"} tone={k.kind === "late" ? "warning" : k.kind === "start" ? "info" : "muted"} />
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
          )}
          {onGo && (
            <Panel title="Go to">
              <div style={{ display: "grid", gap: 8 }}>
                {QUICK.map(q => (
                  <button key={q.id} type="button" onClick={() => onGo(q.id)}
                    style={{ textAlign: "left", padding: "10px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface-muted)", cursor: "pointer", font: "inherit" }}>
                    <strong style={{ fontSize: 13, color: "var(--hf-text)" }}>{q.label}</strong>
                    <span style={{ display: "block", fontSize: 12, color: MUTED }}>{q.hint}</span>
                  </button>
                ))}
              </div>
            </Panel>
          )}
        </div>
      )}
    </div>
  )
}

function LinkButton({ onClick, children }: { onClick: () => void; children: string }) {
  return <button type="button" onClick={onClick} style={{ background: "none", border: "none", color: "var(--hf-primary-text)", fontSize: 12, fontWeight: 600, cursor: "pointer", padding: 0 }}>{children}</button>
}
