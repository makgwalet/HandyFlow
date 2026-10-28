// One engagement: how far it has got, its dates, and its workpapers, findings and actions.

import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { auditEngagementApi } from "../../api/auditEngagementSummary.api"
import type { Engagement, UserOption } from "./auditShared"
import { Meter, Panel, Pill, Stat, StagePill } from "./auditUi"
import { grid } from "./auditUi.styles"
import { engagementStage, shortDate } from "./auditUi.logic"
import { MILESTONE_STYLE, phaseColor, rand, roleLabel } from "./engagementDetail.logic"
import { RemediationSection } from "./RemediationSection"
import { WorkpapersSplit } from "./WorkpapersSplit"

const MUTED = "var(--hf-text-muted)"
type Tab = "overview" | "planning" | "team" | "workpapers" | "findings"
const TABS: { id: Tab; label: string }[] = [{ id: "overview", label: "Overview" }, { id: "planning", label: "Planning" }, { id: "team", label: "Team" }, { id: "workpapers", label: "Workpapers" }, { id: "findings", label: "Findings & actions" }]

export function EngagementDetail({ engagement: e, users, onBack }: { engagement: Engagement; users: UserOption[]; onBack: () => void }) {
  const [tab, setTab] = useState<Tab>("overview")
  const { data, isLoading, isError } = useQuery({ queryKey: ["ia-engagement-summary", e.id], queryFn: () => auditEngagementApi.summary(e.id) })
  const stage = engagementStage(e.status)

  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 10, alignItems: "center", justifyContent: "space-between" }}>
        <button type="button" onClick={onBack} style={{ display: "inline-flex", alignItems: "center", gap: 6, background: "none", border: "none", padding: 0, color: "var(--hf-primary-text)", fontWeight: 600, fontSize: 13, cursor: "pointer" }}>
          <ArrowLeft size={14} /> All engagements
        </button>
        <div style={{ display: "flex", gap: 10, alignItems: "center", fontSize: 12, color: MUTED }}>
          <span>{shortDate(e.startDate)} – {shortDate(e.endDate)}</span>
          <StagePill label={stage.label} group={stage.group} />
        </div>
      </div>

      <div role="tablist" aria-label="Engagement sections" style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
        {TABS.map(t => (
          <button key={t.id} role="tab" aria-selected={tab === t.id} onClick={() => setTab(t.id)}
            style={{ padding: "7px 14px", borderRadius: 8, border: "1px solid var(--hf-border)", background: tab === t.id ? "var(--hf-info-soft)" : "var(--hf-surface)", color: tab === t.id ? "var(--hf-primary-text)" : "var(--hf-text-secondary)", fontWeight: 600, fontSize: 13, cursor: "pointer" }}>
            {t.label}
          </button>
        ))}
      </div>

      {tab === "overview" && (
        isLoading ? <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>
          : isError || !data ? <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>The engagement summary could not be loaded. Refresh the page; if it keeps failing, contact support.</div>
          : (
            <div style={{ display: "grid", gap: 14 }}>
              <div style={grid(170)}>
                <Stat label="Overall progress" value={`${data.overallPct}%`} sub={data.reportStatus ? `Report: ${data.reportStatus.replace(/_/g, " ").toLowerCase()}` : "No report yet"} />
                <Stat label="Workpapers signed off" value={`${data.counts.workpapersSignedOff} of ${data.counts.workpapers}`} />
                <Stat label="Open findings" value={data.counts.openFindings} tone={data.counts.findingsPastDue ? "danger" : undefined} sub={`${data.counts.findings} raised, ${data.counts.findingsPastDue} past due`} />
                <Stat label="Actions overdue" value={data.counts.actionsOverdue} tone={data.counts.actionsOverdue ? "danger" : undefined} sub={`${data.counts.actionsValidated} of ${data.counts.actions} validated`} />
                <Stat label="Team" value={data.counts.team} sub="assigned" />
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 14 }}>
                <Panel title="Progress by phase">
                  <div style={{ display: "grid", gap: 12 }}>
                    {data.phases.map(p => (
                      <div key={p.key}>
                        <Meter pct={p.pct} color={phaseColor(p)} label={`${p.label} ${p.pct}%`} />
                        <div style={{ fontSize: 11.5, color: MUTED, marginTop: 3 }}>{p.basis}</div>
                      </div>
                    ))}
                  </div>
                </Panel>
                <Panel title="Milestones">
                  <ol style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 10 }}>
                    {data.milestones.map(m => {
                      const st = MILESTONE_STYLE[m.status]
                      return (
                        <li key={m.key} style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "center", fontSize: 13 }}>
                          <span><span style={{ fontWeight: 600, color: "var(--hf-text)" }}>{m.label}</span> <span style={{ color: MUTED }}>{m.date ? shortDate(m.date) : "no date set"}</span></span>
                          <Pill label={st.label} tone={st.tone} />
                        </li>
                      )
                    })}
                  </ol>
                </Panel>
              </div>
              <p style={{ fontSize: 12, color: MUTED, margin: 0 }}>Progress is worked out from the work recorded here. Sampling and testing, and changes to planning and the team, are still made in the Engagements list.</p>
            </div>
          )
      )}
      {tab === "planning" && <PlanningView e={e} />}
      {tab === "team" && <TeamView e={e} />}
      {tab === "workpapers" && <WorkpapersSplit engagementId={e.id} users={users} />}
      {tab === "findings" && <RemediationSection engagementId={e.id} />}
    </div>
  )
}

