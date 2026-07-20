// src/pages/accountant-portal/PortalLoginPage.tsx
import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { portalApi } from "../../api/portal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"

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
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", background: "#F8FAFC", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ width: 380, background: "#fff", borderRadius: 16, padding: 32, boxShadow: "0 8px 32px rgba(15,23,42,0.08)" }}>
        <div style={{ textAlign: "center" as const, marginBottom: 28 }}>
          <div style={{ width: 44, height: 44, borderRadius: 12, background: "#1B3A6B", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 12px" }}>
            <span style={{ color: "#fff", fontWeight: 800, fontSize: 18 }}>H</span>
          </div>
          <h1 style={{ fontSize: 19, fontWeight: 800, color: "#0F172A", margin: 0 }}>Client Portal</h1>
          <p style={{ fontSize: 13, color: "#64748B", margin: "4px 0 0" }}>Sign in to view your documents and invoices</p>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Email</label>
            <input type="email" required autoFocus value={email} onChange={e => setEmail(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
          </div>
          <div style={{ marginBottom: 18 }}>
            <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Password</label>
            <input type="password" required value={password} onChange={e => setPassword(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
          </div>

          {error && <div style={{ marginBottom: 14, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}

          <button type="submit" disabled={loading}
            style={{ width: "100%", padding: "11px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
            {loading ? "Signing in..." : "Sign In"}
          </button>
        </form>
      </div>
    </div>
  )
}
