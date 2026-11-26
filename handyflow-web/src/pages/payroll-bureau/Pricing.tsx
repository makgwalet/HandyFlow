// Per-client pricing, extra charges, fee-note cancellation and the profitability report.
import { useEffect, useState } from "react"
import { payrollBureauApi } from "../../api/payrollBureau.api"
import type { PayClient, PayFeeNote, PayPricing, Profitability } from "../../types/payrollBureau.types"
import { Table, Empty, Modal, Field, ErrorBox, ModalActions } from "./ui"
import { fmtR, MUTED, FAINT, INK, inputStyle, btnPrimary, btnSecondary, rowStyle, cellStyle } from "./uiStyles"

const message = (e: unknown, fallback: string) =>
  (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? fallback

const BILLING_LABEL: Record<string, string> = { PER_RUN: "Every pay run", MONTHLY: "Once a month", ANNUAL: "Once a year" }

export function PricingSection({ client, onChanged }: { client: PayClient; onChanged?: () => void }) {
  const [pricing, setPricing] = useState<PayPricing | null>(null)
  const [fee, setFee] = useState("")
  const [minimum, setMinimum] = useState("")
  const [desc, setDesc] = useState("")
  const [amount, setAmount] = useState("")
  const [billing, setBilling] = useState("MONTHLY")
  const [error, setError] = useState("")
  const [saving, setSaving] = useState(false)

  const load = () => payrollBureauApi.getPricing(client.id).then(p => {
    setPricing(p); setFee(String(p.perEmployeeFee)); setMinimum(String(p.minimumFee))
  }).catch(e => setError(message(e, "Could not load pricing")))
  useEffect(() => { load() }, [client.id]) // eslint-disable-line react-hooks/exhaustive-deps

  const savePricing = async () => {
    setError(""); setSaving(true)
    try {
      const p = await payrollBureauApi.updatePricing(client.id, { perEmployeeFee: Number(fee), minimumFee: Number(minimum) })
      setPricing(p); onChanged?.()
    } catch (e) { setError(message(e, "Could not save pricing")) } finally { setSaving(false) }
  }
  const addCharge = async () => {
    setError("")
    try {
      await payrollBureauApi.addCharge(client.id, { description: desc, amount: Number(amount), billing })
      setDesc(""); setAmount(""); await load()
    } catch (e) { setError(message(e, "Could not add the charge")) }
  }
  const removeCharge = async (id: string) => {
    setError("")
    try { await payrollBureauApi.archiveCharge(client.id, id); await load() }
    catch (e) { setError(message(e, "Could not remove the charge")) }
  }

  if (!pricing) return error ? <ErrorBox text={error} /> : <Empty text="Loading…" />
  return (
    <div style={{ marginBottom: 20, padding: 14, border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)" }}>
      <div style={{ fontSize: 13, fontWeight: 700, color: INK, marginBottom: 8 }}>Pricing</div>
      {error && <ErrorBox text={error} />}
      <div style={{ display: "flex", gap: 12, alignItems: "flex-end" }}>
        <Field label="Fee per employee, per pay run (R)">
          <input aria-label="Fee per employee" type="number" min="0" step="0.01" value={fee} onChange={e => setFee(e.target.value)} style={inputStyle} />
        </Field>
        <Field label="Minimum fee per pay run (R)">
          <input aria-label="Minimum fee" type="number" min="0" step="0.01" value={minimum} onChange={e => setMinimum(e.target.value)} style={inputStyle} />
        </Field>
        <button onClick={savePricing} disabled={saving} style={{ ...btnPrimary, marginBottom: 12 }}>{saving ? "Saving…" : "Save"}</button>
      </div>
      <p style={{ fontSize: 11.5, color: FAINT, margin: "0 0 10px" }}>
        Each pay run is billed at the per-employee fee for every payslip, or the minimum if that is more. Weekly and fortnightly runs are billed the same way.
      </p>
      <div style={{ fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 6 }}>Extra charges</div>
      {pricing.charges.length === 0 ? <div style={{ fontSize: 12, color: FAINT, marginBottom: 8 }}>None.</div> : (
        <Table headers={["Charge", "Amount", "Billed", ""]}>
          {pricing.charges.map(c => (
            <tr key={c.id} style={rowStyle}>
              <td style={cellStyle}>{c.description}</td>
              <td style={cellStyle}>{fmtR(c.amount)}</td>
              <td style={cellStyle}>{BILLING_LABEL[c.billing] ?? c.billing}</td>
              <td style={cellStyle}><button onClick={() => removeCharge(c.id)} style={btnSecondary}>Remove</button></td>
            </tr>
          ))}
        </Table>
      )}
      <div style={{ display: "flex", gap: 8, alignItems: "flex-end", marginTop: 10 }}>
        <Field label="Description"><input aria-label="Charge description" value={desc} onChange={e => setDesc(e.target.value)} style={inputStyle} /></Field>
        <Field label="Amount (R)"><input aria-label="Charge amount" type="number" min="0" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} style={inputStyle} /></Field>
        <Field label="Billed">
          <select aria-label="Charge billing" value={billing} onChange={e => setBilling(e.target.value)} style={inputStyle}>
            {Object.entries(BILLING_LABEL).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </Field>
        <button onClick={addCharge} style={{ ...btnSecondary, marginBottom: 12 }}>Add</button>
      </div>
    </div>
  )
}

export function CancelFeeNoteModal({ feeNote, onClose, onCancelled }: { feeNote: PayFeeNote; onClose: () => void; onCancelled: () => void }) {
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [saving, setSaving] = useState(false)
  const submit = async () => {
    setError("")
    if (!reason.trim()) { setError("Say why this invoice is being cancelled."); return }
    setSaving(true)
    try { await payrollBureauApi.cancelFeeNote(feeNote.id, reason.trim()); onCancelled() }
    catch (e) { setError(message(e, "Could not cancel the invoice")) } finally { setSaving(false) }
  }
  return (
    <Modal title={`Cancel invoice ${feeNote.invoiceNumber}`} onClose={onClose}>
      <p style={{ fontSize: 12.5, color: MUTED, marginTop: 0 }}>
        This reverses the invoice in the ledger. The pay run can then be billed again. An invoice with payments cannot be cancelled.
      </p>
      {error && <ErrorBox text={error} />}
      <Field label="Reason"><textarea aria-label="Cancellation reason" rows={3} value={reason} onChange={e => setReason(e.target.value)} style={inputStyle} /></Field>
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Cancel invoice" />
    </Modal>
  )
}

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`

export function ProfitabilityModal({ onClose }: { onClose: () => void }) {
  const now = new Date()
  const [from, setFrom] = useState(iso(new Date(now.getFullYear(), now.getMonth(), 1)))
  const [to, setTo] = useState(iso(new Date(now.getFullYear(), now.getMonth() + 1, 0)))
  const [cost, setCost] = useState("")
  const [data, setData] = useState<Profitability | null>(null)
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)

  useEffect(() => { payrollBureauApi.getCostPerPayslip().then(c => setCost(String(c.costPerPayslip))).catch(() => undefined) }, [])

  const run = async () => {
    setError(""); setLoading(true)
    try { setData(await payrollBureauApi.getProfitability(from, to)) }
    catch (e) { setError(message(e, "Could not load the report")) } finally { setLoading(false) }
  }
  const saveCost = async () => {
    setError("")
    try { await payrollBureauApi.setCostPerPayslip(Number(cost)); await run() }
    catch (e) { setError(message(e, "Could not save the cost")) }
  }
  const pct = (v: number | null) => (v == null ? "—" : `${v}%`)

  return (
    <Modal title="Client profitability" onClose={onClose}>
      <div style={{ width: 620, maxWidth: "100%" }}>
        {error && <ErrorBox text={error} />}
        <div style={{ display: "flex", gap: 8, alignItems: "flex-end" }}>
          <Field label="From"><input aria-label="From date" type="date" value={from} onChange={e => setFrom(e.target.value)} style={inputStyle} /></Field>
          <Field label="To"><input aria-label="To date" type="date" value={to} onChange={e => setTo(e.target.value)} style={inputStyle} /></Field>
          <button onClick={run} disabled={loading} style={{ ...btnPrimary, marginBottom: 12 }}>{loading ? "Loading…" : "Show"}</button>
        </div>
        <div style={{ display: "flex", gap: 8, alignItems: "flex-end" }}>
          <Field label="What one payslip costs you (R)"><input aria-label="Cost per payslip" type="number" min="0" step="0.01" value={cost} onChange={e => setCost(e.target.value)} style={inputStyle} /></Field>
          <button onClick={saveCost} style={{ ...btnSecondary, marginBottom: 12 }}>Save cost</button>
        </div>
        <p style={{ fontSize: 11.5, color: FAINT, margin: "0 0 10px" }}>
          Revenue is the invoices dated in this period; cost is the payslips of runs paid in it, so a run paid at month end and invoiced the next month falls in two periods.
        </p>
        {data && (data.clients.length === 0 ? <Empty text="No runs or invoices in this period." /> : (
          <>
            <Table headers={["Client", "Payslips", "Billed", "Cost", "Margin", "%"]}>
              {data.clients.map(c => (
                <tr key={c.clientId} style={rowStyle}>
                  <td style={cellStyle}>{c.clientName}{c.unbilledRuns > 0 && <div style={{ fontSize: 11, color: "var(--hf-warning-text)" }}>{c.unbilledRuns} run{c.unbilledRuns > 1 ? "s" : ""} not billed (about {fmtR(c.unbilledEstimate)})</div>}</td>
                  <td style={cellStyle}>{c.payslips}</td>
                  <td style={cellStyle}>{fmtR(c.revenue)}</td>
                  <td style={cellStyle}>{fmtR(c.cost)}</td>
                  <td style={{ ...cellStyle, color: c.margin < 0 ? "var(--hf-danger-text)" : INK }}>{fmtR(c.margin)}</td>
                  <td style={cellStyle}>{pct(c.marginPercent)}</td>
                </tr>
              ))}
              <tr style={{ ...rowStyle, fontWeight: 700 }}>
                <td style={cellStyle}>Total</td><td style={cellStyle} />
                <td style={cellStyle}>{fmtR(data.totalRevenue)}</td><td style={cellStyle}>{fmtR(data.totalCost)}</td>
                <td style={cellStyle}>{fmtR(data.totalMargin)}</td><td style={cellStyle}>{pct(data.totalMarginPercent)}</td>
              </tr>
            </Table>
            {data.totalUnbilledRuns > 0 && (
              <p style={{ fontSize: 12, color: "var(--hf-warning-text)" }}>
                {data.totalUnbilledRuns} finalised run{data.totalUnbilledRuns > 1 ? "s have" : " has"} not been invoiced yet: about {fmtR(data.totalUnbilledEstimate)} to bill.
              </p>
            )}
          </>
        ))}
        <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 8 }}><button onClick={onClose} style={btnSecondary}>Close</button></div>
      </div>
    </Modal>
  )
}
