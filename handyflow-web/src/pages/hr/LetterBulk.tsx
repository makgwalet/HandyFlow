// Make the same letter for many people at once: choose a template and the people, check the drafts, then issue the complete ones.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useEmployeeOptions } from "./er.hooks"
import { BULK_ISSUE_MAX, bulkPrepareProblem, bulkTemplateProblem, readyToIssue } from "./letters.logic"
import { errorText, input, plain, primary, td, th, unwrap, type BulkIssued, type BulkPrepared, type Templates } from "./letters.shared"
import { Field, Notice } from "./ErParts"

const BASE = "/api/v1/hr/letters"

export default function LetterBulk({ cancel }: { cancel: () => void }) {
  const qc = useQueryClient()
  const emps = useEmployeeOptions()
  const tpl = useQuery<Templates>({ queryKey: ["letter-templates"], queryFn: async () => unwrap<Templates>(await apiClient.get(`${BASE}/templates`)) })
  const [templateId, setTemplateId] = useState("")
  const [chosen, setChosen] = useState<string[]>([])
  const [filter, setFilter] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [prepared, setPrepared] = useState<BulkPrepared | null>(null)
  const [issued, setIssued] = useState<BulkIssued | null>(null)

  const fields = tpl.data?.fields ?? []
  const active = (tpl.data?.templates ?? []).filter(t => t.active)
  const picked = active.find(t => t.id === templateId)
  const templateProblem = picked ? bulkTemplateProblem(picked, fields) : null
  const problem = bulkPrepareProblem(!!picked && !templateProblem, chosen.length)
  const people = (emps.data ?? []).filter(p => p.name.toLowerCase().includes(filter.trim().toLowerCase()))
  const ready = prepared ? readyToIssue(prepared.items) : []

  const prepare = useMutation({
    mutationFn: async () => unwrap<BulkPrepared>(await apiClient.post(`${BASE}/bulk/prepare`, { templateId, employeeIds: chosen })),
    onSuccess: d => { setPrepared(d); setIssued(null); qc.invalidateQueries({ queryKey: ["letters"] }) },
    onError: e => setError(errorText(e, "The drafts could not be prepared.")),
  })
  const issue = useMutation({
    mutationFn: async () => unwrap<BulkIssued>(await apiClient.post(`${BASE}/bulk/issue`, { letterIds: ready })),
    onSuccess: d => { setIssued(d); qc.invalidateQueries({ queryKey: ["letters"] }) },
    onError: e => setError(errorText(e, "The letters could not be issued.")),
  })
  const toggle = (id: string) => setChosen(c => c.includes(id) ? c.filter(x => x !== id) : [...c, id])

  return (
    <section aria-label="Letters for many people" style={{ border: "1.5px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16, background: "var(--hf-surface)" }}>
      <h3 style={{ margin: "0 0 12px", fontSize: 15 }}>Letters for many people</h3>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>
        Makes one draft per person from a general or employment template. Nothing is sent until you issue the drafts. Drafts that still have gaps are left for you to finish one by one.
      </p>
      {!prepared && (
        <>
          <Field id="lb-tpl" text="Template">
            <select id="lb-tpl" value={templateId} onChange={e => { setTemplateId(e.target.value); setError(null) }} style={input}>
              <option value="">Choose…</option>{active.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
            </select>
          </Field>
          {templateProblem && <Notice kind="error">{templateProblem}</Notice>}
          <Field id="lb-find" text={`Employees (${chosen.length} chosen)`}>
            <input id="lb-find" value={filter} onChange={e => setFilter(e.target.value)} placeholder="Type to narrow the list" style={input} />
          </Field>
          <div style={{ maxHeight: 220, overflow: "auto", border: "1px solid var(--hf-border)", borderRadius: 8, padding: 8, marginBottom: 12 }}>
            {people.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No employees to show.</div>}
            {people.map(p => (
              <label key={p.id} style={{ display: "flex", gap: 8, fontSize: 13, padding: "3px 0" }}>
                <input type="checkbox" checked={chosen.includes(p.id)} onChange={() => toggle(p.id)} />{p.name}
              </label>
            ))}
          </div>
          <button type="button" style={plain} onClick={() => setChosen(Array.from(new Set([...chosen, ...people.map(p => p.id)])))}>Choose everyone shown</button>{" "}
          <button type="button" style={plain} onClick={() => setChosen([])}>Clear</button>
          {error && <Notice kind="error">{error}</Notice>}
          <div style={{ marginTop: 12 }}>
            <button type="button" disabled={!!problem || prepare.isPending} style={primary} onClick={() => { setError(null); prepare.mutate() }}>
              {prepare.isPending ? "Preparing…" : "Prepare drafts"}
            </button>{" "}
            <button type="button" style={plain} onClick={cancel}>Cancel</button>
            {problem && chosen.length > 0 && <span style={{ fontSize: 12, marginLeft: 8, color: "var(--hf-text-muted)" }}>{problem}</span>}
          </div>
        </>
      )}
      {prepared && (
        <>
          <p style={{ fontSize: 13 }}><strong>{prepared.made}</strong> drafts made, <strong>{prepared.refused}</strong> refused.</p>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Employee</th><th style={th}>Draft</th><th style={th}>Still to do</th></tr></thead>
            <tbody>
              {prepared.items.map(i => (
                <tr key={i.employeeId}>
                  <td style={td}>{i.employeeName}</td>
                  <td style={td}>{i.number ?? "–"}</td>
                  <td style={td}>{i.problem ?? (i.gaps.length ? `Fill in: ${i.gaps.join(", ")}` : "Ready to issue")}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {issued && (
            <div style={{ marginTop: 12 }}>
              <Notice kind="ok">{issued.issued} issued, {issued.refused} not issued.</Notice>
              {issued.items.filter(i => i.problem).map(i => <div key={i.letterId} style={{ fontSize: 13 }}>{i.employeeName ?? i.number ?? i.letterId}: {i.problem}</div>)}
            </div>
          )}
          {error && <Notice kind="error">{error}</Notice>}
          <div style={{ marginTop: 12 }}>
            {!issued && (
              <button type="button" style={primary} disabled={issue.isPending || ready.length === 0 || ready.length > BULK_ISSUE_MAX} onClick={() => { setError(null); issue.mutate() }}>
                {issue.isPending ? "Issuing…" : `Issue the complete drafts (${ready.length})`}
              </button>
            )}{" "}
            <button type="button" style={plain} onClick={cancel}>Close</button>
            {ready.length > BULK_ISSUE_MAX && <span style={{ fontSize: 12, marginLeft: 8 }}>At most {BULK_ISSUE_MAX} can be issued at once.</span>}
          </div>
        </>
      )}
    </section>
  )
}
