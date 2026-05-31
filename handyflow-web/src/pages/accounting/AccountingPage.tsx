// src/pages/accounting/AccountingPage.tsx
import { useState } from "react"
import {
  BookOpen, GitBranch, Landmark, BarChart2,
  FileText, TrendingUp, Users, LayoutDashboard,
} from "lucide-react"
import ChartOfAccountsTab from "./ChartOfAccountsTab"
import JournalEntriesTab from "./JournalEntriesTab"
import BankAccountsTab from "./BankAccountsTab"
import ReportsTab from "./ReportsTab"
import VatReturnsTab from "./VatReturnsTab"
import AgingTab from "./AgingTab"
import AccountingDashboard from "./AccountingDashboard"

type Tab = "dashboard" | "accounts" | "journal" | "bank" | "reports" | "vat" | "aging"

const tabs = [
  { id: "dashboard" as Tab, label: "Dashboard",          icon: LayoutDashboard },
  { id: "accounts"  as Tab, label: "Chart of Accounts",  icon: BookOpen },
  { id: "journal"   as Tab, label: "Journal Entries",    icon: GitBranch },
  { id: "bank"      as Tab, label: "Bank Accounts",      icon: Landmark },
  { id: "reports"   as Tab, label: "Reports",            icon: BarChart2 },
  { id: "vat"       as Tab, label: "VAT Returns",        icon: FileText },
  { id: "aging"     as Tab, label: "AR / AP Aging",      icon: Users },
]

export function AccountingPage() {
  const [activeTab, setActiveTab] = useState<Tab>("dashboard")

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: "0 0 4px" }}>
          Accounting & Finance
        </h1>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>
          Double-entry bookkeeping, VAT, bank accounts and financial reports
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        {/* Tab bar */}
        <div style={{
          display: "flex", gap: 2, flexWrap: "wrap",
          borderBottom: "1px solid #E2E8F0",
          marginBottom: 28, paddingBottom: 0,
          overflowX: "auto",
        }}>
          {tabs.map(tab => {
            const Icon = tab.icon
            const active = activeTab === tab.id
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 7, whiteSpace: "nowrap",
                  padding: "10px 16px", background: "none", border: "none",
                  borderBottom: active ? "2px solid #0D9488" : "2px solid transparent",
                  color: active ? "#0D9488" : "#64748B",
                  fontWeight: active ? 600 : 400,
                  fontSize: 13, cursor: "pointer",
                  marginBottom: -1, transition: "all 0.15s",
                }}
              >
                <Icon size={14} />{tab.label}
              </button>
            )
          })}
        </div>

        {activeTab === "dashboard" && <AccountingDashboard onNavigate={setActiveTab} />}
        {activeTab === "accounts"  && <ChartOfAccountsTab />}
        {activeTab === "journal"   && <JournalEntriesTab />}
        {activeTab === "bank"      && <BankAccountsTab />}
        {activeTab === "reports"   && <ReportsTab />}
        {activeTab === "vat"       && <VatReturnsTab />}
        {activeTab === "aging"     && <AgingTab />}
      </div>
    </div>
  )
}