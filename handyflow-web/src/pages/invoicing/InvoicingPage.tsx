// src/pages/invoicing/InvoicingPage.tsx
import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Plus, FileText, Download, ChevronDown, ChevronUp,
  CheckCircle, Send, X, Receipt, FileCheck, Clock,
  AlertCircle, XCircle, Eye,
} from 'lucide-react'
import { apiClient } from '../../api/client'

// ── Types ─────────────────────────────────────────────────────────────────────
type QuoteStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'INVOICED'

interface Quote {
  id: string; quoteNumber: string; title: string; status: QuoteStatus
  total: number; expiresAt: string | null; createdAt: string; customerId: string | null
}

interface Invoice {
  id: string; invoiceNumber: string; customerId: string | null; status: string
  issuedAt: string | null; dueDate: string | null; subtotal: number; vatTotal: number
  total: number; amountPaid: number; lineItems: any[]; createdAt: string
}

// ── Status configs ────────────────────────────────────────────────────────────
const QUOTE_STATUS: Record<QuoteStatus, { label: string; bg: string; color: string; icon: React.ElementType }> = {
  DRAFT:    { label: 'Draft',    bg: '#F1F5F9', color: '#475569', icon: AlertCircle },
  SENT:     { label: 'Sent',     bg: '#EFF6FF', color: '#1D4ED8', icon: Send },
  ACCEPTED: { label: 'Accepted', bg: '#F0FDF4', color: '#166534', icon: CheckCircle },
  REJECTED: { label: 'Rejected', bg: '#FEF2F2', color: '#DC2626', icon: XCircle },
  EXPIRED:  { label: 'Expired',  bg: '#FEF3C7', color: '#92400E', icon: Clock },
  INVOICED: { label: 'Invoiced', bg: '#F3E8FF', color: '#7C3AED', icon: FileCheck },
}

const INVOICE_STATUS: Record<string, { label: string; bg: string; color: string }> = {
  DRAFT:          { bg: '#F1F5F9', color: '#475569', label: 'Draft' },
  ISSUED:         { bg: '#EFF6FF', color: '#1D4ED8', label: 'Issued' },
  PARTIALLY_PAID: { bg: '#FEF3C7', color: '#92400E', label: 'Part paid' },
  PAID:           { bg: '#F0FDF4', color: '#166534', label: 'Paid' },
  OVERDUE:        { bg: '#FEF2F2', color: '#DC2626', label: 'Overdue' },
  CANCELLED:      { bg: '#F8FAFC', color: '#94A3B8', label: 'Cancelled' },
}

// ── Helpers ───────────────────────────────────────────────────────────────────
const fmtR = (n: number | null | undefined) =>
  n == null ? '—' : `R ${n.toLocaleString('en-ZA', { minimumFractionDigits: 2 })}`

const fmtDate = (d: string | null) =>
  d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'

const inp: React.CSSProperties = {
  width: '100%', padding: '10px 12px', border: '1.5px solid #E2E8F0',
  borderRadius: 9, fontSize: 14, boxSizing: 'border-box', background: 'white', outline: 'none',
}

