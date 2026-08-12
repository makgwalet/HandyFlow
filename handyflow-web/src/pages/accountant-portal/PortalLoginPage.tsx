// src/pages/accountant-portal/PortalLoginPage.tsx
import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { portalApi } from "../../api/portal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { color, radius, shadow, space, type } from "./portal-theme"

export function PortalLoginPage() {
  const navigate = useNavigate()
  const setAuth = usePortalAuthStore(s => s.setAuth)
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError("")
    setLoading(true)
    try {
      const res = await portalApi.login({ email, password })
      setAuth(res.token, { portalUserId: res.portalUserId, email: res.email, fullName: res.fullName })
      navigate("/accountant/portal", { replace: true })
    } catch (err: any) {
      setError(err.response?.data?.message ?? "Invalid email or password")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: `linear-gradient(180deg, ${color.canvas} 0%, #EEF2F7 100%)`,
        fontFamily: type.family,
        padding: space(4),
      }}
    >
      <div
        style={{
          width: 400,
          maxWidth: "100%",
          background: color.surface,
          borderRadius: radius.lg,
          padding: space(9),
          boxShadow: shadow.modal,
          border: `1px solid ${color.border}`,
        }}
      >
        <div style={{ textAlign: "center" as const, marginBottom: space(8) }}>
          <div
            style={{
              width: 48,
              height: 48,
              borderRadius: radius.md,
              background: `linear-gradient(135deg, ${color.navy}, ${color.navyDark})`,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              margin: `0 auto ${space(4)}`,
              boxShadow: "0 4px 12px rgba(27, 58, 107, 0.25)",
            }}
          >
            <span style={{ color: "#fff", fontWeight: 800, fontSize: 20 }}>H</span>
          </div>
          <h1 style={{ fontSize: 20, fontWeight: 800, color: color.ink, margin: 0, letterSpacing: "-0.02em" }}>
            Client Portal
          </h1>
          <p style={{ fontSize: 13.5, color: color.muted, margin: `${space(1.5)} 0 0`, lineHeight: 1.5 }}>
            Sign in to view your documents and invoices
          </p>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: space(4) }}>
            <label
              style={{
                display: "block",
                fontSize: 12.5,
                fontWeight: 600,
                color: color.slate,
                marginBottom: space(1.5),
                letterSpacing: "0.01em",
              }}
            >
              Email
            </label>
            <input
              type="email"
              required
              autoFocus
              value={email}
              onChange={e => setEmail(e.target.value)}
              style={inputStyle}
              onFocus={e => (e.currentTarget.style.borderColor = color.navy)}
              onBlur={e => (e.currentTarget.style.borderColor = color.border)}
            />
          </div>
          <div style={{ marginBottom: space(5) }}>
            <label
              style={{
                display: "block",
                fontSize: 12.5,
                fontWeight: 600,
                color: color.slate,
                marginBottom: space(1.5),
                letterSpacing: "0.01em",
              }}
            >
              Password
            </label>
            <input
              type="password"
              required
              value={password}
              onChange={e => setPassword(e.target.value)}
              style={inputStyle}
              onFocus={e => (e.currentTarget.style.borderColor = color.navy)}
              onBlur={e => (e.currentTarget.style.borderColor = color.border)}
            />
          </div>

          {error && (
            <div
              style={{
                marginBottom: space(4),
                padding: `${space(2.5)} ${space(3.5)}`,
                background: color.redBg,
                border: `1px solid #FECACA`,
                borderRadius: radius.sm,
                fontSize: 13,
                color: color.red,
                lineHeight: 1.4,
              }}
            >
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            style={{
              width: "100%",
              padding: `${space(3)} 0`,
              background: loading ? color.navyDark : color.navy,
              color: "#fff",
              border: "none",
              borderRadius: radius.sm,
              fontSize: 14,
              fontWeight: 700,
              cursor: loading ? "default" : "pointer",
              transition: "background 0.15s ease, transform 0.1s ease",
              letterSpacing: "0.01em",
            }}
            onMouseDown={e => (e.currentTarget.style.transform = "scale(0.98)")}
            onMouseUp={e => (e.currentTarget.style.transform = "scale(1)")}
          >
            {loading ? "Signing in…" : "Sign In"}
          </button>
        </form>
      </div>
    </div>
  )
}

const inputStyle: React.CSSProperties = {
  width: "100%",
  padding: "11px 13px",
  border: `1.5px solid ${color.border}`,
  borderRadius: 8,
  fontSize: 14,
  boxSizing: "border-box",
  fontFamily: type.family,
  color: color.ink,
  transition: "border-color 0.15s ease",
  outline: "none",
}
