// src/pages/accountant-portal/PortalAcceptInvitePage.tsx
import { useState } from "react"
import { useNavigate, useSearchParams } from "react-router-dom"
import { portalApi } from "../../api/portal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"

export function PortalAcceptInvitePage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get("token") ?? ""
  const setAuth = usePortalAuthStore(s => s.setAuth)

  const [fullName, setFullName] = useState("")
  const [password, setPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)

  if (!token) {
    return (
      <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", background: "#F8FAFC", fontFamily: "'Inter', system-ui, sans-serif" }}>
        <div style={{ textAlign: "center" as const, color: "#64748B", maxWidth: 320 }}>
          This invite link is missing its token — please use the link from your invitation email.
        </div>
      </div>
    )
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError("")
    if (password !== confirmPassword) {
      setError("Passwords don't match")
      return
    }
    if (password.length < 8) {
      setError("Password must be at least 8 characters")
      return
    }
    setLoading(true)
    try {
      const res = await portalApi.register({ inviteToken: token, password, fullName })
      setAuth(res.token, { portalUserId: res.portalUserId, email: res.email, fullName: res.fullName })
      navigate("/accountant/portal", { replace: true })
    } catch (err: any) {
      setError(err.response?.data?.message ?? "Failed to accept invite — the link may have expired")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", background: "#F8FAFC", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ width: 400, background: "#fff", borderRadius: 16, padding: 32, boxShadow: "0 8px 32px rgba(15,23,42,0.08)" }}>
        <div style={{ textAlign: "center" as const, marginBottom: 28 }}>
          <div style={{ width: 44, height: 44, borderRadius: 12, background: "#1B3A6B", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 12px" }}>
            <span style={{ color: "#fff", fontWeight: 800, fontSize: 18 }}>H</span>
          </div>
          <h1 style={{ fontSize: 19, fontWeight: 800, color: "#0F172A", margin: 0 }}>Set Up Your Account</h1>
          <p style={{ fontSize: 13, color: "#64748B", margin: "4px 0 0" }}>Create a password to access your client portal</p>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Your full name</label>
            <input required autoFocus value={fullName} onChange={e => setFullName(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
          </div>
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Password</label>
            <input type="password" required value={password} onChange={e => setPassword(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
          </div>
          <div style={{ marginBottom: 18 }}>
            <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Confirm password</label>
            <input type="password" required value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
          </div>

          {error && <div style={{ marginBottom: 14, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}

          <button type="submit" disabled={loading}
            style={{ width: "100%", padding: "11px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
            {loading ? "Setting up..." : "Accept Invite & Sign In"}
          </button>
        </form>
      </div>
    </div>
  )
}
