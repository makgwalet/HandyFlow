// src/pages/customers/DuplicatesPanel.tsx
// Customers that look like the same business entered more than once. Nothing is merged: the person can say "not a duplicate"
// (the pair is remembered) or delete the extra record (it can be restored).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { groupKey, oldestFirst, summaryText, differingFields, type Duplicates, type DupGroup } from "./duplicates.logic"

const inner = (r: { data: unknown }): unknown => { const o = r.data as { data?: unknown } | null; return o && !Array.isArray(o) && typeof o === "object" && "data" in o ? o.data : r.data }
const asDuplicates = (r: { data: unknown }): Duplicates => { const d = inner(r) as Partial<Duplicates> | null; return { groups: Array.isArray(d?.groups) ? d.groups : [], scanned: d?.scanned ?? 0, truncated: !!d?.truncated } }
const errorText = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const btn: React.CSSProperties = { padding: "4px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12, cursor: "pointer" }

export default function DuplicatesPanel() {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE"), canDelete = usePermission("CUSTOMER_DELETE")
  const [error, setError] = useState<string | null>(null), [confirm, setConfirm] = useState<string | null>(null)
  const { data, isLoading, isError } = useQuery<Duplicates>({ queryKey: ["crm", "duplicates"], queryFn: async () => asDuplicates(await apiClient.get("/api/v1/crm/customers/duplicates")) })
  const refresh = () => { setError(null); setConfirm(null); qc.invalidateQueries({ queryKey: ["crm", "duplicates"] }) }
  const dismiss = useMutation({ mutationFn: (g: DupGroup) => apiClient.post("/api/v1/crm/customers/duplicates/dismiss", { customerIds: g.customers.map(c => c.id) }), onSuccess: refresh, onError: e => setError(errorText(e)) })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/customers/${id}`), onSuccess: refresh, onError: e => setError(errorText(e)) })
  const busy = dismiss.isPending || remove.isPending

  if (isLoading) return <p>Looking for duplicates…</p>
  if (isError || !data) return <p role="alert">Couldn't check for duplicates. Try again.</p>
  return (
    <div>
      <p aria-label="Duplicate summary" style={{ fontSize: 13, margin: "0 0 10px" }}>{summaryText(data)}</p>
      {data.truncated && <p role="status" style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Only the first {data.scanned} customers were checked.</p>}
      {error && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</p>}
      {data.groups.map(g => {
        const key = groupKey(g), members = oldestFirst(g), differ = differingFields(g)
        return (
          <section key={key} aria-label={`Possible duplicates: ${members.map(m => m.name).join(", ")}`} style={{ border: "1px solid var(--hf-border-subtle)", borderRadius: 10, padding: 12, marginBottom: 10 }}>
            <p style={{ margin: "0 0 8px", fontSize: 12, color: "var(--hf-text-muted)" }}>{g.reasons.join(" · ")}</p>
            <div style={{ display: "grid", gridTemplateColumns: `repeat(${Math.min(members.length, 3)}, minmax(0, 1fr))`, gap: 10 }}>
              {members.map((m, i) => (
                <div key={m.id} style={{ fontSize: 13 }}>
                  <strong>{m.name}</strong> <span style={{ color: "var(--hf-text-muted)" }}>{i === 0 ? "(oldest)" : ""}</span>
                  <div style={{ color: "var(--hf-text-muted)" }}>{m.customerType === "LEAD" ? "Lead" : "Customer"} · added {m.createdAt.slice(0, 10)}</div>
                  <div style={{ fontWeight: differ.includes("email") ? 700 : 400 }}>{m.email ?? "No email"}</div>
                  <div style={{ fontWeight: differ.includes("phone") ? 700 : 400 }}>{m.phone ?? "No phone"}</div>
                  <div style={{ fontWeight: differ.includes("taxNumber") ? 700 : 400 }}>{m.taxNumber ? `VAT ${m.taxNumber}` : "No VAT number"}</div>
                  {canDelete && (confirm === m.id
                    ? <span role="group" aria-label={`Confirm delete ${m.name}`}>
                        <button type="button" style={{ ...btn, color: "var(--hf-danger-text)" }} disabled={busy} onClick={() => remove.mutate(m.id)}>Yes, delete {m.name}</button>{" "}
                        <button type="button" style={btn} onClick={() => setConfirm(null)}>Keep</button>
                      </span>
                    : <button type="button" style={btn} disabled={busy} aria-label={`Delete ${m.name}`} onClick={() => setConfirm(m.id)}>Delete (restorable)</button>)}
                </div>))}
            </div>
            {canEdit && <div style={{ marginTop: 10 }}><button type="button" style={btn} disabled={busy} onClick={() => dismiss.mutate(g)}>Not a duplicate</button></div>}
          </section>)
      })}
    </div>
  )
}
