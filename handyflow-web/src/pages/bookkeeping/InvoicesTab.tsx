// What the practice bills a client: generate for a period (admin), send, record payments, PDF.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { dateText, errorOf, money, rowsOf } from "./bookkeeping.logic"
import { invoiceStatus, monthRange, paymentProblem, periodProblem, type BkInvoice } from "./practice.logic"
import ClientScope from "./ClientScope"
import { btn, card, danger, field, muted, primary, right, td, th } from "./ui"

export default function InvoicesTab() { return <ClientScope>{id => <Invoices clientId={id} />}</ClientScope> }

function Invoices({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const isAdmin = usePermission("BOOKKEEPING_ADMIN")
  const base = "/api/v1/bookkeeping"
  const [range, setRange] = useState(monthRange(new Date()))
  const [paying, setPaying] = useState<string | null>(null), [amount, setAmount] = useState("")
  const [error, setError] = useState<string | null>(null)
  const { data = [] } = useQuery<BkInvoice[]>({ queryKey: ["bk-invoices", clientId], queryFn: async () => rowsOf<BkInvoice>(await apiClient.get(`${base}/invoices?clientId=${clientId}&size=50&sort=issueDate,desc`)) })
  const refresh = () => { setError(null); qc.invalidateQueries({ queryKey: ["bk-invoices"] }); qc.invalidateQueries({ queryKey: ["bk-unbilled"] }); qc.invalidateQueries({ queryKey: ["bk-time"] }) }
  const fail = (e: any) => setError(errorOf(e))
  const generate = useMutation({ mutationFn: () => apiClient.post(`${base}/clients/${clientId}/invoices/generate`, { periodStart: range[0], periodEnd: range[1] }), onSuccess: refresh, onError: fail })
  const send = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/invoices/${id}/send`), onSuccess: refresh, onError: fail })
  const pay = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/invoices/${id}/payments`, { amount: Number(amount) }), onSuccess: () => { setPaying(null); setAmount(""); refresh() }, onError: fail })
  const pdf = async (i: BkInvoice) => {
    try {
      const r = await apiClient.get(`${base}/invoices/${i.id}/invoice.pdf`, { responseType: "blob" })
      const url = URL.createObjectURL(new Blob([r.data], { type: "application/pdf" })); window.open(url, "_blank"); setTimeout(() => URL.revokeObjectURL(url), 60000)
    } catch (e) { fail(e) }
  }
  const periodIssue = periodProblem(range[0], range[1])
  const target = data.find(i => i.id === paying)
  const payIssue = target ? paymentProblem(amount, target.balance) : null
  const owing = data.reduce((t, i) => t + (i.status === "DRAFT" ? 0 : i.balance), 0)
  return (
    <div>
      <div style={{ ...card, fontSize: 13 }}>Owing on sent invoices: <b>{money(owing)}</b></div>
      {isAdmin && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>Generate an invoice</h3>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <label style={muted}>From <input aria-label="Period from" type="date" value={range[0]} onChange={e => setRange([e.target.value, range[1]])} style={field} /></label>
            <label style={muted}>To <input aria-label="Period to" type="date" value={range[1]} onChange={e => setRange([range[0], e.target.value])} style={field} /></label>
            <button style={primary} disabled={!!periodIssue || generate.isPending} onClick={() => generate.mutate()}>Generate</button>
            {periodIssue && <span style={muted}>{periodIssue}</span>}
          </div>
          <p style={{ ...muted, marginBottom: 0 }}>Uses the client's agreement: a retainer is one flat fee; time and materials bills the unbilled time in the period. VAT is not added yet.</p>
        </div>
      )}
      {error && <div role="alert" style={{ ...danger, marginBottom: 8 }}>{error}</div>}
      <div style={card}>
        {data.length === 0 && <p style={muted}>No invoices yet.</p>}
        {data.length > 0 && (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={th}>No.</th><th style={th}>Period</th><th style={th}>Due</th><th style={{ ...th, ...right }}>Total</th><th style={{ ...th, ...right }}>Balance</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{data.map(i => (
              <tr key={i.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={{ ...td, fontFamily: "monospace" }}>{i.invoiceNumber}</td><td style={td}>{dateText(i.periodStart)} – {dateText(i.periodEnd)}</td><td style={td}>{dateText(i.dueDate)}</td>
                <td style={{ ...td, ...right }}>{money(i.total)}</td><td style={{ ...td, ...right }}>{money(i.balance)}</td><td style={td}>{invoiceStatus(i.status)}</td>
                <td style={{ ...td, ...right }}>
                  <button style={btn} onClick={() => pdf(i)}>PDF</button>{" "}
                  {isAdmin && i.status === "DRAFT" && <button style={btn} disabled={send.isPending} onClick={() => send.mutate(i.id)}>Send</button>}
                  {isAdmin && i.status !== "DRAFT" && i.balance > 0 && <button style={btn} onClick={() => { setPaying(paying === i.id ? null : i.id); setAmount("") }}>Record payment</button>}
                </td>
              </tr>))}</tbody>
          </table>
        )}
        {target && (
          <div style={{ marginTop: 10, display: "flex", gap: 8, alignItems: "center" }}>
            <input aria-label="Amount received" placeholder={`Up to ${money(target.balance)}`} value={amount} onChange={e => setAmount(e.target.value)} style={{ ...field, width: 150 }} />
            <button style={primary} disabled={!!payIssue || pay.isPending} onClick={() => pay.mutate(target.id)}>Save payment</button>
            {payIssue && amount && <span style={muted}>{payIssue}</span>}
          </div>
        )}
      </div>
    </div>
  )
}
