// src/pages/supply-chain/RfqTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, Scale } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Badge, inp, TH, TD, Modal, ModalFooter, Field, ErrBox, Spinner, EmptyState, Banner, filterPill, fmtR, fmtDate } from "./scm.shared"
import {
  asList, asOne, errorText, emptyRfq, rfqProblem, rfqBody, weightsTotal, quoteProblem, quoteBody, overridesRecommendation, awardProblem, rfqActions,
  type RfqForm, type QuoteForm, type RfqAction, type Weights,
} from "./scmOps"

interface Rfq { id: string; rfqNumber: string; status: string; title: string; closesAt: string | null; estimatedTotal: number | null; quotationCount: number; awardedQuotationId: string | null; awardReason: string | null; overridesRecommendation: boolean; poId: string | null; weights: Weights
  lines?: { id: string; itemName: string; quantity: number }[]; invitations?: { supplierId: string; supplierName: string; status: string }[] }
interface Row { quotationId: string; supplierName: string; status: string; landedCost: number; totalInclVat: number; leadTimeDays: number; rank: number | null; totalScore: number | null; ranked: boolean; flags: string[]; estimatedSaving: number | null }
interface Comparison { recommendedQuotationId: string | null; lowestPriceQuotationId: string | null; rows: Row[] }
interface SupplierLite { id: string; name: string }

const BASE = "/api/v1/supply-chain/rfqs"
const LABEL: Record<RfqAction, string> = { publish: "Publish", close: "Close", extend: "Extend", cancel: "Cancel", award: "Award", "create-po": "Create purchase order", capture: "Capture quotation" }
const WEIGHT_KEYS: [keyof Weights, string][] = [["price", "Price"], ["leadTime", "Lead time"], ["technical", "Technical"], ["performance", "Performance"], ["bbbee", "B-BBEE"]]

