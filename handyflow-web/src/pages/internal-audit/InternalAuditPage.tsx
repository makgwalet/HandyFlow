// Internal Audit: routes each section of the module to its screen. Shared lists are loaded once here.

import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useNavigate, useParams } from "react-router-dom"
import { EngagementDetail } from "./EngagementDetail"
import { apiClient } from "../../api/client"
import { InternalAuditDashboard } from "./InternalAuditDashboard"
import { CommitteeReport } from "./CommitteeReport"
import { ReportsSection } from "./ReportsSection"
import { QualitySection } from "./QualitySection"
import { RemediationSection } from "./RemediationSection"
import { HeatMapSection } from "./HeatMapSection"
import { ProgrammeSection } from "./ProgrammeSection"
import { MonitoringSection } from "./MonitoringSection"
import { FollowUpSection } from "./FollowUpSection"
import { InvestigationsSection } from "./InvestigationsSection"
import { UniverseSection } from "./UniverseSection"
import { PlansSection } from "./PlansSection"
import { EngagementsSection } from "./EngagementsSection"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { INTERNAL_AUDIT_SECTIONS } from "../../navigation/moduleSections"
import type { UniverseEntry, AnnualPlan, Engagement, UserOption } from "./auditShared"

/** A failed load must not look like an empty list. */
function LoadFailed({ show }: { show: boolean }) {
  return show ? <div role="alert" style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 14 }}>These records could not be loaded. Refresh the page; if it keeps failing, contact support.</div> : null
}

export default function InternalAuditPage() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { engagementId } = useParams<{ engagementId?: string }>()

  const { data: universe = [], isLoading: universeLoading, isError: universeError } = useQuery<UniverseEntry[]>({
    queryKey: ["ia-universe"],
    queryFn: async () => (await apiClient.get("/api/v1/internal-audit/universe")).data,
  })
  const { data: plans = [], isLoading: plansLoading, isError: plansError } = useQuery<AnnualPlan[]>({
    queryKey: ["ia-plans"],
    queryFn: async () => (await apiClient.get("/api/v1/internal-audit/plans")).data,
  })
  const { data: engagements = [], isLoading: engagementsLoading, isError: engagementsError } = useQuery<Engagement[]>({
    queryKey: ["ia-engagements"],
    queryFn: async () => (await apiClient.get("/api/v1/internal-audit/engagements")).data,
  })
  const { data: users = [] } = useQuery<UserOption[]>({
    queryKey: ["ia-users"],
    queryFn: async () => (await apiClient.get("/api/v1/identity/users?size=200")).data?.content ?? (await apiClient.get("/api/v1/identity/users?size=200")).data,
  })

  // Sections are routes (/internal-audit/:section) with navigation in the sidebar
  // (see navigation/moduleSections.ts). The shared data above is loaded once for
  // all three sections, as before.
  const current = engagementId ? engagements.find(e => e.id === engagementId) : undefined
  const detail = engagementId ? { label: current?.name ?? "Engagement", subtitle: current?.universeEntryName } : undefined
  return (
    <SectionedModulePage config={INTERNAL_AUDIT_SECTIONS} detail={detail}
      subtitle="Risk-based audit planning and delivery: universe, plans, engagements, reporting, quality and monitoring"
      render={id => {
        if (engagementId) return engagementsLoading ? null : current ? <EngagementDetail engagement={current} users={users} onBack={() => navigate("/internal-audit/engagements")} /> : <LoadFailed show />
        switch (id) {
          case "reports":     return <ReportsSection />
          case "findings":    return <RemediationSection />
          case "heat-map":    return <HeatMapSection onGo={section => navigate(`/internal-audit/${section}`)} />
          case "quality":     return <QualitySection />
          case "programme":   return <ProgrammeSection />
          case "monitoring":  return <MonitoringSection />
          case "follow-ups":  return <FollowUpSection />
          case "investigations": return <InvestigationsSection />
          case "committee":   return <CommitteeReport />
          case "dashboard":   return <InternalAuditDashboard engagements={engagements} onGo={section => navigate(`/internal-audit/${section}`)} />
          case "universe":    return <><LoadFailed show={universeError} /><UniverseSection universe={universe} isLoading={universeLoading} qc={qc} /></>
          case "plans":       return <><LoadFailed show={plansError} /><PlansSection plans={plans} universe={universe} isLoading={plansLoading} qc={qc} /></>
          case "engagements": return <><LoadFailed show={engagementsError} /><EngagementsSection engagements={engagements} universe={universe} plans={plans} users={users} isLoading={engagementsLoading} qc={qc} /></>
          default:            return null
        }
      }} />
  )
}

