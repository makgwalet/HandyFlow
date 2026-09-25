// src/pages/invoicing/InvoicingPage.tsx
import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Plus, FileText, Download, ChevronDown, ChevronUp,
  CheckCircle, Send, X, Receipt, FileCheck, Clock,
  AlertCircle, XCircle, Eye, RefreshCw, Pause, Play,
  Trash2, Gauge, AlertTriangle, Timer, FileMinus,
} from 'lucide-react'
import { apiClient } from '../../api/client'

// ── Types ─────────────────────────────────────────────────────────────────────
type QuoteStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'INVOICED'
type RecurringStatus = 'ACTIVE' | 'PAUSED' | 'CANCELLED' | 'COMPLETED'

interface Quote {
  id: string; quoteNumber: string; title: string; status: QuoteStatus
  total: number; expiresAt: string | null; createdAt: string; customerId: string | null
  firstViewedAt?: string | null; lastViewedAt?: string | null; viewCount?: number
}

interface CreditNote {
  id: string; creditNoteNumber: string; invoiceId: string; invoiceNumber: string
  reason: string; description: string | null
  subtotal: number; vatTotal: number; total: number; currency: string
  issuedAt: string; createdAt: string
}

interface Invoice {
  id: string; invoiceNumber: string; customerId: string | null; status: string
  issuedAt: string | null; dueDate: string | null
  subtotal: number; vatTotal: number; total: number; amountPaid: number
  lineItems: any[]; createdAt: string
  invoiceType: 'STANDARD' | 'RECURRING_INSTANCE' | 'RETAINER'
  recurringScheduleId: string | null
  committedHours: number | null; ratePerHour: number | null; hoursConsumed: number
  walkinClientName: string | null
}

interface RecurringSchedule {
  id: string; title: string; status: RecurringStatus
  frequency: string; customIntervalDays: number | null
  nextRunAt: string; lastRunAt: string | null
  total: number; subtotal: number; vatTotal: number
  customerId: string | null; lineItems: any[]
  walkinClientName: string | null; createdAt: string
  // Variable-hours contract fields — populated when variableHours is true,
  // null/zero otherwise. Field names mirror RecurringScheduleService.toResponse().
  variableHours: boolean
  ratePerHour: number | null
  minimumHoursPerCycle: number | null
  hoursVatRate: number | null
  contractStartDate: string | null
  contractEndDate: string | null
  contractedTotalHours: number | null
  totalHoursBilled: number
  remainingCycles: number
}

// ── Status configs ────────────────────────────────────────────────────────────
const QUOTE_STATUS: Record<QuoteStatus, { label: string; bg: string; color: string; icon: React.ElementType }> = {
  DRAFT:    { label: 'Draft',    bg: 'var(--hf-surface-sunken)', color: 'var(--hf-text-tertiary)', icon: AlertCircle },
  SENT:     { label: 'Sent',     bg: 'var(--hf-info-soft)', color: 'var(--hf-info-text)', icon: Send },
  ACCEPTED: { label: 'Accepted', bg: 'var(--hf-success-soft)', color: 'var(--hf-success-text-strong)', icon: CheckCircle },
  REJECTED: { label: 'Rejected', bg: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)', icon: XCircle },
  EXPIRED:  { label: 'Expired',  bg: 'var(--hf-warning-soft-strong)', color: 'var(--hf-warning-text-deep)', icon: Clock },
  INVOICED: { label: 'Invoiced', bg: 'var(--hf-violet-soft-strong)', color: 'var(--hf-violet-text)', icon: FileCheck },
}

const INVOICE_STATUS: Record<string, { label: string; bg: string; color: string }> = {
  DRAFT:          { bg: 'var(--hf-surface-sunken)', color: 'var(--hf-text-tertiary)', label: 'Draft' },
  ISSUED:         { bg: 'var(--hf-info-soft)', color: 'var(--hf-info-text)', label: 'Issued' },
  PARTIALLY_PAID: { bg: 'var(--hf-warning-soft-strong)', color: 'var(--hf-warning-text-deep)', label: 'Part paid' },
  PAID:           { bg: 'var(--hf-success-soft)', color: 'var(--hf-success-text-strong)', label: 'Paid' },
  OVERDUE:        { bg: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)', label: 'Overdue' },
  CANCELLED:      { bg: 'var(--hf-surface-muted)', color: 'var(--hf-text-faint)', label: 'Cancelled' },
}

const RECURRING_STATUS: Record<RecurringStatus, { label: string; bg: string; color: string; icon: React.ElementType }> = {
  ACTIVE:    { label: 'Active',    bg: 'var(--hf-success-soft)', color: 'var(--hf-success-text-strong)', icon: Play },
  PAUSED:    { label: 'Paused',    bg: 'var(--hf-warning-soft-strong)', color: 'var(--hf-warning-text-deep)', icon: Pause },
  CANCELLED: { label: 'Cancelled', bg: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)', icon: XCircle },
  COMPLETED: { label: 'Completed', bg: 'var(--hf-violet-soft-strong)', color: 'var(--hf-violet-text)', icon: CheckCircle },
}

const FREQ_LABEL: Record<string, string> = {
  DAILY: 'Daily', WEEKLY: 'Weekly', MONTHLY: 'Monthly', CUSTOM: 'Custom',
}

// ── Helpers ───────────────────────────────────────────────────────────────────
const fmtR = (n: number | null | undefined) =>
  n == null ? '—' : `R ${n.toLocaleString('en-ZA', { minimumFractionDigits: 2 })}`

const fmtDate = (d: string | null) =>
  d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'

const inp: React.CSSProperties = {
  width: '100%', padding: '10px 12px', border: '1.5px solid var(--hf-border)',
  borderRadius: 9, fontSize: 14, boxSizing: 'border-box', background: 'white', outline: 'none',
}

