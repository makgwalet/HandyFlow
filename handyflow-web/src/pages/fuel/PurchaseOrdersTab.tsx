// Stage 4C: orders placed with fuel suppliers. Receipts booked against an order update it; the match view compares ordered, received and priced.
import { Fragment, useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import { fmtL, fmtR } from "./orderOps"
import { STATUS_LABEL, validatePo, validateCloseReason, litresVarianceLabel, type PurchaseOrder, type Match } from "./purchasingOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const FUELS = ["DIESEL", "DIESEL_50PPM", "DIESEL_500PPM", "PETROL_95", "PETROL_93", "PARAFFIN"]
const tone = (s: string) => s === "RECEIVED" ? { bg: "var(--hf-success-soft-strong)", fg: "var(--hf-success-text-strong)" } : s === "CANCELLED" ? { bg: "var(--hf-danger-soft)", fg: "var(--hf-danger-text)" } : s === "PART_RECEIVED" || s === "CLOSED_SHORT" || s === "PENDING_APPROVAL" ? { bg: "var(--hf-warning-soft)", fg: "var(--hf-warning-text)" } : { bg: "var(--hf-surface-alt)", fg: "var(--hf-text)" }
type Dialog = { kind: "edit"; po?: PurchaseOrder } | { kind: "cancel"; po: PurchaseOrder } | { kind: "closeShort"; po: PurchaseOrder } | null

export default function PurchaseOrdersTab() {
  const canManage = usePermissions(["FUEL_MANAGE"])
  const qc = useQueryClient()
  const [dialog, setDialog] = useState<Dialog>(null)
  const [open, setOpen] = useState<string | null>(null)
  const [err, setErr] = useState("")
  const list = useQuery<PurchaseOrder[]>({ queryKey: ["fuel-purchase-orders"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/purchase-orders")) })
  const match = useQuery<Match>({ queryKey: ["fuel-po-match", open], enabled: !!open, queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/purchase-orders/${open}/match`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fuel-purchase-orders"] }); qc.invalidateQueries({ queryKey: ["fuel-po-match"] }) }
  const place = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/fuel/purchase-orders/${id}/place`), onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(msg(e, "Could not place the order")) })
  const rows = list.data ?? []
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0, flex: 1 }}>
          Raise an order, place it with the supplier, then choose it when you receive the fuel (Tanks &rarr; Receive). The order follows the litres that arrive, and a delivery over the tolerance in Settings (2% unless you changed it) is refused. If the supplier will not deliver the rest, close the order short. The supplier's invoice is matched to the order and receipts under Supplier invoices.
        </p>
        {canManage && <button style={btnPrimary} onClick={() => setDialog({ kind: "edit" })}>New purchase order</button>}
      </div>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      {list.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No purchase orders yet.</p> : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Order</th><th style={th}>Supplier</th><th style={th}>Fuel</th><th style={th}>Ordered</th><th style={th}>Received</th><th style={th}>Price / L</th><th style={th}>Expected</th><th style={th}>Status</th><th style={th}></th></tr></thead>
            <tbody>
              {rows.map(p => (
                <Fragment key={p.id}>
                  <tr style={{ borderTop: "1px solid var(--hf-border)" }}>
                    <td style={td}><button style={{ ...btnGhost, border: "none", padding: 0, fontWeight: 600 }} onClick={() => setOpen(open === p.id ? null : p.id)}>{p.poNumber}</button></td>
                    <td style={td}>{p.supplierName}</td><td style={td}>{p.fuelType}</td><td style={td}>{fmtL(p.litresOrdered)}</td><td style={td}>{fmtL(p.litresReceived)}</td>
                    <td style={td}>{fmtR(p.pricePerLitre)}</td>
                    <td style={td}>{p.expectedDate ?? "–"}{p.overdue && <span style={{ color: "var(--hf-danger-text)", marginLeft: 6, fontWeight: 600 }}>overdue</span>}</td>
                    <td style={td}><Chip bg={tone(p.status).bg} fg={tone(p.status).fg}>{STATUS_LABEL[p.status]}</Chip>{p.status === "DRAFT" && p.approvalNote && <div style={{ fontSize: 11, color: "var(--hf-danger-text)", marginTop: 3 }}>{p.approvalNote}</div>}</td>
                    <td style={td}>{canManage && <span style={{ display: "flex", gap: 6 }}>
                      {p.status === "DRAFT" && <><button style={btnGhost} onClick={() => setDialog({ kind: "edit", po: p })}>Edit</button><button style={btnPrimary} onClick={() => place.mutate(p.id)}>Place order</button></>}
                      {(p.status === "DRAFT" || p.status === "PENDING_APPROVAL" || p.status === "ORDERED") && <button style={btnGhost} onClick={() => setDialog({ kind: "cancel", po: p })}>Cancel</button>}
                      {p.status === "PART_RECEIVED" && <button style={btnGhost} onClick={() => setDialog({ kind: "closeShort", po: p })}>Close short</button>}
                    </span>}</td>
                  </tr>
                  {open === p.id && (
                    <tr><td colSpan={9} style={{ ...td, background: "var(--hf-surface-alt)" }}>
                      {match.isLoading || !match.data ? "Loading…" : (
                        <div>
                          <p style={{ margin: "0 0 8px", fontSize: 13 }}>
                            Ordered {fmtL(match.data.order.litresOrdered)} at {fmtR(match.data.order.pricePerLitre)} = {fmtR(match.data.order.orderValue)}. Received {fmtL(match.data.order.litresReceived)} worth {fmtR(match.data.receivedValue)}:
                            {" "}<strong>{litresVarianceLabel(match.data.litresVariance)}</strong>{match.data.order.cancelReason ? `. Cancelled: ${match.data.order.cancelReason}` : ""}{match.data.order.closeReason ? `. Closed short: ${match.data.order.closeReason}` : ""}.
                          </p>
                          {match.data.receipts.length === 0 ? <p style={{ margin: 0, color: "var(--hf-text-muted)", fontSize: 12.5 }}>Nothing received against this order yet.</p> : (
                            <table style={{ width: "100%", borderCollapse: "collapse" }}>
                              <thead><tr><th style={th}>Received</th><th style={th}>Litres</th><th style={th}>Price / L</th><th style={th}>vs order</th><th style={th}>Delivery note</th><th style={th}>Invoice ref</th></tr></thead>
                              <tbody>{match.data.receipts.map(r => (
                                <tr key={r.receiptId} style={{ borderTop: "1px solid var(--hf-border)", opacity: r.reversed ? 0.5 : 1 }}>
                                  <td style={td}>{new Date(r.receivedAt).toLocaleDateString("en-ZA")}{r.reversed ? " (reversed)" : ""}</td>
                                  <td style={td}>{fmtL(r.litres)}</td><td style={td}>{fmtR(r.pricePerLitre)}</td>
                                  <td style={{ ...td, color: r.priceDiffers ? "var(--hf-warning-text)" : undefined, fontWeight: r.priceDiffers ? 600 : undefined }}>{r.priceDiffers ? `${r.priceVariance > 0 ? "+" : ""}${r.priceVariance.toFixed(4)} per L` : "matches"}</td>
                                  <td style={td}>{r.deliveryNote ?? "–"}</td><td style={td}>{r.invoiceRef ?? "–"}</td>
                                </tr>))}</tbody>
                            </table>
                          )}
                        </div>
                      )}
                    </td></tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {dialog?.kind === "edit" && <EditDialog po={dialog.po} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "closeShort" && <CloseShortDialog po={dialog.po} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "cancel" && <CancelDialog po={dialog.po} onDone={refresh} onClose={() => setDialog(null)} />}
    </div>
  )
}

function EditDialog({ po, onDone, onClose }: { po?: PurchaseOrder; onDone: () => void; onClose: () => void }) {
  const [f, setF] = useState({ supplierId: po?.supplierId ?? "", fuelType: po?.fuelType ?? "DIESEL", litres: po ? String(po.litresOrdered) : "", price: po ? String(po.pricePerLitre) : "", expectedDate: po?.expectedDate ?? "", notes: po?.notes ?? "" })
  const [err, setErr] = useState("")
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF(s => ({ ...s, [k]: e.target.value }))
  const suppliers = useQuery<{ id: string; name: string }[]>({ queryKey: ["fuel-suppliers-pick"], queryFn: async () => { const d = unwrap(await apiClient.get("/api/v1/fuel/suppliers")); return Array.isArray(d) ? d : (d?.content ?? []) } })
  const save = useMutation({
    mutationFn: () => {
      const body = { supplierId: f.supplierId, fuelType: f.fuelType, litresOrdered: Number(f.litres), pricePerLitre: Number(f.price), expectedDate: f.expectedDate || null, notes: f.notes || null }
      return po ? apiClient.put(`/api/v1/fuel/purchase-orders/${po.id}`, body) : apiClient.post("/api/v1/fuel/purchase-orders", body)
    },
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not save the order")),
  })
  return (
    <Modal title={po ? `Edit ${po.poNumber}` : "New purchase order"} onClose={onClose}>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        <div><label style={labelStyle}>Supplier *</label>
          <select aria-label="Supplier" value={f.supplierId} disabled={!!po} onChange={set("supplierId")} style={inputStyle}><option value="">Choose…</option>{(suppliers.data ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10 }}>
          <div><label style={labelStyle}>Fuel *</label><select aria-label="Fuel type" value={f.fuelType} onChange={set("fuelType")} style={inputStyle}>{FUELS.map(x => <option key={x} value={x}>{x}</option>)}</select></div>
          <div><label style={labelStyle}>Litres *</label><input aria-label="Litres" type="number" value={f.litres} onChange={set("litres")} style={inputStyle} /></div>
          <div><label style={labelStyle}>Price / litre (R) *</label><input aria-label="Price per litre" type="number" value={f.price} onChange={set("price")} style={inputStyle} /></div>
        </div>
        <div><label style={labelStyle}>Expected delivery</label><input aria-label="Expected" type="date" value={f.expectedDate} onChange={set("expectedDate")} style={inputStyle} /></div>
        {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{err}</p>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
          <button style={btnGhost} onClick={onClose}>Cancel</button>
          <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validatePo(f.supplierId, f.fuelType, f.litres, f.price); if (v) return setErr(v); setErr(""); save.mutate() }}>Save</button>
        </div>
      </div>
    </Modal>
  )
}

function CancelDialog({ po, onDone, onClose }: { po: PurchaseOrder; onDone: () => void; onClose: () => void }) {
  const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  const cancel = useMutation({ mutationFn: () => apiClient.post(`/api/v1/fuel/purchase-orders/${po.id}/cancel`, { reason: reason.trim() }), onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not cancel")) })
  return (
    <Modal title={`Cancel ${po.poNumber}`} onClose={onClose}>
      <label style={labelStyle}>Reason *</label>
      <input aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} style={{ ...inputStyle, width: "100%" }} />
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Keep order</button>
        <button style={btnPrimary} disabled={cancel.isPending} onClick={() => { if (reason.trim().length < 3) return setErr("Give a reason for cancelling."); setErr(""); cancel.mutate() }}>Cancel order</button>
      </div>
    </Modal>
  )
}

function CloseShortDialog({ po, onDone, onClose }: { po: PurchaseOrder; onDone: () => void; onClose: () => void }) {
  const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  const close = useMutation({ mutationFn: () => apiClient.post(`/api/v1/fuel/purchase-orders/${po.id}/close-short`, { reason: reason.trim() }), onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not close the order")) })
  return (
    <Modal title={`Close ${po.poNumber} short`} onClose={onClose}>
      <p style={{ fontSize: 13, margin: "0 0 10px" }}>{fmtL(po.litresReceived)} of {fmtL(po.litresOrdered)} has arrived. Closing short means the remaining {fmtL(po.litresOutstanding)} will not be received. The fuel already received stays in stock, and nothing more can be received on this order.</p>
      <label style={labelStyle}>Reason *</label>
      <input aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} style={{ ...inputStyle, width: "100%" }} />
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Keep open</button>
        <button style={btnPrimary} disabled={close.isPending} onClick={() => { const v = validateCloseReason(reason); if (v) return setErr(v); setErr(""); close.mutate() }}>Close short</button>
      </div>
    </Modal>
  )
}
