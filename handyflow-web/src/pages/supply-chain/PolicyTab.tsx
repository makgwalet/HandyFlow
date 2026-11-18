// src/pages/supply-chain/PolicyTab.tsx
// Procurement financial policy (budget VAT basis, price-increase tolerance) and the price increases waiting for a second person.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { inp, TH, TD, Field, ErrBox, Spinner, EmptyState, Banner, filterPill, fmtR, fmtDate } from "./scm.shared"
import { Scale } from "lucide-react"
import { PolicyRefinements, ContractSavings } from "./PolicyRefinements"
import { asList, asOne, errorText, BUDGET_BASES, policyProblem, policyBody, type PolicyForm } from "./scmOps"

interface PolicyView { effective: { budgetBasis: string; vatRecoverablePct: number; includeNonRecoverableVat: boolean; priceIncreaseTolerancePct: number; baseCurrency?: string }; customised: boolean }
interface Override { id: string; requisitionNumber: string; orderNumber: string; itemName: string; quantity: number; requisitionUnitCost: number; unitCost: number; reason: string | null; note: string | null; quotationRef: string | null; convertedByName: string | null; createdAt: string }

const BASE = "/api/v1/supply-chain"

export function PolicyTab() {
  const qc = useQueryClient()
  const isAdmin = usePermissions(["SCM_ADMIN"])
  const canDecide = usePermissions(["SCM_PRICE_OVERRIDE_APPROVE", "SCM_ADMIN"])
  const [form, setForm] = useState<PolicyForm | null>(null)
  const [err, setErr] = useState("")
  const [saved, setSaved] = useState(false)
  const [decideErr, setDecideErr] = useState("")
  const [note, setNote] = useState<Record<string, string>>({})

  const policy = useQuery({ queryKey: ["scm-procurement-policy"], queryFn: () => apiClient.get(`${BASE}/procurement-policy`).then(r => asOne<PolicyView>(r)) })
  const pending = useQuery({
    queryKey: ["scm-price-overrides"], enabled: canDecide,
    queryFn: () => apiClient.get(`${BASE}/requisitions/price-overrides`).then(asList<Override>),
  })

  if (!form && policy.data) {
    const e = policy.data.effective
    setForm({ budgetBasis: e.budgetBasis, vatRecoverablePct: String(e.vatRecoverablePct), includeNonRecoverableVat: e.includeNonRecoverableVat, priceIncreaseTolerancePct: String(e.priceIncreaseTolerancePct), baseCurrency: e.baseCurrency ?? "ZAR" })
  }

  const save = useMutation({
    mutationFn: () => apiClient.put(`${BASE}/procurement-policy`, policyBody(form!)),
    onSuccess: () => { setSaved(true); setErr(""); qc.invalidateQueries({ queryKey: ["scm-procurement-policy"] }) },
    onError: (e) => { setSaved(false); setErr(errorText(e)) },
  })

  const decide = useMutation({
    mutationFn: ({ id, approve }: { id: string; approve: boolean }) =>
      apiClient.post(`${BASE}/requisitions/price-overrides/${id}/decision`, { approve, note: note[id] ?? "" }),
    onSuccess: () => { setDecideErr(""); qc.invalidateQueries({ queryKey: ["scm-price-overrides"] }) },
    onError: (e) => setDecideErr(errorText(e)),
  })

  const submit = () => {
    const p = form ? policyProblem(form) : null
    if (p) { setErr(p); return }
    setErr(""); save.mutate()
  }
  const basis = BUDGET_BASES.find(b => b.value === form?.budgetBasis)

  return (
    <div style={{ padding: 20, maxWidth: 900 }}>
      <h3 style={{ marginTop: 0 }}>Procurement policy</h3>
      {policy.isError ? <ErrBox msg={errorText(policy.error, "Could not load the policy. The server may need restarting after an update.")} /> : policy.isLoading || !form ? <Spinner /> : (
        <>
          {err && <ErrBox msg={err} />}
          {saved && <Banner variant="info">Saved. If the basis, VAT or base currency changed, existing budget commitments were restated and the change is on the audit trail.</Banner>}
          <Field label="Budgets count">
            <select style={inp} aria-label="Budget basis" value={form.budgetBasis} disabled={!isAdmin} onChange={e => setForm({ ...form, budgetBasis: e.target.value })}>
              {BUDGET_BASES.map(b => <option key={b.value} value={b.value}>{b.label}</option>)}
            </select>
          </Field>
          {basis && <p style={{ fontSize: 13, opacity: 0.8 }}>{basis.help}</p>}
          <Field label="VAT the business can claim back (%)">
            <input style={inp} aria-label="Recoverable VAT" value={form.vatRecoverablePct} disabled={!isAdmin} onChange={e => setForm({ ...form, vatRecoverablePct: e.target.value })} />
          </Field>
          <label style={{ display: "flex", gap: 8, fontSize: 13, margin: "8px 0" }}>
            <input type="checkbox" checked={form.includeNonRecoverableVat} disabled={!isAdmin} onChange={e => setForm({ ...form, includeNonRecoverableVat: e.target.checked })} />
            Include non-recoverable VAT in budget consumption
          </label>
          <Field label="Buyer price increase allowed without approval (%)">
            <input style={inp} aria-label="Price tolerance" value={form.priceIncreaseTolerancePct} disabled={!isAdmin} onChange={e => setForm({ ...form, priceIncreaseTolerancePct: e.target.value })} />
          </Field>
          <Field label="Base currency (budgets are measured in this)">
            <input style={inp} aria-label="Base currency" maxLength={3} value={form.baseCurrency ?? ""} disabled={!isAdmin} onChange={e => setForm({ ...form, baseCurrency: e.target.value.toUpperCase() })} />
          </Field>
          {isAdmin ? <button style={filterPill(true)} disabled={save.isPending} onClick={submit}>Save policy</button>
            : <p style={{ fontSize: 13 }}>Only a Supply Chain administrator can change the policy.</p>}
        </>
      )}

      <PolicyRefinements isAdmin={isAdmin} />
      <ContractSavings />

      {canDecide && (
        <>
          <h3 style={{ marginTop: 28 }}>Price increases waiting for approval</h3>
          {decideErr && <ErrBox msg={decideErr} />}
          {pending.isLoading ? <Spinner /> : (pending.data ?? []).length === 0 ? (
            <EmptyState icon={Scale} title="Nothing waiting" sub="A buyer's price increase beyond the tolerance shows here until someone else decides it." />
          ) : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr>{["Order", "Item", "Estimate", "Price", "Reason", "Buyer", "Raised", ""].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
              <tbody>
                {(pending.data ?? []).map(o => (
                  <tr key={o.id}>
                    <td style={TD}>{o.orderNumber}<br /><small>{o.requisitionNumber}</small></td>
                    <td style={TD}>{o.itemName} × {o.quantity}</td>
                    <td style={TD}>{fmtR(o.requisitionUnitCost)}</td>
                    <td style={TD}>{fmtR(o.unitCost)}</td>
                    <td style={TD}>{(o.reason ?? "").replace(/_/g, " ")}{o.note ? ` — ${o.note}` : ""}{o.quotationRef ? ` (${o.quotationRef})` : ""}</td>
                    <td style={TD}>{o.convertedByName ?? "—"}</td>
                    <td style={TD}>{fmtDate(o.createdAt)}</td>
                    <td style={TD}>
                      <input style={{ ...inp, marginBottom: 6 }} placeholder="Note (required to reject)" aria-label={`Decision note ${o.orderNumber}`}
                        value={note[o.id] ?? ""} onChange={e => setNote({ ...note, [o.id]: e.target.value })} />
                      <button style={{ ...filterPill(true), marginRight: 6 }} disabled={decide.isPending} onClick={() => decide.mutate({ id: o.id, approve: true })}>Approve</button>
                      <button style={filterPill(false)} disabled={decide.isPending} onClick={() => decide.mutate({ id: o.id, approve: false })}>Reject</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  )
}
