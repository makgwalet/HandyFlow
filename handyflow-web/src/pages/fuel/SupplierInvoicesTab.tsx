// Supplier invoices: captured against a purchase order and the receipts they cover, matched on litres and price, then approved or rejected.
import { Fragment, useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import { fmtL, fmtR } from "./orderOps"
import { MATCH_LABEL, STATUS_LABEL, invoiceTotal, validateCapture, validateDecision, needsReason, type SupplierInvoice, type InvReceipt, type CaptureForm } from "./supplierInvoiceOps"
import type { PurchaseOrder } from "./purchasingOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const BASE = "/api/v1/fuel/supplier-invoices"
type Dialog = { kind: "capture" } | { kind: "approve" | "reject"; inv: SupplierInvoice } | null

const matchTone = (m: string) => m === "MATCHED" ? ["var(--hf-success-soft-strong)", "var(--hf-success-text-strong)"] : m === "VARIANCE" ? ["var(--hf-danger-soft)", "var(--hf-danger-text)"] : ["var(--hf-warning-soft)", "var(--hf-warning-text)"]
const statusTone = (s: string) => s === "APPROVED" ? ["var(--hf-success-soft-strong)", "var(--hf-success-text-strong)"] : s === "REJECTED" ? ["var(--hf-surface-alt)", "var(--hf-text-muted)"] : ["var(--hf-info-soft)", "var(--hf-info-text)"]
function Err({ text }: { text: string }) { return <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5, margin: "10px 0 0" }}>{text}</p> }

