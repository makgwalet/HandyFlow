// src/pages/accounting/ConsolidationTab.tsx
//
// Finance F26: consolidated statements for the group: a column per company, an Elimination column, and the Consolidated total.
// Profit and loss for the chosen period; the balance sheet is as at the end date, with earlier profits shown in equity.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { cellText, linesOf, money, SECTIONS, yearStart, type GroupInfo, type Report } from "./group.logic"

// "No group" comes back as data: null, so a null payload must stay null instead of falling back to the whole response.
const unwrap = (r: any) => (r.data && typeof r.data === "object" && "data" in r.data ? r.data.data : r.data)
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14, overflowX: "auto" }
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 6, fontSize: 12, background: "var(--hf-surface)", color: "var(--hf-text)" }
const num: React.CSSProperties = { textAlign: "right", whiteSpace: "nowrap", padding: "3px 8px" }

export default function ConsolidationTab() {
  const today = new Date()
  const [from, setFrom] = useState(yearStart(today))
  const [to, setTo] = useState(today.toISOString().slice(0, 10))

  const { data: group, isLoading: loadingGroup } = useQuery<GroupInfo | null>({
    queryKey: ["acc-group"], queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/group")) ?? null,
  })
  const isParent = group?.myRole === "PARENT"
  const { data, isLoading, error } = useQuery<Report>({
    queryKey: ["acc-consolidation", from, to], enabled: isParent && !!from && !!to && to >= from,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/accounting/group/report?from=${from}&to=${to}`)),
  })

  if (loadingGroup) return <p style={{ fontSize: 13 }}>Loading…</p>
  if (!isParent) {
    return (
      <div>
        <h2 style={{ marginTop: 0 }}>Consolidation</h2>
        <p style={{ fontSize: 13 }}>Consolidated statements are available to the company that owns a group. Set one up under Company group.</p>
      </div>
    )
  }

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Consolidated statements — {group?.name}</h2>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 12, fontSize: 12 }}>
        <label>From <input aria-label="From" type="date" value={from} onChange={e => setFrom(e.target.value)} style={field} /></label>
        <label>To <input aria-label="To" type="date" value={to} onChange={e => setTo(e.target.value)} style={field} /></label>
      </div>
      {to < from && <p role="alert" style={{ fontSize: 12 }}>The period must end on or after it starts.</p>}
      {isLoading && <p style={{ fontSize: 13 }}>Loading…</p>}
      {error && <p role="alert" style={{ fontSize: 13 }}>The consolidation could not be run.</p>}
      {data && <ReportView r={data} />}
    </div>
  )
}

function ReportView({ r }: { r: Report }) {
  const cols = r.entities.length + 2
  return (
    <>
      {r.warnings.length > 0 && (
        <div role="alert" style={{ ...card, background: "var(--hf-warning-soft, #fffbeb)" }}>
          <b style={{ fontSize: 13 }}>Check before relying on this</b>
          <ul style={{ margin: "6px 0 0", paddingLeft: 18, fontSize: 12 }}>{r.warnings.map((w, i) => <li key={i}>{w}</li>)}</ul>
        </div>
      )}
      <div style={card}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
          <thead>
            <tr style={{ fontSize: 11, color: "var(--hf-text-muted)", textTransform: "uppercase" }}>
              <th style={{ textAlign: "left", padding: "3px 8px" }}>Account</th>
              {r.entities.map(e => <th key={e.tenantId} style={num}>{e.name}</th>)}
              <th style={num}>Eliminations</th><th style={num}>Consolidated</th>
            </tr>
          </thead>
          <tbody>
            {SECTIONS.map(s => {
              const rows = linesOf(r, s.type)
              if (rows.length === 0) return null
              return [
                <tr key={s.type + "h"}><td colSpan={cols + 0} style={{ fontWeight: 700, padding: "8px 8px 3px" }}>{s.title}</td></tr>,
                ...rows.map(l => (
                  <tr key={s.type + l.code} style={{ borderTop: "1px solid var(--hf-border)" }}>
                    <td style={{ padding: "3px 8px" }}><span style={{ fontFamily: "monospace" }}>{l.code}</span> {l.name}</td>
                    {l.amounts.map((a, i) => <td key={i} style={num}>{cellText(a)}</td>)}
                    <td style={num}>{cellText(l.elimination)}</td><td style={{ ...num, fontWeight: 600 }}>{cellText(l.consolidated)}</td>
                  </tr>)),
              ]
            })}
          </tbody>
        </table>
      </div>
      <div style={card}>
        <table style={{ fontSize: 13, borderCollapse: "collapse" }}><tbody>
          <tr><td style={{ padding: "2px 12px 2px 0" }}>Income</td><td style={num}>{money(r.income)}</td></tr>
          <tr><td>Expenses</td><td style={num}>{money(r.expenses)}</td></tr>
          <tr style={{ fontWeight: 700 }}><td>Profit for the period</td><td style={num}>{money(r.profit)}</td></tr>
          <tr><td style={{ paddingTop: 8 }}>Assets</td><td style={num}>{money(r.assets)}</td></tr>
          <tr><td>Liabilities</td><td style={num}>{money(r.liabilities)}</td></tr>
          <tr><td>Equity</td><td style={num}>{money(r.equity)}</td></tr>
        </tbody></table>
        {r.checks.length > 0 && <>
          <h3 style={{ fontSize: 13, marginBottom: 4 }}>Intercompany checks</h3>
          <table style={{ fontSize: 12, borderCollapse: "collapse" }}><tbody>{r.checks.map(c => (
            <tr key={c.name}><td style={{ padding: "2px 12px 2px 0" }}>{c.name}</td>
              <td style={{ fontWeight: 600, color: Math.abs(c.difference) < 0.005 ? undefined : "var(--hf-danger-text, #b91c1c)" }}>
                {Math.abs(c.difference) < 0.005 ? "Agrees" : `Differs by ${money(Math.abs(c.difference))}`}</td></tr>))}</tbody></table></>}
      </div>
    </>
  )
}
