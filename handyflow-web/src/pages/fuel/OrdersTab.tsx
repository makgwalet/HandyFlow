// Stage 2: quotations and sales orders: credit check, allocation, loads, loading record.
import { Fragment, useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import type { CustomerAccount } from "./customerOps"
import {
  statusLabel, fmtL, fmtR, validateOrder, validateConfirm, validateLoad, validateLoading, loadVariance,
  type Order, type CreditCheck, type DeliveryLine,
} from "./orderOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const FUELS = ["DIESEL", "DIESEL_50PPM", "DIESEL_500PPM", "PETROL_95", "PETROL_93", "PARAFFIN"]

type Dialog = { kind: "quote"; order?: Order } | { kind: "confirm"; order: Order } | { kind: "load"; order: Order } | { kind: "cancel"; order: Order } | { kind: "loading"; delivery: DeliveryLine } | null

function tone(s: string) {
  if (s === "DELIVERED") return { bg: "var(--hf-success-soft-strong)", fg: "var(--hf-success-text-strong)" }
  if (s === "CANCELLED") return { bg: "var(--hf-danger-soft)", fg: "var(--hf-danger-text)" }
  return { bg: "var(--hf-surface-alt)", fg: "var(--hf-text)" }
}
const Err = ({ text }: { text: string }) => text ? <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{text}</p> : null

export default function OrdersTab() {
  const canManage = usePermissions(["FUEL_MANAGE"])
  const qc = useQueryClient()
  const [dialog, setDialog] = useState<Dialog>(null)
  const [open, setOpen] = useState<string | null>(null)

  const list = useQuery<Order[]>({ queryKey: ["fuel-orders"], queryFn: async () => { const d = unwrap(await apiClient.get("/api/v1/fuel/orders")); return Array.isArray(d) ? d : (d?.content ?? []) } })
  const detail = useQuery<Order>({ queryKey: ["fuel-order", open], enabled: !!open, queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/orders/${open}`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fuel-orders"] }); qc.invalidateQueries({ queryKey: ["fuel-order"] }); qc.invalidateQueries({ queryKey: ["fuel-availability"] }); qc.invalidateQueries({ queryKey: ["deliveries"] }) }
  const rows = list.data ?? []

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0, flex: 1 }}>
          A quotation becomes an order when it is confirmed: the customer's credit is checked and the litres are reserved from a tank. Loads are scheduled against the order; the order follows what is actually delivered.
        </p>
        {canManage && <button style={btnPrimary} onClick={() => setDialog({ kind: "quote" })}>New quotation</button>}
      </div>
      {list.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No orders yet.</p> : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Order</th><th style={th}>Customer</th><th style={th}>Fuel</th><th style={th}>Ordered</th><th style={th}>Delivered</th><th style={th}>Value</th><th style={th}>Status</th><th style={th}></th></tr></thead>
            <tbody>
              {rows.map(o => (
                <Fragment key={o.id}>
                  <tr style={{ borderTop: "1px solid var(--hf-border)" }}>
                    <td style={td}><button style={{ ...btnGhost, border: "none", padding: 0, fontWeight: 600 }} onClick={() => setOpen(open === o.id ? null : o.id)}>{o.orderNumber}</button></td>
                    <td style={td}>{o.customerName}{o.siteName ? ` · ${o.siteName}` : ""}</td>
                    <td style={td}>{o.fuelType}</td>
                    <td style={td}>{fmtL(o.litresOrdered)}</td>
                    <td style={td}>{fmtL(o.litresDelivered)}</td>
                    <td style={td}>{fmtR(o.value)}</td>
                    <td style={td}><Chip bg={tone(o.status).bg} fg={tone(o.status).fg}>{statusLabel(o.status)}</Chip></td>
                    <td style={td}>{canManage && <span style={{ display: "flex", gap: 6 }}>
                      {o.status === "QUOTE" && <><button style={btnGhost} onClick={() => setDialog({ kind: "quote", order: o })}>Edit</button>
                        <button style={btnPrimary} onClick={() => setDialog({ kind: "confirm", order: o })}>Confirm</button></>}
                      {(o.status === "CONFIRMED" || o.status === "PART_DELIVERED") && <button style={btnGhost} onClick={() => setDialog({ kind: "load", order: o })}>Schedule load</button>}
                      {o.status !== "CANCELLED" && o.status !== "DELIVERED" && <button style={btnGhost} onClick={() => setDialog({ kind: "cancel", order: o })}>Cancel</button>}
                    </span>}</td>
                  </tr>
                  {open === o.id && (
                    <tr><td colSpan={8} style={{ ...td, background: "var(--hf-surface-alt)" }}>
                      <div style={{ fontSize: 13, marginBottom: 6 }}>
                        {o.tankName ? `Supplied from ${o.tankName}. ` : ""}Remaining {fmtL(o.litresRemaining)}, scheduled {fmtL(o.litresScheduled)}.
                        {o.creditOverrideReason ? ` Credit override: ${o.creditOverrideReason}.` : ""}{o.cancelReason ? ` Cancelled: ${o.cancelReason}.` : ""}
                      </div>
                      {(detail.data?.deliveries ?? []).length === 0 ? <span style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No loads yet.</span>
                        : (detail.data?.deliveries ?? []).map(d => {
                          const v = loadVariance(d.loadedLitres, d.litresDelivered)
                          return (
                            <div key={d.id} style={{ display: "flex", gap: 10, padding: "6px 0", fontSize: 13, borderTop: "1px solid var(--hf-border)", alignItems: "center" }}>
                              <span style={{ flex: 1 }}>
                                {d.receiptNumber ?? "Load"} · {d.status.toLowerCase().replace("_", " ")} · {fmtL(d.litresDelivered ?? d.litresOrdered)}
                                {d.loadedLitres != null && ` · loaded ${fmtL(d.loadedLitres)}`}{d.sealNumbers ? ` · seals ${d.sealNumbers}` : ""}
                                {v?.needsReview && <> <Chip bg="var(--hf-danger-soft)" fg="var(--hf-danger-text)">Variance {fmtL(v.diff)} needs review</Chip></>}
                                {d.invoiceNumber && ` · ${d.invoiceNumber}`}
                              </span>
                              {canManage && (d.status === "SCHEDULED" || d.status === "IN_TRANSIT") && <button style={btnGhost} onClick={() => setDialog({ kind: "loading", delivery: d })}>Record loading</button>}
                            </div>
                          )
                        })}
                    </td></tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {dialog?.kind === "quote" && <QuoteDialog order={dialog.order} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "confirm" && <ConfirmDialog order={dialog.order} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "load" && <LoadDialog order={dialog.order} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "cancel" && <CancelDialog order={dialog.order} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "loading" && <LoadingDialog delivery={dialog.delivery} onDone={refresh} onClose={() => setDialog(null)} />}
    </div>
  )
}

function Footer({ onClose, label, pending, onSubmit }: { onClose: () => void; label: string; pending: boolean; onSubmit: () => void }) {
  return (
    <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
      <button style={btnGhost} onClick={onClose}>Cancel</button>
      <button style={btnPrimary} disabled={pending} onClick={onSubmit}>{label}</button>
    </div>
  )
}

function QuoteDialog({ order, onDone, onClose }: { order?: Order; onDone: () => void; onClose: () => void }) {
  const [f, setF] = useState({ accountId: order?.accountId ?? "", siteId: order?.siteId ?? "", fuelType: order?.fuelType ?? "DIESEL", litres: order ? String(order.litresOrdered) : "", price: order ? String(order.pricePerLitre) : "", requestedDate: order?.requestedDate ?? "", validUntil: order?.quoteValidUntil ?? "", ref: order?.customerReference ?? "", notes: order?.notes ?? "" })
  const [err, setErr] = useState("")
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF(s => ({ ...s, [k]: e.target.value }))
  const accounts = useQuery<CustomerAccount[]>({ queryKey: ["fuel-customer-accounts"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/customer-accounts")) })
  const acct = useQuery<CustomerAccount>({ queryKey: ["fuel-customer-account", f.accountId], enabled: !!f.accountId, queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/customer-accounts/${f.accountId}`)) })
  const save = useMutation({
    mutationFn: () => {
      const body = { accountId: f.accountId, siteId: f.siteId || null, fuelType: f.fuelType, litres: Number(f.litres), pricePerLitre: Number(f.price), requestedDate: f.requestedDate || null, quoteValidUntil: f.validUntil || null, customerReference: f.ref || null, notes: f.notes || null }
      return order ? apiClient.put(`/api/v1/fuel/orders/${order.id}`, body) : apiClient.post("/api/v1/fuel/orders", body)
    },
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not save the quotation")),
  })
  return (
    <Modal title={order ? `Edit ${order.orderNumber}` : "New quotation"} onClose={onClose}>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        <div><label style={labelStyle}>Customer account *</label>
          <select aria-label="Customer account" value={f.accountId} disabled={!!order} onChange={e => setF(s => ({ ...s, accountId: e.target.value, siteId: "" }))} style={inputStyle}>
            <option value="">Choose…</option>{(accounts.data ?? []).map(a => <option key={a.id} value={a.id}>{a.customerName}{a.creditHold ? " (on credit hold)" : ""}</option>)}
          </select></div>
        <div><label style={labelStyle}>Delivery site</label>
          <select aria-label="Delivery site" value={f.siteId} disabled={!f.accountId} onChange={set("siteId")} style={inputStyle}>
            <option value="">No specific site</option>{(acct.data?.sites ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select></div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10 }}>
          <div><label style={labelStyle}>Fuel *</label><select aria-label="Fuel type" value={f.fuelType} onChange={set("fuelType")} style={inputStyle}>{FUELS.map(x => <option key={x} value={x}>{x}</option>)}</select></div>
          <div><label style={labelStyle}>Litres *</label><input aria-label="Litres" type="number" value={f.litres} onChange={set("litres")} style={inputStyle} /></div>
          <div><label style={labelStyle}>Price / litre (R) *</label><input aria-label="Price per litre" type="number" value={f.price} onChange={set("price")} style={inputStyle} /></div>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
          <div><label style={labelStyle}>Requested delivery date</label><input type="date" value={f.requestedDate} onChange={set("requestedDate")} style={inputStyle} /></div>
          <div><label style={labelStyle}>Quotation valid until</label><input aria-label="Valid until" type="date" value={f.validUntil} onChange={set("validUntil")} style={inputStyle} /></div>
        </div>
        <div><label style={labelStyle}>Customer reference / PO number</label><input value={f.ref} onChange={set("ref")} style={inputStyle} /></div>
        <Err text={err} />
        <Footer onClose={onClose} label="Save quotation" pending={save.isPending} onSubmit={() => { const v = validateOrder(f.accountId, f.fuelType, f.litres, f.price, f.validUntil); if (v) return setErr(v); setErr(""); save.mutate() }} />
      </div>
    </Modal>
  )
}

