// The client contacts who may sign in to the client portal for one client.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pill, ReasonDialog, card, errorText, fail, muted, td, th, when } from "./kit"
import { BASE } from "./lookups"
import { GRANT_STATUS, grantCanRevoke, inviteProblem } from "./fm.logic"

interface Grant { id: string; inviteEmail: string; status: string; acceptedAt: string | null; invitedAt: string | null }

export default function PortalUsersDialog({ clientId, clientName, canInvite, canRevoke, onClose }: { clientId: string; clientName: string; canInvite: boolean; canRevoke: boolean; onClose: () => void }) {
  const qc = useQueryClient(), key = ["fm-portal-users", clientId]
  const [email, setEmail] = useState(""), [err, setErr] = useState(""), [revoking, setRevoking] = useState<Grant | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: key, queryFn: async () => (await apiClient.get<Grant[]>(`${BASE}/clients/${clientId}/portal-users`)).data })
  const invite = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/clients/${clientId}/portal-users`, { clientId, email: email.trim() }),
    onSuccess: () => { toast.success("Invitation sent"); setEmail(""); qc.invalidateQueries({ queryKey: key }) }, onError: async e => setErr(await errorText(e, "Could not send the invitation")),
  })
  const rows = Array.isArray(data) ? data : [], problem = inviteProblem(email)
  return (
    <Dialog title={`Portal access for ${clientName}`} onClose={onClose} width={620} footer={<Btn onClick={onClose}>Close</Btn>}>
      <div style={{ ...muted, marginBottom: 10 }}>Contacts invited here can sign in to see this client's sites, raise fault reports, answer quotes and see invoices.</div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the portal users.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>Nobody has been invited yet.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Contact</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(g => { const s = GRANT_STATUS[g.status] ?? { label: g.status, tone: "neutral" as const }
              return <tr key={g.id} style={{ opacity: g.status === "REVOKED" ? 0.6 : 1 }}><td style={td}>{g.inviteEmail}<div style={muted}>{g.acceptedAt ? `Joined ${when(g.acceptedAt)}` : g.invitedAt ? `Invited ${when(g.invitedAt)}` : ""}</div></td><td style={td}><Pill tone={s.tone}>{s.label}</Pill></td>
                <td style={{ ...td, textAlign: "right" }}>{grantCanRevoke(g.status, canRevoke) && <Btn small onClick={() => setRevoking(g)}>Revoke</Btn>}</td></tr> })}</tbody>
          </table>)}
      </div>
      {canInvite && (
        <div style={{ marginTop: 12 }}>
          <Input label="Invite a contact" type="email" value={email} onChange={v => { setEmail(v); setErr("") }} hint="They get an email with a link to set a password" />
          {email && problem && <div style={muted}>{problem}</div>}
          {err && <ErrorLine text={err} />}
          <Btn kind="primary" disabled={!!problem || invite.isPending} onClick={() => { setErr(""); invite.mutate() }}>{invite.isPending ? "Sending…" : "Send invitation"}</Btn>
        </div>)}
      {revoking && <ReasonDialog title={`Revoke ${revoking.inviteEmail}?`} label="Note" required={false} confirm="Revoke access" danger intro="They will no longer be able to sign in for this client." onClose={() => setRevoking(null)}
        onSubmit={async () => { try { await apiClient.post(`${BASE}/clients/${clientId}/portal-users/${revoking.id}/revoke`); toast.success("Access revoked"); qc.invalidateQueries({ queryKey: key }) } catch (e) { void fail(e, "Could not revoke"); throw e } }} />}
    </Dialog>
  )
}
