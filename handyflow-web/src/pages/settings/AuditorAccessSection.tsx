// src/pages/settings/AuditorAccessSection.tsx
//
// Self-contained — designed to be dropped directly into SettingsPage.tsx
// as a new section/tab, same way EditClientModal etc. were built as
// standalone units this session. Not wired into SettingsPage itself
// since its real current structure hasn't been seen — add an import
// and render <AuditorAccessSection /> wherever a new settings section
// belongs in that file.
import { useEffect, useState } from "react"
import { auditorApi, type AuditorAccessGrant, type AuditEngagement } from "../../api/auditor.api"
import { EngagementsPanel } from "./EngagementsPanel"

const NAVY = "var(--hf-primary)"
const BORDER = "var(--hf-border)"
const CANVAS = "var(--hf-surface-muted)"
const INK = "var(--hf-text)"
const MUTED = "var(--hf-text-muted)"
const FAINT = "var(--hf-text-faint)"

/** Default access period offered in the invite form: 90 days from today, as yyyy-MM-dd. */
export const defaultExpiry = (from = new Date()) => { const d = new Date(from); d.setDate(d.getDate() + 90); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }
const dateText = (iso?: string | null) => (iso ? new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "No end date")

export function AuditorAccessSection() {
  const [grants, setGrants] = useState<AuditorAccessGrant[]>([])
  const [loading, setLoading] = useState(true)
  const [email, setEmail] = useState("")
  const [businessName, setBusinessName] = useState("")
  const [inviting, setInviting] = useState(false)
  const [error, setError] = useState("")
  const [revokingId, setRevokingId] = useState<string | null>(null)
  const [expiresOn, setExpiresOn] = useState(defaultExpiry())
  const [scopeNote, setScopeNote] = useState("")
  const [engagements, setEngagements] = useState<AuditEngagement[]>([])
  const [engagementId, setEngagementId] = useState("")
  const [redating, setRedating] = useState<string | null>(null)
  const [newDate, setNewDate] = useState(defaultExpiry())

  const refetch = () => {
    setLoading(true)
    auditorApi.list().then(setGrants).finally(() => setLoading(false))
    auditorApi.engagements().then(setEngagements).catch(() => setEngagements([]))
  }
  useEffect(refetch, [])

  const handleInvite = async () => {
    if (!email || !businessName) { setError("Email and business name are both required"); return }
    setInviting(true); setError("")
    try {
      await auditorApi.invite(email, businessName, expiresOn, scopeNote, engagementId || undefined)
      setEmail(""); setBusinessName(""); setScopeNote(""); setEngagementId(""); setExpiresOn(defaultExpiry())
      refetch()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to send invite")
    } finally { setInviting(false) }
  }

  const handleRedate = async (id: string) => {
    setError("")
    try { await auditorApi.changeExpiry(id, newDate); setRedating(null); refetch() }
    catch (e: any) { setError(e.response?.data?.message ?? "Failed to change the date") }
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

      <EngagementsPanel engagements={engagements} onChange={refetch} />

      <div style={{ background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 8, padding: 16, marginBottom: 20 }}>
        <div style={{ display: "flex", gap: 10, marginBottom: 10 }}>
          <div style={{ flex: 1 }}>
            <label htmlFor="aud-email" style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>Auditor's email</label>
            <input id="aud-email" type="email" value={email} onChange={e => setEmail(e.target.value)}
              style={{ width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" as const }} />
          </div>
          <div style={{ flex: 1 }}>
            <label htmlFor="aud-biz" style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>
              Your business name (shown in the invite email)
            </label>
            <input id="aud-biz" value={businessName} onChange={e => setBusinessName(e.target.value)}
              style={{ width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" as const }} />
          </div>
        </div>
        <div style={{ display: "flex", gap: 10, marginBottom: 10 }}>
          <div style={{ width: 190 }}>
            <label htmlFor="aud-expiry" style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>Access ends on</label>
            <input id="aud-expiry" type="date" value={expiresOn} onChange={e => setExpiresOn(e.target.value)}
              style={{ width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" as const }} />
          </div>
          <div style={{ flex: 1 }}>
            <label htmlFor="aud-scope" style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>What they are auditing (optional)</label>
            <input id="aud-scope" value={scopeNote} onChange={e => setScopeNote(e.target.value)} placeholder="e.g. Year-end audit, FY2026"
              style={{ width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" as const }} />
          </div>
        </div>
        <div style={{ marginBottom: 10 }}>
          <label htmlFor="aud-eng" style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>Engagement (optional)</label>
          <select id="aud-eng" value={engagementId} onChange={e => setEngagementId(e.target.value)}
            style={{ width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" as const }}>
            <option value="">Not part of an engagement</option>
            {engagements.filter(g => g.status === "PLANNED" || g.status === "ACTIVE").map(g => <option key={g.id} value={g.id}>{g.name} — {g.firmName}</option>)}
          </select>
        </div>
        <div style={{ fontSize: 12, color: FAINT, marginBottom: 10 }}>The auditor sees only the items you choose to share with them. Access stops on the date above unless you extend it.</div>
        {error && <div style={{ padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 6, fontSize: 12.5, color: "var(--hf-danger-text)", marginBottom: 10 }}>{error}</div>}
        <button onClick={handleInvite} disabled={inviting}
          style={{ padding: "7px 14px", background: NAVY, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 6, fontSize: 12.5, fontWeight: 700, cursor: "pointer" }}>
          {inviting ? "Sending…" : "Send Invite"}
        </button>
      </div>

      {loading ? (
        <div style={{ color: FAINT, fontSize: 13 }}>Loading…</div>
      ) : grants.length === 0 ? (
        <div style={{ padding: 24, textAlign: "center" as const, color: FAINT, fontSize: 13, background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 8 }}>
          No auditors invited yet.
        </div>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" as const, background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 8, overflow: "hidden" }}>
          <thead>
            <tr style={{ background: CANVAS }}>
              {["Email", "Status", "Access ends", "Audit", ""].map(h => (
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
                    background: g.status === "ACTIVE" ? "var(--hf-success-soft-strong)" : g.status === "REVOKED" ? "var(--hf-danger-soft)" : "var(--hf-warning-soft)",
                    color: g.status === "ACTIVE" ? "var(--hf-success-text-strong)" : g.status === "REVOKED" ? "var(--hf-danger-text)" : "var(--hf-warning-text)",
                    padding: "2px 8px", borderRadius: 20, fontSize: 10.5, fontWeight: 700,
                  }}>{g.expired && g.status === "ACTIVE" ? "EXPIRED" : g.status}</span>
                </td>
                <td style={{ padding: "8px 12px", fontSize: 12.5, color: g.expired ? "var(--hf-danger-text)" : INK }}>
                  {g.status === "REVOKED" ? "—" : redating === g.id ? (
                    <span style={{ display: "inline-flex", gap: 6 }}>
                      <input type="date" aria-label="New end date" value={newDate} onChange={e => setNewDate(e.target.value)} style={{ padding: "4px 6px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 12 }} />
                      <button onClick={() => handleRedate(g.id)} style={{ padding: "4px 10px", background: NAVY, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }}>Save</button>
                    </span>
                  ) : (<>{g.expired ? "Expired " : ""}{dateText(g.expiresAt)}{" "}
                    <button onClick={() => { setRedating(g.id); setNewDate(defaultExpiry()) }} style={{ background: "none", border: "none", color: NAVY, cursor: "pointer", fontSize: 12, fontWeight: 600 }}>Change</button></>)}
                </td>
                <td style={{ padding: "8px 12px", fontSize: 12.5, color: MUTED }}>{g.scopeNote ?? "—"}</td>
                <td style={{ padding: "8px 12px" }}>
                  {g.status !== "REVOKED" && (
                    <button onClick={() => handleRevoke(g.id)} disabled={revokingId === g.id}
                      style={{ padding: "6px 12px", background: "var(--hf-surface)", color: "var(--hf-danger-text)", border: "1px solid var(--hf-danger)", borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }}>
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
