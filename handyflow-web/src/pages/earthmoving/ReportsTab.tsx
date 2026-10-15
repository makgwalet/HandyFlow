// src/pages/earthmoving/ReportsTab.tsx
//
// The operational reports: hours worked per machine, hire revenue per customer, downtime and service compliance.
// The three that take a period share one shell (dates, tiles, table, CSV). Revenue by customer is financial, so it is
// for administrators only, like the profitability report.
import { useState } from "react"
import { fmtCurrency2dp, toCsv } from "./shared/format"
import { lbl, inputStyle } from "./shared/Modal"
import { useEarthPerms } from "./shared/useEarthPerms"
import { useMachineUtilisation, useCustomerRevenue, useDowntime, useAssets, usePlans } from "./shared/hooks"
import { complianceRows, complianceTotals } from "./shared/compliance"

const btn: React.CSSProperties = {
  padding: "6px 12px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-secondary)",
}
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "14px 18px", background: "var(--hf-surface)" }
const th: React.CSSProperties = { textAlign: "right", padding: "8px 10px", fontSize: 12, color: "var(--hf-text-muted)", fontWeight: 600 }
const td: React.CSSProperties = { textAlign: "right", padding: "8px 10px", fontSize: 13, color: "var(--hf-text)" }
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
const pct = (v: number | null) => (v == null ? "n/a" : `${v.toFixed(1)}%`)

type Cell = string | number | null
interface Table { head: string[]; rows: Cell[][]; total?: Cell[] }

function Shell({ id, title, note, useReport, tiles, table, empty, filename }: {
  id: string; title: string; note: string; filename: string; empty: string
  useReport: (from: string, to: string) => { data?: unknown; isLoading: boolean; isError: boolean }
  tiles: (data: never) => [string, string][]
  table: (data: never) => Table
}) {
  const today = new Date()
  const [from, setFrom] = useState(iso(new Date(today.getFullYear(), today.getMonth(), 1)))
  const [to, setTo] = useState(iso(today))
  const { data, isLoading, isError } = useReport(from, to)
  const t = data ? table(data as never) : null
  const download = () => {
    if (!t) return
    const url = URL.createObjectURL(new Blob([toCsv([t.head, ...t.rows, ...(t.total ? [t.total] : [])])], { type: "text/csv" }))
    const a = document.createElement("a")
    a.href = url; a.download = `earthmoving-${filename}-${from}-to-${to}.csv`; a.click()
    URL.revokeObjectURL(url)
  }
  const fmt = (c: Cell, j: number) => (c == null ? "n/a" : typeof c === "number" && t!.head[j].includes("R") ? fmtCurrency2dp(c) : String(c))
  return (
    <div aria-label={title}>
      <div style={{ display: "flex", gap: 12, alignItems: "flex-end", flexWrap: "wrap", marginBottom: 14 }}>
        <div><label htmlFor={`${id}-from`} style={lbl}>From</label>
          <input id={`${id}-from`} type="date" value={from} onChange={e => setFrom(e.target.value)} style={inputStyle()} /></div>
        <div><label htmlFor={`${id}-to`} style={lbl}>To</label>
          <input id={`${id}-to`} type="date" value={to} onChange={e => setTo(e.target.value)} style={inputStyle()} /></div>
        <button style={btn} disabled={!t} onClick={download}>Download CSV</button>
      </div>
      {from > to && <div role="alert" style={{ ...card, color: "var(--hf-danger-text)" }}>The end date is before the start date.</div>}
      {isError && <div role="alert" style={{ ...card, color: "var(--hf-danger-text)" }}>Could not load the report. Choose a period of at most two years.</div>}
      {isLoading && <div style={{ color: "var(--hf-text-muted)" }}>Loading…</div>}
      {data != null && t && (
        <>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 14 }}>
            {tiles(data as never).map(([label, value]) => (
              <div key={label} style={{ ...card, flex: 1, minWidth: 150 }}>
                <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{label}</div>
                <div style={{ fontSize: 20, fontWeight: 800, color: "var(--hf-text)" }}>{value}</div>
              </div>
            ))}
          </div>
          {t.rows.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed var(--hf-border)", borderRadius: 12, color: "var(--hf-text-faint)", fontWeight: 600 }}>{empty}</div>
          ) : (
            <div style={{ ...card, padding: 0, overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr>{t.head.map((h, j) => <th key={h} style={{ ...th, textAlign: j === 0 ? "left" : "right" }}>{h}</th>)}</tr></thead>
                <tbody>
                  {t.rows.map((r, i) => <tr key={i} style={{ borderTop: "1px solid var(--hf-border)" }}>{r.map((c, j) => <td key={j} style={{ ...td, textAlign: j === 0 ? "left" : "right" }}>{fmt(c, j)}</td>)}</tr>)}
                  {t.total && <tr style={{ borderTop: "1px solid var(--hf-border)", fontWeight: 700 }}>{t.total.map((c, j) => <td key={j} style={{ ...td, textAlign: j === 0 ? "left" : "right" }}>{fmt(c, j)}</td>)}</tr>}
                </tbody>
              </table>
            </div>
          )}
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 10 }}>{note}</div>
        </>
      )}
    </div>
  )
}

