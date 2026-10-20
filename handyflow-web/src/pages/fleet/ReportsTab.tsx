// src/pages/fleet/ReportsTab.tsx
//
// Fleet reports: pick a report, pick a period where it has one, read the table, download it as CSV. The figures are the ones the rest of Fleet shows.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import type { FuelRow } from "./fuelAnalytics.logic"
import type { ListVehicle } from "./fleetBoards.logic"
import {
  REPORTS, PERIOD_LABEL, reportPeriod, costPerKmReport, fuelReport, maintenanceReport, complianceReport, serviceDueReport, driverScoresReport, cellText, toCsv, csvFileName,
  type ReportId, type ReportTable, type PeriodKind, type CostRow, type ReportOrder, type ScoreLine,
} from "./reports.logic"

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const list = <T,>(r: { data: unknown }): T[] => { const p = unwrap<T[] | { content?: T[] } | null>(r); return (Array.isArray(p) ? p : p?.content) ?? [] }
const th: React.CSSProperties = { textAlign: "left", padding: "6px 10px", fontSize: 11, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, color: "var(--hf-text-faint)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "7px 10px", fontSize: 12.5, whiteSpace: "nowrap" }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }

async function build(id: ReportId, period: { from: string; to: string }): Promise<ReportTable> {
  const get = (url: string) => apiClient.get(url)
  switch (id) {
    case "cost-per-km": return costPerKmReport(list<CostRow>(await get("/api/v1/fleet/cost-summary")))
    case "fuel": return fuelReport(list<FuelRow>(await get(`/api/v1/fleet/fuel-analytics/vehicles?from=${period.from}&to=${period.to}`)))
    case "maintenance": return maintenanceReport(list<ReportOrder>(await get("/api/v1/fleet/work-orders?status=COMPLETED&size=500")), period.from, period.to)
    case "compliance": return complianceReport(list<ListVehicle>(await get("/api/v1/fleet/vehicles?size=200")))
    case "service-due": return serviceDueReport(list<ListVehicle>(await get("/api/v1/fleet/vehicles?size=200")))
    case "driver-scores": return driverScoresReport(list<ScoreLine>(await get("/api/v1/fleet/driver-scores")))
  }
}
function download(text: string, fileName: string) {
  const url = URL.createObjectURL(new Blob(["﻿", text], { type: "text/csv;charset=utf-8" })), a = document.createElement("a")
  a.href = url; a.download = fileName; document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url)
}

export default function ReportsTab() {
  const [id, setId] = useState<ReportId>("cost-per-km"), [kind, setKind] = useState<PeriodKind>("30")
  const meta = REPORTS.find(r => r.id === id) as (typeof REPORTS)[number], period = reportPeriod(kind)
  const { data, isLoading, isError, refetch } = useQuery<ReportTable>({ queryKey: ["fleet", "report", id, meta.period ? period.from : "", meta.period ? period.to : ""], queryFn: () => build(id, period) })
  return (
    <div style={{ display: "grid", gridTemplateColumns: "230px minmax(0,1fr)", gap: 16 }}>
      <nav aria-label="Reports" style={{ display: "flex", flexDirection: "column", gap: 6 }}>
        {REPORTS.map(r => (
          <button key={r.id} type="button" aria-pressed={r.id === id} onClick={() => setId(r.id)}
            style={{ textAlign: "left", padding: "9px 12px", borderRadius: 10, border: `1px solid ${r.id === id ? "var(--hf-brand)" : "var(--hf-border)"}`, background: r.id === id ? "var(--hf-surface-muted)" : "var(--hf-surface)", color: "var(--hf-text)", cursor: "pointer" }}>
            <strong style={{ fontSize: 13 }}>{r.title}</strong><div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{r.description}</div></button>))}
      </nav>
      <div style={{ minWidth: 0 }}>
        <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 10 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>{meta.title}</h3>
          {meta.period && <select aria-label="Report period" style={field} value={kind} onChange={e => setKind(e.target.value as PeriodKind)}>{(Object.keys(PERIOD_LABEL) as PeriodKind[]).map(k => <option key={k} value={k}>{PERIOD_LABEL[k]}</option>)}</select>}
          {meta.period && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }} aria-label="Period dates">{period.from} to {period.to}</span>}
          <span style={{ flex: 1 }} />
          <span aria-label="Row count" style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>{data ? `${data.rows.length} ${data.rows.length === 1 ? "row" : "rows"}` : ""}</span>
          <button type="button" disabled={!data || data.rows.length === 0} style={{ ...field, cursor: data && data.rows.length ? "pointer" : "not-allowed", fontWeight: 700 }} onClick={() => data && download(toCsv(data), csvFileName(id, meta.period ? period : null))}>Download CSV</button>
        </div>
        {isError && <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Couldn't build this report. <button type="button" onClick={() => refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit" }}>Try again</button></p>}
        {isLoading ? <p>Building the report…</p> : data && (data.rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>Nothing to report for this {meta.period ? "period" : "report"}.</p> : (
          <div style={{ overflowX: "auto" }}>
            <table aria-label={meta.title} style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr>{data.columns.map(c => <th key={c.key} style={{ ...th, textAlign: c.kind === "text" ? "left" : "right" }}>{c.label}</th>)}</tr></thead>
              <tbody>{data.rows.map((r, i) => <tr key={i} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>{data.columns.map(c => <td key={c.key} style={{ ...td, textAlign: c.kind === "text" ? "left" : "right" }}>{cellText(c.kind, r[c.key] ?? null)}</td>)}</tr>)}</tbody>
              {data.totals && <tfoot><tr aria-label="Totals" style={{ borderTop: "2px solid var(--hf-border)", fontWeight: 800 }}>{data.columns.map(c => <td key={c.key} style={{ ...td, textAlign: c.kind === "text" ? "left" : "right" }}>{data.totals?.[c.key] == null ? "" : cellText(c.kind, data.totals[c.key])}</td>)}</tr></tfoot>}
            </table>
          </div>))}
      </div>
    </div>)
}
