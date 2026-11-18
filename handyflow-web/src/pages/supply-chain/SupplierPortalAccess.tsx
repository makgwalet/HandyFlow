// src/pages/supply-chain/SupplierPortalAccess.tsx — who at a supplier may log in to quote on RFQs online.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { inp, ErrBox, Banner, filterPill, fmtDate } from "./scm.shared"
import { asList, errorText } from "./scmOps"
import { Pill } from "./scmUi"
import { inviteProblem, grantStatusTone } from "./scmLimits"

interface Grant { id: string; email: string; status: string; invitedAt: string; acceptedAt: string | null; inviteExpiresAt: string | null }

export function SupplierPortalAccess({ supplierId }: { supplierId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["SCM_SUPPLIER_MANAGE", "SCM_ADMIN"])
  const [email, setEmail] = useState("")
  const [err, setErr] = useState("")
  const key = ["scm-supplier-portal", supplierId]
  const list = useQuery({ queryKey: key, queryFn: () => apiClient.get(`/api/v1/supply-chain/suppliers/${supplierId}/portal-access`).then(asList<Grant>) })
  const done = { onSuccess: () => { setErr(""); qc.invalidateQueries({ queryKey: key }) }, onError: (e: unknown) => setErr(errorText(e)) }
  const invite = useMutation({ mutationFn: () => apiClient.post(`/api/v1/supply-chain/suppliers/${supplierId}/portal-access`, { email: email.trim() }), ...done, onSuccess: () => { setEmail(""); setErr(""); qc.invalidateQueries({ queryKey: key }) } })
  const resend = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/supply-chain/supplier-portal-access/${id}/resend`), ...done })
  const revoke = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/supply-chain/supplier-portal-access/${id}`), ...done })
  const send = () => { const p = inviteProblem(email); if (p) { setErr(p); return } invite.mutate() }
  const rows = (list.data ?? []).filter(g => g.status !== "REVOKED")

  return (
    <div style={{ marginTop: 16, paddingTop: 12, borderTop: "1px solid var(--hf-border)" }}>
      <strong style={{ fontSize: 13 }}>Supplier portal access</strong>
      <p style={{ fontSize: 12, margin: "4px 0 8px", color: "var(--hf-text-faint)" }}>People here can log in, see only the RFQs this supplier was invited to, and submit or revise their own quotation while the RFQ is open.</p>
      {err && <ErrBox msg={err} />}
      {list.isError && <ErrBox msg={errorText(list.error, "Could not load portal access")} />}
      {rows.length === 0 && !list.isLoading && <Banner variant="info">Nobody at this supplier has portal access yet.</Banner>}
      <ul style={{ listStyle: "none", margin: "0 0 8px", padding: 0, display: "grid", gap: 6, fontSize: 13 }}>
        {rows.map(g => (
          <li key={g.id} style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <span>{g.email}</span><Pill tone={grantStatusTone(g.status)}>{g.status === "PENDING" ? "Invited" : "Active"}</Pill>
            <small>{g.status === "PENDING" && g.inviteExpiresAt ? `link valid until ${fmtDate(g.inviteExpiresAt)}` : g.acceptedAt ? `joined ${fmtDate(g.acceptedAt)}` : ""}</small>
            {canManage && g.status === "PENDING" && <button style={filterPill(false)} disabled={resend.isPending} onClick={() => resend.mutate(g.id)}>Send again</button>}
            {canManage && <button style={filterPill(false)} disabled={revoke.isPending} onClick={() => revoke.mutate(g.id)}>Revoke</button>}
          </li>))}
      </ul>
      {canManage && (
        <div style={{ display: "flex", gap: 8, maxWidth: 480 }}>
          <input style={inp} type="email" aria-label="Invite email" placeholder="name@supplier.co.za" value={email} onChange={e => setEmail(e.target.value)} />
          <button style={filterPill(true)} disabled={invite.isPending} onClick={send}>Invite</button>
        </div>)}
    </div>
  )
}
