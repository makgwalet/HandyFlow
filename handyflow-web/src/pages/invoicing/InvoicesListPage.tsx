// src/pages/invoicing/InvoicesListPage.tsx  (route: /invoices)
import { useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { AlertTriangle, CheckCircle, Download, Plus, Receipt, Send, Wallet } from "lucide-react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { PageHeader } from "../../components/ui/PageHeader"
import Chip from "../../components/ui/Chip"
import { usePermission } from "../../hooks/usePermission"
import {
  INVOICE_LABEL, INVOICE_TONE, OPEN_INVOICE_STATUSES, TYPE_LABEL, balanceOf, countBy, dueLabel, fmtDate, fmtR, fmtRShort, invoiceStats, isTruncated, label, LIST_LIMIT,
  matches, paginate, partyName, sortBy, type Invoice, type SortDir,
} from "./billing.logic"
import { RecordPaymentDialog } from "./dialogs"
import { apiMessage, downloadPdf, useCustomerNames, useInvoices } from "./queries"
import { BillingNav, Btn, FilterPills, Kpis, Loading, LoadError, Notice, Pager, SearchBox, StateBox, TableShell, Th, td, trStyle } from "./ui"

const STATUSES = ["ALL", "OPEN", "DRAFT", "ISSUED", "PARTIALLY_PAID", "OVERDUE", "PAID", "CANCELLED"]
const TYPES = ["ALL", "STANDARD", "RECURRING_INSTANCE", "RETAINER"]

export default function InvoicesListPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const canCreate = usePermission("INVOICE_CREATE")
  const { data, isLoading, isError, refetch } = useInvoices()
  const names = useCustomerNames()
  const [status, setStatus] = useState("ALL")
  const [type, setType] = useState("ALL")
  const [search, setSearch] = useState("")
  const [sort, setSort] = useState<{ key: string; dir: SortDir }>({ key: "createdAt", dir: "desc" })
  const [page, setPage] = useState(0)
  const [paying, setPaying] = useState<Invoice | null>(null)
  const [msg, setMsg] = useState("")

  const invoices = data?.rows ?? []
  const stats = useMemo(() => invoiceStats(invoices), [invoices])
  const counts = useMemo(() => ({ ...countBy(invoices, i => i.status), OPEN: invoices.filter(i => OPEN_INVOICE_STATUSES.includes(i.status)).length }), [invoices])

  const issue = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/invoicing/invoices/${id}/issue`),
    onSuccess: () => { setMsg(""); qc.invalidateQueries({ queryKey: ["invoices"] }) },
    onError: (e: any) => setMsg(apiMessage(e, "The invoice could not be issued.")),
  })

  const view = useMemo(() => {
    const inStatus = (i: Invoice) => status === "ALL" || (status === "OPEN" ? OPEN_INVOICE_STATUSES.includes(i.status) : i.status === status)
    const filtered = invoices.filter(i => inStatus(i) && (type === "ALL" || i.invoiceType === type)
      && matches(search, i.invoiceNumber, i.title, partyName(i.customerId, i.walkinClientName, names)))
    const keys: Record<string, (i: Invoice) => string | number | null> = {
      invoiceNumber: i => i.invoiceNumber, customer: i => partyName(i.customerId, i.walkinClientName, names), issuedAt: i => i.issuedAt,
      dueDate: i => i.dueDate, total: i => Number(i.total), balance: i => balanceOf(i), status: i => i.status, createdAt: i => i.createdAt,
    }
    return paginate(sortBy(filtered, keys[sort.key] ?? keys.createdAt, sort.dir), page)
  }, [invoices, status, type, search, sort, page, names])

  const onSort = (key: string) => { setSort(s => ({ key, dir: s.key === key && s.dir === "asc" ? "desc" : "asc" })); setPage(0) }
  const th = { active: sort.key, dir: sort.dir, onSort }
  const pdf = async (i: Invoice) => setMsg((await downloadPdf(`/api/v1/invoicing/invoices/${i.id}/pdf`, `${i.invoiceNumber}.pdf`)) ?? "")

  return (
    <div>
      <PageHeader title="Invoices" icon={Receipt} subtitle="Quotes, invoices, recurring billing and retainers"
        action={canCreate && <Btn variant="primary" icon={Plus} onClick={() => navigate("/invoices/retainer/new")}>Retainer invoice</Btn>} />
      <BillingNav />

      <Kpis items={[
        { label: "Outstanding", value: fmtRShort(stats.outstanding), hint: `${stats.outstandingCount} open invoice${stats.outstandingCount === 1 ? "" : "s"}`, icon: Wallet, tone: "info", onClick: () => setStatus("OPEN") },
        { label: "Overdue", value: fmtRShort(stats.overdueValue), hint: `${stats.overdueCount} invoice${stats.overdueCount === 1 ? "" : "s"}`, icon: AlertTriangle, tone: stats.overdueCount > 0 ? "bad" : "ok", onClick: () => setStatus("OVERDUE") },
        { label: "Collected", value: fmtRShort(stats.collected), hint: stats.billed > 0 ? `${Math.round((stats.collected / stats.billed) * 100)}% of ${fmtRShort(stats.billed)} billed` : undefined, icon: CheckCircle, tone: "ok", onClick: () => setStatus("PAID") },
        { label: "Drafts to issue", value: stats.drafts, hint: stats.drafts > 0 ? "not yet sent to the client" : "none waiting", icon: Send, tone: stats.drafts > 0 ? "warn" : "ok", onClick: () => setStatus("DRAFT") },
      ]} />

      <div style={{ display: "grid", gap: 10, marginBottom: 14 }}>
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "center" }}>
          <SearchBox value={search} onChange={v => { setSearch(v); setPage(0) }} placeholder="Search invoice number or customer" />
          <FilterPills label="Invoice type" value={type} onChange={v => { setType(v); setPage(0) }} options={TYPES.map(t => ({ value: t, label: t === "ALL" ? "All types" : label(TYPE_LABEL, t) }))} />
        </div>
        <FilterPills label="Invoice status" value={status} onChange={v => { setStatus(v); setPage(0) }}
          options={STATUSES.map(s => ({ value: s, label: s === "ALL" ? "All" : s === "OPEN" ? "Open" : label(INVOICE_LABEL, s), count: s === "ALL" ? invoices.length : (counts as Record<string, number>)[s] ?? 0 }))} />
      </div>
      {msg && <div style={{ marginBottom: 12 }}><Notice tone="bad">{msg}</Notice></div>}
      {isTruncated(data?.total, invoices.length) && <div style={{ marginBottom: 12 }}><Notice tone="info">Showing the newest {LIST_LIMIT} of {data?.total} invoices. Search and filters cover these.</Notice></div>}

      {isLoading ? <Loading text="Loading invoices..." /> : isError ? <LoadError onRetry={() => refetch()} /> :
        view.total === 0 ? (
          <StateBox icon={Receipt} title={invoices.length === 0 ? "No invoices yet" : "No invoices match"}
            text={invoices.length === 0 ? "Convert an accepted quote, create a retainer, or let a recurring schedule run." : "Try a different search, status or type."} />
        ) : (
          <>
            <TableShell>
              <thead><tr style={{ background: "var(--hf-surface-muted)" }}>
                <Th sortKey="invoiceNumber" {...th}>Invoice</Th><Th sortKey="customer" {...th}>Customer</Th><Th sortKey="dueDate" {...th}>Due</Th>
                <Th sortKey="total" align="right" {...th}>Total</Th><Th sortKey="balance" align="right" {...th}>Balance</Th><Th sortKey="status" {...th}>Status</Th><Th>Actions</Th>
              </tr></thead>
              <tbody>
                {view.rows.map(inv => {
                  const due = dueLabel(inv)
                  return (
                    <tr key={inv.id} style={trStyle()} onClick={() => navigate(`/invoices/${inv.id}`)} tabIndex={0} onKeyDown={e => { if (e.key === "Enter") navigate(`/invoices/${inv.id}`) }}>
                      <td style={td}>
                        <div style={{ fontWeight: 700, color: "var(--hf-text-primary)" }}>{inv.invoiceNumber}</div>
                        {inv.invoiceType !== "STANDARD" && <div style={{ marginTop: 3 }}><Chip tone={inv.invoiceType === "RETAINER" ? "accent" : "info"}>{label(TYPE_LABEL, inv.invoiceType)}</Chip></div>}
                      </td>
                      <td style={td}>{partyName(inv.customerId, inv.walkinClientName, names)}</td>
                      <td style={td}>{fmtDate(inv.dueDate)}{due && <div style={{ fontSize: 11, color: inv.status === "OVERDUE" ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }}>{due}</div>}</td>
                      <td style={{ ...td, textAlign: "right" }}>{fmtR(inv.total)}</td>
                      <td style={{ ...td, textAlign: "right", fontWeight: 700, color: balanceOf(inv) > 0 && inv.status !== "DRAFT" && inv.status !== "CANCELLED" ? "var(--hf-text-primary)" : "var(--hf-text-faint)" }}>
                        {inv.status === "DRAFT" || inv.status === "CANCELLED" ? "—" : fmtR(balanceOf(inv))}
                      </td>
                      <td style={td}><Chip tone={INVOICE_TONE[inv.status] ?? "neutral"}>{label(INVOICE_LABEL, inv.status)}</Chip></td>
                      <td style={td} onClick={e => e.stopPropagation()}>
                        <div style={{ display: "flex", gap: 6 }}>
                          {canCreate && inv.status === "DRAFT" && <Btn small icon={Send} onClick={() => issue.mutate(inv.id)} disabled={issue.isPending}>Issue</Btn>}
                          {canCreate && OPEN_INVOICE_STATUSES.includes(inv.status) && <Btn small variant="success" icon={CheckCircle} onClick={() => setPaying(inv)}>Record payment</Btn>}
                          <Btn small icon={Download} aria-label={`Download ${inv.invoiceNumber} PDF`} onClick={() => pdf(inv)}>PDF</Btn>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </TableShell>
            <Pager {...view} onPage={setPage} />
          </>
        )}
      {paying && <RecordPaymentDialog invoice={paying} onClose={() => setPaying(null)} />}
    </div>
  )
}
