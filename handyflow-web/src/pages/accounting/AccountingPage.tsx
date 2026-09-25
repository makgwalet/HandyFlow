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
        case "reports":   return <ReportsTab />
        case "vat":       return <VatReturnsTab />
        case "aging":     return <AgingTab />
        default:          return null
      }
    }} />
  )
}
