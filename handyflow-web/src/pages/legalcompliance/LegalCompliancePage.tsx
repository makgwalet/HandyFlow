// src/pages/legalcompliance/LegalCompliancePage.tsx
//
// Legal & Compliance. Each screen is a route-based sidebar section
// (LEGAL_COMPLIANCE_SECTIONS in navigation/moduleSections.ts), like Contracting
// and Fuel. The section ids are also what the backend sends as `tab` in the
// executive overview's indicators and attention items, so they must stay in
// step with that menu.
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { LEGAL_COMPLIANCE_SECTIONS } from "../../navigation/moduleSections"
import LegalComplianceDashboard from "./LegalComplianceDashboard"
import ObligationsTab from "./ObligationsTab"
import RegulatoryRegisterTab from "./RegulatoryRegisterTab"
import ApplicabilityTab from "./ApplicabilityTab"
import RegulatoryChangesTab from "./RegulatoryChangesTab"
import RiskRegisterTab from "./RiskRegisterTab"
import ControlsTab from "./ControlsTab"
import ControlTestsTab from "./ControlTestsTab"
import FindingsTab from "./FindingsTab"
import AuditsTab from "./AuditsTab"
import CapaTab from "./CapaTab"
import PoliciesTab from "./PoliciesTab"
import AttestationsTab from "./AttestationsTab"
import IncidentsTab from "./IncidentsTab"
import PrivacyTab from "./PrivacyTab"
import FicaTab from "./FicaTab"
import ThirdPartiesTab from "./ThirdPartiesTab"
import LicencesTab from "./LicencesTab"
import CommitteesTab from "./CommitteesTab"
import EthicsTab from "./EthicsTab"
import ExecutiveTab from "./ExecutiveTab"
import ReportsTab from "./ReportsTab"
import ProvidersTab from "./ProvidersTab"
import ContinuityTab from "./ContinuityTab"
import EsgTab from "./EsgTab"
import MonitoringTab from "./MonitoringTab"
import EcosystemTab from "./EcosystemTab"
import { usePermission } from "../../hooks/usePermission"
import LitigationTab from "./LitigationTab"
import PopiaTab from "./PopiaTab"
import DsarTab from "./DsarTab"
import CalendarTab from "./CalendarTab"
import DeclarationCampaignsTab from "./DeclarationCampaignsTab"
import WrittenResolutionsTab from "./WrittenResolutionsTab"

export function LegalCompliancePage() {
  // FICA is restricted (escalations must not be visible to people who do not need them) and applies only to accountable institutions.
  const hasFica = usePermission("LEGALCOMPLIANCE_FICA_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  // Provider tools are for consultancies (and for administrators, who also manage which providers may see their own organisation).
  const hasProviderManage = usePermission("LEGALCOMPLIANCE_PROVIDER_MANAGE")
  const hasProviderConsult = usePermission("LEGALCOMPLIANCE_PROVIDER_CONSULT")
  const showProviders = hasProviderManage || hasProviderConsult || hasAdmin
  const showFica = hasFica || hasAdmin
  // "Across HandyFlow" shows other modules' compliance counts; it follows the same permission as its menu entry.
  const hasManage = usePermission("LEGALCOMPLIANCE_MANAGE")
  const showEcosystem = hasManage || hasAdmin

  return (
    <SectionedModulePage
      config={LEGAL_COMPLIANCE_SECTIONS}
      subtitle="Regulatory library, risks and controls, audits, policies, privacy, third parties, licences, ethics, FICA, continuity and ESG."
      render={(id, goTo) => {
        switch (id) {
          case "dashboard":     return <LegalComplianceDashboard onNavigate={goTo} />
          case "executive":     return <ExecutiveTab onNavigate={goTo} />
          case "reports":       return <ReportsTab />
          case "providers":     return showProviders ? <ProvidersTab /> : null
          case "continuity":    return <ContinuityTab />
          case "esg":           return <EsgTab />
          case "regulatory":    return <RegulatoryRegisterTab />
          case "applicability": return <ApplicabilityTab />
          case "changes":       return <RegulatoryChangesTab />
          case "monitoring":    return <MonitoringTab />
          case "risks":         return <RiskRegisterTab />
          case "controls":      return <ControlsTab />
          case "testing":       return <ControlTestsTab />
          case "findings":      return <FindingsTab />
          case "audits":        return <AuditsTab />
          case "capa":          return <CapaTab />
          case "policies":      return <PoliciesTab />
          case "attestations":  return <AttestationsTab />
          case "declarations":  return <DeclarationCampaignsTab />
          case "incidents":     return <IncidentsTab />
          case "privacy":       return <PrivacyTab />
          case "thirdparties":  return <ThirdPartiesTab />
          case "licences":      return <LicencesTab />
          case "committees":    return <CommitteesTab />
          case "written-resolutions":  return <WrittenResolutionsTab />
          case "ethics":        return <EthicsTab />
          case "ecosystem":     return showEcosystem ? <EcosystemTab /> : null
          case "fica":          return showFica ? <FicaTab /> : null
          case "obligations":   return <ObligationsTab />
          case "litigation":    return <LitigationTab />
          case "popia":         return <PopiaTab />
          case "dsar":          return <DsarTab />
          case "calendar":      return <CalendarTab />
          default:              return null
        }
      }}
    />
  )
}
