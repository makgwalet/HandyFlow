// src/pages/supply-chain/AnalyticsTab.tsx
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { BarChart3 } from "lucide-react"
import { apiClient } from "../../api/client"
import { inp, TH, TD, ErrBox, Spinner, EmptyState, Banner, filterPill, fmtR } from "./scm.shared"
import { asOne, asList, errorText, FLAG_TEXT, pctText, barWidth, defaultRange, rangeProblem } from "./scmOps"

interface Amount { label: string; amount: number; sharePct: number | null }
interface SupplierSpend { supplierId: string; supplierName: string; invoiced: number; sharePct: number | null; invoices: number }
interface Spend { ordered: number; invoiced: number; paid: number; invoicedWithoutPo: number; withoutPoSharePct: number | null; creditsReceived: number; top5SupplierSharePct: number | null
  byMonth: Amount[]; bySupplier: SupplierSpend[]; byBudgetScope: Amount[] }
interface RfqSaving { rfqId: string; rfqNumber: string; title: string; estimate: number | null; awarded: number | null; highestBid: number | null; savedVsEstimate: number | null; savedVsHighestBid: number | null }
interface ContractLeak { orderNumber: string; itemName: string; qty: number; unitCost: number; agreedPrice: number; overpaid: number }
interface Savings { rfqsAwarded: number; rfqsWithEstimate: number; savedVsEstimate: number; savedVsHighestBid: number; rfqs: RfqSaving[]; paidAboveContract: number; linesAboveContract: number; contractLeaks: ContractLeak[]; creditsRecovered: number }
interface SupplierView { supplierId: string; supplierName: string; invoiced: number; spendSharePct: number | null; score: number | null; rating: string | null; onTimePct: number | null; rejectionPct: number | null; lowConfidence: boolean; flag: string | null }

const BASE = "/api/v1/supply-chain/analytics"
const Tile = ({ label, value, note }: { label: string; value: string; note?: string }) => (
  <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 14px", minWidth: 150 }}>
    <div style={{ fontSize: 12, opacity: 0.75 }}>{label}</div><div style={{ fontSize: 20, fontWeight: 700 }}>{value}</div>{note && <div style={{ fontSize: 11, opacity: 0.7 }}>{note}</div>}
  </div>
)
const Bar = ({ pct }: { pct: number | null }) => <div style={{ background: "var(--hf-surface-sunken)", borderRadius: 4, height: 8, width: 120 }}><div style={{ background: "var(--hf-info)", height: 8, borderRadius: 4, width: barWidth(pct) }} /></div>

