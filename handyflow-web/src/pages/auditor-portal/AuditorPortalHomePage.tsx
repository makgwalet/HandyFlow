// src/pages/auditor-portal/AuditorPortalHomePage.tsx
//
// UNVERIFIED/known gap, surfaced honestly rather than hidden: there is
// no confirmed way yet to resolve a real business name from a raw
// tenantId across module boundaries (flagged directly in
// AuditorPortalDataService's own comments). Until that's built, this
// page shows each business as "Business Access #<short id>" rather
// than pretending a real name is available.
import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { auditorPortalApi, type AuditorTenantAccess } from "../../api/auditorPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"

const NAVY = "#1B3A6B"
const BORDER = "#E2E8F0"
const INK = "#0F172A"
const MUTED = "#64748B"
const FAINT = "#94A3B8"

const fmtD = (d: string) => new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })

export function AuditorPortalHomePage() {
  const navigate = useNavigate()
  const user = usePortalAuthStore(s => s.user)
  const logout = usePortalAuthStore(s => s.logout)
  const [tenants, setTenants] = useState<AuditorTenantAccess[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    auditorPortalApi.getMyTenants().then(res => {
      // Same single-item auto-redirect pattern proven for the other
      // three portals earlier this session — skip the list entirely
      // when there's only one business to review.
      if (res.length === 1) {
        navigate(`/auditor/portal/tenants/${res[0].tenantId}`, { replace: true })
        return
      }
      setTenants(res)
    }).finally(() => setLoading(false))
  }, [navigate])

  return (
    <div style={{ minHeight: "100vh", background: "#F8FAFC", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "16px 32px", background: "#fff", borderBottom: `1px solid ${BORDER}` }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <div style={{ width: 28, height: 28, borderRadius: 6, background: NAVY, color: "#fff", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 13 }}>H</div>
          <span style={{ fontSize: 14, fontWeight: 800, color: INK }}>Auditor Access</span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <span style={{ fontSize: 13, color: MUTED }}>{user?.fullName ?? user?.email}</span>
          <button onClick={logout} style={{ padding: "6px 12px", background: "#fff", color: NAVY, border: `1px solid ${NAVY}`, borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }}>Log out</button>
        </div>
      </div>

      <div style={{ padding: "32px" }}>
        <h1 style={{ fontSize: 20, fontWeight: 800, color: INK, marginBottom: 4 }}>Businesses you can review</h1>
        <p style={{ fontSize: 13, color: MUTED, marginBottom: 24 }}>
          Everything you've been granted access to review.
        </p>

        {loading ? (
          <div style={{ color: FAINT, fontSize: 13 }}>Loading…</div>
        ) : tenants.length === 0 ? (
          <div style={{ padding: 40, textAlign: "center" as const, color: FAINT, fontSize: 14, background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8 }}>
            No businesses have granted you access yet.
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 10, maxWidth: 480 }}>
            {tenants.map(t => (
              <button key={t.tenantId} onClick={() => navigate(`/auditor/portal/tenants/${t.tenantId}`)}
                style={{ textAlign: "left" as const, padding: "14px 16px", background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8, cursor: "pointer" }}>
                <div style={{ fontSize: 14, fontWeight: 700, color: INK }}>Business Access #{t.tenantId.slice(0, 8)}</div>
                <div style={{ fontSize: 12, color: FAINT, marginTop: 2 }}>Access granted {fmtD(t.acceptedAt)}</div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
