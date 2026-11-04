// Reports: the live overview, service-level results, vendor and asset performance, and what each client earns.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { Btn, Empty, Input, Pick, Pill, SectionTitle, Stat, card, money, muted, td, th } from "./kit"
import { BASE, useLookups } from "./lookups"
import { PRIORITIES, barShare, complianceTone, defaultRange, hoursText, labelOf, marginTone, minutesToHours, pct, priorityRows, rangeProblem, statusRows } from "./fm.logic"

interface Dash { generatedAt: string; workOrders: { open: number; unassigned: number; onHold: number; overdue: number; awaitingApproval: number; awaitingVerification: number; slaAtRisk: number; slaBreached: number; openByStatus: Record<string, number>; openByPriority: Record<string, number>; completedLast30Days: number; avgResolutionHoursLast30Days: number | null; resolutionSlaComplianceLast30Days: number | null }; finance: null | { outstandingReceivables: number; overdueReceivables: number; overdueInvoices: number; invoicedThisMonth: number; draftInvoices: number; approvedNotSent: number; creditNotesAwaitingApproval: number; unbilledCompletedWork: number } }
interface Portfolio { workOrdersWithSla: number; responseCompliancePercent: number | null; resolutionCompliancePercent: number | null; clients: { clientId: string; clientName: string; workOrdersWithSla: number; responseMet: number; responseBreached: number; responseCompliancePercent: number | null; resolutionMet: number; resolutionBreached: number; resolutionCompliancePercent: number | null }[] }
interface SlaClient { workOrdersWithSla: number; responseMet: number; responseBreached: number; responseOpen: number; responseCompliancePercent: number | null; resolutionMet: number; resolutionBreached: number; resolutionOpen: number; resolutionCompliancePercent: number | null; byPriority: { priority: string; total: number; responseBreached: number; resolutionBreached: number }[] }
interface Vendors { vendors: { vendorId: string; vendorName: string; jobs: number; completed: number; cancelled: number; open: number; avgResolutionHours: number | null; reworkJobs: number; reworkRatePercent: number | null; slaMet: number; slaBreached: number; slaCompliancePercent: number | null; totalCost: number; avgCost: number | null }[] }
interface Assets { assets: { assetId: string; assetTag: string | null; assetName: string; failures: number; repeatFailures: number; downtimeMinutes: number; mttrHours: number | null; mtbfHours: number | null; topFailureCause: string | null; totalCost: number | null }[] }
interface Profit { invoicedRevenue: number; internalCost: number; grossMargin: number; marginPercent: number | null; unbilledBillableValue: number; completedWorkOrders: number }

const R = `${BASE}/reports`
const todayIso = () => new Date().toISOString().slice(0, 10)
type View = "overview" | "sla" | "vendors" | "assets" | "profit"

export default function ReportsTab() {
  const canProfit = ["ADMIN", "COST_READ", "BILLING_MANAGE", "BILLING_APPROVE"].map(p => usePermission(`FACILITIESMANAGEMENT_${p}`)).some(Boolean)
  const canReports = ["READ", "MANAGE", "ADMIN", "SLA_MANAGE", "CONTRACT_MANAGE"].map(p => usePermission(`FACILITIESMANAGEMENT_${p}`)).some(Boolean)
  const [view, setView] = useState<View>("overview"), [range, setRange] = useState(() => defaultRange(todayIso(), 90))
  const problem = rangeProblem(range.from, range.to)
  const tabs: [View, string][] = [["overview", "Overview"], ...(canReports ? [["sla", "Service levels"], ["vendors", "Vendors"], ["assets", "Assets"]] as [View, string][] : []), ...(canProfit ? [["profit", "Client profit"]] as [View, string][] : [])]
  return (
    <div>
      <SectionTitle>Reports</SectionTitle>
      <div role="tablist" style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 12 }}>
        {tabs.map(([k, l]) => <Btn key={k} kind={view === k ? "primary" : "secondary"} onClick={() => setView(k)}>{l}</Btn>)}
      </div>
      {view !== "overview" && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: "0 12px", maxWidth: 420 }}>
          <Input label="From" type="date" value={range.from} onChange={v => setRange(r => ({ ...r, from: v }))} />
          <Input label="To" type="date" value={range.to} onChange={v => setRange(r => ({ ...r, to: v }))} />
        </div>)}
      {view !== "overview" && problem && <div style={{ ...muted, marginBottom: 8 }}>{problem}</div>}
      {view === "overview" && <Overview />}
      {view === "sla" && !problem && <Sla from={range.from} to={range.to} />}
      {view === "vendors" && !problem && <VendorsReport from={range.from} to={range.to} />}
      {view === "assets" && !problem && <AssetsReport from={range.from} to={range.to} />}
      {view === "profit" && !problem && <ProfitReport from={range.from} to={range.to} />}
    </div>
  )
}

