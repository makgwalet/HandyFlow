// src/pages/fuel/FuelPage.tsx
//
// Sections are routes (/fuel/:section) with navigation in the sidebar (see
// navigation/moduleSections.ts). "Cost & Margin" requires FUEL_MARGIN_READ:
// the sidebar hides it and SectionedModulePage redirects away from it without
// the permission, mirroring (not replacing) the server-side check.
import FuelDashboard  from "./FuelDashboard"
import TanksTab       from "./TanksTab"
import ReceiptsTab    from "./ReceiptsTab"
import DispatchesTab  from "./DispatchesTab"
import DeliveriesTab  from "./DeliveriesTab"
import SuppliersTab   from "./SuppliersTab"
import MarginTab      from "./MarginTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { FUEL_SECTIONS } from "../../navigation/moduleSections"

export function FuelPage() {
  return (
    <SectionedModulePage config={FUEL_SECTIONS} render={(id, goTo) => {
      switch (id) {
        case "dashboard":  return <FuelDashboard onNavigate={goTo} />
        case "tanks":      return <TanksTab />
        case "receipts":   return <ReceiptsTab />
        case "dispatches": return <DispatchesTab />
        case "deliveries": return <DeliveriesTab />
        case "suppliers":  return <SuppliersTab />
        case "margin":     return <MarginTab />
        default:           return null
      }
    }} />
  )
}
