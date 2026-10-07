// src/pages/quotes/QuoteDetailPage.tsx  (route: /quotes/:id)
//
// One quote: who it is for, what it prices, where it is in its life (sent, viewed, accepted, invoiced) and the actions
// that apply to its status. Sending needs INVOICE_SEND; accepting, rejecting and converting need INVOICE_CREATE.
import { useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"
import { CheckCircle, Download, Eye, FileCheck, FileText, Send, XCircle } from "lucide-react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { PageHeader } from "../../components/ui/PageHeader"
import Chip from "../../components/ui/Chip"
import { usePermission } from "../../hooks/usePermission"
import { QUOTE_LABEL, QUOTE_TONE, fmtDate, fmtR, label, quoteExpiryLabel, type Quote } from "../invoicing/billing.logic"
import { apiMessage, downloadPdf, useInvoices } from "../invoicing/queries"
import { Btn, Dialog, Facts, Loading, Notice, StateBox, Totals, panel, sectionTitle, td } from "../invoicing/ui"

interface CustomerLite { name: string; email?: string; phone?: string; taxNumber?: string; address?: Record<string, string | undefined> }

export function QuoteDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const canCreate = usePermission("INVOICE_CREATE")
  const canSend = usePermission("INVOICE_SEND")
  const [rejecting, setRejecting] = useState(false)
  const [msg, setMsg] = useState("")

  const { data: quote, isLoading, isError } = useQuery<Quote>({
    queryKey: ["quote", id], enabled: !!id,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/invoicing/quotes/${id}`); return r.data?.data ?? r.data },
  })
  const { data: customer } = useQuery<CustomerLite>({
    queryKey: ["customer", quote?.customerId], enabled: !!quote?.customerId,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/crm/customers/${quote!.customerId}`); return r.data?.data ?? r.data },
  })
  // The invoice a quote became is the one that points back at it.
  const { data: invoices } = useInvoices()
  const invoice = quote?.status === "INVOICED" ? invoices?.rows.find(i => i.quoteId === quote.id) : undefined

  const after = () => { setMsg(""); qc.invalidateQueries({ queryKey: ["quote", id] }); qc.invalidateQueries({ queryKey: ["quotes"] }) }
  const post = (action: string, fallback: string, onOk?: (res: any) => void) => useMutation({ // eslint-disable-line react-hooks/rules-of-hooks
    mutationFn: () => apiClient.post(`/api/v1/invoicing/quotes/${id}/${action}`),
    onSuccess: res => { after(); onOk?.(res) },
    onError: (e: any) => setMsg(apiMessage(e, fallback)),
  })
  const send = post("send", "The quote could not be sent.")
  const accept = post("accept", "The quote could not be accepted.")
  const reject = post("reject", "The quote could not be rejected.", () => setRejecting(false))
  const convert = post("convert-to-invoice", "The quote could not be converted.", res => {
    qc.invalidateQueries({ queryKey: ["invoices"] })
    const newId = res.data?.data ?? res.data
    if (typeof newId === "string") navigate(`/invoices/${newId}`)
  })

  if (isLoading) return <Loading text="Loading quote..." />
  if (isError || !quote) return <StateBox icon={FileText} tone="bad" title="Quote not found" text="It may have been removed, or you may not have access." action={<Btn onClick={() => navigate("/quotes")}>Back to quotes</Btn>} />

  const isWalkin = !quote.customerId
  const name = isWalkin ? quote.walkinClientName : customer?.name
  const email = isWalkin ? quote.walkinClientEmail : customer?.email
  const phone = isWalkin ? quote.walkinClientPhone : customer?.phone
  const addr = customer?.address ? [customer.address.street, customer.address.suburb, customer.address.city, customer.address.province, customer.address.postalCode].filter(Boolean).join(", ") : ""
  const lines = quote.lineItems ?? []
  const expiry = quoteExpiryLabel(quote)
  const steps = [
    { label: "Created", at: quote.createdAt, done: true },
    { label: "Sent to client", at: quote.sentAt, done: !!quote.sentAt },
    { label: quote.firstViewedAt ? `Viewed${(quote.viewCount ?? 0) > 1 ? ` (${quote.viewCount} times)` : ""}` : "Viewed by client", at: quote.firstViewedAt, done: !!quote.firstViewedAt },
    { label: "Accepted", at: quote.acceptedAt, done: !!quote.acceptedAt || quote.status === "INVOICED" },
    { label: "Invoiced", at: invoice?.createdAt, done: quote.status === "INVOICED" },
  ]

  return (
    <div style={{ maxWidth: 1000 }}>
      <PageHeader breadcrumbs={[{ label: "Quotes", to: "/quotes" }, { label: quote.quoteNumber }]} icon={FileText} title={quote.quoteNumber}
        subtitle={<span style={{ display: "inline-flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
          <Chip tone={QUOTE_TONE[quote.status] ?? "neutral"}>{label(QUOTE_LABEL, quote.status)}</Chip>{isWalkin && <Chip tone="warn">Walk-in</Chip>}<span>{quote.title}</span></span>}
        action={<>
          <Btn icon={Download} onClick={async () => setMsg((await downloadPdf(`/api/v1/invoicing/quotes/${quote.id}/pdf`, `${quote.quoteNumber}.pdf`)) ?? "")}>Download PDF</Btn>
          {quote.status === "DRAFT" && canSend && <Btn variant="primary" icon={Send} onClick={() => send.mutate()} disabled={send.isPending || lines.length === 0}>{send.isPending ? "Sending..." : "Send quote"}</Btn>}
          {quote.status === "SENT" && canCreate && <>
            <Btn variant="success" icon={CheckCircle} onClick={() => accept.mutate()} disabled={accept.isPending}>Mark accepted</Btn>
            <Btn icon={XCircle} onClick={() => setRejecting(true)}>Mark rejected</Btn>
          </>}
          {quote.status === "ACCEPTED" && canCreate && <Btn variant="primary" icon={FileCheck} onClick={() => convert.mutate()} disabled={convert.isPending}>{convert.isPending ? "Converting..." : "Convert to invoice"}</Btn>}
          {quote.status === "INVOICED" && <Btn icon={FileCheck} onClick={() => navigate(invoice ? `/invoices/${invoice.id}` : "/invoices")}>View invoice</Btn>}
        </>} />
      {msg && <div style={{ marginBottom: 14 }}><Notice tone="bad">{msg}</Notice></div>}
      {quote.status === "DRAFT" && lines.length === 0 && <div style={{ marginBottom: 14 }}><Notice tone="info">Add at least one line item before this quote can be sent.</Notice></div>}
      {expiry && <div style={{ marginBottom: 14 }}><Notice tone={expiry.startsWith("Lapsed") ? "bad" : "warn"}>{expiry}{quote.expiresAt ? `. Valid until ${fmtDate(quote.expiresAt)}.` : ""}</Notice></div>}
      {quote.status === "ACCEPTED" && <div style={{ marginBottom: 14 }}><Notice tone="ok"><CheckCircle size={14} aria-hidden="true" /> Accepted. Convert it to an invoice when you are ready to bill.</Notice></div>}

      <div style={{ display: "flex", flexWrap: "wrap", gap: 16, alignItems: "flex-start" }}>
        <div style={{ display: "grid", gap: 16, flex: "2 1 480px", minWidth: 0 }}>
          <section style={{ ...panel, padding: 18 }} aria-label="Bill to">
            <p style={sectionTitle}>Bill to</p>
            <div style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text-primary)" }}>{name ?? (isWalkin ? "Walk-in client" : "Loading customer...")}</div>
            {email && <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 4 }}>{email}</div>}
            {phone && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{phone}</div>}
            {addr && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 4 }}>{addr}</div>}
            {customer?.taxNumber && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 4 }}>VAT {customer.taxNumber}</div>}
          </section>

          <section style={{ ...panel, overflow: "hidden" }} aria-label="Line items">
            <p style={{ ...sectionTitle, padding: "16px 18px 0", margin: "0 0 10px" }}>Line items</p>
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr style={{ background: "var(--hf-surface-muted)" }}>
                  {["Description", "Qty", "Unit price", "VAT", "Total"].map((h, i) => <th key={h} style={{ textAlign: i === 0 ? "left" : "right", padding: "9px 14px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" }}>{h}</th>)}
                </tr></thead>
                <tbody>
                  {lines.length === 0 ? <tr><td colSpan={5} style={{ ...td, textAlign: "center", color: "var(--hf-text-faint)", padding: 24 }}>No line items on this quote yet.</td></tr> :
                    lines.map((li, i) => (
                      <tr key={li.id ?? i} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                        <td style={td}>{li.description}{li.unit && <span style={{ color: "var(--hf-text-faint)" }}> · per {li.unit}</span>}</td>
                        <td style={{ ...td, textAlign: "right" }}>{li.quantity}</td><td style={{ ...td, textAlign: "right" }}>{fmtR(li.unitPrice)}</td>
                        <td style={{ ...td, textAlign: "right" }}>{li.vatRate ?? 15}%</td><td style={{ ...td, textAlign: "right", fontWeight: 700, color: "var(--hf-text-primary)" }}>{fmtR(li.lineTotal)}</td>
                      </tr>))}
                </tbody>
              </table>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", padding: "14px 18px 18px" }}>
              <Totals rows={[["Subtotal", fmtR(quote.subtotal)], ["VAT", fmtR(quote.vatTotal)]]} total={fmtR(quote.total)} />
            </div>
            {quote.notes && <div style={{ padding: "14px 18px", borderTop: "1px solid var(--hf-border-subtle)", background: "var(--hf-surface-muted)" }}><p style={sectionTitle}>Notes</p><div style={{ fontSize: 13, color: "var(--hf-text-tertiary)", lineHeight: 1.6 }}>{quote.notes}</div></div>}
          </section>
        </div>

        <aside style={{ display: "grid", gap: 16, flex: "1 1 260px", minWidth: 0 }}>
          <section style={{ ...panel, padding: 18 }} aria-label="Quote details">
            <p style={sectionTitle}>Details</p>
            <Facts rows={[["Created", fmtDate(quote.createdAt)], ["Sent", fmtDate(quote.sentAt)], ["Expires", fmtDate(quote.expiresAt)], ["Total", fmtR(quote.total)]]} />
          </section>
          <section style={{ ...panel, padding: 18 }} aria-label="Timeline">
            <p style={sectionTitle}>Timeline</p>
            <ol style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 10 }}>
              {steps.map(s => (
                <li key={s.label} style={{ display: "flex", gap: 10, alignItems: "center" }}>
                  <span aria-hidden="true" style={{ width: 18, height: 18, borderRadius: "50%", flexShrink: 0, background: s.done ? "var(--hf-success)" : "var(--hf-surface-sunken)", border: `2px solid ${s.done ? "var(--hf-success)" : "var(--hf-border)"}` }} />
                  <span style={{ fontSize: 13, fontWeight: 600, color: s.done ? "var(--hf-text-primary)" : "var(--hf-text-faint)" }}>{s.label}{s.at ? ` · ${fmtDate(s.at)}` : ""}</span>
                </li>))}
            </ol>
            {quote.status === "SENT" && !quote.firstViewedAt && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 10, display: "flex", gap: 5, alignItems: "center" }}><Eye size={12} aria-hidden="true" /> The client has not opened it yet.</div>}
          </section>
          {!isWalkin && <section style={{ ...panel, padding: 18 }} aria-label="Customer"><p style={sectionTitle}>Customer</p><div style={{ fontSize: 14, fontWeight: 700 }}>{customer?.name ?? "…"}</div><div style={{ marginTop: 6, fontSize: 13 }}><Link to="/customers">View in CRM</Link></div></section>}
        </aside>
      </div>

      {rejecting && (
        <Dialog title="Mark this quote as rejected?" subtitle={quote.quoteNumber} onClose={() => setRejecting(false)}
          footer={<><Btn onClick={() => setRejecting(false)}>Keep it</Btn><Btn variant="danger" onClick={() => reject.mutate()} disabled={reject.isPending}>{reject.isPending ? "Rejecting..." : "Mark rejected"}</Btn></>}>
          <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-muted)", lineHeight: 1.6 }}>The quote will be marked <strong>Rejected</strong> and cannot be accepted afterwards. The client would need a new quote to go ahead.</p>
        </Dialog>
      )}
    </div>
  )
}
