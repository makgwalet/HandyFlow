// src/pages/accountant-portal/PortalShell.tsx
//
// Shared header shell for every authenticated portal page. The three
// post-login pages (Home, ClientDetail) each reimplemented this same
// header — logo mark, user name, log-out button, optional back link —
// with small drifts (padding, gap values) between copies. Pulling it
// out once means the header can't visually disagree with itself between
// pages, and any future portal page inherits it for free.

import { useNavigate } from "react-router-dom"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { color, space, type } from "./portal-theme"

export function PortalShell({
  backTo,
  children,
}: {
  backTo?: string
  children: React.ReactNode
}) {
  const navigate = useNavigate()
  const user = usePortalAuthStore(s => s.user)
  const logout = usePortalAuthStore(s => s.logout)

  return (
    <div style={{ minHeight: "100vh", background: color.canvas, fontFamily: type.family }}>
      <header
        style={{
          background: color.surface,
          borderBottom: `1px solid ${color.border}`,
          padding: `${space(4)} ${space(6)}`,
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          position: "sticky",
          top: 0,
          zIndex: 10,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: space(3) }}>
          {backTo && (
            <button
              onClick={() => navigate(backTo)}
              style={{
                background: "none",
                border: "none",
                cursor: "pointer",
                color: color.muted,
                fontSize: 13,
                fontWeight: 600,
                padding: `${space(1)} ${space(2)} ${space(1)} 0`,
                display: "flex",
                alignItems: "center",
                gap: 4,
                transition: "color 0.15s ease",
              }}
              onMouseEnter={e => (e.currentTarget.style.color = color.navy)}
              onMouseLeave={e => (e.currentTarget.style.color = color.muted)}
            >
              ← Back
            </button>
          )}
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <div
              style={{
                width: 30,
                height: 30,
                borderRadius: 9,
                background: `linear-gradient(135deg, ${color.navy}, ${color.navyDark})`,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
              }}
            >
              <span style={{ color: "#fff", fontWeight: 800, fontSize: 14 }}>H</span>
            </div>
            <div style={{ fontWeight: 800, fontSize: 15, color: color.ink, letterSpacing: "-0.01em" }}>
              Client Portal
            </div>
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: space(4) }}>
          <span style={{ fontSize: 13, color: color.muted, fontWeight: 500 }}>{user?.fullName}</span>
          <button
            onClick={() => {
              logout()
              navigate("/accountant/portal/login", { replace: true })
            }}
            style={{
              padding: `${space(1.5)} ${space(3)}`,
              border: `1px solid ${color.border}`,
              borderRadius: 8,
              background: color.surface,
              fontSize: 12,
              fontWeight: 600,
              cursor: "pointer",
              color: color.slate,
              transition: "all 0.15s ease",
            }}
            onMouseEnter={e => {
              e.currentTarget.style.borderColor = color.navy
              e.currentTarget.style.color = color.navy
            }}
            onMouseLeave={e => {
              e.currentTarget.style.borderColor = color.border
              e.currentTarget.style.color = color.slate
            }}
          >
            Log out
          </button>
        </div>
      </header>
      {children}
    </div>
  )
}
