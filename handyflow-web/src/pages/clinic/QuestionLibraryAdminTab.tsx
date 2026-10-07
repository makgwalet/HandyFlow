// src/pages/clinic/QuestionLibraryAdminTab.tsx
// Content administration for the question library: every group with its review status, and the actions its status and
// the user's permissions allow. Authoring is a JSON editor for now; the server validates the whole definition and its
// messages are shown as they come. Nothing here approves content on its own: review and activation are separate
// permissions, and activation by someone other than the reviewer is enforced by the server (DEC-CLINIC-001).
import { useDialogs } from "./dialogs"
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import VisitTypeGroupsPanel from "./VisitTypeGroupsPanel"

export interface GroupRow {
  id: string; code: string; version: number; name: string; category?: string; status: string; demo?: boolean; clinicalSource?: string | null
}
export type ActionId = "view" | "edit" | "submit" | "approve" | "changes" | "activate" | "deprecate" | "new-version"

/** The actions that make sense for a group in this status, for a user with these permissions. */
export function actionsFor(status: string, canAdmin: boolean, canApprove: boolean): ActionId[] {
  const a: ActionId[] = ["view"]
  if ((status === "DRAFT" || status === "CHANGES_REQUESTED") && canAdmin) a.push("edit", "submit")
  if (status === "CLINICAL_REVIEW" && canApprove) a.push("approve", "changes")
  if (status === "APPROVED" && canApprove) a.push("activate")
  if (status === "ACTIVE" && canApprove) a.push("deprecate")
  if ((status === "ACTIVE" || status === "DEPRECATED" || status === "APPROVED") && canAdmin) a.push("new-version")
  return a
}
const LABEL: Record<ActionId, string> = {
  view: "View", edit: "Edit", submit: "Submit for review", approve: "Approve", changes: "Request changes",
  activate: "Activate", deprecate: "Deprecate", "new-version": "New version",
}
export const TEMPLATE = {
  name: "", category: "", minAgeMonths: null, maxAgeMonths: null, sex: [], visitTypes: [], defaultEnabled: false,
  clinicalSource: "", sourceVersion: "", questions: [], redFlags: [],
}

export const parseJson = (text: string): { ok: true; value: any } | { ok: false; error: string } => {
  try { return { ok: true, value: JSON.parse(text) } } catch (e: any) { return { ok: false, error: `This is not valid JSON: ${e.message}` } }
}

const unwrap = (r: any) => r.data?.data ?? r.data
const lib = "/api/v1/clinic/question-groups"
const msg = (e: any) => e?.response?.data?.message ?? e?.message ?? "That did not work."
const btn: React.CSSProperties = { background: "none", border: "none", cursor: "pointer", fontSize: 12, color: "var(--hf-accent-text)", padding: "0 6px 0 0" }

