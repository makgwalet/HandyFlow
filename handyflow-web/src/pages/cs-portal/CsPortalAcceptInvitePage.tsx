// src/pages/cs-portal/CsPortalAcceptInvitePage.tsx
import { useState } from "react"
import { useNavigate, useSearchParams } from "react-router-dom"
import { csPortalApi } from "../../api/csPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { color, radius, shadow, space, type } from "../accountant-portal/portal-theme"
import { errText } from "../fm-portal/portalKit"
import { MIN_PASSWORD, passwordProblem } from "./csPortalRules"

export function CsPortalAcceptInvitePage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const token = params.get("token") ?? ""
  const setAuth = usePortalAuthStore(s => s.setAuth)
  const [fullName, setFullName] = useState("")
  const [password, setPassword] = useState("")
  const [confirm, setConfirm] = useState("")
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)

  const wrap = (children: React.ReactNode) => (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", background: color.canvas, fontFamily: type.family, padding: space(4) }}>
      <div style={{ width: 420, maxWidth: "100%", background: color.surface, borderRadius: radius.lg, padding: space(9), boxShadow: shadow.modal, border: `1px solid ${color.border}` }}>{children}</div>
    </div>
  )

  if (!token) return wrap(<p style={{ color: color.slate, fontSize: 14, lineHeight: 1.6, margin: 0, textAlign: "center" }}>This invite link is missing its token. Please use the link from your invitation email.</p>)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError("")
    const problem = passwordProblem(password, confirm)
    if (problem) { setError(problem); return }
    setLoading(true)
    try {
      const res = await csPortalApi.register({ inviteToken: token, password, fullName: fullName.trim() })
      setAuth(res.token, { portalUserId: res.userId, email: res.email, fullName: res.fullName })
      navigate("/compliance-services/portal/home", { replace: true })
    } catch (err) {
      setError(errText(err, "Could not accept the invite. The link may have expired"))
    } finally { setLoading(false) }
  }

  return wrap(
    <>
      <h1 style={{ fontSize: 20, fontWeight: 800, color: color.ink, margin: 0, letterSpacing: "-0.02em", textAlign: "center" }}>Set up your account</h1>
      <p style={{ fontSize: 13.5, color: color.muted, margin: `${space(1.5)} 0 ${space(6)}`, textAlign: "center" }}>Create a password to see your quotes, agreements and invoices. If you already have a portal login, enter that password.</p>
      <form onSubmit={submit}>
        <label htmlFor="csp-name" style={label}>Your full name</label>
        <input id="csp-name" required autoFocus value={fullName} onChange={e => setFullName(e.target.value)} style={{ ...field, marginBottom: space(4) }} />
        <label htmlFor="csp-pw" style={label}>Password</label>
        <input id="csp-pw" type="password" required value={password} onChange={e => setPassword(e.target.value)} style={field} />
        <div style={{ fontSize: 11.5, color: color.faint, margin: `${space(1)} 0 ${space(4)}` }}>At least {MIN_PASSWORD} characters</div>
        <label htmlFor="csp-confirm" style={label}>Confirm password</label>
        <input id="csp-confirm" type="password" required value={confirm} onChange={e => setConfirm(e.target.value)} style={{ ...field, marginBottom: space(5) }} />
        {error && <div role="alert" style={alert}>{error}</div>}
        <button type="submit" disabled={loading} style={submitStyle(loading)}>{loading ? "Setting up…" : "Accept invite and sign in"}</button>
      </form>
    </>,
  )
}

const label: React.CSSProperties = { display: "block", fontSize: 12.5, fontWeight: 600, color: color.slate, marginBottom: space(1.5) }
const field: React.CSSProperties = { width: "100%", padding: "11px 13px", border: `1.5px solid ${color.border}`, borderRadius: 8, fontSize: 14, boxSizing: "border-box", fontFamily: type.family, color: color.ink }
const alert: React.CSSProperties = { marginBottom: space(4), padding: `${space(2.5)} ${space(3.5)}`, background: color.redBg, border: "1px solid var(--hf-danger-border)", borderRadius: radius.sm, fontSize: 13, color: color.redText }
const submitStyle = (loading: boolean): React.CSSProperties => ({ width: "100%", padding: `${space(3)} 0`, background: loading ? color.navyDark : color.navy, color: "var(--hf-text-on-solid)", border: "none", borderRadius: radius.sm, fontSize: 14, fontWeight: 700, cursor: loading ? "default" : "pointer" })
