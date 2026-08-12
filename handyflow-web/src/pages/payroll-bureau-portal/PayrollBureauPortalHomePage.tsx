// src/pages/payroll-bureau-portal/PayrollBureauPortalHomePage.tsx
import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { payrollBureauPortalApi } from "../../api/payrollBureauPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import type { PortalClientSummary } from "../../types/payrollBureauPortal.types"
import { PortalShell } from "../accountant-portal/PortalShell"
import { color, radius, shadow, space } from "../accountant-portal/portal-theme"

export function PayrollBureauPortalHomePage() {
  const navigate = useNavigate()
  const user = usePortalAuthStore(s => s.user)
  const [clients, setClients] = useState<PortalClientSummary[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    payrollBureauPortalApi.getMyClients().then(setClients).finally(() => setLoading(false))
  }, [])

  const firstName = user?.fullName?.split(" ")[0]

  return (
    <PortalShell>
      <div style={{ maxWidth: 680, margin: "0 auto", padding: `${space(10)} ${space(5)} ${space(12)}` }}>
        <h1 style={{ fontSize: 24, fontWeight: 800, color: color.ink, marginBottom: space(1), letterSpacing: "-0.02em" }}>
          Welcome back{firstName ? `, ${firstName}` : ""}
        </h1>
        <p style={{ fontSize: 14, color: color.muted, marginBottom: space(8) }}>
          {loading ? "Loading your client access…"
            : clients.length === 0 ? "Your client access:"
            : `You have access to ${clients.length} client${clients.length !== 1 ? "s" : ""}.`}
        </p>

        {loading ? (
          <div style={{ display: "flex", flexDirection: "column", gap: space(3) }}>
            {[0, 1].map(i => (
              <div key={i} style={{ height: 76, background: color.surface, border: `1px solid ${color.border}`,
                borderRadius: radius.md, opacity: 0.5 }} />
            ))}
          </div>
        ) : clients.length === 0 ? (
          <div style={{ textAlign: "center" as const, padding: `${space(14)} ${space(6)}`, background: color.surface,
            border: `1px dashed ${color.border}`, borderRadius: radius.lg }}>
            <div style={{ fontSize: 32, marginBottom: space(3) }}>📂</div>
            <div style={{ fontSize: 15, fontWeight: 700, color: color.ink, marginBottom: space(1) }}>No client access yet</div>
            <div style={{ fontSize: 13.5, color: color.muted, maxWidth: 320, margin: "0 auto", lineHeight: 1.5 }}>
              Once your payroll bureau grants you access, it will appear here.
            </div>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: space(3) }}>
            {clients.map(c => (
              <button key={c.clientId} onClick={() => navigate(`/payroll-bureau/portal/clients/${c.clientId}`)}
                style={{ textAlign: "left" as const, width: "100%", padding: `${space(5)} ${space(6)}`,
                  background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md,
                  cursor: "pointer", boxShadow: shadow.card, transition: "box-shadow 0.15s ease, transform 0.1s ease" }}
                onMouseEnter={e => { e.currentTarget.style.boxShadow = shadow.cardHover; e.currentTarget.style.transform = "translateY(-1px)" }}
                onMouseLeave={e => { e.currentTarget.style.boxShadow = shadow.card; e.currentTarget.style.transform = "translateY(0)" }}>
                <span style={{ fontSize: 15.5, fontWeight: 700, color: color.ink, letterSpacing: "-0.01em" }}>
                  {c.tradingName}
                </span>
                <div style={{ fontSize: 12.5, color: color.faint, fontWeight: 500, marginTop: space(3) }}>
                  View invoices and filing deadlines →
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </PortalShell>
  )
}
