// src/pages/booking-agency-portal/BookingAgencyPortalAcceptInvitePage.tsx
import { useState } from "react"
import { useNavigate, useSearchParams } from "react-router-dom"
import { bookingAgencyPortalApi } from "../../api/bookingAgencyPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { color, radius, shadow, space, type } from "../accountant-portal/portal-theme"

export function BookingAgencyPortalAcceptInvitePage() {
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
      <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", background: color.canvas, fontFamily: type.family, padding: space(4) }}>
        <div style={{ textAlign: "center" as const, maxWidth: 340, background: color.surface, borderRadius: radius.lg, padding: space(8), boxShadow: shadow.card, border: `1px solid ${color.border}` }}>
          <div style={{ fontSize: 28, marginBottom: space(3) }}>🔗</div>
          <p style={{ color: color.slate, fontSize: 14, lineHeight: 1.6, margin: 0 }}>This invite link is missing its token — please use the link from your invitation email.</p>
        </div>
      </div>
    )
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError("")
    if (password !== confirmPassword) { setError("Passwords don't match"); return }
    if (password.length < 8) { setError("Password must be at least 8 characters"); return }
    setLoading(true)
    try {
      const res = await bookingAgencyPortalApi.register({ inviteToken: token, password, fullName })
      setAuth(res.token, { portalUserId: res.portalUserId, email: res.email, fullName: res.fullName })
      navigate("/booking-agency/portal", { replace: true })
    } catch (err: any) {
      setError(err.response?.data?.message ?? "Failed to accept invite — the link may have expired")
    } finally { setLoading(false) }
  }

  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", background: `linear-gradient(180deg, ${color.canvas} 0%, #EEF2F7 100%)`, fontFamily: type.family, padding: space(4) }}>
      <div style={{ width: 420, maxWidth: "100%", background: color.surface, borderRadius: radius.lg, padding: space(9), boxShadow: shadow.modal, border: `1px solid ${color.border}` }}>
        <div style={{ textAlign: "center" as const, marginBottom: space(8) }}>
          <div style={{ width: 48, height: 48, borderRadius: radius.md, background: `linear-gradient(135deg, ${color.navy}, ${color.navyDark})`, display: "flex", alignItems: "center", justifyContent: "center", margin: `0 auto ${space(4)}`, boxShadow: "0 4px 12px rgba(27, 58, 107, 0.25)" }}>
            <span style={{ color: "#fff", fontWeight: 800, fontSize: 20 }}>H</span>
          </div>
          <h1 style={{ fontSize: 20, fontWeight: 800, color: color.ink, margin: 0, letterSpacing: "-0.02em" }}>Set Up Your Account</h1>
          <p style={{ fontSize: 13.5, color: color.muted, margin: `${space(1.5)} 0 0` }}>Create a password to access your booking agency portal</p>
        </div>
        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: space(4) }}><label style={labelStyle}>Your full name</label>
            <input required autoFocus value={fullName} onChange={e => setFullName(e.target.value)} style={inputStyle} /></div>
          <div style={{ marginBottom: space(4) }}><label style={labelStyle}>Password</label>
            <input type="password" required value={password} onChange={e => setPassword(e.target.value)} style={inputStyle} />
            <div style={{ fontSize: 11.5, color: color.faint, marginTop: space(1) }}>At least 8 characters</div></div>
          <div style={{ marginBottom: space(5) }}><label style={labelStyle}>Confirm password</label>
            <input type="password" required value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} style={inputStyle} /></div>
          {error && <div style={{ marginBottom: space(4), padding: `${space(2.5)} ${space(3.5)}`, background: color.redBg, border: "1px solid #FECACA", borderRadius: radius.sm, fontSize: 13, color: color.red }}>{error}</div>}
          <button type="submit" disabled={loading} style={{ width: "100%", padding: `${space(3)} 0`, background: loading ? color.navyDark : color.navy, color: "#fff", border: "none", borderRadius: radius.sm, fontSize: 14, fontWeight: 700, cursor: loading ? "default" : "pointer" }}>
            {loading ? "Setting up…" : "Accept Invite & Sign In"}
          </button>
        </form>
      </div>
    </div>
  )
}

const labelStyle: React.CSSProperties = { display: "block", fontSize: 12.5, fontWeight: 600, color: color.slate, marginBottom: space(1.5) }
const inputStyle: React.CSSProperties = { width: "100%", padding: "11px 13px", border: `1.5px solid ${color.border}`, borderRadius: 8, fontSize: 14, boxSizing: "border-box", fontFamily: type.family, color: color.ink, outline: "none" }
