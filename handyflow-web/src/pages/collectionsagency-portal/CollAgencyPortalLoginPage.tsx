// src/pages/collectionsagency-portal/CollAgencyPortalLoginPage.tsx
//
// ⚠ ASSUMPTION FLAGGED: usePortalAuthStore's exact API (the method used
// to store the token/user after a successful login) is NOT confirmed —
// this store lives in your frontend, which isn't part of what's synced
// to me from GitHub (only /platform/src/ is), and I built this without
// the reference file I asked for. I've inferred its shape by analogy to
// the CONFIRMED useAuthStore pattern already used elsewhere in this app
// (`useAuthStore(s => s.token)`, `const { user, logout } = useAuthStore()`)
// — same author, same codebase, very likely the same shape. Everything
// below funnels through the one `applyPortalAuth()` call at the bottom
// of this file, so if your store's real method is named differently
// (setAuth, setToken, etc. instead of login), that's the ONE line to
// fix — not scattered through the component.
import { useState } from "react"
import { useNavigate, Link } from "react-router-dom"
import { apiClient } from "../../api/client"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { Handshake } from "lucide-react"

const ACCENT = "#5B21B6"

export function CollAgencyPortalLoginPage() {
  const navigate = useNavigate()
  const portalAuth = usePortalAuthStore()
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const applyPortalAuth = (token: string, user: { id: string; email: string; fullName: string }) => {
    // See the ⚠ note at the top of this file if this line doesn't match your store's real API.
    (portalAuth as any).login?.(token, user) ?? (portalAuth as any).setAuth?.(token, user)
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null); setLoading(true)
    try {
      const res = await apiClient.post("/api/v1/collections-agency/portal/auth/login", { email, password })
      const { token, id, email: respEmail, fullName } = res.data
      applyPortalAuth(token, { id, email: respEmail, fullName })
      navigate("/collections-agency/portal")
    } catch (err: any) {
      setError(err?.response?.data?.message ?? "Invalid email or password")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: "100vh", background: "#F1F5F9", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 36, width: 380, boxShadow: "0 4px 24px rgba(0,0,0,0.06)" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 24 }}>
          <div style={{ width: 38, height: 38, borderRadius: 10, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Handshake size={19} color="#fff" />
          </div>
          <div>
            <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>Client Portal</p>
            <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>Collections Agency</p>
          </div>
        </div>

        <form onSubmit={submit}>
          <div style={{ marginBottom: 14 }}>
            <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }}>Email</label>
            <input type="email" required value={email} onChange={e => setEmail(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} />
          </div>
          <div style={{ marginBottom: 18 }}>
            <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }}>Password</label>
            <input type="password" required value={password} onChange={e => setPassword(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} />
          </div>

          {error && <p style={{ color: "#DC2626", fontSize: 12.5, marginBottom: 14 }}>{error}</p>}

          <button type="submit" disabled={loading}
            style={{ width: "100%", padding: "11px", borderRadius: 8, border: "none", background: ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: loading ? 0.7 : 1 }}>
            {loading ? "Signing in…" : "Sign in"}
          </button>
        </form>

        <p style={{ fontSize: 12, color: "#94A3B8", textAlign: "center", marginTop: 18 }}>
          Received an invite email? <Link to="/collections-agency/portal/auth/accept-invite" style={{ color: ACCENT, fontWeight: 600 }}>Accept it here</Link>
        </p>
      </div>
    </div>
  )
}
