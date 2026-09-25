// src/pages/property-portal/PropPortalAcceptInvitePage.tsx
import { useState } from "react"
import { useNavigate, useSearchParams, Link } from "react-router-dom"
import { apiClient } from "../../api/client"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { Home } from "lucide-react"

const ACCENT = "#0D9488"

export function PropPortalAcceptInvitePage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const token = params.get("token") ?? ""
  const setAuth = usePortalAuthStore(s => s.setAuth)

  const [fullName, setFullName] = useState("")
  const [password, setPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (password !== confirmPassword) { setError("Passwords don't match"); return }
    if (!token) { setError("This invite link is missing its token — please use the link from your invite email"); return }
    setLoading(true)
    try {
      const res = await apiClient.post("/api/v1/property/portal/auth/register", { inviteToken: token, password, fullName })
      const { token: authToken, userId, email, fullName: respName } = res.data
      setAuth(authToken, { portalUserId: userId, email, fullName: respName })
      navigate("/property/portal")
    } catch (err: any) {
      setError(err?.response?.data?.message ?? "This invite link is invalid or has expired")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: "100vh", background: "var(--hf-surface-sunken)", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 36, width: 380, boxShadow: "0 4px 24px rgba(0,0,0,0.06)" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 10 }}>
          <div style={{ width: 38, height: 38, borderRadius: 10, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Home size={19} color="#fff" />
          </div>
          <div>
            <p style={{ fontSize: 15, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>Accept your invite</p>
            <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>Property Tenant Portal</p>
          </div>
        </div>

        {!token && (
          <p style={{ fontSize: 12, color: "var(--hf-danger-text)", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, padding: 10, margin: "14px 0" }}>
            This link is missing its invite token. Please use the exact link from your invite email.
          </p>
        )}

        <form onSubmit={submit} style={{ marginTop: 16 }}>
          <div style={{ marginBottom: 14 }}>
            <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5, display: "block" }}>Your full name</label>
            <input required value={fullName} onChange={e => setFullName(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} />
          </div>
          <div style={{ marginBottom: 14 }}>
            <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5, display: "block" }}>Choose a password</label>
            <input type="password" required minLength={8} value={password} onChange={e => setPassword(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} />
          </div>
          <div style={{ marginBottom: 18 }}>
            <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5, display: "block" }}>Confirm password</label>
            <input type="password" required value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} />
          </div>

          {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12.5, marginBottom: 14 }}>{error}</p>}

          <button type="submit" disabled={loading || !token}
            style={{ width: "100%", padding: "11px", borderRadius: 8, border: "none", background: ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (loading || !token) ? 0.6 : 1 }}>
            {loading ? "Creating account…" : "Create account & sign in"}
          </button>
        </form>

        <p style={{ fontSize: 12, color: "var(--hf-text-faint)", textAlign: "center", marginTop: 18 }}>
          Already have an account? <Link to="/property/portal/login" style={{ color: ACCENT, fontWeight: 600 }}>Sign in</Link>
        </p>
      </div>
    </div>
  )
}
