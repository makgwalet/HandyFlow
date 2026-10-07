// src/pages/invoicing/CreditNotesListPage.tsx  (route: /credit-notes)
//
// Every credit note issued, newest first. A credit note is issued from an invoice, so this screen is for finding and
// downloading them; it links back to the invoice.
import { useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { Download, FileMinus } from "lucide-react"
import { PageHeader } from "../../components/ui/PageHeader"
import { fmtDate, fmtR, fmtRShort, isTruncated, LIST_LIMIT, matches, paginate, sortBy, type CreditNote, type SortDir } from "./billing.logic"
import { downloadPdf, useAllCreditNotes } from "./queries"
import { BillingNav, Btn, Kpis, Loading, LoadError, Notice, Pager, SearchBox, StateBox, TableShell, Th, td, trStyle } from "./ui"

export default function CreditNotesListPage() {
  const navigate = useNavigate()
  const { data, isLoading, isError, refetch } = useAllCreditNotes()
  const [search, setSearch] = useState("")
  const [sort, setSort] = useState<{ key: string; dir: SortDir }>({ key: "issuedAt", dir: "desc" })
  const [page, setPage] = useState(0)
  const [msg, setMsg] = useState("")

  const notes = data?.rows ?? []
  const month = new Date().toISOString().slice(0, 7)
  const thisMonth = notes.filter(n => (n.issuedAt ?? n.createdAt ?? "").slice(0, 7) === month)
  const view = useMemo(() => {
    const filtered = notes.filter(n => matches(search, n.creditNoteNumber, n.invoiceNumber, n.reason))
    const keys: Record<string, (n: CreditNote) => string | number | null> = {
      creditNoteNumber: n => n.creditNoteNumber, invoiceNumber: n => n.invoiceNumber, reason: n => n.reason, total: n => Number(n.total), issuedAt: n => n.issuedAt ?? n.createdAt,
    }
    return paginate(sortBy(filtered, keys[sort.key] ?? keys.issuedAt, sort.dir), page)
  }, [notes, search, sort, page])
  const onSort = (key: string) => { setSort(s => ({ key, dir: s.key === key && s.dir === "asc" ? "desc" : "asc" })); setPage(0) }
  const th = { active: sort.key, dir: sort.dir, onSort }

  return (
    <div>
      <PageHeader title="Credit notes" icon={FileMinus} subtitle="Quotes, invoices, recurring billing and retainers" />
      <BillingNav />
      <Kpis items={[
        { label: "Credit notes issued", value: notes.length, icon: FileMinus, tone: "neutral" },
        { label: "Total credited", value: fmtRShort(notes.reduce((s, n) => s + Number(n.total || 0), 0)), hint: "including VAT", icon: FileMinus, tone: "warn" },
        { label: "Issued this month", value: thisMonth.length, hint: fmtRShort(thisMonth.reduce((s, n) => s + Number(n.total || 0), 0)), icon: FileMinus, tone: "neutral" },
      ]} />
      <div style={{ marginBottom: 14 }}><SearchBox value={search} onChange={v => { setSearch(v); setPage(0) }} placeholder="Search credit note, invoice or reason" /></div>
      {msg && <div style={{ marginBottom: 12 }}><Notice tone="bad">{msg}</Notice></div>}
      {isTruncated(data?.total, notes.length) && <div style={{ marginBottom: 12 }}><Notice tone="info">Showing the newest {LIST_LIMIT} of {data?.total} credit notes.</Notice></div>}
      {isLoading ? <Loading text="Loading credit notes..." /> : isError ? <LoadError onRetry={() => refetch()} /> :
        view.total === 0 ? (
          <StateBox icon={FileMinus} title={notes.length === 0 ? "No credit notes yet" : "No credit notes match"}
            text={notes.length === 0 ? "Issue a credit note from an invoice's page when you need to credit a customer." : "Try a different search."} />
        ) : (
          <>
            <TableShell>
              <thead><tr style={{ background: "var(--hf-surface-muted)" }}>
                <Th sortKey="creditNoteNumber" {...th}>Credit note</Th><Th sortKey="invoiceNumber" {...th}>Invoice</Th><Th sortKey="reason" {...th}>Reason</Th>
                <Th sortKey="total" align="right" {...th}>Total</Th><Th sortKey="issuedAt" {...th}>Issued</Th><Th>PDF</Th>
              </tr></thead>
              <tbody>
                {view.rows.map(n => (
                  <tr key={n.id} style={trStyle()} onClick={() => navigate(`/invoices/${n.invoiceId}`)} tabIndex={0} onKeyDown={e => { if (e.key === "Enter") navigate(`/invoices/${n.invoiceId}`) }}>
                    <td style={{ ...td, fontWeight: 700, color: "var(--hf-text-primary)" }}>{n.creditNoteNumber}</td>
                    <td style={td}>{n.invoiceNumber}</td>
                    <td style={td}>{n.reason}</td>
                    <td style={{ ...td, textAlign: "right", fontWeight: 700, color: "var(--hf-danger-text-strong)" }}>{fmtR(n.total)}</td>
                    <td style={td}>{fmtDate(n.issuedAt ?? n.createdAt)}</td>
                    <td style={td} onClick={e => e.stopPropagation()}>
                      <Btn small icon={Download} aria-label={`Download ${n.creditNoteNumber} PDF`} onClick={async () => setMsg((await downloadPdf(`/api/v1/invoicing/credit-notes/${n.id}/pdf`, `${n.creditNoteNumber}.pdf`)) ?? "")}>PDF</Btn>
                    </td>
                  </tr>
                ))}
              </tbody>
            </TableShell>
            <Pager {...view} onPage={setPage} />
          </>
        )}
    </div>
  )
}
