// src/pages/customers/CompanyGroupPanel.tsx
// One company's place in a group: who is above it, its branches, and the opportunities of the whole group. The server refuses loops and over-deep groups.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { money } from "./pipeline.logic"
import { errorMessage } from "./crmApi"
import { CompanyPicker } from "./CompanyPicker"
import type { Hierarchy } from "./opportunities.logic"
import { ErrorNote } from "./CrmKit"

const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const ghost: React.CSSProperties = { ...field, cursor: "pointer" }

export function CompanyGroupPanel({ customerId }: { customerId: string }) {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE")
  const [choosing, setChoosing] = useState(false), [parentId, setParentId] = useState(""), [error, setError] = useState<string | null>(null)
  const { data, isLoading, isError } = useQuery<Hierarchy>({
    queryKey: ["crm", "hierarchy", customerId],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/crm/hierarchy/${customerId}`); const o = r.data as { data?: Hierarchy }; return (o && "data" in o && o.data ? o.data : r.data) as Hierarchy },
  })
  const set = useMutation({
    mutationFn: (id: string | null) => apiClient.put(`/api/v1/crm/hierarchy/${customerId}/parent`, { parentId: id }),
    onSuccess: () => { setError(null); setChoosing(false); setParentId(""); qc.invalidateQueries({ queryKey: ["crm", "hierarchy"] }) },
    onError: e => setError(errorMessage(e, "Something went wrong. Try again.")),
  })
  if (isLoading) return <p style={{ fontSize: 12 }}>Loading…</p>
  if (isError || !data) return <ErrorNote>Couldn't load the company group.</ErrorNote>
  const parent = data.ancestors.length ? data.ancestors[data.ancestors.length - 1] : null
  const r = data.rollup
  return (
    <div style={{ display: "grid", gap: 12, fontSize: 13 }}>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)" }}>{error}</div>}
      <section aria-label="Parent company">
        <h4 style={{ margin: "0 0 4px", fontSize: 13 }}>Parent company</h4>
        {data.ancestors.length > 0 && <nav aria-label="Group path" style={{ color: "var(--hf-text-muted)", marginBottom: 4 }}>{data.ancestors.map(a => a.name).join(" › ")} › <strong style={{ color: "var(--hf-text)" }}>{data.self.name}</strong></nav>}
        {parent ? <div>{parent.name}</div> : <div style={{ color: "var(--hf-text-muted)" }}>None. This is a top-level company.</div>}
        {canEdit && !choosing && (
          <div style={{ display: "flex", gap: 6, marginTop: 6 }}>
            <button type="button" style={ghost} onClick={() => { setChoosing(true); setError(null) }}>{parent ? "Change parent" : "Set parent company"}</button>
            {parent && <button type="button" style={ghost} disabled={set.isPending} onClick={() => set.mutate(null)}>Make top-level</button>}
          </div>)}
        {canEdit && choosing && (
          <form aria-label="Choose parent company" onSubmit={e => { e.preventDefault(); if (parentId) set.mutate(parentId) }} style={{ display: "grid", gap: 6, maxWidth: 420, marginTop: 6 }}>
            <CompanyPicker label="Parent company" value={parentId} onChange={id => setParentId(id)} exclude={[customerId, ...data.children.map(c => c.id)]} />
            <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!parentId || set.isPending}>{set.isPending ? "Saving…" : "Save parent"}</button><button type="button" style={ghost} onClick={() => { setChoosing(false); setError(null) }}>Cancel</button></div>
          </form>)}
      </section>
      <section aria-label="Branches">
        <h4 style={{ margin: "0 0 4px", fontSize: 13 }}>Branches ({data.children.length}{data.branchCount > data.children.length ? `, ${data.branchCount} in total` : ""})</h4>
        {data.children.length === 0 ? <div style={{ color: "var(--hf-text-muted)" }}>No branches. Open a branch and set this company as its parent to add one.</div>
          : <ul style={{ margin: 0, paddingLeft: 18 }}>{data.children.map(c => <li key={c.id}>{c.name}</li>)}</ul>}
      </section>
      <section aria-label="Group opportunities">
        <h4 style={{ margin: "0 0 4px", fontSize: 13 }}>Opportunities across the group ({r.companies} {r.companies === 1 ? "company" : "companies"})</h4>
        <div style={{ display: "flex", gap: 18, flexWrap: "wrap" }}>
          <span><strong>{r.openCount}</strong> open</span><span>Open value <strong>{money(r.openValue)}</strong></span><span>Weighted <strong>{money(r.weightedValue)}</strong></span><span>Won <strong>{money(r.wonValue)}</strong> ({r.wonCount})</span>
        </div>
      </section>
    </div>
  )
}
