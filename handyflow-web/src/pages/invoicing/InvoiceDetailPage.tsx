// src/pages/invoicing/InvoiceDetailPage.tsx  (route: /invoices/:id)
//
// One invoice: who it is for, what it bills, what has been paid and what is owing, retainer hours, credit notes and the
// actions that apply to its status. Works for standard, recurring-instance and retainer invoices.
import { useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"
import { AlertTriangle, CheckCircle, Download, FileMinus, FileText, Receipt, Send, Timer } from "lucide-react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { PageHeader } from "../../components/ui/PageHeader"
import Chip from "../../components/ui/Chip"
import { usePermission } from "../../hooks/usePermission"
import {
  INVOICE_LABEL, INVOICE_TONE, OPEN_INVOICE_STATUSES, TYPE_LABEL, balanceOf, dueLabel, fmtDate, fmtR, label, retainerBarColour, retainerUse, num,
  type CreditNote, type Invoice,
} from "./billing.logic"
import { CreditNoteDialog, LogHoursDialog, RecordPaymentDialog } from "./dialogs"
import { apiMessage, downloadPdf } from "./queries"
import { Btn, Facts, Loading, Meter, Notice, StateBox, Totals, panel, sectionTitle, td } from "./ui"

interface CustomerLite { name: string; email?: string; phone?: string; taxNumber?: string }

export default function InvoiceDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const canCreate = usePermission("INVOICE_CREATE")
  const [dialog, setDialog] = useState<"pay" | "hours" | "credit" | null>(null)
  const [msg, setMsg] = useState("")

  const { data: inv, isLoading, isError } = useQuery<Invoice>({
    queryKey: ["invoice", id], enabled: !!id,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/invoicing/invoices/${id}`); return r.data?.data ?? r.data },
  })
  const { data: customer } = useQuery<CustomerLite>({
    queryKey: ["customer", inv?.customerId], enabled: !!inv?.customerId,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/crm/customers/${inv!.customerId}`); return r.data?.data ?? r.data },
  })
  const { data: creditNotes = [] } = useQuery<CreditNote[]>({
    queryKey: ["credit-notes", id], enabled: !!id && !!inv && inv.status !== "DRAFT",
    queryFn: async () => { const r = await apiClient.get(`/api/v1/invoicing/invoices/${id}/credit-notes`); return r.data?.data ?? r.data ?? [] },
  })
  const issue = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/invoicing/invoices/${id}/issue`),
    onSuccess: () => { setMsg(""); qc.invalidateQueries({ queryKey: ["invoice", id] }); qc.invalidateQueries({ queryKey: ["invoices"] }) },
    onError: (e: any) => setMsg(apiMessage(e, "The invoice could not be issued.")),
  })

  if (isLoading) return <Loading text="Loading invoice..." />
  if (isError || !inv) return <StateBox icon={Receipt} tone="bad" title="Invoice not found" text="It may have been removed, or you may not have access." action={<Btn onClick={() => navigate("/invoices")}>Back to invoices</Btn>} />

  const isWalkin = !inv.customerId
  const name = isWalkin ? inv.walkinClientName : customer?.name
  const email = isWalkin ? inv.walkinClientEmail : customer?.email
  const phone = isWalkin ? inv.walkinClientPhone : customer?.phone
  const open = OPEN_INVOICE_STATUSES.includes(inv.status)
  const balance = balanceOf(inv)
  const paidPct = num(inv.total) > 0 ? Math.min(100, (num(inv.amountPaid) / num(inv.total)) * 100) : 0
  const use = inv.invoiceType === "RETAINER" ? retainerUse(inv) : null
  const due = dueLabel(inv)
  const credited = creditNotes.reduce((s, c) => s + num(c.total), 0)
  const when = (d: string | null) => d ? fmtDate(d) : undefined
  const timeline: { label: string; at?: string; done: boolean }[] = [
    { label: "Created", at: when(inv.createdAt), done: true },
    { label: "Issued", at: when(inv.issuedAt), done: !!inv.issuedAt },
    { label: inv.status === "PAID" ? "Paid in full" : "Payment received", at: undefined, done: num(inv.amountPaid) > 0 },
    { label: "Due", at: when(inv.dueDate), done: inv.status === "PAID" },
  ]

  return (
    <div style={{ maxWidth: 1000 }}>
      <PageHeader
        breadcrumbs={[{ label: "Invoices", to: "/invoices" }, { label: inv.invoiceNumber }]} icon={Receipt}
        title={inv.invoiceNumber}
        subtitle={<span style={{ display: "inline-flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
          <Chip tone={INVOICE_TONE[inv.status] ?? "neutral"}>{label(INVOICE_LABEL, inv.status)}</Chip>
          {inv.invoiceType !== "STANDARD" && <Chip tone={inv.invoiceType === "RETAINER" ? "accent" : "info"}>{label(TYPE_LABEL, inv.invoiceType)}</Chip>}
          {inv.title && <span>{inv.title}</span>}
        </span>}
        action={<>
          <Btn icon={Download} onClick={async () => setMsg((await downloadPdf(`/api/v1/invoicing/invoices/${inv.id}/pdf`, `${inv.invoiceNumber}.pdf`)) ?? "")}>Download PDF</Btn>
          {inv.customerId && <Btn icon={FileText} onClick={async () => setMsg((await downloadPdf(`/api/v1/invoicing/customers/${inv.customerId}/statement`, `statement-${name ?? "customer"}.pdf`)) ?? "")}>Statement</Btn>}
          {canCreate && inv.status === "DRAFT" && <Btn variant="primary" icon={Send} onClick={() => issue.mutate()} disabled={issue.isPending || inv.lineItems.length === 0}>{issue.isPending ? "Issuing..." : "Issue invoice"}</Btn>}
          {canCreate && open && <Btn variant="success" icon={CheckCircle} onClick={() => setDialog("pay")}>Record payment</Btn>}
          {canCreate && use && ["ISSUED", "PARTIALLY_PAID", "PAID"].includes(inv.status) && <Btn icon={Timer} onClick={() => setDialog("hours")}>Log hours</Btn>}
          {canCreate && inv.status !== "DRAFT" && inv.status !== "CANCELLED" && <Btn icon={FileMinus} onClick={() => setDialog("credit")}>Credit note</Btn>}
        </>} />
      {msg && <div style={{ marginBottom: 14 }}><Notice tone="bad">{msg}</Notice></div>}

      <div style={{ display: "flex", flexWrap: "wrap", gap: 16, alignItems: "flex-start" }}>
        <div style={{ display: "grid", gap: 16, flex: "2 1 480px", minWidth: 0 }}>
          <section style={{ ...panel, padding: 18 }} aria-label="Bill to">
            <p style={sectionTitle}>Bill to</p>
            <div style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text-primary)" }}>{name ?? (isWalkin ? "Walk-in client" : "Loading customer...")}</div>
            {isWalkin && <div style={{ marginTop: 4 }}><Chip tone="warn">Walk-in client</Chip></div>}
            {email && <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 4 }}>{email}</div>}
            {phone && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{phone}</div>}
            {customer?.taxNumber && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 4 }}>VAT {customer.taxNumber}</div>}
          </section>

          {use && (
            <section style={{ ...panel, padding: 18 }} aria-label="Retainer hours">
              <p style={sectionTitle}>Retainer hours</p>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(110px, 1fr))", gap: 12, marginBottom: 12 }}>
                {[["Committed", `${use.committed}h`], ["Used", `${use.consumed}h`], ["Remaining", use.overage ? "None" : `${use.remaining}h`], ["Rate", `${fmtR(inv.ratePerHour)}/hr`]].map(([k, v]) => (
                  <div key={k}><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{k}</div><div style={{ fontSize: 17, fontWeight: 800, color: "var(--hf-text-primary)" }}>{v}</div></div>
                ))}
              </div>
              <Meter percent={use.percent} colour={retainerBarColour(use.level)} label="Hours used" />
              {use.level === "over" && <div style={{ marginTop: 10 }}><Notice tone="bad"><AlertTriangle size={14} aria-hidden="true" /> {use.overBy}h over the commitment. Consider a reconciliation invoice.</Notice></div>}
              {use.level === "low" && <div style={{ marginTop: 10 }}><Notice tone="warn"><AlertTriangle size={14} aria-hidden="true" /> {Math.round(use.percent)}% of the committed hours are used. Let the client know before they run out.</Notice></div>}
            </section>
          )}

          <section style={{ ...panel, overflow: "hidden" }} aria-label="Line items">
            <p style={{ ...sectionTitle, padding: "16px 18px 0", margin: "0 0 10px" }}>Line items</p>
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr style={{ background: "var(--hf-surface-muted)" }}>
                  {["Description", "Qty", "Unit price", "VAT", "Total"].map((h, i) => <th key={h} style={{ textAlign: i === 0 ? "left" : "right", padding: "9px 14px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" }}>{h}</th>)}
                </tr></thead>
                <tbody>
                  {inv.lineItems.length === 0 ? <tr><td colSpan={5} style={{ ...td, textAlign: "center", color: "var(--hf-text-faint)", padding: 24 }}>No line items.</td></tr> :
                    inv.lineItems.map((li, i) => (
                      <tr key={li.id ?? i} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                        <td style={td}>{li.description}</td><td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{li.quantity}</td><td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{fmtR(li.unitPrice)}</td>
                        <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{li.vatRate ?? 15}%</td><td style={{ ...td, textAlign: "right", whiteSpace: "nowrap", fontWeight: 700, color: "var(--hf-text-primary)" }}>{fmtR(li.lineTotal)}</td>
                      </tr>))}
                </tbody>
              </table>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", padding: "14px 18px 18px" }}>
              <Totals rows={[["Subtotal", fmtR(inv.subtotal)], ["VAT", fmtR(inv.vatTotal)], ...(num(inv.amountPaid) > 0 ? [["Paid", `− ${fmtR(inv.amountPaid)}`] as [string, string]] : [])]}
                total={inv.status === "DRAFT" ? fmtR(inv.total) : fmtR(balance)} totalLabel={inv.status === "DRAFT" ? "Total" : "Balance due"} />
            </div>
          </section>

          {inv.status !== "DRAFT" && (
            <section style={{ ...panel, padding: 18 }} aria-label="Credit notes">
              <p style={sectionTitle}>Credit notes{creditNotes.length > 0 ? ` · ${fmtR(credited)} credited` : ""}</p>
              {creditNotes.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No credit notes have been issued against this invoice.</div> : (
                <div style={{ display: "grid", gap: 8 }}>
                  {creditNotes.map(cn => (
                    <div key={cn.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, padding: "8px 12px" }}>
                      <div><span style={{ fontWeight: 700, color: "var(--hf-text-primary)" }}>{cn.creditNoteNumber}</span><span style={{ fontSize: 12, color: "var(--hf-text-muted)", marginLeft: 8 }}>{cn.reason} · {fmtDate(cn.issuedAt)}</span></div>
                      <span style={{ display: "inline-flex", gap: 10, alignItems: "center" }}>
                        <strong style={{ color: "var(--hf-danger-text-strong)" }}>{fmtR(cn.total)}</strong>
                        <Btn small icon={Download} aria-label={`Download ${cn.creditNoteNumber} PDF`} onClick={async () => setMsg((await downloadPdf(`/api/v1/invoicing/credit-notes/${cn.id}/pdf`, `${cn.creditNoteNumber}.pdf`)) ?? "")}>PDF</Btn>
                      </span>
                    </div>))}
                </div>)}
            </section>
          )}
        </div>

        <aside style={{ display: "grid", gap: 16, flex: "1 1 260px", minWidth: 0 }}>
          <section style={{ ...panel, padding: 18 }} aria-label="Payment">
            <p style={sectionTitle}>Payment</p>
            <div style={{ fontSize: 24, fontWeight: 800, color: balance > 0 && open ? (inv.status === "OVERDUE" ? "var(--hf-danger-text)" : "var(--hf-text-primary)") : "var(--hf-success-text-strong)" }}>
              {inv.status === "DRAFT" ? fmtR(inv.total) : fmtR(balance)}
            </div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 10 }}>{inv.status === "DRAFT" ? "to be invoiced" : balance > 0 ? "still owing" : "nothing owing"}</div>
            {inv.status !== "DRAFT" && <Meter percent={paidPct} colour="var(--hf-success)" label="Paid so far" />}
            <div style={{ marginTop: 12 }}>
              <Facts rows={[["Total", fmtR(inv.total)], ["Paid", fmtR(inv.amountPaid)], ["Due date", fmtDate(inv.dueDate)], ...(due ? [["", due] as [string, string]] : [])]} />
            </div>
          </section>

          <section style={{ ...panel, padding: 18 }} aria-label="Timeline">
            <p style={sectionTitle}>Timeline</p>
            <ol style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 10 }}>
              {timeline.map(s => (
                <li key={s.label} style={{ display: "flex", gap: 10, alignItems: "center" }}>
                  <span aria-hidden="true" style={{ width: 18, height: 18, borderRadius: "50%", flexShrink: 0, background: s.done ? "var(--hf-success)" : "var(--hf-surface-sunken)", border: `2px solid ${s.done ? "var(--hf-success)" : "var(--hf-border)"}` }} />
                  <span style={{ fontSize: 13, fontWeight: 600, color: s.done ? "var(--hf-text-primary)" : "var(--hf-text-faint)" }}>{s.label}{s.at ? ` · ${s.at}` : ""}</span>
                </li>))}
            </ol>
          </section>

          {(inv.quoteId || inv.recurringScheduleId) && (
            <section style={{ ...panel, padding: 18 }} aria-label="Related">
              <p style={sectionTitle}>Related</p>
              <div style={{ display: "grid", gap: 6, fontSize: 13 }}>
                {inv.quoteId && <Link to={`/quotes/${inv.quoteId}`}>The quote this came from</Link>}
                {inv.recurringScheduleId && <Link to={`/recurring/${inv.recurringScheduleId}`}>The recurring schedule that created it</Link>}
              </div>
            </section>
          )}
        </aside>
      </div>

      {dialog === "pay" && <RecordPaymentDialog invoice={inv} onClose={() => setDialog(null)} />}
      {dialog === "hours" && <LogHoursDialog invoice={inv} onClose={() => setDialog(null)} />}
      {dialog === "credit" && <CreditNoteDialog invoice={inv} onClose={() => setDialog(null)} />}
    </div>
  )
}
