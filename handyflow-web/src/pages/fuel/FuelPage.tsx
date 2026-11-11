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
import CustomersTab from "./CustomersTab"
import OrdersTab from "./OrdersTab"
import InvoicingTab from "./InvoicingTab"
import PlanningTab from "./PlanningTab"
import DriverTab from "./DriverTab"
import StockTab       from "./StockTab"
import PricingTab from "./PricingTab"
import ComplianceTab from "./ComplianceTab"
import PurchaseOrdersTab from "./PurchaseOrdersTab"
import OverviewTab from "./OverviewTab"
import ForecourtTab from "./ForecourtTab"
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
        case "stock":      return <StockTab />
        case "customers":  return <CustomersTab />
        case "orders":     return <OrdersTab />
        case "invoicing":  return <InvoicingTab />
        case "planning":   return <PlanningTab />
        case "driver":     return <DriverTab />
        case "pricing":    return <PricingTab />
        case "compliance": return <ComplianceTab />
        case "purchase-orders": return <PurchaseOrdersTab />
        case "forecourt":  return <ForecourtTab />
        case "overview":   return <OverviewTab onNavigate={goTo} />
        case "margin":     return <MarginTab />
        default:           return null
      }
    }} />
  )
}
