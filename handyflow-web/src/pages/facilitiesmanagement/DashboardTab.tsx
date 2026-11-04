// The provider at a glance: what needs someone now, today's bookings, open work by stage, quotes waiting, and the money.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { Btn, Empty, Pill, SectionTitle, Stat, card, muted, money, pageOf } from "./kit"
import { BASE, useLookups } from "./lookups"
import { PRIORITIES, PRIORITY_TONE, attentionCounts, attentionList, barShare, complianceTone, labelOf, pct, slaNote, statusRows, todaysJobs, hoursText } from "./fm.logic"
import type { WorkOrder } from "./WorkOrdersTab"

interface Dash { workOrders: { open: number; unassigned: number; onHold: number; overdue: number; awaitingApproval: number; awaitingVerification: number; slaAtRisk: number; slaBreached: number; openByStatus: Record<string, number>; completedLast30Days: number; avgResolutionHoursLast30Days: number | null; resolutionSlaComplianceLast30Days: number | null }; finance: null | { outstandingReceivables: number; overdueReceivables: number; overdueInvoices: number; invoicedThisMonth: number; draftInvoices: number; approvedNotSent: number; creditNotesAwaitingApproval: number; unbilledCompletedWork: number } }
const grid: React.CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 16 }
/** The stages a person has to act on or watch; each is one small request so the lists stay current without paging through everything. */
const STAGES = ["REQUESTED", "OPEN", "ASSIGNED", "IN_PROGRESS", "PENDING_VERIFICATION"]

