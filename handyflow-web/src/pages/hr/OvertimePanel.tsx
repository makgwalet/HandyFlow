// Overtime claims: yours, or (for approvers and team managers) everyone's waiting for a decision.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { CLAIM_LABEL, fmtMinutes, KIND_LABEL, lastDays, rand, reasonProblem } from "./attendance.logic"
import { card, errorText, input, label, plain, primary, td, th, unwrap } from "./attendance.shared"
import { Chip, Notice } from "./AttendanceParts"

interface Claim {
  id: string; employeeId: string; employeeNumber: string; employeeName: string; workDate: string; kind: string; minutes: number
  multiplier: number; hourlyRate: number; amount: number; status: string; decisionNote: string | null; payRunId: string | null; canDecide: boolean
}

const STATUSES = ["PENDING", "APPROVED", "REJECTED"]

export default function OvertimePanel({ everyone }: { everyone: boolean }) {
  const qc = useQueryClient()
  const [status, setStatus] = useState("PENDING")
  const [deciding, setDeciding] = useState<{ claim: Claim; kind: "reject" } | null>(null)
  const [note, setNote] = useState("")
  const [problem, setProblem] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const period = lastDays(90)
  const list = useQuery({
    queryKey: ["hr-overtime", everyone ? status : "mine"],
    queryFn: async () => unwrap<Claim[]>(everyone
      ? await apiClient.get("/api/v1/hr/overtime", { params: { status } })
      : await apiClient.get("/api/v1/hr/overtime/mine", { params: { from: period.from, to: period.to } })),
  })

  const act = useMutation({
    mutationFn: async (call: () => Promise<unknown>) => call(),
    onSuccess: () => { setError(null); setDeciding(null); setNote(""); setProblem(null); qc.invalidateQueries({ queryKey: ["hr-overtime"] }); qc.invalidateQueries({ queryKey: ["hr-attendance"] }) },
    onError: (e: unknown) => setError(errorText(e, "That did not work.")),
  })

  const reject = () => {
    const p = reasonProblem(note, "for rejecting this claim"); setProblem(p)
    if (!p && deciding) act.mutate(() => apiClient.post(`/api/v1/hr/overtime/${deciding.claim.id}/reject`, { note: note.trim() }))
  }

  return (
    <div>
      {everyone && (
        <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap" }}>
          {STATUSES.map(s => (
            <button key={s} aria-pressed={status === s} onClick={() => setStatus(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: status === s ? 600 : 400,
                background: status === s ? "var(--hf-primary)" : "var(--hf-surface-sunken)", color: status === s ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
              {CLAIM_LABEL[s]}
            </button>
          ))}
        </div>
      )}
      {error && <Notice kind="error">{error}</Notice>}
      {list.isLoading && <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>}
      {list.isError && <Notice kind="error">{errorText(list.error, "Overtime could not be loaded.")}</Notice>}
      {list.data && list.data.length === 0 && <p style={{ color: "var(--hf-text-muted)", fontSize: 14 }}>{everyone ? `No ${CLAIM_LABEL[status].toLowerCase()} overtime.` : "You have no overtime claims in the last 90 days. Claim from My time."}</p>}
      {list.data && list.data.length > 0 && (
        <div style={card}>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr>{everyone && <th style={th}>Employee</th>}<th style={th}>Date</th><th style={th}>Time</th><th style={th}>Rate</th><th style={th}>Amount</th><th style={th}>Status</th><th style={th} /></tr></thead>
              <tbody>
                {list.data.map(c => (
                  <tr key={c.id}>
                    {everyone && <td style={td}>{c.employeeName} <span style={{ color: "var(--hf-text-muted)" }}>{c.employeeNumber}</span></td>}
                    <td style={td}>{c.workDate}</td>
                    <td style={td}>{fmtMinutes(c.minutes)}</td>
                    <td style={td}>{KIND_LABEL[c.kind] ?? c.kind}<div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{rand(c.hourlyRate)} an hour</div></td>
                    <td style={td}>{rand(c.amount)}</td>
                    <td style={td}><Chip text={CLAIM_LABEL[c.status] ?? c.status} color="var(--hf-text-secondary)" bg="var(--hf-surface-sunken)" />
                      {c.payRunId && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>In a pay run</div>}
                      {c.decisionNote && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{c.decisionNote}</div>}</td>
                    <td style={td}>
                      {c.canDecide && c.status === "PENDING" && <button style={plain} onClick={() => act.mutate(() => apiClient.post(`/api/v1/hr/overtime/${c.id}/approve`, {}))}>Approve</button>}
                      {c.canDecide && <>{" "}<button style={plain} onClick={() => { setDeciding({ claim: c, kind: "reject" }); setNote(""); setProblem(null) }}>{c.status === "APPROVED" ? "Take back" : "Reject"}</button></>}
                      {!everyone && (c.status === "PENDING" || c.status === "REJECTED") && <button style={plain} onClick={() => act.mutate(() => apiClient.post(`/api/v1/hr/overtime/${c.id}/withdraw`))}>Withdraw</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {deciding && (
        <div style={{ padding: 14, border: "1px solid var(--hf-border)", borderRadius: 10 }}>
          <label style={label} htmlFor="ot-note">Reason for {deciding.claim.status === "APPROVED" ? "taking back" : "rejecting"} {deciding.claim.employeeName}'s claim for {deciding.claim.workDate}</label>
          <input id="ot-note" style={input} value={note} onChange={e => setNote(e.target.value)} />
          {problem && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 4 }}>{problem}</div>}
          <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
            <button style={primary} onClick={reject}>Confirm</button>
            <button style={plain} onClick={() => setDeciding(null)}>Never mind</button>
          </div>
        </div>
      )}
    </div>
  )
}
