// src/pages/customers/CrmPage.tsx — the CRM home. Sections are routes (/customers/:section) with navigation in the
// sidebar (see navigation/moduleSections.ts CRM_SECTIONS and components/shell/SectionedModulePage).
import { Suspense, lazy } from "react"
import { CustomersPage } from "./CustomersPage"
const PipelineBoard = lazy(() => import("./PipelineBoard")), TaskQueue = lazy(() => import("./TaskQueue")), FunnelReport = lazy(() => import("./FunnelReport")), DuplicatesPanel = lazy(() => import("./DuplicatesPanel")), ForecastPanel = lazy(() => import("./ForecastPanel")), TeamPanel = lazy(() => import("./TeamPanel")), CustomFieldsAdmin = lazy(() => import("./CustomFieldsAdmin")), DashboardPanel = lazy(() => import("./DashboardPanel")), SegmentsPanel = lazy(() => import("./SegmentsPanel")), OpportunitiesPanel = lazy(() => import("./OpportunitiesPanel")), GroupsPanel = lazy(() => import("./GroupsPanel")), ScoringRoutingPanel = lazy(() => import("./ScoringRoutingPanel")), WorkflowsPanel = lazy(() => import("./WorkflowsPanel")), ReportsPanel = lazy(() => import("./ReportsPanel")), AuditPanel = lazy(() => import("./AuditPanel")), IntegrationsPanel = lazy(() => import("./IntegrationsPanel")), CampaignsPanel = lazy(() => import("./CampaignsPanel"))

import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { CRM_SECTIONS } from "../../navigation/moduleSections"

export function CrmPage() {
  return (
    <SectionedModulePage config={CRM_SECTIONS} render={(id, goTo) => (
      <Suspense fallback={<p>Loading…</p>}>
        {id === "dashboard" ? <DashboardPanel go={goTo} /> : id === "customers" ? <CustomersPage /> : id === "pipeline" ? <PipelineBoard /> : id === "opportunities" ? <OpportunitiesPanel /> : id === "groups" ? <GroupsPanel /> : id === "rules" ? <ScoringRoutingPanel /> : id === "automation" ? <WorkflowsPanel /> : id === "reports" ? <ReportsPanel /> : id === "audit" ? <AuditPanel /> : id === "integrations" ? <IntegrationsPanel /> : id === "campaigns" ? <CampaignsPanel /> : id === "tasks" ? <TaskQueue /> : id === "forecast" ? <ForecastPanel /> : id === "team" ? <TeamPanel /> : id === "segments" ? <SegmentsPanel /> : id === "fields" ? <CustomFieldsAdmin /> : id === "duplicates" ? <DuplicatesPanel /> : id === "funnel" ? <FunnelReport /> : null}
      </Suspense>
    )} />
  )
}
