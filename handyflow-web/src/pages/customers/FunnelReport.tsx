// src/pages/customers/FunnelReport.tsx
// Where leads stop: how many reached each stage, the share that moved on, and how long they stayed. The numbers come from the server.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { STAGE_LABEL, barShare, biggestDropOff, daysText, money, percent, reasonLabel, type Funnel, type LostReasonRow, sourceLabel, type SourceRow } from "./pipeline.logic"
import { Loading, ErrorNote } from "./CrmKit"

const unwrap = (r: { data: unknown }): Funnel | null => { const o = r.data as { data?: unknown } | null; return ((o && typeof o === "object" && "data" in o ? o.data : r.data) as Funnel | null) ?? null }

export default function FunnelReport() {
  const { data, isLoading, isError } = useQuery<Funnel | null>({ queryKey: ["crm", "funnel"], queryFn: async () => unwrap(await apiClient.get("/api/v1/crm/reports/funnel")) })
  const lost = useQuery<LostReasonRow[]>({ queryKey: ["crm", "lost-reasons"], queryFn: async () => { const r = await apiClient.get("/api/v1/crm/reports/lost-reasons"); const o = r.data as { data?: unknown } | null; const p = o && !Array.isArray(o) && typeof o === "object" && "data" in o ? o.data : r.data; return Array.isArray(p) ? (p as LostReasonRow[]) : [] } })
  const sources = useQuery<SourceRow[]>({ queryKey: ["crm", "sources"], queryFn: async () => { const r = await apiClient.get("/api/v1/crm/reports/sources"); const o = r.data as { data?: unknown } | null; const p = o && !Array.isArray(o) && typeof o === "object" && "data" in o ? o.data : r.data; return Array.isArray(p) ? (p as SourceRow[]) : [] } })
  if (isLoading) return <Loading />
  if (isError || !data) return <ErrorNote>Couldn't load the funnel report.</ErrorNote>
  if (data.totalLeads === 0) return <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No leads yet, so there is nothing to report. Add leads and move them through the pipeline.</p>
  const drop = biggestDropOff(data.stages)
  const tile = (label: string, value: string) => <div aria-label={label} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "8px 14px", minWidth: 110 }}><div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{label}</div><div style={{ fontSize: 20, fontWeight: 700 }}>{value}</div></div>
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 14 }}>
        {tile("Total leads", String(data.totalLeads))}{tile("Still open", String(data.totalStillOpen))}{tile("Won", String(data.totalWon))}{tile("Lost", String(data.totalLost))}{tile("Win rate", percent(data.overallConversionRate))}
      </div>
      {drop && <p role="note" style={{ fontSize: 13, margin: "0 0 10px" }}>Most leads stop between <strong>{STAGE_LABEL[drop.from]}</strong> and <strong>{STAGE_LABEL[drop.to]}</strong>: {drop.lost} {drop.lost === 1 ? "lead" : "leads"} did not make that step.</p>}
      <table aria-label="Funnel by stage" style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
        <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Stage</th><th style={{ width: "40%" }}>Leads that reached it</th><th>Moved on from previous</th><th>Average time in stage</th></tr></thead>
        <tbody>{data.stages.map(s => (
          <tr key={s.stage} aria-label={STAGE_LABEL[s.stage]} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
            <td style={{ padding: "6px 0" }}>{STAGE_LABEL[s.stage]}</td>
            <td><div style={{ display: "flex", alignItems: "center", gap: 8 }}><div role="img" aria-label={`${barShare(data.stages, s)}% of the widest stage`} style={{ height: 10, borderRadius: 5, width: `${barShare(data.stages, s)}%`, minWidth: s.reachedCount > 0 ? 4 : 0, background: s.stage === "LOST" ? "var(--hf-danger-text)" : s.stage === "WON" ? "var(--hf-success-text)" : "var(--hf-primary-text)" }} /><span>{s.reachedCount}</span></div></td>
            <td>{percent(s.conversionFromPreviousStage)}</td><td>{daysText(s.avgDaysInStage)}</td>
          </tr>))}</tbody>
      </table>
      <h4 style={{ margin: "16px 0 6px" }}>Why leads were lost</h4>
      {lost.isError ? <ErrorNote>Couldn't load the lost-deal reasons.</ErrorNote>
        : (lost.data ?? []).length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>No lost leads yet.</p>
        : <table aria-label="Lost reasons" style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Reason</th><th>Leads</th><th>Deal value lost</th></tr></thead>
          <tbody>{(lost.data ?? []).map((r, i) => <tr key={i} aria-label={reasonLabel(r.reason)} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}><td style={{ padding: "6px 0" }}>{reasonLabel(r.reason)}</td><td>{r.count}</td><td>{money(r.dealValue ?? 0)}</td></tr>)}</tbody></table>}
      <h4 style={{ margin: "16px 0 6px" }}>Where leads came from</h4>
      {sources.isError ? <ErrorNote>Couldn't load the lead sources.</ErrorNote>
        : (sources.data ?? []).length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>No leads yet.</p>
        : <table aria-label="Lead sources" style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Source</th><th>Leads</th><th>Won</th><th>Lost</th><th>Open</th><th>Win rate</th><th>Value won</th></tr></thead>
          <tbody>{(sources.data ?? []).map((r, i) => <tr key={i} aria-label={`Source ${sourceLabel(r.source)}`} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}><td style={{ padding: "6px 0" }}>{sourceLabel(r.source)}</td><td>{r.leads}</td><td>{r.won}</td><td>{r.lost}</td><td>{r.open}</td><td>{r.winRate == null ? "—" : `${r.winRate}%`}</td><td>{money(r.wonValue ?? 0)}</td></tr>)}</tbody></table>}
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>Counts are for leads currently in the pipeline. Won and lost leads stay in the figures.</p>
    </div>
  )
}
