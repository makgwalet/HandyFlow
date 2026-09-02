// src/pages/collectionsagency-portal/CollAgencyPortalClientDetailPage.tsx
//
// Read-only for the external client: placed debtor-account portfolio +
// recovery status, and their trust/remittance transaction history —
// confirmed as the full scope of CollAgencyPortalDataController (no
// write endpoints on the portal side at all). Same ⚠ usePortalAuthStore
// assumption as the other portal pages for the header's user/logout bits.
import { useQuery } from "@tanstack/react-query"
import { useParams, useNavigate } from "react-router-dom"
import { ArrowLeft, LogOut, Handshake, ArrowDownCircle, ArrowUpCircle } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePortalAuthStore } from "../../store/portalAuth.store"

const ACCENT = "#5B21B6"
const fmtMoney = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n ?? 0)

interface DebtorAccountResponse {
  id: string; accountReference: string | null; debtorName: string; originalCreditorName: string
  originalDebtAmount: number; currentBalance: number; status: string; placedDate: string; closedDate: string | null
}
interface TrustTransactionResponse {
  id: string; debtorAccountId: string | null; transactionType: string; amount: number
  transactionDate: string; reference: string | null; notes: string | null
}

const STATUS_COLORS: Record<string, { bg: string; fg: string }> = {
  PLACED: { bg: "#F1F5F9", fg: "#475569" }, IN_PROGRESS: { bg: "#DBEAFE", fg: "#1D4ED8" },
  PAYMENT_PLAN_ACTIVE: { bg: "#FEF3C7", fg: "#92400E" }, DISPUTED: { bg: "#FEE2E2", fg: "#991B1B" },
  RECOVERED: { bg: "#DCFCE7", fg: "#166534" }, RETURNED_TO_CLIENT: { bg: "#F1F5F9", fg: "#64748B" },
  WRITTEN_OFF: { bg: "#F1F5F9", fg: "#64748B" }, CLOSED: { bg: "#F1F5F9", fg: "#64748B" },
}

export function CollAgencyPortalClientDetailPage() {
  const { clientId } = useParams<{ clientId: string }>()
  const navigate = useNavigate()
  const portalAuth = usePortalAuthStore() as any

  const { data: accounts = [], isLoading: accountsLoading } = useQuery<DebtorAccountResponse[]>({
    queryKey: ["ca-portal-debtor-accounts", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/collections-agency/portal/clients/${clientId}/debtor-accounts`)).data,
    enabled: !!clientId,
  })
  const { data: trust = [], isLoading: trustLoading } = useQuery<TrustTransactionResponse[]>({
    queryKey: ["ca-portal-trust", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/collections-agency/portal/clients/${clientId}/trust-statement`)).data,
    enabled: !!clientId,
  })

  const totalOutstanding = accounts.reduce((s, a) => s + (a.currentBalance || 0), 0)
  const totalRecovered = accounts.filter(a => a.status === "RECOVERED").reduce((s, a) => s + (a.originalDebtAmount || 0), 0)

  const logout = () => { portalAuth.logout?.(); navigate("/collections-agency/portal/login") }

  return (
    <div style={{ minHeight: "100vh", background: "#F1F5F9", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <header style={{ background: "#fff", borderBottom: "1px solid #E2E8F0", padding: "16px 32px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{ width: 34, height: 34, borderRadius: 9, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Handshake size={17} color="#fff" />
          </div>
          <p style={{ fontSize: 14, fontWeight: 800, color: "#0F172A", margin: 0 }}>Client Portal</p>
        </div>
        <button onClick={logout} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "1px solid #E2E8F0", borderRadius: 8, padding: "7px 14px", fontSize: 12.5, fontWeight: 600, color: "#64748B", cursor: "pointer" }}>
          <LogOut size={14} /> Sign out
        </button>
      </header>

      <main style={{ maxWidth: 860, margin: "0 auto", padding: "28px 24px" }}>
        <button onClick={() => navigate("/collections-agency/portal")} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", cursor: "pointer", color: "#64748B", fontSize: 13, marginBottom: 18, padding: 0 }}>
          <ArrowLeft size={15} /> All accounts
        </button>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 28 }}>
          <div style={{ background: "#fff", border: "1px solid #E8EDF5", borderRadius: 14, padding: 18 }}>
            <p style={{ fontSize: 11, color: "#94A3B8", margin: "0 0 6px", fontWeight: 700, textTransform: "uppercase" }}>Currently outstanding</p>
            <p style={{ fontSize: 22, fontWeight: 800, color: "#0F172A", margin: 0 }}>{fmtMoney(totalOutstanding)}</p>
          </div>
          <div style={{ background: "#fff", border: "1px solid #E8EDF5", borderRadius: 14, padding: 18 }}>
            <p style={{ fontSize: 11, color: "#94A3B8", margin: "0 0 6px", fontWeight: 700, textTransform: "uppercase" }}>Recovered to date</p>
            <p style={{ fontSize: 22, fontWeight: 800, color: "#059669", margin: 0 }}>{fmtMoney(totalRecovered)}</p>
          </div>
        </div>

        <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: "0 0 10px" }}>Placed accounts ({accounts.length})</p>
        {accountsLoading ? (
          <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
        ) : accounts.length === 0 ? (
          <p style={{ color: "#94A3B8", fontSize: 13, marginBottom: 24 }}>No accounts placed with this agency yet.</p>
        ) : (
          <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden", marginBottom: 28 }}>
            {accounts.map((a, i) => {
              const colors = STATUS_COLORS[a.status] ?? { bg: "#F1F5F9", fg: "#64748B" }
              return (
                <div key={a.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 2 }}>
                      <p style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", margin: 0 }}>{a.debtorName}</p>
                      <span style={{ fontSize: 10.5, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: colors.bg, color: colors.fg }}>{a.status.replace(/_/g, " ")}</span>
                    </div>
                    <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>{a.accountReference ? `${a.accountReference} · ` : ""}Placed {a.placedDate}</p>
                  </div>
                  <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{fmtMoney(a.currentBalance)}</p>
                </div>
              )
            })}
          </div>
        )}

        <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: "0 0 10px" }}>Trust statement</p>
        {trustLoading ? (
          <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
        ) : trust.length === 0 ? (
          <p style={{ color: "#94A3B8", fontSize: 13 }}>No trust movements yet.</p>
        ) : (
          <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
            {trust.map((t, i) => (
              <div key={t.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  {t.transactionType === "RECEIPT" ? <ArrowDownCircle size={17} color="#059669" /> : <ArrowUpCircle size={17} color="#D97706" />}
                  <div>
                    <p style={{ fontSize: 12.5, fontWeight: 600, color: "#0F172A", margin: "0 0 2px" }}>{t.transactionType === "RECEIPT" ? "Payment received" : "Remittance paid to you"}{t.reference ? ` · ${t.reference}` : ""}</p>
                    <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>{t.transactionDate}</p>
                  </div>
                </div>
                <p style={{ fontSize: 13, fontWeight: 700, color: t.transactionType === "RECEIPT" ? "#059669" : "#D97706", margin: 0 }}>
                  {t.transactionType === "RECEIPT" ? "+" : "−"}{fmtMoney(t.amount)}
                </p>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
