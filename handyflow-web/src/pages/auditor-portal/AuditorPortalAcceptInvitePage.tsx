// src/pages/auditor-portal/AuditorPortalAcceptInvitePage.tsx
import { useState } from "react"
import { useNavigate, useSearchParams } from "react-router-dom"
import { auditorPortalApi } from "../../api/auditorPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"

const NAVY = "var(--hf-primary)"
const BORDER = "var(--hf-border)"
const INK = "var(--hf-text)"
const MUTED = "var(--hf-text-muted)"

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
      setAuth(res.token, { portalUserId: res.portalUserId, email: res.email, fullName: res.fullName })
      navigate("/auditor/portal")
    } catch (err: any) {
      setError(err.response?.data?.message ?? "Failed to accept this invite")
    } finally { setLoading(false) }
  }

  return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: "100vh", background: "var(--hf-surface-muted)", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <form onSubmit={submit} style={{ background: "var(--hf-surface)", padding: 32, borderRadius: 12, width: 380, boxShadow: "0 20px 60px rgba(0,0,0,0.08)" }}>
        <h1 style={{ fontSize: 18, fontWeight: 800, color: INK, marginBottom: 4 }}>Accept Invite</h1>
        <p style={{ fontSize: 13, color: MUTED, marginBottom: 20 }}>Set your password to finish setting up your auditor access.</p>

        <label htmlFor="portal-fullname" style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>Full name</label>
        <input id="portal-fullname" required autoComplete="name" value={fullName} onChange={e => setFullName(e.target.value)}
          style={{ width: "100%", padding: "11px 13px", border: `1.5px solid ${BORDER}`, borderRadius: 8, fontSize: 14, marginBottom: 14, boxSizing: "border-box" as const }} />

        <label htmlFor="portal-password" style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>Password</label>
        <input id="portal-password" type="password" required minLength={8} autoComplete="new-password" value={password} onChange={e => setPassword(e.target.value)}
          style={{ width: "100%", padding: "11px 13px", border: `1.5px solid ${BORDER}`, borderRadius: 8, fontSize: 14, marginBottom: 14, boxSizing: "border-box" as const }} />

        <label htmlFor="portal-confirm" style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>Confirm password</label>
        <input id="portal-confirm" type="password" required autoComplete="new-password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)}
          style={{ width: "100%", padding: "11px 13px", border: `1.5px solid ${BORDER}`, borderRadius: 8, fontSize: 14, marginBottom: 18, boxSizing: "border-box" as const }} />

        {error && <div role="alert" style={{ padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 6, fontSize: 12.5, color: "var(--hf-danger-text)", marginBottom: 14 }}>{error}</div>}

        <button type="submit" disabled={loading}
          style={{ width: "100%", padding: "11px 13px", background: NAVY, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
          {loading ? "Setting up…" : "Accept & Sign In"}
        </button>
      </form>
    </div>
  )
}
