// src/pages/security/BranchAccessEditor.tsx
//
// The branch part of one user's site access: which branches they can view or manage. Shown inside the site access panel.
// Only someone with the whole company can change it, and nobody can change their own (the server enforces both; `block` says why).
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { accessErrorText, type ScopeView } from "./siteScopeLogic"
import {
  CHOICES, CHOICE_LABEL, branchBody, branchRows, branchSummary, branchWarning, branchesChanged, choicesFrom, overLimit, setChoice,
  type BranchChoice, type BranchInfo,
} from "./branchAccessLogic"

const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, minWidth: 0 }
const btn: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", fontSize: 13 }
const sub: React.CSSProperties = { fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, margin: "14px 0 6px" }

export default function BranchAccessEditor({ userId, view, block }: { userId: string; view: ScopeView; block: string | null }) {
  const qc = useQueryClient()
  const all = useQuery<BranchInfo[]>({
    queryKey: ["security-branches"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/branches"); const p = r.data?.data ?? r.data; return Array.isArray(p) ? p as BranchInfo[] : [] },
  })
  const [draft, setDraft] = useState<Record<string, BranchChoice> | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  // Until the branch list is here, a branch the user holds would look switched off. Show nothing editable until it has arrived.
  const ready = !!all.data
  const rows = ready ? branchRows(all.data ?? [], view.branches) : []
  const choices = draft ?? choicesFrom(view.branches)
  const sent = branchBody(choices, rows).branches.length
  const dirty = ready && branchesChanged(view.branches, choices, rows)
  const limit = overLimit(choices)
  const warning = dirty ? branchWarning(view, sent) : null

  const save = useMutation({
    mutationFn: async () => apiClient.put(`/api/v1/security/users/${userId}/branch-access`, branchBody(choices, rows)),
    onSuccess: r => {
      setProblem(null); setSaved(true); setDraft(null)
      const fresh = (r?.data?.data ?? r?.data) as ScopeView | undefined
      if (fresh && Array.isArray(fresh.branches)) qc.setQueryData(["user-site-scope", userId], fresh)
      qc.invalidateQueries({ queryKey: ["user-site-scope", userId] }); qc.invalidateQueries({ queryKey: ["my-site-scope"] }); qc.invalidateQueries({ queryKey: ["site-access-overview"] })
    },
    onError: e => { setSaved(false); setProblem(accessErrorText(e, "Branch access could not be saved.")) },
  })

  return (
    <div aria-label="Branch access">
      <div style={sub}>Branches (every site of the branch)</div>
      {all.isLoading && <div style={muted}>Loading branches…</div>}
      {all.error && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)" }}>{accessErrorText(all.error, "The branches could not be loaded.")}</div>}
      {all.error && view.branches.length > 0 && (
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>{view.branches.map(b => <span key={b.id} style={muted}>{b.name} · {CHOICE_LABEL[b.role === "MANAGER" ? "MANAGER" : "VIEWER"]}</span>)}</div>
      )}
      {all.data && rows.length === 0 && <div style={muted}>No branches yet. Create branches under Sites &amp; assets.</div>}
      {rows.map(r => (
        <div key={r.id} style={{ display: "flex", gap: 8, alignItems: "center", padding: "4px 0", fontSize: 13 }}>
          <span style={{ flex: 1 }}>{r.name}{r.region ? ` · ${r.region}` : ""}{r.off ? " (branch switched off)" : ""}</span>
          <select aria-label={`Access to ${r.name}`} style={field} disabled={!!block || r.off} value={r.off ? "NONE" : choices[r.id] ?? "NONE"} onChange={e => setDraft(setChoice(choices, r.id, e.target.value as BranchChoice))}>
            {CHOICES.map(c => <option key={c} value={c}>{CHOICE_LABEL[c]}</option>)}
          </select>
        </div>
      ))}
      {rows.length > 0 && <div style={{ ...muted, marginTop: 4 }}>{branchSummary(rows, choices)}</div>}
      {rows.some(r => r.off) && dirty && <div role="status" style={{ ...muted, marginTop: 4 }}>Saving removes access through a branch that has been switched off.</div>}
      {limit && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)", marginTop: 6 }}>{limit}</div>}
      {warning && <div role="status" style={{ ...muted, color: "var(--hf-warning-text)", marginTop: 6 }}>{warning}</div>}
      {problem && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)", marginTop: 6 }}>{problem}</div>}
      {saved && !dirty && <div role="status" style={{ ...muted, color: "var(--hf-success-text-strong)", marginTop: 6 }}>Branch access saved.</div>}
      <div style={{ marginTop: 8 }}>
        <button type="button" style={{ ...btn, opacity: block || !dirty || limit ? 0.5 : 1 }} disabled={!!block || !dirty || !!limit || save.isPending} onClick={() => { setSaved(false); save.mutate() }}>Save branch access</button>
      </div>
    </div>
  )
}