export default function DashboardTab({ goTo }: { goTo?: (section: string) => void }) {
  const lookups = useLookups(), now = new Date(), today = new Date(now.getTime() - now.getTimezoneOffset() * 6e4).toISOString().slice(0, 10)
  const canQuotes = ["READ", "MANAGE", "ADMIN"].map(p => usePermission(`FACILITIESMANAGEMENT_${p}`)).some(Boolean)
  const dash = useQuery({ queryKey: ["fm-rep-dash"], queryFn: async () => (await apiClient.get<Dash>(`${BASE}/reports/dashboard`)).data })
  const jobs = useQuery({ queryKey: ["fm-dash-jobs"], queryFn: async () => {
    const all = await Promise.all(STAGES.map(async status => pageOf<WorkOrder>(await apiClient.get(`${BASE}/work-orders`, { params: { status, size: 100 } })).content))
    return all.flat()
  } })
  const sent = useQuery({ enabled: canQuotes, queryKey: ["fm-dash-quotes"], queryFn: async () => pageOf<unknown>(await apiClient.get(`${BASE}/quotes`, { params: { status: "SENT", size: 1 } })).totalElements })
  if (dash.isError || jobs.isError) return <Empty>Could not load the dashboard. Please try again.</Empty>
  if (!dash.data || !jobs.data) return <Empty>Loading…</Empty>
  const w = dash.data.workOrders, f = dash.data.finance, all = jobs.data
  const items = attentionList(all, now, 10), chips = attentionCounts(attentionList(all, now, 1000)), booked = todaysJobs(all, today)
  const stages = statusRows(w.openByStatus), max = Math.max(0, ...stages.map(r => r.count)), level = complianceTone(w.resolutionSlaComplianceLast30Days)
  return (
    <div>
      <SectionTitle right={<Btn onClick={() => goTo?.("work-orders")}>All work orders</Btn>}>Dashboard</SectionTitle>
      <div style={{ ...card, ...grid, marginBottom: 12 }}>
        <Stat label="Open jobs" value={w.open} sub={`${w.unassigned} not yet assigned`} />
        <Stat label="Overdue" value={w.overdue} tone={w.overdue ? "bad" : undefined} sub={`${w.slaAtRisk} at risk · ${w.slaBreached} missed`} />
        <Stat label="Booked today" value={booked.length} sub={booked.length ? "Still to be done" : "Nothing booked"} />
        {canQuotes && <Stat label="Quotes with clients" value={sent.data ?? "—"} sub="Sent, waiting for an answer" />}
        <Stat label="Fixed on time, 30 days" value={pct(w.resolutionSlaComplianceLast30Days)} tone={level === "success" ? "good" : level === "danger" ? "bad" : undefined} sub={`${w.completedLast30Days} done · ${hoursText(w.avgResolutionHoursLast30Days)} on average`} />
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 12, marginBottom: 12 }}>
        <div style={card}>
          <SectionTitle>Needs attention</SectionTitle>
          {chips.length > 0 && <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 8 }}>{chips.map(c => <Pill key={c.reason} tone="neutral">{c.reason}: {c.count}</Pill>)}</div>}
          {items.length === 0 ? <div style={muted}>Nothing needs attention right now.</div> : items.map(i => {
            const sla = slaNote(i.job.resolutionDueAt, i.job.resolutionSla, false, now)
            return (
              <div key={i.job.id} style={{ display: "flex", justifyContent: "space-between", gap: 8, padding: "7px 0", borderTop: "1px solid var(--hf-border)", fontSize: 13 }}>
                <div style={{ minWidth: 0 }}>
                  <b>{i.job.workOrderNumber}</b> <Pill tone={PRIORITY_TONE[i.job.priority] ?? "neutral"}>{labelOf(PRIORITIES, i.job.priority)}</Pill>
                  <div style={{ ...muted, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{lookups.clientName(i.job.clientId)} · {lookups.siteName(i.job.siteId)}</div>
                </div>
                <div style={{ textAlign: "right", flexShrink: 0 }}><Pill tone={i.tone}>{i.reason}</Pill>{sla && i.reason !== "Overdue" && <div style={muted}>{sla.text}</div>}</div>
              </div>)
          })}
        </div>
        <div style={card}>
          <SectionTitle>Booked today</SectionTitle>
          {booked.length === 0 ? <div style={muted}>Nothing booked for today.</div> : booked.slice(0, 10).map(j => (
            <div key={j.id} style={{ display: "flex", justifyContent: "space-between", gap: 8, padding: "7px 0", borderTop: "1px solid var(--hf-border)", fontSize: 13 }}>
              <div style={{ minWidth: 0 }}><b>{j.workOrderNumber}</b><div style={{ ...muted, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{j.description}</div></div>
              <div style={{ textAlign: "right", flexShrink: 0 }}>{j.technicianName || j.vendorName || <span style={muted}>Nobody yet</span>}<div style={muted}>{j.scheduledDate && j.scheduledDate.length > 10 ? new Date(j.scheduledDate).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" }) : "All day"}</div></div>
            </div>))}
        </div>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 12 }}>
        <div style={card}>
          <SectionTitle>Open work by stage</SectionTitle>
          {stages.length === 0 ? <div style={muted}>Nothing open.</div> : stages.map(r => (
            <div key={r.key} style={{ display: "grid", gridTemplateColumns: "110px 1fr 36px", gap: 8, alignItems: "center", margin: "5px 0", fontSize: 12 }}>
              <span>{r.label}</span><div style={{ background: "var(--hf-surface-sunken)", borderRadius: 4, height: 10 }}><div style={{ width: `${barShare(r.count, max)}%`, height: 10, borderRadius: 4, background: "var(--hf-primary)" }} /></div><b style={{ textAlign: "right" }}>{r.count}</b>
            </div>))}
        </div>
        <div style={card}>
          <SectionTitle right={f ? <Btn onClick={() => goTo?.("invoices")}>Invoices</Btn> : undefined}>Money</SectionTitle>
          {f ? <div style={grid}>
            <Stat label="Owed to you" value={money(f.outstandingReceivables)} tone={f.overdueInvoices ? "bad" : undefined} sub={f.overdueInvoices ? `${money(f.overdueReceivables)} overdue` : "Nothing overdue"} />
            <Stat label="Invoiced this month" value={money(f.invoicedThisMonth)} />
            <Stat label="Done, not invoiced" value={money(f.unbilledCompletedWork)} tone={f.unbilledCompletedWork > 0 ? "warn" : undefined} />
            <Stat label="Waiting on you" value={f.draftInvoices + f.approvedNotSent + f.creditNotesAwaitingApproval} sub={`${f.draftInvoices} drafts · ${f.approvedNotSent} to send`} />
          </div> : <div style={muted}>Money figures are only shown to people with billing or cost access.</div>}
        </div>
      </div>
    </div>
  )
}