function ConfirmDialog({ order, onDone, onClose }: { order: Order; onDone: () => void; onClose: () => void }) {
  const canOverride = usePermissions(["FUEL_CREDIT_OVERRIDE"])
  const [tankId, setTankId] = useState(""); const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  const check = useQuery<CreditCheck>({ queryKey: ["fuel-credit-check", order.id], queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/orders/${order.id}/credit-check`)) })
  const tanks = useQuery<any[]>({ queryKey: ["tanks"], queryFn: async () => { const r = await apiClient.get("/api/v1/fuel/tanks"); return r.data?.data ?? r.data } })
  const matching = (tanks.data ?? []).filter(t => t.fuelType === order.fuelType)
  const c = check.data
  const confirm = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/orders/${order.id}/confirm`, { tankId, overrideReason: reason || null }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not confirm the order")),
  })
  return (
    <Modal title={`Confirm ${order.orderNumber}`} onClose={onClose} width={520}>
      <p style={{ fontSize: 13, marginTop: 0 }}>{order.customerName}: {fmtL(order.litresOrdered)} {order.fuelType} at {fmtR(order.pricePerLitre)}/L = <strong>{fmtR(order.value)}</strong></p>
      {c && (
        <div style={{ ...card, padding: 12, fontSize: 13, marginBottom: 10 }}>
          <div>Credit limit: {fmtR(c.creditLimit)}</div>
          <div>Unpaid invoices: {fmtR(c.invoicesOutstanding)} · Delivered, not invoiced: {fmtR(c.deliveredNotInvoiced)} · Other open orders: {fmtR(c.otherOpenOrders)}</div>
          <div><strong>Exposure with this order: {fmtR(c.exposureAfter)}</strong></div>
          <div role="status" style={{ color: c.withinLimit && !c.onCreditHold ? "var(--hf-success-text-strong)" : "var(--hf-danger-text)", fontWeight: 600 }}>
            {c.onCreditHold ? "Customer is on credit hold" : c.withinLimit ? "Within credit limit" : "Over credit limit"}
          </div>
        </div>
      )}
      <label style={labelStyle}>Supply from tank *</label>
      <select aria-label="Tank" value={tankId} onChange={e => setTankId(e.target.value)} style={inputStyle}>
        <option value="">Choose…</option>{matching.map(t => <option key={t.id} value={t.id}>{t.name} ({fmtL(t.currentLitres)})</option>)}
      </select>
      {c && !c.withinLimit && !c.onCreditHold && canOverride && (<>
        <label style={{ ...labelStyle, marginTop: 10 }}>Reason for overriding the credit limit *</label>
        <input aria-label="Override reason" value={reason} onChange={e => setReason(e.target.value)} style={inputStyle} />
      </>)}
      <Err text={err} />
      <Footer onClose={onClose} label="Confirm and allocate stock" pending={confirm.isPending} onSubmit={() => { const v = validateConfirm(tankId, c, canOverride, reason); if (v) return setErr(v); setErr(""); confirm.mutate() }} />
    </Modal>
  )
}

