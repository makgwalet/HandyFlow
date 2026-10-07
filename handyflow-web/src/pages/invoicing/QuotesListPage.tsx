// src/pages/invoicing/QuotesListPage.tsx  (route: /quotes)
import { useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { Eye, FileCheck, FileText, Plus, Send, Wallet } from "lucide-react"
import { PageHeader } from "../../components/ui/PageHeader"
import Chip from "../../components/ui/Chip"
import { usePermission } from "../../hooks/usePermission"
import {
  QUOTE_LABEL, QUOTE_TONE, countBy, fmtDate, fmtR, fmtRShort, isTruncated, label, LIST_LIMIT, matches, paginate, partyName, quoteExpiryLabel, quoteStats, sortBy,
  type Quote, type SortDir,
} from "./billing.logic"
import { useCustomerNames, useQuotes } from "./queries"
import { BillingNav, Btn, FilterPills, Kpis, Loading, LoadError, Notice, Pager, SearchBox, StateBox, TableShell, Th, td, trStyle } from "./ui"

const STATUSES = ["ALL", "DRAFT", "SENT", "ACCEPTED", "INVOICED", "REJECTED", "EXPIRED"]

export default function QuotesListPage() {
  const navigate = useNavigate()
  const canCreate = usePermission("INVOICE_CREATE")
  const { data, isLoading, isError, refetch } = useQuotes()
  const names = useCustomerNames()
  const [status, setStatus] = useState("ALL")
  const [search, setSearch] = useState("")
  const [sort, setSort] = useState<{ key: string; dir: SortDir }>({ key: "createdAt", dir: "desc" })
  const [page, setPage] = useState(0)

  const quotes = data?.rows ?? []
  const stats = useMemo(() => quoteStats(quotes), [quotes])
  const counts = useMemo(() => countBy(quotes, q => q.status), [quotes])

  const view = useMemo(() => {
    const filtered = quotes.filter(q => (status === "ALL" || q.status === status) && matches(search, q.quoteNumber, q.title, partyName(q.customerId, q.walkinClientName, names)))
    const keys: Record<string, (q: Quote) => string | number | null> = {
      quoteNumber: q => q.quoteNumber, title: q => q.title, customer: q => partyName(q.customerId, q.walkinClientName, names),
      status: q => q.status, total: q => Number(q.total), expiresAt: q => q.expiresAt, createdAt: q => q.createdAt,
    }
    return paginate(sortBy(filtered, keys[sort.key] ?? keys.createdAt, sort.dir), page)
  }, [quotes, status, search, sort, page, names])

  const onSort = (key: string) => { setSort(s => ({ key, dir: s.key === key && s.dir === "asc" ? "desc" : "asc" })); setPage(0) }
  const th = { active: sort.key, dir: sort.dir, onSort }

  return (
    <div>
      <PageHeader title="Quotes" icon={FileText} subtitle="Quotes, invoices, recurring billing and retainers"
        action={canCreate && <Btn variant="primary" icon={Plus} onClick={() => navigate("/quotes/new")}>New quote</Btn>} />
      <BillingNav />

      <Kpis items={[
        { label: "Open quotes", value: fmtRShort(stats.openValue), hint: `${stats.draft} draft, ${stats.sent} sent`, icon: FileText, tone: "info", onClick: () => setStatus("SENT") },
        { label: "Accepted, not invoiced", value: stats.awaitingInvoice, hint: stats.awaitingInvoice > 0 ? fmtRShort(stats.awaitingInvoiceValue) : "Nothing waiting", icon: FileCheck, tone: stats.awaitingInvoice > 0 ? "warn" : "ok", onClick: () => setStatus("ACCEPTED") },
        { label: "Sent, not opened", value: stats.unopened, hint: "the client has not viewed them", icon: Eye, tone: stats.unopened > 0 ? "warn" : "ok", onClick: () => setStatus("SENT") },
        { label: "Win rate", value: stats.winRate == null ? "—" : `${stats.winRate}%`, hint: "of quotes decided", icon: Wallet, tone: "neutral" },
      ]} />

      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
        <SearchBox value={search} onChange={v => { setSearch(v); setPage(0) }} placeholder="Search quote number, title or customer" />
        <FilterPills label="Quote status" value={status} onChange={v => { setStatus(v); setPage(0) }}
          options={STATUSES.map(s => ({ value: s, label: s === "ALL" ? "All" : label(QUOTE_LABEL, s), count: s === "ALL" ? quotes.length : counts[s] ?? 0 }))} />
      </div>
      {isTruncated(data?.total, quotes.length) && <div style={{ marginBottom: 12 }}><Notice tone="info">Showing the newest {LIST_LIMIT} of {data?.total} quotes. Search covers these.</Notice></div>}

      {isLoading ? <Loading text="Loading quotes..." /> : isError ? <LoadError onRetry={() => refetch()} /> :
        view.total === 0 ? (
          <StateBox icon={FileText} title={quotes.length === 0 ? "No quotes yet" : "No quotes match"}
            text={quotes.length === 0 ? "Create your first quote to get started." : "Try a different search or status."}
            action={quotes.length === 0 && canCreate ? <Btn variant="primary" icon={Plus} onClick={() => navigate("/quotes/new")}>New quote</Btn> : undefined} />
        ) : (
          <>
            <TableShell>
              <thead><tr style={{ background: "var(--hf-surface-muted)" }}>
                <Th sortKey="quoteNumber" {...th}>Quote</Th><Th sortKey="customer" {...th}>Customer</Th><Th sortKey="status" {...th}>Status</Th>
                <Th sortKey="total" align="right" {...th}>Total</Th><Th sortKey="expiresAt" {...th}>Expires</Th><Th sortKey="createdAt" {...th}>Created</Th>
              </tr></thead>
              <tbody>
                {view.rows.map(q => {
                  const expiry = quoteExpiryLabel(q)
                  return (
                    <tr key={q.id} style={trStyle()} onClick={() => navigate(`/quotes/${q.id}`)} tabIndex={0}
                      onKeyDown={e => { if (e.key === "Enter") navigate(`/quotes/${q.id}`) }}>
                      <td style={td}><div style={{ fontWeight: 700, color: "var(--hf-text-primary)" }}>{q.quoteNumber}</div><div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{q.title}</div></td>
                      <td style={td}>{partyName(q.customerId, q.walkinClientName, names)}</td>
                      <td style={td}>
                        <Chip tone={QUOTE_TONE[q.status] ?? "neutral"}>{label(QUOTE_LABEL, q.status)}</Chip>
                        {q.status === "SENT" && (
                          <div style={{ fontSize: 11, marginTop: 4, color: q.firstViewedAt ? "var(--hf-accent-text)" : "var(--hf-text-faint)", display: "flex", gap: 4, alignItems: "center" }}>
                            <Send size={10} aria-hidden="true" />{q.firstViewedAt ? `Viewed ${fmtDate(q.firstViewedAt)}${(q.viewCount ?? 0) > 1 ? ` · ${q.viewCount} times` : ""}` : "Not opened yet"}
                          </div>
                        )}
                      </td>
                      <td style={{ ...td, textAlign: "right", fontWeight: 700, color: "var(--hf-text-primary)" }}>{fmtR(q.total)}</td>
                      <td style={td}>{fmtDate(q.expiresAt)}{expiry && <div style={{ fontSize: 11, color: expiry.startsWith("Lapsed") ? "var(--hf-danger-text)" : "var(--hf-warning-text)" }}>{expiry}</div>}</td>
                      <td style={td}>{fmtDate(q.createdAt)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </TableShell>
            <Pager {...view} onPage={setPage} />
          </>
        )}
    </div>
  )
}
