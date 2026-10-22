// The company's limit on carrying annual leave into a new year, and who would lose days at the next rollover.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { canSetPolicy, policyProblem } from "./leave.logic"

interface Policy { carryOverCapDays: number | null; effectiveYear: number | null; agreementRef: string | null }
interface AtRiskRow { employeeId: string; employeeName: string; availableNow: number; projectedClosing: number; wouldLose: number }
interface AtRiskReport { carryOverCapDays: number | null; rolloverInto: number; employees: AtRiskRow[] }

const BASE = "/api/v1/hr"
const data = <T,>(r: { data?: { data?: T } }): T | undefined => r.data?.data
const reason = (e: unknown, fallback: string) => {
  const m = (e as { response?: { data?: { message?: unknown } } } | null)?.response?.data?.message
  return typeof m === "string" && m ? m : fallback
}
const box: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: 16, marginBottom: 18, background: "var(--hf-surface)" }
const field: React.CSSProperties = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 6, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" }

export default function LeavePolicy({ permissions }: { permissions: string[] }) {
  const qc = useQueryClient()
  const mayEdit = canSetPolicy(permissions)
  const thisYear = new Date().getFullYear()
  const policy = useQuery<Policy | undefined>({ queryKey: ["leave-policy"], retry: false, queryFn: async () => data<Policy>(await apiClient.get(`${BASE}/leave-policy`)) })
  const risk = useQuery<AtRiskReport | undefined>({ queryKey: ["leave-at-risk"], retry: false, queryFn: async () => data<AtRiskReport>(await apiClient.get(`${BASE}/leave-policy/at-risk`)) })
  const [editing, setEditing] = useState(false)
  const [cap, setCap] = useState("")
  const [year, setYear] = useState(String(thisYear + 1))
  const [agreement, setAgreement] = useState("")
  const [error, setError] = useState<string | null>(null)
  const problem = policyProblem(cap, year, agreement, thisYear)

  const save = useMutation({
    mutationFn: async () => apiClient.put(`${BASE}/leave-policy`, cap.trim() === "" ? { carryOverCapDays: null, effectiveYear: null, agreementRef: null }
      : { carryOverCapDays: Number(cap), effectiveYear: Number(year), agreementRef: agreement.trim() }),
    onSuccess: () => { setEditing(false); setError(null); qc.invalidateQueries({ queryKey: ["leave-policy"] }); qc.invalidateQueries({ queryKey: ["leave-at-risk"] }) },
    onError: e => setError(reason(e, "The policy could not be saved.")),
  })

  if (policy.isError || !policy.data) return null // no right to see it, or not loaded: show nothing rather than an error on the leave screen
  const p = policy.data
  const rows = risk.data?.employees ?? []

  return (
    <section aria-label="Carrying leave into a new year" style={box}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
        <div>
          <div style={{ fontWeight: 700, fontSize: 14 }}>Carrying annual leave into a new year</div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 3 }}>
            {p.carryOverCapDays == null
              ? "No limit: all unused annual leave is carried over."
              : `At most ${p.carryOverCapDays} days are carried over from the end of ${(p.effectiveYear ?? 0) - 1} onward. Agreement: ${p.agreementRef ?? "–"}.`}
          </div>
        </div>
        {mayEdit && !editing && (
          <button onClick={() => { setCap(p.carryOverCapDays == null ? "" : String(p.carryOverCapDays)); setYear(String(p.effectiveYear ?? thisYear + 1)); setAgreement(p.agreementRef ?? ""); setError(null); setEditing(true) }}
            style={{ padding: "6px 14px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", cursor: "pointer", fontSize: 13 }}>Change</button>
        )}
      </div>

      {editing && (
        <form aria-label="Leave carry-over policy" onSubmit={e => { e.preventDefault(); setError(null); if (!problem) save.mutate() }} style={{ marginTop: 14, display: "grid", gap: 10, maxWidth: 520 }}>
          <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: 0 }}>
            Leave can only be forfeited by written agreement with employees. Days above the limit are removed when the year rolls over and show on the statement as "Forfeited under company policy". It applies to rollovers that have not happened yet, never to the past. Leave an empty limit to remove it.
          </p>
          <label style={{ fontSize: 12, fontWeight: 600 }}>Most days to carry over
            <input id="lp-cap" aria-label="Most days to carry over" value={cap} onChange={e => setCap(e.target.value)} inputMode="decimal" style={{ ...field, display: "block", width: 120, marginTop: 4 }} />
          </label>
          <label style={{ fontSize: 12, fontWeight: 600 }}>Applies from the end of the year before
            <input aria-label="From year" value={year} onChange={e => setYear(e.target.value)} inputMode="numeric" style={{ ...field, display: "block", width: 120, marginTop: 4 }} />
          </label>
          <label style={{ fontSize: 12, fontWeight: 600 }}>Agreement or policy document that allows it
            <input aria-label="Agreement" value={agreement} onChange={e => setAgreement(e.target.value)} maxLength={200} style={{ ...field, display: "block", width: "100%", boxSizing: "border-box", marginTop: 4 }} />
          </label>
          {cap.trim() !== "" && problem && <div style={{ fontSize: 12, color: "var(--hf-danger-text)" }}>{problem}</div>}
          {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
          <div style={{ display: "flex", gap: 8 }}>
            <button type="submit" disabled={!!problem || save.isPending} style={{ padding: "7px 16px", borderRadius: 8, border: "none", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", cursor: "pointer", fontSize: 13, fontWeight: 600, opacity: problem || save.isPending ? 0.6 : 1 }}>
              {save.isPending ? "Saving…" : "Save policy"}
            </button>
            <button type="button" onClick={() => setEditing(false)} style={{ padding: "7px 16px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", cursor: "pointer", fontSize: 13 }}>Cancel</button>
          </div>
        </form>
      )}

      {rows.length > 0 && (
        <div style={{ marginTop: 16 }}>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>
            {rows.length} {rows.length === 1 ? "person" : "people"} would lose annual leave at the end of {risk.data!.rolloverInto - 1} if they took no more
          </div>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Employee</th><th>Available now</th><th>By year end</th><th>Would lose</th></tr></thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.employeeId}>
                  <td>{r.employeeName}</td><td>{Number(r.availableNow).toFixed(1)}</td><td>{Number(r.projectedClosing).toFixed(1)}</td>
                  <td style={{ fontWeight: 700, color: "var(--hf-danger-text)" }}>{Number(r.wouldLose).toFixed(1)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
