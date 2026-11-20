// Firm reports: matters, unbilled work, debtors ageing, billing and collections, trust balances. Each can be exported as CSV. A person walled off from
// some matters or clients sees figures without them, and the screen says so.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { filenameFrom, saveBlob } from "../../api/recipientAccess.client"
import { toast } from "../../store/toast.store"
import { Btn, Empty, ErrorLine, Input, Pill, SectionTitle, Stat, card, day, errorText, money, muted, td, th } from "./kit"
import { BASE } from "./lookups"
import {
  REPORTS, bucketTone, isoDay, matterGrid, matterStatusText, reportAsAtProblem, reportMonthText, reportRangeProblem, reportUrl, reportWallNote, reportYearStart,
  type BillingReport, type DebtorsReport, type MattersReport, type ReportKey, type TrustReport, type UnbilledReport,
} from "./legal.logic"

const URL = `${BASE}/reports`
const unwrap = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const right = { textAlign: "right" as const }

export default function ReportsTab() {
  const today = isoDay(new Date())
  const [key, setKey] = useState<ReportKey>("matters"), [from, setFrom] = useState(reportYearStart(today)), [to, setTo] = useState(today), [asAt, setAsAt] = useState(today)
  const [err, setErr] = useState(""), [busy, setBusy] = useState(false)
  const problem = key === "billing" ? reportRangeProblem(from, to, today) : key === "trust" ? reportAsAtProblem(asAt, today) : null

  const exportCsv = async () => {
    setErr(""); setBusy(true)
    try {
      const u = reportUrl(key, { from, to, asAt }, true)
      const r = await apiClient.get(`${URL}/${u.path}`, { params: u.params, responseType: "blob" })
      saveBlob(r.data as Blob, filenameFrom(r.headers?.["content-disposition"]) || `${key}.csv`)
      toast.success("Exported")
    } catch (e) { setErr(await errorText(e, "Could not export the report")) } finally { setBusy(false) }
  }

  return (
    <div>
      <SectionTitle right={<Btn disabled={!!problem || busy} onClick={exportCsv}>{busy ? "Exporting…" : "Export CSV"}</Btn>}>Reports</SectionTitle>
      <div style={{ display: "flex", gap: 6, marginBottom: 10, flexWrap: "wrap" }}>
        {REPORTS.map(([k, l]) => <Btn key={k} small kind={key === k ? "primary" : undefined} onClick={() => { setKey(k); setErr("") }}>{l}</Btn>)}
      </div>
      {key === "billing" && (
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 8 }}>
          <div style={{ width: 170 }}><Input label="Issued from" type="date" value={from} onChange={setFrom} max={today} /></div>
          <div style={{ width: 170 }}><Input label="Issued to" type="date" value={to} onChange={setTo} max={today} /></div>
        </div>)}
      {key === "trust" && <div style={{ width: 170, marginBottom: 8 }}><Input label="Balances as at" type="date" value={asAt} onChange={setAsAt} max={today} /></div>}
      {problem && <ErrorLine text={problem} />}
      {err && <ErrorLine text={err} />}
      {key === "matters" && <Matters />}
      {key === "unbilled" && <Unbilled />}
      {key === "debtors" && <Debtors />}
      {key === "billing" && !problem && <Billing from={from} to={to} />}
      {key === "trust" && !problem && <Trust asAt={asAt} />}
    </div>
  )
}

const useReport = <T,>(key: ReportKey, params: Record<string, string | undefined> = {}) =>
  useQuery({ queryKey: ["lp-report", key, params], queryFn: async () => unwrap<T>(await apiClient.get(`${URL}/${key}`, { params })) })

