// src/pages/debtcollection/DebtCollectionPage.tsx
//
// Module 2a (Debt Collection — Internal). Confirmed against the real
// synced backend: za.co.handyflow.platform.debtcollection, present in the
// GitHub sync already (no push-gap this time, unlike Module 1's
// legalcompliance). Two controllers — DebtCollectionCaseController
// (/api/v1/debtcollection/cases/...) and PaymentPlanController
// (/api/v1/debtcollection/payment-plans/{id}/...) — every DTO, enum, and
// entity method read directly from source.
//
// Internal-only — no portal for this sub-module (the outsourced
// Collections Agency sibling, which DOES get a portal, is Module 2b, a
// separate build).
//
// Sections are routes (/debtcollection/:section) with navigation in the
// sidebar (see navigation/moduleSections.ts and
// components/shell/SectionedModulePage).
import DebtCollectionDashboard from "./DebtCollectionDashboard"
import CasesTab from "./CasesTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { DEBT_COLLECTION_SECTIONS } from "../../navigation/moduleSections"

export function DebtCollectionPage() {
  return (
    <SectionedModulePage config={DEBT_COLLECTION_SECTIONS}
      subtitle="Internal collection cases · Contact trail · Structured payment plans · Demand letters"
      render={(id, goTo) => {
        switch (id) {
          case "dashboard": return <DebtCollectionDashboard onNavigate={goTo} />
          case "cases":     return <CasesTab />
          default:          return null
        }
      }} />
  )
}
