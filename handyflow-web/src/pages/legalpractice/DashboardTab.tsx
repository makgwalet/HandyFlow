// The firm at a glance: matters by status, what clients owe, money held in trust, and the deadlines coming up.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Empty, Pill, SectionTitle, Stat, card, day, muted, pageOf, td, th } from "./kit"
import { money } from "../insurance/parts"
import { BASE, useLookups } from "./lookups"
import { DATE_TYPES, MATTER_STATUS, dashboardStats, isoDay, keyDateNote, labelOf, upcomingDates } from "./legal.logic"
import type { Matter } from "./MattersTab"
import type { Invoice } from "./InvoicesTab"
import type { FirmKeyDate } from "./CalendarTab"

const grid: React.CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 16 }

export default function DashboardTab({ goTo }: { goTo?: (section: string) => void }) {
  const lookups = useLookups(), today = isoDay(new Date())
  const matters = useQuery({ queryKey: ["lp-dash-matters"], queryFn: async () => pageOf<Matter>(await apiClient.get(`${BASE}/matters`, { params: { size: 500 } })).content })
  const invoices = useQuery({ queryKey: ["lp-dash-invoices"], queryFn: async () => pageOf<Invoice>(await apiClient.get(`${BASE}/invoices`, { params: { size: 500 } })).content })
  const horizon = isoDay(new Date(Date.now() + 60 * 864e5))
  const dates = useQuery({ queryKey: ["lp-dash-dates", today], queryFn: async () => { const r: any = await apiClient.get(`${BASE}/key-dates`, { params: { from: isoDay(new Date(Date.now() - 90 * 864e5)), to: horizon } }); const d = r?.data?.data ?? r?.data; return (Array.isArray(d) ? d : []) as FirmKeyDate[] } })
  if (matters.isError || invoices.isError) return <Empty>Could not load the dashboard. Please try again.</Empty>
  if (!matters.data || !invoices.data) return <Empty>Loading…</Empty>
  const s = dashboardStats(matters.data, invoices.data, lookups.clients, today)
  const next = upcomingDates(dates.data ?? [], 8), max = Math.max(1, ...Object.values(s.byStatus))
  const lateInvoices = invoices.data.filter(i => ["SENT", "PARTIALLY_PAID"].includes(i.status) && i.dueDate && i.dueDate < today && i.outstandingBalance > 0.005).sort((a, b) => (a.dueDate ?? "").localeCompare(b.dueDate ?? "")).slice(0, 5)
  return (
    <div>
      <SectionTitle>Dashboard</SectionTitle>
      <div style={{ ...card, ...grid, marginBottom: 12 }}>
        <Stat label="Matters" value={s.total} sub={`${s.open} open · ${s.onHold} on hold · ${s.closed} closed`} />
        <Stat label="Owed by clients" value={money(s.outstanding)} sub={s.overdueCount ? `${money(s.overdue)} overdue on ${s.overdueCount} invoice${s.overdueCount === 1 ? "" : "s"}` : "Nothing overdue"} tone={s.overdueCount ? "bad" : undefined} />
        <Stat label="Held in trust" value={money(s.trustHeld)} sub="Across all clients" />
        <Stat label="Invoices in draft" value={s.drafts} sub={s.drafts ? "Not yet sent" : "None waiting"} tone={s.drafts ? "warn" : undefined} />
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 12 }}>
        <div style={card}>
          <SectionTitle>Matters by status</SectionTitle>
          {s.total === 0 ? <div style={muted}>No matters yet.</div> : Object.entries(MATTER_STATUS).filter(([k]) => s.byStatus[k]).map(([k, v]) => (
            <div key={k} style={{ display: "grid", gridTemplateColumns: "90px 1fr 32px", gap: 8, alignItems: "center", margin: "6px 0", fontSize: 12 }}>
              <span>{v.label}</span><div style={{ background: "var(--hf-surface-sunken)", borderRadius: 4, height: 10 }}><div style={{ width: `${Math.max(3, Math.round(s.byStatus[k] / max * 100))}%`, height: 10, borderRadius: 4, background: "var(--hf-primary)" }} /></div><b style={{ textAlign: "right" }}>{s.byStatus[k]}</b>
            </div>))}
        </div>
        <div style={{ ...card, padding: 0, overflowX: "auto" }}>
          <div style={{ padding: "12px 16px 0" }}><SectionTitle right={goTo && <a href="/legal-practice/calendar" onClick={e => { e.preventDefault(); goTo("calendar") }} style={{ fontSize: 12 }}>Open calendar</a>}>Coming up</SectionTitle></div>
          {dates.isError ? <Empty>Could not load the deadlines.</Empty> : next.length === 0 ? <Empty>No pending deadlines in the next two months.</Empty> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Due</th><th style={th}>What</th></tr></thead>
              <tbody>{next.map(k => { const n = keyDateNote(k.status, k.dueDate)
                return <tr key={k.id}><td style={td}><b>{day(k.dueDate)}</b>{n && <div style={{ marginTop: 3 }}><Pill tone={n.tone}>{n.text}</Pill></div>}</td><td style={td}>{k.description}<div style={muted}>{k.matterNumber} · {k.matterName} · {labelOf(DATE_TYPES, k.dateType)}</div></td></tr> })}</tbody>
            </table>)}
        </div>
      </div>
      {lateInvoices.length > 0 && (
        <div style={{ ...card, padding: 0, overflowX: "auto", marginTop: 12 }}>
          <div style={{ padding: "12px 16px 0" }}><SectionTitle>Overdue invoices</SectionTitle></div>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Invoice</th><th style={th}>Client</th><th style={th}>Due</th><th style={{ ...th, textAlign: "right" }}>Owing</th></tr></thead>
            <tbody>{lateInvoices.map(i => <tr key={i.id}><td style={td}><b>{i.invoiceNumber}</b></td><td style={td}>{lookups.clientName(i.clientId)}</td><td style={td}>{day(i.dueDate)}</td><td style={{ ...td, textAlign: "right" }}>{money(i.outstandingBalance)}</td></tr>)}</tbody>
          </table>
        </div>)}
    </div>
  )
}
