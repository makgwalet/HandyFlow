// Fee invoices: send a draft, record payments, and (administrators only) write one off. Invoices are raised from a matter.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pill, SectionTitle, Stat, card, day, errorText, fail, inp, money, muted, pageOf, td, th } from "./kit"
import { BASE, useLookups } from "./lookups"
import { INVOICE_STATUS, invoiceActions, paymentProblem } from "./legal.logic"

export interface Invoice { id: string; clientId: string; matterId: string | null; invoiceNumber: string; description: string | null; issueDate: string | null; dueDate: string | null; subtotal: number; vatAmount: number; totalAmount: number; amountPaid: number; outstandingBalance: number; status: string; notes: string | null }
const overdue = (i: Invoice) => ["SENT", "PARTIALLY_PAID"].includes(i.status) && !!i.dueDate && i.dueDate < new Date().toISOString().slice(0, 10)

export default function InvoicesTab() {
  const qc = useQueryClient()
  const canWrite = usePermission("LEGALPRACTICE_MANAGE") || usePermission("LEGALPRACTICE_ADMIN")
  const isAdmin = usePermission("LEGALPRACTICE_ADMIN")
  const lookups = useLookups()
  const [page, setPage] = useState(0), [clientId, setClientId] = useState(""), [paying, setPaying] = useState<Invoice | null>(null), [writing, setWriting] = useState<Invoice | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["lp-invoices", clientId, page], queryFn: async () => pageOf<Invoice>(await apiClient.get(`${BASE}/invoices`, { params: { clientId: clientId || undefined, page, size: 25 } })) })
  const refresh = () => { for (const k of ["lp-invoices", "lp-client-invoices", "lp-unbilled-time", "lp-unbilled-disb", "lp-time", "lp-disb"]) qc.invalidateQueries({ queryKey: [k] }) }
  const send = useMutation({ mutationFn: (i: Invoice) => apiClient.post(`${BASE}/invoices/${i.id}/send`), onSuccess: () => { toast.success("Invoice sent"); refresh() }, onError: e => { void fail(e, "Could not send the invoice") } })
  const rows = data?.content ?? []
  const owing = rows.reduce((s, i) => s + (["SENT", "PARTIALLY_PAID"].includes(i.status) ? i.outstandingBalance : 0), 0)
  const late = rows.filter(overdue)
  return (
    <div>
      <SectionTitle>Invoices</SectionTitle>
      <div style={{ display: "flex", gap: 8, marginBottom: 12, flexWrap: "wrap" }}>
        <select aria-label="Client" value={clientId} onChange={e => { setClientId(e.target.value); setPage(0) }} style={{ ...inp, width: 260 }}>
          <option value="">All clients</option>{lookups.clients.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
      </div>
      {rows.length > 0 && (
        <div style={{ ...card, display: "flex", gap: 28, flexWrap: "wrap", marginBottom: 12 }}>
          <Stat label={(data?.totalPages ?? 0) > 1 ? "Owing on this page" : "Owing"} value={money(owing)} tone={owing > 0 ? "warn" : "good"} />
          <Stat label="Overdue" value={late.length} sub={late.length ? money(late.reduce((s, i) => s + i.outstandingBalance, 0)) : undefined} tone={late.length ? "bad" : "good"} />
        </div>)}
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the invoices. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No invoices yet. Raise one from a matter.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Invoice</th><th style={th}>Client</th><th style={th}>Due</th><th style={{ ...th, textAlign: "right" }}>Total</th><th style={{ ...th, textAlign: "right" }}>Owing</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(i => {
              const st = INVOICE_STATUS[i.status] ?? { label: i.status, tone: "neutral" as const }
              const acts = canWrite ? invoiceActions(i.status, isAdmin) : []
              return (
                <tr key={i.id}>
                  <td style={td}><b>{i.invoiceNumber}</b><div style={muted}>{day(i.issueDate)}{i.description ? ` · ${i.description}` : ""}</div></td>
                  <td style={td}>{lookups.clientName(i.clientId)}</td>
                  <td style={td}>{day(i.dueDate)}{overdue(i) && <div style={{ marginTop: 3 }}><Pill tone="danger">Overdue</Pill></div>}</td>
                  <td style={{ ...td, textAlign: "right" }}>{money(i.totalAmount)}<div style={muted}>VAT {money(i.vatAmount)}</div></td>
                  <td style={{ ...td, textAlign: "right", fontWeight: 700 }}>{money(i.outstandingBalance)}</td>
                  <td style={td}><Pill tone={st.tone}>{st.label}</Pill></td>
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                    {acts.includes("send") && <Btn small disabled={send.isPending} onClick={() => send.mutate(i)}>Send</Btn>}{" "}
                    {acts.includes("pay") && <Btn small onClick={() => setPaying(i)}>Record payment</Btn>}{" "}
                    {acts.includes("writeOff") && <Btn small kind="danger" onClick={() => setWriting(i)}>Write off</Btn>}
                  </td>
                </tr>)
            })}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {paying && <PayDialog invoice={paying} onClose={() => setPaying(null)} onDone={refresh} />}
      {writing && <WriteOffDialog invoice={writing} onClose={() => setWriting(null)} onDone={refresh} />}
    </div>
  )
}

function PayDialog({ invoice: i, onClose, onDone }: { invoice: Invoice; onClose: () => void; onDone: () => void }) {
  const [amount, setAmount] = useState(String(i.outstandingBalance)), [err, setErr] = useState("")
  const problem = paymentProblem(i.outstandingBalance, amount)
  const pay = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/invoices/${i.id}/record-payment`, { amount: Number(amount) }),
    onSuccess: () => { toast.success("Payment recorded"); onDone(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not record the payment")),
  })
  return (
    <Dialog title={`Payment on ${i.invoiceNumber}`} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || pay.isPending} onClick={() => { setErr(""); pay.mutate() }}>{pay.isPending ? "Saving…" : "Record payment"}</Btn></>}>
      <div style={{ ...muted, marginBottom: 12 }}>Outstanding: <b>{money(i.outstandingBalance)}</b></div>
      <Input label="Amount received (R)" type="number" min="0" step="0.01" value={amount} onChange={setAmount} autoFocus />
      {problem && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function WriteOffDialog({ invoice: i, onClose, onDone }: { invoice: Invoice; onClose: () => void; onDone: () => void }) {
  const [err, setErr] = useState("")
  const m = useMutation({ mutationFn: () => apiClient.post(`${BASE}/invoices/${i.id}/write-off`), onSuccess: () => { toast.success("Invoice written off"); onDone(); onClose() }, onError: async e => setErr(await errorText(e, "Could not write off the invoice")) })
  return (
    <Dialog title={`Write off ${i.invoiceNumber}?`} onClose={onClose}
      footer={<><Btn onClick={onClose}>Keep it</Btn><Btn kind="danger" disabled={m.isPending} onClick={() => { setErr(""); m.mutate() }}>{m.isPending ? "Writing off…" : "Write off"}</Btn></>}>
      <div style={{ lineHeight: 1.5, fontSize: 13 }}>The <b>{money(i.outstandingBalance)}</b> still owing will be written off as unrecoverable. This cannot be undone.</div>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