const grid: React.CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 16 }
function Bars({ rows }: { rows: { key: string; label: string; count: number }[] }) {
  const max = Math.max(0, ...rows.map(r => r.count))
  return <div>{rows.map(r => (
    <div key={r.key} style={{ display: "grid", gridTemplateColumns: "110px 1fr 36px", gap: 8, alignItems: "center", margin: "5px 0", fontSize: 12 }}>
      <span>{r.label}</span><div style={{ background: "var(--hf-surface-sunken)", borderRadius: 4, height: 10 }}><div style={{ width: `${barShare(r.count, max)}%`, height: 10, borderRadius: 4, background: "var(--hf-primary)" }} /></div><b style={{ textAlign: "right" }}>{r.count}</b>
    </div>))}</div>
}
const Loading = ({ q }: { q: { isLoading: boolean; isError: boolean } }) => q.isError ? <Empty>Could not load this report. Please try again.</Empty> : q.isLoading ? <Empty>Loading…</Empty> : null

function Overview() {
  const q = useQuery({ queryKey: ["fm-rep-dash"], queryFn: async () => (await apiClient.get<Dash>(`${R}/dashboard`)).data })
  const d = q.data
  if (!d) return <Loading q={q} />
  const w = d.workOrders, f = d.finance
  return (
    <div>
      <div style={{ ...card, ...grid, marginBottom: 12 }}>
        <Stat label="Open jobs" value={w.open} sub={`${w.unassigned} not yet assigned`} />
        <Stat label="Overdue" value={w.overdue} tone={w.overdue ? "bad" : undefined} />
        <Stat label="Service targets at risk" value={w.slaAtRisk} tone={w.slaAtRisk ? "warn" : undefined} sub={`${w.slaBreached} already missed`} />
        <Stat label="Completed, last 30 days" value={w.completedLast30Days} sub={`${hoursText(w.avgResolutionHoursLast30Days)} on average`} />
        <Stat label="Fixed on time, last 30 days" value={pct(w.resolutionSlaComplianceLast30Days)} tone={complianceTone(w.resolutionSlaComplianceLast30Days) === "success" ? "good" : complianceTone(w.resolutionSlaComplianceLast30Days) === "danger" ? "bad" : undefined} />
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 12, marginBottom: 12 }}>
        <div style={card}><SectionTitle>Open work by stage</SectionTitle>{statusRows(w.openByStatus).length === 0 ? <div style={muted}>Nothing open.</div> : <Bars rows={statusRows(w.openByStatus)} />}
          <div style={{ ...muted, marginTop: 8 }}>{w.awaitingApproval} awaiting approval · {w.awaitingVerification} awaiting a check · {w.onHold} on hold</div></div>
        <div style={card}><SectionTitle>Open work by priority</SectionTitle>{priorityRows(w.openByPriority).length === 0 ? <div style={muted}>Nothing open.</div> : <Bars rows={priorityRows(w.openByPriority)} />}</div>
      </div>
      {f ? (
        <div style={card}><SectionTitle>Money</SectionTitle>
          <div style={grid}>
            <Stat label="Owed to you" value={money(f.outstandingReceivables)} sub={f.overdueInvoices ? `${money(f.overdueReceivables)} overdue on ${f.overdueInvoices} invoice${f.overdueInvoices === 1 ? "" : "s"}` : "Nothing overdue"} tone={f.overdueInvoices ? "bad" : undefined} />
            <Stat label="Invoiced this month" value={money(f.invoicedThisMonth)} />
            <Stat label="Done but not yet invoiced" value={money(f.unbilledCompletedWork)} tone={f.unbilledCompletedWork > 0 ? "warn" : undefined} />
            <Stat label="Waiting on you" value={f.draftInvoices + f.approvedNotSent + f.creditNotesAwaitingApproval} sub={`${f.draftInvoices} drafts · ${f.approvedNotSent} to send · ${f.creditNotesAwaitingApproval} credit notes`} />
          </div></div>) : <div style={muted}>Money figures are only shown to people with billing or cost access.</div>}
    </div>
  )
}

