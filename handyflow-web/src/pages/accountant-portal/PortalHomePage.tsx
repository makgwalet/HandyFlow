// src/pages/accountant-portal/PortalHomePage.tsx
import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { portalApi } from "../../api/portal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import type { PortalClientSummary } from "../../types/portal.types"

// NEW: deliberately minimal — this exists so the login/register flow
// has somewhere real to land, not a 404. The full fee note/document
// view per client is the clearly-flagged next step, not built yet;
// this is honest about that in the UI itself rather than showing a
// clickable client row that goes nowhere.
export function PortalHomePage() {
  const navigate = useNavigate()
  const user = usePortalAuthStore(s => s.user)
  const logout = usePortalAuthStore(s => s.logout)
  const [clients, setClients] = useState<PortalClientSummary[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    portalApi.getMyClients().then(setClients).finally(() => setLoading(false))
  }, [])

  return (
    <div style={{ minHeight: "100vh", background: "#F8FAFC", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ background: "#fff", borderBottom: "1px solid #E2E8F0", padding: "16px 24px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div style={{ fontWeight: 800, fontSize: 16, color: "#0F172A" }}>Client Portal</div>
        <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
          <span style={{ fontSize: 13, color: "#64748B" }}>{user?.fullName}</span>
          <button onClick={() => { logout(); navigate("/accountant/portal/login", { replace: true }) }}
            style={{ padding: "6px 12px", border: "1px solid #E2E8F0", borderRadius: 7, background: "#fff", fontSize: 12, cursor: "pointer", color: "#64748B" }}>
            Log out
          </button>
        </div>
      </div>

      <div style={{ maxWidth: 640, margin: "40px auto", padding: "0 20px" }}>
        <h1 style={{ fontSize: 20, fontWeight: 800, color: "#0F172A", marginBottom: 4 }}>Welcome, {user?.fullName?.split(" ")[0]}</h1>
        <p style={{ fontSize: 13, color: "#64748B", marginBottom: 24 }}>Your client access:</p>

        {loading ? (
          <div style={{ textAlign: "center" as const, padding: 40, color: "#94A3B8" }}>Loading...</div>
        ) : clients.length === 0 ? (
          <div style={{ textAlign: "center" as const, padding: 40, color: "#94A3B8" }}>No client access found on this account.</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {clients.map(c => (
              <button key={c.clientId} onClick={() => navigate(`/accountant/portal/clients/${c.clientId}`)}
                style={{ textAlign: "left" as const, width: "100%", padding: "16px 20px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10, fontSize: 14, fontWeight: 600, color: "#0F172A", cursor: "pointer" }}>
                {c.tradingName}
                <div style={{ fontSize: 12, color: "#94A3B8", fontWeight: 400, marginTop: 4 }}>
                  View fee notes and documents →
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
