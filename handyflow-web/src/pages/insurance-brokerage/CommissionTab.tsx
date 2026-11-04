// What the brokerage has earned in commission and what is still to be paid. Invoices are raised by activating or renewing a
// policy; here they are only read and settled.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { errorText } from "../../api/projectDocs"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Pill, SectionTitle, Stat, card, day, muted, th, td } from "../projects/tabs/pmKit"
import { paymentProblem } from "../insurance/insurance.logic"
import { Input, Pager, money } from "../insurance/parts"
import { BASE, pageOf, useLookups } from "./lookups"

interface Inv { id: string; clientId: string; policyId: string; invoiceNumber: string; description: string | null; invoiceDate: string; dueDate: string | null; subtotal: number; vatAmount: number; total: number; amountPaid: number; balance: number; status: string; paidAt: string | null }
const TONE: Record<string, "neutral" | "info" | "success" | "warning" | "danger"> = { PAID: "success", PARTIALLY_PAID: "warning", OVERDUE: "danger", SENT: "info", DRAFT: "neutral", ISSUED: "info" }
const WORDS = (s: string) => s.replace(/_/g, " ").toLowerCase().replace(/^./, c => c.toUpperCase())
const overdue = (i: Inv) => i.balance > 0 && !!i.dueDate && i.dueDate < new Date().toISOString().slice(0, 10)

export default function CommissionTab() {
  const qc = useQueryClient()
  const canWrite = usePermission("INSURANCEBROKERAGE_MANAGE") || usePermission("INSURANCEBROKERAGE_ADMIN")
  const { clientName } = useLookups()
  const [page, setPage] = useState(0), [paying, setPaying] = useState<Inv | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["ib-commission", page], queryFn: async () => pageOf<Inv>(await apiClient.get(`${BASE}/commission-invoices`, { params: { page, size: 24 } })) })
  const rows = data?.content ?? []
  const owing = rows.reduce((s, i) => s + (i.balance > 0 ? i.balance : 0), 0)
  const late = rows.filter(overdue)
  return (
    <div>
      <SectionTitle>Commission</SectionTitle>
      {rows.length > 0 && (
        <div style={{ ...card, display: "flex", gap: 28, flexWrap: "wrap", marginBottom: 12 }}>
          <Stat label={data && data.totalPages > 1 ? "Owing on this page" : "Owing"} value={money(owing)} tone={owing > 0 ? "warn" : "good"} />
          <Stat label="Overdue" value={late.length} sub={late.length ? money(late.reduce((s, i) => s + i.balance, 0)) : undefined} tone={late.length ? "bad" : "good"} />
        </div>)}
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the commission invoices. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No commission invoices yet. One is raised each time a policy is activated or renewed.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Invoice</th><th style={th}>Client</th><th style={th}>Due</th><th style={{ ...th, textAlign: "right" }}>Total</th><th style={{ ...th, textAlign: "right" }}>Paid</th><th style={{ ...th, textAlign: "right" }}>Balance</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(i => (
              <tr key={i.id}>
                <td style={td}><b>{i.invoiceNumber}</b><div style={muted}>{day(i.invoiceDate)}{i.description ? ` · ${i.description}` : ""}</div></td>
                <td style={td}>{clientName(i.clientId)}</td>
                <td style={td}>{day(i.dueDate)}{overdue(i) && <div style={{ marginTop: 3 }}><Pill tone="danger">Overdue</Pill></div>}</td>
                <td style={{ ...td, textAlign: "right" }}>{money(i.total)}<div style={muted}>VAT {money(i.vatAmount)}</div></td>
                <td style={{ ...td, textAlign: "right" }}>{money(i.amountPaid)}</td>
                <td style={{ ...td, textAlign: "right", fontWeight: 700 }}>{money(i.balance)}</td>
                <td style={td}><Pill tone={TONE[i.status] ?? "neutral"}>{WORDS(i.status)}</Pill></td>
                <td style={{ ...td, textAlign: "right" }}>{canWrite && i.balance > 0 && <Btn small onClick={() => setPaying(i)}>Record payment</Btn>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {paying && <PayDialog invoice={paying} onClose={() => setPaying(null)} onDone={() => { qc.invalidateQueries({ queryKey: ["ib-commission"] }); qc.invalidateQueries({ queryKey: ["ib-client-invoices"] }) }} />}
    </div>
  )
}

function PayDialog({ invoice: i, onClose, onDone }: { invoice: Inv; onClose: () => void; onDone: () => void }) {
  const [amount, setAmount] = useState(String(i.balance)), [err, setErr] = useState("")
  const problem = paymentProblem(i.balance, amount)
  const pay = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/commission-invoices/${i.id}/payments`, { amount: Number(amount) }),
    onSuccess: () => { toast.success("Payment recorded"); onDone(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not record the payment")),
  })
  return (
    <Dialog title={`Payment on ${i.invoiceNumber}`} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || pay.isPending} onClick={() => { setErr(""); pay.mutate() }}>{pay.isPending ? "Saving…" : "Record payment"}</Btn></>}>
      <div style={{ ...muted, marginBottom: 12, lineHeight: 1.5 }}>Balance owing: <b>{money(i.balance)}</b>. This only records that the invoice has been settled; it does not post anything to the ledger.</div>
      <Input label="Amount received (R)" type="number" min="0" step="0.01" value={amount} onChange={setAmount} autoFocus />
      {problem && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
