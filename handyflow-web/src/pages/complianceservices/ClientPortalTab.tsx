// src/pages/complianceservices/ClientPortalTab.tsx
//
// Who at the client can sign in to the portal to see their sent quotes, live agreements and issued invoices. The invite
// goes to the person by email and the link is never shown here. Removing access takes effect on their next request.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission, usePermissions } from "../../hooks/usePermission"
import { emailProblem, emailNote, ordered, statusLabel, dayOf, type Grant } from "./portalRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const button: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const bad: React.CSSProperties = { fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }

export default function ClientPortalTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canInvite = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const canRevoke = usePermission("COMPLIANCE_SERVICES_ADMIN")
  const base = `/api/v1/compliance-services/clients/${clientId}/portal-users`
  const [email, setEmail] = useState("")
  const [confirming, setConfirming] = useState<string | null>(null)
  const [error, setError] = useState("")

  const { data: grants = [] } = useQuery<Grant[]>({ queryKey: ["cs-portal", clientId], queryFn: async () => unwrap<Grant[]>(await apiClient.get(base)) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["cs-portal", clientId] })
  const invite = useMutation({
    mutationFn: async () => unwrap<Grant>(await apiClient.post(base, { email: email.trim() })),
    onSuccess: () => { setError(""); setEmail(""); refresh() }, onError: (e: unknown) => setError(errText(e)),
  })
  const revoke = useMutation({
    mutationFn: async (id: string) => unwrap<Grant>(await apiClient.post(`${base}/${id}/revoke`, {})),
    onSuccess: () => { setError(""); setConfirming(null); refresh() }, onError: (e: unknown) => setError(errText(e)),
  })
  const problem = email.trim() ? emailProblem(email) : null

  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
        People at this client who can sign in to the client portal. They see the quotes you have sent them, their agreements and their issued invoices, and nothing else. The invite is emailed to them.
      </div>
      {error && <div role="alert" style={bad}>{error}</div>}

      {canInvite && (
        <div style={{ display: "flex", gap: 8, alignItems: "end", flexWrap: "wrap" }}>
          <div style={{ flex: 1, minWidth: 220 }}>
            <label htmlFor="portal-email" style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }}>Invite someone by email</label>
            <input id="portal-email" type="email" style={input} value={email} onChange={e => setEmail(e.target.value)} />
            {problem && <div style={bad}>{problem}</div>}
          </div>
          <button style={{ ...button, opacity: !email.trim() || problem || invite.isPending ? 0.5 : 1 }} disabled={!email.trim() || !!problem || invite.isPending} onClick={() => invite.mutate()}>Send invite</button>
        </div>
      )}

      {grants.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No one has been invited yet.</div>}
      {ordered(grants).map(g => (
        <div key={g.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "8px 10px", display: "grid", gap: 4 }}>
          <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
            <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{g.email}</span>
            <span style={{ flex: 1 }} />
            <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
              {statusLabel(g.status)}
              {g.status === "ACTIVE" && g.acceptedAt ? ` since ${dayOf(g.acceptedAt)}` : ""}
              {g.status === "PENDING" && g.inviteExpiresAt ? ` · link expires ${dayOf(g.inviteExpiresAt)}` : ""}
              {g.status === "REVOKED" && g.revokedAt ? ` on ${dayOf(g.revokedAt)}` : ""}
            </span>
          </div>
          {emailNote(g) && <div style={{ fontSize: 12, color: "var(--hf-warning-text, #8a5a00)" }}>{emailNote(g)}</div>}
          {canRevoke && g.status !== "REVOKED" && (confirming === g.id
            ? (
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                <span style={{ fontSize: 12, color: "var(--hf-text)" }}>{g.status === "ACTIVE" ? "They will lose access on their next request." : "The invite link will stop working."}</span>
                <button style={button} disabled={revoke.isPending} onClick={() => revoke.mutate(g.id)}>Yes, remove</button>
                <button style={ghost} onClick={() => setConfirming(null)}>Keep</button>
              </div>
            )
            : <div><button style={ghost} onClick={() => setConfirming(g.id)}>{g.status === "ACTIVE" ? "Remove access" : "Cancel invite"}</button></div>)}
        </div>
      ))}
    </div>
  )
}