function Level({ p }: { p: number | null }) { return <Pill tone={complianceTone(p)}>{pct(p)}</Pill> }

function Sla({ from, to }: { from: string; to: string }) {
  const lookups = useLookups(), [clientId, setClientId] = useState("")
  const q = useQuery({ queryKey: ["fm-rep-portfolio", from, to], queryFn: async () => (await apiClient.get<Portfolio>(`${R}/sla-portfolio`, { params: { from, to } })).data })
  const one = useQuery({ enabled: !!clientId, queryKey: ["fm-rep-sla", clientId, from, to], queryFn: async () => (await apiClient.get<SlaClient>(`${R}/sla-performance`, { params: { clientId, from, to } })).data })
  const p = q.data
  if (!p) return <Loading q={q} />
  return (
    <div style={{ marginTop: 8 }}>
      <div style={{ ...card, ...grid, marginBottom: 12 }}>
        <Stat label="Jobs with a promised time" value={p.workOrdersWithSla} />
        <Stat label="Replied in time" value={pct(p.responseCompliancePercent)} />
        <Stat label="Fixed in time" value={pct(p.resolutionCompliancePercent)} />
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto", marginBottom: 12 }}>
        {p.clients.length === 0 ? <Empty>No jobs with promised times in this period.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Client</th><th style={{ ...th, textAlign: "right" }}>Jobs</th><th style={th}>Replied in time</th><th style={th}>Fixed in time</th></tr></thead>
            <tbody>{p.clients.map(c => <tr key={c.clientId}><td style={td}><b>{c.clientName}</b></td><td style={{ ...td, textAlign: "right" }}>{c.workOrdersWithSla}</td>
              <td style={td}><Level p={c.responseCompliancePercent} /> <span style={muted}>{c.responseBreached} missed</span></td><td style={td}><Level p={c.resolutionCompliancePercent} /> <span style={muted}>{c.resolutionBreached} missed</span></td></tr>)}</tbody>
          </table>)}
      </div>
      <div style={{ maxWidth: 360 }}><Pick label="Look closer at one client" value={clientId} onChange={setClientId} blank="Choose a client…" options={lookups.clients.map(c => [c.id, c.tradingName] as const)} /></div>
      {clientId && one.data && (
        <div style={{ ...card, padding: 0, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Priority</th><th style={{ ...th, textAlign: "right" }}>Jobs</th><th style={{ ...th, textAlign: "right" }}>Reply missed</th><th style={{ ...th, textAlign: "right" }}>Fix missed</th></tr></thead>
            <tbody>{[...one.data.byPriority].sort((a, b) => PRIORITIES.findIndex(x => x[0] === b.priority) - PRIORITIES.findIndex(x => x[0] === a.priority)).map(r => <tr key={r.priority}><td style={td}>{labelOf(PRIORITIES, r.priority)}</td><td style={{ ...td, textAlign: "right" }}>{r.total}</td><td style={{ ...td, textAlign: "right" }}>{r.responseBreached}</td><td style={{ ...td, textAlign: "right" }}>{r.resolutionBreached}</td></tr>)}</tbody>
          </table>
          <div style={{ ...muted, padding: 10 }}>Still running: {one.data.responseOpen} awaiting a reply, {one.data.resolutionOpen} awaiting a fix.</div>
        </div>)}
      {clientId && one.isError && <Empty>Could not load this client's results.</Empty>}
    </div>
  )
}

function VendorsReport({ from, to }: { from: string; to: string }) {
  const q = useQuery({ queryKey: ["fm-rep-vendors", from, to], queryFn: async () => (await apiClient.get<Vendors>(`${R}/vendor-performance`, { params: { from, to } })).data })
  if (!q.data) return <Loading q={q} />
  const rows = q.data.vendors
  return (
    <div style={{ ...card, padding: 0, overflowX: "auto", marginTop: 8 }}>
      {rows.length === 0 ? <Empty>No vendor work in this period.</Empty> : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Vendor</th><th style={{ ...th, textAlign: "right" }}>Jobs</th><th style={th}>On time</th><th style={{ ...th, textAlign: "right" }}>Average time</th><th style={th}>Sent back</th><th style={{ ...th, textAlign: "right" }}>Cost</th></tr></thead>
          <tbody>{rows.map(v => <tr key={v.vendorId}><td style={td}><b>{v.vendorName}</b></td>
            <td style={{ ...td, textAlign: "right" }}>{v.jobs}<div style={muted}>{v.completed} done · {v.open} open</div></td>
            <td style={td}><Level p={v.slaCompliancePercent} /><div style={muted}>{v.slaBreached} missed</div></td>
            <td style={{ ...td, textAlign: "right" }}>{hoursText(v.avgResolutionHours)}</td>
            <td style={td}>{v.reworkJobs}<div style={muted}>{pct(v.reworkRatePercent)}</div></td>
            <td style={{ ...td, textAlign: "right" }}>{money(v.totalCost)}<div style={muted}>{money(v.avgCost)} a job</div></td></tr>)}</tbody>
        </table>)}
    </div>
  )
}

function AssetsReport({ from, to }: { from: string; to: string }) {
  const q = useQuery({ queryKey: ["fm-rep-assets", from, to], queryFn: async () => (await apiClient.get<Assets>(`${R}/asset-reliability`, { params: { from, to, limit: 25 } })).data })
  if (!q.data) return <Loading q={q} />
  const rows = q.data.assets, max = Math.max(0, ...rows.map(a => a.failures))
  return (
    <div style={{ ...card, padding: 0, overflowX: "auto", marginTop: 8 }}>
      {rows.length === 0 ? <Empty>No breakdowns in this period.</Empty> : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Asset</th><th style={th}>Breakdowns</th><th style={{ ...th, textAlign: "right" }}>Down for</th><th style={{ ...th, textAlign: "right" }}>Fix time</th><th style={{ ...th, textAlign: "right" }}>Between faults</th><th style={th}>Most common cause</th><th style={{ ...th, textAlign: "right" }}>Cost</th></tr></thead>
          <tbody>{rows.map(a => <tr key={a.assetId}><td style={td}><b>{a.assetName}</b><div style={muted}>{a.assetTag}</div></td>
            <td style={td}><div style={{ display: "flex", gap: 8, alignItems: "center" }}><div style={{ width: 70, background: "var(--hf-surface-sunken)", borderRadius: 4, height: 8 }}><div style={{ width: `${barShare(a.failures, max)}%`, height: 8, borderRadius: 4, background: a.repeatFailures ? "var(--hf-danger-text)" : "var(--hf-primary)" }} /></div><b>{a.failures}</b></div>{a.repeatFailures > 0 && <div style={muted}>{a.repeatFailures} repeat</div>}</td>
            <td style={{ ...td, textAlign: "right" }}>{minutesToHours(a.downtimeMinutes)}</td><td style={{ ...td, textAlign: "right" }}>{hoursText(a.mttrHours)}</td><td style={{ ...td, textAlign: "right" }}>{hoursText(a.mtbfHours)}</td>
            <td style={td}>{a.topFailureCause || "—"}</td><td style={{ ...td, textAlign: "right" }}>{a.totalCost == null ? "—" : money(a.totalCost)}</td></tr>)}</tbody>
        </table>)}
    </div>
  )
}

