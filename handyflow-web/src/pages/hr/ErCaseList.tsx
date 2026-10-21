// The cases HR can see: filter by kind and status, see what is late, open a new case.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useEmployeeOptions } from "./er.hooks"
import { CATEGORIES, STATUS_LABEL, TYPES, TYPE_LABEL, dueText, fmtDate, openProblem, todaySast, words, type OpenForm } from "./er.logic"
import { card, errorText, input, plain, primary, td, th, unwrap, type Detail, type Row } from "./er.shared"
import { Chip, Field, Notice, PersonSelect, StatusChip } from "./ErParts"

const BASE = "/api/v1/hr/employee-relations"

export default function ErCaseList({ manage, open }: { manage: boolean; open: (id: string) => void }) {
  const [type, setType] = useState("")
  const [status, setStatus] = useState("OPEN")
  const [adding, setAdding] = useState(false)

  const q = useQuery<Row[]>({
    queryKey: ["er-cases", type, status],
    queryFn: async () => {
      const qs = new URLSearchParams()
      if (type) qs.set("type", type)
      if (status) qs.set("status", status)
      return unwrap<Row[]>(await apiClient.get(`${BASE}/cases?${qs.toString()}`)) ?? []
    },
  })
  const rows = q.data ?? []

  return (
    <div>
      <div style={{ display: "flex", gap: 10, marginBottom: 14, flexWrap: "wrap", alignItems: "end" }}>
        <div>
          <label htmlFor="er-f-type" style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 4 }}>Kind</label>
          <select id="er-f-type" value={type} onChange={e => setType(e.target.value)} style={{ ...input, width: 170 }}>
            <option value="">All</option>{TYPES.map(t => <option key={t} value={t}>{TYPE_LABEL[t]}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="er-f-status" style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 4 }}>Status</label>
          <select id="er-f-status" value={status} onChange={e => setStatus(e.target.value)} style={{ ...input, width: 190 }}>
            <option value="OPEN">Open</option><option value="">Everything</option>
            {Object.entries(STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div style={{ flex: 1 }} />
        {manage && <button onClick={() => setAdding(a => !a)} style={primary}>{adding ? "Close form" : "Open a case"}</button>}
      </div>

      {adding && <OpenCase done={id => { setAdding(false); open(id) }} cancel={() => setAdding(false)} />}

      {q.isError && <Notice kind="error">{errorText(q.error, "The cases could not be loaded.")}</Notice>}
      <div style={card}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr><th style={th}>Case</th><th style={th}>Employee</th><th style={th}>Kind</th><th style={th}>Status</th><th style={th}>Next due</th><th style={th}>Outcome</th></tr>
          </thead>
          <tbody>
            {q.isLoading && <tr><td style={td} colSpan={6}>Loading…</td></tr>}
            {!q.isLoading && rows.length === 0 && <tr><td style={td} colSpan={6}>No cases match.</td></tr>}
            {rows.map(r => (
              <tr key={r.id}>
                <td style={td}>
                  <button onClick={() => open(r.id)} style={{ background: "none", border: "none", padding: 0, cursor: "pointer", color: "var(--hf-primary)", fontWeight: 700, textAlign: "left" }}>{r.number}</button>
                  <div style={{ color: "var(--hf-text-muted)" }}>{r.title}</div>
                </td>
                <td style={td}>{r.employeeName}</td>
                <td style={td}>
                  {TYPE_LABEL[r.type] ?? r.type}<div style={{ color: "var(--hf-text-muted)" }}>{words(r.category)}</div>
                  <div style={{ display: "flex", gap: 4, marginTop: 2 }}>
                    {r.confidential && <Chip text="Confidential" color="var(--hf-danger-text)" bg="var(--hf-danger-soft)" />}
                    {r.legacy && <Chip text="Imported" color="var(--hf-text-muted)" bg="var(--hf-surface-sunken)" />}
                  </div>
                </td>
                <td style={td}><StatusChip status={r.status} /><div style={{ color: "var(--hf-text-muted)", marginTop: 2 }}>Reported {fmtDate(r.reportedOn)}</div></td>
                <td style={{ ...td, color: r.overdue ? "var(--hf-danger-text)" : undefined, fontWeight: r.overdue ? 700 : undefined }}>{dueText(r.dueLabel, r.dueOn, r.overdue)}</td>
                <td style={td}>{words(r.outcome)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function OpenCase({ done, cancel }: { done: (id: string) => void; cancel: () => void }) {
  const qc = useQueryClient()
  const emps = useEmployeeOptions()
  const [f, setF] = useState<OpenForm>({ employeeId: "", type: "DISCIPLINARY", category: "", title: "", description: "", incidentDate: todaySast() })
  const [respondent, setRespondent] = useState("")
  const [confidential, setConfidential] = useState(false)
  const [tried, setTried] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const set = (k: keyof OpenForm, v: string) => setF(x => ({ ...x, [k]: v }))
  const problem = openProblem(f)

  const m = useMutation({
    mutationFn: async () => unwrap<Detail>(await apiClient.post(`${BASE}/cases`, {
      ...f, title: f.title.trim(), description: f.description.trim(), respondentId: f.type === "GRIEVANCE" && respondent ? respondent : null, confidential: confidential || null,
    })),
    onSuccess: d => { qc.invalidateQueries({ queryKey: ["er-cases"] }); done(d.id) },
    onError: e => setError(errorText(e, "The case could not be opened.")),
  })

  return (
    <form aria-label="Open a case" onSubmit={e => { e.preventDefault(); setTried(true); setError(null); if (!problem) m.mutate() }}
      style={{ border: "1.5px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16, background: "var(--hf-surface)" }}>
      <h3 style={{ margin: "0 0 12px", fontSize: 15 }}>Open a case</h3>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 12 }}>
        <Field id="er-emp" text="Employee"><PersonSelect id="er-emp" value={f.employeeId} onChange={v => set("employeeId", v)} options={emps.data ?? []} /></Field>
        <Field id="er-type" text="Kind of case">
          <select id="er-type" value={f.type} onChange={e => setF(x => ({ ...x, type: e.target.value, category: "" }))} style={input}>
            {TYPES.map(t => <option key={t} value={t}>{TYPE_LABEL[t]}</option>)}
          </select>
        </Field>
        <Field id="er-cat" text="Category">
          <select id="er-cat" value={f.category} onChange={e => set("category", e.target.value)} style={input}>
            <option value="">Choose…</option>{(CATEGORIES[f.type] ?? []).map(c => <option key={c} value={c}>{words(c)}</option>)}
          </select>
        </Field>
        <Field id="er-inc" text="Incident date"><input id="er-inc" type="date" value={f.incidentDate} max={todaySast()} onChange={e => set("incidentDate", e.target.value)} style={input} /></Field>
      </div>
      {f.type === "GRIEVANCE" && (
        <Field id="er-resp2" text="Who the grievance is about (if anyone)"><PersonSelect id="er-resp2" value={respondent} onChange={setRespondent} options={emps.data ?? []} empty="Nobody in particular" /></Field>
      )}
      <Field id="er-title" text="Short title"><input id="er-title" value={f.title} onChange={e => set("title", e.target.value)} style={input} /></Field>
      <Field id="er-desc" text="What happened"><textarea id="er-desc" rows={4} value={f.description} onChange={e => set("description", e.target.value)} style={input} /></Field>
      <label style={{ fontSize: 13, display: "flex", gap: 8, alignItems: "center", marginBottom: 12 }}>
        <input type="checkbox" checked={confidential} onChange={e => setConfidential(e.target.checked)} /> Keep this case confidential (harassment, discrimination, ill-health and victimisation are always kept confidential)
      </label>
      {tried && problem && <Notice kind="error">{problem}</Notice>}
      {error && <Notice kind="error">{error}</Notice>}
      <button type="submit" disabled={m.isPending} style={primary}>{m.isPending ? "Opening…" : "Open case"}</button>{" "}
      <button type="button" style={plain} onClick={cancel}>Cancel</button>
    </form>
  )
}
