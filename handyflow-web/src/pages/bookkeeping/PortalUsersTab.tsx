// Who at the client may see their invoices and time in the client portal.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { dateText, errorOf, rowsOf } from "./bookkeeping.logic"
import type { Grant } from "./practice.logic"
import ClientScope from "./ClientScope"
import { btn, card, danger, field, muted, primary, td, th } from "./ui"

export default function PortalUsersTab() { return <ClientScope>{id => <Portal clientId={id} />}</ClientScope> }

const STATUS: Record<string, string> = { PENDING: "Invited", ACTIVE: "Active", REVOKED: "Access removed" }
const looksLikeEmail = (s: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s.trim())

function Portal({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("BOOKKEEPING_MANAGE") || usePermission("BOOKKEEPING_ADMIN")
  const isAdmin = usePermission("BOOKKEEPING_ADMIN")
  const base = `/api/v1/bookkeeping/clients/${clientId}/portal-users`
  const [email, setEmail] = useState(""), [error, setError] = useState<string | null>(null), [sent, setSent] = useState<string | null>(null)
  const { data = [] } = useQuery<Grant[]>({ queryKey: ["bk-portal", clientId], queryFn: async () => rowsOf<Grant>(await apiClient.get(base)) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["bk-portal"] })
  const invite = useMutation({ mutationFn: () => apiClient.post(base, { clientId, email: email.trim() }), onSuccess: () => { setError(null); setSent(email.trim()); setEmail(""); refresh() }, onError: e => { setSent(null); setError(errorOf(e)) } })
  const revoke = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/${id}/revoke`), onSuccess: refresh, onError: e => setError(errorOf(e)) })
  return (
    <div>
      {canManage && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>Invite someone at the client</h3>
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <input aria-label="Email address" placeholder="name@client.co.za" value={email} onChange={e => setEmail(e.target.value)} style={{ ...field, width: 260 }} />
            <button style={primary} disabled={!looksLikeEmail(email) || invite.isPending} onClick={() => invite.mutate()}>Send invite</button>
          </div>
          {sent && <div role="status" style={{ ...muted, marginTop: 6 }}>Invite sent to {sent}.</div>}
          {error && <div role="alert" style={{ ...danger, marginTop: 6 }}>{error}</div>}
        </div>
      )}
      <div style={card}>
        {data.length === 0 && <p style={muted}>No one has portal access yet.</p>}
        {data.length > 0 && (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={th}>Email</th><th style={th}>Invited</th><th style={th}>Joined</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{data.map(g => (
              <tr key={g.id} style={{ borderTop: "1px solid var(--hf-border)", opacity: g.status === "REVOKED" ? 0.55 : 1 }}>
                <td style={td}>{g.inviteEmail}</td><td style={td}>{dateText(g.invitedAt)}</td><td style={td}>{g.acceptedAt ? dateText(g.acceptedAt) : "—"}</td><td style={td}>{STATUS[g.status] ?? g.status}</td>
                <td style={{ ...td, textAlign: "right" }}>{isAdmin && g.status !== "REVOKED" && <button style={btn} onClick={() => { if (window.confirm(`Remove ${g.inviteEmail}'s access?`)) revoke.mutate(g.id) }}>Remove access</button>}</td>
              </tr>))}</tbody>
          </table>
        )}
      </div>
    </div>
  )
}
