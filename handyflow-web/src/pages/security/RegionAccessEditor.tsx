// src/pages/security/RegionAccessEditor.tsx
//
// The region part of one user's site access: which regions they are limited to. Shown inside the site access panel, under the branches.
// Only someone with the whole company can change it, and nobody can change their own (the server enforces both; `block` says why).
// Shows nothing when the company has no regions and the user holds none, so companies that do not use regions never see it.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { accessErrorText, regionsOf, type ScopeView } from "./siteScopeLogic"
import { emptyRegionNote, regionBody, regionOverLimit, regionRows, regionSummary, regionWarning, regionsChanged, selectedFrom, toggle, type RegionInfo } from "./regionAccessLogic"

const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const btn: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", fontSize: 13 }
const sub: React.CSSProperties = { fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, margin: "14px 0 6px" }

export default function RegionAccessEditor({ userId, view, block }: { userId: string; view: ScopeView; block: string | null }) {
  const qc = useQueryClient()
  const all = useQuery<RegionInfo[]>({
    queryKey: ["security-regions"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/regions"); const p = r.data?.data ?? r.data; return Array.isArray(p) ? p as RegionInfo[] : [] },
  })
  const [draft, setDraft] = useState<string[] | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const ready = !!all.data
  const rows = ready ? regionRows(all.data ?? [], view) : []
  const ids = draft ?? selectedFrom(view)
  const dirty = ready && regionsChanged(view, ids)
  const limit = regionOverLimit(ids)
  const warning = dirty ? regionWarning(view, ids) : null
  const note = dirty ? emptyRegionNote(rows, ids, view) : null

  const save = useMutation({
    mutationFn: async () => apiClient.put(`/api/v1/security/users/${userId}/region-access`, regionBody(ids)),
    onSuccess: r => {
      setProblem(null); setSaved(true); setDraft(null)
      const fresh = (r?.data?.data ?? r?.data) as ScopeView | undefined
      if (fresh && Array.isArray(fresh.branches)) qc.setQueryData(["user-site-scope", userId], fresh)
      qc.invalidateQueries({ queryKey: ["user-site-scope", userId] }); qc.invalidateQueries({ queryKey: ["my-site-scope"] }); qc.invalidateQueries({ queryKey: ["site-access-overview"] }); qc.invalidateQueries({ queryKey: ["security-regions"] })
    },
    onError: e => { setSaved(false); setProblem(accessErrorText(e, "Region access could not be saved.")) },
  })

  const holds = regionsOf(view).length > 0
  if (all.isLoading) return null
  if (all.data && all.data.length === 0 && !holds) return null

  return (
    <div aria-label="Region access">
      <div style={sub}>Regions (every branch in the region, now and later)</div>
      {all.error && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)" }}>{accessErrorText(all.error, "The regions could not be loaded.")}</div>}
      {all.error && holds && <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>{regionsOf(view).map(r => <span key={r.id} style={muted}>{r.name}</span>)}</div>}
      {rows.map(r => (
        <label key={r.id} style={{ display: "flex", gap: 8, alignItems: "center", padding: "4px 0", fontSize: 13 }}>
          <input type="checkbox" disabled={!!block} checked={ids.includes(r.id)} onChange={() => setDraft(toggle(ids, r.id))} />
          <span style={{ flex: 1 }}>{r.name}</span>
          <span style={muted}>{r.branchIds.length === 1 ? "1 branch" : `${r.branchIds.length} branches`}</span>
        </label>
      ))}
      {ready && rows.length > 0 && <div style={{ ...muted, marginTop: 4 }}>{regionSummary(ids)}</div>}
      {limit && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)", marginTop: 6 }}>{limit}</div>}
      {warning && <div role="status" style={{ ...muted, color: "var(--hf-warning-text)", marginTop: 6 }}>{warning}</div>}
      {note && <div role="status" style={{ ...muted, color: "var(--hf-warning-text)", marginTop: 6 }}>{note}</div>}
      {problem && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)", marginTop: 6 }}>{problem}</div>}
      {saved && !dirty && <div role="status" style={{ ...muted, color: "var(--hf-success-text-strong)", marginTop: 6 }}>Region access saved.</div>}
      <div style={{ marginTop: 8 }}>
        <button type="button" style={{ ...btn, opacity: block || !dirty || limit ? 0.5 : 1 }} disabled={!!block || !dirty || !!limit || save.isPending} onClick={() => { setSaved(false); save.mutate() }}>Save region access</button>
      </div>
    </div>
  )
}
