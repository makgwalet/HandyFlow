// src/pages/catalogue/BulkModal.tsx — one change to the selected items: status, category, or prices by a percentage. Check first, then apply.
import { useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ErrorBanner, Field, Modal } from "./CatalogueKit"
import { cancelBtn, inp, inpErr, primaryBtn, sel, td, th } from "./catalogue.styles"
import {
  EMPTY_BULK_FORM, STATUS_LABEL, bulkBody, bulkProblem, bulkSummary, errorMessage, indented, moveNeedsReason,
  type BulkAction, type BulkForm, type BulkResult, type Category, type ItemStatus,
} from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }

export default function BulkModal({ ids, categories, canArchive, canPrice, onClose, onDone }: {
  ids: string[]; categories: Category[]; canArchive: boolean; canPrice: boolean; onClose: () => void; onDone: () => void
}) {
  const [f, setF] = useState<BulkForm>(EMPTY_BULK_FORM)
  const [shown, setShown] = useState(false)
  const [result, setResult] = useState<BulkResult | null>(null)
  const [error, setError] = useState("")
  const set = (patch: Partial<BulkForm>) => { setF({ ...f, ...patch }); setResult(null) }
  const problem = shown ? bulkProblem(f) : null
  const statuses = (["ACTIVE", "SUSPENDED", "DISCONTINUED", "ARCHIVED", "DRAFT"] as ItemStatus[]).filter(s => s === "ACTIVE" || s === "SUSPENDED" || s === "DRAFT" || canArchive)
  const run = useMutation({
    mutationFn: async (dryRun: boolean) => inner<BulkResult>(await apiClient.post("/api/v1/catalogue/items/bulk", bulkBody(ids, f, dryRun))),
    onSuccess: r => { setResult(r); setError(""); if (!r.dryRun) onDone() },
    onError: e => setError(errorMessage(e, "The change could not be made.")),
  })
  const go = (dryRun: boolean) => { setShown(true); if (bulkProblem(f)) return; run.mutate(dryRun) }
  const applied = result !== null && !result.dryRun

  return (
    <Modal title={`Change ${ids.length} item${ids.length === 1 ? "" : "s"}`} onClose={onClose} wide>
      {error && <ErrorBanner message={error} />}
      <Field label="What do you want to change?" id="bulk-action">
        <select id="bulk-action" style={sel} value={f.action} onChange={e => set({ action: e.target.value as BulkAction })} disabled={applied}>
          <option value="STATUS">Status</option><option value="CATEGORY">Category</option>
          {canPrice && <option value="PRICE_PERCENT">Prices, by a percentage</option>}
        </select>
      </Field>
      {f.action === "STATUS" && (
        <>
          <Field label="New status" id="bulk-status"><select id="bulk-status" style={sel} value={f.status} onChange={e => set({ status: e.target.value as ItemStatus })}>{statuses.map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}</select></Field>
          <Field label={moveNeedsReason(f.status) ? "Reason" : "Reason (optional)"} id="bulk-reason" required={moveNeedsReason(f.status)} error={problem ?? undefined}>
            <input id="bulk-reason" style={problem ? inpErr : inp} value={f.reason} onChange={e => set({ reason: e.target.value })} maxLength={255} />
          </Field>
        </>
      )}
      {f.action === "CATEGORY" && (
        <Field label="Move to category" id="bulk-cat" error={problem ?? undefined}>
          <select id="bulk-cat" style={sel} value={f.categoryId} onChange={e => set({ categoryId: e.target.value })}>
            <option value="">Choose…</option>{categories.map(c => <option key={c.id} value={c.id}>{indented(c)}</option>)}
          </select>
        </Field>
      )}
      {f.action === "PRICE_PERCENT" && (
        <>
          <Field label="Change prices by (%)" id="bulk-pct" error={problem ?? undefined}><input id="bulk-pct" style={problem ? inpErr : inp} inputMode="decimal" value={f.percent} onChange={e => set({ percent: e.target.value })} placeholder="5 or -10" /></Field>
          <Field label="From (optional)" id="bulk-from"><input id="bulk-from" type="date" style={inp} value={f.effectiveFrom} onChange={e => set({ effectiveFrom: e.target.value })} /></Field>
          <Field label="Reason (optional)" id="bulk-reason2"><input id="bulk-reason2" style={inp} value={f.reason} onChange={e => set({ reason: e.target.value })} maxLength={255} /></Field>
          <p style={muted}>This uses the normal price change process: if prices need approval, each change waits for an approver.</p>
        </>
      )}

      {result && (
        <div style={{ marginTop: 8 }}>
          <p role="status" style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)" }}>{bulkSummary(result)}</p>
          <div style={{ overflowX: "auto", border: "1px solid var(--hf-border-subtle)", borderRadius: 10, maxHeight: 300, overflowY: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Item</th><th style={th}>Result</th><th style={th}>Details</th></tr></thead>
              <tbody>{result.rows.map(r => (
                <tr key={r.id}><td style={td}>{r.name}{r.sku ? ` (${r.sku})` : ""}</td>
                  <td style={{ ...td, fontWeight: 600, color: r.outcome === "REFUSED" ? "var(--hf-danger-text)" : "var(--hf-text)" }}>{r.outcome === "OK" ? (result.dryRun ? "Would change" : "Changed") : r.outcome === "UNCHANGED" ? "No change" : "Refused"}</td>
                  <td style={{ ...td, ...muted }}>{r.message}</td></tr>
              ))}</tbody>
            </table>
          </div>
        </div>
      )}

      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 16 }}>
        <button type="button" style={cancelBtn} onClick={onClose}>{applied ? "Close" : "Cancel"}</button>
        {!applied && <button type="button" style={cancelBtn} disabled={run.isPending} onClick={() => go(true)}>Check first</button>}
        {!applied && <button type="button" style={primaryBtn} disabled={run.isPending || !result || !result.dryRun || result.ok === 0} onClick={() => go(false)}>{run.isPending ? "Working…" : `Apply to ${result?.ok ?? 0} item${result?.ok === 1 ? "" : "s"}`}</button>}
      </div>
    </Modal>
  )
}
