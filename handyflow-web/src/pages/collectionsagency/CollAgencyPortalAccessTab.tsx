// src/pages/collectionsagency/CollAgencyPortalAccessTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, X, Mail, ShieldOff } from "lucide-react"
import { apiClient } from "../../api/client"
import { CA_ACCENT } from "./constants"

interface PortalAccessGrantResponse {
  id: string; inviteEmail: string; status: "PENDING" | "ACTIVE" | "REVOKED"
  invitedAt: string; acceptedAt: string | null; revokedAt: string | null
}

const STATUS_COLORS: Record<string, { bg: string; fg: string }> = {
  PENDING: { bg: "#FEF3C7", fg: "#92400E" }, ACTIVE: { bg: "#DCFCE7", fg: "#166534" }, REVOKED: { bg: "#F1F5F9", fg: "#64748B" },
}

function InviteModal({ clientId, onClose }: { clientId: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [email, setEmail] = useState("")
  const save = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/collections-agency/clients/${clientId}/portal-access`, { email }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ca-portal-access", clientId] }); onClose() },
  })
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 300 }} onClick={onClose}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 26, width: 400 }} onClick={e => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <h3 style={{ fontSize: 15, fontWeight: 700, color: "#0F172A", margin: 0 }}>Invite a client contact</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={17} color="#94A3B8" /></button>
        </div>
        <p style={{ fontSize: 12, color: "#94A3B8", margin: "0 0 12px" }}>They'll be able to log in and view their placed portfolio and trust/remittance statement. The invite link expires after 7 days.</p>
        <input type="email" placeholder="contact@client.co.za" style={{ width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} value={email} onChange={e => setEmail(e.target.value)} />
        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 10 }}>{(save.error as any)?.response?.data?.message ?? "Could not send this invite"}</p>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 18 }}>
          <button onClick={onClose} style={{ padding: "9px 16px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 13, cursor: "pointer" }}>Cancel</button>
          <button onClick={() => save.mutate()} disabled={!email || save.isPending}
            style={{ padding: "9px 18px", borderRadius: 8, border: "none", background: CA_ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            {save.isPending ? "Sending…" : "Send invite"}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function CollAgencyPortalAccessTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const [showInvite, setShowInvite] = useState(false)
  const { data: grants = [], isLoading } = useQuery<PortalAccessGrantResponse[]>({
    queryKey: ["ca-portal-access", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/collections-agency/clients/${clientId}/portal-access`)).data,
  })
  const revoke = useMutation({
    mutationFn: async (grantId: string) => apiClient.post(`/api/v1/collections-agency/clients/${clientId}/portal-access/${grantId}/revoke`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ca-portal-access", clientId] }),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#64748B", margin: 0 }}>{grants.length} portal invite{grants.length === 1 ? "" : "s"}</p>
        <button onClick={() => setShowInvite(true)} style={{ display: "flex", alignItems: "center", gap: 6, background: CA_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "8px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> Invite client contact
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : grants.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No one has been invited to this client's portal yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {grants.map((g, i) => {
            const colors = STATUS_COLORS[g.status]
            return (
              <div key={g.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <Mail size={15} color="#94A3B8" />
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      <p style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", margin: 0 }}>{g.inviteEmail}</p>
                      <span style={{ fontSize: 10.5, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: colors.bg, color: colors.fg }}>{g.status}</span>
                    </div>
                    <p style={{ fontSize: 11.5, color: "#94A3B8", margin: "2px 0 0" }}>
                      Invited {new Date(g.invitedAt).toLocaleDateString("en-ZA")}
                      {g.acceptedAt ? ` · Accepted ${new Date(g.acceptedAt).toLocaleDateString("en-ZA")}` : ""}
                      {g.revokedAt ? ` · Revoked ${new Date(g.revokedAt).toLocaleDateString("en-ZA")}` : ""}
                    </p>
                  </div>
                </div>
                {g.status !== "REVOKED" && (
                  <button onClick={() => revoke.mutate(g.id)} style={{ display: "flex", alignItems: "center", gap: 5, background: "none", border: "1px solid #FECACA", borderRadius: 8, padding: "6px 12px", fontSize: 12, fontWeight: 600, color: "#DC2626", cursor: "pointer" }}>
                    <ShieldOff size={13} /> Revoke
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
