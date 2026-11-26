import { useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import { GraduationCap } from "lucide-react"
import { trainProvPortalClient } from "../../api/trainProvPortal.client"
import { usePortalAuthStore } from "../../store/portalAuth.store"

export function LearnerPortalLoginPage() {
  const navigate = useNavigate()
  const setAuth = usePortalAuthStore(s => s.setAuth)
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(""); setBusy(true)
    try {
      const res = await trainProvPortalClient.post("/api/v1/training-provider/portal/learner/auth/login", { email, password })
      setAuth(res.data.token, { portalUserId: res.data.portalUserId, email: res.data.email, fullName: res.data.fullName })
      navigate("/training-provider/portal/learner/home", { replace: true })
    } catch (err: unknown) {
      setError((err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Invalid email or password")
    } finally { setBusy(false) }
  }
  const box = { width: "100%", padding: "10px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" } as const
  return (
    <div style={{ minHeight: "100vh", background: "var(--hf-surface-sunken)", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 36, width: 380, boxShadow: "0 4px 24px rgba(0,0,0,0.06)" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 24 }}>
          <GraduationCap size={22} />
          <div><p style={{ fontSize: 15, fontWeight: 800, margin: 0 }}>Learner portal</p><p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>Your own training records</p></div>
        </div>
        <form onSubmit={submit}>
          <label htmlFor="lp-email" style={{ fontSize: 12, fontWeight: 600 }}>Email</label>
          <input id="lp-email" type="email" required value={email} onChange={e => setEmail(e.target.value)} style={{ ...box, marginBottom: 14 }} />
          <label htmlFor="lp-pw" style={{ fontSize: 12, fontWeight: 600 }}>Password</label>
          <input id="lp-pw" type="password" required value={password} onChange={e => setPassword(e.target.value)} style={{ ...box, marginBottom: 18 }} />
          {error && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5 }}>{error}</p>}
          <button type="submit" disabled={busy} style={{ width: "100%", padding: 11, borderRadius: 8, border: "none", background: "var(--hf-warning-solid-strong)", color: "var(--hf-text-on-solid)", fontWeight: 700, cursor: "pointer" }}>{busy ? "Signing in…" : "Sign in"}</button>
        </form>
        <p style={{ fontSize: 12, color: "var(--hf-text-faint)", textAlign: "center", marginTop: 18 }}>
          Received an invite email? <Link to="/training-provider/portal/learner/accept-invite">Accept it here</Link>
        </p>
      </div>
    </div>
  )
}