// ── Root Page ─────────────────────────────────────────────────────────────────
export function InvoicingPage() {
  const location = useLocation()
  const navigate = useNavigate()

  const activeTab = location.pathname.startsWith('/invoices')  ? 'invoices'
                  : location.pathname.startsWith('/recurring') ? 'recurring'
                  : 'quotes'

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: 'var(--hf-text)', margin: '0 0 4px' }}>Invoicing</h1>
          <p style={{ fontSize: 13, color: 'var(--hf-text-faint)', margin: 0 }}>Quotes, invoices, recurring billing and retainers</p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          {activeTab === 'quotes' && (
            <button onClick={() => navigate('/quotes/new')}
              style={{ display: 'flex', alignItems: 'center', gap: 7, background: 'var(--hf-primary)', color: 'white', border: 'none', borderRadius: 10, padding: '10px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
              <Plus size={15} /> New Quote
            </button>
          )}
          {activeTab === 'recurring' && (
            <>
              <button onClick={() => navigate('/recurring/variable-hours/new')}
                style={{ display: 'flex', alignItems: 'center', gap: 7, background: 'white', color: 'var(--hf-warning-text)', border: '1.5px solid var(--hf-warning-border)', borderRadius: 10, padding: '10px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
                <Gauge size={15} /> Variable-Hours Contract
              </button>
              <button onClick={() => navigate('/recurring/new')}
                style={{ display: 'flex', alignItems: 'center', gap: 7, background: 'var(--hf-accent)', color: 'white', border: 'none', borderRadius: 10, padding: '10px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
                <Plus size={15} /> New Schedule
              </button>
            </>
          )}
          {activeTab === 'invoices' && (
            <button onClick={() => navigate('/invoices/retainer/new')}
              style={{ display: 'flex', alignItems: 'center', gap: 7, background: 'var(--hf-violet)', color: 'white', border: 'none', borderRadius: 10, padding: '10px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
              <Plus size={15} /> Retainer Invoice
            </button>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 24, background: 'var(--hf-surface-sunken)', borderRadius: 12, padding: 4, width: 'fit-content' }}>
        {[
          { key: 'quotes',    label: 'Quotes',    icon: FileText,   path: '/quotes' },
          { key: 'invoices',  label: 'Invoices',  icon: Receipt,    path: '/invoices' },
          { key: 'recurring', label: 'Recurring', icon: RefreshCw,  path: '/recurring' },
        ].map(tab => (
          <button key={tab.key} onClick={() => navigate(tab.path)}
            style={{
              display: 'flex', alignItems: 'center', gap: 7,
              padding: '8px 20px', borderRadius: 9, border: 'none',
              fontSize: 14, fontWeight: 600, cursor: 'pointer',
              background: activeTab === tab.key ? 'white' : 'transparent',
              color:      activeTab === tab.key ? 'var(--hf-primary-text)' : 'var(--hf-text-muted)',
              boxShadow:  activeTab === tab.key ? '0 1px 4px rgba(0,0,0,0.1)' : 'none',
              transition: 'all 0.15s',
            }}>
            <tab.icon size={15} /> {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'quotes'    && <QuotesTab />}
      {activeTab === 'invoices'  && <InvoicesTab />}
      {activeTab === 'recurring' && <RecurringTab />}
    </div>
  )
}

// ── Quotes Tab ────────────────────────────────────────────────────────────────
function QuotesTab() {
  const navigate = useNavigate()
  const { data, isLoading, isError } = useQuery({
    queryKey: ['quotes'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/invoicing/quotes?size=100&sort=createdAt,desc')
      return (res.data?.data ?? res.data) as { content: Quote[]; totalElements: number }
    },
  })
  const quotes = data?.content ?? []

  return (
    <div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 20 }}>
        {[
          { label: 'Total',    value: quotes.length,                                      color: 'var(--hf-primary-text)' },
          { label: 'Draft',    value: quotes.filter(q => q.status === 'DRAFT').length,    color: 'var(--hf-text-tertiary)' },
          { label: 'Sent',     value: quotes.filter(q => q.status === 'SENT').length,     color: 'var(--hf-info-text)' },
          { label: 'Accepted', value: quotes.filter(q => q.status === 'ACCEPTED').length, color: 'var(--hf-success-text-strong)' },
        ].map(s => (
          <div key={s.label} style={{ background: 'white', border: '1px solid var(--hf-border)', borderRadius: 12, padding: '14px 18px' }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ background: 'white', border: '1px solid var(--hf-border)', borderRadius: 14, overflow: 'hidden' }}>
        {isLoading ? <LoadingRow text="Loading quotes..." /> :
         isError   ? <ErrorRow /> :
         quotes.length === 0 ? <EmptyRow icon={FileText} text="No quotes yet" sub="Create your first quote to get started." /> : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--hf-border-subtle)', background: 'var(--hf-surface-muted)' }}>
                {['Quote #', 'Title', 'Status', 'Total', 'Expires', ''].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '11px 16px', fontSize: 11, fontWeight: 700, color: 'var(--hf-text-faint)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {quotes.map(q => {
                const s = QUOTE_STATUS[q.status] ?? QUOTE_STATUS.DRAFT
                const Icon = s.icon
                return (
                  <tr key={q.id}
                    onClick={() => navigate(`/quotes/${q.id}`)}
                    style={{ borderBottom: '1px solid var(--hf-border-subtle)', cursor: 'pointer' }}
                    onMouseEnter={e => (e.currentTarget.style.background = 'var(--hf-surface-muted)')}
                    onMouseLeave={e => (e.currentTarget.style.background = 'white')}>
                    <td style={{ padding: '14px 16px' }}>
                      <span style={{ fontFamily: 'monospace', fontSize: 13, fontWeight: 700, color: 'var(--hf-info-text)' }}>{q.quoteNumber}</span>
                    </td>
                    <td style={{ padding: '14px 16px', fontSize: 14, fontWeight: 600, color: 'var(--hf-text)' }}>{q.title}</td>
                    <td style={{ padding: '14px 16px' }}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: s.bg, color: s.color, fontSize: 12, fontWeight: 700, padding: '4px 10px', borderRadius: 20 }}>
                        <Icon size={11} />{s.label}
                      </span>
                      {/* FIX: "no quote view-tracking" gap — sendQuote()/the public link
                          existed, but nothing showed whether the client had opened it. */}
                      {q.status === 'SENT' && (
                        q.firstViewedAt ? (
                          <div title={`First viewed ${new Date(q.firstViewedAt).toLocaleString('en-ZA')} · opened ${q.viewCount ?? 1} time${(q.viewCount ?? 1) === 1 ? '' : 's'}`}
                            style={{ display: 'flex', alignItems: 'center', gap: 4, marginTop: 5, fontSize: 11, color: 'var(--hf-accent-text)' }}>
                            <Eye size={11} /> Viewed {new Date(q.firstViewedAt).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short' })}
                            {(q.viewCount ?? 0) > 1 && ` · ${q.viewCount}×`}
                          </div>
                        ) : (
                          <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginTop: 5, fontSize: 11, color: 'var(--hf-text-faint)' }}>
                            <Eye size={11} /> Not opened yet
                          </div>
                        )
                      )}
                    </td>
                    <td style={{ padding: '14px 16px', fontSize: 14, fontWeight: 700, color: 'var(--hf-text)' }}>{fmtR(q.total)}</td>
                    <td style={{ padding: '14px 16px', fontSize: 13, color: 'var(--hf-text-muted)' }}>
                      {q.expiresAt ? new Date(q.expiresAt).toLocaleDateString('en-ZA') : '—'}
                    </td>
                    <td style={{ padding: '14px 16px' }}><Eye size={15} color="#94A3B8" /></td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

// ── Invoices Tab ──────────────────────────────────────────────────────────────
function InvoicesTab() {
  const qc = useQueryClient()
  const [expanded, setExpanded]         = useState<string | null>(null)
  const [downloading, setDownloading]   = useState<string | null>(null)
  const [payModal, setPayModal]         = useState<Invoice | null>(null)
  const [hoursModal, setHoursModal]     = useState<Invoice | null>(null)
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [typeFilter, setTypeFilter]     = useState('ALL')
  const [payForm, setPayForm] = useState({ amount: '', paymentMethod: 'EFT', reference: '', note: '' })
  const [hoursForm, setHoursForm] = useState({ hours: '', note: '' })
  const [payError, setPayError]   = useState('')
  const [hoursError, setHoursError] = useState('')
  // FIX: "no credit note generation" gap — TenantSequenceService's own doc
  // comment already anticipated a CREDIT_NOTE sequence.
  const [creditNoteModal, setCreditNoteModal] = useState<Invoice | null>(null)
  const [creditNoteForm, setCreditNoteForm] = useState({ reason: '', description: '', amount: '', vatRate: '15' })
  const [creditNoteError, setCreditNoteError] = useState('')

  const { data: invoices = [], isLoading, isError } = useQuery<Invoice[]>({
    queryKey: ['invoices'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/invoicing/invoices?size=100&sort=createdAt,desc')
      const payload = res.data?.data ?? res.data
      return payload.content ?? payload ?? []
    },
  })

  const { data: customers = [] } = useQuery({
    queryKey: ['customers-map'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/crm/customers?size=500')
      const payload = res.data?.data ?? res.data
      return (payload.content ?? payload) as { id: string; name: string }[]
    },
  })
  const customerMap = Object.fromEntries(customers.map(c => [c.id, c.name]))

  const issueInvoice = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/invoicing/invoices/${id}/issue`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['invoices'] }),
  })

  const markPaid = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/invoicing/invoices/${id}/payments`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['invoices'] })
      setPayModal(null); setPayError('')
      setPayForm({ amount: '', paymentMethod: 'EFT', reference: '', note: '' })
    },
    onError: (e: any) => {
      const d = e.response?.data
      if (d?.data && typeof d.data === 'object') {
        const [k, v] = Object.entries(d.data)[0] as [string, string]
        setPayError(`${k}: ${v}`)
      } else {
        setPayError(d?.message ?? 'Failed to record payment.')
      }
    },
  })

  const logHours = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/invoicing/invoices/${id}/hours`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['invoices'] })
      setHoursModal(null); setHoursError('')
      setHoursForm({ hours: '', note: '' })
    },
    onError: (e: any) => setHoursError(e.response?.data?.message ?? 'Failed to log hours.'),
  })

  const downloadPdf = async (inv: Invoice) => {
    setDownloading(inv.id)
    try {
      const res = await apiClient.get(`/api/v1/invoicing/invoices/${inv.id}/pdf`, { responseType: 'blob' } as any)
      const url = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
      const a = document.createElement('a'); a.href = url; a.download = `${inv.invoiceNumber}.pdf`; a.click()
      URL.revokeObjectURL(url)
    } catch { alert('Failed to download PDF.') }
    finally { setDownloading(null) }
  }

  // Credit notes for the currently-expanded invoice — fetched on demand,
  // not for every row, since only one row is ever expanded at a time.
  const { data: creditNotes = [] } = useQuery<CreditNote[]>({
    queryKey: ['credit-notes', expanded],
    queryFn: async () => {
      const res = await apiClient.get(`/api/v1/invoicing/invoices/${expanded}/credit-notes`)
      return res.data?.data ?? res.data ?? []
    },
    enabled: !!expanded,
  })

  const createCreditNote = useMutation({
    mutationFn: (body: { reason: string; description?: string; amount: number; vatRate: number }) =>
      apiClient.post(`/api/v1/invoicing/invoices/${creditNoteModal!.id}/credit-notes`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['credit-notes', creditNoteModal?.id] })
      setCreditNoteModal(null)
      setCreditNoteForm({ reason: '', description: '', amount: '', vatRate: '15' })
      setCreditNoteError('')
    },
    onError: (e: any) => setCreditNoteError(e.response?.data?.message ?? 'Failed to issue credit note'),
  })

  const downloadCreditNotePdf = async (cn: CreditNote) => {
    try {
      const res = await apiClient.get(`/api/v1/invoicing/credit-notes/${cn.id}/pdf`, { responseType: 'blob' } as any)
      const url = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
      const a = document.createElement('a'); a.href = url; a.download = `${cn.creditNoteNumber}.pdf`; a.click()
      URL.revokeObjectURL(url)
    } catch { alert('Failed to download credit note PDF.') }
  }

  // FIX: "no statement of account PDF" gap.
  const [downloadingStatement, setDownloadingStatement] = useState<string | null>(null)
  const downloadStatement = async (customerId: string) => {
    setDownloadingStatement(customerId)
    try {
      const res = await apiClient.get(`/api/v1/invoicing/customers/${customerId}/statement`, { responseType: 'blob' } as any)
      const url = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
      const a = document.createElement('a'); a.href = url; a.download = `statement-${customerId}.pdf`; a.click()
      URL.revokeObjectURL(url)
    } catch { alert('Failed to download statement of account.') }
    finally { setDownloadingStatement(null) }
  }

  let filtered = statusFilter === 'ALL' ? invoices : invoices.filter(i => i.status === statusFilter)
  if (typeFilter !== 'ALL') filtered = filtered.filter(i => i.invoiceType === typeFilter)

  const totalRevenue     = invoices.filter(i => i.status === 'PAID').reduce((s, i) => s + i.total, 0)
  const totalOutstanding = invoices.filter(i => ['ISSUED','PARTIALLY_PAID','OVERDUE'].includes(i.status)).reduce((s, i) => s + i.total, 0)
  const overdueCount     = invoices.filter(i => i.status === 'OVERDUE').length
  const retainerCount    = invoices.filter(i => i.invoiceType === 'RETAINER').length

  const displayName = (inv: Invoice) => {
    if (!inv.customerId) return inv.walkinClientName
      ? <span style={{ fontSize: 13 }}>{inv.walkinClientName} <span style={{ color: 'var(--hf-text-faint)', fontStyle: 'italic' }}>(walk-in)</span></span>
      : <span style={{ color: 'var(--hf-text-faint)', fontStyle: 'italic' }}>Walk-in client</span>
    return customerMap[inv.customerId] ?? <span style={{ color: 'var(--hf-text-faint)', fontSize: 12 }}>{inv.customerId.slice(0, 8)}…</span>
  }

  return (
    <div>
      {/* Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 12, marginBottom: 20 }}>
        {[
          { label: 'Total invoices', value: invoices.length,                  fmt: false, color: 'var(--hf-primary-text)' },
          { label: 'Paid',           value: invoices.filter(i => i.status === 'PAID').length, fmt: false, color: 'var(--hf-success-text-strong)' },
          { label: 'Retainers',      value: retainerCount,                    fmt: false, color: 'var(--hf-violet-text)' },
          { label: 'Revenue',        value: fmtR(totalRevenue),               fmt: true,  color: 'var(--hf-accent-text)' },
          { label: 'Outstanding',    value: fmtR(totalOutstanding),           fmt: true,  color: overdueCount > 0 ? 'var(--hf-danger-text)' : 'var(--hf-warning-text)' },
        ].map(s => (
          <div key={s.label} style={{ background: 'white', border: '1px solid var(--hf-border)', borderRadius: 12, padding: '14px 18px' }}>
            <div style={{ fontSize: s.fmt ? 16 : 24, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Filters row */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 16, alignItems: 'center', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', gap: 6 }}>
          {['ALL', 'DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED'].map(s => (
            <button key={s} onClick={() => setStatusFilter(s)}
              style={{ padding: '6px 12px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: 'none',
                background: statusFilter === s ? 'var(--hf-primary)' : 'var(--hf-surface-sunken)',
                color:      statusFilter === s ? 'white'   : 'var(--hf-text-muted)' }}>
              {s === 'ALL' ? 'All status' : (INVOICE_STATUS[s]?.label ?? s)}
              {s !== 'ALL' && ` (${invoices.filter(i => i.status === s).length})`}
            </button>
          ))}
        </div>
        <div style={{ height: 20, width: 1, background: 'var(--hf-surface-strong)' }} />
        <div style={{ display: 'flex', gap: 6 }}>
          {['ALL', 'STANDARD', 'RECURRING_INSTANCE', 'RETAINER'].map(t => (
            <button key={t} onClick={() => setTypeFilter(t)}
              style={{ padding: '6px 12px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: 'none',
                background: typeFilter === t ? 'var(--hf-violet)' : 'var(--hf-surface-sunken)',
                color:      typeFilter === t ? 'white'   : 'var(--hf-text-muted)' }}>
              {t === 'ALL' ? 'All types' : t === 'RECURRING_INSTANCE' ? 'Recurring' : t === 'RETAINER' ? 'Retainer' : 'Standard'}
            </button>
          ))}
        </div>
      </div>

      {/* Table */}
      <div style={{ background: 'white', border: '1px solid var(--hf-border)', borderRadius: 14, overflow: 'hidden' }}>
        {isLoading ? <LoadingRow text="Loading invoices..." /> :
         isError   ? <ErrorRow /> :
         filtered.length === 0 ? <EmptyRow icon={FileText} text="No invoices" sub="Convert an accepted quote, create a retainer, or let a recurring schedule fire." /> : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--hf-border-subtle)', background: 'var(--hf-surface-muted)' }}>
                {['Invoice #', 'Customer', 'Issued', 'Due', 'Amount', 'Status', 'Actions'].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '11px 16px', fontSize: 11, fontWeight: 700, color: 'var(--hf-text-faint)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map(inv => {
                const ss    = INVOICE_STATUS[inv.status] ?? INVOICE_STATUS.DRAFT
                const isExp = expanded === inv.id
                const isRetainer = inv.invoiceType === 'RETAINER'
                const isRecurring = inv.invoiceType === 'RECURRING_INSTANCE'

                // Hours state for retainers
                const pctConsumed = isRetainer && inv.committedHours
                  ? Math.min(100, ((inv.hoursConsumed ?? 0) / inv.committedHours) * 100)
                  : 0
                const isOverage = isRetainer && inv.committedHours != null
                  && (inv.hoursConsumed ?? 0) > inv.committedHours

                return (
                  <>
                    <tr key={inv.id}
                      onClick={() => setExpanded(isExp ? null : inv.id)}
                      style={{ borderBottom: '1px solid var(--hf-border-subtle)', cursor: 'pointer', background: isExp ? 'var(--hf-surface-muted)' : 'white' }}
                      onMouseEnter={e => { if (!isExp) (e.currentTarget as HTMLElement).style.background = 'var(--hf-surface-muted)' }}
                      onMouseLeave={e => { if (!isExp) (e.currentTarget as HTMLElement).style.background = 'white' }}>

                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <FileText size={14} color={isRetainer ? '#7C3AED' : isRecurring ? '#0D9488' : '#64748B'} />
                          <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--hf-text)' }}>{inv.invoiceNumber}</span>
                          {isExp ? <ChevronUp size={13} color="#94A3B8" /> : <ChevronDown size={13} color="#94A3B8" />}
                        </div>
                        {/* Type badges */}
                        <div style={{ display: 'flex', gap: 4, marginTop: 4 }}>
                          {isRetainer && (
                            <span style={{ fontSize: 10, fontWeight: 700, background: 'var(--hf-violet-soft-strong)', color: 'var(--hf-violet-text)', padding: '2px 6px', borderRadius: 6 }}>RETAINER</span>
                          )}
                          {isRecurring && (
                            <span style={{ fontSize: 10, fontWeight: 700, background: 'var(--hf-accent-soft)', color: 'var(--hf-accent-text-strong)', padding: '2px 6px', borderRadius: 6 }}>RECURRING</span>
                          )}
                          {isOverage && (
                            <span style={{ fontSize: 10, fontWeight: 700, background: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)', padding: '2px 6px', borderRadius: 6 }}>OVERAGE</span>
                          )}
                        </div>
                      </td>

                      <td style={{ padding: '14px 16px', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)' }}>{displayName(inv)}</td>
                      <td style={{ padding: '14px 16px', fontSize: 13, color: 'var(--hf-text-muted)' }}>{fmtDate(inv.issuedAt)}</td>
                      <td style={{ padding: '14px 16px', fontSize: 13, color: inv.status === 'OVERDUE' ? 'var(--hf-danger-text)' : 'var(--hf-text-muted)' }}>
                        {fmtDate(inv.dueDate)}
                      </td>

                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--hf-text)' }}>{fmtR(inv.total)}</div>
                        {inv.status === 'PARTIALLY_PAID' && inv.amountPaid > 0 && (
                          <div style={{ fontSize: 11, color: 'var(--hf-warning-text-deep)', marginTop: 1 }}>Paid: {fmtR(inv.amountPaid)}</div>
                        )}
                        {/* Hours progress bar for retainers */}
                        {isRetainer && inv.committedHours != null && (
                          <div style={{ marginTop: 6 }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, color: isOverage ? 'var(--hf-danger-text)' : 'var(--hf-text-muted)', marginBottom: 2 }}>
                              <span>{inv.hoursConsumed ?? 0}h used</span>
                              <span>{inv.committedHours}h committed</span>
                            </div>
                            <div style={{ height: 4, borderRadius: 2, background: 'var(--hf-surface-sunken)', overflow: 'hidden' }}>
                              <div style={{ height: '100%', width: `${pctConsumed}%`, background: isOverage ? 'var(--hf-danger)' : pctConsumed > 80 ? 'var(--hf-warning)' : 'var(--hf-accent)', borderRadius: 2, transition: 'width 0.3s' }} />
                            </div>
                          </div>
                        )}
                      </td>

                      <td style={{ padding: '14px 16px' }}>
                        <span style={{ background: ss.bg, color: ss.color, padding: '3px 10px', borderRadius: 20, fontSize: 12, fontWeight: 600 }}>{ss.label}</span>
                      </td>

                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ display: 'flex', gap: 5 }} onClick={e => e.stopPropagation()}>
                          {inv.status === 'DRAFT' && (
                            <ActionBtn icon={Send} label="Issue" color="#1D4ED8" bg="#EFF6FF" border="#BFDBFE"
                              onClick={() => issueInvoice.mutate(inv.id)} disabled={issueInvoice.isPending} />
                          )}
                          {['ISSUED','PARTIALLY_PAID','OVERDUE'].includes(inv.status) && (
                            <ActionBtn icon={CheckCircle} label="Mark paid" color="#166534" bg="#F0FDF4" border="#BBF7D0"
                              onClick={() => { setPayModal(inv); setPayError(''); setPayForm(f => ({ ...f, amount: String(inv.total - (inv.amountPaid ?? 0)) })) }} />
                          )}
                          {isRetainer && ['ISSUED','PARTIALLY_PAID','PAID'].includes(inv.status) && (
                            <ActionBtn icon={Timer} label="Log hrs" color="#7C3AED" bg="#F3E8FF" border="#DDD6FE"
                              onClick={() => { setHoursModal(inv); setHoursError(''); setHoursForm({ hours: '', note: '' }) }} />
                          )}
                          <ActionBtn icon={Download} label={downloading === inv.id ? '…' : 'PDF'} color="#64748B" bg="#F8FAFC" border="#E2E8F0"
                            onClick={() => downloadPdf(inv)} disabled={downloading === inv.id} />
                          {inv.customerId && (
                            <ActionBtn icon={FileText} label={downloadingStatement === inv.customerId ? '…' : 'Statement'} color="#0D9488" bg="#F0FDFA" border="#99F6E4"
                              onClick={() => downloadStatement(inv.customerId!)} disabled={downloadingStatement === inv.customerId} />
                          )}
                        </div>
                      </td>
                    </tr>

                    {isExp && (
                      <tr key={`${inv.id}-exp`}>
                        <td colSpan={7} style={{ padding: 0 }}>
                          <div style={{ background: 'var(--hf-surface-muted)', borderBottom: '1px solid var(--hf-border-subtle)', padding: '16px 24px' }}>
                            {/* Retainer detail panel */}
                            {isRetainer && inv.committedHours != null && (
                              <div style={{ background: 'white', border: '1px solid var(--hf-violet-border)', borderRadius: 10, padding: '14px 18px', marginBottom: 16 }}>
                                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-violet-text)', letterSpacing: '0.06em', marginBottom: 10 }}>RETAINER HOURS</div>
                                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
                                  {[
                                    { label: 'Committed', value: `${inv.committedHours}h`,        color: 'var(--hf-primary-text)' },
                                    { label: 'Consumed',  value: `${inv.hoursConsumed ?? 0}h`,    color: isOverage ? 'var(--hf-danger-text)' : 'var(--hf-text)' },
                                    { label: 'Remaining', value: isOverage ? 'Overage!' : `${Math.max(0, inv.committedHours - (inv.hoursConsumed ?? 0))}h`, color: isOverage ? 'var(--hf-danger-text)' : 'var(--hf-success-text-strong)' },
                                    { label: 'Rate',      value: fmtR(inv.ratePerHour) + '/hr',   color: 'var(--hf-text-muted)' },
                                  ].map(m => (
                                    <div key={m.label}>
                                      <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginBottom: 3 }}>{m.label}</div>
                                      <div style={{ fontSize: 16, fontWeight: 700, color: m.color }}>{m.value}</div>
                                    </div>
                                  ))}
                                </div>
                                {isOverage && (
                                  <div style={{ marginTop: 10, display: 'flex', alignItems: 'center', gap: 7, background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, padding: '8px 12px', fontSize: 12, color: 'var(--hf-danger-text)' }}>
                                    <AlertTriangle size={13} /> Consumed {((inv.hoursConsumed ?? 0) - inv.committedHours!).toFixed(2)}h over commitment — consider issuing a reconciliation invoice.
                                  </div>
                                )}
                                {/* FIX: "no retainer low-balance warning" gap — mirrors the overage
                                    banner above but for the 80-100% band, before the client runs out. */}
                                {!isOverage && pctConsumed >= 80 && (
                                  <div style={{ marginTop: 10, display: 'flex', alignItems: 'center', gap: 7, background: 'var(--hf-warning-soft)', border: '1px solid var(--hf-warning-border-strong)', borderRadius: 8, padding: '8px 12px', fontSize: 12, color: 'var(--hf-warning-text-deep)' }}>
                                    <AlertTriangle size={13} /> {pctConsumed.toFixed(0)}% of committed hours consumed — consider notifying the client before the retainer runs out.
                                  </div>
                                )}
                              </div>
                            )}

                            {/* Line items */}
                            <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-text-faint)', letterSpacing: '0.06em', marginBottom: 10 }}>LINE ITEMS</div>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                              <thead>
                                <tr style={{ borderBottom: '1px solid var(--hf-border)' }}>
                                  {['Description', 'Qty', 'Unit Price', 'VAT %', 'Total'].map(h => (
                                    <th key={h} style={{ textAlign: 'left', padding: '6px 12px', fontSize: 11, fontWeight: 600, color: 'var(--hf-text-muted)', textTransform: 'uppercase' as const }}>{h}</th>
                                  ))}
                                </tr>
                              </thead>
                              <tbody>
                                {inv.lineItems.map((li, i) => (
                                  <tr key={i} style={{ borderBottom: i < inv.lineItems.length - 1 ? '1px solid var(--hf-border-subtle)' : 'none' }}>
                                    <td style={{ padding: '8px 12px', color: 'var(--hf-text-secondary)' }}>{li.description}</td>
                                    <td style={{ padding: '8px 12px', color: 'var(--hf-text-muted)' }}>{li.quantity}</td>
                                    <td style={{ padding: '8px 12px', color: 'var(--hf-text-muted)' }}>{fmtR(li.unitPrice)}</td>
                                    <td style={{ padding: '8px 12px', color: 'var(--hf-text-muted)' }}>{li.vatRate ?? 15}%</td>
                                    <td style={{ padding: '8px 12px', fontWeight: 600, color: 'var(--hf-text)' }}>{fmtR(li.lineTotal)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 12 }}>
                              <div style={{ minWidth: 240 }}>
                                {[['Subtotal', fmtR(inv.subtotal)], ['VAT', fmtR(inv.vatTotal)]].map(([l, v]) => (
                                  <div key={l} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: 13, color: 'var(--hf-text-muted)' }}>
                                    <span>{l}</span><span>{v}</span>
                                  </div>
                                ))}
                                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0 0', fontSize: 15, fontWeight: 700, color: 'var(--hf-text)', borderTop: '1px solid var(--hf-border)', marginTop: 4 }}>
                                  <span>Total</span><span>{fmtR(inv.total)}</span>
                                </div>
                              </div>
                            </div>

                            {/* Credit notes */}
                            <div style={{ marginTop: 20, paddingTop: 16, borderTop: '1px solid var(--hf-border)' }}>
                              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-text-faint)', letterSpacing: '0.06em' }}>CREDIT NOTES</div>
                                {inv.status !== 'DRAFT' && (
                                  <button onClick={() => { setCreditNoteModal(inv); setCreditNoteError('') }}
                                    style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'white', color: 'var(--hf-danger-text-strong)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, padding: '6px 12px', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                                    <FileMinus size={13} /> Issue credit note
                                  </button>
                                )}
                              </div>
                              {creditNotes.length === 0 ? (
                                <div style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>No credit notes issued against this invoice.</div>
                              ) : (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                                  {creditNotes.map(cn => (
                                    <div key={cn.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, padding: '8px 12px' }}>
                                      <div>
                                        <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--hf-text)' }}>{cn.creditNoteNumber}</span>
                                        <span style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginLeft: 8 }}>{cn.reason}</span>
                                      </div>
                                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                        <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--hf-danger-text-strong)' }}>{fmtR(cn.total)}</span>
                                        <button onClick={() => downloadCreditNotePdf(cn)}
                                          style={{ display: 'flex', alignItems: 'center', gap: 4, background: 'none', border: 'none', color: 'var(--hf-primary-text)', cursor: 'pointer', fontSize: 12 }}>
                                          <Download size={12} /> PDF
                                        </button>
                                      </div>
                                    </div>
                                  ))}
                                </div>
                              )}
                            </div>
                          </div>
                        </td>
                      </tr>
                    )}
                  </>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* Mark as Paid modal */}
      {payModal && (
        <Modal title="Record Payment"
          subtitle={`${payModal.invoiceNumber} · ${fmtR(payModal.total)}${payModal.amountPaid > 0 ? ` · Paid ${fmtR(payModal.amountPaid)}` : ''}`}
          onClose={() => { setPayModal(null); setPayError('') }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {[
              { label: 'Amount Received (R) *', key: 'amount',    type: 'number', placeholder: '' },
              { label: 'Payment Reference',     key: 'reference', type: 'text',   placeholder: 'e.g. EFT-20260530-001' },
              { label: 'Note (optional)',        key: 'note',      type: 'text',   placeholder: 'e.g. Paid in full' },
            ].map(f => (
              <div key={f.key}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>{f.label}</label>
                <input type={f.type} value={(payForm as any)[f.key]}
                  onChange={e => setPayForm(p => ({ ...p, [f.key]: e.target.value }))}
                  placeholder={f.placeholder} style={inp} />
              </div>
            ))}
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>Payment Method</label>
              <select value={payForm.paymentMethod} onChange={e => setPayForm(p => ({ ...p, paymentMethod: e.target.value }))}
                style={{ ...inp, appearance: 'none' as const }}>
                {['EFT', 'CASH', 'CARD', 'CHEQUE', 'OTHER'].map(m => <option key={m}>{m}</option>)}
              </select>
            </div>
          </div>
          {payError && <ErrorBanner msg={payError} />}
          <ModalFooter
            onCancel={() => { setPayModal(null); setPayError('') }}
            onConfirm={() => markPaid.mutate({ id: payModal.id, body: { amountPaid: Number(payForm.amount), paymentMethod: payForm.paymentMethod, reference: payForm.reference || undefined, paidDate: undefined } })}
            confirmLabel={markPaid.isPending ? 'Saving...' : 'Confirm payment'}
            disabled={!payForm.amount || markPaid.isPending}
            confirmColor="#16A34A" />
        </Modal>
      )}

      {/* Log Hours modal */}
      {hoursModal && (
        <Modal title="Log Hours"
          subtitle={`${hoursModal.invoiceNumber} · ${hoursModal.hoursConsumed ?? 0}h used of ${hoursModal.committedHours}h committed`}
          onClose={() => { setHoursModal(null); setHoursError('') }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>Hours to log *</label>
              <input type="number" min="0.01" step="0.25" value={hoursForm.hours}
                onChange={e => setHoursForm(f => ({ ...f, hours: e.target.value }))}
                placeholder="e.g. 4.5" style={inp} autoFocus />
              <p style={{ fontSize: 11, color: 'var(--hf-text-faint)', margin: '4px 0 0' }}>
                Remaining commitment: {Math.max(0, (hoursModal.committedHours ?? 0) - (hoursModal.hoursConsumed ?? 0)).toFixed(2)}h
              </p>
            </div>
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>Note (optional)</label>
              <input type="text" value={hoursForm.note}
                onChange={e => setHoursForm(f => ({ ...f, note: e.target.value }))}
                placeholder="e.g. Dozer on site 07:00–13:00, load count 48" style={inp} />
            </div>
            {Number(hoursForm.hours) > 0 && (Number(hoursForm.hours) + (hoursModal.hoursConsumed ?? 0)) > (hoursModal.committedHours ?? Infinity) && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'var(--hf-warning-soft)', border: '1px solid var(--hf-warning-border-strong)', borderRadius: 8, padding: '10px 12px', fontSize: 12, color: 'var(--hf-warning-text-deep)' }}>
                <AlertTriangle size={14} color="#F59E0B" />
                This will exceed the committed hours — invoice will enter <strong>overage</strong>.
              </div>
            )}
          </div>
          {hoursError && <ErrorBanner msg={hoursError} />}
          <ModalFooter
            onCancel={() => { setHoursModal(null); setHoursError('') }}
            onConfirm={() => logHours.mutate({ id: hoursModal.id, body: { hours: Number(hoursForm.hours), note: hoursForm.note || undefined } })}
            confirmLabel={logHours.isPending ? 'Saving...' : 'Log hours'}
            disabled={!hoursForm.hours || logHours.isPending}
            confirmColor="#7C3AED" />
        </Modal>
      )}

      {/* Issue Credit Note modal */}
      {creditNoteModal && (
        <Modal title="Issue Credit Note"
          subtitle={`${creditNoteModal.invoiceNumber} · ${fmtR(creditNoteModal.total)}`}
          onClose={() => { setCreditNoteModal(null); setCreditNoteError('') }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>Reason *</label>
              <input type="text" value={creditNoteForm.reason}
                onChange={e => setCreditNoteForm(f => ({ ...f, reason: e.target.value }))}
                placeholder="e.g. Overcharged for materials" style={inp} autoFocus />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>Amount (R, excl. VAT) *</label>
                <input type="number" min="0.01" step="0.01" value={creditNoteForm.amount}
                  onChange={e => setCreditNoteForm(f => ({ ...f, amount: e.target.value }))}
                  placeholder="e.g. 500.00" style={inp} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>VAT rate (%)</label>
                <input type="number" min="0" max="100" step="0.5" value={creditNoteForm.vatRate}
                  onChange={e => setCreditNoteForm(f => ({ ...f, vatRate: e.target.value }))} style={inp} />
              </div>
            </div>
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>Description (optional)</label>
              <input type="text" value={creditNoteForm.description}
                onChange={e => setCreditNoteForm(f => ({ ...f, description: e.target.value }))}
                placeholder="Additional detail for the client" style={inp} />
            </div>
            {creditNoteForm.amount && (
              <div style={{ padding: '10px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text-strong)' }}>
                Total to credit: {fmtR(Number(creditNoteForm.amount) * (1 + Number(creditNoteForm.vatRate || 0) / 100))}
              </div>
            )}
          </div>
          {creditNoteError && <ErrorBanner msg={creditNoteError} />}
          <ModalFooter
            onCancel={() => { setCreditNoteModal(null); setCreditNoteError('') }}
            onConfirm={() => createCreditNote.mutate({
              reason: creditNoteForm.reason,
              description: creditNoteForm.description || undefined,
              amount: Number(creditNoteForm.amount),
              vatRate: Number(creditNoteForm.vatRate || 15),
            })}
            confirmLabel={createCreditNote.isPending ? 'Issuing...' : 'Issue credit note'}
            disabled={!creditNoteForm.reason || !creditNoteForm.amount || createCreditNote.isPending}
            confirmColor="#B43C32" />
        </Modal>
      )}
    </div>
  )
}

// ── Recurring Tab ─────────────────────────────────────────────────────────────
function RecurringTab() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [expanded, setExpanded] = useState<string | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['recurring-schedules'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/invoicing/recurring-schedules?size=100&sort=createdAt,desc')
      const payload = res.data?.data ?? res.data
      return payload as { content: RecurringSchedule[]; totalElements: number }
    },
  })

  const schedules = data?.content ?? []

  const { data: customers = [] } = useQuery({
    queryKey: ['customers-map'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/crm/customers?size=500')
      const payload = res.data?.data ?? res.data
      return (payload.content ?? payload) as { id: string; name: string }[]
    },
  })
  const customerMap = Object.fromEntries(customers.map(c => [c.id, c.name]))

  const pause = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/invoicing/recurring-schedules/${id}/pause`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['recurring-schedules'] }),
  })
  const resume = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/invoicing/recurring-schedules/${id}/resume`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['recurring-schedules'] }),
  })
  const cancel = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/invoicing/recurring-schedules/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['recurring-schedules'] }),
  })

  // FIX: "no log cycle hours UI" gap — the backend
  // (logCycleHours/resolveBillableHours) was fully built with no way to
  // reach it from the UI. Mirrors InvoicesTab's existing "Log hrs" pattern
  // for retainers, but posts to the variable-hours-contract endpoint and
  // also invalidates the invoices list, since this call generates a new
  // issued invoice as a side effect.
  const [cycleHoursModal, setCycleHoursModal] = useState<RecurringSchedule | null>(null)
  const [cycleHoursForm, setCycleHoursForm] = useState({ actualHours: '', periodLabel: '', operatorNotes: '' })
  const [cycleHoursError, setCycleHoursError] = useState('')

  const logCycleHours = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/invoicing/recurring-schedules/${id}/log-cycle-hours`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['recurring-schedules'] })
      qc.invalidateQueries({ queryKey: ['invoices'] })
      setCycleHoursModal(null); setCycleHoursError('')
      setCycleHoursForm({ actualHours: '', periodLabel: '', operatorNotes: '' })
    },
    onError: (e: any) => setCycleHoursError(e.response?.data?.message ?? 'Failed to log cycle hours.'),
  })

  const active    = schedules.filter(s => s.status === 'ACTIVE').length
  const paused    = schedules.filter(s => s.status === 'PAUSED').length
  const monthlyMRR = schedules
    .filter(s => s.status === 'ACTIVE' && s.frequency === 'MONTHLY')
    .reduce((sum, s) => sum + s.total, 0)

  const displayName = (s: RecurringSchedule) => {
    if (!s.customerId) return s.walkinClientName ?? <span style={{ color: 'var(--hf-text-faint)', fontStyle: 'italic' }}>Walk-in</span>
    return customerMap[s.customerId] ?? s.customerId.slice(0, 8) + '…'
  }

  const freqLabel = (s: RecurringSchedule) => {
    if (s.frequency === 'CUSTOM' && s.customIntervalDays) return `Every ${s.customIntervalDays}d`
    return FREQ_LABEL[s.frequency] ?? s.frequency
  }

  return (
    <div>
      {/* Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 20 }}>
        {[
          { label: 'Total schedules', value: schedules.length, fmt: false, color: 'var(--hf-primary-text)' },
          { label: 'Active',          value: active,           fmt: false, color: 'var(--hf-success-text-strong)' },
          { label: 'Paused',          value: paused,           fmt: false, color: 'var(--hf-warning-text-deep)' },
          { label: 'Monthly MRR',     value: fmtR(monthlyMRR), fmt: true,  color: 'var(--hf-accent-text)' },
        ].map(s => (
          <div key={s.label} style={{ background: 'white', border: '1px solid var(--hf-border)', borderRadius: 12, padding: '14px 18px' }}>
            <div style={{ fontSize: s.fmt ? 18 : 24, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Info callout */}
      <div style={{ marginBottom: 16, padding: '12px 16px', background: 'var(--hf-success-soft)', border: '1px solid var(--hf-success-border-subtle)', borderRadius: 10, fontSize: 13, color: 'var(--hf-success-text-strong)', display: 'flex', alignItems: 'flex-start', gap: 10 }}>
        <RefreshCw size={15} style={{ marginTop: 1, flexShrink: 0 }} />
        <div>
          Schedules fire automatically at <strong>02:45 every morning</strong>. Each run creates a new issued invoice linked back to this schedule.
          Monthly schedules are great for site security fees, equipment lease charges, and management retainers.
        </div>
      </div>

      <div style={{ background: 'white', border: '1px solid var(--hf-border)', borderRadius: 14, overflow: 'hidden' }}>
        {isLoading ? <LoadingRow text="Loading schedules..." /> :
         isError   ? <ErrorRow /> :
         schedules.length === 0 ? (
          <div style={{ padding: '60px 24px', textAlign: 'center' }}>
            <RefreshCw size={36} color="#CBD5E1" style={{ marginBottom: 12 }} />
            <div style={{ fontWeight: 600, color: 'var(--hf-text-tertiary)', marginBottom: 4 }}>No recurring schedules yet</div>
            <div style={{ fontSize: 13, color: 'var(--hf-text-faint)', marginBottom: 20 }}>
              Set up automatic monthly invoicing for mining site security, equipment rentals, or any regular service.
            </div>
            <button onClick={() => navigate('/recurring/new')}
              style={{ display: 'inline-flex', alignItems: 'center', gap: 7, background: 'var(--hf-accent)', color: 'white', border: 'none', borderRadius: 10, padding: '10px 20px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
              <Plus size={15} /> Create first schedule
            </button>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--hf-border-subtle)', background: 'var(--hf-surface-muted)' }}>
                {['Schedule', 'Customer', 'Frequency', 'Amount', 'Next run', 'Status', 'Actions'].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '11px 16px', fontSize: 11, fontWeight: 700, color: 'var(--hf-text-faint)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {schedules.map(s => {
                const sc    = RECURRING_STATUS[s.status] ?? RECURRING_STATUS.ACTIVE
                const Icon  = sc.icon
                const isExp = expanded === s.id

                return (
                  <>
                    <tr key={s.id}
                      onClick={() => setExpanded(isExp ? null : s.id)}
                      style={{ borderBottom: '1px solid var(--hf-border-subtle)', cursor: 'pointer', background: isExp ? 'var(--hf-accent-soft)' : 'white' }}
                      onMouseEnter={e => { if (!isExp) (e.currentTarget as HTMLElement).style.background = 'var(--hf-surface-muted)' }}
                      onMouseLeave={e => { if (!isExp) (e.currentTarget as HTMLElement).style.background = 'white' }}>

                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <RefreshCw size={14} color="#0D9488" />
                          <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--hf-text)' }}>{s.title}</span>
                          {isExp ? <ChevronUp size={13} color="#94A3B8" /> : <ChevronDown size={13} color="#94A3B8" />}
                        </div>
                      </td>
                      <td style={{ padding: '14px 16px', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)' }}>{displayName(s)}</td>
                      <td style={{ padding: '14px 16px' }}>
                        <span style={{ fontSize: 13, background: 'var(--hf-surface-sunken)', color: 'var(--hf-text-tertiary)', padding: '3px 9px', borderRadius: 12, fontWeight: 600 }}>{freqLabel(s)}</span>
                      </td>
                      <td style={{ padding: '14px 16px', fontSize: 14, fontWeight: 700, color: 'var(--hf-text)' }}>{fmtR(s.total)}</td>
                      <td style={{ padding: '14px 16px', fontSize: 13, color: 'var(--hf-text-muted)' }}>{fmtDate(s.nextRunAt)}</td>
                      <td style={{ padding: '14px 16px' }}>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: sc.bg, color: sc.color, fontSize: 12, fontWeight: 700, padding: '4px 10px', borderRadius: 20 }}>
                          <Icon size={11} />{sc.label}
                        </span>
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ display: 'flex', gap: 5 }} onClick={e => e.stopPropagation()}>
                          {s.status === 'ACTIVE' && (
                            <ActionBtn icon={Pause} label="Pause" color="#92400E" bg="#FEF3C7" border="#FCD34D"
                              onClick={() => pause.mutate(s.id)} disabled={pause.isPending} />
                          )}
                          {s.variableHours && s.status === 'ACTIVE' && (
                            <ActionBtn icon={Timer} label="Log hrs" color="#D97706" bg="#FFFBEB" border="#FDE68A"
                              onClick={() => { setCycleHoursModal(s); setCycleHoursError(''); setCycleHoursForm({ actualHours: '', periodLabel: '', operatorNotes: '' }) }} />
                          )}
                          {s.status === 'PAUSED' && (
                            <ActionBtn icon={Play} label="Resume" color="#166534" bg="#F0FDF4" border="#BBF7D0"
                              onClick={() => resume.mutate(s.id)} disabled={resume.isPending} />
                          )}
                          {['ACTIVE','PAUSED'].includes(s.status) && (
                            <ActionBtn icon={Trash2} label="Cancel" color="#DC2626" bg="#FEF2F2" border="#FECACA"
                              onClick={() => { if (confirm(`Cancel recurring schedule "${s.title}"?`)) cancel.mutate(s.id) }}
                              disabled={cancel.isPending} />
                          )}
                        </div>
                      </td>
                    </tr>

                    {isExp && (
                      <tr key={`${s.id}-exp`}>
                        <td colSpan={7} style={{ padding: 0 }}>
                          <div style={{ background: 'var(--hf-accent-soft)', borderBottom: '1px solid var(--hf-accent-border)', padding: '16px 24px' }}>
                            {s.lastRunAt && (
                              <p style={{ fontSize: 12, color: 'var(--hf-accent-text-strong)', margin: '0 0 12px', display: 'flex', alignItems: 'center', gap: 6 }}>
                                <CheckCircle size={13} /> Last invoice generated: {fmtDate(s.lastRunAt)}
                              </p>
                            )}

                            {s.variableHours ? (
                              <div style={{ background: 'white', border: '1px solid var(--hf-warning-border)', borderRadius: 10, padding: '14px 18px' }}>
                                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-warning-text)', letterSpacing: '0.06em', marginBottom: 10 }}>VARIABLE-HOURS CONTRACT</div>
                                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
                                  {[
                                    { label: 'Rate/hr',           value: fmtR(s.ratePerHour) + '/hr' },
                                    { label: 'Minimum hrs/cycle', value: s.minimumHoursPerCycle != null ? `${s.minimumHoursPerCycle}h` : '—' },
                                    { label: 'Total hours billed',value: `${s.totalHoursBilled ?? 0}h` },
                                    { label: 'Contracted total',  value: s.contractedTotalHours != null ? `${s.contractedTotalHours}h` : 'Not set' },
                                  ].map(m => (
                                    <div key={m.label}>
                                      <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginBottom: 3 }}>{m.label}</div>
                                      <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--hf-text)' }}>{m.value}</div>
                                    </div>
                                  ))}
                                </div>
                                <div style={{ marginTop: 10, fontSize: 12, color: 'var(--hf-warning-text-deep)' }}>
                                  {s.remainingCycles >= 0 ? `~${s.remainingCycles} cycle${s.remainingCycles === 1 ? '' : 's'} remaining on contract` : 'Open-ended contract (no end date)'}
                                  {s.contractStartDate && ` · Started ${fmtDate(s.contractStartDate)}`}
                                  {s.contractEndDate && ` · Ends ${fmtDate(s.contractEndDate)}`}
                                </div>
                              </div>
                            ) : (
                              <>
                                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-accent-text-strong)', letterSpacing: '0.06em', marginBottom: 10 }}>TEMPLATE LINE ITEMS</div>
                                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                                  <thead>
                                    <tr style={{ borderBottom: '1px solid var(--hf-accent-border)' }}>
                                      {['Description', 'Qty', 'Unit Price', 'VAT %', 'Line Total'].map(h => (
                                        <th key={h} style={{ textAlign: 'left', padding: '6px 12px', fontSize: 11, fontWeight: 600, color: 'var(--hf-accent-text-strong)', textTransform: 'uppercase' as const }}>{h}</th>
                                      ))}
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {s.lineItems.map((li: any, i: number) => (
                                      <tr key={i} style={{ borderBottom: i < s.lineItems.length - 1 ? '1px solid #F0FDFA' : 'none' }}>
                                        <td style={{ padding: '8px 12px', color: 'var(--hf-text-secondary)' }}>{li.description}</td>
                                        <td style={{ padding: '8px 12px', color: 'var(--hf-text-muted)' }}>{li.quantity}</td>
                                        <td style={{ padding: '8px 12px', color: 'var(--hf-text-muted)' }}>{fmtR(li.unitPrice)}</td>
                                        <td style={{ padding: '8px 12px', color: 'var(--hf-text-muted)' }}>{li.vatRate ?? 15}%</td>
                                        <td style={{ padding: '8px 12px', fontWeight: 600, color: 'var(--hf-text)' }}>{fmtR(li.lineTotal)}</td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                                <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 12 }}>
                                  <div style={{ minWidth: 240 }}>
                                    {[['Subtotal', fmtR(s.subtotal)], ['VAT', fmtR(s.vatTotal)]].map(([l, v]) => (
                                      <div key={l} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: 13, color: 'var(--hf-text-muted)' }}>
                                        <span>{l}</span><span>{v}</span>
                                      </div>
                                    ))}
                                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0 0', fontSize: 15, fontWeight: 700, color: 'var(--hf-text)', borderTop: '1px solid var(--hf-success-border-subtle)', marginTop: 4 }}>
                                      <span>Per invoice</span><span>{fmtR(s.total)}</span>
                                    </div>
                                  </div>
                                </div>
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* Log Cycle Hours modal */}
      {cycleHoursModal && (
        <Modal title="Log Cycle Hours"
          subtitle={`${cycleHoursModal.title} · minimum ${cycleHoursModal.minimumHoursPerCycle ?? 0}h/cycle at ${fmtR(cycleHoursModal.ratePerHour)}/hr`}
          onClose={() => { setCycleHoursModal(null); setCycleHoursError('') }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>Actual hours worked *</label>
              <input type="number" min="0" step="0.25" value={cycleHoursForm.actualHours}
                onChange={e => setCycleHoursForm(f => ({ ...f, actualHours: e.target.value }))}
                placeholder="e.g. 187.5" style={inp} autoFocus />
              <p style={{ fontSize: 11, color: 'var(--hf-text-faint)', margin: '4px 0 0' }}>
                Minimum billed regardless of hours worked: {cycleHoursModal.minimumHoursPerCycle ?? 0}h
              </p>
            </div>
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>Period label *</label>
              <input type="text" value={cycleHoursForm.periodLabel}
                onChange={e => setCycleHoursForm(f => ({ ...f, periodLabel: e.target.value }))}
                placeholder="e.g. June 2026" style={inp} />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>Operator notes (optional)</label>
              <input type="text" value={cycleHoursForm.operatorNotes}
                onChange={e => setCycleHoursForm(f => ({ ...f, operatorNotes: e.target.value }))}
                placeholder="e.g. Down 3 days for scheduled service" style={inp} />
            </div>
            {Number(cycleHoursForm.actualHours) > 0 && Number(cycleHoursForm.actualHours) < (cycleHoursModal.minimumHoursPerCycle ?? 0) && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'var(--hf-warning-soft)', border: '1px solid var(--hf-warning-border-strong)', borderRadius: 8, padding: '10px 12px', fontSize: 12, color: 'var(--hf-warning-text-deep)' }}>
                <AlertTriangle size={14} color="#F59E0B" />
                Below the {cycleHoursModal.minimumHoursPerCycle}h minimum — the invoice will bill the minimum, not the actual hours worked.
              </div>
            )}
          </div>
          {cycleHoursError && <ErrorBanner msg={cycleHoursError} />}
          <ModalFooter
            onCancel={() => { setCycleHoursModal(null); setCycleHoursError('') }}
            onConfirm={() => logCycleHours.mutate({
              id: cycleHoursModal.id,
              body: {
                actualHours: Number(cycleHoursForm.actualHours),
                periodLabel: cycleHoursForm.periodLabel,
                operatorNotes: cycleHoursForm.operatorNotes || undefined,
              },
            })}
            confirmLabel={logCycleHours.isPending ? 'Saving...' : 'Log hours & generate invoice'}
            disabled={!cycleHoursForm.actualHours || !cycleHoursForm.periodLabel || logCycleHours.isPending}
            confirmColor="#D97706" />
        </Modal>
      )}
    </div>
  )
}

