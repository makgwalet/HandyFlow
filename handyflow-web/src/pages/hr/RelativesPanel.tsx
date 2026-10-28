// Next of kin and dependants. HR keeps them for an employee; an employee keeps their own. The same panel serves both, given the paths.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { fmtDate } from "./er.logic"
import { card, errorText, input, label, plain, primary, td, th, unwrap } from "./attendance.shared"
import { Notice } from "./ErParts"
import {
  RELATIONSHIPS, beneficiaryText, emptyRelative, fromRelative, relativeBody, relativeProblem, sharesTotal,
  type RelativeForm, type RelativeView,
} from "./profile.logic"

interface Props {
  /** List and create here; update and delete at `${base}/${id}`. */
  base: string
  canEdit: boolean
  /** Distinguishes the cache of one employee from another's. */
  cacheKey: string
}

export default function RelativesPanel({ base, canEdit, cacheKey }: Props) {
  const qc = useQueryClient()
  const key = ["relatives", cacheKey]
  const [form, setForm] = useState<RelativeForm | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const q = useQuery<RelativeView[]>({ queryKey: key, queryFn: async () => unwrap<RelativeView[]>(await apiClient.get(base)) ?? [] })
  const rows = q.data ?? []

  const save = useMutation({
    mutationFn: async (f: RelativeForm) => editingId
      ? unwrap<RelativeView>(await apiClient.put(`${base}/${editingId}`, relativeBody(f)))
      : unwrap<RelativeView>(await apiClient.post(base, relativeBody(f))),
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); close() },
    onError: e => setError(errorText(e, "That could not be saved.")),
  })
  const remove = useMutation({
    mutationFn: async (id: string) => { await apiClient.delete(`${base}/${id}`) },
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); setError(null) },
    onError: e => setError(errorText(e, "That could not be removed.")),
  })

  const close = () => { setForm(null); setEditingId(null); setError(null) }
  const open = (kind: "NEXT_OF_KIN" | "DEPENDANT") => { setForm(emptyRelative(kind)); setEditingId(null); setError(null) }
  const edit = (r: RelativeView) => { setForm(fromRelative(r)); setEditingId(r.id); setError(null) }
  const submit = () => {
    if (!form) return
    const problem = relativeProblem(form, rows, editingId)
    if (problem) { setError(problem); return }
    setError(null)
    save.mutate(form)
  }
  const set = (patch: Partial<RelativeForm>) => setForm(f => (f ? { ...f, ...patch } : f))

  const group = (kind: "NEXT_OF_KIN" | "DEPENDANT", title: string, empty: string) => {
    const list = rows.filter(r => r.kind === kind)
    return (
      <div style={card}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 12px" }}>
          <strong style={{ fontSize: 13 }}>{title}</strong>
          {canEdit && <button onClick={() => open(kind)} style={plain}>Add</button>}
        </div>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Name</th><th style={th}>{kind === "DEPENDANT" ? "Born" : "Contact"}</th><th style={th}>Beneficiary</th>{canEdit && <th style={th}></th>}</tr></thead>
          <tbody>
            {q.isLoading && <tr><td style={td} colSpan={4}>Loading…</td></tr>}
            {!q.isLoading && list.length === 0 && <tr><td style={td} colSpan={4}>{empty}</td></tr>}
            {list.map(r => (
              <tr key={r.id}>
                <td style={td}><strong>{r.fullName}</strong><div style={{ color: "var(--hf-text-muted)" }}>{r.relationshipLabel}</div></td>
                <td style={td}>{kind === "DEPENDANT"
                  ? <>{fmtDate(r.dateOfBirth)}{r.age != null && <div style={{ color: "var(--hf-text-muted)" }}>{r.age} {r.age === 1 ? "year" : "years"}</div>}</>
                  : <>{r.phone || "—"}{r.email && <div style={{ color: "var(--hf-text-muted)" }}>{r.email}</div>}</>}</td>
                <td style={td}>{beneficiaryText(r) || "—"}</td>
                {canEdit && (
                  <td style={{ ...td, whiteSpace: "nowrap" }}>
                    <button onClick={() => edit(r)} style={plain}>Edit</button>{" "}
                    <button onClick={() => { if (window.confirm(`Remove ${r.fullName}?`)) remove.mutate(r.id) }} style={plain} disabled={remove.isPending}>Remove</button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )
  }

  const total = sharesTotal(rows)
  return (
    <div>
      {q.isError && <Notice kind="error">{errorText(q.error, "The family details could not be loaded.")}</Notice>}
      {!form && error && <Notice kind="error">{error}</Notice>}
      {group("NEXT_OF_KIN", "Next of kin", "No next of kin is recorded.")}
      {group("DEPENDANT", "Dependants", "No dependants are recorded.")}
      {total > 0 && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>Beneficiary shares given so far add up to {+total.toFixed(2)}%.</p>}
      {form && (
        <div role="dialog" aria-label={editingId ? "Change person" : "Add person"} style={{ ...card, padding: 16 }}>
          <strong style={{ fontSize: 14 }}>{editingId ? "Change" : "Add"} {form.kind === "DEPENDANT" ? "dependant" : "next of kin"}</strong>
          {error && <Notice kind="error">{error}</Notice>}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 10, marginTop: 10 }}>
            <div><label htmlFor="rel-name" style={label}>Full name *</label><input id="rel-name" value={form.fullName} onChange={e => set({ fullName: e.target.value })} style={input} /></div>
            <div><label htmlFor="rel-relation" style={label}>Relationship *</label>
              <select id="rel-relation" value={form.relationship} onChange={e => set({ relationship: e.target.value })} style={input}>
                <option value="">Select…</option>{RELATIONSHIPS.map(r => <option key={r.value} value={r.value}>{r.label}</option>)}
              </select></div>
            <div><label htmlFor="rel-phone" style={label}>Phone{form.kind === "NEXT_OF_KIN" ? " *" : ""}</label><input id="rel-phone" value={form.phone} onChange={e => set({ phone: e.target.value })} style={input} /></div>
            <div><label htmlFor="rel-email" style={label}>Email</label><input id="rel-email" type="email" value={form.email} onChange={e => set({ email: e.target.value })} style={input} /></div>
            <div><label htmlFor="rel-dob" style={label}>Date of birth{form.kind === "DEPENDANT" ? " *" : ""}</label><input id="rel-dob" type="date" value={form.dateOfBirth} onChange={e => set({ dateOfBirth: e.target.value })} style={input} /></div>
            <div><label htmlFor="rel-address" style={label}>Address</label><input id="rel-address" value={form.address} onChange={e => set({ address: e.target.value })} style={input} /></div>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <input id="rel-ben" type="checkbox" checked={form.beneficiary} onChange={e => set({ beneficiary: e.target.checked })} />
              <label htmlFor="rel-ben" style={{ ...label, margin: 0 }}>Beneficiary</label>
            </div>
            {form.beneficiary && (
              <div><label htmlFor="rel-share" style={label}>Share (%)</label><input id="rel-share" inputMode="decimal" value={form.beneficiaryPercent} onChange={e => set({ beneficiaryPercent: e.target.value })} style={input} /></div>
            )}
            <div style={{ gridColumn: "1 / -1" }}><label htmlFor="rel-notes" style={label}>Notes</label><input id="rel-notes" value={form.notes} onChange={e => set({ notes: e.target.value })} style={input} /></div>
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
            <button onClick={submit} disabled={save.isPending} style={primary}>{save.isPending ? "Saving…" : "Save"}</button>
            <button onClick={close} style={plain}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}
