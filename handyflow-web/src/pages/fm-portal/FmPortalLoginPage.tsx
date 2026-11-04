// src/pages/fm-portal/FmPortalLoginPage.tsx
import { useState } from "react"
import { Clock, FileText, Receipt, Wrench } from "lucide-react"
import { useNavigate } from "react-router-dom"
import { fmPortalApi } from "../../api/fmPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { color, radius, shadow, space, type } from "../accountant-portal/portal-theme"

export function FmPortalLoginPage() {
  const navigate = useNavigate()
  const setAuth = usePortalAuthStore(s => s.setAuth)
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(""); setLoading(true)
    try {
      const res = await fmPortalApi.login({ email, password })
      setAuth(res.token, { portalUserId: res.portalUserId, email: res.email, fullName: res.fullName })
      navigate("/facilitiesmanagement/portal/home", { replace: true })
    } catch (err: any) {
      setError(err.response?.data?.message ?? "Invalid email or password")
    } finally { setLoading(false) }
  }

  const features: [typeof Wrench, string][] = [[Wrench, "Report problems"], [Clock, "Track progress"], [FileText, "Answer quotes"], [Receipt, "See invoices"]]
  return (
    <div className="fmp-login" style={{ minHeight: "100vh", display: "flex", fontFamily: type.family, background: color.canvas }}>
      <style>{`@media (max-width: 860px){.fmp-login{flex-direction:column}.fmp-hero{padding:32px 24px !important;min-height:auto !important}.fmp-hero-feats{display:none !important}}`}</style>
      <section className="fmp-hero" style={{ flex: "1 1 52%", minHeight: "100vh", padding: "48px 56px", display: "flex", flexDirection: "column", justifyContent: "space-between", color: "var(--hf-text-on-solid)",
        background: `radial-gradient(circle at 20% 15%, rgba(255,255,255,0.14), transparent 45%), linear-gradient(150deg, ${color.navy}, ${color.navyDark})` }}>
        <div style={{ display: "flex", alignItems: "center", gap: space(3) }}>
          <div style={{ width: 40, height: 40, borderRadius: 11, background: "rgba(255,255,255,0.16)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 19 }}>H</div>
          <span style={{ fontWeight: 800, fontSize: 20, letterSpacing: "-0.01em" }}>HandyFlow</span>
        </div>
        <div style={{ maxWidth: 480 }}>
          <h1 style={{ fontSize: 36, fontWeight: 800, margin: 0, letterSpacing: "-0.02em", lineHeight: 1.15 }}>Facilities Management</h1>
          <div style={{ fontSize: 20, fontWeight: 600, opacity: 0.9, marginTop: 6 }}>Client Portal</div>
          <p style={{ fontSize: 15, lineHeight: 1.6, opacity: 0.85, marginTop: space(4) }}>Submit requests, track progress, answer quotes and see your invoices for the sites we look after for you, all in one place.</p>
        </div>
        <div className="fmp-hero-feats" style={{ display: "flex", gap: space(6), flexWrap: "wrap" }}>
          {features.map(([Icon, label]) => <div key={label} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8, fontSize: 12.5, fontWeight: 600, opacity: 0.9 }}>
            <span style={{ width: 42, height: 42, borderRadius: 12, border: "1px solid rgba(255,255,255,0.35)", display: "flex", alignItems: "center", justifyContent: "center" }}><Icon size={19} /></span>{label}</div>)}
        </div>
      </section>
      <section style={{ flex: "1 1 48%", display: "flex", alignItems: "center", justifyContent: "center", padding: space(6) }}>
        <div style={{ width: 400, maxWidth: "100%", background: color.surface, borderRadius: radius.lg, padding: space(8), boxShadow: shadow.modal, border: `1px solid ${color.border}` }}>
          <h2 style={{ fontSize: 22, fontWeight: 800, color: color.ink, margin: 0, letterSpacing: "-0.02em" }}>Welcome back</h2>
          <p style={{ fontSize: 13.5, color: color.muted, margin: `${space(1.5)} 0 ${space(6)}`, lineHeight: 1.5 }}>Sign in to your client portal</p>
          <form onSubmit={handleSubmit}>
            <div style={{ marginBottom: space(4) }}>
              <label htmlFor="fmp-email" style={labelStyle}>Email address</label>
              <input id="fmp-email" type="email" required autoFocus value={email} onChange={e => setEmail(e.target.value)}
                style={inputStyle} onFocus={e => (e.currentTarget.style.borderColor = color.navy)} onBlur={e => (e.currentTarget.style.borderColor = color.border)} />
            </div>
            <div style={{ marginBottom: space(5) }}>
              <label htmlFor="fmp-password" style={labelStyle}>Password</label>
              <input id="fmp-password" type="password" required value={password} onChange={e => setPassword(e.target.value)}
                style={inputStyle} onFocus={e => (e.currentTarget.style.borderColor = color.navy)} onBlur={e => (e.currentTarget.style.borderColor = color.border)} />
            </div>
            {error && <div role="alert" style={{ marginBottom: space(4), padding: `${space(2.5)} ${space(3.5)}`, background: color.redBg, border: "1px solid var(--hf-danger-border)", borderRadius: radius.sm, fontSize: 13, color: color.redText, lineHeight: 1.4 }}>{error}</div>}
            <button type="submit" disabled={loading} style={{ width: "100%", padding: `${space(3)} 0`, background: loading ? color.navyDark : color.navy,
              color: "var(--hf-text-on-solid)", border: "none", borderRadius: radius.sm, fontSize: 14, fontWeight: 700, cursor: loading ? "default" : "pointer", letterSpacing: "0.01em" }}>
              {loading ? "Signing in…" : "Sign In"}
            </button>
          </form>
          <p style={{ fontSize: 12.5, color: color.muted, margin: `${space(5)} 0 0`, textAlign: "center" }}>Need help? Contact your facilities manager.</p>
        </div>
      </section>
    </div>
  )
}

const labelStyle: React.CSSProperties = { display: "block", fontSize: 12.5, fontWeight: 600, color: color.slate, marginBottom: space(1.5) }
const inputStyle: React.CSSProperties = { width: "100%", padding: "11px 13px", border: `1.5px solid ${color.border}`, borderRadius: 8, fontSize: 14, boxSizing: "border-box", fontFamily: type.family, color: color.ink, outline: "none" }
