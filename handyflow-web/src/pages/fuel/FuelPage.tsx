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
import TruckStopTab from "./TruckStopTab"
import EnquiriesTab from "./EnquiriesTab"
import SupplierInvoicesTab from "./SupplierInvoicesTab"
import CostToServeTab from "./CostToServeTab"
import LandedCostTab from "./LandedCostTab"
import ReturnedFuelTab from "./ReturnedFuelTab"
import ContractsTab from "./ContractsTab"
import StaysTab from "./StaysTab"
import GroupReportTab from "./GroupReportTab"
import AutomationTab from "./AutomationTab"
import SettingsTab from "./SettingsTab"
import VariancesTab from "./VariancesTab"
import SetupTab from "./SetupTab"
import AccessLimitsTab from "./AccessLimitsTab"
import ImportsTab from "./ImportsTab"
import GaugeImportsTab from "./GaugeImportsTab"
import LegalEntitiesTab from "./LegalEntitiesTab"
import EligibilityTab from "./EligibilityTab"
import { useEffect } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useFuelNavStore } from "../../store/fuelNav.store"
import type { Profile } from "./setupOps"
import { Link } from "react-router-dom"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { FUEL_SECTIONS } from "../../navigation/moduleSections"

/** Loads which divisions are active so the sidebar can hide the rest. A failed load leaves everything visible (the server still enforces). */
function useFuelDivisions() {
  const setDivisions = useFuelNavStore(s => s.setDivisions)
  const q = useQuery<Profile>({ queryKey: ["fuel-profile"], queryFn: async () => { const r: any = await apiClient.get("/api/v1/fuel/operating-profile"); return r.data?.data ?? r.data }, retry: false })
  useEffect(() => { if (q.data) setDivisions(q.data.activeDivisions) }, [q.data, setDivisions])
  useEffect(() => () => setDivisions(null), [setDivisions])
  return q.data
}

function SetupBanner() {
  return (
    <div role="status" style={{ padding: "10px 14px", marginBottom: 12, borderRadius: 8, background: "var(--hf-warning-bg)", color: "var(--hf-warning-text)", fontSize: 13 }}>
      Fuel setup is not finished. <Link to="/fuel/setup">Choose your divisions</Link> to unlock the division screens.
    </div>
  )
}

export function FuelPage() {
  const profile = useFuelDivisions()
  const needsSetup = profile != null && profile.status !== "ACTIVE"
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
        case "eligibility": return <EligibilityTab />
        case "driver":     return <DriverTab />
        case "pricing":    return <PricingTab />
        case "compliance": return <ComplianceTab />
        case "purchase-orders": return <PurchaseOrdersTab />
        case "supplier-invoices": return <SupplierInvoicesTab />
        case "forecourt":  return <ForecourtTab />
        case "truck-stop": return <TruckStopTab />
        case "enquiries":  return <EnquiriesTab />
        case "overview":   return <OverviewTab onNavigate={goTo} />
        case "margin":     return <MarginTab />
        case "cost-to-serve": return <CostToServeTab onNavigate={goTo} />
        case "overnight-stays": return <StaysTab />
        case "contracts": return <ContractsTab />
        case "returned-fuel": return <ReturnedFuelTab />
        case "landed-cost": return <LandedCostTab />
        case "group-report": return <GroupReportTab />
        case "automation": return <AutomationTab />
        case "settings":   return <SettingsTab />
        case "variances": return <VariancesTab />
        case "setup":      return <SetupTab />
        case "access-limits": return <AccessLimitsTab />
        case "imports": return <ImportsTab />
        case "gauge-readings": return <GaugeImportsTab />
        case "legal-entities": return <LegalEntitiesTab />
        default:           return null
      }
    }} banner={needsSetup ? <SetupBanner /> : undefined} />
  )
}
