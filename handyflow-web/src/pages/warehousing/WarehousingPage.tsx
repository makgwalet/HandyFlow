// src/pages/warehousing/WarehousingPage.tsx
//
// Thin tab-shell, same shape as DebtCollectionPage.tsx (the reference
// file for this whole build session): useState<Tab>, inline styles,
// single accent constant (imported from ./constants, not declared here
// — see that file's own header comment for why), Lucide icons,
// delegates to per-tab components.
//
// Sections are routes (/warehousing/:section) with navigation in the sidebar
// (see navigation/moduleSections.ts and components/shell/SectionedModulePage).
// The client portal (/warehousing/portal/*) is routed separately, outside the shell.
import WhseDashboard from "./WhseDashboard"
import WhseClientsTab from "./WhseClientsTab"
import WhseLocationsTab from "./WhseLocationsTab"
import WhseApprovalsTab from "./WhseApprovalsTab"
import WhseTasksTab from "./WhseTasksTab"
import WhseInspectionTab from "./WhseInspectionTab"
import WhseReturnsTab from "./WhseReturnsTab"
import WhseLotsTab from "./WhseLotsTab"
import WhseDispatchTab from "./WhseDispatchTab"
import WhseScanTab from "./WhseScanTab"
import WhseReplenTab from "./WhseReplenTab"
import WhseDockTab from "./WhseDockTab"
import WhseWebhooksTab from "./WhseWebhooksTab"
import WhseSlottingTab from "./WhseSlottingTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { WAREHOUSING_SECTIONS } from "../../navigation/moduleSections"

export default function WarehousingPage() {
  return (
    <SectionedModulePage config={WAREHOUSING_SECTIONS}
      subtitle="3PL / public warehousing operations"
      render={id => {
        switch (id) {
          case "dashboard": return <WhseDashboard />
          case "clients":   return <WhseClientsTab />
          case "locations": return <WhseLocationsTab />
          case "approvals": return <WhseApprovalsTab />
          case "scan":       return <WhseScanTab />
          case "tasks":      return <WhseTasksTab />
          case "replenishment": return <WhseReplenTab />
          case "inspection": return <WhseInspectionTab />
          case "returns":    return <WhseReturnsTab />
          case "lots":       return <WhseLotsTab />
          case "dispatch":   return <WhseDispatchTab />
          case "dock":       return <WhseDockTab />
          case "webhooks":   return <WhseWebhooksTab />
          case "slotting":   return <WhseSlottingTab />
          default:          return null
        }
      }} />
  )
}
