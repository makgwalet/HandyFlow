// src/pages/invoicing/RetainersListPage.tsx  (route: /retainers)
//
// Retainers are invoices of type RETAINER: a block of committed hours billed up front and drawn down as work is logged.
import { useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { AlertTriangle, Gauge, Plus, Timer, Wallet } from "lucide-react"
import { PageHeader } from "../../components/ui/PageHeader"
import Chip from "../../components/ui/Chip"
import { usePermission } from "../../hooks/usePermission"
import {
  INVOICE_LABEL, INVOICE_TONE, fmtR, fmtRShort, isTruncated, label, LIST_LIMIT, matches, paginate, partyName, retainerBarColour, retainerStats, retainerUse, sortBy,
  type Invoice, type SortDir,
} from "./billing.logic"
import { LogHoursDialog } from "./dialogs"
import { useCustomerNames, useInvoices } from "./queries"
import { BillingNav, Btn, FilterPills, Kpis, Loading, LoadError, Meter, Notice, Pager, SearchBox, StateBox, TableShell, Th, td, trStyle } from "./ui"

const VIEWS = [{ value: "ALL", label: "All" }, { value: "ATTENTION", label: "Needs attention" }, { value: "OVER", label: "In overage" }]

export default function RetainersListPage() {
  const navigate = useNavigate()
  const canCreate = usePermission("INVOICE_CREATE")
  const { data, isLoading, isError, refetch } = useInvoices()
  const names = useCustomerNames()
  const [viewKey, setView] = useState("ALL")
  const [search, setSearch] = useState("")
  const [sort, setSort] = useState<{ key: string; dir: SortDir }>({ key: "used", dir: "desc" })
  const [page, setPage] = useState(0)
  const [logging, setLogging] = useState<Invoice | null>(null)

  const all = data?.rows ?? []
  const retainers = useMemo(() => all.filter(i => i.invoiceType === "RETAINER" && i.status !== "CANCELLED"), [all])
  const stats = useMemo(() => retainerStats(all), [all])

  const view = useMemo(() => {
    const lvl = (i: Invoice) => retainerUse(i)?.level
    const filtered = retainers.filter(i => (viewKey === "ALL" || (viewKey === "OVER" ? lvl(i) === "over" : lvl(i) === "low" || lvl(i) === "over"))
      && matches(search, i.invoiceNumber, i.title, partyName(i.customerId, i.walkinClientName, names)))
    const keys: Record<string, (i: Invoice) => string | number | null> = {
      invoiceNumber: i => i.invoiceNumber, customer: i => partyName(i.customerId, i.walkinClientName, names),
      used: i => retainerUse(i)?.percent ?? null, remaining: i => retainerUse(i)?.remaining ?? null, total: i => Number(i.total),
    }
    return paginate(sortBy(filtered, keys[sort.key] ?? keys.used, sort.dir), page)
  }, [retainers, viewKey, search, sort, page, names])

  const onSort = (key: string) => { setSort(s => ({ key, dir: s.key === key && s.dir === "asc" ? "desc" : "asc" })); setPage(0) }
  const th = { active: sort.key, dir: sort.dir, onSort }

  return (
    <div>
      <PageHeader title="Retainers" icon={Gauge} subtitle="Quotes, invoices, recurring billing and retainers"
        action={canCreate && <Btn variant="primary" icon={Plus} onClick={() => navigate("/invoices/retainer/new")}>New retainer</Btn>} />
      <BillingNav />

      <Kpis items={[
        { label: "Active retainers", value: stats.count, hint: `${fmtRShort(stats.value)} billed`, icon: Wallet, tone: "info" },
        { label: "Hours used", value: `${Math.round(stats.consumed * 10) / 10}h`, hint: `of ${Math.round(stats.committed * 10) / 10}h committed`, icon: Timer, tone: "neutral" },
        { label: "Running low", value: stats.low, hint: "80% or more used", icon: AlertTriangle, tone: stats.low > 0 ? "warn" : "ok", onClick: () => setView("ATTENTION") },
        { label: "In overage", value: stats.over, hint: "past the committed hours", icon: AlertTriangle, tone: stats.over > 0 ? "bad" : "ok", onClick: () => setView("OVER") },
      ]} />

      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
        <SearchBox value={search} onChange={v => { setSearch(v); setPage(0) }} placeholder="Search retainer or customer" />
        <FilterPills label="Retainer view" value={viewKey} onChange={v => { setView(v); setPage(0) }} options={VIEWS} />
      </div>
      {isTruncated(data?.total, all.length) && <div style={{ marginBottom: 12 }}><Notice tone="info">Retainers are found among the newest {LIST_LIMIT} of {data?.total} invoices.</Notice></div>}

      {isLoading ? <Loading text="Loading retainers..." /> : isError ? <LoadError onRetry={() => refetch()} /> :
        view.total === 0 ? (
          <StateBox icon={Gauge} title={retainers.length === 0 ? "No retainers yet" : "No retainers match"}
            text={retainers.length === 0 ? "A retainer bills a block of hours up front and tracks how they are used." : "Try a different search or view."}
            action={retainers.length === 0 && canCreate ? <Btn variant="primary" icon={Plus} onClick={() => navigate("/invoices/retainer/new")}>Create a retainer</Btn> : undefined} />
        ) : (
          <>
            <TableShell>
              <thead><tr style={{ background: "var(--hf-surface-muted)" }}>
                <Th sortKey="invoiceNumber" {...th}>Retainer</Th><Th sortKey="customer" {...th}>Customer</Th><Th sortKey="used" {...th}>Hours used</Th>
                <Th sortKey="remaining" align="right" {...th}>Remaining</Th><Th sortKey="total" align="right" {...th}>Billed</Th><Th>Invoice</Th><Th>Actions</Th>
              </tr></thead>
              <tbody>
                {view.rows.map(i => {
                  const u = retainerUse(i)
                  return (
                    <tr key={i.id} style={trStyle()} onClick={() => navigate(`/invoices/${i.id}`)} tabIndex={0} onKeyDown={e => { if (e.key === "Enter") navigate(`/invoices/${i.id}`) }}>
                      <td style={td}><div style={{ fontWeight: 700, color: "var(--hf-text-primary)" }}>{i.invoiceNumber}</div>{i.title && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{i.title}</div>}</td>
                      <td style={td}>{partyName(i.customerId, i.walkinClientName, names)}</td>
                      <td style={{ ...td, minWidth: 190 }}>
                        {u ? (<>
                          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, marginBottom: 3, color: u.level === "over" ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }}>
                            <span>{u.consumed}h used</span><span>{u.committed}h committed</span></div>
                          <Meter percent={u.percent} colour={retainerBarColour(u.level)} label={`${i.invoiceNumber} hours used`} />
                          {u.level === "over" && <div style={{ marginTop: 3 }}><Chip tone="bad">Over by {u.overBy}h</Chip></div>}
                          {u.level === "low" && <div style={{ marginTop: 3 }}><Chip tone="warn">{Math.round(u.percent)}% used</Chip></div>}
                        </>) : "—"}
                      </td>
                      <td style={{ ...td, textAlign: "right", fontWeight: 700 }}>{u ? `${u.remaining}h` : "—"}</td>
                      <td style={{ ...td, textAlign: "right" }}>{fmtR(i.total)}</td>
                      <td style={td}><Chip tone={INVOICE_TONE[i.status] ?? "neutral"}>{label(INVOICE_LABEL, i.status)}</Chip></td>
                      <td style={td} onClick={e => e.stopPropagation()}>
                        {canCreate && ["ISSUED", "PARTIALLY_PAID", "PAID"].includes(i.status) && <Btn small icon={Timer} onClick={() => setLogging(i)}>Log hours</Btn>}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </TableShell>
            <Pager {...view} onPage={setPage} />
          </>
        )}
      {logging && <LogHoursDialog invoice={logging} onClose={() => setLogging(null)} />}
    </div>
  )
}
