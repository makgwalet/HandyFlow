// Letters HR has made: filter by status, prepare a new one.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useEmployeeOptions } from "./er.hooks"
import { fmtDate } from "./er.logic"
import { STATUS_LABEL } from "./letters.logic"
import { card, errorText, input, plain, primary, td, th, unwrap, type LetterDetail, type LetterRow, type Templates } from "./letters.shared"
import { Field, Notice, PersonSelect } from "./ErParts"
import { LetterStatus } from "./LetterParts"

const BASE = "/api/v1/hr/letters"

export default function LetterList({ canIssue, open }: { canIssue: boolean; open: (id: string) => void }) {
  const [status, setStatus] = useState("")
  const [preparing, setPreparing] = useState(false)
  const q = useQuery<LetterRow[]>({
    queryKey: ["letters", status],
    queryFn: async () => unwrap<LetterRow[]>(await apiClient.get(`${BASE}${status ? `?status=${status}` : ""}`)) ?? [],
  })
  const rows = q.data ?? []
  return (
    <div>
      <div style={{ display: "flex", gap: 10, marginBottom: 14, alignItems: "end", flexWrap: "wrap" }}>
        <div>
          <label htmlFor="lt-f-status" style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 4 }}>Status</label>
          <select id="lt-f-status" value={status} onChange={e => setStatus(e.target.value)} style={{ ...input, width: 170 }}>
            <option value="">All</option>{Object.entries(STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div style={{ flex: 1 }} />
        {canIssue && <button onClick={() => setPreparing(p => !p)} style={primary}>{preparing ? "Close form" : "Prepare a letter"}</button>}
      </div>
      {preparing && <PrepareLetter done={id => { setPreparing(false); open(id) }} cancel={() => setPreparing(false)} />}
      {q.isError && <Notice kind="error">{errorText(q.error, "The letters could not be loaded.")}</Notice>}
      <div style={card}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Letter</th><th style={th}>Employee</th><th style={th}>Template</th><th style={th}>Status</th><th style={th}>Issued</th><th style={th}>Receipt</th></tr></thead>
          <tbody>
            {q.isLoading && <tr><td style={td} colSpan={6}>Loading…</td></tr>}
            {!q.isLoading && rows.length === 0 && <tr><td style={td} colSpan={6}>No letters yet.</td></tr>}
            {rows.map(r => (
              <tr key={r.id}>
                <td style={td}>
                  <button onClick={() => open(r.id)} style={{ background: "none", border: "none", padding: 0, cursor: "pointer", color: "var(--hf-primary)", fontWeight: 700, textAlign: "left" }}>{r.number}</button>
                  <div style={{ color: "var(--hf-text-muted)" }}>{r.subject}</div>
                </td>
                <td style={td}>{r.employeeName}{r.caseNumber ? <div style={{ color: "var(--hf-text-muted)" }}>Case {r.caseNumber}</div> : null}</td>
                <td style={td}>{r.templateName}</td>
                <td style={td}><LetterStatus status={r.status} /></td>
                <td style={td}>{fmtDate(r.issuedOn)}</td>
                <td style={td}>{!r.requiresAck ? "Not asked" : r.acknowledgedAt ? "Confirmed" : r.status === "ISSUED" ? "Waiting" : "–"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

/** Choose a template and an employee; the server fills the letter in and keeps it as a draft. */
function PrepareLetter({ done, cancel }: { done: (id: string) => void; cancel: () => void }) {
  const qc = useQueryClient()
  const emps = useEmployeeOptions()
  const tpl = useQuery<Templates>({ queryKey: ["letter-templates"], queryFn: async () => unwrap<Templates>(await apiClient.get(`${BASE}/templates`)) })
  const [templateId, setTemplateId] = useState("")
  const [employeeId, setEmployeeId] = useState("")
  const [error, setError] = useState<string | null>(null)
  const problem = !templateId ? "Choose the template." : !employeeId ? "Choose the employee." : null
  const m = useMutation({
    mutationFn: async () => unwrap<LetterDetail>(await apiClient.post(`${BASE}/prepare`, { templateId, employeeId, caseId: null })),
    onSuccess: d => { qc.invalidateQueries({ queryKey: ["letters"] }); done(d.row.id) },
    onError: e => setError(errorText(e, "The letter could not be prepared.")),
  })
  const active = (tpl.data?.templates ?? []).filter(t => t.active)
  return (
    <form aria-label="Prepare a letter" onSubmit={e => { e.preventDefault(); setError(null); if (!problem) m.mutate() }}
      style={{ border: "1.5px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16, background: "var(--hf-surface)" }}>
      <h3 style={{ margin: "0 0 12px", fontSize: 15 }}>Prepare a letter</h3>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>For a letter about a case, prepare it from the case itself so it uses the case's details.</p>
      <Field id="lt-tpl" text="Template">
        <select id="lt-tpl" value={templateId} onChange={e => setTemplateId(e.target.value)} style={input}>
          <option value="">Choose…</option>{active.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
        </select>
      </Field>
      <Field id="lt-emp" text="Employee"><PersonSelect id="lt-emp" value={employeeId} onChange={setEmployeeId} options={emps.data ?? []} /></Field>
      {error && <Notice kind="error">{error}</Notice>}
      <button type="submit" disabled={!!problem || m.isPending} style={primary}>{m.isPending ? "Preparing…" : "Prepare draft"}</button>{" "}
      <button type="button" style={plain} onClick={cancel}>Cancel</button>
    </form>
  )
}
