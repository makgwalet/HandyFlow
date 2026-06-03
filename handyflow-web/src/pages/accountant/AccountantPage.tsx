// src/pages/accountant/AccountantPage.tsx
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Briefcase, Users, Calendar, Clock, FileText,
  BarChart2, AlertTriangle, CheckCircle, TrendingUp,
} from "lucide-react"
import AccountantDashboard  from "./AccountantDashboard"
import ClientsTab           from "./ClientsTab"
import DeadlinesTab         from "./DeadlinesTab"
import TimeTab              from "./TimeTab"
import BillingTab           from "./BillingTab"

type Tab = "dashboard" | "clients" | "deadlines" | "time" | "billing"

const TABS = [
  { id: "dashboard" as Tab, label: "Dashboard",  icon: BarChart2  },
  { id: "clients"   as Tab, label: "Clients",    icon: Users      },
  { id: "deadlines" as Tab, label: "Compliance", icon: Calendar   },
  { id: "time"      as Tab, label: "Time",       icon: Clock      },
  { id: "billing"   as Tab, label: "Billing",    icon: FileText   },
]

export function AccountantPage() {
  const [tab, setTab] = useState<Tab>("dashboard")

  const { data: dashboard } = useQuery<any>({
    queryKey: ["accountant-dashboard"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/accountant/dashboard")
      return r.data?.data ?? r.data
    },
  })

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: "#1B3A6B", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Briefcase size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Accountant</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Client portfolio · SARS compliance · Time tracking · Billing
        </p>
      </div>

      {/* KPI strip */}
      {dashboard && (
        <div style={{ display: "flex", gap: 12, marginBottom: 22, flexWrap: "wrap" }}>
          {[
            { label: "Active clients",       value: dashboard.totalClients,              color: "#1B3A6B", bg: "#EEF2FF" },
            { label: "Overdue filings",       value: dashboard.overdueFilings,           color: dashboard.overdueFilings > 0 ? "#DC2626" : "#166534", bg: dashboard.overdueFilings > 0 ? "#FEF2F2" : "#F0FDF4" },
            { label: "Due next 30 days",      value: dashboard.pendingFilingsNext30Days, color: "#D97706", bg: "#FFFBEB" },
            { label: "Unbilled WIP",          value: `R ${Number(dashboard.totalWip ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`, color: "#0D9488", bg: "#F0FDF9" },
            { label: "Outstanding invoices",  value: `R ${Number(dashboard.totalOutstandingInvoices ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`, color: "#1D4ED8", bg: "#EFF6FF" },
          ].map(k => (
            <div key={k.label} style={{ background: k.bg, borderRadius: 10, padding: "12px 18px", minWidth: 140 }}>
              <div style={{ fontSize: 20, fontWeight: 800, color: k.color }}>{k.value}</div>
              <div style={{ fontSize: 11, color: k.color, marginTop: 2, opacity: 0.8 }}>{k.label}</div>
            </div>
          ))}
        </div>
      )}

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 28, overflowX: "auto" }}>
          {TABS.map(t => {
            const Icon   = t.icon
            const active = tab === t.id
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 6, padding: "10px 18px",
                  background: "none", border: "none", whiteSpace: "nowrap" as const,
                  borderBottom: active ? "2px solid #1B3A6B" : "2px solid transparent",
                  color: active ? "#1B3A6B" : "#64748B",
                  fontWeight: active ? 600 : 400, fontSize: 14, cursor: "pointer", marginBottom: -1,
                }}>
                <Icon size={15} />{t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard" && <AccountantDashboard onNavigate={setTab} />}
        {tab === "clients"   && <ClientsTab />}
        {tab === "deadlines" && <DeadlinesTab />}
        {tab === "time"      && <TimeTab />}
        {tab === "billing"   && <BillingTab />}
      </div>
    </div>
  )
}