// ── Main Page ─────────────────────────────────────────────────────────────────
export function InvoicingPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const activeTab = location.pathname.startsWith('/invoices') ? 'invoices' : 'quotes'

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>

      {/* Page header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', margin: '0 0 4px' }}>Invoicing</h1>
          <p style={{ fontSize: 13, color: '#94A3B8', margin: 0 }}>Quotes, invoices and payment tracking</p>
        </div>
        {activeTab === 'quotes' && (
          <button onClick={() => navigate('/quotes/new')}
            style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 10, padding: '10px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
            <Plus size={15} /> New Quote
          </button>
        )}
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 24, background: '#F1F5F9', borderRadius: 12, padding: 4, width: 'fit-content' }}>
        {[
          { key: 'quotes',   label: 'Quotes',   icon: FileText, path: '/quotes' },
          { key: 'invoices', label: 'Invoices', icon: Receipt,  path: '/invoices' },
        ].map(tab => (
          <button key={tab.key} onClick={() => navigate(tab.path)}
            style={{
              display: 'flex', alignItems: 'center', gap: 7,
              padding: '8px 20px', borderRadius: 9, border: 'none',
              fontSize: 14, fontWeight: 600, cursor: 'pointer',
              background: activeTab === tab.key ? 'white' : 'transparent',
              color: activeTab === tab.key ? '#1B3A6B' : '#64748B',
              boxShadow: activeTab === tab.key ? '0 1px 4px rgba(0,0,0,0.1)' : 'none',
              transition: 'all 0.15s',
            }}>
            <tab.icon size={15} /> {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'quotes' ? <QuotesTab /> : <InvoicesTab />}
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
      const payload = res.data?.data ?? res.data
      return payload as { content: Quote[]; totalElements: number }
    },
  })

  const quotes = data?.content ?? []

  return (
    <div>
      {/* Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 20 }}>
        {[
          { label: 'Total',    value: quotes.length,                                      color: '#1B3A6B' },
          { label: 'Draft',    value: quotes.filter(q => q.status === 'DRAFT').length,    color: '#475569' },
          { label: 'Sent',     value: quotes.filter(q => q.status === 'SENT').length,     color: '#1D4ED8' },
          { label: 'Accepted', value: quotes.filter(q => q.status === 'ACCEPTED').length, color: '#166534' },
        ].map(s => (
          <div key={s.label} style={{ background: 'white', border: '1px solid #E2E8F0', borderRadius: 12, padding: '14px 18px' }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: '#64748B', marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ background: 'white', border: '1px solid #E2E8F0', borderRadius: 14, overflow: 'hidden' }}>
        {isLoading ? (
          <div style={{ padding: 60, textAlign: 'center', color: '#94A3B8' }}>Loading quotes...</div>
        ) : isError ? (
          <div style={{ padding: 60, textAlign: 'center' }}>
            <AlertCircle size={32} color="#DC2626" style={{ marginBottom: 10 }} />
            <div style={{ fontWeight: 600, color: '#DC2626' }}>Failed to load quotes</div>
            <div style={{ fontSize: 13, color: '#94A3B8', marginTop: 4 }}>Please refresh and try again.</div>
          </div>
        ) : quotes.length === 0 ? (
          <div style={{ padding: 60, textAlign: 'center', color: '#94A3B8' }}>
            <FileText size={36} color="#CBD5E1" style={{ marginBottom: 12 }} />
            <div style={{ fontWeight: 600, color: '#475569', marginBottom: 4 }}>No quotes yet</div>
            <div style={{ fontSize: 13 }}>Create your first quote to get started.</div>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #F1F5F9', background: '#F8FAFC' }}>
                {['Quote #', 'Title', 'Status', 'Total', 'Expires', ''].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '11px 16px', fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{h}</th>
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
                    style={{ borderBottom: '1px solid #F8FAFC', cursor: 'pointer' }}
                    onMouseEnter={e => (e.currentTarget.style.background = '#F8FAFC')}
                    onMouseLeave={e => (e.currentTarget.style.background = 'white')}>
                    <td style={{ padding: '14px 16px' }}>
                      <span style={{ fontFamily: 'monospace', fontSize: 13, fontWeight: 700, color: '#1D4ED8' }}>{q.quoteNumber}</span>
                    </td>
                    <td style={{ padding: '14px 16px', fontSize: 14, fontWeight: 600, color: '#0F172A' }}>{q.title}</td>
                    <td style={{ padding: '14px 16px' }}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: s.bg, color: s.color, fontSize: 12, fontWeight: 700, padding: '4px 10px', borderRadius: 20 }}>
                        <Icon size={11} />{s.label}
                      </span>
                    </td>
                    <td style={{ padding: '14px 16px', fontSize: 14, fontWeight: 700, color: '#0F172A' }}>{fmtR(q.total)}</td>
                    <td style={{ padding: '14px 16px', fontSize: 13, color: '#64748B' }}>
                      {q.expiresAt ? new Date(q.expiresAt).toLocaleDateString('en-ZA') : '—'}
                    </td>
                    <td style={{ padding: '14px 16px' }}>
                      <Eye size={15} color="#94A3B8" />
                    </td>
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
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [payForm, setPayForm] = useState({ amount: '', paymentMethod: 'EFT', reference: '', note: '' })
  const [payError, setPayError] = useState('')

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
        const data = e.response?.data
        // Pull the first field-level error if available
        if (data?.data && typeof data.data === 'object') {
            const firstError = Object.values(data.data)[0]
            setPayError(`${Object.keys(data.data)[0]}: ${firstError}`)
        } else {
            setPayError(data?.message ?? 'Failed to record payment. Please try again.')
        }
        },
  })

  const downloadPdf = async (inv: Invoice) => {
    setDownloading(inv.id)
    try {
      const res = await apiClient.get(`/api/v1/invoicing/invoices/${inv.id}/pdf`, { responseType: 'blob' } as any)
      const url = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
      const a = document.createElement('a')
      a.href = url; a.download = `${inv.invoiceNumber}.pdf`; a.click()
      URL.revokeObjectURL(url)
    } catch {
      alert('Failed to download PDF. Please try again.')
    } finally {
      setDownloading(null)
    }
  }

  const filtered = statusFilter === 'ALL' ? invoices : invoices.filter(i => i.status === statusFilter)

  const totalRevenue     = invoices.filter(i => i.status === 'PAID').reduce((s, i) => s + i.total, 0)
  const totalOutstanding = invoices.filter(i => ['ISSUED', 'PARTIALLY_PAID', 'OVERDUE'].includes(i.status)).reduce((s, i) => s + i.total, 0)
  const overdueCount     = invoices.filter(i => i.status === 'OVERDUE').length

  const customerName = (id: string | null) => {
    if (!id) return <span style={{ color: '#94A3B8', fontStyle: 'italic' }}>Walk-in client</span>
    return customerMap[id] ?? <span style={{ color: '#94A3B8', fontSize: 12 }}>{id.slice(0, 8)}…</span>
  }

  return (
    <div>
      {/* Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14, marginBottom: 20 }}>
        {[
          { label: 'Total invoices', value: invoices.length,    fmt: false, color: '#1B3A6B' },
          { label: 'Paid',           value: invoices.filter(i => i.status === 'PAID').length, fmt: false, color: '#166534' },
          { label: 'Total revenue',  value: fmtR(totalRevenue),     fmt: true, color: '#0D9488' },
          { label: 'Outstanding',    value: fmtR(totalOutstanding),  fmt: true, color: overdueCount > 0 ? '#DC2626' : '#D97706' },
        ].map(s => (
          <div key={s.label} style={{ background: 'white', border: '1px solid #E2E8F0', borderRadius: 12, padding: '16px 20px' }}>
            <div style={{ fontSize: s.fmt ? 18 : 26, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: '#64748B', marginTop: 3 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Status filter */}
      <div style={{ display: 'flex', gap: 6, marginBottom: 16, flexWrap: 'wrap' }}>
        {['ALL', 'DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED'].map(s => (
          <button key={s} onClick={() => setStatusFilter(s)}
            style={{ padding: '6px 14px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: 'none',
              background: statusFilter === s ? '#1B3A6B' : '#F1F5F9',
              color:      statusFilter === s ? 'white'   : '#64748B',
            }}>
            {s === 'ALL' ? 'All' : (INVOICE_STATUS[s]?.label ?? s)}
            {s !== 'ALL' && ` (${invoices.filter(i => i.status === s).length})`}
          </button>
        ))}
      </div>

      {/* Table */}
      <div style={{ background: 'white', border: '1px solid #E2E8F0', borderRadius: 14, overflow: 'hidden' }}>
        {isLoading ? (
          <div style={{ padding: 60, textAlign: 'center', color: '#94A3B8' }}>Loading invoices...</div>
        ) : isError ? (
          <div style={{ padding: 60, textAlign: 'center' }}>
            <AlertCircle size={32} color="#DC2626" style={{ marginBottom: 10 }} />
            <div style={{ fontWeight: 600, color: '#DC2626' }}>Failed to load invoices</div>
          </div>
        ) : filtered.length === 0 ? (
          <div style={{ padding: 60, textAlign: 'center', color: '#94A3B8' }}>
            <FileText size={36} color="#CBD5E1" style={{ marginBottom: 12, opacity: 0.5 }} />
            <div style={{ fontWeight: 600, color: '#475569', marginBottom: 4 }}>No invoices</div>
            <div style={{ fontSize: 13 }}>Convert an accepted quote to generate an invoice.</div>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #F1F5F9', background: '#F8FAFC' }}>
                {['Invoice #', 'Customer', 'Issued', 'Due', 'Total', 'Status', 'Actions'].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '11px 16px', fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map(inv => {
                const ss = INVOICE_STATUS[inv.status] ?? INVOICE_STATUS.DRAFT
                const isExp = expanded === inv.id
                return (
                  <>
                    <tr key={inv.id}
                      onClick={() => setExpanded(isExp ? null : inv.id)}
                      style={{ borderBottom: '1px solid #F8FAFC', cursor: 'pointer', background: isExp ? '#FAFBFF' : 'white' }}
                      onMouseEnter={e => { if (!isExp) (e.currentTarget as HTMLElement).style.background = '#F8FAFC' }}
                      onMouseLeave={e => { if (!isExp) (e.currentTarget as HTMLElement).style.background = 'white' }}>
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <FileText size={14} color="#0D9488" />
                          <span style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>{inv.invoiceNumber}</span>
                          {isExp ? <ChevronUp size={13} color="#94A3B8" /> : <ChevronDown size={13} color="#94A3B8" />}
                        </div>
                      </td>
                      <td style={{ padding: '14px 16px', fontSize: 13, fontWeight: 600, color: '#374151' }}>
                        {customerName(inv.customerId)}
                      </td>
                      <td style={{ padding: '14px 16px', fontSize: 13, color: '#64748B' }}>{fmtDate(inv.issuedAt)}</td>
                      <td style={{ padding: '14px 16px', fontSize: 13, color: inv.status === 'OVERDUE' ? '#DC2626' : '#64748B' }}>
                        {fmtDate(inv.dueDate)}
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>{fmtR(inv.total)}</div>
                        {inv.status === 'PARTIALLY_PAID' && inv.amountPaid > 0 && (
                          <div style={{ fontSize: 11, color: '#92400E', marginTop: 1 }}>Paid: {fmtR(inv.amountPaid)}</div>
                        )}
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <span style={{ background: ss.bg, color: ss.color, padding: '3px 10px', borderRadius: 20, fontSize: 12, fontWeight: 600 }}>
                          {ss.label}
                        </span>
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ display: 'flex', gap: 6 }} onClick={e => e.stopPropagation()}>
                          {inv.status === 'DRAFT' && (
                            <button disabled={issueInvoice.isPending}
                              onClick={() => issueInvoice.mutate(inv.id)}
                              style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: '#EFF6FF', color: '#1D4ED8', border: '1px solid #BFDBFE', borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                              <Send size={12} /> Issue
                            </button>
                          )}
                          {['ISSUED', 'PARTIALLY_PAID', 'OVERDUE'].includes(inv.status) && (
                            <button
                              onClick={() => {
                                setPayModal(inv); setPayError('')
                                setPayForm(f => ({ ...f, amount: String(inv.total - (inv.amountPaid ?? 0)) }))
                              }}
                              style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: '#F0FDF4', color: '#166534', border: '1px solid #BBF7D0', borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                              <CheckCircle size={12} /> Mark paid
                            </button>
                          )}
                          <button disabled={downloading === inv.id} onClick={() => downloadPdf(inv)}
                            style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: '#F8FAFC', color: '#64748B', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 12, fontWeight: 500, cursor: 'pointer' }}>
                            <Download size={12} />{downloading === inv.id ? '…' : 'PDF'}
                          </button>
                        </div>
                      </td>
                    </tr>

                    {/* Expanded line items */}
                    {isExp && (
                      <tr key={`${inv.id}-lines`}>
                        <td colSpan={7} style={{ padding: 0 }}>
                          <div style={{ background: '#F8FAFC', borderBottom: '1px solid #F1F5F9', padding: '16px 24px' }}>
                            <div style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.06em', marginBottom: 10 }}>LINE ITEMS</div>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                              <thead>
                                <tr style={{ borderBottom: '1px solid #E2E8F0' }}>
                                  {['Description', 'Qty', 'Unit Price', 'VAT %', 'Total'].map(h => (
                                    <th key={h} style={{ textAlign: 'left', padding: '6px 12px', fontSize: 11, fontWeight: 600, color: '#64748B', textTransform: 'uppercase' as const }}>{h}</th>
                                  ))}
                                </tr>
                              </thead>
                              <tbody>
                                {inv.lineItems.map((li, i) => (
                                  <tr key={i} style={{ borderBottom: i < inv.lineItems.length - 1 ? '1px solid #F8FAFC' : 'none' }}>
                                    <td style={{ padding: '8px 12px', color: '#374151' }}>{li.description}</td>
                                    <td style={{ padding: '8px 12px', color: '#64748B' }}>{li.quantity}</td>
                                    <td style={{ padding: '8px 12px', color: '#64748B' }}>{fmtR(li.unitPrice)}</td>
                                    <td style={{ padding: '8px 12px', color: '#64748B' }}>{li.vatRate ?? 15}%</td>
                                    <td style={{ padding: '8px 12px', fontWeight: 600, color: '#0F172A' }}>{fmtR(li.lineTotal)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 12 }}>
                              <div style={{ minWidth: 240 }}>
                                {[['Subtotal', fmtR(inv.subtotal)], ['VAT', fmtR(inv.vatTotal)]].map(([l, v]) => (
                                  <div key={l} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: 13, color: '#64748B' }}>
                                    <span>{l}</span><span>{v}</span>
                                  </div>
                                ))}
                                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0 0', fontSize: 15, fontWeight: 700, color: '#0F172A', borderTop: '1px solid #E2E8F0', marginTop: 4 }}>
                                  <span>Total</span><span>{fmtR(inv.total)}</span>
                                </div>
                              </div>
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
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: 'white', borderRadius: 16, padding: 28, width: 460, boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>Record Payment</h3>
                <p style={{ margin: '3px 0 0', fontSize: 13, color: '#94A3B8' }}>
                  {payModal.invoiceNumber} · Total {fmtR(payModal.total)}
                  {payModal.amountPaid > 0 && ` · Already paid ${fmtR(payModal.amountPaid)}`}
                </p>
              </div>
              <button onClick={() => setPayModal(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {[
                { label: 'Amount Received (R) *', key: 'amount',    type: 'number', placeholder: '' },
                { label: 'Payment Reference',     key: 'reference', type: 'text',   placeholder: 'e.g. EFT-20260530-001' },
                { label: 'Note (optional)',        key: 'note',      type: 'text',   placeholder: 'e.g. Paid in full' },
              ].map(f => (
                <div key={f.key}>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 5 }}>{f.label}</label>
                  <input type={f.type} value={(payForm as any)[f.key]}
                    onChange={e => setPayForm(p => ({ ...p, [f.key]: e.target.value }))}
                    placeholder={f.placeholder} style={inp} />
                </div>
              ))}
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 5 }}>Payment Method</label>
                <select value={payForm.paymentMethod}
                  onChange={e => setPayForm(p => ({ ...p, paymentMethod: e.target.value }))}
                  style={{ ...inp, appearance: 'none' as const }}>
                  {['EFT', 'CASH', 'CARD', 'CHEQUE', 'OTHER'].map(m => <option key={m}>{m}</option>)}
                </select>
              </div>
            </div>

            {payError && (
              <div style={{ marginTop: 14, padding: '10px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 8 }}>
                <AlertCircle size={15} color="#DC2626" style={{ flexShrink: 0 }} />{payError}
              </div>
            )}

            <div style={{ display: 'flex', gap: 10, marginTop: 22, justifyContent: 'flex-end' }}>
              <button onClick={() => { setPayModal(null); setPayError('') }}
                style={{ padding: '10px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: 'white', fontSize: 14, cursor: 'pointer', color: '#374151' }}>
                Cancel
              </button>
              <button
                disabled={!payForm.amount || markPaid.isPending}
                onClick={() => markPaid.mutate({
                  id: payModal.id,
                  body: {
                    amountPaid: Number(payForm.amount),
                    paymentMethod: payForm.paymentMethod,
                    reference: payForm.reference || undefined,
                    note: payForm.note || undefined,
                  },
                })}
                style={{
                  padding: '10px 22px', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700,
                  background: !payForm.amount ? '#E2E8F0' : '#16A34A',
                  color: !payForm.amount ? '#94A3B8' : 'white',
                  cursor: !payForm.amount ? 'not-allowed' : 'pointer',
                }}>
                {markPaid.isPending ? 'Saving...' : 'Confirm payment'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}