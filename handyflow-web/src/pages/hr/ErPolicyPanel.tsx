// Company policy numbers for employee relations, and a look at one employee's warnings.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useEmployeeOptions } from "./er.hooks"
import { fmtDate, policyProblem, todaySast, words, type PolicyForm } from "./er.logic"
import { card, errorText, input, primary, td, th, unwrap, type PolicyView, type WarningView } from "./er.shared"
import { Field, Notice, PersonSelect } from "./ErParts"

const BASE = "/api/v1/hr/employee-relations"

const FIELDS: { key: keyof PolicyForm; text: string; hint?: string }[] = [
  { key: "noticeHours", text: "Notice of a hearing (hours)" },
  { key: "verbalMonths", text: "Verbal warning stays valid (months)" },
  { key: "writtenMonths", text: "Written warning stays valid (months)" },
  { key: "finalMonths", text: "Final written warning stays valid (months)" },
  { key: "appealDays", text: "Time to appeal (days)" },
  { key: "grievanceResponseDays", text: "Time to respond to a grievance (working days)", hint: "Weekends are skipped. Public holidays are not." },
  { key: "minReviewDays", text: "Shortest review period for poor performance (days)" },
]

export default function ErPolicyPanel({ manage }: { manage: boolean }) {
  const q = useQuery<PolicyView>({ queryKey: ["er-policy"], queryFn: async () => unwrap<PolicyView>(await apiClient.get(`${BASE}/policy`)) })
  if (q.isLoading) return <div style={{ padding: 24, color: "var(--hf-text-faint)" }}>Loading…</div>
  if (q.isError || !q.data) return <Notice kind="error">{errorText(q.error, "The policy could not be loaded.")}</Notice>
  return (
    <div>
      <PolicyEditor initial={q.data} manage={manage} />
      <Warnings />
    </div>
  )
}

function PolicyEditor({ initial, manage }: { initial: PolicyView; manage: boolean }) {
  const qc = useQueryClient()
  const [f, setF] = useState<PolicyForm>({ ...initial })
  const [msg, setMsg] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)

  const m = useMutation({
    mutationFn: async (p: PolicyForm) => unwrap<PolicyView>(await apiClient.put(`${BASE}/policy`, p)),
    onSuccess: d => { qc.setQueryData(["er-policy"], d); setErr(null); setMsg("Policy saved. It applies to new steps; cases already decided keep their dates.") },
    onError: e => { setMsg(null); setErr(errorText(e, "The policy could not be saved.")) },
  })

  const problem = policyProblem(f)
  return (
    <div style={{ ...card, padding: 16 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>Company policy</h3>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 14px" }}>
        These are your own rules, not figures set by law. The Code of Good Practice asks for reasonable notice and a fair process without fixing the numbers. The 30 days to refer a dismissal to the CCMA is law and is not changed here.
      </p>
      <form aria-label="Policy" onSubmit={e => { e.preventDefault(); setMsg(null); if (!problem) m.mutate(f) }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: 12 }}>
          {FIELDS.map(x => (
            <Field key={x.key} id={`pol-${x.key}`} text={x.text} hint={x.hint}>
              <input id={`pol-${x.key}`} type="number" value={f[x.key]} disabled={!manage} onChange={e => setF({ ...f, [x.key]: Number(e.target.value) })} style={input} />
            </Field>
          ))}
        </div>
        {manage && problem && <Notice kind="error">{problem}</Notice>}
        {err && <Notice kind="error">{err}</Notice>}
        {msg && <Notice kind="ok">{msg}</Notice>}
        {manage ? <button type="submit" disabled={m.isPending || !!problem} style={primary}>{m.isPending ? "Saving…" : "Save policy"}</button>
          : <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Only people who manage employee relations can change this.</div>}
      </form>
    </div>
  )
}

function Warnings() {
  const emps = useEmployeeOptions()
  const [emp, setEmp] = useState("")
  const q = useQuery<WarningView[]>({
    queryKey: ["er-warnings", emp], enabled: !!emp,
    queryFn: async () => unwrap<WarningView[]>(await apiClient.get(`${BASE}/employees/${emp}/warnings`)) ?? [],
  })
  const today = todaySast()
  return (
    <div style={{ ...card, padding: 16, marginTop: 20 }}>
      <h3 style={{ margin: "0 0 10px", fontSize: 15 }}>An employee's warnings</h3>
      <Field id="war-emp" text="Employee"><PersonSelect id="war-emp" value={emp} onChange={setEmp} options={emps.data ?? []} /></Field>
      {q.isError && <Notice kind="error">{errorText(q.error, "The warnings could not be loaded.")}</Notice>}
      {emp && q.data && (q.data.length === 0 ? <div style={{ fontSize: 13 }}>No warnings on record.</div> : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Case</th><th style={th}>Warning</th><th style={th}>Given</th><th style={th}>Valid until</th><th style={th}>Now</th></tr></thead>
          <tbody>
            {q.data.map(w => (
              <tr key={w.caseId}>
                <td style={td}>{w.number}</td><td style={td}>{words(w.sanction)}</td><td style={td}>{fmtDate(w.issuedOn)}</td>
                <td style={td}>{w.validUntil ? fmtDate(w.validUntil) : "Not recorded"}</td>
                <td style={td}>{!w.validUntil ? "Needs review" : w.validUntil >= today ? "In force" : "Expired"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ))}
    </div>
  )
}