const label: React.CSSProperties = { fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", marginBottom: 4 }
const text: React.CSSProperties = { fontSize: 13, color: "var(--hf-text)", whiteSpace: "pre-wrap", overflowWrap: "anywhere" }
const none = <span style={{ fontSize: 13, color: MUTED }}>Not set</span>

function PlanningView({ e }: { e: Engagement }) {
  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))", gap: 14 }}>
        <Panel title="Objectives"><div style={text}>{e.objectives?.trim() || none}</div></Panel>
        <Panel title="Scope"><div style={text}>{e.scope?.trim() || none}</div></Panel>
        <Panel title="Audit criteria"><div style={text}>{e.auditCriteria?.trim() || none}</div></Panel>
      </div>
      <Panel title="Materiality">
        <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "6px 16px", margin: 0, fontSize: 13 }}>
          <dt style={label}>Overall</dt><dd style={{ margin: 0 }}>{rand(e.overallMateriality)}</dd>
          <dt style={label}>Performance</dt><dd style={{ margin: 0 }}>{rand(e.performanceMateriality)}</dd>
          <dt style={label}>Clearly trivial</dt><dd style={{ margin: 0 }}>{rand(e.clearlyTrivialThreshold)}</dd>
        </dl>
        {e.specificMateriality.length > 0 && (
          <ul style={{ listStyle: "none", margin: "12px 0 0", padding: 0, display: "grid", gap: 4, fontSize: 13 }}>
            {e.specificMateriality.map(m => <li key={m.id}><strong>{m.accountOrGlSegment}</strong>: {rand(m.threshold)}</li>)}
          </ul>
        )}
      </Panel>
    </div>
  )
}

function TeamView({ e }: { e: Engagement }) {
  return (
    <Panel title={`Team (${e.assignments.length})`}>
      {e.assignments.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>Nobody has been assigned to this engagement yet.</span> : (
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8 }}>
          {e.assignments.map(a => (
            <li key={a.id} style={{ display: "flex", justifyContent: "space-between", gap: 10, fontSize: 13, paddingBottom: 8, borderBottom: "1px solid var(--hf-border-subtle)" }}>
              <span style={{ fontWeight: 600, color: "var(--hf-text)" }}>{a.userName}</span><Pill label={roleLabel(a.role)} tone="info" />
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}