function Note({ hidden, truncated }: { hidden: boolean; truncated?: boolean }) {
  return <>
    {hidden && <div style={{ ...muted, margin: "6px 0", color: "var(--hf-warning-text, inherit)" }}>⚠ {reportWallNote}</div>}
    {truncated && <div style={{ ...muted, margin: "6px 0", color: "var(--hf-warning-text, inherit)" }}>⚠ Only the first 2000 rows are shown and counted. Totals cover those rows only.</div>}
  </>
}
const Status = ({ q, children }: { q: { isError: boolean; isLoading: boolean; data?: unknown }; children: React.ReactNode }) =>
  q.isError ? <Empty>Could not load this report.</Empty> : q.isLoading || !q.data ? <Empty>Loading…</Empty> : <>{children}</>
const Stats = ({ items }: { items: [string, React.ReactNode, ("good" | "bad" | "warn")?][] }) => (
  <div style={{ ...card, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 14, marginBottom: 10 }}>{items.map(([l, v, t]) => <Stat key={l} label={l} value={v} tone={t} />)}</div>)

function Matters() {
  const q = useReport<MattersReport>("matters")
  const r = q.data, g = r ? matterGrid(r.rows) : null
  return (
    <Status q={q}>{r && g && (
      <div>
        <Stats items={[["Matters", r.total], ["Open or on hold", r.open]]} />
        <Note hidden={r.hidden} />
        <div style={{ ...card, padding: 0, overflowX: "auto" }}>
          {r.rows.length === 0 ? <Empty>No matters.</Empty> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Kind of matter</th>{g.statuses.map(s => <th key={s} style={{ ...th, ...right }}>{matterStatusText(s)}</th>)}</tr></thead>
              <tbody>{g.kinds.map(k => <tr key={k}><td style={td}>{k.replace(/_/g, " ").toLowerCase().replace(/^\w/, c => c.toUpperCase())}</td>{g.statuses.map(s => <td key={s} style={{ ...td, ...right }}>{g.cell(s, k)}</td>)}</tr>)}</tbody>
            </table>)}
        </div>
      </div>)}</Status>
  )
}

function Unbilled() {
  const q = useReport<UnbilledReport>("unbilled"), r = q.data
  return (
    <Status q={q}>{r && (
      <div>
        <Stats items={[["Unbilled hours", r.hours.toFixed(2)], ["Time value", money(r.timeValue)], ["Disbursements", money(r.disbursements)], ["Total unbilled", money(r.total), r.total > 0 ? "warn" : undefined]]} />
        <Note hidden={r.hidden} truncated={r.truncated} />
        <div style={{ ...card, padding: 0, overflowX: "auto" }}>
          {r.rows.length === 0 ? <Empty>Nothing unbilled.</Empty> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Matter</th><th style={th}>Client</th><th style={{ ...th, ...right }}>Hours</th><th style={{ ...th, ...right }}>Time</th><th style={{ ...th, ...right }}>Disbursements</th><th style={{ ...th, ...right }}>Total</th></tr></thead>
              <tbody>{r.rows.map(x => <tr key={x.matterId}><td style={td}>{x.matterLabel}</td><td style={td}>{x.clientName}</td><td style={{ ...td, ...right }}>{x.hours.toFixed(2)}</td><td style={{ ...td, ...right }}>{money(x.timeValue)}</td><td style={{ ...td, ...right }}>{money(x.disbursements)}</td><td style={{ ...td, ...right }}><b>{money(x.total)}</b></td></tr>)}</tbody>
            </table>)}
        </div>
      </div>)}</Status>
  )
}

