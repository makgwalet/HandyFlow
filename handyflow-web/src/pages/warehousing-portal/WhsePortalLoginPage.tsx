// src/pages/warehousing-portal/WhsePortalLoginPage.tsx
//
// Same ⚠ usePortalAuthStore assumption as the Collections Agency portal
// build (see that module's CollAgencyPortalLoginPage.tsx for the full
// note) — funnelled through one applyPortalAuth() call. Endpoint base
// path /api/v1/warehousing/portal/auth/{login,register} directly
// confirmed via WhsePortalAuthController source.
import { useState } from "react"
import { useNavigate, Link } from "react-router-dom"
import { apiClient } from "../../api/client"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { Warehouse } from "lucide-react"

const ACCENT = "#0F766E"

export function WhsePortalLoginPage() {
  const navigate = useNavigate()
  const portalAuth = usePortalAuthStore()
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const applyPortalAuth = (token: string, user: { id: string; email: string; fullName: string }) => {
    // See CollAgencyPortalLoginPage.tsx's header note if this line doesn't match your store's real API.
    (portalAuth as any).login?.(token, user) ?? (portalAuth as any).setAuth?.(token, user)
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null); setLoading(true)
    try {
      const res = await apiClient.post("/api/v1/warehousing/portal/auth/login", { email, password })
      const { token, id, email: respEmail, fullName } = res.data
      applyPortalAuth(token, { id, email: respEmail, fullName })
      navigate("/warehousing/portal")
    } catch (err: any) {
      setError(err?.response?.data?.message ?? "Invalid email or password")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: "100vh", background: "var(--hf-surface-sunken)", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 36, width: 380, boxShadow: "0 4px 24px rgba(0,0,0,0.06)" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 24 }}>
          <div style={{ width: 38, height: 38, borderRadius: 10, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Warehouse size={19} color="#fff" />
          </div>
          <div>
            <p style={{ fontSize: 15, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>Client Portal</p>
            <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>Warehousing</p>
          </div>
        </div>

        <form onSubmit={submit}>
          <div style={{ marginBottom: 14 }}>
            <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5, display: "block" }}>Email</label>
            <input type="email" required value={email} onChange={e => setEmail(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} />
          </div>
          <div style={{ marginBottom: 18 }}>
            <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5, display: "block" }}>Password</label>
            <input type="password" required value={password} onChange={e => setPassword(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} />
          </div>

          {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12.5, marginBottom: 14 }}>{error}</p>}

          <button type="submit" disabled={loading}
            style={{ width: "100%", padding: "11px", borderRadius: 8, border: "none", background: ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: loading ? 0.7 : 1 }}>
            {loading ? "Signing in…" : "Sign in"}
          </button>
        </form>

        <p style={{ fontSize: 12, color: "var(--hf-text-faint)", textAlign: "center", marginTop: 18 }}>
          Received an invite email? <Link to="/warehousing/portal/auth/accept-invite" style={{ color: ACCENT, fontWeight: 600 }}>Accept it here</Link>
        </p>
      </div>
    </div>
  )
}
