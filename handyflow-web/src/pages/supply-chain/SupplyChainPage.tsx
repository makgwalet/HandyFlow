// src/pages/supply-chain/SupplyChainPage.tsx
//
// Sections are routes (/supply-chain/:section) with navigation in the
// sidebar (see navigation/moduleSections.ts and
// components/shell/SectionedModulePage).
import { ScmDashboard }       from "./ScmDashboard"
import { SuppliersTab }       from "./SuppliersTab"
import { PurchaseOrdersTab }  from "./PurchaseOrdersTab"
import { InventoryTab }       from "./InventoryTab"
import { InvoicesTab }        from "./InvoicesTab"
import { RequisitionsTab }    from "./RequisitionsTab"
import { RfqTab }             from "./RfqTab"
import { BudgetsTab }         from "./BudgetsTab"
import { AnalyticsTab }       from "./AnalyticsTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { SUPPLY_CHAIN_SECTIONS } from "../../navigation/moduleSections"

export function SupplyChainPage() {
  return (
    <SectionedModulePage config={SUPPLY_CHAIN_SECTIONS}
      subtitle="Suppliers · Purchase orders · Inventory · Supplier invoices"
      render={(id, goTo) => {
        switch (id) {
          case "dashboard":       return <ScmDashboard onNav={goTo} />
          case "suppliers":       return <SuppliersTab />
          case "purchase-orders": return <PurchaseOrdersTab />
          case "inventory":       return <InventoryTab />
          case "invoices":        return <InvoicesTab />
          case "requisitions":    return <RequisitionsTab />
          case "rfqs":            return <RfqTab />
          case "budgets":         return <BudgetsTab />
          case "analytics":       return <AnalyticsTab />
          default:                return null
        }
      }} />
  )
}