export function RfqTab() {
  const qc = useQueryClient()
  const canManage = usePermissions(["SCM_RFQ_MANAGE", "SCM_ADMIN"])
  const canAward = usePermissions(["SCM_ORDER", "SCM_ADMIN"])
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState<RfqForm>(emptyRfq())
  const [err, setErr] = useState("")
  const [openId, setOpenId] = useState<string | null>(null)
  const [quote, setQuote] = useState<QuoteForm | null>(null)
  const [quoteErr, setQuoteErr] = useState("")
  const [awardId, setAwardId] = useState("")
  const [reason, setReason] = useState("")
  const [detailErr, setDetailErr] = useState("")

  const list = useQuery({ queryKey: ["scm-rfqs"], queryFn: () => apiClient.get(BASE, { params: { size: 100 } }).then(asList<Rfq>) })
  const suppliers = useQuery({ queryKey: ["scm-suppliers-lite"], enabled: creating, queryFn: () => apiClient.get("/api/v1/supply-chain/suppliers").then(asList<SupplierLite>) })
  const detail = useQuery({ queryKey: ["scm-rfq", openId], enabled: !!openId, queryFn: () => apiClient.get(`${BASE}/${openId}`).then(r => asOne<Rfq>(r)) })
  const rfq = detail.data
  // The comparison is refused while quotations are sealed (before closing); that is shown as a note, not an error.
  const comparison = useQuery({ queryKey: ["scm-rfq-comparison", openId, rfq?.status, rfq?.quotationCount], enabled: !!openId && !!rfq && rfq.quotationCount > 0, retry: false,
    queryFn: () => apiClient.get(`${BASE}/${openId}/comparison`).then(r => asOne<Comparison>(r)) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["scm-rfqs"] }); qc.invalidateQueries({ queryKey: ["scm-rfq"] }); qc.invalidateQueries({ queryKey: ["scm-rfq-comparison"] }) }

  const create = useMutation({
    mutationFn: () => apiClient.post(BASE, rfqBody(form)),
    onSuccess: () => { setCreating(false); setForm(emptyRfq()); setErr(""); refresh() },
    onError: (e) => setErr(errorText(e)),
  })
  const act = useMutation({
    mutationFn: ({ path, body }: { path: string; body?: unknown }) => apiClient.post(`${BASE}/${openId}/${path}`, body ?? {}),
    onSuccess: () => { setDetailErr(""); setAwardId(""); setReason(""); refresh() },
    onError: (e) => setDetailErr(errorText(e)),
  })
  const capture = useMutation({
    mutationFn: (q: QuoteForm) => apiClient.post(`${BASE}/${openId}/quotations`, quoteBody(q)),
    onSuccess: () => { setQuote(null); setQuoteErr(""); refresh() },
    onError: (e) => setQuoteErr(errorText(e)),
  })

  const submitCreate = () => { const p = rfqProblem(form); if (p) { setErr(p); return } setErr(""); create.mutate() }
  const startCapture = () => rfq && setQuote({ supplierId: "", quoteReference: "", validUntil: "", leadTimeDays: "", warrantyMonths: "", paymentTermsDays: "", deliveryCost: "", otherCharges: "",
    lines: (rfq.lines ?? []).map(l => ({ rfqLineId: l.id, noBid: false, quantity: String(l.quantity), unitPrice: "", discountPct: "" })) })
  const doAward = () => { const p = awardProblem(awardId, reason); if (p) { setDetailErr(p); return } act.mutate({ path: "award", body: { quotationId: awardId, reason: reason.trim() } }) }

  const rows = list.data ?? []
  const cmp = comparison.data
  const w = form.weights
  const total = weightsTotal(w)

  return (
    <div style={{ padding: 20 }}>
      <div style={{ display: "flex", marginBottom: 14 }}>
        <div style={{ flex: 1 }} />
        {canManage && <button onClick={() => setCreating(true)} style={{ ...filterPill(true), display: "flex", gap: 6, alignItems: "center" }}><Plus size={14} /> New RFQ</button>}
      </div>

      {list.isLoading ? <Spinner /> : list.isError ? <ErrBox msg={errorText(list.error, "Could not load RFQs")} /> : rows.length === 0 ? (
        <EmptyState icon={Scale} title="No RFQs" sub="Ask suppliers for quotations and compare them here." />
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr>{["Number", "Title", "Closes", "Estimate", "Quotes", "Status"].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
          <tbody>{rows.map(r => (
            <tr key={r.id} onClick={() => { setOpenId(r.id); setDetailErr("") }} style={{ cursor: "pointer" }}>
              <td style={TD}>{r.rfqNumber}</td><td style={TD}>{r.title}</td><td style={TD}>{fmtDate(r.closesAt)}</td>
              <td style={TD}>{r.estimatedTotal == null ? "—" : fmtR(r.estimatedTotal)}</td><td style={TD}>{r.quotationCount}</td><td style={TD}><Badge status={r.status} /></td>
            </tr>))}</tbody>
        </table>
      )}

      {creating && (
        <Modal title="New RFQ" wide onClose={() => setCreating(false)}>
          {err && <ErrBox msg={err} />}
          <Field label="Title"><input style={inp} aria-label="Title" value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} /></Field>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginTop: 8 }}>
            <Field label="Required by"><input style={inp} type="date" value={form.requiredByDate} onChange={e => setForm({ ...form, requiredByDate: e.target.value })} /></Field>
            <Field label="Closes at"><input style={inp} type="datetime-local" value={form.closesAt} onChange={e => setForm({ ...form, closesAt: e.target.value })} /></Field>
          </div>
          <h4 style={{ margin: "14px 0 6px" }}>Lines</h4>
          {form.lines.map((l, i) => (
            <div key={i} style={{ display: "grid", gridTemplateColumns: "3fr 1fr 1fr auto", gap: 8, marginBottom: 6 }}>
              <input style={inp} placeholder="Item" aria-label={`Item ${i + 1}`} value={l.itemName} onChange={e => setForm(f => ({ ...f, lines: f.lines.map((x, j) => j === i ? { ...x, itemName: e.target.value } : x) }))} />
              <input style={inp} placeholder="Qty" aria-label={`Quantity ${i + 1}`} value={l.quantity} onChange={e => setForm(f => ({ ...f, lines: f.lines.map((x, j) => j === i ? { ...x, quantity: e.target.value } : x) }))} />
              <input style={inp} placeholder="Estimate" value={l.estimatedUnitCost} onChange={e => setForm(f => ({ ...f, lines: f.lines.map((x, j) => j === i ? { ...x, estimatedUnitCost: e.target.value } : x) }))} />
              <button type="button" disabled={form.lines.length === 1} onClick={() => setForm(f => ({ ...f, lines: f.lines.filter((_, j) => j !== i) }))}>✕</button>
            </div>))}
          <button type="button" style={filterPill(false)} onClick={() => setForm(f => ({ ...f, lines: [...f.lines, { itemName: "", quantity: "1", estimatedUnitCost: "" }] }))}>Add line</button>
          <h4 style={{ margin: "14px 0 6px" }}>Suppliers to invite</h4>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
            {(suppliers.data ?? []).map(s => (
              <label key={s.id} style={{ fontSize: 13 }}><input type="checkbox" checked={form.supplierIds.includes(s.id)}
                onChange={e => setForm(f => ({ ...f, supplierIds: e.target.checked ? [...f.supplierIds, s.id] : f.supplierIds.filter(x => x !== s.id) }))} /> {s.name}</label>))}
          </div>
          <h4 style={{ margin: "14px 0 6px" }}>How quotations are scored <span style={{ color: total === 100 ? undefined : "var(--hf-danger-text)" }}>({total}/100)</span></h4>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 8 }}>
            {WEIGHT_KEYS.map(([k, label]) => (
              <Field key={k} label={label}><input style={inp} aria-label={`${label} weight`} value={w[k]} onChange={e => setForm(f => ({ ...f, weights: { ...f.weights, [k]: Number(e.target.value) } }))} /></Field>))}
          </div>
          <ModalFooter onCancel={() => setCreating(false)} onConfirm={submitCreate} label="Save draft" loading={create.isPending} />
        </Modal>
      )}

      {openId && (
        <Modal title={rfq ? `${rfq.rfqNumber} — ${rfq.title}` : "RFQ"} wide onClose={() => setOpenId(null)}>
          {detail.isLoading || !rfq ? <Spinner /> : (
            <>
              {detailErr && <ErrBox msg={detailErr} />}
              <p><Badge status={rfq.status} /> {rfq.quotationCount} quotation(s)</p>
              {(rfq.invitations ?? []).length > 0 && <p style={{ fontSize: 13 }}>Invited: {(rfq.invitations ?? []).map(i => `${i.supplierName} (${i.status.toLowerCase()})`).join(", ")}</p>}

              <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "8px 0" }}>
                {rfqActions(rfq.status, !!rfq.poId).filter(a => a !== "award").map(a => (
                  (canManage || (a === "create-po" && canAward)) && (
                    <button key={a} style={filterPill(false)} disabled={act.isPending}
                      onClick={() => { if (a === "capture") startCapture(); else if (a === "cancel") act.mutate({ path: "cancel", body: { reason: "Cancelled from the RFQ screen" } }); else if (a === "extend") { const d = window.prompt("New closing time (YYYY-MM-DDTHH:mm)"); if (d) act.mutate({ path: "extend", body: { closesAt: new Date(d).toISOString(), reason: "Extended" } }) } else act.mutate({ path: a }) }}>
                      {LABEL[a]}
                    </button>)))}
              </div>

              {rfq.status === "OPEN" && rfq.quotationCount > 0 && <Banner variant="info">Quotations are sealed until the RFQ is closed — the comparison appears then.</Banner>}
              {comparison.isError && rfq.status !== "OPEN" && <ErrBox msg={errorText(comparison.error, "Could not load the comparison")} />}

              {cmp && (
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead><tr>{["#", "Supplier", "Landed cost", "Incl. VAT", "Lead time", "Score", "Notes", ""].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
                  <tbody>{cmp.rows.map(r => (
                    <tr key={r.quotationId}>
                      <td style={TD}>{r.rank ?? "—"}</td>
                      <td style={TD}>{r.supplierName} {r.quotationId === cmp.recommendedQuotationId && <b title="Recommended">★ Recommended</b>} {r.quotationId === cmp.lowestPriceQuotationId && <i>Lowest price</i>}</td>
                      <td style={TD}>{fmtR(r.landedCost)}</td><td style={TD}>{fmtR(r.totalInclVat)}</td><td style={TD}>{r.leadTimeDays} days</td>
                      <td style={TD}>{r.totalScore == null ? "—" : Number(r.totalScore).toFixed(1)}</td>
                      <td style={TD}>{r.flags.map(f => f.replace(/_/g, " ").toLowerCase()).join(", ") || "—"}</td>
                      <td style={TD}>
                        {canManage && rfq.status === "CLOSED" && r.status !== "DISQUALIFIED" && (
                          <>
                            <button style={filterPill(awardId === r.quotationId)} onClick={() => setAwardId(r.quotationId)}>Choose</button>{" "}
                            <button style={filterPill(false)} onClick={() => { const why = window.prompt("Why is this quotation disqualified?"); if (why) apiClient.post(`${BASE}/${rfq.id}/quotations/${r.quotationId}/disqualify`, { reason: why }).then(refresh).catch(e => setDetailErr(errorText(e))) }}>Disqualify</button>
                          </>)}
                      </td>
                    </tr>))}</tbody>
                </table>
              )}

              {canAward && rfq.status === "CLOSED" && cmp && (
                <div style={{ marginTop: 12 }}>
                  {overridesRecommendation(awardId, cmp.recommendedQuotationId) && <Banner variant="warning">This is not the recommended quotation. It will be recorded as overriding the recommendation, so the reason matters.</Banner>}
                  <Field label="Why this supplier (required)"><textarea style={inp} aria-label="Award reason" value={reason} onChange={e => setReason(e.target.value)} /></Field>
                  <button style={{ ...filterPill(true), marginTop: 8 }} disabled={act.isPending} onClick={doAward}>Award</button>
                </div>
              )}

              {rfq.status === "AWARDED" && <Banner variant="info">Awarded{rfq.overridesRecommendation ? " (overrode the recommendation)" : ""}: {rfq.awardReason}</Banner>}
            </>
          )}
        </Modal>
      )}

      {quote && rfq && (
        <Modal title="Capture quotation" wide onClose={() => setQuote(null)}>
          {quoteErr && <ErrBox msg={quoteErr} />}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Supplier">
              <select style={inp} aria-label="Supplier" value={quote.supplierId} onChange={e => setQuote({ ...quote, supplierId: e.target.value })}>
                <option value="">Choose…</option>{(rfq.invitations ?? []).map(i => <option key={i.supplierId} value={i.supplierId}>{i.supplierName}</option>)}
              </select>
            </Field>
            <Field label="Their reference"><input style={inp} value={quote.quoteReference} onChange={e => setQuote({ ...quote, quoteReference: e.target.value })} /></Field>
            <Field label="Lead time (days)"><input style={inp} aria-label="Lead time" value={quote.leadTimeDays} onChange={e => setQuote({ ...quote, leadTimeDays: e.target.value })} /></Field>
            <Field label="Valid until"><input style={inp} type="date" value={quote.validUntil} onChange={e => setQuote({ ...quote, validUntil: e.target.value })} /></Field>
            <Field label="Delivery cost"><input style={inp} value={quote.deliveryCost} onChange={e => setQuote({ ...quote, deliveryCost: e.target.value })} /></Field>
            <Field label="Other charges"><input style={inp} value={quote.otherCharges} onChange={e => setQuote({ ...quote, otherCharges: e.target.value })} /></Field>
          </div>
          <h4 style={{ margin: "14px 0 6px" }}>Prices</h4>
          {quote.lines.map((l, i) => {
            const line = (rfq.lines ?? []).find(x => x.id === l.rfqLineId)
            const set = (patch: Partial<typeof l>) => setQuote({ ...quote, lines: quote.lines.map((x, j) => j === i ? { ...x, ...patch } : x) })
            return (
              <div key={l.rfqLineId} style={{ display: "grid", gridTemplateColumns: "3fr 1fr 1fr 1fr auto", gap: 8, marginBottom: 6, alignItems: "center" }}>
                <span>{line?.itemName}</span>
                <input style={inp} disabled={l.noBid} aria-label={`Quantity ${i + 1}`} value={l.quantity} onChange={e => set({ quantity: e.target.value })} />
                <input style={inp} disabled={l.noBid} aria-label={`Unit price ${i + 1}`} placeholder="Unit price" value={l.unitPrice} onChange={e => set({ unitPrice: e.target.value })} />
                <input style={inp} disabled={l.noBid} placeholder="Disc %" value={l.discountPct} onChange={e => set({ discountPct: e.target.value })} />
                <label style={{ fontSize: 12 }}><input type="checkbox" checked={l.noBid} onChange={e => set({ noBid: e.target.checked })} /> No bid</label>
              </div>)
          })}
          <ModalFooter onCancel={() => setQuote(null)} label="Save quotation" loading={capture.isPending}
            onConfirm={() => { const p = quoteProblem(quote); if (p) { setQuoteErr(p); return } setQuoteErr(""); capture.mutate(quote) }} />
        </Modal>
      )}
    </div>
  )
}
