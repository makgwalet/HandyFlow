// src/pages/security/SecurityPage.tsx
import type { ReactNode } from "react"
import { Navigate, useNavigate, useParams } from "react-router-dom"
import SecurityDashboard  from "./SecurityDashboard"
import GuardsTab          from "./GuardsTab"
import SitesTab           from "./SitesTab"
import PostOrdersTab      from "./PostOrdersTab"
import GateAccessTab      from "./GateAccessTab"
import PatrolRoutesTab    from "./PatrolRoutesTab"
import RotationPatternsTab from "./RotationPatternsTab"
import ShiftSwapsTab      from "./ShiftSwapsTab"
import GuardScreeningTab  from "./GuardScreeningTab"
import ShiftsTab          from "./ShiftsTab"
import IncidentsTab       from "./IncidentsTab"
import LiveMapTab         from "./LiveMapTab"
import ArmouryTab         from "./ArmouryTab"
import ControlRoomTab     from "./ControlRoomTab"
import DeviceSessionsTab  from "./DeviceSessionsTab"
import CctvTab            from "./CctvTab"
import ReportsTab         from "./ReportsTab"
import CloseProtectionTab from "./CloseProtectionTab"
import PayrollTab         from "./PayrollTab"
import BranchesTab        from "./BranchesTab"
import PublicApiTab       from "./PublicApiTab"
import { PageHeader } from "../../components/ui/PageHeader"
import {
  SECURITY_SECTIONS, SECURITY_SECTION_ALIASES, findSection,
} from "../../navigation/moduleSections"

/**
 * Security module. Each section is a route (/security/:section), with
 * navigation in the sidebar (see navigation/moduleSections.ts). This replaces
 * the previous three-level in-page tab strips, which needed a horizontal
 * scrollbar and could not be deep-linked.
 */
export function SecurityPage() {
  const { section: rawSection } = useParams<{ section?: string }>()
  const navigate = useNavigate()
  const base = SECURITY_SECTIONS.basePath

  const sectionId = rawSection ? (SECURITY_SECTION_ALIASES[rawSection] ?? rawSection) : undefined
  const found = findSection(SECURITY_SECTIONS, sectionId)
  if (!found || sectionId !== rawSection) {
    // Bare /security, a renamed id, or an unknown one: land on a real section.
    return <Navigate replace to={`${base}/${found ? found.section.id : SECURITY_SECTIONS.defaultSection}`} />
  }
  const { section, group } = found
  const goTo = (id: string) => navigate(`${base}/${id}`)

  const content: Record<string, ReactNode> = {
    "dashboard":         <SecurityDashboard onNavigate={goTo} />,
    "control-room":      <ControlRoomTab />,
    "live":              <LiveMapTab />,
    "shifts":            <ShiftsTab />,
    "incidents":         <IncidentsTab />,
    "patrol-routes":     <PatrolRoutesTab />,
    "post-orders":       <PostOrdersTab />,
    "gate-access":       <GateAccessTab />,
    "guards":            <GuardsTab />,
    "guard-screening":   <GuardScreeningTab />,
    "rotation-patterns": <RotationPatternsTab />,
    "shift-swaps":       <ShiftSwapsTab />,
    "payroll":           <PayrollTab />,
    "sites":             <SitesTab />,
    "branches":          <BranchesTab />,
    "armoury":           <ArmouryTab />,
    "cctv":              <CctvTab />,
    "sessions":          <DeviceSessionsTab />,
    "close-protection":  <CloseProtectionTab />,
    "reports":           <ReportsTab />,
    "public-api":        <PublicApiTab />,
  }

  return (
    <div>
      <PageHeader
        title={section.label}
        icon={section.icon}
        breadcrumbs={[
          { label: SECURITY_SECTIONS.title, to: `${base}/${SECURITY_SECTIONS.defaultSection}` },
          { label: group.label },
          { label: section.label },
        ]}
      />
      {/* key: remount on section change so each section starts fresh, as it
          did when it was a conditionally rendered tab. */}
      <div key={section.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14, padding: 24 }}>
        {content[section.id]}
      </div>
    </div>
  )
}
