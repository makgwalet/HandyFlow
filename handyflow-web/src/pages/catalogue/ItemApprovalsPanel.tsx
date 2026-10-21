// src/pages/catalogue/ItemApprovalsPanel.tsx — drafts waiting to go live, the decisions made, and the switch for "new items need approval".
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { ErrorBanner, Field, Modal } from "./CatalogueKit"
import { cancelBtn, inp, inpErr, linkBtn, primaryBtn, td, th } from "./catalogue.styles"
import { APPROVAL_TABS, errorMessage, rejectProblem, type ApprovalStatus, type CatalogueSettings, type ItemApproval } from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }

/** Approve or reject one request. Also used from the item's own page. */
export function DecisionModal({ approval, kind, onClose, onDone }: { approval: ItemApproval; kind: "approve" | "reject"; onClose: () => void; onDone: () => void }) {
  const [note, setNote] = useState("")
  const [shown, setShown] = useState(false)
  const [error, setError] = useState("")
  const problem = shown && kind === "reject" ? rejectProblem(note) : null
  const m = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/catalogue/approvals/${approval.id}/${kind}`, { note: note.trim() || undefined }),
    onSuccess: onDone, onError: e => setError(errorMessage(e, "Couldn't save the decision.")),
  })
  const submit = () => { setShown(true); if (kind === "reject" && rejectProblem(note)) return; m.mutate() }
  return (
    <Modal title={`${kind === "approve" ? "Approve" : "Reject"} — ${approval.itemName ?? "item"}`} onClose={onClose}>
      {error && <ErrorBanner message={error} />}
      {approval.note && <p style={muted}>Note from the requester: {approval.note}</p>}
      <p style={muted}>{kind === "approve" ? "The item becomes active and can be sold." : "The item stays a draft. The requester sees your reason."}</p>
      <Field label={kind === "reject" ? "Reason" : "Note (optional)"} id="appr-note" required={kind === "reject"} error={problem ?? undefined}>
        <input id="appr-note" style={problem ? inpErr : inp} value={note} onChange={e => setNote(e.target.value)} maxLength={255} />
      </Field>
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
        <button type="button" style={cancelBtn} onClick={onClose}>Cancel</button>
        <button type="button" style={primaryBtn} disabled={m.isPending} onClick={submit}>{m.isPending ? "Saving…" : kind === "approve" ? "Approve" : "Reject"}</button>
      </div>
    </Modal>
  )
}

export default function ItemApprovalsPanel() {
  const qc = useQueryClient()
  const canDecide = usePermissions(["CATALOGUE_APPROVE"])
  const canAdmin = usePermissions(["CATALOGUE_ADMIN"])
  const [tab, setTab] = useState<ApprovalStatus>("PENDING")
  const [action, setAction] = useState<{ a: ItemApproval; kind: "approve" | "reject" } | null>(null)
  const [error, setError] = useState("")
  const settings = useQuery<CatalogueSettings>({ queryKey: ["catalogue", "settings"], queryFn: async () => inner<CatalogueSettings>(await apiClient.get("/api/v1/catalogue/settings")) })
  const q = useQuery<ItemApproval[]>({ queryKey: ["catalogue", "approvals", tab], queryFn: async () => inner<ItemApproval[]>(await apiClient.get("/api/v1/catalogue/approvals", { params: { status: tab } })) ?? [] })
  const refresh = () => { for (const k of ["approvals", "items", "dashboard", "overview"]) qc.invalidateQueries({ queryKey: ["catalogue", k] }) }
  const toggle = useMutation({
    mutationFn: (v: boolean) => apiClient.put("/api/v1/catalogue/settings", { itemApprovalRequired: v }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["catalogue", "settings"] }), onError: e => setError(errorMessage(e, "Couldn't save the setting.")),
  })
  const withdraw = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/catalogue/approvals/${id}/withdraw`),
    onSuccess: refresh, onError: e => setError(errorMessage(e, "Couldn't withdraw the request.")),
  })
  const on = settings.data?.itemApprovalRequired ?? false
  const rows = q.data ?? []

  return (
    <div>
      {error && <ErrorBanner message={error} />}
      <section aria-label="Item approval setting" style={{ border: "1px solid var(--hf-primary-border)", borderRadius: 12, padding: 16, marginBottom: 20 }}>
        <label style={{ display: "flex", gap: 10, alignItems: "flex-start", fontSize: 14 }}>
          <input type="checkbox" checked={on} disabled={!canAdmin || settings.isLoading || toggle.isPending} onChange={e => toggle.mutate(e.target.checked)} style={{ marginTop: 3 }} />
          <span>
            <strong>New items need approval before they go live</strong>
            <span style={{ display: "block", ...muted, marginTop: 2 }}>
              {on ? "Items are saved as drafts. Someone with approval rights, other than the person who asked, approves a draft to make it active."
                : "Items can be made active straight away. Switch this on to have a second person approve new items."}
            </span>
          </span>
        </label>
        {!canAdmin && <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "8px 0 0" }}>Only a catalogue administrator can change this.</p>}
      </section>

      <div role="tablist" aria-label="Requests" style={{ display: "flex", gap: 8, marginBottom: 14 }}>
        {APPROVAL_TABS.map(t => (
          <button key={t.id} type="button" role="tab" aria-selected={tab === t.id} onClick={() => setTab(t.id)}
            style={{ ...linkBtn, background: tab === t.id ? "var(--hf-primary)" : "none", color: tab === t.id ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)" }}>{t.label}</button>
        ))}
      </div>
      {q.isLoading && <p>Loading…</p>}
      {q.isError && <ErrorBanner message={errorMessage(q.error, "Couldn't load the requests.")} />}
      {q.data && rows.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>{tab === "PENDING" ? "Nothing is waiting for approval." : "Nothing here yet."}</p>}
      {rows.length > 0 && (
        <div style={{ overflowX: "auto", border: "1px solid var(--hf-primary-border)", borderRadius: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Item</th><th style={th}>Asked</th><th style={th}>Note</th>{tab !== "PENDING" && <th style={th}>Decision</th>}<th style={th} /></tr></thead>
            <tbody>
              {rows.map(a => (
                <tr key={a.id}>
                  <td style={td}><strong>{a.itemName ?? "—"}</strong>{a.sku && <span style={{ color: "var(--hf-text-faint)" }}> · {a.sku}</span>}</td>
                  <td style={td}>{new Date(a.requestedAt).toLocaleDateString("en-ZA")}</td>
                  <td style={{ ...td, ...muted }}>{a.note ?? "—"}</td>
                  {tab !== "PENDING" && <td style={{ ...td, ...muted }}>{a.decisionNote ?? "—"}{a.decidedAt ? ` (${new Date(a.decidedAt).toLocaleDateString("en-ZA")})` : ""}</td>}
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                    {tab === "PENDING" && canDecide && <>
                      <button type="button" style={{ ...linkBtn, marginRight: 6 }} aria-label={`Approve ${a.itemName}`} onClick={() => setAction({ a, kind: "approve" })}>Approve</button>
                      <button type="button" style={{ ...linkBtn, marginRight: 6 }} aria-label={`Reject ${a.itemName}`} onClick={() => setAction({ a, kind: "reject" })}>Reject</button>
                    </>}
                    {tab === "PENDING" && <button type="button" style={linkBtn} aria-label={`Withdraw ${a.itemName}`} onClick={() => withdraw.mutate(a.id)}>Withdraw</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {action && <DecisionModal approval={action.a} kind={action.kind} onClose={() => setAction(null)} onDone={() => { setAction(null); refresh() }} />}
    </div>
  )
}
