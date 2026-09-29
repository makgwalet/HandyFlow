// src/pages/compliancetender/CompliancePage.tsx
//
// Business Compliance & Tender. Sections are routes
// (/compliancetender/:section) with navigation in the sidebar (see
// navigation/moduleSections.ts and components/shell/SectionedModulePage).
// Tender detail pages are a separate route, /compliancetender/tenders/:id.
import ComplianceDashboard from "./ComplianceDashboard"
import RegistrationsTab from "./RegistrationsTab"
import DocumentsTab from "./DocumentsTab"
import DeadlinesTab from "./DeadlinesTab"
import TendersTab from "./TendersTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { COMPLIANCE_TENDER_SECTIONS } from "../../navigation/moduleSections"

export default function CompliancePage() {
  return (
    <SectionedModulePage config={COMPLIANCE_TENDER_SECTIONS}
      subtitle="CIPC, SARS, UIF, PSIRA, CSD, cidb & NHBRC registrations · Document vault · Compliance calendar · Tender workspace"
      render={(id, goTo) => {
        switch (id) {
          case "dashboard":     return <ComplianceDashboard onNavigate={goTo} />
          case "registrations": return <RegistrationsTab />
          case "documents":     return <DocumentsTab />
          case "deadlines":     return <DeadlinesTab />
          case "tenders":       return <TendersTab />
          default:              return null
        }
      }} />
  )
}
