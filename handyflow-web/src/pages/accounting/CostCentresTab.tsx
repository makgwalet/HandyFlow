// src/pages/accounting/CostCentresTab.tsx
//
// Finance F24: the list of cost centres, departments, branches and projects that journal lines can be tagged with.
// Switched-off ones keep their history but cannot be used on new journals. Nothing is deleted.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { KINDS, kindLabel, parentChoices, treeOrder, labelOf, type CostCentre } from "./costCentre.logic"

const unwrap = (r: any) => r.data?.data ?? r.data
const errorOf = (e: any) => e?.response?.data?.message ?? "Something went wrong. Please try again."
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14 }
const btn: React.CSSProperties = { padding: "7px 13px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontWeight: 600, fontSize: 12, cursor: "pointer" }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 6, fontSize: 12, background: "var(--hf-surface)", color: "var(--hf-text)" }

export default function CostCentresTab() {
  const qc = useQueryClient()
  const canManage = usePermission("ACCOUNTING_MANAGE")
  const [editing, setEditing] = useState<string | null>(null)
  const [code, setCode] = useState("")
  const [name, setName] = useState("")
  const [kind, setKind] = useState("COST_CENTRE")
  const [parentId, setParentId] = useState("")
  const [error, setError] = useState<string | null>(null)

  const { data: list = [] } = useQuery<CostCentre[]>({
    queryKey: ["acc-cost-centres"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/cost-centres")) ?? [],
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ["acc-cost-centres"] })
  const reset = () => { setEditing(null); setCode(""); setName(""); setKind("COST_CENTRE"); setParentId(""); setError(null) }

  const save = useMutation({
    mutationFn: () => {
      const body = { code: code.trim(), name: name.trim(), kind, parentId: parentId || null }
      return editing ? apiClient.put(`/api/v1/accounting/cost-centres/${editing}`, body) : apiClient.post("/api/v1/accounting/cost-centres", body)
    },
    onSuccess: () => { reset(); refresh() },
    onError: e => setError(errorOf(e)),
  })
  const toggle = useMutation({
    mutationFn: (c: CostCentre) => apiClient.post(`/api/v1/accounting/cost-centres/${c.id}/active`, { active: !c.active }),
    onSuccess: refresh,
    onError: e => setError(errorOf(e)),
  })

  const rows = treeOrder(list)
  const startEdit = (c: CostCentre) => { setEditing(c.id); setCode(c.code); setName(c.name); setKind(c.kind); setParentId(c.parentId ?? ""); setError(null) }

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Cost centres</h2>
      <p style={{ fontSize: 13, color: "var(--hf-text-secondary)" }}>
        Tag journal lines with a cost centre, department, branch or project, then see profit and loss for each one.
        Lines without a tag stay valid and show as Unallocated.
      </p>
      {rows.length === 0 && <p style={{ fontSize: 13 }}>No cost centres yet.</p>}
      {rows.length > 0 && (
        <div style={card}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr style={{ textAlign: "left", fontSize: 11, color: "var(--hf-text-muted)", textTransform: "uppercase" }}>
              <th>Code</th><th>Name</th><th>Kind</th><th style={{ textAlign: "right" }}>Journal lines</th><th>Status</th><th /></tr></thead>
            <tbody>
              {rows.map(({ c, depth }) => (
                <tr key={c.id} style={{ borderTop: "1px solid var(--hf-border)", opacity: c.active ? 1 : 0.6 }}>
                  <td style={{ fontFamily: "monospace", paddingLeft: depth * 16 }}>{c.code}</td>
                  <td>{c.name}</td>
                  <td>{kindLabel(c.kind)}</td>
                  <td style={{ textAlign: "right" }}>{c.lineCount}</td>
                  <td>{c.active ? "Active" : "Switched off"}</td>
                  <td style={{ textAlign: "right" }}>
                    {canManage && <>
                      <button style={btn} onClick={() => startEdit(c)}>Edit</button>{" "}
                      <button style={btn} disabled={toggle.isPending} onClick={() => toggle.mutate(c)}>{c.active ? "Switch off" : "Switch on"}</button>
                    </>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {canManage && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>{editing ? "Edit cost centre" : "New cost centre"}</h3>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <input aria-label="Code" placeholder="Code, e.g. DBN" value={code} onChange={e => setCode(e.target.value)} style={{ ...field, width: 110 }} />
            <input aria-label="Name" placeholder="Name, e.g. Durban branch" value={name} onChange={e => setName(e.target.value)} style={{ ...field, width: 220 }} />
            <select aria-label="Kind" value={kind} onChange={e => setKind(e.target.value)} style={field}>
              {KINDS.map(k => <option key={k.value} value={k.value}>{k.label}</option>)}
            </select>
            <select aria-label="Inside" value={parentId} onChange={e => setParentId(e.target.value)} style={field}>
              <option value="">Top level</option>
              {parentChoices(list, editing).map(c => <option key={c.id} value={c.id}>{labelOf(c)}</option>)}
            </select>
            <button style={btn} disabled={!code.trim() || !name.trim() || save.isPending} onClick={() => save.mutate()}>{editing ? "Save" : "Add"}</button>
            {editing && <button style={btn} onClick={reset}>Cancel</button>}
          </div>
          {error && <div role="alert" style={{ color: "var(--hf-danger-text, #b91c1c)", fontSize: 12, marginTop: 6 }}>{error}</div>}
        </div>
      )}
    </div>
  )
}