// ── Shared UI primitives ──────────────────────────────────────────────────────
function ActionBtn({ icon: Icon, label, color, bg, border, onClick, disabled }: {
  icon: React.ElementType; label: string; color: string; bg: string; border: string
  onClick: () => void; disabled?: boolean
}) {
  return (
    <button disabled={disabled} onClick={onClick}
      style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 10px',
        background: disabled ? 'var(--hf-surface-muted)' : bg, color: disabled ? 'var(--hf-text-disabled)' : color,
        border: `1px solid ${disabled ? '#E2E8F0' : border}`,
        borderRadius: 7, fontSize: 12, fontWeight: 600,
        cursor: disabled ? 'not-allowed' : 'pointer', whiteSpace: 'nowrap' as const }}>
      <Icon size={12} />{label}
    </button>
  )
}

function Modal({ title, subtitle, children, onClose }: {
  title: string; subtitle?: string; children: React.ReactNode; onClose: () => void
}) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: 'white', borderRadius: 16, padding: 28, width: 460, boxShadow: '0 20px 60px rgba(0,0,0,0.2)', maxHeight: '90vh', overflowY: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20 }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: 'var(--hf-text)' }}>{title}</h3>
            {subtitle && <p style={{ margin: '3px 0 0', fontSize: 13, color: 'var(--hf-text-faint)' }}>{subtitle}</p>}
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'flex', padding: 2 }}><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