function LoadDialog({ order, onDone, onClose }: { order: Order; onDone: () => void; onClose: () => void }) {
  const [litres, setLitres] = useState(""); const [at, setAt] = useState(""); const [driver, setDriver] = useState(""); const [reg, setReg] = useState(""); const [err, setErr] = useState("")
  const left = Math.max(0, order.litresOrdered - order.litresDelivered - order.litresScheduled)
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/orders/${order.id}/deliveries`, { litres: Number(litres), scheduledAt: new Date(at).toISOString(), driverName: driver || null, vehicleReg: reg || null }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not schedule the load")),
  })
  return (
    <Modal title={`Schedule a load for ${order.orderNumber}`} onClose={onClose}>
      <p style={{ fontSize: 13, marginTop: 0, color: "var(--hf-text-muted)" }}>{fmtL(left)} not yet delivered or scheduled.</p>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        <div><label style={labelStyle}>Litres *</label><input aria-label="Load litres" type="number" value={litres} onChange={e => setLitres(e.target.value)} style={inputStyle} /></div>
        <div><label style={labelStyle}>Scheduled for *</label><input aria-label="Scheduled for" type="datetime-local" value={at} onChange={e => setAt(e.target.value)} style={inputStyle} /></div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
          <div><label style={labelStyle}>Driver</label><input value={driver} onChange={e => setDriver(e.target.value)} style={inputStyle} /></div>
          <div><label style={labelStyle}>Vehicle reg</label><input value={reg} onChange={e => setReg(e.target.value)} style={inputStyle} /></div>
        </div>
        <Err text={err} />
        <Footer onClose={onClose} label="Schedule load" pending={save.isPending} onSubmit={() => { const v = validateLoad(litres, left, at); if (v) return setErr(v); setErr(""); save.mutate() }} />
      </div>
    </Modal>
  )
}

function CancelDialog({ order, onDone, onClose }: { order: Order; onDone: () => void; onClose: () => void }) {
  const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  const cancel = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/orders/${order.id}/cancel`, { reason }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not cancel the order")),
  })
  return (
    <Modal title={`Cancel ${order.orderNumber}`} onClose={onClose}>
      <p style={{ fontSize: 13, marginTop: 0, color: "var(--hf-text-muted)" }}>Reserved fuel is released. Anything already delivered stays delivered and is still invoiced.</p>
      <label style={labelStyle}>Reason *</label>
      <input aria-label="Cancel reason" value={reason} onChange={e => setReason(e.target.value)} style={inputStyle} />
      <Err text={err} />
      <Footer onClose={onClose} label="Cancel order" pending={cancel.isPending} onSubmit={() => { if (reason.trim().length < 5) return setErr("Give a reason of at least 5 characters."); setErr(""); cancel.mutate() }} />
    </Modal>
  )
}

function LoadingDialog({ delivery, onDone, onClose }: { delivery: DeliveryLine; onDone: () => void; onClose: () => void }) {
  const [litres, setLitres] = useState(delivery.loadedLitres ? String(delivery.loadedLitres) : ""); const [seals, setSeals] = useState(delivery.sealNumbers ?? ""); const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/deliveries/${delivery.id}/loading`, { loadedLitres: Number(litres), sealNumbers: seals || null }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not record the loading")),
  })
  return (
    <Modal title="Record loading" onClose={onClose}>
      <label style={labelStyle}>Litres loaded *</label>
      <input aria-label="Loaded litres" type="number" value={litres} onChange={e => setLitres(e.target.value)} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Seal numbers</label>
      <input aria-label="Seal numbers" value={seals} onChange={e => setSeals(e.target.value)} style={inputStyle} />
      <Err text={err} />
      <Footer onClose={onClose} label="Save loading" pending={save.isPending} onSubmit={() => { const v = validateLoading(litres); if (v) return setErr(v); setErr(""); save.mutate() }} />
    </Modal>
  )
}
