// src/pages/trainingprovider-portal/TrainProvPortalAcceptInvitePage.tsx
//
// Reads the invite token from the URL query string (?token=...) — same
// convention as every sibling portal this session. Posts to
// /api/v1/training-provider/portal/auth/register, confirmed via
// TrainProvPortalAuthController source
// (PortalRegisterRequest{inviteToken, password, fullName}).
import { useState } from "react"
import { useNavigate, useSearchParams, Link } from "react-router-dom"
import { apiClient } from "../../api/client"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { GraduationCap } from "lucide-react"

const ACCENT = "#B45309"

export function TrainProvPortalAcceptInvitePage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const token = params.get("token") ?? ""
  const portalAuth = usePortalAuthStore()

  const [fullName, setFullName] = useState("")
  const [password, setPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const applyPortalAuth = (t: string, user: { id: string; email: string; fullName: string }) => {
    (portalAuth as any).login?.(t, user) ?? (portalAuth as any).setAuth?.(t, user)
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (password !== confirmPassword) { setError("Passwords don't match"); return }
    if (!token) { setError("This invite link is missing its token — please use the link from your invite email"); return }
    setLoading(true)
    try {
      const res = await apiClient.post("/api/v1/training-provider/portal/auth/register", { inviteToken: token, password, fullName })
      const { token: authToken, id, email, fullName: respName } = res.data
      applyPortalAuth(authToken, { id, email, fullName: respName })
      navigate("/training-provider/portal")
    } catch (err: any) {
      setError(err?.response?.data?.message ?? "This invite link is invalid or has expired")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: "100vh", background: "#F1F5F9", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 36, width: 380, boxShadow: "0 4px 24px rgba(0,0,0,0.06)" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 10 }}>
          <div style={{ width: 38, height: 38, borderRadius: 10, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <GraduationCap size={19} color="#fff" />
          </div>
          <div>
            <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>Accept your invite</p>
            <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>Training Provider Client Portal</p>
          </div>
        </div>

        {!token && (
          <p style={{ fontSize: 12, color: "#DC2626", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, padding: 10, margin: "14px 0" }}>
            This link is missing its invite token. Please use the exact link from your invite email.
          </p>
        )}

        <form onSubmit={submit} style={{ marginTop: 16 }}>
          <div style={{ marginBottom: 14 }}>
            <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }}>Your full name</label>
            <input required value={fullName} onChange={e => setFullName(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} />
          </div>
          <div style={{ marginBottom: 14 }}>
            <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }}>Choose a password</label>
            <input type="password" required minLength={8} value={password} onChange={e => setPassword(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} />
          </div>
          <div style={{ marginBottom: 18 }}>
            <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }}>Confirm password</label>
            <input type="password" required value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)}
              style={{ width: "100%", padding: "10px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} />
          </div>

          {error && <p style={{ color: "#DC2626", fontSize: 12.5, marginBottom: 14 }}>{error}</p>}

          <button type="submit" disabled={loading || !token}
            style={{ width: "100%", padding: "11px", borderRadius: 8, border: "none", background: ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (loading || !token) ? 0.6 : 1 }}>
            {loading ? "Creating account…" : "Create account & sign in"}
          </button>
        </form>

        <p style={{ fontSize: 12, color: "#94A3B8", textAlign: "center", marginTop: 18 }}>
          Already have an account? <Link to="/training-provider/portal/login" style={{ color: ACCENT, fontWeight: 600 }}>Sign in</Link>
        </p>
      </div>
    </div>
  )
}
