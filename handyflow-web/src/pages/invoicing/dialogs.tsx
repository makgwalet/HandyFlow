// src/pages/invoicing/dialogs.tsx
//
// The four forms that act on an invoice or schedule: record a payment, log retainer hours, issue a credit note and log
// the hours for a variable-hours cycle. They are used from both the lists and the detail pages, so each one runs its own
// request and tells the caller when it is done. Every form is checked before it is sent.
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { AlertTriangle } from "lucide-react"
import { apiClient } from "../../api/client"
import {
  balanceOf, billableHours, creditNoteError, creditNoteTotals, cycleHoursError, fmtR, hoursError, num, paymentError, retainerUse, wouldExceed,
  type Invoice, type RecurringSchedule,
} from "./billing.logic"
import { apiMessage } from "./queries"
import { Btn, Dialog, Field, Notice, fieldStyle } from "./ui"

const refreshInvoices = (qc: ReturnType<typeof useQueryClient>, id?: string) => {
  qc.invalidateQueries({ queryKey: ["invoices"] })
  if (id) { qc.invalidateQueries({ queryKey: ["invoice", id] }); qc.invalidateQueries({ queryKey: ["credit-notes", id] }) }
  qc.invalidateQueries({ queryKey: ["credit-notes-all"] })
}

export function RecordPaymentDialog({ invoice, onClose, onDone }: { invoice: Invoice; onClose: () => void; onDone?: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState({ amount: String(balanceOf(invoice)), method: "EFT", reference: "" })
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/invoicing/invoices/${invoice.id}/payments`, {
      amountPaid: Number(f.amount), paymentMethod: f.method, reference: f.reference.trim() || undefined }),
    onSuccess: () => { refreshInvoices(qc, invoice.id); onDone?.(); onClose() },
    onError: (e: any) => setErr(apiMessage(e, "The payment could not be recorded.")),
  })
  const submit = () => { const m = paymentError(f.amount, invoice); if (m) setErr(m); else { setErr(""); save.mutate() } }
  return (
    <Dialog title="Record payment" onClose={onClose}
      subtitle={`${invoice.invoiceNumber} · total ${fmtR(invoice.total)} · still owing ${fmtR(balanceOf(invoice))}`}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn variant="success" onClick={submit} disabled={save.isPending}>{save.isPending ? "Saving..." : "Record payment"}</Btn></>}>
      <Field label="Amount received (R)">{id => <input id={id} type="number" min="0.01" step="0.01" value={f.amount} onChange={e => setF({ ...f, amount: e.target.value })} style={fieldStyle} />}</Field>
      <Field label="Payment method">{id => (
        <select id={id} value={f.method} onChange={e => setF({ ...f, method: e.target.value })} style={fieldStyle}>
          {["EFT", "CASH", "CARD", "CHEQUE", "OTHER"].map(m => <option key={m}>{m}</option>)}
        </select>)}
      </Field>
      <Field label="Reference (optional)" hint="For example the EFT reference from the bank statement.">{id => <input id={id} value={f.reference} onChange={e => setF({ ...f, reference: e.target.value })} style={fieldStyle} />}</Field>
      {err && <Notice tone="bad">{err}</Notice>}
    </Dialog>
  )
}

export function LogHoursDialog({ invoice, onClose, onDone }: { invoice: Invoice; onClose: () => void; onDone?: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState({ hours: "", note: "" })
  const [err, setErr] = useState("")
  const use = retainerUse(invoice)
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/invoicing/invoices/${invoice.id}/hours`, { hours: Number(f.hours), note: f.note.trim() || undefined }),
    onSuccess: () => { refreshInvoices(qc, invoice.id); onDone?.(); onClose() },
    onError: (e: any) => setErr(apiMessage(e, "The hours could not be logged.")),
  })
  const submit = () => { const m = hoursError(f.hours); if (m) setErr(m); else { setErr(""); save.mutate() } }
  return (
    <Dialog title="Log hours" onClose={onClose}
      subtitle={`${invoice.invoiceNumber} · ${use?.consumed ?? 0}h used of ${use?.committed ?? 0}h committed`}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn variant="primary" onClick={submit} disabled={save.isPending}>{save.isPending ? "Saving..." : "Log hours"}</Btn></>}>
      <Field label="Hours worked" hint={`Remaining commitment: ${use?.remaining ?? 0}h`}>{id => <input id={id} type="number" min="0.01" step="0.25" value={f.hours} onChange={e => setF({ ...f, hours: e.target.value })} style={fieldStyle} />}</Field>
      <Field label="Note (optional)">{id => <input id={id} value={f.note} onChange={e => setF({ ...f, note: e.target.value })} style={fieldStyle} placeholder="e.g. Dozer on site 07:00 to 13:00" />}</Field>
      {wouldExceed(f.hours, invoice) && <Notice tone="warn"><AlertTriangle size={14} aria-hidden="true" /> This takes the retainer past its committed hours, so it will go into overage.</Notice>}
      {err && <Notice tone="bad">{err}</Notice>}
    </Dialog>
  )
}