export default function SupplierInvoicesTab() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_SUPPLIER_INVOICE_MANAGE"])
  const qc = useQueryClient()
  const [dialog, setDialog] = useState<Dialog>(null)
  const [open, setOpen] = useState<string | null>(null)
  const list = useQuery<SupplierInvoice[]>({ queryKey: ["fuel-supplier-invoices"], queryFn: async () => unwrap(await apiClient.get(BASE)) })
  const refresh = () => { for (const k of ["fuel-supplier-invoices", "fuel-purchase-orders", "fuel-po-match"]) qc.invalidateQueries({ queryKey: [k] }) }
  const rows = list.data ?? []
  const waiting = rows.filter(r => r.status === "CAPTURED").length

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14, flexWrap: "wrap" }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0, flex: 1, minWidth: 260 }}>
          Capture the supplier's invoice, tick the deliveries it covers and we check the litres against what you received and the price against the receipts and the purchase order. A mismatch can still be approved, with a reason. Approval here records the decision; it does not post to Accounts Payable or pay anything.
        </p>
        {canManage && <button style={btnPrimary} onClick={() => setDialog({ kind: "capture" })}>Capture invoice</button>}
      </div>
      {waiting > 0 && <p style={{ fontSize: 13, color: "var(--hf-text)", margin: "0 0 10px" }}>{waiting} invoice{waiting === 1 ? "" : "s"} awaiting approval.</p>}

      {list.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No supplier invoices yet.</p> : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Invoice</th><th style={th}>Supplier</th><th style={th}>Order</th><th style={th}>Litres</th><th style={th}>Total</th><th style={th}>Match</th><th style={th}>Status</th><th style={th}></th></tr></thead>
            <tbody>
              {rows.map(i => {
                const [mb, mf] = matchTone(i.matchStatus), [sb, sf] = statusTone(i.status)
                return (
                  <Fragment key={i.id}>
                    <tr style={{ borderTop: "1px solid var(--hf-border)" }}>
                      <td style={td}><button style={{ ...btnGhost, padding: "2px 6px" }} onClick={() => setOpen(open === i.id ? null : i.id)}>{i.invoiceNumber}</button><br /><span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{i.invoiceDate}</span></td>
                      <td style={td}>{i.supplierName}</td>
                      <td style={td}>{i.poNumber ?? "–"}</td>
                      <td style={td}>{fmtL(i.litres)}</td>
                      <td style={td}>{fmtR(i.totalAmount)}</td>
                      <td style={td}>{i.status === "REJECTED" ? "–" : <Chip bg={mb} fg={mf}>{MATCH_LABEL[i.matchStatus]}</Chip>}</td>
                      <td style={td}><Chip bg={sb} fg={sf}>{STATUS_LABEL[i.status]}</Chip></td>
                      <td style={td}>{canManage && i.status === "CAPTURED" && (
                        <span style={{ display: "flex", gap: 6 }}>
                          <button style={btnGhost} onClick={() => setDialog({ kind: "approve", inv: i })}>Approve</button>
                          <button style={btnGhost} onClick={() => setDialog({ kind: "reject", inv: i })}>Reject</button>
                        </span>)}</td>
                    </tr>
                    {open === i.id && (
                      <tr><td colSpan={8} style={{ ...td, background: "var(--hf-surface-alt)" }}>
                        <p style={{ margin: "0 0 6px", fontSize: 12.5 }}>
                          Invoiced {fmtL(i.litres)} at {fmtR(i.pricePerLitre)} = {fmtR(i.goodsValue)}; received {fmtL(i.litresReceived)} ({fmtR(i.receiptValue)}). Charges {fmtR(i.otherCharges)}, VAT {fmtR(i.vatAmount)}.
                        </p>
                        {i.issues.length > 0 && <ul style={{ margin: "0 0 6px", paddingLeft: 18, color: "var(--hf-danger-text)", fontSize: 12.5 }}>{i.issues.map(x => <li key={x}>{x}</li>)}</ul>}
                        {i.receipts.length > 0 && (
                          <table style={{ width: "100%", borderCollapse: "collapse" }}>
                            <thead><tr><th style={th}>Received</th><th style={th}>Delivery note</th><th style={th}>Litres</th><th style={th}>Price / L</th><th style={th}>Cost</th></tr></thead>
                            <tbody>{i.receipts.map(r => (
                              <tr key={r.receiptId}><td style={td}>{new Date(r.receivedAt).toLocaleDateString("en-ZA")}{r.reversed ? " (reversed)" : ""}</td><td style={td}>{r.deliveryNote ?? "–"}</td><td style={td}>{fmtL(r.litres)}</td><td style={td}>{fmtR(r.pricePerLitre)}</td><td style={td}>{fmtR(r.totalCost)}</td></tr>
                            ))}</tbody>
                          </table>
                        )}
                        {i.decisionReason && <p style={{ margin: "6px 0 0", fontSize: 12.5, color: "var(--hf-text-muted)" }}>{i.status === "REJECTED" ? "Rejected" : "Approved"}: {i.decisionReason}</p>}
                      </td></tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {dialog?.kind === "capture" && <CaptureDialog onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog && dialog.kind !== "capture" && <DecisionDialog kind={dialog.kind} inv={dialog.inv} onDone={refresh} onClose={() => setDialog(null)} />}
    </div>
  )
}

function DecisionDialog({ kind, inv, onDone, onClose }: { kind: "approve" | "reject"; inv: SupplierInvoice; onDone: () => void; onClose: () => void }) {
  const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  const required = kind === "reject" || needsReason(inv.matchStatus)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/${inv.id}/${kind}`, { reason: reason.trim() || null }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, kind === "approve" ? "Could not approve the invoice" : "Could not reject the invoice")),
  })
  return (
    <Modal title={`${kind === "approve" ? "Approve" : "Reject"} ${inv.invoiceNumber}`} onClose={onClose}>
      {kind === "approve" && inv.issues.length > 0 && (
        <>
          <p style={{ fontSize: 13, margin: "0 0 6px" }}>This invoice does not match:</p>
          <ul style={{ margin: "0 0 10px", paddingLeft: 18, color: "var(--hf-danger-text)", fontSize: 12.5 }}>{inv.issues.map(x => <li key={x}>{x}</li>)}</ul>
        </>
      )}
      {kind === "reject" && <p style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 0 }}>The receipts on this invoice become free to put on a corrected invoice.</p>}
      <label style={labelStyle}>{required ? "Reason *" : "Note (optional)"}</label>
      <input aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} style={inputStyle} />
      {err && <Err text={err} />}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateDecision(reason, required); if (v) return setErr(v); setErr(""); save.mutate() }}>{kind === "approve" ? "Approve" : "Reject"} invoice</button>
      </div>
    </Modal>
  )
}

function CaptureDialog({ onDone, onClose }: { onDone: () => void; onClose: () => void }) {
  const [f, setF] = useState<CaptureForm>({ supplierId: "", invoiceNumber: "", invoiceDate: new Date().toISOString().slice(0, 10), dueDate: "", litres: "", price: "", other: "", vat: "" })
  const [poId, setPoId] = useState(""); const [notes, setNotes] = useState("")
  const [picked, setPicked] = useState<Record<string, boolean>>({})
  const [typed, setTyped] = useState({ litres: false, price: false })
  const [err, setErr] = useState("")
  const set = (k: keyof CaptureForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    if (k === "litres" || k === "price") setTyped(t => ({ ...t, [k]: true }))
    setF(s => ({ ...s, [k]: e.target.value }))
  }
  const suppliers = useQuery<{ id: string; name: string }[]>({ queryKey: ["fuel-suppliers-pick"], queryFn: async () => { const d = unwrap(await apiClient.get("/api/v1/fuel/suppliers")); return Array.isArray(d) ? d : (d?.content ?? []) } })
  const pos = useQuery<PurchaseOrder[]>({ queryKey: ["fuel-purchase-orders"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/purchase-orders")) })
  const cands = useQuery<InvReceipt[]>({ queryKey: ["fuel-invoice-candidates", f.supplierId], enabled: !!f.supplierId, queryFn: async () => unwrap(await apiClient.get(`${BASE}/candidates?supplierId=${f.supplierId}`)) })
  const myPos = (pos.data ?? []).filter(p => p.supplierId === f.supplierId && p.status !== "DRAFT" && p.status !== "CANCELLED")
  const chosen = (cands.data ?? []).filter(r => picked[r.receiptId])

  const togglePick = (r: InvReceipt, on: boolean) => {
    const next = { ...picked, [r.receiptId]: on }
    setPicked(next)
    const sel = (cands.data ?? []).filter(x => next[x.receiptId])
    const litres = sel.reduce((a, x) => a + x.litres, 0)
    const value = sel.reduce((a, x) => a + x.totalCost, 0)
    setF(s => ({
      ...s,
      litres: typed.litres ? s.litres : (litres > 0 ? String(Math.round(litres * 100) / 100) : ""),
      price: typed.price ? s.price : (litres > 0 ? String(Math.round((value / litres) * 10000) / 10000) : ""),
    }))
  }

  const total = invoiceTotal(f)
  const save = useMutation({
    mutationFn: () => apiClient.post(BASE, {
      supplierId: f.supplierId, purchaseOrderId: poId || null, invoiceNumber: f.invoiceNumber.trim(), invoiceDate: f.invoiceDate, dueDate: f.dueDate || null,
      litres: Number(f.litres), pricePerLitre: Number(f.price), otherCharges: f.other.trim() === "" ? null : Number(f.other), vatAmount: f.vat.trim() === "" ? null : Number(f.vat),
      notes: notes.trim() || null, receiptIds: chosen.map(r => r.receiptId),
    }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not capture the invoice")),
  })
  return (
    <Modal title="Capture supplier invoice" onClose={onClose}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 10 }}>
        <div>
          <label style={labelStyle}>Supplier *</label>
          <select aria-label="Supplier" style={inputStyle} value={f.supplierId} onChange={e => { setF(s => ({ ...s, supplierId: e.target.value })); setPoId(""); setPicked({}) }}>
            <option value="">Choose a supplier</option>
            {(suppliers.data ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
        <div>
          <label style={labelStyle}>Purchase order</label>
          <select aria-label="Purchase order" style={inputStyle} value={poId} onChange={e => setPoId(e.target.value)} disabled={!f.supplierId}>
            <option value="">None</option>
            {myPos.map(p => <option key={p.id} value={p.id}>{p.poNumber} · {p.fuelType}</option>)}
          </select>
        </div>
        <div><label style={labelStyle}>Invoice number *</label><input aria-label="Invoice number" style={inputStyle} value={f.invoiceNumber} onChange={set("invoiceNumber")} /></div>
        <div><label style={labelStyle}>Invoice date *</label><input aria-label="Invoice date" type="date" style={inputStyle} value={f.invoiceDate} onChange={set("invoiceDate")} /></div>
        <div><label style={labelStyle}>Due date</label><input aria-label="Due date" type="date" style={inputStyle} value={f.dueDate} onChange={set("dueDate")} /></div>
      </div>

      <p style={{ fontSize: 13, fontWeight: 700, margin: "14px 0 6px" }}>Deliveries this invoice covers</p>
      {!f.supplierId ? <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>Choose a supplier to see their receipts that are not on an invoice yet.</p>
        : (cands.data ?? []).length === 0 ? <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>{cands.isLoading ? "Loading…" : "No uninvoiced receipts from this supplier. You can still capture the invoice, but it will show as nothing to match."}</p>
        : (cands.data ?? []).map(r => (
          <label key={r.receiptId} style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, marginBottom: 4 }}>
            <input type="checkbox" aria-label={`Receipt ${r.deliveryNote ?? r.receiptId}`} checked={!!picked[r.receiptId]} onChange={e => togglePick(r, e.target.checked)} />
            {new Date(r.receivedAt).toLocaleDateString("en-ZA")} · {r.deliveryNote ?? "no delivery note"} · {fmtL(r.litres)} at {fmtR(r.pricePerLitre)}
          </label>
        ))}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: 10, marginTop: 14 }}>
        <div><label style={labelStyle}>Litres invoiced *</label><input aria-label="Litres invoiced" type="number" min="0" style={inputStyle} value={f.litres} onChange={set("litres")} /></div>
        <div><label style={labelStyle}>Price per litre *</label><input aria-label="Price per litre" type="number" min="0" step="0.0001" style={inputStyle} value={f.price} onChange={set("price")} /></div>
        <div><label style={labelStyle}>Other charges</label><input aria-label="Other charges" type="number" min="0" style={inputStyle} value={f.other} onChange={set("other")} /></div>
        <div><label style={labelStyle}>VAT</label><input aria-label="VAT" type="number" min="0" style={inputStyle} value={f.vat} onChange={set("vat")} /></div>
      </div>
      <p data-testid="total" style={{ fontSize: 13, margin: "8px 0 0" }}>Invoice total: {total == null ? "–" : fmtR(total)}</p>
      <label style={{ ...labelStyle, marginTop: 8 }}>Notes</label>
      <input aria-label="Notes" style={inputStyle} value={notes} onChange={e => setNotes(e.target.value)} />
      {err && <Err text={err} />}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateCapture(f); if (v) return setErr(v); setErr(""); save.mutate() }}>Capture invoice</button>
      </div>
    </Modal>
  )
}
