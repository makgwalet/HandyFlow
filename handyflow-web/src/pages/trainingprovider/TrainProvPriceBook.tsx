// src/pages/trainingprovider/TrainProvPriceBook.tsx
//
// The fees recorded for one programme version, each with where it comes from. Entered by an administrator; nothing is seeded. A line is never edited:
// a changed fee is a new line and the old one is withdrawn with a reason. Invoicing only shows a line as a suggestion.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { BOOK_NOTE, FEE_TYPES, NONE_NOTE, emptyPrice, lineText, priceBody, priceProblem, stateText, withdrawProblem, type PriceForm, type PriceLine } from "./priceRules"
import { Badge, Field, Notice } from "./tpUi"
import { errText, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { FND, LRN } from "./tpApi"
import type { Funder } from "./fundingRules"

const API = `${LRN}/price-lines`

export default function TrainProvPriceBook({ versionId, isAdmin }: { versionId: string; isAdmin: boolean }) {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState<PriceForm>(emptyPrice(todayIso()))
  const [withdrawing, setWithdrawing] = useState<string | null>(null)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data = [], isLoading } = useQuery<PriceLine[]>({
    queryKey: ["tp-price-lines", versionId], enabled: open,
    queryFn: async () => unwrap<PriceLine[]>(await apiClient.get(API, { params: { programmeVersionId: versionId } })),
  })
  const { data: funders = [] } = useQuery<Funder[]>({
    queryKey: ["tp-funders-active"], enabled: open && adding,
    queryFn: async () => unwrap<Funder[]>(await apiClient.get(`${FND}/funders`, { params: { status: "ACTIVE" } })),
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ["tp-price-lines", versionId] })
  const run = async (fn: () => Promise<unknown>, done: () => void) => {
    setBusy(true); setError("")
    try { await fn(); done(); await refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  if (!open) return <div style={{ marginTop: 8 }}><button style={ghost} onClick={() => setOpen(true)}>Fees (price book)</button></div>
  const live = data.filter(p => p.status === "ACTIVE")
  const earlier = data.filter(p => p.status === "WITHDRAWN")
  return (
    <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
      <strong style={{ fontSize: 13 }}>Fees (price book)</strong>
      <div style={muted}>{BOOK_NOTE}</div>
      {isLoading ? <div style={muted}>Loading…</div> : live.length === 0 ? <div style={muted}>{NONE_NOTE}</div> : live.map(p => (
        <div key={p.id} style={{ fontSize: 13, display: "grid", gap: 2 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}><span>{lineText(p)}</span><Badge text={stateText(p, todayIso())} tone={p.inForceToday ? "good" : "neutral"} /></div>
          <div style={muted}>Source: {p.sourceNote}</div>
          {isAdmin && withdrawing !== p.id && <button style={{ ...ghost, justifySelf: "start" }} onClick={() => { setWithdrawing(p.id); setReason(""); setError("") }}>Withdraw</button>}
          {withdrawing === p.id && (
            <div style={{ display: "grid", gap: 6 }}>
              <Field label="Why is it withdrawn?"><input style={input} value={reason} onChange={e => setReason(e.target.value)} /></Field>
              <Notice text={error} />
              <div style={{ display: "flex", gap: 8 }}>
                <button style={solid} disabled={busy} onClick={() => { const m = withdrawProblem(reason); if (m) { setError(m); return } void run(() => apiClient.post(`${API}/${p.id}/withdraw`, { reason: reason.trim() }), () => setWithdrawing(null)) }}>Withdraw fee</button>
                <button style={ghost} onClick={() => setWithdrawing(null)}>Cancel</button>
              </div>
            </div>
          )}
        </div>
      ))}
      {earlier.map(p => <div key={p.id} style={muted}>{lineText(p)}. Withdrawn: {p.withdrawnReason}</div>)}
      {isAdmin && !adding && <button style={{ ...ghost, justifySelf: "start" }} onClick={() => { setAdding(true); setF(emptyPrice(todayIso())); setError("") }}>Add a fee</button>}
      {!isAdmin && <div style={muted}>Only an administrator can add or withdraw a fee.</div>}
      {adding && (
        <div style={{ display: "grid", gap: 8 }}>
          <Field label="Kind of fee"><select aria-label="Kind of fee" style={input} value={f.feeType} onChange={e => setF({ ...f, feeType: e.target.value })}><option value="">Choose…</option>{Object.entries(FEE_TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
          {f.feeType === "OTHER" && <Field label="What is the fee for?"><input style={input} value={f.description} onChange={e => setF({ ...f, description: e.target.value })} /></Field>}
          <Field label="For one funder only (optional)" hint="Leave blank for a fee that applies whoever pays."><select aria-label="Funder" style={input} value={f.funderId} onChange={e => setF({ ...f, funderId: e.target.value })}><option value="">Any funder</option>{funders.map(x => <option key={x.id} value={x.id}>{x.name}</option>)}</select></Field>
          <Field label="Amount (rand)"><input aria-label="Amount" inputMode="decimal" style={input} value={f.amount} onChange={e => setF({ ...f, amount: e.target.value })} /></Field>
          <Field label="Does the amount include VAT?"><select aria-label="Includes VAT" style={input} value={f.includesVat} onChange={e => setF({ ...f, includesVat: e.target.value })}><option value="">Choose…</option><option value="yes">Yes, it includes VAT</option><option value="no">No, VAT is not included</option></select></Field>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <Field label="From"><input type="date" style={input} value={f.validFrom} onChange={e => setF({ ...f, validFrom: e.target.value })} /></Field>
            <Field label="To (optional)"><input type="date" style={input} value={f.validTo} onChange={e => setF({ ...f, validTo: e.target.value })} /></Field>
          </div>
          <Field label="Where does the fee come from?"><textarea style={input} rows={2} value={f.sourceNote} onChange={e => setF({ ...f, sourceNote: e.target.value })} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} disabled={busy} onClick={() => { const p = priceProblem(f); if (p) { setError(p); return } void run(() => apiClient.post(API, priceBody(versionId, f)), () => setAdding(false)) }}>Save fee</button>
            <button style={ghost} onClick={() => setAdding(false)}>Cancel</button>
          </div>
        </div>
      )}
      <button style={{ ...ghost, justifySelf: "start" }} onClick={() => setOpen(false)}>Hide fees</button>
    </div>
  )
}
