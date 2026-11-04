// Invoicing: prepare an invoice for a client, approve it, send it, record payments, credit it, and see anything the ledger refused.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, ReasonDialog, Row, SectionTitle, card, day, downloadFile, errorText, fail, money, muted, pageOf, td, th } from "./kit"
import { BASE, useLookups } from "./lookups"
import type { Agreement } from "./AgreementsTab"
import { BILL_BASES, needsBillingSetup, CREDIT_STATUS, INVOICE_STATUS, PAY_METHODS, billWho, creditActions, creditBody, creditProblem, dueNote, emptyCredit, emptyGenerate, emptyPay, generateBody, generateProblem, glNote, invoiceActions, labelOf, payBody, payProblem, type BillWho, type CreditForm, type GenerateForm, type PayForm } from "./fm.logic"

interface Invoice { id: string; clientId: string; invoiceNumber: string; periodStart: string; periodEnd: string; issueDate: string | null; dueDate: string | null; subtotal: number; vatAmount: number; total: number; amountPaid: number; balance: number; status: string; vatRate: number | null; glStatus: string | null; glLastError: string | null; agreementId: string | null; creditedAmount: number | null; taxInvoice: boolean; clientName: string | null }
interface Payment { id: string; amount: number; paymentDate: string; method: string | null; reference: string | null; createdAt: string; glStatus: string | null }
interface Credit { id: string; creditNoteNumber: string; issueDate: string; reason: string; subtotal: number; vatAmount: number; total: number; status: string; glStatus: string | null; glLastError: string | null }
interface GlException { type: string; id: string; invoiceId: string | null; reference: string; amount: number; attempts: number; lastError: string | null; lastAttemptAt: string | null }
const todayIso = () => new Date().toISOString().slice(0, 10)

function useBillWho(): BillWho {
  const admin = usePermission("FACILITIESMANAGEMENT_ADMIN"), m = usePermission("FACILITIESMANAGEMENT_BILLING_MANAGE"), a = usePermission("FACILITIESMANAGEMENT_BILLING_APPROVE")
  return billWho(p => ({ FACILITIESMANAGEMENT_ADMIN: admin, FACILITIESMANAGEMENT_BILLING_MANAGE: m, FACILITIESMANAGEMENT_BILLING_APPROVE: a } as Record<string, boolean>)[p] ?? false)
}

