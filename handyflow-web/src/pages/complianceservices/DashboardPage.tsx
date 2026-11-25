// src/pages/complianceservices/DashboardPage.tsx
//
// The practice at a glance. It counts what is already recorded: nothing here is a score, a rating or a legal opinion, and every tile leads to the
// place where the work is done.
import { useQuery } from "@tanstack/react-query"
import { Link } from "react-router-dom"
import { apiClient } from "../../api/client"
import StatTile from "../../components/ui/StatTile"
import { ChartCard, ColumnAxis, ColumnChart, SegmentedBar, StageBars, type Row } from "./DashboardCharts"
import {
  QUOTE_STAGE_LABELS, TENDER_STAGE_LABELS, billingNote, clientStatusLabel, monthLabel, stageLabel, tiles, weekLabel, weeksNote, type Stage, type Summary,
} from "./dashboardRules"
import { linkFor, severityLabel, typeLabel } from "./queueRules"
import { formatMoney } from "./pricingRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const TONE: Record<string, string> = { HIGH: "var(--hf-danger-text, #c0392b)", MEDIUM: "var(--hf-warning-text, #8a5a00)", LOW: "var(--hf-text-muted)" }
const BASE = "/complianceservices"

const stageRows = (stages: Stage[], labels: Record<string, string>, money: boolean): Row[] =>
  stages.map(s => ({ label: stageLabel(labels, s.status), value: s.count, display: money && s.value > 0 ? `${s.count} · ${formatMoney(s.value)}` : String(s.count) }))

export default function DashboardPage() {
  const { data, isLoading, isError } = useQuery<Summary>({ queryKey: ["cs-dashboard"], queryFn: async () => unwrap<Summary>(await apiClient.get("/api/v1/compliance-services/dashboard")) })
  if (isError) return <div role="alert" style={{ fontSize: 13, color: TONE.HIGH }}>The dashboard could not be loaded.</div>
  if (isLoading || !data) return <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>

  const weeks: Row[] = data.deadlineWeeks.map((w, i) => ({ label: `Week of ${weekLabel(w.start)}`, value: w.count, display: String(w.count), late: false, extra: i === 0 ? "this week" : undefined }))
  const months: Row[] = data.billingForecast.map(m => ({ label: monthLabel(m.month), value: m.amount, display: formatMoney(m.amount), extra: `${m.count} bill${m.count === 1 ? "" : "s"}` }))
  const clients = data.clientsByStatus.map(c => ({ label: clientStatusLabel(c.status), count: c.count, color: c.status === "ACTIVE" ? "var(--hf-success-solid-strong)" : "var(--hf-text-faint)" }))

  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
        Where the practice stands on {data.generatedOn}. These are counts of what is recorded, not scores. Each tile opens the place where the work is done.
      </div>

      <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
        {tiles(data.kpis).map(t => (
          <Link key={t.key} to={`${BASE}/${t.to}`} style={{ textDecoration: "none", flex: "1 1 170px", display: "flex" }}>
            <StatTile label={t.label} value={t.value} hint={t.hint} tone={t.tone} />
          </Link>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 12 }}>
        <section style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "14px 16px", display: "grid", gap: 8, alignContent: "start" }}>
          <h3 style={{ margin: 0, fontSize: 14, fontWeight: 700 }}>Needs attention</h3>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{data.queue.total} in the work queue · {data.queue.high} urgent, {data.queue.medium} soon</div>
          {data.urgent.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Nothing needs attention right now.</div>}
          {data.urgent.map((i, n) => (
            <Link key={`${i.type}-${n}`} to={linkFor(i)} style={{ display: "grid", gap: 2, textDecoration: "none", color: "inherit", borderTop: n ? "1px solid var(--hf-border)" : "none", paddingTop: n ? 8 : 0 }}>
              <span style={{ fontSize: 13, fontWeight: 600 }}>{i.clientName ? `${i.clientName} · ` : ""}{i.title}</span>
              <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}><span style={{ color: TONE[i.severity] ?? TONE.LOW, fontWeight: 700 }}>{severityLabel(i.severity)}</span> · {typeLabel(i.type)} · {i.detail}</span>
            </Link>
          ))}
          {data.queue.total > data.urgent.length && <Link to={`${BASE}/queue`} style={{ fontSize: 12 }}>See the whole work queue</Link>}
        </section>

        <ChartCard title="Deadlines, next 8 weeks" note={weeksNote(data.deadlinesOverdue, data.deadlineWeeks)} rows={weeks} columns={["Week", "Deadlines"]}>
          <ColumnChart rows={weeks} label="Deadlines due in each of the next eight weeks" />
          <ColumnAxis labels={data.deadlineWeeks.map(w => weekLabel(w.start))} />
        </ChartCard>

        <ChartCard title="Billing forecast" note={billingNote(data.billingBehind, data.billingBehindCount)} rows={months} columns={["Month", "Scheduled bills"]}>
          <ColumnChart rows={months} label="Scheduled bills by month, before VAT" />
          <ColumnAxis labels={data.billingForecast.map(m => monthLabel(m.month))} />
        </ChartCard>

        <ChartCard title="Quotes" rows={stageRows(data.quotePipeline, QUOTE_STAGE_LABELS, true)} columns={["Stage", "Quotes · value"]}>
          {data.quotePipeline.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No quotes yet.</div> : <StageBars rows={stageRows(data.quotePipeline, QUOTE_STAGE_LABELS, true)} label="Quotes by stage" />}
        </ChartCard>

        <ChartCard title="Tenders" rows={stageRows(data.tenderPipeline, TENDER_STAGE_LABELS, true)} columns={["Stage", "Tenders · estimated value"]}>
          {data.tenderPipeline.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No tenders yet.</div> : <StageBars rows={stageRows(data.tenderPipeline, TENDER_STAGE_LABELS, true)} label="Tenders by stage" />}
        </ChartCard>

        <ChartCard title="Clients" rows={clients.map(c => ({ label: c.label, value: c.count, display: String(c.count) }))} columns={["Status", "Clients"]}>
          <SegmentedBar rows={clients} label="Clients by status" />
        </ChartCard>

        <section style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "14px 16px", display: "grid", gap: 8, alignContent: "start" }}>
          <h3 style={{ margin: 0, fontSize: 14, fontWeight: 700 }}>Coming up, next 30 days</h3>
          {data.upcoming.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No deadlines in the next 30 days.</div>}
          {data.upcoming.map((u, n) => (
            <Link key={`${u.clientId}-${u.dueDate}-${n}`} to={`${BASE}/clients/${u.clientId}?tab=deadlines`} style={{ display: "flex", gap: 8, textDecoration: "none", color: "inherit", fontSize: 13 }}>
              <span style={{ minWidth: 76, color: u.dueDate < data.generatedOn ? TONE.HIGH : "var(--hf-text-muted)", fontWeight: u.dueDate < data.generatedOn ? 700 : 400 }}>{u.dueDate}{u.dueDate < data.generatedOn ? " (overdue)" : ""}</span>
              <span style={{ flex: 1 }}>{u.clientName} · {u.title}</span>
            </Link>
          ))}
        </section>
      </div>
    </div>
  )
}
