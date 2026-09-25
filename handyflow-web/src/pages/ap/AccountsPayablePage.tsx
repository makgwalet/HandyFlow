// src/pages/ap/AccountsPayablePage.tsx
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  FileText, CreditCard, AlertTriangle, CheckCircle,
  Clock, TrendingDown, BarChart2, Calendar, Users, RefreshCw, Landmark,
} from "lucide-react"
import { BillsTab }   from "./BillsTab"
import { BatchesTab } from "./BatchesTab"
import AgingTab from "./AgingTab"
import { RecurringBillsTab } from "./RecurringBillsTab"
import { SupplierBankingTab } from "./SupplierBankingTab"

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
  const [tab, setTab] = useState<"bills" | "batches" | "aging" | "recurring" | "suppliers">("bills")

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

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 22, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: "var(--hf-primary)", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <CreditCard size={18} color="#fff" />
            </div>
            <h1 style={{ fontSize: 24, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>Accounts Payable</h1>
          </div>
          <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0, paddingLeft: 46 }}>
            Supplier bills · EFT batch payments · Accounting integration
          </p>
        </div>
      </div>

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

      {/* Tabs */}
      <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14 }}>
        <div style={{ display: "flex", borderBottom: "1px solid var(--hf-border)", padding: "0 24px" }}>
          {([
            { key: "bills",     label: "Bills",       icon: <FileText size={14} /> },
            { key: "batches",   label: "EFT Batches", icon: <CreditCard size={14} /> },
            { key: "aging",     label: "Aging",       icon: <Users size={14} /> },
            { key: "recurring", label: "Recurring",   icon: <RefreshCw size={14} /> },
            { key: "suppliers", label: "Suppliers",   icon: <Landmark size={14} /> },
          ] as const).map(t => (
            <button key={t.key} onClick={() => setTab(t.key)}
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "14px 18px", fontSize: 13, fontWeight: 600, cursor: "pointer", border: "none", background: "none", color: tab === t.key ? "var(--hf-primary-text)" : "var(--hf-text-faint)", borderBottom: `2px solid ${tab === t.key ? "#1B3A6B" : "transparent"}`, marginBottom: -1 }}>
              {t.icon}{t.label}
            </button>
          ))}
        </div>
        <div style={{ padding: 24 }}>
          {tab === "bills"     && <BillsTab onRefreshSummary={() => qc.invalidateQueries({ queryKey: ["ap-summary"] })} />}
          {tab === "batches"   && <BatchesTab onRefreshSummary={() => qc.invalidateQueries({ queryKey: ["ap-summary"] })} />}
          {tab === "aging"     && <AgingTab />}
          {tab === "recurring" && <RecurringBillsTab onRefreshSummary={() => qc.invalidateQueries({ queryKey: ["ap-summary"] })} />}
          {tab === "suppliers" && <SupplierBankingTab />}
        </div>
      </div>
    </div>
  )
}
