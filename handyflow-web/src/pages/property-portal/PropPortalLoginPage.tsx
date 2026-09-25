// src/pages/property-portal/PropPortalLoginPage.tsx
//
// Direct mirror of WhsePortalLoginPage, with one real correction: that
// template's own header comment flagged uncertainty about
// usePortalAuthStore's real API ("Same ⚠ usePortalAuthStore assumption
// as the Collections Agency portal build"). Checked the actual store
// directly — it's setAuth(token, user), and user.portalUserId (not
// .id) — used that directly here rather than propagating the same
// defensive fallback hack into a new file.
import { useState } from "react"
import { useNavigate, Link } from "react-router-dom"
import { apiClient } from "../../api/client"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { Home } from "lucide-react"

const ACCENT = "#0D9488"

export function PropPortalLoginPage() {
  const navigate = useNavigate()
  const setAuth = usePortalAuthStore(s => s.setAuth)
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null); setLoading(true)
    try {
      const res = await apiClient.post("/api/v1/property/portal/auth/login", { email, password })
      // apiClient's response interceptor already unwraps ApiResponse —
      // res.data is the real PortalAuthResponse payload directly,
      // confirmed via that interceptor's own source rather than assumed.
      const { token, userId, email: respEmail, fullName } = res.data
      setAuth(token, { portalUserId: userId, email: respEmail, fullName })
      navigate("/property/portal")
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
            <Home size={19} color="#fff" />
          </div>
          <div>
            <p style={{ fontSize: 15, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>Tenant Portal</p>
            <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>Property</p>
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
          Received an invite email? <Link to="/property/portal/auth/accept-invite" style={{ color: ACCENT, fontWeight: 600 }}>Accept it here</Link>
        </p>
      </div>
    </div>
  )
}