export default function QuestionLibraryAdminTab() {
  const qc = useQueryClient()
  const { prompt, dialogs } = useDialogs()
  const canAdmin = usePermission("CLINIC_CONTENT_ADMIN")
  const canApprove = usePermission("CLINIC_CONTENT_APPROVE")
  const [error, setError] = useState("")
  const [editor, setEditor] = useState<null | { id?: string; code: string; text: string; readOnly: boolean; title: string }>(null)

  const { data, isLoading, isError } = useQuery<GroupRow[]>({
    queryKey: ["clinic-question-admin"], retry: false,
    queryFn: async () => unwrap(await apiClient.get(`${lib}/admin`)) ?? [],
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ["clinic-question-admin"] })
  const run = useMutation({
    mutationFn: async (fn: () => Promise<unknown>) => fn(),
    onSuccess: () => { setError(""); setEditor(null); refresh() },
    onError: e => setError(msg(e)),
  })

  const open = async (g: GroupRow, readOnly: boolean) => {
    try {
      const v = unwrap(await apiClient.get(`${lib}/${g.id}/definition`))
      setError(""); setEditor({ id: g.id, code: g.code, text: JSON.stringify(v.definition, null, 2), readOnly, title: `${g.code} v${g.version} (${g.status})` })
    } catch (e) { setError(msg(e)) }
  }
  const act = async (g: GroupRow, a: ActionId) => {
    if (a === "view") return open(g, true)
    if (a === "edit") return open(g, false)
    if (a === "submit") return run.mutate(() => apiClient.post(`${lib}/${g.id}/submit-for-review`, {}))
    if (a === "new-version") return run.mutate(() => apiClient.post(`${lib}/${g.id}/new-version`))
    const status = { approve: "APPROVED", changes: "CHANGES_REQUESTED", activate: "ACTIVE", deprecate: "DEPRECATED" }[a]
    const note = a === "changes" ? await prompt({ title: "Request changes", label: "What needs to change?", multiline: true, confirmLabel: "Send back" }) : null
    if (a === "changes" && !note?.trim()) return
    run.mutate(() => apiClient.post(`${lib}/${g.id}/status`, { status, note }))
  }
  const save = () => {
    if (!editor) return
    const p = parseJson(editor.text)
    if (!p.ok) return setError(p.error)
    run.mutate(() => editor.id
      ? apiClient.put(`${lib}/${editor.id}/definition`, p.value)
      : apiClient.post(lib, { code: editor.code.trim(), definition: p.value }))
  }

  const rows = data ?? []
  return (
    <div>
      {dialogs}
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 10 }}>
        Question groups need clinical review before use. Content marked DEMO is sample content only.
      </div>
      {canAdmin && <button type="button" onClick={() => setEditor({ code: "", text: JSON.stringify(TEMPLATE, null, 2), readOnly: false, title: "New question group" })}>New group</button>}
      {isLoading && <div style={{ fontSize: 13 }}>Loading…</div>}
      {isError && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The library could not be loaded. You need content administration rights.</div>}
      {error && <div role="status" style={{ fontSize: 13, color: "var(--hf-danger-text)", margin: "8px 0", whiteSpace: "pre-wrap" }}>{error}</div>}
      {rows.length > 0 && (
        <table style={{ borderCollapse: "collapse", width: "100%", fontSize: 12, marginTop: 10 }}>
          <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Code</th><th>Name</th><th>Version</th><th>Status</th><th>Source</th><th /></tr></thead>
          <tbody>
            {rows.map(g => (
              <tr key={g.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td>{g.code}{g.demo ? " (DEMO)" : ""}</td><td>{g.name}</td><td>{g.version}</td>
                <td>{g.status.toLowerCase().replace(/_/g, " ")}</td><td>{g.clinicalSource ?? "—"}</td>
                <td style={{ whiteSpace: "nowrap" }}>
                  {actionsFor(g.status, canAdmin, canApprove).map(a => <button key={a} type="button" style={btn} onClick={() => act(g, a)}>{LABEL[a]}</button>)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <VisitTypeGroupsPanel groupCodes={[...new Set(rows.map(g => g.code))]} />
      {editor && (
        <div style={{ marginTop: 16, border: "1px solid var(--hf-border)", borderRadius: 8, padding: 12 }}>
          <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 6 }}>{editor.title}</div>
          {!editor.id && <input aria-label="Group code" placeholder="Code, e.g. ADULT_INTAKE" value={editor.code}
            onChange={e => setEditor({ ...editor, code: e.target.value })} style={{ marginBottom: 6 }} />}
          <textarea aria-label="Definition" rows={18} readOnly={editor.readOnly} value={editor.text} spellCheck={false}
            onChange={e => setEditor({ ...editor, text: e.target.value })}
            style={{ width: "100%", fontFamily: "monospace", fontSize: 12, boxSizing: "border-box" }} />
          <div style={{ marginTop: 6, display: "flex", gap: 8 }}>
            {!editor.readOnly && <button type="button" onClick={save} disabled={run.isPending}>Save</button>}
            <button type="button" onClick={() => { setEditor(null); setError("") }}>Close</button>
          </div>
        </div>
      )}
    </div>
  )
}
