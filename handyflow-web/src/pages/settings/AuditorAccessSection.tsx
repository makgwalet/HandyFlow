// src/pages/settings/AuditorAccessSection.tsx
//
// Self-contained — designed to be dropped directly into SettingsPage.tsx
// as a new section/tab, same way EditClientModal etc. were built as
// standalone units this session. Not wired into SettingsPage itself
// since its real current structure hasn't been seen — add an import
// and render <AuditorAccessSection /> wherever a new settings section
// belongs in that file.
import { useEffect, useState } from "react"
import { auditorApi, type AuditorAccessGrant } from "../../api/auditor.api"

const NAVY = "#1B3A6B"
const BORDER = "#E2E8F0"
const CANVAS = "#F8FAFC"
const INK = "#0F172A"
const MUTED = "#64748B"
const FAINT = "#94A3B8"

export function AuditorAccessSection() {
  const [grants, setGrants] = useState<AuditorAccessGrant[]>([])
  const [loading, setLoading] = useState(true)
  const [email, setEmail] = useState("")
  const [businessName, setBusinessName] = useState("")
  const [inviting, setInviting] = useState(false)
  const [error, setError] = useState("")
  const [revokingId, setRevokingId] = useState<string | null>(null)

  const refetch = () => {
    setLoading(true)
    auditorApi.list().then(setGrants).finally(() => setLoading(false))
  }
  useEffect(refetch, [])

  const handleInvite = async () => {
    if (!email || !businessName) { setError("Email and business name are both required"); return }
    setInviting(true); setError("")
    try {
      await auditorApi.invite(email, businessName)
      setEmail(""); setBusinessName("")
      refetch()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to send invite")
    } finally { setInviting(false) }
  }

  const handleRevoke = async (id: string) => {
    if (!confirm("Revoke this auditor's access? They'll immediately lose the ability to view your records.")) return
    setRevokingId(id)
    try { await auditorApi.revoke(id); refetch() }
    finally { setRevokingId(null) }
  }

  return (
    <div>
      <h2 style={{ fontSize: 16, fontWeight: 800, color: INK, marginBottom: 4 }}>Auditor Access</h2>
      <p style={{ fontSize: 12.5, color: MUTED, marginBottom: 20 }}>
        Give an external auditor or accountant a read-only login to review your evidence and control exceptions directly.
      </p>

      <div style={{ background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8, padding: 16, marginBottom: 20 }}>
        <div style={{ display: "flex", gap: 10, marginBottom: 10 }}>
          <div style={{ flex: 1 }}>
            <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>Auditor's email</label>
            <input type="email" value={email} onChange={e => setEmail(e.target.value)}
              style={{ width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" as const }} />
          </div>
          <div style={{ flex: 1 }}>
            <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>
              Your business name (shown in the invite email)
            </label>
            <input value={businessName} onChange={e => setBusinessName(e.target.value)}
              style={{ width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" as const }} />
          </div>
        </div>
        {error && <div style={{ padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 6, fontSize: 12.5, color: "#DC2626", marginBottom: 10 }}>{error}</div>}
        <button onClick={handleInvite} disabled={inviting}
          style={{ padding: "7px 14px", background: NAVY, color: "#fff", border: "none", borderRadius: 6, fontSize: 12.5, fontWeight: 700, cursor: "pointer" }}>
          {inviting ? "Sending…" : "Send Invite"}
        </button>
      </div>

      {loading ? (
        <div style={{ color: FAINT, fontSize: 13 }}>Loading…</div>
      ) : grants.length === 0 ? (
        <div style={{ padding: 24, textAlign: "center" as const, color: FAINT, fontSize: 13, background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8 }}>
          No auditors invited yet.
        </div>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" as const, background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8, overflow: "hidden" }}>
          <thead>
            <tr style={{ background: CANVAS }}>
              {["Email", "Status", "Invited", ""].map(h => (
                <th key={h} style={{ textAlign: "left" as const, padding: "8px 12px", fontSize: 11, fontWeight: 700, color: MUTED, textTransform: "uppercase" as const, letterSpacing: "0.03em" }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {grants.map(g => (
              <tr key={g.id} style={{ borderTop: `1px solid ${BORDER}` }}>
                <td style={{ padding: "8px 12px", fontSize: 12.5, color: INK }}>{g.inviteEmail}</td>
                <td style={{ padding: "8px 12px", fontSize: 12.5 }}>
                  <span style={{
                    background: g.status === "ACTIVE" ? "#DCFCE7" : g.status === "REVOKED" ? "#FEF2F2" : "#FFFBEB",
                    color: g.status === "ACTIVE" ? "#166534" : g.status === "REVOKED" ? "#DC2626" : "#D97706",
                    padding: "2px 8px", borderRadius: 20, fontSize: 10.5, fontWeight: 700,
                  }}>{g.status}</span>
                </td>
                <td style={{ padding: "8px 12px", fontSize: 12.5, color: INK }}>
                  {new Date(g.invitedAt).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })}
                </td>
                <td style={{ padding: "8px 12px" }}>
                  {g.status !== "REVOKED" && (
                    <button onClick={() => handleRevoke(g.id)} disabled={revokingId === g.id}
                      style={{ padding: "6px 12px", background: "#fff", color: "#DC2626", border: "1px solid #DC2626", borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }}>
                      {revokingId === g.id ? "…" : "Revoke"}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
