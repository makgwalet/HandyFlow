// Who at the client can sign in to the client portal. The firm invites an email address; the person sets a password from the link.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, ErrorLine, Input, Pill, card, day, errorText, fail, muted, td, th } from "./kit"
import { BASE } from "./lookups"
import { GRANT_STATUS, canRevokeGrant, inviteProblem } from "./legal.logic"

interface Grant { id: string; inviteEmail: string; status: string; invitedAt: string | null; acceptedAt: string | null; revokedAt: string | null }

export default function PortalAccessPanel({ clientId, canWrite }: { clientId: string; canWrite: boolean }) {
  const qc = useQueryClient()
  const key = ["lp-portal-grants", clientId]
  const [inviting, setInviting] = useState(false), [revoking, setRevoking] = useState<Grant | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: key, queryFn: async () => (await apiClient.get<Grant[]>(`${BASE}/clients/${clientId}/portal-access`)).data })
  const rows = Array.isArray(data) ? data : []
  const revoke = useMutation({
    mutationFn: (g: Grant) => apiClient.post(`${BASE}/clients/${clientId}/portal-access/${g.id}/revoke`),
    onSuccess: () => { toast.success("Portal access revoked"); qc.invalidateQueries({ queryKey: key }); setRevoking(null) },
    onError: e => { void fail(e, "Could not revoke that"); setRevoking(null) },
  })
  return (
    <div style={{ marginTop: 14 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", margin: "0 0 6px" }}>
        <div style={{ fontSize: 13, fontWeight: 700 }}>Client portal access</div>
        {canWrite && <Btn small onClick={() => setInviting(true)}>Invite to the portal</Btn>}
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <div style={{ ...muted, padding: 14 }}>Could not load the portal access.</div> : isLoading ? <div style={{ ...muted, padding: 14 }}>Loading…</div> : rows.length === 0 ? <div style={{ ...muted, padding: 14 }}>Nobody has been invited yet.</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Email</th><th style={th}>Status</th><th style={th}>Invited</th><th /></tr></thead>
            <tbody>{rows.map(g => {
              const st = GRANT_STATUS[g.status] ?? { label: g.status, tone: "neutral" as const }
              return (
                <tr key={g.id} style={{ opacity: g.status === "REVOKED" ? 0.6 : 1 }}>
                  <td style={td}>{g.inviteEmail}</td>
                  <td style={td}><Pill tone={st.tone}>{st.label}</Pill>{g.status === "ACTIVE" && g.acceptedAt ? <div style={muted}>since {day(g.acceptedAt)}</div> : null}{g.status === "REVOKED" && g.revokedAt ? <div style={muted}>on {day(g.revokedAt)}</div> : null}</td>
                  <td style={td}>{day(g.invitedAt)}</td>
                  <td style={{ ...td, textAlign: "right" }}>{canWrite && canRevokeGrant(g.status) && <Btn small onClick={() => setRevoking(g)}>{g.status === "PENDING" ? "Cancel invite" : "Revoke"}</Btn>}</td>
                </tr>)})}</tbody>
          </table>)}
      </div>
      {inviting && <InviteDialog clientId={clientId} existing={rows} onClose={() => setInviting(false)} onDone={() => qc.invalidateQueries({ queryKey: key })} />}
      {revoking && (
        <Dialog title={revoking.status === "PENDING" ? "Cancel this invite?" : "Take away portal access?"} onClose={() => setRevoking(null)}
          footer={<><Btn onClick={() => setRevoking(null)}>Not yet</Btn><Btn kind="danger" disabled={revoke.isPending} onClick={() => revoke.mutate(revoking)}>{revoke.isPending ? "Working…" : revoking.status === "PENDING" ? "Cancel invite" : "Revoke access"}</Btn></>}>
          <div style={{ fontSize: 13, lineHeight: 1.5 }}>{revoking.inviteEmail} {revoking.status === "PENDING" ? "will no longer be able to use the invite link." : "will no longer be able to see this client's matters and invoices in the portal."}</div>
        </Dialog>)}
    </div>
  )
}

function InviteDialog({ clientId, existing, onClose, onDone }: { clientId: string; existing: Grant[]; onClose: () => void; onDone: () => void }) {
  const [email, setEmail] = useState(""), [err, setErr] = useState("")
  const problem = inviteProblem(email, existing)
  const send = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/clients/${clientId}/portal-access`, { inviteEmail: email.trim() }),
    onSuccess: () => { toast.success("Invite sent"); onDone(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not send the invite")),
  })
  return (
    <Dialog title="Invite to the client portal" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || send.isPending} onClick={() => { setErr(""); send.mutate() }}>{send.isPending ? "Sending…" : "Send invite"}</Btn></>}>
      <Input label="Their email address" type="email" value={email} onChange={setEmail} autoFocus />
      <div style={muted}>They get a link to choose a password, then can see this client's matters and invoices.</div>
      {problem && email.trim() && <div style={{ ...muted, marginTop: 6 }}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
