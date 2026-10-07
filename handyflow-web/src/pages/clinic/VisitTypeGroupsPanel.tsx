// src/pages/clinic/VisitTypeGroupsPanel.tsx
// Which question groups each visit type opens, in order. A practice can set its own list (which replaces the platform
// default for that visit type) or go back to the default. Whether a listed group is actually shown still depends on
// its review status, so listing an unapproved group opens nothing yet.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"

export interface Entry { groupCode: string; required: boolean }
export interface Mapping { visitType: string; source: "TENANT" | "PLATFORM"; groups: Entry[] }
export const VISIT_TYPES = ["CONSULTATION", "FOLLOW_UP", "PROCEDURE", "EMERGENCY", "CHECK_UP", "CHECKUP", "TELEHEALTH"]

/** Moves the entry at `index` by `delta` places; out-of-range moves change nothing. Returns a new list. */
export function move(list: Entry[], index: number, delta: number): Entry[] {
  const to = index + delta
  if (index < 0 || index >= list.length || to < 0 || to >= list.length) return list
  const out = [...list]
  ;[out[index], out[to]] = [out[to], out[index]]
  return out
}
export const addGroup = (list: Entry[], code: string): Entry[] =>
  !code || list.some(e => e.groupCode === code) ? list : [...list, { groupCode: code, required: false }]

const unwrap = (r: any) => r.data?.data ?? r.data
const url = (v: string) => `/api/v1/clinic/visit-types/${encodeURIComponent(v)}/groups`
const msg = (e: any) => e?.response?.data?.message ?? e?.message ?? "That did not work."

export default function VisitTypeGroupsPanel({ groupCodes }: { groupCodes: string[] }) {
  const qc = useQueryClient()
  const canAdmin = usePermission("CLINIC_CONTENT_ADMIN")
  const [visitType, setVisitType] = useState("CONSULTATION")
  const [draft, setDraft] = useState<Entry[]>([])
  const [dirty, setDirty] = useState(false)
  const [pick, setPick] = useState("")
  const [error, setError] = useState("")

  const { data, isError } = useQuery<Mapping>({
    queryKey: ["clinic-visit-mapping", visitType], retry: false,
    queryFn: async () => unwrap(await apiClient.get(url(visitType))),
  })
  useEffect(() => { if (data) { setDraft(data.groups ?? []); setDirty(false); setError("") } }, [data])

  const after = { onSuccess: () => { setError(""); qc.invalidateQueries({ queryKey: ["clinic-visit-mapping", visitType] }) }, onError: (e: any) => setError(msg(e)) }
  const save = useMutation({ mutationFn: () => apiClient.put(url(visitType), { groups: draft }), ...after })
  const reset = useMutation({ mutationFn: () => apiClient.delete(url(visitType)), ...after })
  const edit = (next: Entry[]) => { setDraft(next); setDirty(true) }

  return (
    <div style={{ marginTop: 24 }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", letterSpacing: "0.06em", marginBottom: 6 }}>GROUPS PER VISIT TYPE</div>
      <select aria-label="Visit type" value={visitType} onChange={e => setVisitType(e.target.value)}>
        {VISIT_TYPES.map(v => <option key={v} value={v}>{v.replace(/_/g, " ")}</option>)}
      </select>
      {isError && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 6 }}>The list could not be loaded.</div>}
      {data && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "6px 0" }}>
        {data.source === "TENANT" ? "This practice's own list." : "Platform default. Saving creates this practice's own list."}
      </div>}
      {data && draft.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No groups are opened for this visit type.</div>}
      {draft.map((e, i) => (
        <div key={e.groupCode} style={{ display: "flex", gap: 8, alignItems: "center", padding: "2px 0", fontSize: 13 }}>
          <span style={{ width: 20, color: "var(--hf-text-muted)" }}>{i + 1}.</span>
          <span style={{ flex: 1 }}>{e.groupCode}</span>
          {canAdmin ? (<>
            <label style={{ fontSize: 12 }}><input type="checkbox" checked={e.required} aria-label={`${e.groupCode} required`}
              onChange={() => edit(draft.map((x, j) => j === i ? { ...x, required: !x.required } : x))} /> required</label>
            <button type="button" aria-label={`Move ${e.groupCode} up`} onClick={() => edit(move(draft, i, -1))}>↑</button>
            <button type="button" aria-label={`Move ${e.groupCode} down`} onClick={() => edit(move(draft, i, 1))}>↓</button>
            <button type="button" aria-label={`Remove ${e.groupCode}`} onClick={() => edit(draft.filter((_, j) => j !== i))}>Remove</button>
          </>) : <span style={{ fontSize: 12 }}>{e.required ? "required" : ""}</span>}
        </div>
      ))}
      {canAdmin && (
        <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
          <select aria-label="Group to add" value={pick} onChange={e => setPick(e.target.value)}>
            <option value="">Add a group…</option>
            {groupCodes.filter(c => !draft.some(e => e.groupCode === c)).map(c => <option key={c} value={c}>{c}</option>)}
          </select>
          <button type="button" disabled={!pick} onClick={() => { edit(addGroup(draft, pick)); setPick("") }}>Add</button>
          <button type="button" disabled={!dirty || save.isPending} onClick={() => save.mutate()}>Save list</button>
          {data?.source === "TENANT" && <button type="button" disabled={reset.isPending}
            onClick={() => { if (window.confirm("Go back to the platform default for this visit type?")) reset.mutate() }}>Use platform default</button>}
        </div>
      )}
      {error && <div role="status" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
    </div>
  )
}
