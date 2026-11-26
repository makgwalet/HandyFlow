import { useState } from "react"
import { useNavigate, useSearchParams } from "react-router-dom"
import { trainProvPortalClient } from "../../api/trainProvPortal.client"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { MIN_PASSWORD, passwordProblem } from "./learnerPortalRules"

export function LearnerPortalAcceptInvitePage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const token = params.get("token") ?? ""
  const setAuth = usePortalAuthStore(s => s.setAuth)
  const [fullName, setFullName] = useState("")
  const [password, setPassword] = useState("")
  const [confirm, setConfirm] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const shell = (children: React.ReactNode) => (
    <div style={{ minHeight: "100vh", background: "var(--hf-surface-sunken)", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 36, width: 400, maxWidth: "100%" }}>{children}</div>
    </div>
  )
  if (!token) return shell(<p>This invite link is missing its token. Please use the link from your invitation email.</p>)
  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const problem = passwordProblem(password, confirm)
    if (problem) { setError(problem); return }
    setBusy(true); setError("")
    try {
      const res = await trainProvPortalClient.post("/api/v1/training-provider/portal/learner/auth/register", { inviteToken: token, password, fullName: fullName.trim() })
      setAuth(res.data.token, { portalUserId: res.data.portalUserId, email: res.data.email, fullName: res.data.fullName })
      navigate("/training-provider/portal/learner/home", { replace: true })
    } catch (err: unknown) {
      setError((err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "This invite link is invalid or has expired")
    } finally { setBusy(false) }
  }
  const box = { width: "100%", padding: "10px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box", marginBottom: 12 } as const
  return shell(
    <form onSubmit={submit}>
      <h1 style={{ fontSize: 18, margin: "0 0 16px" }}>Set up your learner account</h1>
      <label htmlFor="lpa-name" style={{ fontSize: 12, fontWeight: 600 }}>Your full name</label>
      <input id="lpa-name" required value={fullName} onChange={e => setFullName(e.target.value)} style={box} />
      <label htmlFor="lpa-pw" style={{ fontSize: 12, fontWeight: 600 }}>Password (at least {MIN_PASSWORD} characters)</label>
      <input id="lpa-pw" type="password" required value={password} onChange={e => setPassword(e.target.value)} style={box} />
      <label htmlFor="lpa-confirm" style={{ fontSize: 12, fontWeight: 600 }}>Confirm password</label>
      <input id="lpa-confirm" type="password" required value={confirm} onChange={e => setConfirm(e.target.value)} style={box} />
      {error && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5 }}>{error}</p>}
      <button type="submit" disabled={busy} style={{ width: "100%", padding: 11, borderRadius: 8, border: "none", background: "var(--hf-warning-solid-strong)", color: "var(--hf-text-on-solid)", fontWeight: 700, cursor: "pointer" }}>{busy ? "Setting up…" : "Accept invite and sign in"}</button>
    </form>,
  )
}
