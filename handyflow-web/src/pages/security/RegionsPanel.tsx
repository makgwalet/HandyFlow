// src/pages/security/RegionsPanel.tsx
//
// Regions: groups of branches. A user limited to a region sees every site of every branch in it. Create, rename and delete here;
// put a branch in a region from the branch form. Only someone with the whole company can change regions (the server enforces it).
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { accessErrorText } from "./siteScopeLogic"
import type { RegionInfo } from "./regionAccessLogic"
import { NAME_MAX, deleteBlock, nameProblem, regionLine } from "./regionsLogic"

const muted: React.CSSProperties = { fontSize: 12, color: "var(--hf-text-muted)" }
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, minWidth: 0 }
const btn: React.CSSProperties = { padding: "5px 10px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", fontSize: 12 }

const unwrap = (r: { data?: { data?: unknown } & Record<string, unknown> } | undefined): RegionInfo[] | null => {
  const p = (r?.data?.data ?? r?.data) as unknown
  return Array.isArray(p) ? p as RegionInfo[] : null
}

export default function RegionsPanel() {
  const qc = useQueryClient()
  const list = useQuery<RegionInfo[]>({
    queryKey: ["security-regions"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/regions"); return unwrap(r) ?? [] },
  })
  const regions = list.data ?? []
  const [name, setName] = useState("")
  const [editing, setEditing] = useState<{ id: string; name: string } | null>(null)
  const [confirming, setConfirming] = useState<string | null>(null)
  const [problem, setProblem] = useState<string | null>(null)

  const done = (r: Parameters<typeof unwrap>[0]) => {
    setProblem(null); setConfirming(null)
    const fresh = unwrap(r)
    if (fresh) qc.setQueryData(["security-regions"], fresh)
    qc.invalidateQueries({ queryKey: ["security-regions"] }); qc.invalidateQueries({ queryKey: ["branches"] }); qc.invalidateQueries({ queryKey: ["security-branches"] })
  }
  const fail = (e: unknown) => setProblem(accessErrorText(e, "The change could not be saved."))
  const create = useMutation({ mutationFn: (n: string) => apiClient.post("/api/v1/security/regions", { name: n }), onSuccess: r => { done(r); setName("") }, onError: fail })
  const rename = useMutation({ mutationFn: (v: { id: string; name: string; description?: string | null }) => apiClient.put(`/api/v1/security/regions/${v.id}`, { name: v.name, description: v.description ?? null }), onSuccess: r => { done(r); setEditing(null) }, onError: fail })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/security/regions/${id}`), onSuccess: done, onError: fail })

  const newProblem = name.trim() ? nameProblem(name, regions) : null
  const renameProblem = editing ? nameProblem(editing.name, regions, editing.id) : null

  return (
    <section aria-label="Regions" style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 20 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>Regions</h3>
      <div style={{ ...muted, marginBottom: 10 }}>A region groups branches. A user limited to a region sees every site of every branch in it, including branches added later. Put a branch in a region when you edit the branch.</div>
      {list.isLoading && <div style={muted}>Loading regions…</div>}
      {list.error && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)" }}>{accessErrorText(list.error, "The regions could not be loaded.")}</div>}
      {list.data && regions.length === 0 && <div style={muted}>No regions yet.</div>}
      {regions.map(r => {
        const block = deleteBlock(r)
        return (
          <div key={r.id} style={{ display: "flex", gap: 8, alignItems: "center", padding: "6px 0", borderTop: "1px solid var(--hf-border-subtle)", flexWrap: "wrap" }}>
            {editing?.id === r.id ? (
              <>
                <input aria-label={`New name for ${r.name}`} style={{ ...field, flex: 1 }} maxLength={NAME_MAX + 20} value={editing.name} onChange={e => setEditing({ id: r.id, name: e.target.value })} />
                <button type="button" style={{ ...btn, opacity: renameProblem ? 0.5 : 1 }} disabled={!!renameProblem || rename.isPending} onClick={() => rename.mutate({ id: r.id, name: editing.name, description: r.description })}>Save</button>
                <button type="button" style={btn} onClick={() => setEditing(null)}>Cancel</button>
                {renameProblem && <span role="alert" style={{ ...muted, color: "var(--hf-danger-text)", flexBasis: "100%" }}>{renameProblem}</span>}
              </>
            ) : (
              <>
                <span style={{ flex: 1, fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>{r.name}<span style={{ ...muted, fontWeight: 400, marginLeft: 8 }}>{regionLine(r)}</span></span>
                <button type="button" style={btn} onClick={() => { setProblem(null); setConfirming(null); setEditing({ id: r.id, name: r.name }) }}>Rename</button>
                {confirming === r.id ? (
                  <>
                    <button type="button" style={{ ...btn, color: "var(--hf-danger-text-strong)" }} disabled={remove.isPending} onClick={() => remove.mutate(r.id)}>Confirm delete {r.name}</button>
                    <button type="button" style={btn} onClick={() => setConfirming(null)}>Keep</button>
                  </>
                ) : (
                  <button type="button" style={{ ...btn, opacity: block ? 0.5 : 1 }} disabled={!!block} title={block ?? undefined} aria-label={`Delete ${r.name}`} onClick={() => setConfirming(r.id)}>Delete</button>
                )}
              </>
            )}
          </div>
        )
      })}
      <div style={{ display: "flex", gap: 8, marginTop: 10, flexWrap: "wrap" }}>
        <input aria-label="New region name" placeholder="New region, e.g. KwaZulu-Natal" style={{ ...field, flex: 1, minWidth: 160 }} value={name} onChange={e => setName(e.target.value)} />
        <button type="button" style={{ ...btn, opacity: !name.trim() || newProblem ? 0.5 : 1 }} disabled={!name.trim() || !!newProblem || create.isPending} onClick={() => create.mutate(name)}>Add region</button>
      </div>
      {newProblem && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)", marginTop: 4 }}>{newProblem}</div>}
      {problem && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)", marginTop: 6 }}>{problem}</div>}
    </section>
  )
}
