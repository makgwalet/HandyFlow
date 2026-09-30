// src/pages/collectionsagency/CollectionsAgencyPage.tsx
//
// Module 2b (Collections Agency — outsourced third-party debt collector).
// Confirmed against the real synced backend:
// za.co.handyflow.platform.collectionsagency — 9 controllers, 27 DTOs,
// every field read directly from source before this was written.
//
// Sibling of Module 2a (debtcollection, internal-only) but a genuinely
// separate module: no dependency between them in either direction. This
// module additionally has a client-facing portal (see
// ../collectionsagency-portal/) — the agency's own creditor clients log
// in to see their placed portfolio and trust/remittance statement.
//
// Same shell pattern as DebtCollectionPage.tsx: thin tab shell, inline
// styles, one ACCENT constant, each tab's real content in its own file.
//
// Sections are routes (/collections-agency/:section) with navigation in the
// sidebar (see navigation/moduleSections.ts and
// components/shell/SectionedModulePage). The creditor-client portal
// (/collections-agency/portal/*) is routed separately, outside the shell.
import CollAgencyDashboard from "./CollAgencyDashboard"
import CollAgencyClientsTab from "./CollAgencyClientsTab"
import CollAgencyCollectorsTab from "./CollAgencyCollectorsTab"
import CollAgencyProfileTab from "./CollAgencyProfileTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { COLLECTIONS_AGENCY_SECTIONS } from "../../navigation/moduleSections"

export function CollectionsAgencyPage() {
  return (
    <SectionedModulePage config={COLLECTIONS_AGENCY_SECTIONS}
      subtitle="Creditor client portfolios · Placement & recovery · Trust ledger · Commission billing"
      render={(id, goTo) => {
        switch (id) {
          case "dashboard":  return <CollAgencyDashboard onNavigate={goTo} />
          case "clients":    return <CollAgencyClientsTab />
          case "collectors": return <CollAgencyCollectorsTab />
          case "profile":    return <CollAgencyProfileTab />
          default:           return null
        }
      }} />
  )
}
