// src/pages/customers/MergeDialog.tsx
// Review and confirm folding one customer record into another. The server previews what moves and what blocks the merge, and does the merge in one step.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { errorMessage, unwrapList } from "./crmApi"
import { fieldList, mergeBody, mergeSentence, movesList, resultText, valueText, type Choices, type MergePreview, type MergeResult } from "./merge.logic"

const btn: React.CSSProperties = { padding: "5px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, cursor: "pointer" }
interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
interface FieldRow { key: string; label: string }
const one = <T,>(r: { data?: unknown }): T | null => { const d = r?.data as { data?: T } | undefined; return d?.data ?? null }

export default function MergeDialog({ keepId, mergeId, onSwap, onClose, onDone }: { keepId: string; mergeId: string; onSwap: () => void; onClose: () => void; onDone: () => void }) {
  const qc = useQueryClient()
  const [choices, setChoices] = useState<Choices>({}), [sure, setSure] = useState(false), [error, setError] = useState<string | null>(null), [result, setResult] = useState<MergeResult | null>(null)
  const preview = useQuery({
    queryKey: ["crm", "merge-preview", keepId, mergeId], retry: false,
    queryFn: async () => one<MergePreview>(await apiClient.get("/api/v1/crm/customer-merge/preview", { params: { keepId, mergeId } })),
  })
  const users = useQuery({ queryKey: ["crm", "task-users"], retry: false, queryFn: async () => unwrapList<UserRow>(await apiClient.get("/api/v1/identity/users", { params: { size: 200 } })) })
  const fields = useQuery({ queryKey: ["crm", "custom-fields"], retry: false, queryFn: async () => unwrapList<FieldRow>(await apiClient.get("/api/v1/crm/custom-fields")) })
  const owners = Object.fromEntries((users.data ?? []).map(u => [u.id, [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email || "Colleague"]))
  const labels = Object.fromEntries((fields.data ?? []).filter(f => f.key).map(f => [f.key, f.label]))
  const p = preview.data
  const merge = useMutation({
    mutationFn: async () => one<MergeResult>(await apiClient.post("/api/v1/crm/customer-merge", mergeBody(keepId, mergeId, p?.conflicts ?? [], choices))),
    onSuccess: r => { setError(null); setResult(r); for (const k of ["duplicates", "customers", "pipeline", "campaigns"]) qc.invalidateQueries({ queryKey: ["crm", k] }); qc.invalidateQueries({ queryKey: ["customers"] }) },
    onError: e => { setSure(false); setError(errorMessage(e, "The merge could not be done. Nothing was changed.")) },
  })

  if (result) {
    return (
      <div role="status" aria-label="Merge done" style={{ display: "grid", gap: 6, fontSize: 13, marginTop: 10 }}>
        <strong>{resultText(result, labels)}</strong>
        {result.consentNote && <span>{result.consentNote}</span>}
        <div><button type="button" style={btn} onClick={onDone}>Done</button></div>
      </div>)
  }
  if (preview.isLoading) return <p style={{ fontSize: 13 }}>Checking what merging would do…</p>
  if (preview.isError || !p) return <div role="alert" style={{ fontSize: 13 }}>{errorMessage(preview.error, "Couldn't check the merge.")} <button type="button" style={btn} onClick={onClose}>Close</button></div>
  const moves = movesList(p.moves)
  return (
    <div role="group" aria-label="Merge review" style={{ display: "grid", gap: 8, fontSize: 13, marginTop: 10, padding: 12, border: "1px solid var(--hf-border)", borderRadius: 10, background: "var(--hf-surface)" }}>
      <strong>{mergeSentence(p)}</strong>
      <div><button type="button" style={btn} disabled={merge.isPending} onClick={() => { onSwap(); setChoices({}); setSure(false) }}>Keep {p.merge.name} instead</button></div>
      {p.blockers.map(b => <div key={b} role="alert" style={{ color: "var(--hf-danger-text)" }}>{b}.</div>)}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)" }}>{error}</div>}
      {p.conflicts.length > 0 && (
        <fieldset style={{ border: "1px solid var(--hf-border-subtle)", borderRadius: 8, margin: 0, padding: 8 }}>
          <legend>These differ. Choose which to keep.</legend>
          {p.conflicts.map(c => (
            <div key={c.field} role="radiogroup" aria-label={c.label} style={{ display: "flex", gap: 14, flexWrap: "wrap", alignItems: "center", padding: "3px 0" }}>
              <span style={{ minWidth: 130 }}>{c.label}</span>
              <label><input type="radio" name={`m-${c.field}`} checked={choices[c.field] !== "other"} onChange={() => setChoices({ ...choices, [c.field]: "keep" })} /> {valueText(c.field, c.keep, owners)}</label>
              <label><input type="radio" name={`m-${c.field}`} checked={choices[c.field] === "other"} onChange={() => setChoices({ ...choices, [c.field]: "other" })} /> {valueText(c.field, c.other, owners)}</label>
            </div>))}
        </fieldset>)}
      {p.willFill.length > 0 && <div>{p.keep.name} will also gain {fieldList(p.willFill, labels)} from {p.merge.name}, where it has none of its own.</div>}
      <div>{moves.length ? `Moved onto ${p.keep.name}: ${moves.join(", ")}.` : `${p.merge.name} has nothing else to move.`}</div>
      <div style={{ color: "var(--hf-text-muted)" }}>The removed record can be restored from the deleted list, but what was moved stays with {p.keep.name}.</div>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
        {!sure
          ? <button type="button" style={{ ...btn, fontWeight: 700 }} disabled={!p.canMerge || merge.isPending} onClick={() => setSure(true)}>Merge</button>
          : <button type="button" style={{ ...btn, fontWeight: 700, color: "var(--hf-danger-text)" }} disabled={merge.isPending} onClick={() => merge.mutate()}>{merge.isPending ? "Merging…" : `Yes, merge ${p.merge.name} into ${p.keep.name}`}</button>}
        <button type="button" style={btn} disabled={merge.isPending} onClick={onClose}>Cancel</button>
      </div>
    </div>
  )
}
