// src/pages/accounting/TagRulesTab.tsx
//
// Finance F28: rules that put a cost centre on income and expense lines automatically, whichever module made the journal.
// The first matching rule (lowest priority number) wins. A rule only fills in lines that have no cost centre.
// "Apply to past journals" adds the dimension to posted lines; it changes no amount, and every line it tags is logged.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { labelOf, type CostCentre } from "./costCentre.logic"
import { ENTRY_TYPES, ruleProblem, ruleSummary, yearStart, type ApplyResult, type TagRule } from "./profitability.logic"

const unwrap = (r: any) => r.data?.data ?? r.data
const errorOf = (e: any) => e?.response?.data?.message ?? "Something went wrong. Please try again."
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14, overflowX: "auto" }
const btn: React.CSSProperties = { padding: "7px 13px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontWeight: 600, fontSize: 12, cursor: "pointer" }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 6, fontSize: 12, background: "var(--hf-surface)", color: "var(--hf-text)" }

export default function TagRulesTab() {
  const qc = useQueryClient()
  const canManage = usePermission("ACCOUNTING_MANAGE")
  const canAdmin = usePermission("ACCOUNTING_ADMIN")
  const [name, setName] = useState(""), [account, setAccount] = useState(""), [text, setText] = useState(""), [type, setType] = useState(""), [centre, setCentre] = useState(""), [priority, setPriority] = useState("100")
  const [error, setError] = useState<string | null>(null)
  const today = new Date()
  const [from, setFrom] = useState(yearStart(today)), [to, setTo] = useState(today.toISOString().slice(0, 10))
  const [result, setResult] = useState<ApplyResult | null>(null)

  const { data: rules = [] } = useQuery<TagRule[]>({ queryKey: ["acc-tag-rules"], queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/tag-rules")) ?? [] })
  const { data: centres = [] } = useQuery<CostCentre[]>({ queryKey: ["acc-cost-centres"], queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/cost-centres")) ?? [] })
  const refresh = () => qc.invalidateQueries({ queryKey: ["acc-tag-rules"] })

  const add = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/tag-rules", { name: name.trim(), priority: Number(priority) || 100, accountCode: account.trim() || null, textContains: text.trim() || null, entryType: type || null, costCentreId: centre }),
    onSuccess: () => { setError(null); setName(""); setAccount(""); setText(""); setType(""); refresh() }, onError: e => setError(errorOf(e)),
  })
  const toggle = useMutation({ mutationFn: (r: TagRule) => apiClient.post(`/api/v1/accounting/tag-rules/${r.id}/active`, { active: !r.active }), onSuccess: refresh })
  const del = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/accounting/tag-rules/${id}`), onSuccess: refresh })
  const retro = useMutation({
    mutationFn: (apply: boolean) => apiClient.post("/api/v1/accounting/tag-rules/apply", { from, to, apply }),
    onSuccess: r => { setError(null); setResult(unwrap(r)); if (unwrap(r).applied) { qc.invalidateQueries({ queryKey: ["acc-profitability"] }); qc.invalidateQueries({ queryKey: ["acc-by-centre"] }) } },
    onError: e => { setResult(null); setError(errorOf(e)) },
  })
  const problem = ruleProblem(name, account, text, type, centre)

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Tagging rules</h2>
      <p style={{ fontSize: 13, color: "var(--hf-text-secondary)" }}>
        Put a cost centre on income and expense lines automatically, whichever part of the system made the journal. For example: account 5140 with the words “CA 123” goes to the vehicle CA 123;
        words “Sandton Mall” go to that site. The lowest priority number is tried first, and only lines without a cost centre are filled in.
      </p>
      {rules.length > 0 && (
        <div style={card}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <tbody>{rules.map(r => (
              <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border)", opacity: r.active ? 1 : 0.55 }}>
                <td style={{ width: 50 }}>{r.priority}</td><td><b>{r.name}</b></td><td>When {ruleSummary(r)}</td><td>→ {r.costCentreName}</td>
                <td style={{ textAlign: "right", whiteSpace: "nowrap" }}>{canManage && <>
                  <button style={btn} onClick={() => toggle.mutate(r)}>{r.active ? "Switch off" : "Switch on"}</button>{" "}
                  <button style={btn} onClick={() => { if (window.confirm(`Remove the rule “${r.name}”?`)) del.mutate(r.id) }}>Remove</button></>}</td></tr>))}</tbody>
          </table>
        </div>
      )}
      {canManage && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>New rule</h3>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <input aria-label="Rule name" placeholder="Rule name" value={name} onChange={e => setName(e.target.value)} style={{ ...field, width: 170 }} />
            <input aria-label="Account code" placeholder="Account code" value={account} onChange={e => setAccount(e.target.value)} style={{ ...field, width: 110 }} />
            <input aria-label="Words in the text" placeholder="Words in the text" value={text} onChange={e => setText(e.target.value)} style={{ ...field, width: 170 }} />
            <select aria-label="Journal type" value={type} onChange={e => setType(e.target.value)} style={field}>{ENTRY_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}</select>
            <select aria-label="Tag with" value={centre} onChange={e => setCentre(e.target.value)} style={field}>
              <option value="">Tag with…</option>{centres.filter(c => c.active).map(c => <option key={c.id} value={c.id}>{labelOf(c)}</option>)}</select>
            <input aria-label="Priority" type="number" title="Lower numbers are tried first" value={priority} onChange={e => setPriority(e.target.value)} style={{ ...field, width: 80 }} />
            <button style={btn} disabled={!!problem || add.isPending} onClick={() => add.mutate()}>Add rule</button>
          </div>
          {problem && name && <div style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginTop: 6 }}>{problem}</div>}
        </div>
      )}
      {canAdmin && rules.length > 0 && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>Apply to journals already posted</h3>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", fontSize: 12 }}>
            <label>From <input aria-label="Apply from" type="date" value={from} onChange={e => setFrom(e.target.value)} style={field} /></label>
            <label>To <input aria-label="Apply to" type="date" value={to} onChange={e => setTo(e.target.value)} style={field} /></label>
            <button style={btn} disabled={retro.isPending} onClick={() => retro.mutate(false)}>Preview</button>
            {result && !result.applied && result.matched > 0 && <button style={btn} disabled={retro.isPending} onClick={() => retro.mutate(true)}>Apply to {result.matched} lines</button>}
          </div>
          {result && (
            <div role="status" style={{ marginTop: 10, fontSize: 12 }}>
              {result.applied ? `Tagged ${result.matched} lines.` : `${result.matched} untagged lines match.`}
              {result.limitReached && " Only the first 5,000 lines were looked at; run it again for the rest."}
              {Object.entries(result.byRule).map(([n, c]) => <div key={n}>{n}: {c}</div>)}
              {result.samples.length > 0 && !result.applied && (
                <table style={{ borderCollapse: "collapse", marginTop: 6 }}><tbody>{result.samples.map((s, i) => (
                  <tr key={i}><td style={{ padding: "1px 8px 1px 0" }}>{s.date}</td><td style={{ padding: "1px 8px" }}>{s.entryNumber}</td><td style={{ padding: "1px 8px" }}>{s.accountCode}</td>
                    <td style={{ padding: "1px 8px" }}>{s.description}</td><td style={{ padding: "1px 8px" }}>→ {s.costCentre}</td></tr>))}</tbody></table>)}
              <div style={{ color: "var(--hf-text-secondary)", marginTop: 4 }}>No amount, account or date changes; balances stay exactly as they are.</div>
            </div>
          )}
        </div>
      )}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text, #b91c1c)", fontSize: 12 }}>{error}</div>}
    </div>
  )
}
