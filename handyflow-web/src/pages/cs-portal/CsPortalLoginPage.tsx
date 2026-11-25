// src/pages/cs-portal/CsPortalLoginPage.tsx
import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { csPortalApi } from "../../api/csPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { color, radius, shadow, space, type } from "../accountant-portal/portal-theme"
import { errText } from "../fm-portal/portalKit"

export function CsPortalLoginPage() {
  const navigate = useNavigate()
  const setAuth = usePortalAuthStore(s => s.setAuth)
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(""); setLoading(true)
    try {
      const res = await csPortalApi.login({ email, password })
      setAuth(res.token, { portalUserId: res.userId, email: res.email, fullName: res.fullName })
      navigate("/compliance-services/portal/home", { replace: true })
    } catch (err) {
      setError(errText(err, "Invalid email or password"))
    } finally { setLoading(false) }
  }

  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", background: color.canvas, fontFamily: type.family, padding: space(4) }}>
      <div style={{ width: 400, maxWidth: "100%", background: color.surface, borderRadius: radius.lg, padding: space(8), boxShadow: shadow.modal, border: `1px solid ${color.border}` }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: color.ink, margin: 0, letterSpacing: "-0.02em" }}>Compliance client portal</h1>
        <p style={{ fontSize: 13.5, color: color.muted, margin: `${space(1.5)} 0 ${space(6)}`, lineHeight: 1.5 }}>Sign in to see your quotes, agreements and invoices.</p>
        <form onSubmit={submit}>
          <label htmlFor="csp-email" style={label}>Email address</label>
          <input id="csp-email" type="email" required autoFocus value={email} onChange={e => setEmail(e.target.value)} style={{ ...field, marginBottom: space(4) }} />
          <label htmlFor="csp-password" style={label}>Password</label>
          <input id="csp-password" type="password" required value={password} onChange={e => setPassword(e.target.value)} style={{ ...field, marginBottom: space(5) }} />
          {error && <div role="alert" style={alert}>{error}</div>}
          <button type="submit" disabled={loading} style={submitStyle(loading)}>{loading ? "Signing in…" : "Sign In"}</button>
        </form>
        <p style={{ fontSize: 12.5, color: color.muted, margin: `${space(5)} 0 0`, textAlign: "center" }}>Need access? Ask your compliance practice to invite you.</p>
      </div>
    </div>
  )
}

const label: React.CSSProperties = { display: "block", fontSize: 12.5, fontWeight: 600, color: color.slate, marginBottom: space(1.5) }
const field: React.CSSProperties = { width: "100%", padding: "11px 13px", border: `1.5px solid ${color.border}`, borderRadius: 8, fontSize: 14, boxSizing: "border-box", fontFamily: type.family, color: color.ink }
const alert: React.CSSProperties = { marginBottom: space(4), padding: `${space(2.5)} ${space(3.5)}`, background: color.redBg, border: "1px solid var(--hf-danger-border)", borderRadius: radius.sm, fontSize: 13, color: color.redText }
const submitStyle = (loading: boolean): React.CSSProperties => ({ width: "100%", padding: `${space(3)} 0`, background: loading ? color.navyDark : color.navy, color: "var(--hf-text-on-solid)", border: "none", borderRadius: radius.sm, fontSize: 14, fontWeight: 700, cursor: loading ? "default" : "pointer" })
