// src/pages/earthmoving/ProfitabilityTab.tsx
//
// Hire revenue against maintenance cost, per machine, for a period. Revenue is hire charges that have not been voided,
// counted in the period the hire starts (so it follows what was charged, not what was paid). Cost is the cost on service
// records performed in the period. It does not include fuel, operators, depreciation or finance costs. Amounts exclude VAT.
// Administrators only, because it is financial.
import { useState } from "react"
import { FileBarChart } from "lucide-react"
import { fmtCurrency2dp, toCsv } from "./shared/format"
import { lbl, inputStyle } from "./shared/Modal"
import { useEarthPerms } from "./shared/useEarthPerms"
import { useProfitability } from "./shared/hooks"
import type { ProfitRow } from "./shared/types"

const btn: React.CSSProperties = {
  padding: "6px 12px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-secondary)",
}
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "14px 18px", background: "var(--hf-surface)" }
const th: React.CSSProperties = { textAlign: "right", padding: "8px 10px", fontSize: 12, color: "var(--hf-text-muted)", fontWeight: 600 }
const td: React.CSSProperties = { textAlign: "right", padding: "8px 10px", fontSize: 13, color: "var(--hf-text)" }

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
const pct = (v: number | null) => (v == null ? "n/a" : `${v.toFixed(1)}%`)

export default function ProfitabilityTab() {
  const perms = useEarthPerms()
  const today = new Date()
  const [from, setFrom] = useState(iso(new Date(today.getFullYear(), 0, 1)))
  const [to, setTo] = useState(iso(today))
  const { data, isLoading, isError } = useProfitability(from, to, perms.canAdmin)

  if (!perms.canAdmin) {
    return <div style={{ ...card, color: "var(--hf-text-muted)" }}>The profitability report is financial, so it needs Earthmoving administrator access.</div>
  }

  const download = () => {
    if (!data) return
    const rows: (string | number | null)[][] = [["Machine", "Charges", "Revenue", "Invoiced", "Not invoiced", "Maintenance cost", "Margin", "Margin %"]]
    for (const r of [...data.machines, data.totals])
      rows.push([r.machine, r.charges, r.revenue, r.invoiced, r.notInvoiced, r.maintenanceCost, r.margin, r.marginPercent])
    const url = URL.createObjectURL(new Blob([toCsv(rows)], { type: "text/csv" }))
    const a = document.createElement("a")
    a.href = url; a.download = `earthmoving-profitability-${from}-to-${to}.csv`; a.click()
    URL.revokeObjectURL(url)
  }

  const tile = (label: string, value: string, bad = false) => (
    <div key={label} style={{ ...card, flex: 1, minWidth: 150 }}>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{label}</div>
      <div style={{ fontSize: 20, fontWeight: 800, color: bad ? "var(--hf-danger-text)" : "var(--hf-text)" }}>{value}</div>
    </div>
  )

  const renderRow = (r: ProfitRow, strong = false) => (
    <tr key={r.assetId ?? "total"} style={{ borderTop: "1px solid var(--hf-border)", fontWeight: strong ? 700 : 400 }}>
      <td style={{ ...td, textAlign: "left" }}>{r.machine}</td>
      <td style={td}>{r.charges}</td>
      <td style={td}>{fmtCurrency2dp(r.revenue)}</td>
      <td style={td}>{fmtCurrency2dp(r.notInvoiced)}</td>
      <td style={td}>{fmtCurrency2dp(r.maintenanceCost)}</td>
      <td style={{ ...td, color: r.margin < 0 ? "var(--hf-danger-text)" : "var(--hf-text)" }}>{fmtCurrency2dp(r.margin)}</td>
      <td style={td}>{pct(r.marginPercent)}</td>
    </tr>
  )

  return (
    <div>
      <div style={{ display: "flex", gap: 12, alignItems: "flex-end", flexWrap: "wrap", marginBottom: 14 }}>
        <div><label htmlFor="pf-from" style={lbl}>From</label>
          <input id="pf-from" type="date" value={from} onChange={e => setFrom(e.target.value)} style={inputStyle()} /></div>
        <div><label htmlFor="pf-to" style={lbl}>To</label>
          <input id="pf-to" type="date" value={to} onChange={e => setTo(e.target.value)} style={inputStyle()} /></div>
        <button style={btn} disabled={!data} onClick={download}>Download CSV</button>
      </div>
      {from > to && <div role="alert" style={{ ...card, color: "var(--hf-danger-text)" }}>The end date is before the start date.</div>}
      {isError && <div role="alert" style={{ ...card, color: "var(--hf-danger-text)" }}>Could not load the report. Choose a period of at most two years.</div>}
      {isLoading && <div style={{ color: "var(--hf-text-muted)" }}>Loading…</div>}
      {data && (
        <>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 14 }}>
            {tile("Hire revenue", fmtCurrency2dp(data.totals.revenue))}
            {tile("Maintenance cost", fmtCurrency2dp(data.totals.maintenanceCost))}
            {tile("Margin", fmtCurrency2dp(data.totals.margin), data.totals.margin < 0)}
            {tile("Margin %", pct(data.totals.marginPercent), data.totals.margin < 0)}
          </div>
          {data.machines.length === 0 ? (
            <div style={{ textAlign: "center", padding: "50px 20px", border: "1px dashed var(--hf-border)", borderRadius: 12, color: "var(--hf-text-faint)" }}>
              <FileBarChart size={34} style={{ marginBottom: 10, opacity: 0.4 }} />
              <div style={{ fontWeight: 600 }}>No hire charges or costed service records in this period</div>
            </div>
          ) : (
            <div style={{ ...card, padding: 0, overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr>
                  <th style={{ ...th, textAlign: "left" }}>Machine</th><th style={th}>Charges</th><th style={th}>Revenue</th>
                  <th style={th}>Not yet invoiced</th><th style={th}>Maintenance cost</th><th style={th}>Margin</th><th style={th}>Margin %</th>
                </tr></thead>
                <tbody>{data.machines.map(r => renderRow(r))}{renderRow(data.totals, true)}</tbody>
              </table>
            </div>
          )}
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 10 }}>
            Amounts exclude VAT. Revenue is hire charges not voided, counted in the period each hire starts, whether or not invoiced or paid.
            Cost is service records performed in the period. Fuel, operators, depreciation and finance costs are not included.
          </div>
        </>
      )}
    </div>
  )
}
