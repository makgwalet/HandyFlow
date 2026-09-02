// src/pages/warehousing/WhsePortalAccessTab.tsx
//
// Invite/list/revoke a client contact's portal access — direct structural
// mirror of CollAgencyPortalAccessTab, all confirmed via
// WhsePortalAdminController/WhsePortalService source (same shape:
// PortalAccessGrantResponse(id, inviteEmail, status, invitedAt,
// acceptedAt, revokedAt), InvitePortalUserRequest(email)).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { UserPlus, X, Ban, Mail } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT } from "./constants"

interface PortalAccessGrantResponse {
  id: string; inviteEmail: string; status: "PENDING" | "ACTIVE" | "REVOKED"
  invitedAt: string; acceptedAt: string | null; revokedAt: string | null
}

const STATUS_COLORS: Record<string, { bg: string; fg: string }> = {
  PENDING: { bg: "#FEF3C7", fg: "#92400E" }, ACTIVE: { bg: "#DCFCE7", fg: "#166534" }, REVOKED: { bg: "#F1F5F9", fg: "#94A3B8" },
}
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }

function InviteModal({ clientId, onClose }: { clientId: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [email, setEmail] = useState("")

  const invite = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/clients/${clientId}/portal-access`, { email }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-portal-access", clientId] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 380 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>Invite to portal</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }}>Contact email *</label>
        <input type="email" style={inputStyle} value={email} onChange={e => setEmail(e.target.value)} placeholder="contact@client.co.za" />
        {invite.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(invite.error as any)?.response?.data?.message ?? "Could not send this invite"}</p>}
        <button onClick={() => invite.mutate()} disabled={!email || invite.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!email || invite.isPending) ? 0.6 : 1 }}>
          {invite.isPending ? "Sending…" : "Send invite"}
        </button>
      </div>
    </div>
  )
}

export default function WhsePortalAccessTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const [showInvite, setShowInvite] = useState(false)

  const { data: grants = [], isLoading } = useQuery<PortalAccessGrantResponse[]>({
    queryKey: ["whse-portal-access", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/portal-access`)).data,
  })

  const revoke = useMutation({
    mutationFn: async (grantId: string) => apiClient.post(`/api/v1/warehousing/clients/${clientId}/portal-access/${grantId}/revoke`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["whse-portal-access", clientId] }),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>{grants.length} invite{grants.length === 1 ? "" : "s"}</p>
        <button onClick={() => setShowInvite(true)}
          style={{ display: "flex", alignItems: "center", gap: 6, background: WHSE_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <UserPlus size={15} /> Invite contact
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : grants.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No portal invites sent yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {grants.map((g, i) => {
            const colors = STATUS_COLORS[g.status] ?? { bg: "#F1F5F9", fg: "#64748B" }
            return (
              <div key={g.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <Mail size={14} color={WHSE_ACCENT} />
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      <p style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", margin: 0 }}>{g.inviteEmail}</p>
                      <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: colors.bg, color: colors.fg }}>{g.status}</span>
                    </div>
                    <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>Invited {g.invitedAt}{g.acceptedAt ? ` · Accepted ${g.acceptedAt}` : ""}</p>
                  </div>
                </div>
                {g.status !== "REVOKED" && (
                  <button onClick={() => { if (confirm(`Revoke portal access for ${g.inviteEmail}?`)) revoke.mutate(g.id) }} title="Revoke"
                    style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                    <Ban size={13} color="#DC2626" />
                  </button>
                )}
              </div>
            )
          })}
        </div>
      )}

      {showInvite && <InviteModal clientId={clientId} onClose={() => setShowInvite(false)} />}
    </div>
  )
}
