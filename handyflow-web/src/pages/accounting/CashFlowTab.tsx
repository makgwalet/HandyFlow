// src/pages/accounting/CashFlowTab.tsx
//
// Finance patch F13: cash flow statement, indirect method, worked out from the ledger. If the statement and the real
// movement of the cash and bank accounts disagree, it says so rather than hiding it.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { AlertCircle, CheckCircle } from "lucide-react"
import { apiClient } from "../../api/client"
import { lastMonthEnd, monthStart } from "./monthEnd.logic"

interface Line { label: string; amount: number }
interface CashFlow {
  fromDate: string; toDate: string; netProfit: number
  operating: Line[]; operatingTotal: number; investing: Line[]; investingTotal: number; financing: Line[]; financingTotal: number
  netChange: number; openingCash: number; closingCash: number; difference: number; agrees: boolean
}

const fmtR = (n: number) => `${n < 0 ? "-" : ""}R ${Math.abs(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 18, marginBottom: 16 }
const input: React.CSSProperties = { display: "block", marginTop: 4, padding: "8px 10px", border: "1.5px solid var(--hf-border)",
  borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" }

export default function CashFlowTab() {
  const end = lastMonthEnd()
  const [from, setFrom] = useState(monthStart(end))
  const [to, setTo] = useState(end)
  const { data, isLoading, isError } = useQuery<CashFlow>({
    queryKey: ["cash-flow", from, to], enabled: !!from && !!to && from <= to,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/accounting/reports/cash-flow?from=${from}&to=${to}`); return r.data?.data ?? r.data },
  })

  const section = (title: string, lines: Line[], total: number, id: string) => (
    <div style={card}>
      <h3 style={{ margin: "0 0 8px" }}>{title}</h3>
      {lines.length === 0 ? <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-secondary)" }}>Nothing in this period.</p> :
        lines.map((l, i) => <div key={i} style={{ display: "flex", justifyContent: "space-between", fontSize: 13, padding: "4px 0" }}><span>{l.label}</span><span>{fmtR(l.amount)}</span></div>)}
      <div data-testid={id} style={{ display: "flex", justifyContent: "space-between", fontWeight: 700, borderTop: "1px solid var(--hf-border)", marginTop: 6, paddingTop: 6 }}>
        <span>Total</span><span>{fmtR(total)}</span></div>
    </div>
  )

  return (
    <div style={{ maxWidth: 760 }}>
      <h2 style={{ margin: "0 0 4px" }}>Cash flow</h2>
      <p style={{ margin: "0 0 16px", color: "var(--hf-text-secondary)", fontSize: 13 }}>Where cash came from and went, from profit and the changes in debtors, creditors, assets, loans and equity.</p>
      <div style={{ ...card, display: "flex", gap: 12, flexWrap: "wrap" }}>
        <label style={{ fontSize: 12, fontWeight: 600 }}>From<input aria-label="From" type="date" style={input} value={from} onChange={e => setFrom(e.target.value)} /></label>
        <label style={{ fontSize: 12, fontWeight: 600 }}>To<input aria-label="To" type="date" style={input} value={to} onChange={e => setTo(e.target.value)} /></label>
      </div>
      {from > to && <p role="alert" style={{ color: "#dc2626" }}>The start date is after the end date.</p>}
      {isLoading && <p>Loading…</p>}
      {isError && <p role="alert" style={{ color: "#dc2626" }}>Could not load the cash flow statement.</p>}
      {data && (
        <>
          {section("Operating activities", data.operating, data.operatingTotal, "operating-total")}
          {section("Investing activities", data.investing, data.investingTotal, "investing-total")}
          {section("Financing activities", data.financing, data.financingTotal, "financing-total")}
          <div style={card}>
            <div style={{ display: "flex", justifyContent: "space-between" }}><span>Cash at start</span><span data-testid="opening">{fmtR(data.openingCash)}</span></div>
            <div style={{ display: "flex", justifyContent: "space-between" }}><span>Net change</span><span data-testid="net-change">{fmtR(data.netChange)}</span></div>
            <div style={{ display: "flex", justifyContent: "space-between", fontWeight: 700 }}><span>Cash at end</span><span data-testid="closing">{fmtR(data.closingCash)}</span></div>
            {data.agrees
              ? <p style={{ margin: "10px 0 0", fontSize: 13, color: "#16a34a" }}><CheckCircle size={13} /> Agrees with the cash and bank accounts.</p>
              : <p data-testid="disagree" role="alert" style={{ margin: "10px 0 0", fontSize: 13, color: "#dc2626" }}><AlertCircle size={13} /> The statement is {fmtR(Math.abs(data.difference))} away from the cash and bank accounts. A journal may be out of balance or an account may be classified wrongly.</p>}
          </div>
        </>
      )}
    </div>
  )
}
