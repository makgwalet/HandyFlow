// src/pages/accounting/AccountingDashboard.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  TrendingUp, TrendingDown, Landmark, FileText,
  AlertCircle, ArrowRight, DollarSign,
} from "lucide-react"

interface BankAccount { id: string; accountName: string; bankName: string; currentBalance: number; currency: string }
interface Invoice { id: string; status: string; total: number; dueDate: string | null; customerId: string }

const fmtR = (n: number) => `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

export default function AccountingDashboard({ onNavigate }: { onNavigate: (tab: any) => void }) {

  const { data: bankAccounts = [] } = useQuery<BankAccount[]>({
    queryKey: ["bank-accounts"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/accounting/bank-accounts")
      return (res.data?.data ?? res.data) as BankAccount[]
    },
  })

  const { data: plReport } = useQuery({
    queryKey: ["pl-ytd"],
    queryFn: async () => {
      const now = new Date()
      const from = `${now.getFullYear()}-01-01`
      const to   = now.toISOString().split("T")[0]
      const res = await apiClient.get(`/api/v1/accounting/reports/profit-and-loss?from=${from}&to=${to}`)
      return res.data?.data ?? res.data
    },
  })

  const { data: invoicesData } = useQuery({
    queryKey: ["invoices-accounting"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/invoicing/invoices?size=200")
      const payload = res.data?.data ?? res.data
      return (payload.content ?? payload) as Invoice[]
    },
  })

  const invoices = invoicesData ?? []
  const totalCash        = bankAccounts.reduce((s, a) => s + (a.currentBalance ?? 0), 0)
  const outstanding      = invoices.filter(i => ["ISSUED", "PARTIALLY_PAID", "OVERDUE"].includes(i.status)).reduce((s, i) => s + i.total, 0)
  const overdueAmount    = invoices.filter(i => i.status === "OVERDUE").reduce((s, i) => s + i.total, 0)
  const overdueCount     = invoices.filter(i => i.status === "OVERDUE").length
  const netProfit        = plReport?.netResult ?? 0
  const income           = plReport?.sections?.find((s: any) => s.title === "Income")?.total ?? 0
  const expenses         = plReport?.sections?.find((s: any) => s.title === "Expenses")?.total ?? 0

  const kpis = [
    {
      label: "Total Cash Position",
      value: fmtR(totalCash),
      icon: Landmark,
      color: totalCash >= 0 ? "#166534" : "#DC2626",
      bg: totalCash >= 0 ? "#F0FDF4" : "#FEF2F2",
      tab: "bank",
    },
    {
      label: "YTD Revenue",
      value: fmtR(income),
      icon: TrendingUp,
      color: "#1D4ED8",
      bg: "#EFF6FF",
      tab: "reports",
    },
    {
      label: "YTD Expenses",
      value: fmtR(expenses),
      icon: TrendingDown,
      color: "#D97706",
      bg: "#FFFBEB",
      tab: "reports",
    },
    {
      label: "Net Profit (YTD)",
      value: fmtR(netProfit),
      icon: DollarSign,
      color: netProfit >= 0 ? "#166534" : "#DC2626",
      bg: netProfit >= 0 ? "#F0FDF4" : "#FEF2F2",
      tab: "reports",
    },
    {
      label: "Outstanding Invoices",
      value: fmtR(outstanding),
      icon: FileText,
      color: "#7C3AED",
      bg: "#F5F3FF",
      tab: "aging",
    },
    {
      label: "Overdue",
      value: fmtR(overdueAmount),
      sub: overdueCount > 0 ? `${overdueCount} invoice${overdueCount !== 1 ? "s" : ""} overdue` : "All current",
      icon: AlertCircle,
      color: overdueCount > 0 ? "#DC2626" : "#166534",
      bg: overdueCount > 0 ? "#FEF2F2" : "#F0FDF4",
      tab: "aging",
    },
  ]

  return (
    <div>
      {/* KPI grid */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 14, marginBottom: 28 }}>
        {kpis.map(k => (
          <div
            key={k.label}
            onClick={() => onNavigate(k.tab)}
            style={{ background: k.bg, border: `1px solid ${k.bg}`, borderRadius: 12, padding: "18px 20px", cursor: "pointer", transition: "box-shadow 0.15s" }}
            onMouseEnter={e => (e.currentTarget.style.boxShadow = "0 4px 16px rgba(0,0,0,0.08)")}
            onMouseLeave={e => (e.currentTarget.style.boxShadow = "none")}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 10 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: k.color, textTransform: "uppercase", letterSpacing: "0.05em" }}>{k.label}</div>
              <k.icon size={16} color={k.color} />
            </div>
            <div style={{ fontSize: 22, fontWeight: 800, color: k.color }}>{k.value}</div>
            {k.sub && <div style={{ fontSize: 11, color: k.color, marginTop: 4, opacity: 0.8 }}>{k.sub}</div>}
          </div>
        ))}
      </div>

      {/* Bank accounts strip */}
      {bankAccounts.length > 0 && (
        <div style={{ marginBottom: 24 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>Bank Accounts</span>
            <button onClick={() => onNavigate("bank")}
              style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#0D9488", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
              View all <ArrowRight size={13} />
            </button>
          </div>
          <div style={{ display: "flex", gap: 12, overflowX: "auto" }}>
            {bankAccounts.map(acc => (
              <div key={acc.id} style={{ minWidth: 220, background: "#1B3A6B", borderRadius: 10, padding: "14px 16px", flexShrink: 0 }}>
                <div style={{ fontSize: 11, color: "rgba(255,255,255,0.6)", marginBottom: 4 }}>{acc.bankName} · {acc.currency}</div>
                <div style={{ fontSize: 14, fontWeight: 600, color: "#fff", marginBottom: 8 }}>{acc.accountName}</div>
                <div style={{ fontSize: 20, fontWeight: 700, color: acc.currentBalance >= 0 ? "#4ADE80" : "#F87171" }}>
                  {fmtR(acc.currentBalance)}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Quick actions */}
      <div>
        <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 12 }}>Quick Actions</div>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
          {[
            { label: "New Journal Entry", tab: "journal", color: "#1B3A6B" },
            { label: "Run P&L Report",    tab: "reports", color: "#0D9488" },
            { label: "VAT Returns",       tab: "vat",     color: "#7C3AED" },
            { label: "AR Aging Report",   tab: "aging",   color: "#D97706" },
          ].map(a => (
            <button key={a.label} onClick={() => onNavigate(a.tab)}
              style={{ padding: "9px 18px", background: a.color, color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
              {a.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}