export function UtilisationReport() {
  return <Shell id="ut" title="Machine utilisation" filename="utilisation" empty="No hour-metered machines" useReport={useMachineUtilisation}
    note="Hours from completed operator shifts, counted on the day each shift started. Only machines on an hour meter are listed: a kilometre meter's distance is not hours. 'Days worked' is the share of days in the period with at least one shift."
    tiles={(d: import("./shared/types").MachineUtilisation) => [["Hours worked", `${d.totalHours} hrs`], ["Machines", String(d.machines.length)], ["Days in period", String(d.days)]] as [string, string][]}
    table={(d: import("./shared/types").MachineUtilisation) => ({ head: ["Machine", "Shifts", "Hours", "Days worked", "Days worked %"],
      rows: d.machines.map(r => [r.machine, r.shifts, r.hours, r.daysWorked, pct(r.daysWorkedPercent)]) })} />
}

export function CustomerRevenueReport() {
  const perms = useEarthPerms()
  if (!perms.canAdmin) return <div style={{ ...card, color: "var(--hf-text-muted)" }}>Revenue by customer is financial, so it needs Earthmoving administrator access.</div>
  return <Shell id="cr" title="Hire revenue by customer" filename="customer-revenue" empty="No hire charges in this period" useReport={useCustomerRevenue}
    note="Amounts exclude VAT. Hire charges not voided, counted in the period each hire starts, whether or not invoiced or paid."
    tiles={(d: import("./shared/types").CustomerRevenue) => [["Hire revenue", fmtCurrency2dp(d.totals.revenue)], ["Not yet invoiced", fmtCurrency2dp(d.totals.notInvoiced)], ["Customers", String(d.customers.length)]] as [string, string][]}
    table={(d: import("./shared/types").CustomerRevenue) => ({ head: ["Customer", "Charges", "Revenue (R)", "Not invoiced (R)", "Share"],
      rows: d.customers.map(r => [r.customer, r.charges, r.revenue, r.notInvoiced, pct(r.sharePercent)]),
      total: ["All customers", d.totals.charges, d.totals.revenue, d.totals.notInvoiced, "100.0%"] })} />
}

export function DowntimeReport() {
  return <Shell id="dt" title="Downtime" filename="downtime" empty="No machine was out of service in this period" useReport={useDowntime}
    note="Worked out from recorded status changes: time in breakdown and time in maintenance. A machine with no change recorded is taken as in service. A period that has not ended counts up to now."
    tiles={(d: import("./shared/types").Downtime) => [["Breakdowns", String(d.totals.breakdowns)], ["Breakdown hours", `${d.totals.breakdownHours} hrs`], ["Maintenance hours", `${d.totals.maintenanceHours} hrs`]] as [string, string][]}
    table={(d: import("./shared/types").Downtime) => ({ head: ["Machine", "Breakdowns", "Breakdown hrs", "Maintenance hrs", "Total hrs", "In service"],
      rows: d.machines.map(r => [r.machine, r.breakdowns, r.breakdownHours, r.maintenanceHours, r.totalHours, pct(r.availabilityPercent)]) })} />
}

export function ComplianceReport() {
  const { data: assets = [], isLoading } = useAssets()
  const { data: plans = [] } = usePlans()
  const rows = complianceRows(assets, plans)
  const totals = complianceTotals(rows)
  return (
    <div aria-label="Service compliance">
      {isLoading ? <div style={{ color: "var(--hf-text-muted)" }}>Loading…</div> : (
        <>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 14 }}>
            {[["Plans in order", pct(totals.inOrderPercent)], ["Overdue", String(totals.overdue)], ["Due now", String(totals.due)], ["Machines with plans", String(totals.machines)]].map(([l, v]) => (
              <div key={l} style={{ ...card, flex: 1, minWidth: 150 }}>
                <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{l}</div>
                <div style={{ fontSize: 20, fontWeight: 800, color: "var(--hf-text)" }}>{v}</div>
              </div>
            ))}
          </div>
          {rows.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed var(--hf-border)", borderRadius: 12, color: "var(--hf-text-faint)", fontWeight: 600 }}>No service plans set up yet</div>
          ) : (
            <div style={{ ...card, padding: 0, overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr>{["Machine", "Plans", "OK", "Due soon", "Due", "Overdue", "In order"].map((h, j) => <th key={h} style={{ ...th, textAlign: j === 0 ? "left" : "right" }}>{h}</th>)}</tr></thead>
                <tbody>{rows.map(r => (
                  <tr key={r.assetId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                    <td style={{ ...td, textAlign: "left" }}>{r.machine}</td><td style={td}>{r.plans}</td><td style={td}>{r.ok}</td><td style={td}>{r.dueSoon}</td>
                    <td style={td}>{r.due}</td><td style={{ ...td, color: r.overdue > 0 ? "var(--hf-danger-text)" : "var(--hf-text)" }}>{r.overdue}</td><td style={td}>{pct(r.inOrderPercent)}</td>
                  </tr>))}</tbody>
              </table>
            </div>
          )}
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 10 }}>
            How things stand now, not over a period. A plan is in order while it is OK or only due soon. Machines without an active plan are not listed.
          </div>
        </>
      )}
    </div>
  )
}
