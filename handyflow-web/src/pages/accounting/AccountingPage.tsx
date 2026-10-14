// src/pages/accounting/AccountingPage.tsx
//
// Sections are routes (/accounting/:section) with navigation in the sidebar
// (see navigation/moduleSections.ts and components/shell/SectionedModulePage).
import ChartOfAccountsTab from "./ChartOfAccountsTab"
import JournalEntriesTab from "./JournalEntriesTab"
import BankAccountsTab from "./BankAccountsTab"
import ReportsTab from "./ReportsTab"
import VatReturnsTab from "./VatReturnsTab"
import AgingTab from "./AgingTab"
import DashboardTab from "./AccountingDashboard"
import PostingProblemsTab from "./PostingProblemsTab"
import PeriodLockTab from "./PeriodLockTab"
import BankReconciliationTab from "./BankReconciliationTab"
import MonthEndCloseTab from "./MonthEndCloseTab"
import CashFlowTab from "./CashFlowTab"
import RecurringJournalsTab from "./RecurringJournalsTab"
import BudgetsTab from "./BudgetsTab"
import CashForecastTab from "./CashForecastTab"
import CostCentresTab from "./CostCentresTab"
import ByCostCentreTab from "./ByCostCentreTab"
import FixedAssetsTab from "./FixedAssetsTab"
import DepreciationTab from "./DepreciationTab"
import GroupTab from "./GroupTab"
import ConsolidationTab from "./ConsolidationTab"
import ProfitabilityTab from "./ProfitabilityTab"
import TagRulesTab from "./TagRulesTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { ACCOUNTING_SECTIONS } from "../../navigation/moduleSections"

export function AccountingPage() {
  return (
    <SectionedModulePage config={ACCOUNTING_SECTIONS} render={id => {
      switch (id) {
        case "dashboard": return <DashboardTab />
        case "accounts":  return <ChartOfAccountsTab />
        case "journal":   return <JournalEntriesTab />
        case "bank":      return <BankAccountsTab />
        case "reconcile": return <BankReconciliationTab />
        case "posting":   return <PostingProblemsTab />
        case "lock":      return <PeriodLockTab />
        case "recurring": return <RecurringJournalsTab />
        case "close":     return <MonthEndCloseTab />
        case "cashflow":  return <CashFlowTab />
        case "budgets":   return <BudgetsTab />
        case "forecast":  return <CashForecastTab />
        case "centres":   return <CostCentresTab />
        case "bycentre":  return <ByCostCentreTab />
        case "assets":    return <FixedAssetsTab />
        case "depreciation": return <DepreciationTab />
        case "group":     return <GroupTab />
        case "consolidation": return <ConsolidationTab />
        case "profitability": return <ProfitabilityTab />
        case "tagrules":  return <TagRulesTab />
        case "reports":   return <ReportsTab />
        case "vat":       return <VatReturnsTab />
        case "aging":     return <AgingTab />
        default:          return null
      }
    }} />
  )
}