export default function BillingTab() {
  const qc = useQueryClient(), lookups = useLookups(), who = useBillWho()
  const [clientId, setClientId] = useState(""), [page, setPage] = useState(0), [generating, setGenerating] = useState(false), [open, setOpen] = useState<string | null>(null)
  const { data, isLoading, isError } = useQuery({ enabled: !!clientId, queryKey: ["fm-invoices", clientId, page], queryFn: async () => pageOf<Invoice>(await apiClient.get(`${BASE}/invoices`, { params: { clientId, page, size: 20 } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fm-invoices"] }); qc.invalidateQueries({ queryKey: ["fm-invoice"] }); qc.invalidateQueries({ queryKey: ["fm-gl-exceptions"] }) }
  const rows = data?.content ?? [], today = todayIso()
  return (
    <div>
      <SectionTitle right={who.prepare && clientId && <Btn kind="primary" onClick={() => setGenerating(true)}>Prepare an invoice</Btn>}>Invoices</SectionTitle>
      <GlExceptions who={who} onRetried={refresh} />
      <div style={{ maxWidth: 360, marginBottom: 12 }}><Pick label="Client" value={clientId} onChange={v => { setClientId(v); setPage(0) }} blank="Choose a client…" options={lookups.clients.map(c => [c.id, c.tradingName] as const)} /></div>
      {!clientId ? <div style={card}><Empty>Choose a client to see their invoices.</Empty></div> : (
        <div style={{ ...card, padding: 0, overflowX: "auto" }}>
          {isError ? <Empty>Could not load the invoices. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No invoices for this client yet.</Empty> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Invoice</th><th style={th}>Period</th><th style={th}>Due</th><th style={{ ...th, textAlign: "right" }}>Total</th><th style={{ ...th, textAlign: "right" }}>Owing</th><th style={th}>Status</th></tr></thead>
              <tbody>{rows.map(i => {
                const s = INVOICE_STATUS[i.status] ?? { label: i.status, tone: "neutral" as const }, d = dueNote(i.status, i.balance, i.dueDate, today), g = glNote(i.glStatus)
                return (
                  <tr key={i.id} onClick={() => setOpen(i.id)} style={{ cursor: "pointer" }}>
                    <td style={td}><b>{i.invoiceNumber}</b></td>
                    <td style={td}>{day(i.periodStart)} – {day(i.periodEnd)}</td>
                    <td style={td}>{i.dueDate ? day(i.dueDate) : "—"}{d && <div><Pill tone={d.tone}>{d.text}</Pill></div>}</td>
                    <td style={{ ...td, textAlign: "right" }}>{money(i.total)}</td>
                    <td style={{ ...td, textAlign: "right" }}>{money(i.balance)}</td>
                    <td style={td}><Pill tone={s.tone}>{s.label}</Pill>{g && g.tone !== "success" && <div style={{ marginTop: 4 }}><Pill tone={g.tone}>{g.label}</Pill></div>}</td>
                  </tr>)
              })}</tbody>
            </table>)}
        </div>)}
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {generating && <GenerateDialog clientId={clientId} onClose={() => setGenerating(false)} onDone={id => { refresh(); setOpen(id) }} />}
      {open && <InvoiceView id={open} who={who} onClose={() => setOpen(null)} onChanged={refresh} />}
    </div>
  )
}

/** Postings the ledger refused. Shown only to people who may see them; only approvers can send them round again. */
function GlExceptions({ who, onRetried }: { who: BillWho; onRetried: () => void }) {
  const see = usePermission("FACILITIESMANAGEMENT_ADMIN") || usePermission("FACILITIESMANAGEMENT_BILLING_MANAGE") || usePermission("FACILITIESMANAGEMENT_BILLING_APPROVE")
  const { data } = useQuery({ enabled: see, queryKey: ["fm-gl-exceptions"], queryFn: async () => (await apiClient.get<GlException[]>(`${BASE}/billing/gl-exceptions`)).data })
  const retry = useMutation({
    mutationFn: async () => (await apiClient.post<{ requeued: number }>(`${BASE}/billing/gl-exceptions/retry`)).data,
    onSuccess: r => { toast.success(`${r?.requeued ?? 0} posting${r?.requeued === 1 ? "" : "s"} queued again`); onRetried() }, onError: e => { void fail(e, "Could not retry the postings") },
  })
  const rows = Array.isArray(data) ? data : []
  if (!see || rows.length === 0) return null
  return (
    <div style={{ ...card, marginBottom: 12, borderColor: "var(--hf-danger-text, #b42318)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
        <b>{rows.length} posting{rows.length === 1 ? "" : "s"} did not reach the ledger</b>
        {who.approve && <Btn small disabled={retry.isPending} onClick={() => retry.mutate()}>{retry.isPending ? "Retrying…" : "Retry them all"}</Btn>}
      </div>
      <div style={{ ...muted, margin: "4px 0 8px" }}>This is usually a missing account in the chart of accounts. Fix that first, then retry.</div>
      {rows.map(r => <div key={r.type + r.id} style={{ fontSize: 12, padding: "4px 0", borderTop: "1px solid var(--hf-border-subtle, #e5e5e5)" }}><b>{r.reference}</b> · {labelOf([["INVOICE", "Invoice"], ["PAYMENT", "Payment"], ["CREDIT_NOTE", "Credit note"]], r.type)} · {money(r.amount)} · tried {r.attempts}×{r.lastError ? ` — ${r.lastError}` : ""}{needsBillingSetup(r.lastError) && <> · <a href="/facilitiesmanagement/settings" style={{ fontWeight: 700 }}>Open Company &amp; billing</a></>}</div>)}
    </div>
  )
}

function GenerateDialog({ clientId, onClose, onDone }: { clientId: string; onClose: () => void; onDone: (id: string) => void }) {
  const [f, setF] = useState<GenerateForm>(() => emptyGenerate(todayIso())), [err, setErr] = useState("")
  const ags = useQuery({ enabled: f.basis === "RETAINER", queryKey: ["fm-agreements-active", clientId], queryFn: async () => pageOf<Agreement>(await apiClient.get(`${BASE}/service-agreements`, { params: { clientId, size: 100 } })).content })
  const retainers = (ags.data ?? []).filter(a => a.status === "ACTIVE" && a.billingType === "RETAINER")
  const problem = generateProblem(f)
  const go = useMutation({
    mutationFn: async () => (await apiClient.post<Invoice>(`${BASE}/clients/${clientId}/invoices/generate`, generateBody(f))).data,
    onSuccess: i => { toast.success("Invoice prepared as a draft"); onDone(i.id); onClose() }, onError: async e => setErr(await errorText(e, "Could not prepare the invoice")),
  })
  return (
    <Dialog title="Prepare an invoice" onClose={onClose} footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || go.isPending} onClick={() => { setErr(""); go.mutate() }}>{go.isPending ? "Preparing…" : "Prepare draft"}</Btn></>}>
      <Pick label="Invoice for" value={f.basis} onChange={v => setF(s => ({ ...s, basis: v }))} options={BILL_BASES} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Input label="Period starts" type="date" value={f.periodStart} onChange={v => setF(s => ({ ...s, periodStart: v }))} />
        <Input label="Period ends" type="date" value={f.periodEnd} onChange={v => setF(s => ({ ...s, periodEnd: v }))} />
      </div>
      {f.basis === "RETAINER" && <Pick label="Retainer" value={f.agreementId} onChange={v => setF(s => ({ ...s, agreementId: v }))} blank={retainers.length > 1 ? "Choose a retainer…" : "The client's retainer"} options={retainers.map(a => [a.id, a.name || "Untitled"] as const)} />}
      <div style={muted}>The invoice is a draft until someone with approval rights approves it. Only then does it reach the ledger. VAT is added when the supplier is registered.</div>
      {problem && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function InvoiceView({ id, who, onClose, onChanged }: { id: string; who: BillWho; onClose: () => void; onChanged: () => void }) {
  const qc = useQueryClient()
  const [dlg, setDlg] = useState<"pay" | "credit" | "cancel" | null>(null), [cancelCn, setCancelCn] = useState<Credit | null>(null)
  const inv = useQuery({ queryKey: ["fm-invoice", id], queryFn: async () => (await apiClient.get<Invoice>(`${BASE}/invoices/${id}`)).data })
  const pays = useQuery({ queryKey: ["fm-invoice", id, "payments"], queryFn: async () => (await apiClient.get<Payment[]>(`${BASE}/invoices/${id}/payments`)).data })
  const cns = useQuery({ queryKey: ["fm-invoice", id, "credits"], queryFn: async () => (await apiClient.get<Credit[]>(`${BASE}/invoices/${id}/credit-notes`)).data })
  const done = () => { qc.invalidateQueries({ queryKey: ["fm-invoice", id] }); onChanged() }
  const act = useMutation({
    mutationFn: (p: string) => apiClient.post(`${BASE}/${p}`),
    onSuccess: (_r, p) => { toast.success(p.includes("approve") ? "Approved" : p.endsWith("send") ? "Sent to the client" : "Done"); done() }, onError: e => { void fail(e, "That did not work") },
  })
  const i = inv.data
  if (inv.isError) return <Dialog title="Invoice" onClose={onClose} footer={<Btn onClick={onClose}>Close</Btn>}><Empty>Could not load the invoice.</Empty></Dialog>
  if (!i) return <Dialog title="Invoice" onClose={onClose} footer={<Btn onClick={onClose}>Close</Btn>}><Empty>Loading…</Empty></Dialog>
  const s = INVOICE_STATUS[i.status] ?? { label: i.status, tone: "neutral" as const }, g = glNote(i.glStatus), actions = invoiceActions(i.status, i.balance, who), d = dueNote(i.status, i.balance, i.dueDate, todayIso())
  const credits = Array.isArray(cns.data) ? cns.data : [], payments = Array.isArray(pays.data) ? pays.data : []
  return (
    <Dialog title={<>{i.invoiceNumber} <Pill tone={s.tone}>{s.label}</Pill></>} onClose={onClose} width={700}
      footer={<><Btn onClick={onClose}>Close</Btn>
        {i.status !== "DRAFT" && <Btn onClick={() => void downloadFile(`${BASE}/invoices/${id}/invoice.pdf`, `${i.invoiceNumber}.pdf`)}>PDF</Btn>}
        {actions.includes("cancel") && <Btn kind="danger" onClick={() => setDlg("cancel")}>Cancel draft</Btn>}
        {actions.includes("credit") && <Btn onClick={() => setDlg("credit")}>Credit note</Btn>}
        {actions.includes("pay") && <Btn onClick={() => setDlg("pay")}>Record payment</Btn>}
        {actions.includes("send") && <Btn kind="primary" disabled={act.isPending} onClick={() => act.mutate(`invoices/${id}/send`)}>Send to client</Btn>}
        {actions.includes("approve") && <Btn kind="primary" disabled={act.isPending} onClick={() => act.mutate(`invoices/${id}/approve`)}>Approve</Btn>}</>}>
      <Row k="Client">{i.clientName ?? "—"}</Row>
      <Row k="Period">{day(i.periodStart)} – {day(i.periodEnd)}</Row>
      <Row k="Issued / due">{i.issueDate ? day(i.issueDate) : "Not issued"} / {i.dueDate ? day(i.dueDate) : "—"} {d && <Pill tone={d.tone}>{d.text}</Pill>}</Row>
      {g && <Row k="Ledger"><Pill tone={g.tone}>{g.label}</Pill>{i.glLastError ? <span style={muted}> {i.glLastError}</span> : null}</Row>}
      <div style={{ textAlign: "right", lineHeight: 1.7, margin: "12px 0" }}>
        <div style={muted}>Subtotal {money(i.subtotal)}</div>
        <div style={muted}>VAT{i.vatRate != null ? ` (${Math.round(i.vatRate * 100 * 100) / 100}%)` : ""} {money(i.vatAmount)}{i.taxInvoice ? "" : " · not a tax invoice"}</div>
        <div style={{ fontWeight: 700, fontSize: 16 }}>Total {money(i.total)}</div>
        {(i.creditedAmount ?? 0) > 0 && <div style={muted}>Credited {money(i.creditedAmount)}</div>}
        <div style={muted}>Paid {money(i.amountPaid)}</div>
        <div style={{ fontWeight: 700 }}>Owing {money(i.balance)}</div>
      </div>
      {i.status === "DRAFT" && <div style={muted}>A draft does not reach the ledger. {who.approve ? "Approve it to post it." : "Someone with approval rights must approve it."} The person who prepared it usually cannot approve it themselves.</div>}
      <div style={{ fontSize: 13, fontWeight: 700, margin: "14px 0 6px" }}>Payments</div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {pays.isError ? <div style={{ ...muted, padding: 12 }}>Could not load the payments.</div> : payments.length === 0 ? <div style={{ ...muted, padding: 12 }}>No payments yet.</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}><tbody>{payments.map(p => {
            const pg = glNote(p.glStatus)
            return <tr key={p.id}><td style={td}>{day(p.paymentDate)}</td><td style={td}>{labelOf(PAY_METHODS, p.method)}{p.reference ? <div style={muted}>{p.reference}</div> : null}</td><td style={{ ...td, textAlign: "right" }}>{money(p.amount)}</td><td style={td}>{pg && pg.tone !== "success" && <Pill tone={pg.tone}>{pg.label}</Pill>}</td></tr>
          })}</tbody></table>)}
      </div>
      <div style={{ fontSize: 13, fontWeight: 700, margin: "14px 0 6px" }}>Credit notes</div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {cns.isError ? <div style={{ ...muted, padding: 12 }}>Could not load the credit notes.</div> : credits.length === 0 ? <div style={{ ...muted, padding: 12 }}>None.</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}><tbody>{credits.map(c => {
            const cs = CREDIT_STATUS[c.status] ?? { label: c.status, tone: "neutral" as const }, ca = creditActions(c.status, who), cg = glNote(c.glStatus)
            return (
              <tr key={c.id}>
                <td style={td}><b>{c.creditNoteNumber}</b><div style={muted}>{day(c.issueDate)} · {c.reason}</div></td>
                <td style={{ ...td, textAlign: "right" }}>{money(c.total)}</td>
                <td style={td}><Pill tone={cs.tone}>{cs.label}</Pill>{cg && cg.tone !== "success" && c.status === "APPROVED" && <div style={{ marginTop: 4 }}><Pill tone={cg.tone}>{cg.label}</Pill></div>}</td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                  {c.status === "APPROVED" && <Btn small onClick={() => void downloadFile(`${BASE}/credit-notes/${c.id}/credit-note.pdf`, `${c.creditNoteNumber}.pdf`)}>PDF</Btn>}
                  {ca.includes("approve") && <Btn small kind="primary" disabled={act.isPending} onClick={() => act.mutate(`credit-notes/${c.id}/approve`)}>Approve</Btn>}{" "}
                  {ca.includes("cancel") && <Btn small onClick={() => setCancelCn(c)}>Cancel</Btn>}
                </td>
              </tr>)
          })}</tbody></table>)}
      </div>
      {dlg === "pay" && <PayDialog invoice={i} onClose={() => setDlg(null)} onDone={done} />}
      {dlg === "credit" && <CreditDialog invoice={i} onClose={() => setDlg(null)} onDone={done} />}
      {dlg === "cancel" && <ReasonDialog title="Cancel this draft?" label="Reason" confirm="Cancel draft" danger intro="The work it covers goes back to being waiting to invoice." onClose={() => setDlg(null)}
        onSubmit={async reason => { await apiClient.post(`${BASE}/invoices/${id}/cancel`, { reason }); toast.success("Draft cancelled"); done() }} />}
      {cancelCn && <ReasonDialog title="Cancel this credit note?" label="Reason" confirm="Cancel credit note" danger onClose={() => setCancelCn(null)}
        onSubmit={async reason => { await apiClient.post(`${BASE}/credit-notes/${cancelCn.id}/cancel`, { reason }); toast.success("Credit note cancelled"); done() }} />}
    </Dialog>
  )
}

function PayDialog({ invoice, onClose, onDone }: { invoice: Invoice; onClose: () => void; onDone: () => void }) {
  const [f, setF] = useState<PayForm>(() => emptyPay(invoice.balance, todayIso())), [err, setErr] = useState("")
  // One key per opening of the dialog: pressing the button twice, or a retry after a dropped connection, records the payment once.
  const [key] = useState(() => (typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : String(Date.now()) + Math.random()))
  const problem = payProblem(f, invoice.balance)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/invoices/${invoice.id}/payments`, { ...payBody(f), idempotencyKey: key }),
    onSuccess: () => { toast.success("Payment recorded"); onDone(); onClose() }, onError: async e => setErr(await errorText(e, "Could not record the payment")),
  })
  return (
    <Dialog title={`Record a payment on ${invoice.invoiceNumber}`} onClose={onClose} footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Record payment"}</Btn></>}>
      <div style={{ ...muted, marginBottom: 8 }}>Owing {money(invoice.balance)}</div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Input label="Amount (R)" type="number" min="0.01" step="0.01" value={f.amount} onChange={v => setF(s => ({ ...s, amount: v }))} autoFocus />
        <Input label="Paid on" type="date" value={f.paymentDate} onChange={v => setF(s => ({ ...s, paymentDate: v }))} />
        <Pick label="Method" value={f.method} onChange={v => setF(s => ({ ...s, method: v }))} options={PAY_METHODS} />
        <Input label="Reference" value={f.reference} onChange={v => setF(s => ({ ...s, reference: v }))} maxLength={100} hint="Bank reference, if any" />
      </div>
      {problem && f.amount !== "" && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function CreditDialog({ invoice, onClose, onDone }: { invoice: Invoice; onClose: () => void; onDone: () => void }) {
  const [f, setF] = useState<CreditForm>(emptyCredit), [err, setErr] = useState("")
  const problem = creditProblem(f)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/invoices/${invoice.id}/credit-notes`, creditBody(f)),
    onSuccess: () => { toast.success("Credit note drafted"); onDone(); onClose() }, onError: async e => setErr(await errorText(e, "Could not draft the credit note")),
  })
  return (
    <Dialog title={`Credit ${invoice.invoiceNumber}`} onClose={onClose} footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Draft credit note"}</Btn></>}>
      <Pick label="Credit" value={f.mode} onChange={v => setF(s => ({ ...s, mode: v as CreditForm["mode"] }))} options={[["full", `The whole amount still owing (${money(invoice.balance)})`], ["part", "Part of it"]]} />
      {f.mode === "part" && <Input label="Amount before VAT (R)" type="number" min="0.01" step="0.01" value={f.subtotal} onChange={v => setF(s => ({ ...s, subtotal: v }))} hint="VAT is added the same way as on the invoice" />}
      <Input label="Reason" value={f.reason} onChange={v => setF(s => ({ ...s, reason: v }))} maxLength={500} />
      <div style={muted}>The credit note is a draft. Once someone with approval rights approves it, the invoice balance goes down and the ledger is updated.</div>
      {problem && f.reason.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
