// src/pages/ap/AccountsPayablePage.tsx
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { FileText, CreditCard, AlertTriangle, CheckCircle, Clock, TrendingDown, Calendar } from "lucide-react"
import { BillsTab }   from "./BillsTab"
import { BatchesTab } from "./BatchesTab"
import AgingTab from "./AgingTab"
import { RecurringBillsTab } from "./RecurringBillsTab"
import { SupplierBankingTab } from "./SupplierBankingTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { AP_SECTIONS } from "../../navigation/moduleSections"

interface Summary {
  totalOutstanding: number; overdueAmount: number
  dueThisWeek: number; dueThisMonth: number
  draftCount: number; approvedCount: number
  overdueCount: number; pendingBatches: number
}

const fmtR = (n: any) =>
  n != null ? `R\u00A0${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "R\u00A00.00"

export function AccountsPayablePage() {
  const qc = useQueryClient()
  const refreshSummary = () => qc.invalidateQueries({ queryKey: ["ap-summary"] })

  const { data: summary } = useQuery<Summary>({
    queryKey: ["ap-summary"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/ap/summary")
      return r.data?.data ?? r.data
    },
    refetchInterval: 30_000,
  })

  const kpis = [
    { label: "Total outstanding",  value: fmtR(summary?.totalOutstanding),  color: "var(--hf-primary-text)", bg: "var(--hf-indigo-soft)", icon: <TrendingDown size={16} /> },
    { label: "Overdue",            value: fmtR(summary?.overdueAmount),      color: summary?.overdueAmount ? "var(--hf-danger-text)" : "var(--hf-text-faint)", bg: summary?.overdueAmount ? "var(--hf-danger-soft)" : "var(--hf-surface-muted)", icon: <AlertTriangle size={16} /> },
    { label: "Due this week",      value: fmtR(summary?.dueThisWeek),        color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", icon: <Clock size={16} /> },
    { label: "Due this month",     value: fmtR(summary?.dueThisMonth),       color: "var(--hf-accent-text)", bg: "var(--hf-accent-soft)", icon: <Calendar size={16} /> },
    { label: "Draft bills",        value: String(summary?.draftCount ?? 0),  color: "var(--hf-text-muted)", bg: "var(--hf-surface-muted)", icon: <FileText size={16} /> },
    { label: "Approved bills",     value: String(summary?.approvedCount ?? 0), color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", icon: <CheckCircle size={16} /> },
    { label: "Overdue bills",      value: String(summary?.overdueCount ?? 0),  color: summary?.overdueCount ? "var(--hf-danger-text)" : "var(--hf-text-faint)", bg: summary?.overdueCount ? "var(--hf-danger-soft)" : "var(--hf-surface-muted)", icon: <AlertTriangle size={16} /> },
    { label: "Pending batches",    value: String(summary?.pendingBatches ?? 0), color: "var(--hf-violet-text)", bg: "var(--hf-violet-soft)", icon: <CreditCard size={16} /> },
  ]

  // Summary strip shown above every section.
  const banner = (
    <>
    {/* KPI strip */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12, marginBottom: 22 }}>
            {kpis.slice(0, 4).map(k => (
              <div key={k.label} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "14px 18px", display: "flex", alignItems: "center", gap: 12 }}>
                <div style={{ width: 36, height: 36, borderRadius: 9, background: k.bg, display: "flex", alignItems: "center", justifyContent: "center", color: k.color, flexShrink: 0 }}>{k.icon}</div>
                <div>
                  <div style={{ fontSize: 18, fontWeight: 800, color: k.color, letterSpacing: "-0.02em" }}>{k.value}</div>
                  <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 1 }}>{k.label}</div>
                </div>
              </div>
            ))}
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12, marginBottom: 22 }}>
            {kpis.slice(4).map(k => (
              <div key={k.label} style={{ background: k.bg, border: "1px solid transparent", borderRadius: 12, padding: "12px 16px", display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ color: k.color }}>{k.icon}</div>
                <div>
                  <div style={{ fontSize: 20, fontWeight: 800, color: k.color }}>{k.value}</div>
                  <div style={{ fontSize: 10, color: k.color, opacity: 0.7 }}>{k.label}</div>
                </div>
              </div>
            ))}
          </div>
    </>
  )

  // Sections are routes (/ap/:section); /ap opens on Bills.
  return (
    <SectionedModulePage config={AP_SECTIONS} banner={banner}
      subtitle="Supplier bills · EFT batch payments · Accounting integration"
      render={id => {
        switch (id) {
          case "bills":     return <BillsTab onRefreshSummary={refreshSummary} />
          case "batches":   return <BatchesTab onRefreshSummary={refreshSummary} />
          case "aging":     return <AgingTab />
          case "recurring": return <RecurringBillsTab onRefreshSummary={refreshSummary} />
          case "suppliers": return <SupplierBankingTab />
          default:          return null
        }
      }} />
  )
}
