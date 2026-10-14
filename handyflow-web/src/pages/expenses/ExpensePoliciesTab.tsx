// src/pages/expenses/ExpensePoliciesTab.tsx
// Finance patch F18: limits, receipt rules and age limits per category. Duplicate checks need no setup.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { CATEGORY_OPTIONS, describePolicy, emptyPolicy, policyBody, policyProblem, type PolicyForm } from "./expensePolicy.logic"

const unwrap = (r: any) => r.data?.data ?? r.data
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 12 }
const btn: React.CSSProperties = { padding: "7px 13px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontWeight: 600, fontSize: 12, cursor: "pointer" }
const input: React.CSSProperties = { width: "100%", padding: "7px 9px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 12, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }

export default function ExpensePoliciesTab() {
  const qc = useQueryClient()
  const canEdit = usePermission("EXPENSES_ADMIN")
  const [form, setForm] = useState<PolicyForm>(emptyPolicy())
  const [error, setError] = useState<string | null>(null)

  const { data: policies = [] } = useQuery<any[]>({
    queryKey: ["expense-policies"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/expenses/policies")) ?? [],
  })
  const done = () => { setError(null); qc.invalidateQueries({ queryKey: ["expense-policies"] }) }
  const fail = (e: any) => setError(e?.response?.data?.message ?? "Something went wrong. Please try again.")
  const save = useMutation({ mutationFn: () => apiClient.put("/api/v1/expenses/policies", policyBody(form)), onSuccess: () => { done(); setForm(emptyPolicy()) }, onError: fail })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/expenses/policies/${id}`), onSuccess: done, onError: fail })
  const problem = policyProblem(form)
  const set = (patch: Partial<PolicyForm>) => setForm(f => ({ ...f, ...patch }))

  return (
    <div>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", maxWidth: 620 }}>
        A category's own policy applies to its claims; "Every other category" covers the rest. Claims that look like one already made
        (same person and amount within three days) are always flagged to the approver.
      </p>
      {error && <div role="alert" style={{ ...card, color: "var(--hf-danger-text)", background: "var(--hf-danger-soft)" }}>{error}</div>}

      {policies.length === 0 ? <div style={{ color: "var(--hf-text-faint)", fontSize: 13, marginBottom: 12 }}>No policies yet: nothing is limited.</div> : policies.map(p => (
        <div key={p.id} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <strong>{p.category === "*" ? "Every other category" : p.category.replace(/_/g, " ")}</strong>
            {canEdit && <button style={btn} onClick={() => remove.mutate(p.id)} aria-label={`Remove policy for ${p.category}`}>Remove</button>}
          </div>
          <ul style={{ margin: "6px 0 0", paddingLeft: 18, fontSize: 12 }}>{describePolicy(p).map(t => <li key={t}>{t}</li>)}</ul>
        </div>
      ))}

      {canEdit && (
        <div style={card}>
          <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 8 }}>Set a policy</div>
          <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr 1fr 1fr", gap: 8, marginBottom: 8 }}>
            <select aria-label="Category" value={form.category} onChange={e => set({ category: e.target.value })} style={input}>
              <option value="*">Every other category</option>
              {CATEGORY_OPTIONS.map(c => <option key={c} value={c}>{c.replace(/_/g, " ")}</option>)}
            </select>
            <input aria-label="Limit per claim" type="number" placeholder="Limit per claim" value={form.maxPerClaim} onChange={e => set({ maxPerClaim: e.target.value })} style={input} />
            <select aria-label="Over the limit" value={form.limitAction} onChange={e => set({ limitAction: e.target.value })} style={input}>
              <option value="WARN">Over limit: flag</option>
              <option value="BLOCK">Over limit: refuse</option>
            </select>
            <input aria-label="Receipt above" type="number" placeholder="Receipt above (0 = always)" value={form.receiptAbove} onChange={e => set({ receiptAbove: e.target.value })} style={input} />
            <input aria-label="Maximum age in days" type="number" placeholder="Max age (days)" value={form.maxAgeDays} onChange={e => set({ maxAgeDays: e.target.value })} style={input} />
          </div>
          {problem && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 6 }}>{problem}</div>}
          <button style={btn} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>Save policy</button>
        </div>
      )}
    </div>
  )
}
