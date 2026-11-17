import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle } from "../warehousing/whseUi"
import { aliasProblem, knownSources, type AliasView } from "./importOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const B = "/api/v1/fuel/forecourt/imports"

/** Map what a pump or POS system calls a pump to the pump set up in Fuel. */
export default function PumpLabels({ seenSources }: { seenSources: { source: string }[] }) {
  const qc = useQueryClient()
  const [source, setSource] = useState(""); const [alias, setAlias] = useState(""); const [pumpId, setPumpId] = useState("")
  const labels = useQuery<AliasView[]>({ queryKey: ["fuel-import-aliases"], retry: false, queryFn: async () => { const d = unwrap(await apiClient.get(`${B}/aliases`)); return Array.isArray(d) ? d : [] } })
  const pumps = useQuery<any[]>({ queryKey: ["fuel-import-pumps"], retry: false, queryFn: async () => { const d = unwrap(await apiClient.get("/api/v1/fuel/forecourt/pumps")); return Array.isArray(d) ? d : (d?.content ?? []) } })
  const refresh = () => qc.invalidateQueries({ queryKey: ["fuel-import-aliases"] })
  const add = useMutation({ mutationFn: () => apiClient.post(`${B}/aliases`, { source: source.trim(), alias: alias.trim(), pumpId }), onSuccess: () => { setAlias(""); setPumpId(""); refresh() } })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`${B}/aliases/${id}`), onSuccess: refresh })
  const existing = labels.data ?? []
  const problem = aliasProblem(source, alias, pumpId, existing)
  const sources = knownSources(seenSources)
  return (
    <div style={{ ...card, padding: 16, marginTop: 14 }} aria-label="Pump labels">
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Pump labels</h3>
      <p style={{ margin: "0 0 10px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
        When a system calls a pump something other than its name in Fuel (a pump id, say), map that label here. Labels are per source. A pump with no label is still matched by its name.
      </p>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 8 }}>
        <input aria-label="Source" list="import-sources" placeholder="Source" value={source} onChange={e => setSource(e.target.value)} maxLength={40} style={{ ...inputStyle, width: 160 }} />
        <datalist id="import-sources">{sources.map(s => <option key={s} value={s} />)}</datalist>
        <input aria-label="Label the sender uses" placeholder="Label, e.g. P-01" value={alias} onChange={e => setAlias(e.target.value)} maxLength={40} style={{ ...inputStyle, width: 160 }} />
        <select aria-label="Fuel pump" value={pumpId} onChange={e => setPumpId(e.target.value)} style={{ ...inputStyle, width: 200 }}>
          <option value="">Choose the Fuel pump…</option>
          {(pumps.data ?? []).filter(p => p.active !== false).map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
        <button style={btnPrimary} disabled={!!problem || add.isPending} onClick={() => add.mutate()}>Add label</button>
      </div>
      {problem && (source || alias || pumpId) && <p role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", margin: "0 0 6px" }}>{problem}</p>}
      <ErrorLine error={add.error ?? remove.error} fallback="Could not change the label" />
      {existing.map(a => (
        <div key={a.id} style={{ display: "flex", gap: 10, alignItems: "center", padding: "6px 0", borderTop: "1px solid var(--hf-border)", fontSize: 13 }}>
          <div style={{ flex: 1 }}><strong>{a.source}</strong>: <code>{a.alias}</code> is {a.pumpName}</div>
          <button style={btnGhost} onClick={() => remove.mutate(a.id)}>Remove</button>
        </div>))}
      {labels.data && existing.length === 0 && <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>No labels yet.</p>}
    </div>
  )
}
