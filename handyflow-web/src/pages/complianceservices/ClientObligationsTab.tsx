// src/pages/complianceservices/ClientObligationsTab.tsx
//
// Which catalogue obligations apply to this client, and why. The system suggests from the profile; a person
// confirms or overrides with a reason. Taking an obligation off a client needs a second person's approval, and
// until then it still counts as applying.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission, usePermissions } from "../../hooks/usePermission"
import { useAuth } from "../../hooks/useAuth"
import { RefreshCw } from "lucide-react"
import { STATES, stateLabel, decisionProblem, needsApproval, approveNeedsReason, approveProblem, rowNotes } from "./applicabilityRules"

interface Row {
  obligationId: string; code: string | null; name: string | null; versionNumber: number; state: string; effectiveState: string; suggestedState: string
  suggestionReasons: string[]; basedOnUnconfirmed: boolean; overridden: boolean; decidedBy: string | null; decisionReason: string | null
  evidenceRef: string | null; reviewDue: string | null; approvalStatus: string; reviewNeeded: boolean
}
interface Resp { assignments: Row[]; counts: { applicable: number; notApplicable: number; pending: number; awaitingApproval: number; reviewNeeded: number } }
interface HistoryRow { fromState: string | null; toState: string; event: string; reason: string | null; occurredAt: string }

const ACCENT = "var(--hf-success-solid-strong)"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const button: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "5px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }

