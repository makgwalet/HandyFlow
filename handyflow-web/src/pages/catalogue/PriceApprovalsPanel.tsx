// src/pages/catalogue/PriceApprovalsPanel.tsx — price changes waiting for a decision, and those scheduled for a future date.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { ErrorBanner, Field, Modal } from "./CatalogueKit"
import { cancelBtn, inp, inpErr, linkBtn, primaryBtn, td, th } from "./catalogue.styles"
import { changeText, errorMessage, type PriceChange, type PriceChangeStatus } from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
type Action = { change: PriceChange; kind: "approve" | "reject" | "cancel" }

export default function PriceApprovalsPanel() {
  const qc = useQueryClient()
  const canDecide = usePermissions(["CATALOGUE_PRICE_APPROVE"])
  const [tab, setTab] = useState<Extract<PriceChangeStatus, "PENDING" | "SCHEDULED">>("PENDING")
  const [action, setAction] = useState<Action | null>(null)
  const q = useQuery<PriceChange[]>({ queryKey: ["catalogue", "price-queue", tab], queryFn: async () => inner<PriceChange[]>(await apiClient.get("/api/v1/catalogue/price-changes", { params: { status: tab } })) ?? [] })
  const done = () => { setAction(null); qc.invalidateQueries({ queryKey: ["catalogue", "price-queue"] }); qc.invalidateQueries({ queryKey: ["catalogue", "items"] }); qc.invalidateQueries({ queryKey: ["catalogue", "pricing"] }) }
  const rows = q.data ?? []

  return (
    <div>
      <div role="tablist" aria-label="Price changes" style={{ display: "flex", gap: 8, marginBottom: 14 }}>
        {(["PENDING", "SCHEDULED"] as const).map(t => (
          <button key={t} type="button" role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
            style={{ ...linkBtn, background: tab === t ? "var(--hf-primary)" : "none", color: tab === t ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)" }}>
            {t === "PENDING" ? "Waiting for approval" : "Scheduled"}
          </button>
        ))}
      </div>
      {q.isLoading && <p>Loading…</p>}
      {q.isError && <ErrorBanner message={errorMessage(q.error, "Couldn't load the price changes.")} />}
      {q.data && rows.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>{tab === "PENDING" ? "Nothing is waiting for approval." : "No price changes are scheduled."}</p>}
      {rows.length > 0 && (
        <div style={{ overflowX: "auto", border: "1px solid var(--hf-primary-border)", borderRadius: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Item</th><th style={th}>List</th><th style={th}>Change</th><th style={th}>From</th><th style={th}>Reason</th><th style={th} /></tr></thead>
            <tbody>
              {rows.map(c => (
                <tr key={c.id}>
                  <td style={{ ...td, fontWeight: 600 }}>{c.itemName ?? "Item"}</td>
                  <td style={td}>{c.priceListName}</td>
                  <td style={td}>{changeText(c)}</td>
                  <td style={td}>{c.effectiveFrom}</td>
                  <td style={td}>{c.reason ?? "—"}</td>
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                    {tab === "PENDING" && canDecide && !c.proposedByMe && <>
                      <button type="button" style={linkBtn} aria-label={`Approve ${c.itemName}`} onClick={() => setAction({ change: c, kind: "approve" })}>Approve</button>{" "}
                      <button type="button" style={linkBtn} aria-label={`Reject ${c.itemName}`} onClick={() => setAction({ change: c, kind: "reject" })}>Reject</button>{" "}
                    </>}
                    {tab === "PENDING" && c.proposedByMe && <span style={{ fontSize: 12, color: "var(--hf-text-faint)", marginRight: 8 }}>Yours — someone else decides</span>}
                    {(c.proposedByMe || canDecide) && <button type="button" style={linkBtn} aria-label={`Cancel ${c.itemName}`} onClick={() => setAction({ change: c, kind: "cancel" })}>{c.proposedByMe ? "Withdraw" : "Cancel"}</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {action && <DecisionModal action={action} onClose={() => setAction(null)} onDone={done} />}
    </div>
  )
}

const TITLE = { approve: "Approve price change", reject: "Reject price change", cancel: "Cancel price change" } as const
const CONFIRM = { approve: "Approve", reject: "Reject", cancel: "Cancel the change" } as const

function DecisionModal({ action, onClose, onDone }: { action: Action; onClose: () => void; onDone: () => void }) {
  const [note, setNote] = useState("")
  const [error, setError] = useState("")
  const [general, setGeneral] = useState("")
  const { change, kind } = action
  const send = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/catalogue/price-changes/${change.id}/${kind}`, { note: note.trim() || undefined }),
    onSuccess: onDone,
    onError: e => setGeneral(errorMessage(e, "Couldn't save the decision.")),
  })
  const submit = () => {
    if (kind === "reject" && note.trim().length < 3) { setError("Say why (at least 3 characters)."); return }
    setError(""); setGeneral(""); send.mutate()
  }
  return (
    <Modal title={TITLE[kind]} onClose={onClose}>
      <p style={{ margin: "0 0 12px", fontSize: 14 }}><strong>{change.itemName}</strong> · {change.priceListName}<br />{changeText(change)} from {change.effectiveFrom}</p>
      {change.reason && <p style={{ margin: "0 0 12px", fontSize: 13, color: "var(--hf-text-muted)" }}>Reason given: {change.reason}</p>}
      <Field label={kind === "reject" ? "Why are you rejecting it?" : "Note (optional)"} required={kind === "reject"} error={error} id="pd-note">
        <input id="pd-note" style={error ? inpErr : inp} value={note} onChange={e => setNote(e.target.value)} autoFocus />
      </Field>
      {general && <ErrorBanner message={general} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
        <button type="button" style={cancelBtn} onClick={onClose}>Back</button>
        <button type="button" style={primaryBtn} disabled={send.isPending} onClick={submit}>{send.isPending ? "Saving…" : CONFIRM[kind]}</button>
      </div>
    </Modal>
  )
}
