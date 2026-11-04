// Corrects the tax-year LABEL on pay runs whose pay date says otherwise. It never recalculates: PAYE, UIF, SDL,
// net pay, payslips and status stay exactly as they are, and every correction is recorded on the pay run.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { applyBody, applyProblem, fixable, labelSummary, needReview, type LabelPreview } from "./taxYearLabels.logic"

const unwrap = <T,>(r: any): T => (r.data?.data ?? r.data) as T
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)", marginBottom: 20, overflow: "hidden" }
const th: React.CSSProperties = { textAlign: "left", padding: "9px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, borderTop: "1px solid var(--hf-border-subtle)", verticalAlign: "top" }
const URL = "/api/v1/hr/pay-runs/tax-year-labels"

export default function TaxYearLabelsPanel() {
  const qc = useQueryClient()
  const { data, isLoading, isError } = useQuery({ queryKey: ["hr-tax-year-labels"], queryFn: async () => unwrap<LabelPreview>(await apiClient.get(URL)) })
  const [picked, setPicked] = useState<string[]>([]), [reason, setReason] = useState(""), [msg, setMsg] = useState(""), [err, setErr] = useState("")
  const go = useMutation({
    mutationFn: async () => unwrap<{ migrationRef: string; corrected: number; skipped: number }>(await apiClient.post(`${URL}/apply`, applyBody(picked, reason))),
    onSuccess: r => { setErr(""); setMsg(`${r.corrected} pay run${r.corrected === 1 ? "" : "s"} corrected (ref ${r.migrationRef}).${r.skipped ? ` ${r.skipped} skipped.` : ""}`); setPicked([]); setReason(""); qc.invalidateQueries({ queryKey: ["hr-tax-year-labels"] }) },
    onError: (e: any) => { setMsg(""); setErr(e?.response?.data?.message ?? "Could not correct the labels") },
  })
  if (isLoading) return null
  if (!isError && data && !Array.isArray(data.rows)) return null
  if (isError || !data) return <div style={{ ...card, padding: 16, fontSize: 13 }}>Could not load the tax-year label check.</div>
  const todo = fixable(data.rows), review = needReview(data.rows), problem = applyProblem(picked, reason)
  const toggle = (id: string) => setPicked(p => p.includes(id) ? p.filter(x => x !== id) : [...p, id])
  return (
    <section style={card}>
      <div style={{ padding: "14px 18px", borderBottom: "1px solid var(--hf-border-subtle)" }}>
        <h3 style={{ margin: 0, fontSize: 15 }}>Tax-year labels on pay runs</h3>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>Only the label changes. PAYE, UIF, SDL, net pay and payslips are never touched, and the original label is kept in the pay run's history.</div>
      </div>
      <div style={{ padding: "12px 18px", fontSize: 13 }}>{labelSummary(data)}</div>
      {(todo.length > 0 || review.length > 0) && (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th} /><th style={th}>Pay run</th><th style={th}>Pay date</th><th style={th}>Label now</th><th style={th}>Verified</th><th style={th}>Status</th><th style={th}>Note</th></tr></thead>
            <tbody>{[...todo, ...review].map(r => (
              <tr key={r.id}>
                <td style={td}>{r.verdict === "WRONG_LABEL" && <input type="checkbox" aria-label={`Correct ${r.payRunNumber}`} checked={picked.includes(r.id)} onChange={() => toggle(r.id)} />}</td>
                <td style={td}><b>{r.payRunNumber}</b></td><td style={td}>{r.payDate ?? "—"}</td><td style={td}>{r.storedLabel}</td>
                <td style={td}>{r.verdict === "WRONG_LABEL" ? r.proposedLabel : "Needs review"}</td>
                <td style={td}>{r.status}{r.finalisedOrPaid ? " (locked)" : ""}</td>
                <td style={td}>{r.reason}{r.payslipsOnOtherYear > 0 ? ` ${r.payslipsOnOtherYear} of ${r.payslips} payslips carry a different year; they are left as issued.` : ""}</td>
              </tr>))}</tbody>
          </table>
        </div>)}
      {todo.length > 0 && (
        <div style={{ padding: "12px 18px", borderTop: "1px solid var(--hf-border-subtle)", display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
          <button type="button" onClick={() => setPicked(picked.length === todo.length ? [] : todo.map(r => r.id))} style={{ fontSize: 12 }}>{picked.length === todo.length ? "Clear" : "Select all"}</button>
          <input value={reason} onChange={e => setReason(e.target.value)} placeholder="Why (for the record)" aria-label="Reason" style={{ flex: "1 1 240px", padding: 8, border: "1px solid var(--hf-border)", borderRadius: 8 }} />
          <button type="button" disabled={!!problem || go.isPending} title={problem ?? ""} onClick={() => go.mutate()}>{go.isPending ? "Correcting…" : "Correct labels"}</button>
        </div>)}
      {msg && <div role="status" style={{ padding: "0 18px 12px", fontSize: 13 }}>{msg}</div>}
      {err && <div role="alert" style={{ padding: "0 18px 12px", fontSize: 13, color: "var(--hf-danger-text)" }}>{err}</div>}
    </section>
  )
}
