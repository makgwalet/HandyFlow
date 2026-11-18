// src/pages/supply-chain/NewReturnDialog.tsx — raise a supplier return or credit claim, against a goods receipt or by hand.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { inp, Modal, ModalFooter, Field, ErrBox, fmtR, fmtDate } from "./scm.shared"
import { asList, errorText, emptyReturn, returnProblem, returnBody, returnValue, RETURN_REASONS, type ReturnForm } from "./scmOps"

interface Receipt { id: string; receiptNumber: string; status: string; receivedDate: string; supplierId: string }
interface GrLine { id: string; itemName: string; qtyAccepted: number; unitCost: number }
const BASE = "/api/v1/supply-chain"

export function NewReturnDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState<ReturnForm>(emptyReturn()); const [err, setErr] = useState("")
  const suppliers = useQuery({ queryKey: ["scm-suppliers-lite"], queryFn: () => apiClient.get(`${BASE}/suppliers`).then(asList<{ id: string; name: string }>) })
  const receipts = useQuery({ queryKey: ["scm-receipts-posted"], queryFn: () => apiClient.get(`${BASE}/goods-receipts`, { params: { size: 50 } }).then(asList<Receipt>) })
  const create = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/returns`, returnBody(f)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["scm-returns"] }); onClose() }, onError: e => setErr(errorText(e)),
  })

  const pickReceipt = async (id: string) => {
    const r = (receipts.data ?? []).find(x => x.id === id)
    if (!id) { setF({ ...f, goodsReceiptId: "", lines: emptyReturn().lines }); return }
    const lines = await apiClient.get(`${BASE}/goods-receipts/${id}/lines`).then(asList<GrLine>)
    setF({ ...f, goodsReceiptId: id, supplierId: r?.supplierId ?? f.supplierId, lines: lines.map(l => ({ grLineId: l.id, itemName: l.itemName, qty: "", unitCost: String(l.unitCost), max: Number(l.qtyAccepted) })) })
  }
  const set = (i: number, patch: Partial<ReturnForm["lines"][0]>) => setF(x => ({ ...x, lines: x.lines.map((l, j) => j === i ? { ...l, ...patch } : l) }))
  const byReceipt = !!f.goodsReceiptId
  const submit = () => { const p = returnProblem(f); if (p) { setErr(p); return } setErr(""); create.mutate() }

  return (
    <Modal title="New return or claim" wide onClose={onClose}>
      {err && <ErrBox msg={err} />}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <Field label="Type"><select style={inp} aria-label="Type" value={f.kind} onChange={e => setF({ ...f, kind: e.target.value as ReturnForm["kind"] })}><option value="RETURN">Return goods</option><option value="CLAIM">Credit claim (no goods back)</option></select></Field>
        <Field label="Reason"><select style={inp} aria-label="Reason" value={f.reason} onChange={e => setF({ ...f, reason: e.target.value })}>{RETURN_REASONS.map(r => <option key={r.value} value={r.value}>{r.label}</option>)}</select></Field>
        <Field label="Against goods receipt (optional)"><select style={inp} aria-label="Goods receipt" value={f.goodsReceiptId} onChange={e => void pickReceipt(e.target.value)}>
          <option value="">None — enter items by hand</option>{(receipts.data ?? []).filter(r => r.status === "POSTED").map(r => <option key={r.id} value={r.id}>{r.receiptNumber} · {fmtDate(r.receivedDate)}</option>)}</select></Field>
        <Field label="Supplier"><select style={inp} aria-label="Supplier" disabled={byReceipt} value={f.supplierId} onChange={e => setF({ ...f, supplierId: e.target.value })}><option value="">Choose…</option>{(suppliers.data ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></Field>
      </div>
      <h4 style={{ margin: "14px 0 6px" }}>{byReceipt ? "How many of each come back" : "Items"}</h4>
      {f.lines.map((l, i) => (
        <div key={i} style={{ display: "grid", gridTemplateColumns: byReceipt ? "3fr 1fr 1fr" : "3fr 1fr 1fr auto", gap: 8, marginBottom: 6, alignItems: "center" }}>
          {byReceipt ? <span>{l.itemName}{l.max != null && <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}> · up to {l.max}</span>}</span> : <input style={inp} placeholder="Item" aria-label={`Item ${i + 1}`} value={l.itemName} onChange={e => set(i, { itemName: e.target.value })} />}
          <input style={inp} placeholder="Qty" aria-label={`Quantity ${i + 1}`} inputMode="decimal" value={l.qty} onChange={e => set(i, { qty: e.target.value })} />
          {byReceipt ? <span style={{ fontSize: 13 }}>{fmtR(Number(l.unitCost))} each</span> : <input style={inp} placeholder="Unit cost" aria-label={`Unit cost ${i + 1}`} inputMode="decimal" value={l.unitCost} onChange={e => set(i, { unitCost: e.target.value })} />}
          {!byReceipt && <button type="button" aria-label={`Remove line ${i + 1}`} disabled={f.lines.length === 1} onClick={() => setF(x => ({ ...x, lines: x.lines.filter((_, j) => j !== i) }))}>✕</button>}
        </div>))}
      {!byReceipt && <button type="button" onClick={() => setF(x => ({ ...x, lines: [...x.lines, { grLineId: null, itemName: "", qty: "1", unitCost: "", max: null }] }))}>Add line</button>}
      <div style={{ fontSize: 13, margin: "10px 0" }}>Expected credit (before VAT) <strong>{fmtR(returnValue(f))}</strong></div>
      <Field label="Notes"><textarea style={inp} rows={2} aria-label="Notes" value={f.notes} onChange={e => setF({ ...f, notes: e.target.value })} /></Field>
      <ModalFooter onCancel={onClose} onConfirm={submit} label="Save as draft" loading={create.isPending} />
    </Modal>
  )
}