function Debtors() {
  const q = useReport<DebtorsReport>("debtors"), r = q.data
  return (
    <Status q={q}>{r && (
      <div>
        <Stats items={[["Owing", money(r.outstanding)], ["Overdue", money(r.overdue), r.overdue > 0 ? "bad" : "good"], ...r.buckets.map(b => [b.label, `${money(b.amount)} (${b.count})`] as [string, string])]} />
        <Note hidden={r.hidden} truncated={r.truncated} />
        <div style={{ ...card, padding: 0, overflowX: "auto" }}>
          {r.rows.length === 0 ? <Empty>Nobody owes the firm anything.</Empty> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Invoice</th><th style={th}>Client</th><th style={th}>Due</th><th style={{ ...th, ...right }}>Total</th><th style={{ ...th, ...right }}>Paid</th><th style={{ ...th, ...right }}>Owing</th><th style={th}>Ageing</th></tr></thead>
              <tbody>{r.rows.map(x => (
                <tr key={x.invoiceId}>
                  <td style={td}><b>{x.invoiceNumber}</b>{x.matterLabel && <div style={muted}>{x.matterLabel}</div>}</td>
                  <td style={td}>{x.clientName}</td>
                  <td style={td}>{x.dueDate ? day(x.dueDate) : "—"}</td>
                  <td style={{ ...td, ...right }}>{money(x.total)}</td><td style={{ ...td, ...right }}>{money(x.paid)}</td><td style={{ ...td, ...right }}><b>{money(x.outstanding)}</b></td>
                  <td style={td}><Pill tone={bucketTone(x.bucket)}>{x.daysOverdue > 0 ? `${x.daysOverdue} days late` : "Not yet due"}</Pill></td>
                </tr>))}</tbody>
            </table>)}
        </div>
      </div>)}</Status>
  )
}

function Billing({ from, to }: { from: string; to: string }) {
  const q = useReport<BillingReport>("billing", { from, to }), r = q.data
  return (
    <Status q={q}>{r && (
      <div>
        <Stats items={[["Invoiced (incl. VAT)", money(r.invoiced)], ["VAT", money(r.vat)], ["Paid", money(r.paid)], ["Collected", `${r.collectedPercent.toFixed(1)}%`, r.invoiced > 0 && r.collectedPercent < 50 ? "warn" : undefined], ["Written off", money(r.writtenOff)]]} />
        <Note hidden={r.hidden} />
        <div style={{ ...muted, marginBottom: 6 }}>Invoices by the month they were issued. Drafts are left out. Written-off invoices are counted apart from what was invoiced.</div>
        <div style={{ ...card, padding: 0, overflowX: "auto" }}>
          {r.months.length === 0 ? <Empty>No invoices were issued in this range.</Empty> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Month</th><th style={{ ...th, ...right }}>Invoices</th><th style={{ ...th, ...right }}>Invoiced</th><th style={{ ...th, ...right }}>Paid</th><th style={{ ...th, ...right }}>Written off</th></tr></thead>
              <tbody>{r.months.map(m => <tr key={m.month}><td style={td}>{reportMonthText(m.month)}</td><td style={{ ...td, ...right }}>{m.invoices}</td><td style={{ ...td, ...right }}>{money(m.invoiced)}</td><td style={{ ...td, ...right }}>{money(m.paid)}</td><td style={{ ...td, ...right }}>{money(m.writtenOff)}</td></tr>)}</tbody>
            </table>)}
        </div>
      </div>)}</Status>
  )
}

function Trust({ asAt }: { asAt: string }) {
  const q = useReport<TrustReport>("trust", { asAt }), r = q.data
  return (
    <Status q={q}>{r && (
      <div>
        <Stats items={[["Held in trust", money(r.total)], ["Clients with a balance", r.rows.length], ["Overdrawn", r.overdrawn, r.overdrawn > 0 ? "bad" : "good"]]} />
        <Note hidden={r.hidden} />
        {r.hidden && <div style={{ ...muted, marginBottom: 6 }}>Movements on matters you are walled from are not counted, so a client's balance here can differ from their real balance.</div>}
        <div style={{ ...card, padding: 0, overflowX: "auto" }}>
          {r.rows.length === 0 ? <Empty>No client had a trust balance on this date.</Empty> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Client</th><th style={{ ...th, ...right }}>Balance</th><th style={th}>Last movement</th></tr></thead>
              <tbody>{r.rows.map(x => <tr key={x.clientId}><td style={td}>{x.clientName}</td><td style={{ ...td, ...right }}>{x.balance < 0 ? <Pill tone="danger">{money(x.balance)}</Pill> : money(x.balance)}</td><td style={td}>{x.lastMovement ? day(x.lastMovement) : "—"}</td></tr>)}</tbody>
            </table>)}
        </div>
      </div>)}</Status>
  )
}
