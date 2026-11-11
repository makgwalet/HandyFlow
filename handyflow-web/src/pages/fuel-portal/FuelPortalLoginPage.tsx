// Fuel customer portal sign-in. Uses the portal client (portal token), not the staff client.
import { useState } from "react"
import { useNavigate, Link } from "react-router-dom"
import { fuelPortalApiClient } from "../../api/fuelPortal.client"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { Fuel } from "lucide-react"

const ACCENT = "var(--hf-accent-solid-strong)"
const ACCENT_TEXT = "var(--hf-accent-text-strong)"
const input: React.CSSProperties = { width: "100%", padding: "10px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }
const label: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5, display: "block" }

export function FuelPortalLoginPage() {
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
      const res = await fuelPortalApiClient.post("/api/v1/fuel/portal/auth/login", { email, password })
      const { token, userId, email: respEmail, fullName } = res.data
      setAuth(token, { portalUserId: userId, email: respEmail, fullName })
      navigate("/fuel/portal")
    } catch (err: any) {
      setError(err?.response?.data?.message ?? "Invalid email or password")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: "100vh", background: "var(--hf-surface-sunken)", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 36, width: 380, maxWidth: "92vw", boxShadow: "0 4px 24px rgba(0,0,0,0.06)" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 24 }}>
          <div style={{ width: 38, height: 38, borderRadius: 10, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Fuel size={19} style={{ color: "var(--hf-text-on-solid)" }} />
          </div>
          <div>
            <p style={{ fontSize: 15, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>Customer Portal</p>
            <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>Fuel</p>
          </div>
        </div>
        <form onSubmit={submit}>
          <div style={{ marginBottom: 14 }}>
            <label htmlFor="fp-email" style={label}>Email</label>
            <input id="fp-email" type="email" required value={email} onChange={e => setEmail(e.target.value)} style={input} />
          </div>
          <div style={{ marginBottom: 18 }}>
            <label htmlFor="fp-password" style={label}>Password</label>
            <input id="fp-password" type="password" required value={password} onChange={e => setPassword(e.target.value)} style={input} />
          </div>
          {error && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5, marginBottom: 14 }}>{error}</p>}
          <button type="submit" disabled={loading}
            style={{ width: "100%", padding: 11, borderRadius: 8, border: "none", background: ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: loading ? 0.7 : 1 }}>
            {loading ? "Signing in…" : "Sign in"}
          </button>
        </form>
        <p style={{ fontSize: 12, color: "var(--hf-text-faint)", textAlign: "center", marginTop: 18 }}>
          Received an invite email? Use the link in it, or <Link to="/fuel/portal/auth/accept-invite" style={{ color: ACCENT_TEXT, fontWeight: 600 }}>open the invite page</Link>.
        </p>
      </div>
    </div>
  )
}