export function AnalyticsTab() {
  const [range, setRange] = useState(defaultRange())
  const [view, setView] = useState<"spend" | "savings" | "suppliers">("spend")
  const problem = rangeProblem(range.from, range.to)
  const params = { from: range.from, to: range.to }

  const spend = useQuery({ queryKey: ["scm-an-spend", range], enabled: view === "spend" && !problem, queryFn: () => apiClient.get(`${BASE}/spend`, { params }).then(r => asOne<Spend>(r)) })
  const savings = useQuery({ queryKey: ["scm-an-savings", range], enabled: view === "savings" && !problem, queryFn: () => apiClient.get(`${BASE}/savings`, { params }).then(r => asOne<Savings>(r)) })
  const suppliers = useQuery({ queryKey: ["scm-an-suppliers"], enabled: view === "suppliers", queryFn: () => apiClient.get(`${BASE}/suppliers`, { params: { months: 12 } }).then(asList<SupplierView>) })

  const sp = spend.data, sv = savings.data

  return (
    <div style={{ padding: 20 }}>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 14 }}>
        {(["spend", "savings", "suppliers"] as const).map(v => <button key={v} style={filterPill(view === v)} onClick={() => setView(v)}>{v === "spend" ? "Spend" : v === "savings" ? "Savings" : "Supplier performance"}</button>)}
        <div style={{ flex: 1 }} />
        {view !== "suppliers" && <>
          <input type="date" style={{ ...inp, width: 150 }} aria-label="From" value={range.from} onChange={e => setRange({ ...range, from: e.target.value })} />
          <input type="date" style={{ ...inp, width: 150 }} aria-label="To" value={range.to} onChange={e => setRange({ ...range, to: e.target.value })} />
        </>}
      </div>
      {view !== "suppliers" && problem && <ErrBox msg={problem} />}

      {view === "spend" && (spend.isLoading ? <Spinner /> : spend.isError ? <ErrBox msg={errorText(spend.error, "Could not load spend")} /> : !sp ? null : (
        <>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 14 }}>
            <Tile label="Invoiced (approved or paid, ex VAT)" value={fmtR(sp.invoiced)} />
            <Tile label="Paid" value={fmtR(sp.paid)} />
            <Tile label="Ordered" value={fmtR(sp.ordered)} />
            <Tile label="Credits received" value={fmtR(sp.creditsReceived)} />
            <Tile label="Top 5 suppliers" value={pctText(sp.top5SupplierSharePct)} note="of spend" />
          </div>
          {sp.withoutPoSharePct != null && sp.withoutPoSharePct >= 20 && <Banner variant="warning">{pctText(sp.withoutPoSharePct)} of spend ({fmtR(sp.invoicedWithoutPo)}) was invoiced without a purchase order.</Banner>}
          <h4>By supplier</h4>
          {sp.bySupplier.length === 0 ? <EmptyState icon={BarChart3} title="No spend in this period" sub="Approved or paid supplier invoices appear here." /> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr>{["Supplier", "Invoices", "Spend", "Share", ""].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
              <tbody>{sp.bySupplier.map(s => <tr key={s.supplierId}><td style={TD}>{s.supplierName}</td><td style={TD}>{s.invoices}</td><td style={TD}>{fmtR(s.invoiced)}</td><td style={TD}>{pctText(s.sharePct)}</td><td style={TD}><Bar pct={s.sharePct} /></td></tr>)}</tbody>
            </table>
          )}
          <h4>By month</h4>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr>{["Month", "Spend", ""].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
            <tbody>{sp.byMonth.map(m => <tr key={m.label}><td style={TD}>{m.label}</td><td style={TD}>{fmtR(m.amount)}</td><td style={TD}><Bar pct={m.sharePct} /></td></tr>)}</tbody>
          </table>
          <h4>Charged to (committed on orders, plus no-PO invoices)</h4>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr>{["Department / cost centre / project", "Amount", "Share"].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
            <tbody>{sp.byBudgetScope.map(a => <tr key={a.label}><td style={TD}>{a.label}</td><td style={TD}>{fmtR(a.amount)}</td><td style={TD}>{pctText(a.sharePct)}</td></tr>)}</tbody>
          </table>
        </>
      ))}

      {view === "savings" && (savings.isLoading ? <Spinner /> : savings.isError ? <ErrBox msg={errorText(savings.error, "Could not load savings")} /> : !sv ? null : (
        <>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 14 }}>
            <Tile label="Saved vs estimate" value={fmtR(sv.savedVsEstimate)} note={`${sv.rfqsWithEstimate} of ${sv.rfqsAwarded} awarded RFQs had a full estimate`} />
            <Tile label="Saved vs highest bid" value={fmtR(sv.savedVsHighestBid)} />
            <Tile label="Paid above contract" value={fmtR(sv.paidAboveContract)} note={`${sv.linesAboveContract} order line(s)`} />
            <Tile label="Credits recovered" value={fmtR(sv.creditsRecovered)} />
          </div>
          <h4>Awarded RFQs</h4>
          {sv.rfqs.length === 0 ? <p>No RFQs were awarded in this period.</p> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr>{["RFQ", "Estimate", "Awarded", "Highest bid", "Saved vs estimate", "Saved vs highest"].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
              <tbody>{sv.rfqs.map(r => <tr key={r.rfqId}><td style={TD}>{r.rfqNumber} — {r.title}</td><td style={TD}>{r.estimate == null ? "—" : fmtR(r.estimate)}</td><td style={TD}>{r.awarded == null ? "—" : fmtR(r.awarded)}</td>
                <td style={TD}>{r.highestBid == null ? "—" : fmtR(r.highestBid)}</td><td style={TD}>{r.savedVsEstimate == null ? "—" : fmtR(r.savedVsEstimate)}</td><td style={TD}>{r.savedVsHighestBid == null ? "—" : fmtR(r.savedVsHighestBid)}</td></tr>)}</tbody>
            </table>
          )}
          <h4>Paid above the contract price</h4>
          {sv.contractLeaks.length === 0 ? <p>No order line was priced above a contract.</p> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr>{["Order", "Item", "Qty", "Paid", "Contract", "Over"].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
              <tbody>{sv.contractLeaks.map((l, i) => <tr key={i}><td style={TD}>{l.orderNumber}</td><td style={TD}>{l.itemName}</td><td style={TD}>{l.qty}</td><td style={TD}>{fmtR(l.unitCost)}</td><td style={TD}>{fmtR(l.agreedPrice)}</td><td style={TD}>{fmtR(l.overpaid)}</td></tr>)}</tbody>
            </table>
          )}
        </>
      ))}

      {view === "suppliers" && (suppliers.isLoading ? <Spinner /> : suppliers.isError ? <ErrBox msg={errorText(suppliers.error, "Could not load suppliers")} /> : (suppliers.data ?? []).length === 0 ? (
        <EmptyState icon={BarChart3} title="No suppliers" sub="Supplier performance appears once there are suppliers." />
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr>{["Supplier", "Spend (12 mo)", "Share", "Score", "On time", "Rejected", ""].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
          <tbody>{(suppliers.data ?? []).map(s => (
            <tr key={s.supplierId}>
              <td style={TD}>{s.supplierName}</td><td style={TD}>{fmtR(s.invoiced)}</td><td style={TD}>{pctText(s.spendSharePct)}</td>
              <td style={TD}>{s.score == null ? "—" : `${s.score}${s.lowConfidence ? " (few deliveries)" : ""}`}</td><td style={TD}>{pctText(s.onTimePct)}</td><td style={TD}>{pctText(s.rejectionPct)}</td>
              <td style={{ ...TD, color: s.flag ? "var(--hf-danger-text)" : undefined, fontWeight: s.flag ? 700 : 400 }}>{s.flag ? FLAG_TEXT[s.flag] ?? s.flag : ""}</td>
            </tr>))}</tbody>
        </table>
      ))}
    </div>
  )
}
