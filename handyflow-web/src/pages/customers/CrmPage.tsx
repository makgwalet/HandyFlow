// src/pages/customers/CrmPage.tsx — the CRM home: customer register, sales pipeline board and funnel report.
import { Suspense, lazy, useState } from "react"
import { CustomersPage } from "./CustomersPage"
const PipelineBoard = lazy(() => import("./PipelineBoard")), TaskQueue = lazy(() => import("./TaskQueue")), FunnelReport = lazy(() => import("./FunnelReport")), DuplicatesPanel = lazy(() => import("./DuplicatesPanel")), ForecastPanel = lazy(() => import("./ForecastPanel")), TeamPanel = lazy(() => import("./TeamPanel")), CustomFieldsAdmin = lazy(() => import("./CustomFieldsAdmin")), DashboardPanel = lazy(() => import("./DashboardPanel")), SegmentsPanel = lazy(() => import("./SegmentsPanel")), OpportunitiesPanel = lazy(() => import("./OpportunitiesPanel")), GroupsPanel = lazy(() => import("./GroupsPanel")), ScoringRoutingPanel = lazy(() => import("./ScoringRoutingPanel")), WorkflowsPanel = lazy(() => import("./WorkflowsPanel")), ReportsPanel = lazy(() => import("./ReportsPanel")), AuditPanel = lazy(() => import("./AuditPanel")), IntegrationsPanel = lazy(() => import("./IntegrationsPanel")), CampaignsPanel = lazy(() => import("./CampaignsPanel"))

type Tab = "dashboard" | "customers" | "pipeline" | "opportunities" | "groups" | "rules" | "automation" | "reports" | "audit" | "integrations" | "campaigns" | "tasks" | "funnel" | "forecast" | "team" | "segments" | "duplicates" | "fields"
const TABS: { id: Tab; label: string }[] = [{ id: "dashboard", label: "Dashboard" }, { id: "customers", label: "Customers" }, { id: "pipeline", label: "Pipeline" }, { id: "opportunities", label: "Opportunities" }, { id: "groups", label: "Company groups" }, { id: "rules", label: "Scoring & routing" }, { id: "automation", label: "Automation" }, { id: "reports", label: "Scheduled reports" }, { id: "audit", label: "Audit log" }, { id: "integrations", label: "Integrations" }, { id: "campaigns", label: "Campaigns" }, { id: "tasks", label: "Tasks" }, { id: "funnel", label: "Funnel report" }, { id: "forecast", label: "Forecast" }, { id: "team", label: "Team" }, { id: "segments", label: "Segments" }, { id: "duplicates", label: "Duplicates" }, { id: "fields", label: "Fields" }]

export function CrmPage() {
  const [tab, setTab] = useState<Tab>("dashboard")
  return (
    <div>
      <div role="tablist" aria-label="CRM sections" style={{ display: "flex", gap: 6, padding: "0 4px 8px", borderBottom: "1px solid var(--hf-border-subtle)", marginBottom: 12 }}>
        {TABS.map(t => <button key={t.id} type="button" role="tab" aria-selected={tab === t.id} onClick={() => setTab(t.id)}
          style={{ padding: "6px 14px", borderRadius: 8, border: "none", cursor: "pointer", fontWeight: 700, fontSize: 13, background: tab === t.id ? "var(--hf-info-soft)" : "transparent", color: tab === t.id ? "var(--hf-primary-text)" : "var(--hf-text-muted)" }}>{t.label}</button>)}
      </div>
      <Suspense fallback={<p>Loading…</p>}>
        {tab === "dashboard" ? <DashboardPanel go={t => setTab(t as Tab)} /> : tab === "customers" ? <CustomersPage /> : tab === "pipeline" ? <PipelineBoard /> : tab === "opportunities" ? <OpportunitiesPanel /> : tab === "groups" ? <GroupsPanel /> : tab === "rules" ? <ScoringRoutingPanel /> : tab === "automation" ? <WorkflowsPanel /> : tab === "reports" ? <ReportsPanel /> : tab === "audit" ? <AuditPanel /> : tab === "integrations" ? <IntegrationsPanel /> : tab === "campaigns" ? <CampaignsPanel /> : tab === "tasks" ? <TaskQueue /> : tab === "forecast" ? <ForecastPanel /> : tab === "team" ? <TeamPanel /> : tab === "segments" ? <SegmentsPanel /> : tab === "fields" ? <CustomFieldsAdmin /> : tab === "duplicates" ? <DuplicatesPanel /> : <FunnelReport />}
      </Suspense>
    </div>
  )
}