function ModalFooter({ onCancel, onConfirm, confirmLabel, disabled, confirmColor }: {
  onCancel: () => void; onConfirm: () => void; confirmLabel: string
  disabled?: boolean; confirmColor: string
}) {
  return (
    <div style={{ display: 'flex', gap: 10, marginTop: 22, justifyContent: 'flex-end' }}>
      <button onClick={onCancel}
        style={{ padding: '10px 18px', border: '1px solid var(--hf-border)', borderRadius: 9, background: 'white', fontSize: 14, cursor: 'pointer', color: 'var(--hf-text-secondary)' }}>
        Cancel
      </button>
      <button disabled={disabled} onClick={onConfirm}
        style={{ padding: '10px 22px', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700,
          background: disabled ? 'var(--hf-surface-strong)' : confirmColor,
          color: disabled ? 'var(--hf-text-faint)' : 'white',
          cursor: disabled ? 'not-allowed' : 'pointer' }}>
        {confirmLabel}
      </button>
    </div>
  )
}

function ErrorBanner({ msg }: { msg: string }) {
  return (
    <div style={{ marginTop: 14, padding: '10px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)', display: 'flex', alignItems: 'center', gap: 8 }}>
      <AlertCircle size={15} style={{ flexShrink: 0 }} />{msg}
    </div>
  )
}

function LoadingRow({ text }: { text: string }) {
  return <div style={{ padding: 60, textAlign: 'center', color: 'var(--hf-text-faint)' }}>{text}</div>
}

function ErrorRow() {
  return (
    <div style={{ padding: 60, textAlign: 'center' }}>
      <AlertCircle size={32} color="#DC2626" style={{ marginBottom: 10 }} />
      <div style={{ fontWeight: 600, color: 'var(--hf-danger-text)' }}>Failed to load data</div>
      <div style={{ fontSize: 13, color: 'var(--hf-text-faint)', marginTop: 4 }}>Please refresh and try again.</div>
    </div>
  )
}

function EmptyRow({ icon: Icon, text, sub }: { icon: React.ElementType; text: string; sub: string }) {
  return (
    <div style={{ padding: 60, textAlign: 'center', color: 'var(--hf-text-faint)' }}>
      <Icon size={36} color="#CBD5E1" style={{ marginBottom: 12 }} />
      <div style={{ fontWeight: 600, color: 'var(--hf-text-tertiary)', marginBottom: 4 }}>{text}</div>
      <div style={{ fontSize: 13 }}>{sub}</div>
    </div>
  )
}
