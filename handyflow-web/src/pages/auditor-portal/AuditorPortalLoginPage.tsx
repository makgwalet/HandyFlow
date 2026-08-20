// src/pages/auditor-portal/AuditorPortalLoginPage.tsx
import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { auditorPortalApi } from "../../api/auditorPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"

const NAVY = "#1B3A6B"
const BORDER = "#E2E8F0"
const INK = "#0F172A"
const MUTED = "#64748B"

export function AuditorPortalLoginPage() {
  const navigate = useNavigate()
  const setAuth = usePortalAuthStore(s => s.setAuth)
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true); setError("")
    try {
      const res = await auditorPortalApi.login({ email, password })
      setAuth(res.token, { id: res.portalUserId, email: res.email, fullName: res.fullName })
      navigate("/auditor/portal")
    } catch (err: any) {
      setError(err.response?.data?.message ?? "Invalid email or password")
    } finally { setLoading(false) }
  }

  return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: "100vh", background: "#F8FAFC", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <form onSubmit={submit} style={{ background: "#fff", padding: 32, borderRadius: 12, width: 360, boxShadow: "0 20px 60px rgba(0,0,0,0.08)" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 24 }}>
          <div style={{ width: 32, height: 32, borderRadius: 8, background: NAVY, color: "#fff", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 15 }}>H</div>
          <span style={{ fontSize: 15, fontWeight: 800, color: INK }}>Auditor Access</span>
        </div>
        <h1 style={{ fontSize: 18, fontWeight: 800, color: INK, marginBottom: 4 }}>Sign in</h1>
        <p style={{ fontSize: 13, color: MUTED, marginBottom: 20 }}>Review the records you've been granted access to.</p>

        <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>Email</label>
        <input type="email" required value={email} onChange={e => setEmail(e.target.value)}
          style={{ width: "100%", padding: "11px 13px", border: `1.5px solid ${BORDER}`, borderRadius: 8, fontSize: 14, marginBottom: 14, boxSizing: "border-box" as const }} />

        <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>Password</label>
        <input type="password" required value={password} onChange={e => setPassword(e.target.value)}
          style={{ width: "100%", padding: "11px 13px", border: `1.5px solid ${BORDER}`, borderRadius: 8, fontSize: 14, marginBottom: 18, boxSizing: "border-box" as const }} />

        {error && <div style={{ padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 6, fontSize: 12.5, color: "#DC2626", marginBottom: 14 }}>{error}</div>}

        <button type="submit" disabled={loading}
          style={{ width: "100%", padding: "11px 13px", background: NAVY, color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
          {loading ? "Signing in…" : "Sign In"}
        </button>
      </form>
    </div>
  )
}