export function CreditNoteDialog({ invoice, onClose, onDone }: { invoice: Invoice; onClose: () => void; onDone?: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState({ reason: "", description: "", amount: "", vatRate: "15" })
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/invoicing/invoices/${invoice.id}/credit-notes`, {
      reason: f.reason.trim(), description: f.description.trim() || undefined, amount: Number(f.amount), vatRate: Number(f.vatRate || 15) }),
    onSuccess: () => { refreshInvoices(qc, invoice.id); onDone?.(); onClose() },
    onError: (e: any) => setErr(apiMessage(e, "The credit note could not be issued.")),
  })
  const t = creditNoteTotals(f.amount, f.vatRate)
  const submit = () => { const m = creditNoteError(f.reason, f.amount, f.vatRate); if (m) setErr(m); else { setErr(""); save.mutate() } }
  return (
    <Dialog title="Issue credit note" onClose={onClose} subtitle={`${invoice.invoiceNumber} · invoice total ${fmtR(invoice.total)}`}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn variant="danger" onClick={submit} disabled={save.isPending}>{save.isPending ? "Issuing..." : "Issue credit note"}</Btn></>}>
      <Field label="Reason">{id => <input id={id} value={f.reason} onChange={e => setF({ ...f, reason: e.target.value })} style={fieldStyle} placeholder="e.g. Overcharged for materials" />}</Field>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <Field label="Amount (R, excluding VAT)">{id => <input id={id} type="number" min="0.01" step="0.01" value={f.amount} onChange={e => setF({ ...f, amount: e.target.value })} style={fieldStyle} />}</Field>
        <Field label="VAT rate (%)">{id => <input id={id} type="number" min="0" max="100" step="0.5" value={f.vatRate} onChange={e => setF({ ...f, vatRate: e.target.value })} style={fieldStyle} />}</Field>
      </div>
      <Field label="Description (optional)">{id => <input id={id} value={f.description} onChange={e => setF({ ...f, description: e.target.value })} style={fieldStyle} />}</Field>
      {t.subtotal > 0 && <Notice tone="info">Credit of {fmtR(t.subtotal)} plus {fmtR(t.vat)} VAT: {fmtR(t.total)} in total.</Notice>}
      {err && <Notice tone="bad">{err}</Notice>}
    </Dialog>
  )
}

export function CycleHoursDialog({ schedule, onClose, onDone }: { schedule: RecurringSchedule; onClose: () => void; onDone?: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState({ actualHours: "", periodLabel: "", operatorNotes: "" })
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/invoicing/recurring-schedules/${schedule.id}/log-cycle-hours`, {
      actualHours: Number(f.actualHours), periodLabel: f.periodLabel.trim(), operatorNotes: f.operatorNotes.trim() || undefined }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["recurring-schedules"] }); qc.invalidateQueries({ queryKey: ["recurring", schedule.id] })
      qc.invalidateQueries({ queryKey: ["invoices"] }); onDone?.(); onClose()
    },
    onError: (e: any) => setErr(apiMessage(e, "The hours could not be logged.")),
  })
  const min = num(schedule.minimumHoursPerCycle)
  const billed = billableHours(f.actualHours, schedule.minimumHoursPerCycle)
  const submit = () => { const m = cycleHoursError(f.actualHours, f.periodLabel); if (m) setErr(m); else { setErr(""); save.mutate() } }
  return (
    <Dialog title="Log cycle hours" onClose={onClose} subtitle={`${schedule.title} · minimum ${min}h a cycle at ${fmtR(schedule.ratePerHour)} an hour`}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn variant="primary" onClick={submit} disabled={save.isPending}>{save.isPending ? "Saving..." : "Log hours and invoice"}</Btn></>}>
      <Field label="Actual hours worked" hint={`At least ${min}h is billed whatever was worked.`}>{id => <input id={id} type="number" min="0" step="0.25" value={f.actualHours} onChange={e => setF({ ...f, actualHours: e.target.value })} style={fieldStyle} />}</Field>
      <Field label="Period">{id => <input id={id} value={f.periodLabel} onChange={e => setF({ ...f, periodLabel: e.target.value })} style={fieldStyle} placeholder="e.g. June 2026" />}</Field>
      <Field label="Notes (optional)">{id => <input id={id} value={f.operatorNotes} onChange={e => setF({ ...f, operatorNotes: e.target.value })} style={fieldStyle} placeholder="e.g. Down 3 days for a scheduled service" />}</Field>
      {f.actualHours.trim() !== "" && Number(f.actualHours) < min && <Notice tone="warn"><AlertTriangle size={14} aria-hidden="true" /> Below the minimum, so the invoice bills {billed}h, not {f.actualHours}h.</Notice>}
      {f.actualHours.trim() !== "" && <Notice tone="info">This creates an issued invoice for {billed}h at {fmtR(schedule.ratePerHour)}.</Notice>}
      {err && <Notice tone="bad">{err}</Notice>}
    </Dialog>
  )
}
