// src/pages/auditor-portal/AuditorPortalAcceptInvitePage.tsx
import { useState } from "react"
import { useNavigate, useSearchParams } from "react-router-dom"
import { auditorPortalApi } from "../../api/auditorPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"

const NAVY = "#1B3A6B"
const BORDER = "#E2E8F0"
const INK = "#0F172A"
const MUTED = "#64748B"

export function AuditorPortalAcceptInvitePage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const inviteToken = searchParams.get("token") ?? ""
  const setAuth = usePortalAuthStore(s => s.setAuth)

  const [fullName, setFullName] = useState("")
  const [password, setPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (password !== confirmPassword) { setError("Passwords don't match"); return }
    if (!inviteToken) { setError("This invite link is missing its token — check the link and try again"); return }
    setLoading(true); setError("")
    try {
      const res = await auditorPortalApi.register({ inviteToken, password, fullName })
      setAuth(res.token, { id: res.portalUserId, email: res.email, fullName: res.fullName })
      navigate("/auditor/portal")
    } catch (err: any) {
      setError(err.response?.data?.message ?? "Failed to accept this invite")
    } finally { setLoading(false) }
  }

  return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: "100vh", background: "#F8FAFC", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <form onSubmit={submit} style={{ background: "#fff", padding: 32, borderRadius: 12, width: 380, boxShadow: "0 20px 60px rgba(0,0,0,0.08)" }}>
        <h1 style={{ fontSize: 18, fontWeight: 800, color: INK, marginBottom: 4 }}>Accept Invite</h1>
        <p style={{ fontSize: 13, color: MUTED, marginBottom: 20 }}>Set your password to finish setting up your auditor access.</p>

        <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>Full name</label>
        <input required value={fullName} onChange={e => setFullName(e.target.value)}
          style={{ width: "100%", padding: "11px 13px", border: `1.5px solid ${BORDER}`, borderRadius: 8, fontSize: 14, marginBottom: 14, boxSizing: "border-box" as const }} />

        <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>Password</label>
        <input type="password" required minLength={8} value={password} onChange={e => setPassword(e.target.value)}
          style={{ width: "100%", padding: "11px 13px", border: `1.5px solid ${BORDER}`, borderRadius: 8, fontSize: 14, marginBottom: 14, boxSizing: "border-box" as const }} />

        <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>Confirm password</label>
        <input type="password" required value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)}
          style={{ width: "100%", padding: "11px 13px", border: `1.5px solid ${BORDER}`, borderRadius: 8, fontSize: 14, marginBottom: 18, boxSizing: "border-box" as const }} />

        {error && <div style={{ padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 6, fontSize: 12.5, color: "#DC2626", marginBottom: 14 }}>{error}</div>}

        <button type="submit" disabled={loading}
          style={{ width: "100%", padding: "11px 13px", background: NAVY, color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
          {loading ? "Setting up…" : "Accept & Sign In"}
        </button>
      </form>
    </div>
  )
}
