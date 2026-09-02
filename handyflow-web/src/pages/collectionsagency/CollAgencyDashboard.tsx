// src/pages/collectionsagency/CollAgencyDashboard.tsx
//
// DESIGN NOTE: this module has no dedicated dashboard/summary endpoint
// (unlike some other modules) — only per-client sub-resources
// (debtor-accounts, trust-transactions, etc. all live under
// /clients/{clientId}/...). Pulling debtor-account-level detail for
// EVERY client here would mean N+1 requests (one per client), which
// doesn't scale as the client list grows. So this dashboard deliberately
// stays to what's cheap: the two real "...all"/unpaginated
// list-for-dashboards endpoints (clients, collectors) plus values already
// carried on ClientResponse itself (trustBalance). Debtor-account-level
// detail (aging, per-status counts) lives one click away in the Clients
// tab's own client detail view, scoped to one client at a time.
import { useQuery } from "@tanstack/react-query"
import { Users, Wallet, UserCog, AlertTriangle } from "lucide-react"
import { apiClient } from "../../api/client"
import { CA_ACCENT } from "./constants"

interface ClientResponse {
  id: string; tradingName: string; registrationNumber: string | null; commissionRatePct: number
  contactName: string | null; contactEmail: string | null; contactPhone: string | null; address: string | null
  trustBalance: number; onboardedAt: string; status: string; notes: string | null
}
interface CollectorResponse {
  id: string; userId: string | null; fullName: string; registrationNumber: string | null
  registrationExpiryDate: string | null; email: string | null; phone: string | null; active: boolean
}

const fmtMoney = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n ?? 0)

function StatCard({ label, value, sub, icon: Icon, tone }: { label: string; value: string | number; sub?: string; icon: React.ElementType; tone: string }) {
  return (
    <div style={{ background: "#fff", border: "1px solid #E8EDF5", borderRadius: 14, padding: 18, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
      <div>
        <p style={{ fontSize: 11, color: "#94A3B8", margin: "0 0 6px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.04em" }}>{label}</p>
        <p style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: "0 0 2px" }}>{value}</p>
        {sub && <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>{sub}</p>}
      </div>
      <div style={{ width: 42, height: 42, borderRadius: 12, background: `${tone}1A`, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
        <Icon size={20} color={tone} />
      </div>
    </div>
  )
}

export default function CollAgencyDashboard({ onNavigate }: { onNavigate: (tab: "clients" | "collectors") => void }) {
  const { data: clients = [], isLoading: clientsLoading } = useQuery<ClientResponse[]>({
    queryKey: ["ca-clients-all"],
    queryFn: async () => (await apiClient.get("/api/v1/collections-agency/clients/all")).data,
  })
  const { data: collectors = [], isLoading: collectorsLoading } = useQuery<CollectorResponse[]>({
    queryKey: ["ca-collectors"],
    queryFn: async () => (await apiClient.get("/api/v1/collections-agency/collectors")).data,
  })

  const activeClients   = clients.filter(c => c.status === "ACTIVE")
  const totalTrustHeld  = activeClients.reduce((s, c) => s + (c.trustBalance || 0), 0)
  const activeCollectors = collectors.filter(c => c.active)

  const today = new Date()
  const in30  = new Date(today.getTime() + 30 * 86_400_000)
  const expiringCollectors = activeCollectors.filter(c => {
    if (!c.registrationExpiryDate) return false
    const exp = new Date(c.registrationExpiryDate)
    return exp <= in30
  })

  const clientsWithTrust = [...activeClients].sort((a, b) => (b.trustBalance || 0) - (a.trustBalance || 0)).slice(0, 6)

  if (clientsLoading || collectorsLoading) {
    return <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
  }

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 28 }}>
        <StatCard label="Active clients"     value={activeClients.length}          icon={Users}         tone={CA_ACCENT} />
        <StatCard label="Trust held"         value={fmtMoney(totalTrustHeld)}      icon={Wallet}        tone="#059669" sub="Across all active clients" />
        <StatCard label="Registered collectors" value={activeCollectors.length}    icon={UserCog}       tone="#0369A1" />
        <StatCard label="Registrations expiring" value={expiringCollectors.length} icon={AlertTriangle} tone={expiringCollectors.length > 0 ? "#DC2626" : "#94A3B8"} sub="Within 30 days" />
      </div>

      {expiringCollectors.length > 0 && (
        <div style={{ background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 12, padding: "14px 18px", marginBottom: 24, display: "flex", alignItems: "center", gap: 12 }}>
          <AlertTriangle size={18} color="#DC2626" />
          <div>
            <p style={{ fontSize: 13, fontWeight: 700, color: "#991B1B", margin: "0 0 2px" }}>
              {expiringCollectors.length} collector registration{expiringCollectors.length === 1 ? "" : "s"} expiring within 30 days
            </p>
            <p style={{ fontSize: 12, color: "#B91C1C", margin: 0 }}>
              Collecting while unregistered is a criminal offence under the Debt Collectors Act — renew before expiry.
            </p>
          </div>
          <button onClick={() => onNavigate("collectors")}
            style={{ marginLeft: "auto", background: "#DC2626", color: "#fff", border: "none", borderRadius: 8, padding: "6px 14px", fontSize: 12, fontWeight: 700, cursor: "pointer", whiteSpace: "nowrap" }}>
            Review collectors
          </button>
        </div>
      )}

      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
        <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>Largest trust balances</p>
        <button onClick={() => onNavigate("clients")}
          style={{ background: "none", border: "1px solid #E2E8F0", cursor: "pointer", fontSize: 12, color: CA_ACCENT, fontWeight: 600, padding: "6px 12px", borderRadius: 8 }}>
          All clients →
        </button>
      </div>

      {clientsWithTrust.length === 0 ? (
        <p style={{ fontSize: 13, color: "#94A3B8" }}>No active clients yet. Onboard a creditor client to get started.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {clientsWithTrust.map((c, i) => (
            <div key={c.id} onClick={() => onNavigate("clients")}
              style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9", cursor: "pointer" }}>
              <div>
                <p style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", margin: "0 0 2px" }}>{c.tradingName}</p>
                <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>{c.commissionRatePct}% commission{c.contactName ? ` · ${c.contactName}` : ""}</p>
              </div>
              <p style={{ fontSize: 14, fontWeight: 700, color: "#059669", margin: 0 }}>{fmtMoney(c.trustBalance)}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