function ProfitReport({ from, to }: { from: string; to: string }) {
  const lookups = useLookups(), [clientId, setClientId] = useState("")
  const q = useQuery({ enabled: !!clientId, queryKey: ["fm-rep-profit", clientId, from, to], queryFn: async () => (await apiClient.get<Profit>(`${R}/client-profitability`, { params: { clientId, from, to } })).data })
  const p = q.data
  return (
    <div style={{ marginTop: 8 }}>
      <div style={{ maxWidth: 360 }}><Pick label="Client" value={clientId} onChange={setClientId} blank="Choose a client…" options={lookups.clients.map(c => [c.id, c.tradingName] as const)} /></div>
      {!clientId ? <div style={card}><Empty>Choose a client to see what they earn you.</Empty></div> : q.isError ? <Empty>Could not load this report.</Empty> : !p ? <Empty>Loading…</Empty> : (
        <div style={{ ...card, ...grid }}>
          <Stat label="Invoiced" value={money(p.invoicedRevenue)} />
          <Stat label="What the work cost you" value={money(p.internalCost)} />
          <Stat label="Margin" value={money(p.grossMargin)} sub={pct(p.marginPercent)} tone={marginTone(p.marginPercent) === "danger" ? "bad" : "good"} />
          <Stat label="Done, not yet invoiced" value={money(p.unbilledBillableValue)} tone={p.unbilledBillableValue > 0 ? "warn" : undefined} />
          <Stat label="Jobs completed" value={p.completedWorkOrders} />
        </div>)}
    </div>
  )
}
