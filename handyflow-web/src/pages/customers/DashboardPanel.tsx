// src/pages/customers/DashboardPanel.tsx
// The CRM landing screen: headline numbers, the funnel, lead sources and what needs attention today.
// Everything is built from data the other CRM tabs already load; each block links to the tab that has the detail.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { attentionTasks, buildDashboard } from "./dashboard.logic"
import { STAGE_LABEL, barShare, localToday, money, sourceLabel, type Funnel, type PipelineLead, type SourceRow } from "./pipeline.logic"
import { dueText, type Task } from "./tasks.logic"

const inner = (r: { data: unknown }): unknown => { const o = r.data as { data?: unknown } | null; return o && !Array.isArray(o) && typeof o === "object" && "data" in o ? o.data : r.data }
const asList = <T,>(r: { data: unknown }, key?: string): T[] => { const d = inner(r) as Record<string, unknown> | T[] | null; const v = Array.isArray(d) ? d : key ? (d as Record<string, unknown> | null)?.[key] : null; return Array.isArray(v) ? (v as T[]) : [] }
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 12, padding: "12px 14px", background: "var(--hf-surface)" }
const link: React.CSSProperties = { background: "none", border: "none", padding: 0, cursor: "pointer", color: "var(--hf-primary-text)", fontWeight: 700, fontSize: 12.5 }

export default function DashboardPanel({ go }: { go: (tab: string) => void }) {
  const today = localToday()
  const customers = useQuery<number>({ queryKey: ["crm", "dashboard", "customer-count"], retry: false, queryFn: async () => Number((inner(await apiClient.get("/api/v1/crm/customers", { params: { page: 0, size: 1 } })) as { totalElements?: number } | null)?.totalElements ?? 0) })
  const leads = useQuery<PipelineLead[]>({ queryKey: ["crm", "pipeline"], queryFn: async () => asList<PipelineLead>(await apiClient.get("/api/v1/crm/customers/pipeline")) })
  // Own key: the task queue caches this URL as { tasks, truncated } under ["crm", "tasks", "open"].
  const tasks = useQuery<Task[]>({ queryKey: ["crm", "tasks", "dashboard-open"], queryFn: async () => asList<Task>(await apiClient.get("/api/v1/crm/tasks", { params: { done: false } }), "tasks") })
  const funnel = useQuery<Funnel | null>({ queryKey: ["crm", "funnel"], queryFn: async () => (inner(await apiClient.get("/api/v1/crm/reports/funnel")) as Funnel | null) ?? null })
  const sources = useQuery<SourceRow[]>({ queryKey: ["crm", "sources"], queryFn: async () => asList<SourceRow>(await apiClient.get("/api/v1/crm/reports/sources")) })
  if (leads.isLoading || tasks.isLoading) return <p>Loading…</p>
  if (leads.isError || tasks.isError || !leads.data || !tasks.data) return <div role="alert">Couldn't load the dashboard.</div>
  const f = buildDashboard(leads.data, tasks.data, today), attention = attentionTasks(tasks.data, today)
  const tile = (label: string, value: string, note: string, tab: string, tone?: string) => (
    <button type="button" key={label} aria-label={`${label}: ${value}`} onClick={() => go(tab)} style={{ ...card, textAlign: "left", cursor: "pointer", minWidth: 150, flex: "1 1 150px" }}>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 700, color: tone }}>{value}</div>
      <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{note}</div>
    </button>)
  const stages = funnel.data?.stages.filter(s => s.stage !== "LOST") ?? [], widest = funnel.data?.stages ?? []
  const maxSource = Math.max(1, ...(sources.data ?? []).map(s => s.leads))
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 14 }}>
        {tile("Customers", customers.isError || customers.data == null ? "—" : String(customers.data), "on the register", "customers")}
        {tile("Open deals", String(f.openDeals), money(f.openValue), "pipeline")}
        {tile("Expected to win", money(f.weighted), "open deals at stage odds", "forecast")}
        {tile("Won", money(f.wonValue), "all won deals", "funnel")}
        {tile("Late tasks", String(f.overdueTasks), f.escalatedTasks > 0 ? `${f.escalatedTasks} escalated` : `${f.dueToday} due today`, "tasks", f.overdueTasks > 0 ? "var(--hf-danger-text)" : undefined)}
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 12 }}>
        <section aria-label="Needs attention" style={card}>
          <div style={{ display: "flex", justifyContent: "space-between" }}><h4 style={{ margin: "0 0 8px" }}>Needs attention</h4><button type="button" style={link} onClick={() => go("tasks")}>All tasks</button></div>
          {attention.length === 0 && f.overdueDeals === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Nothing is late. </p> : <>
            {f.overdueDeals > 0 && <p role="note" style={{ fontSize: 13, margin: "0 0 6px", color: "var(--hf-danger-text)" }}>{f.overdueDeals} {f.overdueDeals === 1 ? "deal is" : "deals are"} past the expected close date.</p>}
            <ul style={{ listStyle: "none", margin: 0, padding: 0, fontSize: 13 }}>
              {attention.map(t => <li key={t.id} style={{ display: "flex", justifyContent: "space-between", gap: 8, padding: "4px 0", borderTop: "1px solid var(--hf-border-subtle)" }}><span>{t.customerName}</span><span style={{ color: "var(--hf-text-muted)" }}>{dueText(t, today)}</span></li>)}
            </ul></>}
        </section>
        <section aria-label="Sales funnel" style={card}>
          <div style={{ display: "flex", justifyContent: "space-between" }}><h4 style={{ margin: "0 0 8px" }}>Sales funnel</h4><button type="button" style={link} onClick={() => go("funnel")}>Full report</button></div>
          {funnel.isError ? <div role="alert" style={{ fontSize: 12.5 }}>Couldn't load the funnel.</div> : !funnel.data || funnel.data.totalLeads === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>No leads yet.</p> :
            stages.map(s => <div key={s.stage} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, padding: "3px 0" }}>
              <span style={{ width: 80 }}>{STAGE_LABEL[s.stage]}</span>
              <div role="img" aria-label={`${STAGE_LABEL[s.stage]}: ${s.reachedCount}`} style={{ height: 10, borderRadius: 5, background: "var(--hf-primary-text)", width: `${Math.round(barShare(widest, s) * 0.7)}%`, minWidth: s.reachedCount > 0 ? 4 : 0 }} /><span>{s.reachedCount}</span></div>)}
        </section>
        <section aria-label="Where leads come from" style={card}>
          <h4 style={{ margin: "0 0 8px" }}>Where leads come from</h4>
          {sources.isError ? <div role="alert" style={{ fontSize: 12.5 }}>Couldn't load the lead sources.</div> : (sources.data ?? []).length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>No leads yet.</p> :
            (sources.data ?? []).map((s, i) => <div key={i} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, padding: "3px 0" }}>
              <span style={{ width: 100 }}>{sourceLabel(s.source)}</span>
              <div role="img" aria-label={`${sourceLabel(s.source)}: ${s.leads}`} style={{ height: 10, borderRadius: 5, background: "var(--hf-success-text)", width: `${Math.round((s.leads / maxSource) * 60)}%`, minWidth: 4 }} /><span>{s.leads}</span></div>)}
        </section>
      </div>
    </div>
  )
}
