// src/pages/catalogue/PricingModal.tsx — one item's prices on every list, its minimum price, open changes and price history.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ErrorBanner, Field, Modal } from "./CatalogueKit"
import { cancelBtn, inp, inpErr, linkBtn, primaryBtn, sel } from "./catalogue.styles"
import {
  EMPTY_PRICE_FORM, PRICE_STATUS_LABEL, changeText, errorMessage, marginText, minPriceBody, minPriceProblem, priceBody, priceProblems, rand,
  type CatalogueItem, type ItemPricing, type ListPrice, type PriceChange, type PriceForm,
} from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.05em", borderBottom: "1px solid var(--hf-border-subtle)" }
const td: React.CSSProperties = { padding: "9px 10px", fontSize: 13, color: "var(--hf-text)", borderBottom: "1px solid var(--hf-border-subtle)", verticalAlign: "top" }

export default function PricingModal({ item, canWrite, onClose, onChanged }: { item: CatalogueItem; canWrite: boolean; onClose: () => void; onChanged: () => void }) {
  const qc = useQueryClient()
  const key = ["catalogue", "pricing", item.id]
  const pricing = useQuery<ItemPricing>({ queryKey: key, queryFn: async () => inner<ItemPricing>(await apiClient.get(`/api/v1/catalogue/items/${item.id}/pricing`)) })
  const history = useQuery<PriceChange[]>({ queryKey: ["catalogue", "price-history", item.id], queryFn: async () => inner<PriceChange[]>(await apiClient.get(`/api/v1/catalogue/items/${item.id}/price-changes`)) ?? [] })
  const [changing, setChanging] = useState<ListPrice | null>(null)
  const refresh = () => { qc.invalidateQueries({ queryKey: key }); qc.invalidateQueries({ queryKey: ["catalogue", "price-history", item.id] }); qc.invalidateQueries({ queryKey: ["catalogue", "price-queue"] }); onChanged() }
  const p = pricing.data

  return (
    <Modal title={`Pricing — ${item.name}`} onClose={onClose}>
      {pricing.isLoading && <p>Loading…</p>}
      {pricing.isError && <ErrorBanner message={errorMessage(pricing.error, "Couldn't load the prices.")} />}
      {p && (
        <>
          <p style={{ margin: "0 0 12px", fontSize: 13, color: "var(--hf-text-muted)" }}>
            Prices are per {p.unit}. {p.approvalRequired ? "Changes wait for someone else to approve them." : "Changes take effect on their date without approval."}
          </p>
          <div style={{ overflowX: "auto", border: "1px solid var(--hf-border-subtle)", borderRadius: 10, marginBottom: 14 }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>List</th><th style={th}>Price</th><th style={th}>Incl. VAT</th><th style={th}>Margin</th><th style={th} /></tr></thead>
              <tbody>
                {p.lists.map(l => (
                  <tr key={l.priceListId}>
                    <td style={td}><strong>{l.name}</strong>{l.defaultList && <span style={{ color: "var(--hf-text-faint)" }}> · default</span>}
                      {l.open.map(c => (
                        <div key={c.id} style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>
                          {PRICE_STATUS_LABEL[c.status]}: {rand(c.newPrice)} from {c.effectiveFrom}
                        </div>
                      ))}
                    </td>
                    <td style={td}>{rand(l.price)}</td>
                    <td style={td}>{rand(l.priceInclVat)}</td>
                    <td style={td}>{marginText(l.marginPercent)}</td>
                    <td style={{ ...td, textAlign: "right" }}>
                      {canWrite && <button type="button" style={linkBtn} aria-label={`Change price on ${l.name}`} onClick={() => setChanging(l)}>Change</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "4px 14px", margin: "0 0 14px", fontSize: 13 }}>
            <dt style={{ color: "var(--hf-text-muted)" }}>Cost</dt><dd style={{ margin: 0 }}>{rand(p.costPrice)}</dd>
            <dt style={{ color: "var(--hf-text-muted)" }}>VAT</dt><dd style={{ margin: 0 }}>{p.vatExempt ? "Exempt" : `${p.vatRate}%`}</dd>
          </dl>
          <MinPrice pricing={p} canWrite={canWrite} onSaved={refresh} />
          <h4 style={{ margin: "18px 0 8px", fontSize: 13 }}>Price history</h4>
          {history.isLoading && <p>Loading…</p>}
          {history.data && history.data.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No price changes recorded yet.</p>}
          {history.data && history.data.length > 0 && (
            <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
              {history.data.map(c => (
                <li key={c.id} style={{ padding: "8px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
                    <strong>{c.priceListName ?? "List"}: {changeText(c)}</strong>
                    <span style={{ color: "var(--hf-text-faint)", whiteSpace: "nowrap" }}>{PRICE_STATUS_LABEL[c.status]}</span>
                  </div>
                  <div style={{ color: "var(--hf-text-muted)" }}>From {c.effectiveFrom}{c.reason ? ` · ${c.reason}` : ""}{c.decisionNote ? ` · ${c.decisionNote}` : ""}</div>
                </li>
              ))}
            </ul>
          )}
          {changing && <ChangePrice item={item} pricing={p} list={changing} onClose={() => setChanging(null)} onDone={() => { setChanging(null); refresh() }} />}
        </>
      )}
    </Modal>
  )
}

function MinPrice({ pricing, canWrite, onSaved }: { pricing: ItemPricing; canWrite: boolean; onSaved: () => void }) {
  const [value, setValue] = useState(pricing.minPrice == null ? "" : String(pricing.minPrice))
  const [error, setError] = useState("")
  const current = pricing.lists.find(l => l.defaultList)?.price ?? null
  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/catalogue/items/${pricing.itemId}/min-price`, minPriceBody(value)),
    onSuccess: onSaved,
    onError: e => setError(errorMessage(e, "Couldn't save the minimum price.")),
  })
  const submit = () => { const m = minPriceProblem(value, current); setError(m ?? ""); if (!m) save.mutate() }
  if (!canWrite) return <p style={{ fontSize: 13, margin: 0 }}><span style={{ color: "var(--hf-text-muted)" }}>Minimum price</span> {rand(pricing.minPrice)}</p>
  return (
    <Field label="Minimum price (R)" error={error} id="pr-min">
      <div style={{ display: "flex", gap: 8 }}>
        <input id="pr-min" style={error ? inpErr : inp} value={value} onChange={e => setValue(e.target.value)} placeholder="No minimum" inputMode="decimal" />
        <button type="button" style={cancelBtn} disabled={save.isPending} onClick={submit}>Save</button>
      </div>
    </Field>
  )
}

function ChangePrice({ item, pricing, list, onClose, onDone }: { item: CatalogueItem; pricing: ItemPricing; list: ListPrice; onClose: () => void; onDone: () => void }) {
  const [form, setForm] = useState<PriceForm>({ ...EMPTY_PRICE_FORM, priceListId: list.priceListId })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState("")
  const set = <K extends keyof PriceForm>(k: K, v: PriceForm[K]) => setForm(f => ({ ...f, [k]: v }))
  const target = pricing.lists.find(l => l.priceListId === form.priceListId) ?? list
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/catalogue/items/${item.id}/price-changes`, priceBody(form)),
    onSuccess: onDone,
    onError: e => setGeneral(errorMessage(e, "Couldn't record the price change.")),
  })
  const submit = () => {
    const errs = priceProblems(form, { minPrice: pricing.minPrice, current: target.price, approvalRequired: pricing.approvalRequired })
    setErrors(errs); setGeneral("")
    if (Object.keys(errs).length === 0) save.mutate()
  }
  return (
    <Modal title={`Change price — ${target.name}`} onClose={onClose}>
      <p style={{ margin: "0 0 12px", fontSize: 13, color: "var(--hf-text-muted)" }}>Now {rand(target.price)}{pricing.minPrice != null ? ` · minimum ${rand(pricing.minPrice)}` : ""}</p>
      <Field label="Price list" error={errors.priceListId} id="pc-list">
        <select id="pc-list" style={sel} value={form.priceListId} onChange={e => set("priceListId", e.target.value)}>
          {pricing.lists.map(l => <option key={l.priceListId} value={l.priceListId}>{l.name}</option>)}
        </select>
      </Field>
      <Field label="New price (R)" required error={errors.newPrice} id="pc-price">
        <input id="pc-price" style={errors.newPrice ? inpErr : inp} value={form.newPrice} onChange={e => set("newPrice", e.target.value)} inputMode="decimal" placeholder="0.00" autoFocus />
      </Field>
      <Field label="Effective from (optional)" error={errors.effectiveFrom} id="pc-date">
        <input id="pc-date" type="date" style={errors.effectiveFrom ? inpErr : inp} value={form.effectiveFrom} onChange={e => set("effectiveFrom", e.target.value)} />
      </Field>
      <Field label={pricing.approvalRequired ? "Reason" : "Reason (optional)"} required={pricing.approvalRequired} error={errors.reason} id="pc-reason">
        <input id="pc-reason" style={errors.reason ? inpErr : inp} value={form.reason} onChange={e => set("reason", e.target.value)} placeholder="e.g. Supplier increase" />
      </Field>
      {general && <ErrorBanner message={general} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
        <button type="button" style={cancelBtn} onClick={onClose}>Cancel</button>
        <button type="button" style={primaryBtn} disabled={save.isPending} onClick={submit}>
          {save.isPending ? "Saving…" : pricing.approvalRequired ? "Submit for approval" : "Change price"}
        </button>
      </div>
    </Modal>
  )
}
