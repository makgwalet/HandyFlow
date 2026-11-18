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
import { PolicyTab }          from "./PolicyTab"
import { ContractsTab }       from "./ContractsTab"
import { ReturnsTab }         from "./ReturnsTab"
import { QualityTab }         from "./QualityTab"
import { ReplenishmentTab }   from "./ReplenishmentTab"
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
          case "contracts":       return <ContractsTab />
          case "returns":         return <ReturnsTab />
          case "quality":         return <QualityTab />
          case "replenishment":   return <ReplenishmentTab />
          case "policy":          return <PolicyTab />
          case "analytics":       return <AnalyticsTab />
          default:                return null
        }
      }} />
  )
}