export default function ClientObligationsTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const canApprove = usePermission("COMPLIANCE_SERVICES_ADMIN")
  const { user } = useAuth()
  const base = `/api/v1/compliance-services/clients/${clientId}/obligations`
  const [open, setOpen] = useState<string | null>(null)
  const [d, setD] = useState({ state: "APPLICABLE", reason: "", evidenceRef: "", reviewDue: "" })
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [showHistory, setShowHistory] = useState<string | null>(null)

  const { data } = useQuery<Resp>({ queryKey: ["cs-client-obligations", clientId], queryFn: async () => unwrap<Resp>(await apiClient.get(base)) })
  const { data: history = [] } = useQuery<HistoryRow[]>({
    queryKey: ["cs-client-obligation-history", clientId, showHistory], enabled: !!showHistory,
    queryFn: async () => unwrap<HistoryRow[]>(await apiClient.get(`${base}/${showHistory}/history`)),
  })
  const done = (r: Resp) => { setError(""); setOpen(null); setReason(""); qc.setQueryData(["cs-client-obligations", clientId], r); qc.invalidateQueries({ queryKey: ["cs-client-obligation-history", clientId] }) }
  const fail = (e: unknown) => setError(errText(e))
  const refresh = useMutation({ mutationFn: async () => unwrap<Resp>(await apiClient.post(`${base}/refresh`, {})), onSuccess: done, onError: fail })
  const decide = useMutation({
    mutationFn: async (v: { id: string }) => unwrap<Resp>(await apiClient.put(`${base}/${v.id}/decision`, {
      state: d.state, reason: d.reason || null, evidenceRef: d.evidenceRef || null, effectiveFrom: null, reviewDue: d.reviewDue || null })),
    onSuccess: done, onError: fail,
  })
  const approve = useMutation({
    mutationFn: async (v: { id: string; reason: string }) => unwrap<Resp>(await apiClient.post(`${base}/${v.id}/approve`, { exceptionReason: v.reason || null })),
    onSuccess: done, onError: fail,
  })

  const rows = data?.assignments ?? []
  const c = data?.counts

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap", marginBottom: 12 }}>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)", flex: 1 }}>
          {c ? `${c.applicable} apply, ${c.notApplicable} do not, ${c.pending} undecided` : "Loading..."}
          {c && c.awaitingApproval > 0 ? ` · ${c.awaitingApproval} removal${c.awaitingApproval > 1 ? "s" : ""} awaiting approval` : ""}
          {c && c.reviewNeeded > 0 ? ` · ${c.reviewNeeded} to look at again` : ""}
        </div>
        {canManage && <button style={{ ...button, display: "inline-flex", alignItems: "center", gap: 6 }} disabled={refresh.isPending} onClick={() => refresh.mutate()}>
          <RefreshCw size={13} /> {refresh.isPending ? "Working..." : "Update suggestions"}</button>}
      </div>
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)", marginBottom: 8 }}>{error}</div>}
      {rows.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Nothing suggested yet. Update suggestions to read the profile against the catalogue.</div>}

      <div style={{ display: "grid", gap: 8 }}>
        {rows.map(r => {
          const problem = decisionProblem(d, r.suggestedState)
          const needsReason = approveNeedsReason(r.decidedBy, user?.userId)
          const aProblem = approveProblem(needsReason, reason)
          return (
            <div key={r.obligationId} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 12px" }}>
              <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
                <strong style={{ fontSize: 13, minWidth: 140 }}>{r.code}</strong>
                <span style={{ fontSize: 13 }}>{r.name}</span>
                <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>v{r.versionNumber}</span>
                <span style={{ flex: 1 }} />
                <span style={{ fontSize: 12, fontWeight: 600 }}>{stateLabel(r.effectiveState)}</span>
                <button style={ghost} onClick={() => setShowHistory(showHistory === r.obligationId ? null : r.obligationId)}>History</button>
                {canManage && <button style={ghost} onClick={() => { setOpen(open === r.obligationId ? null : r.obligationId); setD({ state: r.state, reason: "", evidenceRef: "", reviewDue: "" }) }}>Decide</button>}
              </div>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 6 }}>{r.suggestionReasons.map((t, i) => <div key={i}>{t}</div>)}</div>
              {rowNotes(r).map(n => <div key={n} style={{ fontSize: 11, color: "var(--hf-warning-text, #8a5a00)", marginTop: 3 }}>{n}</div>)}
              {r.decisionReason && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>Decision: {r.decisionReason}{r.evidenceRef ? ` · ${r.evidenceRef}` : ""}{r.reviewDue ? ` · look again by ${r.reviewDue}` : ""}</div>}

              {canApprove && r.approvalStatus === "AWAITING" && (
                <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap", alignItems: "center" }}>
                  {needsReason && <input style={{ ...input, maxWidth: 320 }} placeholder="Reason, at least 15 characters" value={reason} onChange={e => setReason(e.target.value)} />}
                  <button style={{ ...button, opacity: aProblem || approve.isPending ? 0.5 : 1 }} disabled={!!aProblem || approve.isPending}
                    onClick={() => approve.mutate({ id: r.obligationId, reason: needsReason ? reason : "" })}>Approve removal</button>
                  {aProblem && reason.length > 0 && <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{aProblem}</span>}
                </div>
              )}

              {open === r.obligationId && (
                <div style={{ display: "grid", gap: 8, marginTop: 10 }}>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 8 }}>
                    <select style={input} value={d.state} onChange={e => setD({ ...d, state: e.target.value })}>{STATES.map(s => <option key={s[0]} value={s[0]}>{s[1]}</option>)}</select>
                    <input style={input} placeholder="Evidence, e.g. letter or document name" value={d.evidenceRef} onChange={e => setD({ ...d, evidenceRef: e.target.value })} />
                    {d.state === "NOT_APPLICABLE" && <input style={input} type="date" title="Look again by" value={d.reviewDue} onChange={e => setD({ ...d, reviewDue: e.target.value })} />}
                  </div>
                  <textarea style={input} rows={2} placeholder="Reason" value={d.reason} onChange={e => setD({ ...d, reason: e.target.value })} />
                  {needsApproval(d.state, r.suggestedState) && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>A second person has to approve removing this. Until then it still counts as applying.</div>}
                  {problem && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{problem}</div>}
                  <div><button style={{ ...button, opacity: problem || decide.isPending ? 0.5 : 1 }} disabled={!!problem || decide.isPending} onClick={() => decide.mutate({ id: r.obligationId })}>Save decision</button></div>
                </div>
              )}

              {showHistory === r.obligationId && (
                <div style={{ marginTop: 8, fontSize: 12, color: "var(--hf-text-muted)", display: "grid", gap: 3 }}>
                  {history.length === 0 && <div>No history yet.</div>}
                  {history.map((h, i) => <div key={i}>{h.occurredAt.slice(0, 10)} · {h.event.toLowerCase()} · {h.fromState ? `${stateLabel(h.fromState)} → ` : ""}{stateLabel(h.toState)}{h.reason ? ` · ${h.reason.split("\n")[0]}` : ""}</div>)}
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
