// src/pages/warehousing-portal/WhsePortalHomePage.tsx
//
// Lists every client this portal user has access to — GET
// /api/v1/warehousing/portal/clients, confirmed via WhsePortalDataController.
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { Warehouse, LogOut, ChevronRight, Building2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePortalAuthStore } from "../../store/portalAuth.store"

const ACCENT = "#0F766E"

interface PortalClientSummary { clientId: string; tradingName: string }

export function WhsePortalHomePage() {
  const navigate = useNavigate()
  const portalAuth = usePortalAuthStore() as any

  const { data: clients = [], isLoading } = useQuery<PortalClientSummary[]>({
    queryKey: ["whse-portal-my-clients"],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/portal/clients")).data,
  })

  const logout = () => {
    portalAuth.logout?.()
    navigate("/warehousing/portal/login")
  }

  return (
    <div style={{ minHeight: "100vh", background: "#F1F5F9", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <header style={{ background: "#fff", borderBottom: "1px solid #E2E8F0", padding: "16px 32px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{ width: 34, height: 34, borderRadius: 9, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Warehouse size={17} color="#fff" />
          </div>
          <div>
            <p style={{ fontSize: 14, fontWeight: 800, color: "#0F172A", margin: 0 }}>Client Portal</p>
            <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{portalAuth.user?.fullName ?? portalAuth.user?.email ?? ""}</p>
          </div>
        </div>
        <button onClick={logout} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "1px solid #E2E8F0", borderRadius: 8, padding: "7px 14px", fontSize: 12.5, fontWeight: 600, color: "#64748B", cursor: "pointer" }}>
          <LogOut size={14} /> Sign out
        </button>
      </header>

      <main style={{ maxWidth: 720, margin: "0 auto", padding: "32px 24px" }}>
        <h1 style={{ fontSize: 20, fontWeight: 800, color: "#0F172A", margin: "0 0 4px" }}>Your accounts</h1>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: "0 0 24px" }}>Stock, shipments and billing for the operators you work with.</p>

        {isLoading ? (
          <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
        ) : clients.length === 0 ? (
          <p style={{ color: "#94A3B8", fontSize: 13 }}>You don't have access to any client accounts yet.</p>
        ) : (
          <div style={{ display: "grid", gap: 10 }}>
            {clients.map(c => (
              <button key={c.clientId} onClick={() => navigate(`/warehousing/portal/clients/${c.clientId}`)}
                style={{ display: "flex", alignItems: "center", justifyContent: "space-between", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "18px 20px", cursor: "pointer", textAlign: "left" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <div style={{ width: 38, height: 38, borderRadius: 10, background: "#F0FDFA", display: "flex", alignItems: "center", justifyContent: "center" }}>
                    <Building2 size={18} color={ACCENT} />
                  </div>
                  <p style={{ fontSize: 14, fontWeight: 700, color: "#0F172A", margin: 0 }}>{c.tradingName}</p>
                </div>
                <ChevronRight size={18} color="#CBD5E1" />
              </button>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
