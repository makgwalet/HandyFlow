// src/pages/accountant/WorkloadTab.tsx
// Staff workload, work in progress (time not yet billed) and billed-against-time per client (finance F22).
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { fmtR, overBudget, realisationText } from "./practice.logic"

const unwrap = (r: any) => r.data?.data ?? r.data
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14 }
const th: React.CSSProperties = { textAlign: "right", fontWeight: 600, color: "var(--hf-text-secondary)", padding: "4px 6px" }
const td: React.CSSProperties = { textAlign: "right", padding: "5px 6px", borderTop: "1px solid var(--hf-border)" }
const inp: React.CSSProperties = { padding: "6px 8px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 12, background: "var(--hf-surface)", color: "var(--hf-text)" }
const iso = (d: Date) => d.toISOString().slice(0, 10)

export default function WorkloadTab() {
  const base = "/api/v1/accountant"
  const now = new Date()
  const [from, setFrom] = useState(iso(new Date(now.getFullYear(), now.getMonth() - 2, 1)))
  const [to, setTo] = useState(iso(now))
  const { data: staff = [] } = useQuery<any[]>({ queryKey: ["acc-workload"], queryFn: async () => unwrap(await apiClient.get(`${base}/workload`)) ?? [] })
  const { data: wip } = useQuery<any>({ queryKey: ["acc-wip"], queryFn: async () => unwrap(await apiClient.get(`${base}/wip`)) })
  const { data: profit } = useQuery<any>({
    queryKey: ["acc-profit", from, to], enabled: !!from && !!to && from <= to,
    queryFn: async () => unwrap(await apiClient.get(`${base}/profitability?from=${from}&to=${to}`)),
  })

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Workload and work in progress</h2>

      <div style={{ ...card, overflowX: "auto" }}>
        <h3 style={{ marginTop: 0 }}>Staff</h3>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
          <thead><tr><th style={{ ...th, textAlign: "left" }}>Person</th><th style={th}>Clients (prepare / review / partner)</th><th style={th}>Deadlines, 30 days</th><th style={th}>Overdue</th><th style={th}>Hours, 30 days</th><th style={th}>Unbilled time</th><th style={th}>Services due</th></tr></thead>
          <tbody>{staff.map(s => (
            <tr key={s.userId}>
              <td style={{ ...td, textAlign: "left" }}>{s.name}</td>
              <td style={td}>{s.clientsPrepared} / {s.clientsReviewed} / {s.clientsPartner}</td>
              <td style={td}>{s.deadlinesNext30Days}</td>
              <td style={{ ...td, color: s.deadlinesOverdue ? "var(--hf-danger-text, #b91c1c)" : undefined, fontWeight: s.deadlinesOverdue ? 700 : 400 }}>{s.deadlinesOverdue}</td>
              <td style={td}>{s.hoursLast30Days}</td>
              <td style={td}>{s.unbilledHours} h · {fmtR(s.unbilledValue)}</td>
              <td style={td}>{s.serviceRunsDue}</td>
            </tr>))}</tbody>
        </table>
        {staff.length === 0 && <p style={{ fontSize: 13 }}>No staff assigned to clients yet.</p>}
        <p style={{ fontSize: 11, color: "var(--hf-text-secondary)", marginBottom: 0 }}>Deadlines are counted for the clients a person prepares. Without all-clients access you see only your own row.</p>
      </div>

      {wip && (
        <div style={{ ...card, overflowX: "auto" }}>
          <h3 style={{ marginTop: 0 }}>Work in progress: {fmtR(wip.totalValue)} <span style={{ fontSize: 12, fontWeight: 400 }}>({fmtR(wip.totalOver60)} older than 60 days)</span></h3>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={{ ...th, textAlign: "left" }}>Client</th><th style={th}>Hours</th><th style={th}>0–30 days</th><th style={th}>31–60</th><th style={th}>61–90</th><th style={th}>Over 90</th><th style={th}>Value</th></tr></thead>
            <tbody>{wip.rows.map((r: any) => (
              <tr key={r.clientId}><td style={{ ...td, textAlign: "left" }}>{r.clientName}</td><td style={td}>{r.unbilledHours}</td><td style={td}>{fmtR(r.current)}</td>
                <td style={td}>{fmtR(r.days31to60)}</td><td style={td}>{fmtR(r.days61to90)}</td><td style={td}>{fmtR(r.over90)}</td><td style={{ ...td, fontWeight: 700 }}>{fmtR(r.unbilledValue)}</td></tr>))}</tbody>
          </table>
          {wip.rows.length === 0 && <p style={{ fontSize: 13 }}>No unbilled time.</p>}
        </div>
      )}

      <div style={{ ...card, overflowX: "auto" }}>
        <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 8 }}>
          <h3 style={{ margin: 0 }}>Billed against time</h3>
          <input aria-label="From" type="date" value={from} onChange={e => setFrom(e.target.value)} style={inp} />
          <input aria-label="To" type="date" value={to} onChange={e => setTo(e.target.value)} style={inp} />
        </div>
        {profit && (<>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={{ ...th, textAlign: "left" }}>Client</th><th style={th}>Hours</th><th style={th}>Budget used</th><th style={th}>Time at standard rates</th><th style={th}>Fees invoiced</th><th style={th}>Billed as</th></tr></thead>
            <tbody>{profit.rows.map((r: any) => (
              <tr key={r.clientId}><td style={{ ...td, textAlign: "left" }}>{r.clientName}</td><td style={td}>{r.hours}</td>
                <td style={{ ...td, color: overBudget(r.budgetUsedPercent) ? "var(--hf-danger-text, #b91c1c)" : undefined }}>{r.budgetUsedPercent == null ? "–" : `${r.budgetUsedPercent}% of ${r.budgetHours} h`}</td>
                <td style={td}>{fmtR(r.standardValue)}</td><td style={td}>{fmtR(r.billed)}</td><td style={td}>{realisationText(r.realisationPercent)}</td></tr>))}</tbody>
          </table>
          <p style={{ fontSize: 11, color: "var(--hf-text-secondary)", marginBottom: 0 }}>
            Fees invoiced in the period (excluding VAT, written-off notes left out) against billable hours at standard rates. A low percentage means time that was not recovered; it is not profit, because staff cost is not included.
          </p>
        </>)}
      </div>
    </div>
  )
}
