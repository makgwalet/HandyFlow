// Rules that recognise bank lines (for example "ENGEN" is fuel) so they can be categorised in bulk.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { DIRECTIONS, FIELDS, emptyRule, errorOf, rowsOf, ruleProblem, ruleRequest, ruleSummary, type Account, type BankAccount, type Rule } from "./bookkeeping.logic"
import ClientScope from "./ClientScope"
import { btn, card, danger, field, muted, primary, td, th } from "./ui"

export default function BankRulesTab() {
  return <ClientScope>{clientId => <Rules clientId={clientId} />}</ClientScope>
}

function Rules({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("BOOKKEEPING_MANAGE") || usePermission("BOOKKEEPING_ADMIN")
  const base = `/api/v1/bookkeeping/clients/${clientId}`
  const [form, setForm] = useState(emptyRule())
  const [error, setError] = useState<string | null>(null)
  const set = (k: keyof typeof form, v: string) => setForm(f => ({ ...f, [k]: v }))
  const { data: rules = [] } = useQuery<Rule[]>({ queryKey: ["bk-rules", clientId], queryFn: async () => rowsOf<Rule>(await apiClient.get(`${base}/bank-rules`)) })
  const { data: accounts = [] } = useQuery<Account[]>({ queryKey: ["bk-accounts", clientId], queryFn: async () => rowsOf<Account>(await apiClient.get(`${base}/accounts`)) })
  const { data: banks = [] } = useQuery<BankAccount[]>({ queryKey: ["bk-banks", clientId], queryFn: async () => rowsOf<BankAccount>(await apiClient.get(`${base}/bank-accounts?size=100`)) })
  const changed = (r: any) => { setError(null); qc.setQueryData(["bk-rules", clientId], rowsOf<Rule>(r)); qc.invalidateQueries({ queryKey: ["bk-suggestions"] }) }
  const fail = (e: any) => setError(errorOf(e))
  const add = useMutation({ mutationFn: () => apiClient.post(`${base}/bank-rules`, ruleRequest(form)), onSuccess: r => { changed(r); setForm(emptyRule()) }, onError: fail })
  const toggle = useMutation({ mutationFn: (r: Rule) => apiClient.post(`${base}/bank-rules/${r.id}/active?active=${!r.active}`), onSuccess: changed, onError: fail })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`${base}/bank-rules/${id}`), onSuccess: changed, onError: fail })
  const problem = ruleProblem(form)
  const nameOf = (id: string) => { const a = accounts.find(x => x.id === id); return a ? `${a.accountCode} ${a.accountName}` : "—" }
  return (
    <div>
      <p style={muted}>The lowest priority number is tried first; if two rules tie, the one looking for longer text wins. Switching a rule off keeps it for later.</p>
      {rules.length > 0 && (
        <div style={card}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={th}>Priority</th><th style={th}>Rule</th><th style={th}>When</th><th style={th}>Book to</th><th /></tr></thead>
            <tbody>{rules.map(r => (
              <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border)", opacity: r.active ? 1 : 0.55 }}>
                <td style={td}>{r.priority}</td><td style={td}><b>{r.name}</b></td><td style={td}>{ruleSummary(r)}</td><td style={td}>{nameOf(r.contraAccountId)}</td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canManage && <>
                  <button style={btn} onClick={() => toggle.mutate(r)}>{r.active ? "Switch off" : "Switch on"}</button>{" "}
                  <button style={btn} onClick={() => { if (window.confirm(`Remove the rule “${r.name}”?`)) remove.mutate(r.id) }}>Remove</button></>}</td>
              </tr>))}</tbody>
          </table>
        </div>
      )}
      {rules.length === 0 && <p style={muted}>No rules yet.</p>}
      {canManage && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>New rule</h3>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <input aria-label="Rule name" placeholder="Rule name" value={form.name} onChange={e => set("name", e.target.value)} style={{ ...field, width: 160 }} />
            <select aria-label="Bank account" value={form.bankAccountId} onChange={e => set("bankAccountId", e.target.value)} style={field}>
              <option value="">Any bank account</option>{banks.map(b => <option key={b.id} value={b.id}>{b.bankName} {b.accountNumber}</option>)}</select>
            <select aria-label="Direction" value={form.direction} onChange={e => set("direction", e.target.value)} style={field}>{DIRECTIONS.map(d => <option key={d.value} value={d.value}>{d.label}</option>)}</select>
            <input aria-label="Words to look for" placeholder="Words to look for" value={form.text} onChange={e => set("text", e.target.value)} style={{ ...field, width: 160 }} />
            <select aria-label="Look in" value={form.field} onChange={e => set("field", e.target.value)} style={field}>{FIELDS.map(d => <option key={d.value} value={d.value}>{d.label}</option>)}</select>
            <input aria-label="Smallest amount" placeholder="Smallest amount" value={form.min} onChange={e => set("min", e.target.value)} style={{ ...field, width: 110 }} />
            <input aria-label="Largest amount" placeholder="Largest amount" value={form.max} onChange={e => set("max", e.target.value)} style={{ ...field, width: 110 }} />
            <select aria-label="Book to account" value={form.contraAccountId} onChange={e => set("contraAccountId", e.target.value)} style={{ ...field, minWidth: 200 }}>
              <option value="">Book to…</option>{accounts.map(a => <option key={a.id} value={a.id}>{a.accountCode} {a.accountName}</option>)}</select>
            <input aria-label="Priority" type="number" title="Lower numbers are tried first" value={form.priority} onChange={e => set("priority", e.target.value)} style={{ ...field, width: 80 }} />
            <button style={primary} disabled={!!problem || add.isPending} onClick={() => add.mutate()}>Add rule</button>
          </div>
          {problem && form.name && <div style={{ ...muted, marginTop: 6 }}>{problem}</div>}
          {error && <div role="alert" style={{ ...danger, marginTop: 6 }}>{error}</div>}
        </div>
      )}
    </div>
  )
}
