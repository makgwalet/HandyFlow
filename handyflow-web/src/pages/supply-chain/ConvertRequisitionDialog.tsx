// src/pages/supply-chain/ConvertRequisitionDialog.tsx
// Turns an approved requisition into a draft PO. The buyer may set the real price; a change from the estimate needs a reason,
// a lower price is recorded as a saving, and a rise beyond the company's tolerance must be approved by someone else.
import { useState } from "react"
import { useQuery, useMutation } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { inp, Modal, ModalFooter, Field, ErrBox, Spinner, Banner, fmtR } from "./scm.shared"
import { asList, asOne, errorText, PRICE_REASONS, priceView, convertProblem, convertBody, type ConvertForm } from "./scmOps"

interface Line { id: string; itemName: string; qtyRequested: number; qtyRemaining?: number; estimatedUnitCost: number }
interface Detail { id: string; number: string; lines: Line[] }
interface Supplier { id: string; name: string }
interface Policy { effective?: { priceIncreaseTolerancePct?: number } }

export function ConvertRequisitionDialog({ requisitionId, number, onClose, onDone }: { requisitionId: string; number: string; onClose: () => void; onDone: () => void }) {
  const [form, setForm] = useState<ConvertForm | null>(null)
  const [err, setErr] = useState("")

  const detail = useQuery({
    queryKey: ["scm-requisition", requisitionId],
    queryFn: () => apiClient.get(`/api/v1/supply-chain/requisitions/${requisitionId}`).then(r => asOne<Detail>(r)),
  })
  const suppliers = useQuery({
    queryKey: ["scm-suppliers-list"],
    queryFn: () => apiClient.get("/api/v1/supply-chain/suppliers?size=200").then(asList<Supplier>),
  })
  const policy = useQuery({
    queryKey: ["scm-procurement-policy"],
    queryFn: () => apiClient.get("/api/v1/supply-chain/procurement-policy").then(r => asOne<Policy>(r)),
  })
  const tolerance = policy.data?.effective?.priceIncreaseTolerancePct ?? 5

  // Build the editable form once the requisition has loaded; lines with nothing left to order are not offered.
  if (!form && detail.data) {
    const lines = (detail.data.lines ?? []).map(l => {
      const left = l.qtyRemaining ?? l.qtyRequested
      return { requisitionLineId: l.id, itemName: l.itemName, selected: left > 0, quantity: String(left), unitCost: String(l.estimatedUnitCost),
        estimate: l.estimatedUnitCost, reason: "", note: "", quotationRef: "", left }
    }).filter(l => l.left > 0).map(({ left: _left, ...rest }) => rest)
    setForm({ supplierId: "", notes: "", lines })
  }

  const convert = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/supply-chain/requisitions/${requisitionId}/convert-to-po`, convertBody(form!)),
    onSuccess: onDone,
    onError: (e) => setErr(errorText(e)),
  })

  const setLine = (i: number, k: string, v: string | boolean) =>
    setForm(f => (f ? { ...f, lines: f.lines.map((l, j) => (j === i ? { ...l, [k]: v } : l)) } : f))

  const submit = () => {
    const p = form ? convertProblem(form) : "Nothing to convert"
    if (p) { setErr(p); return }
    setErr(""); convert.mutate()
  }

  return (
    <Modal title={`Convert to purchase order — ${number}`} wide onClose={onClose}>
      {err && <ErrBox msg={err} />}
      {detail.isLoading || !form ? <Spinner /> : detail.isError ? <ErrBox msg={errorText(detail.error, "Could not load the requisition")} /> : (
        <>
          <Field label="Supplier">
            <select style={inp} aria-label="Supplier" value={form.supplierId} onChange={e => setForm({ ...form, supplierId: e.target.value })}>
              <option value="">Choose…</option>
              {(suppliers.data ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          {form.lines.map((l, i) => {
            const v = priceView(l.estimate, Number(l.unitCost), Number(l.quantity), tolerance)
            return (
              <div key={l.requisitionLineId} style={{ border: "1px solid var(--hf-border)", borderRadius: 8, padding: 10, margin: "10px 0" }}>
                <label style={{ fontWeight: 600, display: "flex", gap: 8 }}>
                  <input type="checkbox" checked={l.selected} onChange={e => setLine(i, "selected", e.target.checked)} /> {l.itemName}
                </label>
                {l.selected && (
                  <>
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginTop: 8 }}>
                      <Field label="Quantity"><input style={inp} aria-label={`Quantity ${l.itemName}`} value={l.quantity} onChange={e => setLine(i, "quantity", e.target.value)} /></Field>
                      <Field label={`Unit price (estimate ${fmtR(l.estimate)})`}><input style={inp} aria-label={`Unit price ${l.itemName}`} value={l.unitCost} onChange={e => setLine(i, "unitCost", e.target.value)} /></Field>
                      <Field label="Quotation reference"><input style={inp} aria-label={`Quotation ${l.itemName}`} value={l.quotationRef} onChange={e => setLine(i, "quotationRef", e.target.value)} /></Field>
                    </div>
                    {v.kind !== "unchanged" && (
                      <>
                        <Banner variant={v.kind === "beyond" ? "warning" : "info"}>{v.text}</Banner>
                        <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: 8 }}>
                          <Field label="Reason for the price change">
                            <select style={inp} aria-label={`Reason ${l.itemName}`} value={l.reason} onChange={e => setLine(i, "reason", e.target.value)}>
                              <option value="">Choose…</option>
                              {PRICE_REASONS.map(r => <option key={r.value} value={r.value}>{r.label}</option>)}
                            </select>
                          </Field>
                          <Field label="Note"><input style={inp} aria-label={`Note ${l.itemName}`} value={l.note} onChange={e => setLine(i, "note", e.target.value)} /></Field>
                        </div>
                      </>
                    )}
                  </>
                )}
              </div>
            )
          })}
          <Field label="Notes"><textarea style={inp} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} /></Field>
          <ModalFooter onCancel={onClose} onConfirm={submit} label="Create draft PO" loading={convert.isPending} />
        </>
      )}
    </Modal>
  )
}
