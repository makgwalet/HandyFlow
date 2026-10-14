// src/pages/accountant/ClientSignoffPanel.tsx
// Client team and financial-year sign-off, shown in the client workspace (finance F15).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { allowedActions, reasonOk, rolesLeftFor, failureText, ROLE_LABEL, STATUS_LABEL, type ActionSpec } from "./clientSignoff.logic"

const unwrap = (r: any) => r.data?.data ?? r.data
const fmtDT = (d: any) => d ? new Date(d).toLocaleString("en-ZA") : ""
const box: React.CSSProperties = { padding: "9px 12px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }
const btn: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", fontSize: 12, fontWeight: 600, cursor: "pointer" }

export default function ClientSignoffPanel({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const can = (p: string) => (permissions[p] ?? false)
  const canWrite = usePermission("ACCOUNTANT_WRITE")
  const canSign = usePermission("ACCOUNTANT_SIGNOFF")
  const canAssign = usePermission("ACCOUNTANT_ASSIGN")
  const permissions: Record<string, boolean> = { ACCOUNTANT_WRITE: canWrite, ACCOUNTANT_SIGNOFF: canSign }

  const base = `/api/v1/accountant/clients/${clientId}`
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState<{ year: number; spec: ActionSpec } | null>(null)
  const [reason, setReason] = useState("")
  const [userId, setUserId] = useState("")
  const [role, setRole] = useState("PREPARER")

  const { data: team = [] } = useQuery<any[]>({
    queryKey: ["acc-client-team", clientId],
    queryFn: async () => unwrap(await apiClient.get(`${base}/assignments`)) ?? [],
  })
  const { data: years = [], isLoading } = useQuery<any[]>({
    queryKey: ["acc-client-engagements", clientId],
    queryFn: async () => unwrap(await apiClient.get(`${base}/engagements`)) ?? [],
  })
  const { data: users = [] } = useQuery<any[]>({
    queryKey: ["acc-staff-options"],
    enabled: canAssign,
    queryFn: async () => { const d = (await apiClient.get("/api/v1/identity/users?size=200")).data; return d?.content ?? d?.data?.content ?? d?.data ?? d ?? [] },
  })

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["acc-client-team", clientId] })
    qc.invalidateQueries({ queryKey: ["acc-client-engagements", clientId] })
  }
  const onError = (e: any) => setError(failureText(e))

  const act = useMutation({
    mutationFn: ({ year, spec, note }: { year: number; spec: ActionSpec; note: string }) =>
      apiClient.post(`${base}/engagements/${year}/${spec.action}`, { note: note || null }),
    onSuccess: () => { setError(null); setPending(null); setReason(""); refresh() },
    onError,
  })
  const assign = useMutation({
    mutationFn: () => apiClient.post(`${base}/assignments`, { userId, role }),
    onSuccess: () => { setError(null); setUserId(""); refresh() },
    onError,
  })
  const unassign = useMutation({
    mutationFn: (id: string) => apiClient.delete(`${base}/assignments/${id}`),
    onSuccess: () => { setError(null); refresh() },
    onError,
  })

  const start = (year: number, spec: ActionSpec) => {
    if (spec.needsReason) { setPending({ year, spec }); setReason("") }
    else act.mutate({ year, spec, note: "" })
  }
  const roleChoices = userId ? rolesLeftFor(userId, team) : Object.keys(ROLE_LABEL)

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
      {error && <div role="alert" style={{ ...box, color: "var(--hf-danger-text)", background: "var(--hf-danger-soft)" }}>{error}</div>}

      <div>
        <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text-secondary)", marginBottom: 8 }}>Team</div>
        {team.length === 0 ? (
          <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Nobody is assigned to this client.</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
            {team.map((a: any) => (
              <div key={a.id} style={{ ...box, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <span>{a.userName} · {ROLE_LABEL[a.role] ?? a.role}</span>
                {canAssign && <button style={btn} disabled={unassign.isPending} onClick={() => unassign.mutate(a.id)} aria-label={`Remove ${a.userName}`}>Remove</button>}
              </div>
            ))}
          </div>
        )}
        {canAssign && (
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <select aria-label="Person" value={userId} onChange={e => setUserId(e.target.value)} style={{ flex: 1, padding: 7, fontSize: 12, borderRadius: 7, border: "1px solid var(--hf-border)" }}>
              <option value="">Choose a person…</option>
              {users.map((u: any) => <option key={u.id} value={u.id}>{u.firstName} {u.lastName}</option>)}
            </select>
            <select aria-label="Role" value={role} onChange={e => setRole(e.target.value)} style={{ padding: 7, fontSize: 12, borderRadius: 7, border: "1px solid var(--hf-border)" }}>
              {roleChoices.map(r => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
            </select>
            <button style={btn} disabled={!userId || !roleChoices.includes(role) || assign.isPending} onClick={() => assign.mutate()}>Assign</button>
          </div>
        )}
      </div>

      <div>
        <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text-secondary)", marginBottom: 8 }}>Financial years</div>
        {isLoading ? <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Loading…</div> : years.length === 0 ? (
          <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No financial years yet.</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {years.map((y: any) => {
              const actions = allowedActions(y, can)
              const open = pending && pending.year === y.financialYear ? pending : null
              return (
                <div key={y.financialYear} style={box}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <strong>FY{y.financialYear} · {y.yearStart} to {y.yearEnd}</strong>
                    <span style={{ fontWeight: 700 }}>{STATUS_LABEL[y.status] ?? y.status}</span>
                  </div>
                  <div style={{ color: "var(--hf-text-muted)", marginTop: 4 }}>
                    {y.preparedByName && <>Prepared by {y.preparedByName} {fmtDT(y.preparedAt)}. </>}
                    {y.reviewedByName && <>Reviewed by {y.reviewedByName} {fmtDT(y.reviewedAt)}. </>}
                    {y.signedByName && <>Signed off by {y.signedByName} {fmtDT(y.signedAt)}.</>}
                  </div>
                  {y.unpostedJournals > 0 && <div style={{ color: "var(--hf-warning-text)", marginTop: 4 }}>{y.unpostedJournals} journal(s) not yet posted.</div>}
                  {(y.warnings ?? []).map((w: string, i: number) => <div key={i} style={{ color: "var(--hf-warning-text)", marginTop: 2 }}>{w}</div>)}
                  {actions.length > 0 && (
                    <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
                      {actions.map(a => <button key={a.action} style={btn} disabled={act.isPending} onClick={() => start(y.financialYear, a)}>{a.label}</button>)}
                    </div>
                  )}
                  {open && (
                    <div style={{ marginTop: 8 }}>
                      <textarea aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} rows={2}
                        placeholder="Reason (at least 10 characters)" style={{ width: "100%", boxSizing: "border-box", fontSize: 12, padding: 7, borderRadius: 7, border: "1px solid var(--hf-border)" }} />
                      <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
                        <button style={btn} disabled={!reasonOk(reason) || act.isPending}
                          onClick={() => act.mutate({ year: open.year, spec: open.spec, note: reason.trim() })}>Confirm: {open.spec.label}</button>
                        <button style={btn} onClick={() => setPending(null)}>Cancel</button>
                      </div>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
