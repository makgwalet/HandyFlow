// src/pages/supply-chain/PolicyRefinements.tsx
// Price tolerance per catalogue category, VAT recoverability per supplier, exchange rates, and the contract-savings report.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { inp, TH, TD, Field, ErrBox, Spinner, Banner, filterPill, fmtR, fmtDate } from "./scm.shared"
import { asOne, errorText } from "./scmOps"
import { pctProblem, rateProblem, rateBody, emptyRate, restateText, savingPct } from "./scmPolicy"

const BASE = "/api/v1/supply-chain"

interface Overview {
  baseCurrency: string; companyTolerancePct: number; companyVatRecoverablePct: number
  categoryTolerances: { categoryId: string; categoryName: string; tolerancePct: number }[]
  categories: { id: string; name: string }[]
  supplierVat: { supplierId: string; supplierName: string; vatRecoverablePct: number; note: string | null }[]
  suppliers: { id: string; name: string }[]
  exchangeRates: { id: string; currency: string; rate: number; effectiveFrom: string; createdByName: string | null }[]
  currenciesWithoutRate: string[]
}
interface Change { overview?: Overview; commitmentsRestated?: number; commitmentsSkipped?: number }

export function PolicyRefinements({ isAdmin }: { isAdmin: boolean }) {
  const qc = useQueryClient()
  const [err, setErr] = useState("")
  const [info, setInfo] = useState("")
  const [cat, setCat] = useState({ id: "", pct: "" })
  const [sup, setSup] = useState({ id: "", pct: "", note: "" })
  const [rate, setRate] = useState(emptyRate(new Date().toISOString().slice(0, 10)))

  const ov = useQuery({ queryKey: ["scm-policy-overrides"], queryFn: () => apiClient.get(`${BASE}/procurement-policy/overrides`).then(r => asOne<Overview>(r)) })
  const d = ov.data
  const categories = d?.categories ?? [], suppliers = d?.suppliers ?? []
  const base = d?.baseCurrency ?? "ZAR"

  const done = (r: unknown) => {
    const c = asOne<Change>(r as never) ?? {}
    setErr(""); setInfo(restateText(c.commitmentsRestated ?? 0, c.commitmentsSkipped ?? 0))
    qc.invalidateQueries({ queryKey: ["scm-policy-overrides"] })
    qc.invalidateQueries({ queryKey: ["scm-budgets"] })
  }
  const fail = (e: unknown) => { setInfo(""); setErr(errorText(e)) }
  const useRun = (fn: () => Promise<unknown>) => useMutation({ mutationFn: fn, onSuccess: done, onError: fail })

  const setTol = useRun(() => apiClient.put(`${BASE}/procurement-policy/category-tolerance/${cat.id}`, { tolerancePct: Number(cat.pct) }))
  const clrTol = useMutation({ mutationFn: (id: string) => apiClient.delete(`${BASE}/procurement-policy/category-tolerance/${id}`), onSuccess: done, onError: fail })
  const setVat = useRun(() => apiClient.put(`${BASE}/procurement-policy/supplier-vat/${sup.id}`, { vatRecoverablePct: Number(sup.pct), note: sup.note.trim() || null }))
  const clrVat = useMutation({ mutationFn: (id: string) => apiClient.delete(`${BASE}/procurement-policy/supplier-vat/${id}`), onSuccess: done, onError: fail })
  const addRate = useRun(() => apiClient.post(`${BASE}/exchange-rates`, rateBody(rate)))
  const delRate = useMutation({ mutationFn: (id: string) => apiClient.delete(`${BASE}/exchange-rates/${id}`), onSuccess: done, onError: fail })

  const submitTol = () => { const p = !cat.id ? "Choose a category" : pctProblem(cat.pct, "tolerance"); if (p) setErr(p); else { setErr(""); setTol.mutate() } }
  const submitVat = () => { const p = !sup.id ? "Choose a supplier" : pctProblem(sup.pct, "recoverable VAT"); if (p) setErr(p); else { setErr(""); setVat.mutate() } }
  const submitRate = () => { const p = rateProblem(rate, base); if (p) setErr(p); else { setErr(""); addRate.mutate() } }

  if (ov.isLoading) return <Spinner />
  if (ov.isError) return <ErrBox msg={errorText(ov.error, "Could not load the policy overrides. The server may need restarting after an update.")} />

  return (
    <div style={{ marginTop: 28 }}>
      {err && <ErrBox msg={err} />}
      {info && <Banner variant="info">{info}</Banner>}

      <h3>Price tolerance by category</h3>
      <p style={{ fontSize: 13, opacity: 0.8 }}>A category's own tolerance replaces the company's ({d?.companyTolerancePct ?? 5} %) for items in that category.</p>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead><tr>{["Category", "Tolerance", ""].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
        <tbody>
          {(d?.categoryTolerances ?? []).map(c => (
            <tr key={c.categoryId}><td style={TD}>{c.categoryName}</td><td style={TD}>{c.tolerancePct} %</td>
              <td style={TD}>{isAdmin && <button style={filterPill(false)} onClick={() => clrTol.mutate(c.categoryId)}>Remove</button>}</td></tr>
          ))}
          {(d?.categoryTolerances ?? []).length === 0 && <tr><td style={TD} colSpan={3}>No category has its own tolerance.</td></tr>}
        </tbody>
      </table>
      {isAdmin && (
        <div style={{ display: "flex", gap: 8, alignItems: "flex-end", marginTop: 8, flexWrap: "wrap" }}>
          <Field label="Category"><select style={inp} aria-label="Tolerance category" value={cat.id} onChange={e => setCat({ ...cat, id: e.target.value })}>
            <option value="">Choose…</option>{categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>
          <Field label="Tolerance (%)"><input style={inp} aria-label="Category tolerance" value={cat.pct} onChange={e => setCat({ ...cat, pct: e.target.value })} /></Field>
          <button style={filterPill(true)} onClick={submitTol}>Set tolerance</button>
        </div>
      )}

      <h3 style={{ marginTop: 28 }}>VAT recoverability by supplier</h3>
      <p style={{ fontSize: 13, opacity: 0.8 }}>A supplier's own percentage replaces the company's ({d?.companyVatRecoverablePct ?? 100} %), for example 0 % for a supplier that is not VAT-registered. Existing commitments for that supplier are restated.</p>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead><tr>{["Supplier", "Recoverable", "Note", ""].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
        <tbody>
          {(d?.supplierVat ?? []).map(s => (
            <tr key={s.supplierId}><td style={TD}>{s.supplierName}</td><td style={TD}>{s.vatRecoverablePct} %</td><td style={TD}>{s.note ?? ""}</td>
              <td style={TD}>{isAdmin && <button style={filterPill(false)} onClick={() => clrVat.mutate(s.supplierId)}>Remove</button>}</td></tr>
          ))}
          {(d?.supplierVat ?? []).length === 0 && <tr><td style={TD} colSpan={4}>Every supplier uses the company percentage.</td></tr>}
        </tbody>
      </table>
      {isAdmin && (
        <div style={{ display: "flex", gap: 8, alignItems: "flex-end", marginTop: 8, flexWrap: "wrap" }}>
          <Field label="Supplier"><select style={inp} aria-label="VAT supplier" value={sup.id} onChange={e => setSup({ ...sup, id: e.target.value })}>
            <option value="">Choose…</option>{suppliers.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></Field>
          <Field label="Recoverable (%)"><input style={inp} aria-label="Supplier recoverable VAT" value={sup.pct} onChange={e => setSup({ ...sup, pct: e.target.value })} /></Field>
          <Field label="Note"><input style={inp} aria-label="Supplier VAT note" value={sup.note} onChange={e => setSup({ ...sup, note: e.target.value })} /></Field>
          <button style={filterPill(true)} onClick={submitVat}>Set VAT</button>
        </div>
      )}

      <h3 style={{ marginTop: 28 }}>Exchange rates</h3>
      <p style={{ fontSize: 13, opacity: 0.8 }}>1 unit of the currency = this many {base}. A purchase order or requisition uses the latest rate on or before its date. A foreign-currency purchase order with no rate cannot be approved against a budget.</p>
      {(d?.currenciesWithoutRate ?? []).length > 0 && <Banner variant="warning">No rate yet for: {(d?.currenciesWithoutRate ?? []).join(", ")}. Their commitments cannot be measured.</Banner>}
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead><tr>{["Currency", `Rate (${base})`, "From", "Entered by", ""].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
        <tbody>
          {(d?.exchangeRates ?? []).map(r => (
            <tr key={r.id}><td style={TD}>{r.currency}</td><td style={TD}>{r.rate}</td><td style={TD}>{fmtDate(r.effectiveFrom)}</td><td style={TD}>{r.createdByName ?? "—"}</td>
              <td style={TD}>{isAdmin && <button style={filterPill(false)} onClick={() => delRate.mutate(r.id)}>Delete</button>}</td></tr>
          ))}
          {(d?.exchangeRates ?? []).length === 0 && <tr><td style={TD} colSpan={5}>No exchange rates yet.</td></tr>}
        </tbody>
      </table>
      {isAdmin && (
        <div style={{ display: "flex", gap: 8, alignItems: "flex-end", marginTop: 8, flexWrap: "wrap" }}>
          <Field label="Currency"><input style={inp} aria-label="Rate currency" maxLength={3} value={rate.currency} onChange={e => setRate({ ...rate, currency: e.target.value.toUpperCase() })} /></Field>
          <Field label={`Rate (${base})`}><input style={inp} aria-label="Exchange rate" value={rate.rate} onChange={e => setRate({ ...rate, rate: e.target.value })} /></Field>
          <Field label="Applies from"><input style={inp} type="date" aria-label="Rate date" value={rate.effectiveFrom} onChange={e => setRate({ ...rate, effectiveFrom: e.target.value })} /></Field>
          <button style={filterPill(true)} onClick={submitRate}>Save rate</button>
        </div>
      )}
    </div>
  )
}

interface Savings {
  baseCurrency: string; from: string; to: string; listValue: number; paidValue: number; saving: number; leakage: number
  lines: number; linesWithoutListPrice: number; linesWithoutRate: number
  byItem: { catalogueItemId: string; itemName: string; qty: number; listValue: number; paidValue: number; saving: number; leakage: number; lines: number; contractLines: number }[]
}

export function ContractSavings() {
  const [range, setRange] = useState({ from: "", to: "" })
  const [run, setRun] = useState<{ from: string; to: string } | null>(null)
  const q = useQuery({
    queryKey: ["scm-contract-savings", run], enabled: run !== null,
    queryFn: () => apiClient.get(`${BASE}/savings/contracts`, { params: { from: run!.from || undefined, to: run!.to || undefined } }).then(r => asOne<Savings>(r)),
  })
  const s = q.data
  const pct = s ? savingPct(s) : null
  return (
    <div style={{ marginTop: 28 }}>
      <h3>Savings against list price</h3>
      <p style={{ fontSize: 13, opacity: 0.8 }}>What approved purchase orders paid compared with each catalogue item's list price, and where an order cost more than an active contract allowed. Leave the dates empty for the last year.</p>
      <div style={{ display: "flex", gap: 8, alignItems: "flex-end", flexWrap: "wrap" }}>
        <Field label="From"><input style={inp} type="date" aria-label="Savings from" value={range.from} onChange={e => setRange({ ...range, from: e.target.value })} /></Field>
        <Field label="To"><input style={inp} type="date" aria-label="Savings to" value={range.to} onChange={e => setRange({ ...range, to: e.target.value })} /></Field>
        <button style={filterPill(true)} onClick={() => setRun({ ...range })}>Run report</button>
      </div>
      {q.isFetching && <Spinner />}
      {q.isError && <ErrBox msg={errorText(q.error)} />}
      {s && !q.isFetching && (
        <>
          <p style={{ marginTop: 12 }}>
            <strong>Saved {fmtR(s.saving)}</strong>{pct !== null ? ` (${pct} % of list value)` : ""} · Contract leakage <strong>{fmtR(s.leakage)}</strong> · {s.lines} line{s.lines === 1 ? "" : "s"} in {s.baseCurrency}
          </p>
          {(s.linesWithoutListPrice > 0 || s.linesWithoutRate > 0) && (
            <Banner variant="warning">
              {s.linesWithoutListPrice > 0 && `${s.linesWithoutListPrice} line(s) left out: the item has no list price. `}
              {s.linesWithoutRate > 0 && `${s.linesWithoutRate} line(s) left out: no exchange rate for their currency.`}
            </Banner>
          )}
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr>{["Item", "Qty", "List value", "Paid", "Saved", "Leakage", "Under contract"].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
            <tbody>
              {s.byItem.map(i => (
                <tr key={i.catalogueItemId}><td style={TD}>{i.itemName}</td><td style={TD}>{i.qty}</td><td style={TD}>{fmtR(i.listValue)}</td><td style={TD}>{fmtR(i.paidValue)}</td>
                  <td style={TD}>{fmtR(i.saving)}</td><td style={TD}>{fmtR(i.leakage)}</td><td style={TD}>{i.contractLines} of {i.lines}</td></tr>
              ))}
              {s.byItem.length === 0 && <tr><td style={TD} colSpan={7}>No catalogue-linked purchase order lines in this period.</td></tr>}
            </tbody>
          </table>
        </>
      )}
    </div>
  )
}
