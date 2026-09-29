// src/pages/legalcompliance/LegalCompliancePage.tsx
//
// Module 1 (of the Track 7 build order): internal-only, no portal. See each
// tab file's header comment for its specific endpoints.
//
// Sections are routes (/legalcompliance/:section) with navigation in the
// sidebar (see navigation/moduleSections.ts and
// components/shell/SectionedModulePage).
import LegalComplianceDashboard from "./LegalComplianceDashboard"
import ObligationsTab from "./ObligationsTab"
import LitigationTab from "./LitigationTab"
import PopiaTab from "./PopiaTab"
import DsarTab from "./DsarTab"
import CalendarTab from "./CalendarTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { LEGAL_COMPLIANCE_SECTIONS } from "../../navigation/moduleSections"

export function LegalCompliancePage() {
  return (
    <SectionedModulePage config={LEGAL_COMPLIANCE_SECTIONS}
      subtitle="Regulatory obligations · Litigation register · POPIA processing activities · Data subject access requests"
      render={(id, goTo) => {
        switch (id) {
          case "dashboard":   return <LegalComplianceDashboard onNavigate={goTo} />
          case "obligations": return <ObligationsTab />
          case "litigation":  return <LitigationTab />
          case "popia":       return <PopiaTab />
          case "dsar":        return <DsarTab />
          case "calendar":    return <CalendarTab />
          default:            return null
        }
      }} />
  )
}
