import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { card, btnGhost, inputStyle, ErrorLine } from "../warehousing/whseUi"
import { fmtL } from "./orderOps"
import { isFcMatching, receiptChoices, differenceText, type FcMatchingReport } from "./forecourtSupplyOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const th: React.CSSProperties = { textAlign: "left", padding: "8px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "8px 12px", fontSize: 13 }
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })
const BASE = "/api/v1/fuel/forecourt/supply-report/dispatch-matching"

/** Supply runs to each forecourt against the receipts linked to them. Renders nothing when the Retail division is off, there are no supply runs, or the answer is not a report. */
export default function ForecourtMatchingSection({ days }: { days: number }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_STOCK_MANAGE"])
  const [pick, setPick] = useState<Record<string, string>>({})
  const q = useQuery<FcMatchingReport | null>({
    queryKey: ["fuel-forecourt-matching", days], retry: false,
    queryFn: async () => { const d = unwrap(await apiClient.get(`${BASE}?days=${days}`)); return isFcMatching(d) ? d : null },
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ["fuel-forecourt-matching"] })
  const link = useMutation({ mutationFn: (v: { dispatchId: string; receiptId: string }) => apiClient.post(BASE, v), onSuccess: refresh })
  const unlink = useMutation({ mutationFn: (matchId: string) => apiClient.delete(`${BASE}/${matchId}`), onSuccess: refresh })
  const d = q.data
  if (!d || d.forecourts.length === 0) return null
  return (
    <section style={{ marginTop: 18 }} aria-label="Forecourt dispatches and receipts">
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Forecourt supply runs: dispatched and received</h3>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>{d.from} to {d.to}. A dispatch that names a forecourt is matched to the receipt in the forecourt's tank. Nothing is matched automatically and no difference is judged: you decide what is acceptable.</p>
      <ErrorLine error={link.error ?? unlink.error} fallback="Could not change the match" />
      {d.forecourts.map(f => (
        <div key={f.depotId} style={{ ...card, overflowX: "auto", marginBottom: 12 }}>
          <div style={{ padding: "10px 12px", fontWeight: 700, fontSize: 13.5 }}>{f.name} <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>dispatched {fmtL(f.dispatchedLitres)}, received on linked dispatches {fmtL(f.receivedLitres)}</span></div>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Dispatched</th><th style={th}>From</th><th style={th}>Litres sent</th><th style={th}>Received</th><th style={th}></th></tr></thead>
            <tbody>
              {f.matched.map(m => (
                <tr key={m.dispatchId} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <td style={td}>{when(m.dispatchedAt)}</td><td style={td}>{m.fromTank} ({m.fuelType})</td><td style={td}>{fmtL(m.litresDispatched)}</td>
                  <td style={td}>{m.byTransfer ? <>{fmtL(m.litresReceived)} <span style={{ color: "var(--hf-text-muted)" }}>(delivered by stock transfer, no receipt needed)</span></> : <>{fmtL(m.litresReceived)} <span style={{ color: "var(--hf-text-muted)" }}>({differenceText(m)})</span></>}</td>
                  <td style={td}>{canManage && m.receipts.map(r => <button key={r.matchId} style={{ ...btnGhost, padding: "2px 8px", marginRight: 4 }} onClick={() => unlink.mutate(r.matchId)}>Unlink {fmtL(r.litres)}</button>)}</td>
                </tr>))}
              {f.openDispatches.map(o => {
                const choices = receiptChoices(f, o)
                return (
                  <tr key={o.dispatchId} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                    <td style={td}>{when(o.dispatchedAt)}</td><td style={td}>{o.fromTank} ({o.fuelType})</td><td style={td}>{fmtL(o.litres)}</td>
                    <td style={{ ...td, color: "var(--hf-warning-text-strong)" }}>No receipt linked</td>
                    <td style={td}>{canManage && (choices.length === 0 ? <span style={{ color: "var(--hf-text-muted)" }}>No open receipt of this fuel</span> : <>
                      <select aria-label={`Receipt for dispatch ${o.dispatchId}`} style={{ ...inputStyle, width: 220 }} value={pick[o.dispatchId] ?? ""} onChange={e => setPick(p => ({ ...p, [o.dispatchId]: e.target.value }))}>
                        <option value="">Choose a receipt…</option>
                        {choices.map(r => <option key={r.receiptId} value={r.receiptId}>{when(r.receivedAt)} - {fmtL(r.litres)}{r.deliveryNote ? ` - ${r.deliveryNote}` : ""}</option>)}
                      </select>{" "}
                      <button style={{ ...btnGhost, padding: "4px 10px" }} disabled={!pick[o.dispatchId] || link.isPending} onClick={() => link.mutate({ dispatchId: o.dispatchId, receiptId: pick[o.dispatchId] })}>Link</button></>)}</td>
                  </tr>)
              })}
              {f.matched.length === 0 && f.openDispatches.length === 0 && <tr><td colSpan={5} style={{ ...td, color: "var(--hf-text-muted)" }}>No dispatches to this forecourt in the period.</td></tr>}
            </tbody>
          </table>
          {f.openReceipts.length > 0 && <p style={{ fontSize: 12, margin: "6px 12px 10px", color: "var(--hf-text-muted)" }}>Receipts not linked to a dispatch: {f.openReceipts.map(r => `${when(r.receivedAt)} ${fmtL(r.litres)} (${r.tank})`).join("; ")}</p>}
        </div>))}
    </section>
  )
}
